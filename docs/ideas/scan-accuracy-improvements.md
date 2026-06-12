# Scan Accuracy Improvements — Edge-Case Hardening

> Status: Discovery notes — candidate improvements for the recognition pipeline.
> Date: 2026-06-12
> Targets three edge cases: **partially visible (occluded) items**, **stacked items**, and **visually similar packaging** (e.g., flavor variants).

## Where the pipeline stands today

The current scan path (`ScanCounter` → `OpenRouterRecognizer`) is deliberately minimal — correct for an MVP, but it leaves most of the accuracy levers unpulled:

| Stage | Current behavior | Relevant code |
|---|---|---|
| Capture | Single CameraX photo, framing hint "spread items flat" (SCAN-1 assumes non-overlapping, top-down) | `ui/scan/capture/`, `ui/common/camera/` |
| Preprocess | Downscale longest edge to **1024 px**, JPEG q80 | `data/image/AndroidImageDownscaler.kt` |
| Catalog context | **Text only**: `SKU - name` list for the full active catalog | `OpenRouterRecognizer.catalogContext()` |
| Prompt | Single short instruction; asks for aggregate `{sku, quantity, confidence}` | `OpenRouterRecognizer.kt` `PROMPT` |
| Model | One call to a free-tier model (`nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`) | `OpenRouterDefaults.kt` |
| Output | Aggregate quantity per SKU; `BoundingBox` exists in the domain model but is **never requested or populated** | `domain/recognizer/Recognizer.kt` |
| Review | Low-confidence flag below 0.6; unidentified items surfaced; cashier edits | `ui/scan/draft/` |

Key observation: the original idea doc (`photo-checkout.md`) said enrollment photos "seed the LLM prompt context now and become the embedding gallery later" — **the first half was never implemented**. Reference photos are enrolled and stored on `CatalogItem.photos` but the recognizer never sees them. That is the single biggest gap for the "similar packaging" edge case.

## Why each edge case fails today

- **Partially scanned (occluded) items.** At 1024 px across a whole counter, a half-hidden item is a few dozen ambiguous pixels. The prompt never tells the model whether to count partial items, so behavior is undefined — sometimes dropped (silent undercount), sometimes guessed at high confidence. There is no signal in the response schema to say "I saw something but only part of it."
- **Stacked items.** The model returns one aggregate `quantity` per SKU with a single confidence. Counting a stack of 4 identical cups requires counting rim edges — exactly what small VLMs are worst at — and a wrong count of a *correctly identified* item often comes back with *high* confidence, so it sails past the 0.6 flag. The cashier has no cue that "3" might really be 4.
- **Similar packaging.** The model only knows each product as a *name string*. "Choco Wafer" vs "Vanilla Wafer" differ by a small label patch the model has never seen for *our* products. Text names give it nothing visual to anchor on, and near-identical variants are the known failure mode the idea doc already flagged.

---

## Recommendations

Ordered by expected impact-per-effort. R1–R4 are prompt/schema/context changes inside `data/recognizer/` — no architecture change, swappable behind the existing `Recognizer` seam (X-2). R5–R8 touch capture/UI. R9–R10 are process/infrastructure that make every other change measurable.

### R1. Put enrolled reference photos into the prompt (visual grounding) — *similar packaging*

Attach reference photos as additional `image_url` parts, labeled by SKU, so the model matches *our* packaging instead of imagining what a name looks like:

```
[text]  "Reference photos follow, one per labeled SKU. Then the counter photo."
[text]  "SKU-0001 — Choco Wafer:"        [image: enrollment photo, thumbnail]
[text]  "SKU-0002 — Vanilla Wafer:"      [image: enrollment photo, thumbnail]
...
[text]  "Counter photo to itemize:"      [image: capture]
```

Cost control (the catalog can't all be attached forever):

- Downscale reference photos hard (~256 px thumbnails) — they only need to convey packaging gestalt and label color, and image tokens scale with resolution. Pre-generate thumbnails at enrollment, not per scan.
- **Tiered rollout:** (a) all SKUs while the catalog is small; (b) only SKUs in *confusion groups* (R4) once it grows; (c) two-pass disambiguation (R5) at scale.
- This is exactly the enrollment-investment payoff the idea doc promised; the photos already exist.

### R2. Per-instance detections with bounding boxes — *stacked + occluded*

Change the requested schema from aggregate counts to one entry **per physical item instance**, with a box and per-instance flags:

```json
{"items": [
  {"sku": "SKU-0001", "box": [0.10, 0.22, 0.34, 0.51],
   "confidence": 0.92, "occluded": false},
  {"sku": "SKU-0001", "box": [0.12, 0.18, 0.33, 0.27],
   "confidence": 0.55, "occluded": true}
]}
```

Why this beats `quantity: 2`:

- **Counting becomes enumeration.** Forcing the model to localize each instance is a classic VLM accuracy trick — it can't lazily emit "3" without committing to three places; stacks get counted by visible rims/edges instead of vibes.
- **Occlusion gets an explicit channel.** A partially visible item arrives as its own low-confidence, `occluded: true` instance instead of silently inflating or missing a count.
- **The UI can show its work** (R7): boxes drawn over the capture let the cashier verify a count in one glance.
- The domain model is already shaped for this — `RecognizedItem.boundingBox` exists, unused. `ScanCounter`/`priceDraft` aggregate instances → quantity, so pricing logic is untouched.

Keep aggregate parsing as a fallback for models that return boxes poorly; measure per model with the eval set (R9).

### R3. Make the prompt edge-case-explicit, with structured uncertainty — *all three*

The current one-sentence prompt leaves every edge-case policy to model whim. Spell them out and give uncertainty somewhere structured to go:

- **Occlusion policy:** "Count an item if you can identify it from the visible part. If partially hidden, set `occluded: true` and lower confidence. If you can see *something* but cannot identify it, return it as `sku: null` with a box" → flows into the existing `UnidentifiedItem` path (SCAN-7) instead of being dropped.
- **Stack policy:** "Items may be stacked or rowed. Count visible rims/edges/lids. If more units may be hidden behind/under what you can see, set `possiblyMore: true`." → draft UI renders "3+? check stack" on that line.
- **Look-alike policy:** "Some catalog items differ only by flavor/color/size. When torn between specific SKUs, pick the best and list the runners-up in `alternates`." → `{"sku": "SKU-0001", "alternates": ["SKU-0007"], ...}` powers a one-tap disambiguation chip in Draft Review — turning a wrong-item edit (find in picker) into a single tap.

These response fields extend `RecognizedItem` naturally (`occluded`, `possiblyMore`, `alternates`) and degrade gracefully when a model omits them.

### R4. Enrich catalog context with visual descriptions + confusion groups — *similar packaging*

Cheap text-token upgrades to `catalogContext()`:

- **Distinguishing-feature line per SKU.** At enrollment, auto-generate it with a one-time VLM call over the reference photos ("describe the visual features that distinguish this package: colors, label text, size, shape"), editable by the owner. Scan-time context becomes `SKU-0001 - Choco Wafer | brown wrapper, red "CHOCO" band, 24g sachet`. One-time cost per product, pennies; every scan benefits.
- **Confusion groups.** Maintain groups of look-alike SKUs (manual tag at enrollment first; later auto-derived from cashier-correction telemetry, R10). Two uses:
  1. Prompt: "SKU-0001/0007/0012 look nearly identical — distinguish by label color."
  2. **Trust policy:** when a detected SKU belongs to a confusion group, force the low-confidence flag in `priceDraft` regardless of reported confidence. Models are reliably *overconfident* between near-identical variants; the group membership is a better uncertainty signal than the model's own number.

### R5. Two-pass recognition: detect, then zoom-and-verify — *all three, biggest accuracy ceiling*

The downscaler throws away the resolution that distinguishes look-alikes: at 1024 px over a 10-item counter, each item is ~150–250 px and label text is gone. The original full-res JPEG is still in hand (`ScanCaptureViewModel.onPhotoCaptured` has the pre-downscale bytes). Use it:

1. **Pass 1 (cheap, full scene):** current call at 1024 px, now returning boxes (R2).
2. **Crop escalation (targeted, full-res):** for instances that are low-confidence, `occluded`, in a confusion group, or `sku: null` — crop that box *from the original full-res bytes* and re-ask: "Which of these SKUs is this?" with only the relevant reference photos (R1) attached.

This buys 3–6× effective resolution exactly where it matters while keeping the common all-confident scan at one cheap call. Latency: crops run in parallel; budget remains ≲5 s (SCAN-3). Implement as a `Recognizer` decorator (`TwoPassRecognizer(primary, verifier)`) so it composes behind the seam and `FakeRecognizer` tests (SCAN-10) stay valid. Also simply test raising `MAX_EDGE_PX` to 1536 — on current-gen VLM pricing the single-call cost bump may be acceptable and is a one-constant experiment.

### R6. Capture-side quality gates and guidance — *prevents bad inputs*

Recognition can't recover what the photo never contained:

- **Pre-upload quality check:** cheap on-device blur (Laplacian variance) and exposure/glare checks; prompt an immediate retake instead of burning a 5 s round-trip on a doomed image. No-network, no-cost.
- **Live framing guidance:** strengthen the existing "spread items flat" hint — edge-of-frame warnings (cut-off items are self-inflicted "partial scans"), angle hint for stacks (~30–45° oblique shows both lids and side labels; a pure top-down shot makes stack counting nearly impossible).
- **Optional second shot for stacks (Phase 1.5):** when a draft comes back with `possiblyMore` lines, offer "Add angle photo?" and send both images in one request. Multi-shot was scoped out of MVP (SCAN-1) — keep it *reactive* (only when the first pass signals stack uncertainty) so the common case stays one-photo-fast.

### R7. Show the evidence in Draft Review — *cashier catches what the model can't*

With boxes (R2), the review step becomes verification rather than recall:

- Thumbnail of the capture above the draft with numbered detection boxes; tapping a line highlights its box (and vice versa).
- `occluded` / `possiblyMore` lines get a distinct "verify count" badge — a different *action* than the generic low-confidence flag (check the pile vs. check the identity).
- `alternates` render as one-tap swap chips on the line.
- An unidentified detection shows its crop next to the catalog picker, so the cashier resolves it by sight.

This doesn't raise model accuracy, but it converts residual model errors from *refunds* into *one-glance fixes* — which is the metric that actually matters (rule: "draft, don't decide").

### R8. Model strategy: benchmark, then escalate on doubt

The default model is a free nano-tier model — fine for wiring, but accuracy claims about edge cases are untestable on it:

- Benchmark 2–3 stronger VLMs (e.g., Claude Haiku/Sonnet-class, Gemini Flash-class) on the eval set (R9). The `Recognizer` seam + `modelId` config make this a settings change, not a code change.
- **Escalation policy:** cheap model first; if the result trips doubt signals (low confidence, confusion-group hit, `sku: null`), re-run pass 2 (R5) on a stronger model. Pay top-tier price only for the hard ~10–20% of scans.

### R9. Build the eval harness *before* tuning anything

Every recommendation above is a knob; without a fixed measuring stick, tuning is guesswork:

- Assemble the golden set the idea doc already called for (~50 labeled counter photos), **deliberately oversampling the three edge cases**: partial occlusion, stacks of 2–6, and look-alike pairs side by side.
- A JVM/CLI harness that runs the set through any `Recognizer` config and reports item precision/recall, count MAE (the stacked-items metric), and look-alike confusion rate. Track cost + latency per scan alongside.
- Run it on every prompt/schema/resolution/model change. This is the only way to know whether, say, R2 helps or hurts on a given model.

### R10. Close the loop with correction telemetry

`ScanTelemetryEvent` currently records latency/success/counts. The highest-value accuracy signal is free and unexploited: **what the cashier edits**.

- Record per-draft corrections: line removed (false positive), quantity changed ±n (miscount — stacks show up here), manual add after scan (miss), and alternate-chip swaps (look-alike confusion, with both SKUs).
- Aggregate corrected-SKU pairs → auto-populate confusion groups (R4) and prioritize which products need better enrollment photos.
- Per-product correction rate flags weak enrollment ("Choco Wafer is corrected in 30% of scans — retake its reference photos").

---

## Suggested sequencing

| Step | Items | Effort | Risk |
|---|---|---|---|
| 1 | R9 eval set + harness | S–M | none — pure measurement |
| 2 | R3 prompt/schema (occlusion, stack, alternates) + R2 per-instance boxes | S | low — parser fallback keeps old behavior |
| 3 | R1 reference photos in prompt (small-catalog mode) + R4 visual descriptions | M | cost ↑ per scan — measure on harness |
| 4 | R7 evidence UI + R10 correction telemetry | M | UI-only |
| 5 | R8 model benchmark; pick default + escalation rule | S | config-only |
| 6 | R5 two-pass crop escalation | M–L | latency — keep within SCAN-3 budget |
| 7 | R6 quality gates; reactive second shot for stacks | M | scope: relaxes SCAN-1's one-shot rule |

Steps 1–3 alone should move all three edge cases measurably, stay entirely inside `data/recognizer/` + prompt territory, and respect every locked decision (model identifies / DB prices; draft-don't-decide; vendor-agnostic seam).

## Open questions

- How large will the active catalog realistically get? (Decides how long R1's "attach everything" mode survives before confusion-group filtering is required.)
- Can the chosen model return usable bounding boxes? (Varies sharply by model — R2's value is model-dependent; the harness answers this.)
- Is the per-scan cost ceiling firm enough to rule out a mid-tier model as the *default* rather than the escalation target?
- For stacks of fully hidden units (a 6-stack photographed dead top-down shows 1 lid), is the reactive second shot (R6) acceptable UX, or should the framing guidance simply forbid top-down captures?
