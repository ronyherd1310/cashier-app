# Spec: R2 — Per-Instance Detections with Bounding Boxes

> Source idea: `docs/ideas/scan-accuracy-improvements.md` (R2 only)
> Parent spec: `docs/spec/SPEC.md` (Photo Checkout) — this spec inherits its commands,
> project structure, code style, and boundaries; only deltas are stated here.
> Sibling spec: `docs/spec/scan-r1-reference-photos.md` (R1, implemented) — R2 builds on
> the same request-assembly code without conflicting with it.
> Status: Approved — all open questions resolved at review (2026-06-13); implementing.
> Date: 2026-06-13

## Objective

Change the requested recognition schema from an **aggregate count per SKU**
(`{sku, quantity, confidence}`) to **one entry per physical item instance**, each
carrying a normalized bounding box: `{sku, box, confidence}`. Populate the
already-defined-but-unused `RecognizedItem.boundingBox`, then aggregate instances back
into one priced draft line per SKU so pricing and the Draft Review UI are untouched.

**Why:** The model returns a single `quantity` per SKU with a single confidence today.
Counting a stack of 4 identical cups means counting rim edges — exactly what small VLMs
do worst — and a wrong count of a *correctly identified* item often comes back with
*high* confidence, sailing past the 0.6 flag (`Pricing.kt` low-confidence rule). Forcing
the model to localize **each** instance turns lazy counting into enumeration: it cannot
emit "3" without committing to three boxes. This is the primary lever for the
**stacked-items** edge case and a prerequisite for the occlusion channel (R3) and the
evidence UI (R7), both of which need per-instance boxes to exist.

**User:** The cashier, indirectly — more accurate stack counts mean fewer quantity edits
per scan. No UI changes in R2; boxes are populated in the domain model but not yet drawn
(that is R7). This is a `data/recognizer/` + `domain/pricing/` change behind the existing
`Recognizer` seam (X-2).

**Success looks like:** stack counts are right more often (lower count error on the eval
set when it lands, R9), with no regression to single-item scans, no change to pricing
math (X-1), and no change to the Draft Review screen or its edit/remove/add behavior.

## Assumptions

Resolved from the code and the idea doc; correct them at review if wrong.

1. **Aggregation is added to `priceDraft`, and that is the load-bearing change.** Today
   `priceDraft` emits one `DraftLine` per `RecognizedItem` and **never groups by SKU**
   (`domain/pricing/Pricing.kt`), while the whole Draft Review screen keys edits by SKU
   (`DraftViewModel.updateLine(sku)` / `removeLine(sku)` / `addCatalogItem` match on
   `line.sku`). Per-instance detections would otherwise produce N lines for the same SKU
   and break those mutations. R2 makes `priceDraft` group detections by SKU into one line
   with `quantity = number of instances`. This grouping is backward-compatible: a legacy
   aggregate detection (`quantity: 2`, no box) groups to the same single line it produces
   today. The idea doc's "pricing logic is untouched" is true only *after* this grouping
   exists — calling it out as the central design point, not a footnote.
2. **Confidence after grouping = the minimum across that SKU's instances.** A stack flagged
   low by any one uncertain instance should flag the whole line — conservative is correct
   for "draft, don't decide." (Alternative: mean. Flip at review.)
3. **`box` is `[left, top, right, bottom]`, normalized 0f..1f, top-left origin** — the
   shape `BoundingBox` already encodes (`domain/recognizer/Recognizer.kt`). A malformed,
   out-of-range, or wrong-length box degrades that detection to `boundingBox = null`; it
   is **never** dropped and **never** fails the scan (the detection still counts).
4. **No `Recognizer` interface change.** `recognize(image, catalog): Result<List<RecognizedItem>>`
   is unchanged; `RecognizedItem.boundingBox` already exists. R2 only starts *populating*
   it. This respects the parent "Ask first: changing the `Recognizer` signature".
5. **Aggregate parsing is kept as a fallback.** A model that ignores the per-instance
   instruction and returns `{sku, quantity: 3}` with no box still parses correctly: the
   DTO maps to one `RecognizedItem(quantity = 3, boundingBox = null)` and groups normally.
   Per-model box quality is measured later on the eval set (R9), not gated here.
6. **`occluded` / `possiblyMore` / `alternates` are out of R2.** The idea doc's R2 JSON
   example shows an `occluded` flag, but the occlusion *policy wording* and the new
   `RecognizedItem` fields belong to R3 (which extends the same DTO and prompt). R2 stops
   at per-instance enumeration + boxes + aggregation, so R3 lands cleanly on top without
   re-touching aggregation. The prompt change here stays minimal and additive.
7. **Default model only.** Built and verified against the configured default
   (`nemotron` free tier). Whether that model returns usable boxes is an open question the
   R9 harness answers (and R8 owns model strategy); R2's parser-fallback (Assumption 5)
   means a box-poor model degrades to today's behavior rather than regressing.

## Scope

**In:** per-instance prompt wording, the response-DTO `box` field + bounding-box parsing
in `OpenRouterRecognizer`, populating `RecognizedItem.boundingBox`, SKU-grouping
aggregation in `priceDraft` (for both identified lines and unidentified items), and tests.

**Out (non-goals):** drawing boxes / numbered detections in Draft Review (R7); the
`occluded` / `possiblyMore` / `alternates` schema fields and edge-case prompt policies
(R3); confusion groups and visual descriptions (R4); two-pass crop escalation (R5);
capture-side quality gates (R6); model benchmarking and escalation (R8); the eval harness
and golden set (R9 — see Open Questions); correction telemetry (R10); any `DraftLine`,
`DraftReceipt`, Room, or `ScanTelemetryEvent` schema change; any settings UI.

## Design

### Requested schema

Today the prompt asks for `{"items":[{"sku","quantity","confidence"}]}`. R2 asks for one
entry **per physical instance**, each with a normalized box and no `quantity`:

```json
{"items": [
  {"sku": "SKU-0001", "box": [0.10, 0.22, 0.34, 0.51], "confidence": 0.92},
  {"sku": "SKU-0001", "box": [0.12, 0.18, 0.33, 0.27], "confidence": 0.55}
]}
```

`box` is `[left, top, right, bottom]`, each `0..1`, origin top-left. Two entries with the
same SKU mean two physical units — the model counts by localizing, not by emitting a
number. The "use ONLY catalog SKUs / never invent SKUs / never include prices" rules and
the reference-photo wording (R1) are unchanged.

### Prompt delta (`OpenRouterRecognizer.PROMPT`)

Replace the "how many of each" aggregate instruction with per-instance enumeration:

> Return **one entry per physical item instance** you can see, not one entry per product
> type. For each instance give its bounding box as `[left, top, right, bottom]` with each
> value between 0 and 1 (top-left origin), the matching catalog SKU, and a confidence
> 0..1. If you see three units of the same product, return three entries. Use ONLY SKUs
> from the catalog list. Never invent SKUs and never include prices.

Response format stays `json_object`.

### DTO (`data/recognizer/dto/ChatCompletionResponse.kt`)

```kotlin
@Serializable
public data class RecognizedItemDto(
    val sku: String,
    val quantity: Int = 1,          // kept for the aggregate fallback (Assumption 5)
    val confidence: Float = 0f,
    val box: List<Float>? = null,   // [left, top, right, bottom], normalized 0..1
)
```

`box` is nullable and defaults to null, so existing parse tests and any aggregate-only
model response keep working unchanged.

### Recognizer mapping (`OpenRouterRecognizer.recognize`)

Each DTO maps to a `RecognizedItem`, populating `boundingBox` only when `box` is well-formed:

```kotlin
payload.items.map { dto ->
    RecognizedItem(
        sku = dto.sku,
        quantity = dto.quantity,                 // 1 for per-instance entries
        confidence = dto.confidence,
        boundingBox = dto.box?.toBoundingBoxOrNull(),
    )
}

// A box must be exactly 4 finite values, each in 0f..1f, with left < right and top < bottom.
// Anything else → null (detection kept, box dropped). Never throws, never fails the scan.
private fun List<Float>.toBoundingBoxOrNull(): BoundingBox? { ... }
```

### Aggregation (`domain/pricing/Pricing.kt` — the central change)

`priceDraft` groups detections by SKU before building lines:

- **Identified SKUs:** one `DraftLine` per SKU; `quantity = sum of the group's quantities`
  (per-instance entries contribute 1 each; a legacy aggregate contributes its `quantity`),
  clamped `>= 1`; `confidence = min over the group` (Assumption 2); `lowConfidence` derived
  from that min vs `CONFIDENCE_THRESHOLD`. `unitPriceMinor`/`lineTotalMinor` from the
  catalog as today (X-1). Group order follows first appearance, so output ordering is
  stable and deterministic.
- **Unidentified SKUs:** grouped the same way into one `UnidentifiedItem` per raw SKU,
  `quantity = group size/sum`, `confidence = min` — still surfaced, never dropped (SCAN-7).
- Boxes are **not** stored on `DraftLine` in R2 (no schema change; that is R7). They live on
  `RecognizedItem` up to the pricing boundary; R7 will thread them into the draft model.

This makes per-instance output and legacy aggregate output converge on identical drafts,
so `ScanCounter`, `DraftViewModel`, and every `ui/` screen are untouched.

### What does NOT change

`Recognizer` interface, `RecognizedItem`/`BoundingBox` shape, `DraftLine`/`DraftReceipt`,
`ScanCounter`, `DraftViewModel` and all of `ui/`, `FakeRecognizer` (SCAN-10), `StubRecognizer`,
the Room schema, settings, and the R1 reference-photo request assembly.

## Tech Stack

Inherited from `docs/spec/SPEC.md`. No new dependencies (kotlinx.serialization handles the
optional `box` list; bounding-box validation is plain Kotlin).

## Commands

Inherited from `docs/spec/SPEC.md`:

```
Unit tests:            ./gradlew app:testDebugUnitTest
Instrumented tests:    ./gradlew app:connectedDebugAndroidTest
Format / lint:         ./gradlew app:ktlintFormat && ./gradlew app:ktlintCheck
Static analysis:       ./gradlew app:detekt
```

(Gradle needs JDK 17+; export `JAVA_HOME` to java-21 first.)

## Project Structure (files touched)

```
app/src/main/java/com/cashierapp/photocheckout/
  data/recognizer/dto/ChatCompletionResponse.kt   → add nullable `box` to RecognizedItemDto
  data/recognizer/OpenRouterRecognizer.kt          → PROMPT rewrite + box parse/validate + map boundingBox
  domain/pricing/Pricing.kt                         → group detections by SKU; sum qty; min confidence
app/src/test/java/com/cashierapp/photocheckout/
  domain/pricing/PricingTest.kt                     → aggregation cases (grouping, min-confidence, fallback)
app/src/androidTest/java/com/cashierapp/photocheckout/
  data/recognizer/OpenRouterRecognizerTest.kt       → per-instance parse, box population, malformed-box degrade
docs/spec/SPEC.md                                   → amend the X-2 / catalog-context notes after approval
```

## Code Style

Inherited (ktlint, explicit public API, KDoc citing criteria IDs, vendor detail only in
`data/recognizer/`, integer-minor-unit money). Aggregation sketch — note pricing still
reads prices only from the catalog:

```kotlin
// domain/pricing/Pricing.kt — group per-instance detections into one line per SKU (R2).
val bySku = recognized.groupBy { it.sku }
for ((sku, group) in bySku) {
    val quantity = group.sumOf { it.quantity }.coerceAtLeast(1)
    val confidence = group.minOf { it.confidence }     // conservative flag (Assumption 2)
    val item = catalog[sku]
    if (item == null) {
        unidentified += UnidentifiedItem(rawSku = sku, quantity = quantity, confidence = confidence)
    } else {
        lines += DraftLine(
            sku = item.sku, name = item.name, quantity = quantity,
            unitPriceMinor = item.priceMinor,                       // from DB, never the model
            lineTotalMinor = item.priceMinor * quantity,
            confidence = confidence,
            lowConfidence = confidence < CONFIDENCE_THRESHOLD,
            photoPath = item.photos.firstOrNull()?.path,
        )
    }
}
```

## Testing Strategy

`PricingTest` is JVM unit (L1); recognizer parsing is instrumented (L2/SCAN-INT-4,
MockWebServer) where `OpenRouterRecognizerTest` already lives.

- **`PricingTest` (JVM unit) — aggregation is the riskiest logic, cover it exhaustively:**
  - Three per-instance detections of one SKU (`quantity = 1` each) → one line, `quantity = 3`.
  - Mixed identified + unidentified per-instance entries → grouped correctly on both sides.
  - Min-confidence rule: a group with one instance at 0.5 and two at 0.9 → line `confidence`
    0.5 and `lowConfidence = true`; a group all ≥ 0.6 → not flagged.
  - **Backward-compat:** a single legacy aggregate detection (`quantity = 2`, no box) →
    identical line to today (`quantity = 2`). Existing `PricingTest` cases stay green.
  - Money math (X-1): `lineTotalMinor == unitPriceMinor * summed quantity`, integer minor
    units, verified to the unit.
  - Deterministic ordering: lines follow first-appearance SKU order.
- **`OpenRouterRecognizerTest` extensions (androidTest, MockWebServer):**
  - A per-instance response (two same-SKU entries with valid boxes) parses into two
    `RecognizedItem`s, each with `boundingBox` populated and correct corner values.
  - A malformed box (length ≠ 4 / value > 1 / `left >= right`) → that detection still
    present with `boundingBox = null`; the scan **succeeds**.
  - The outgoing request prompt asks for one entry per instance with a box (assert prompt
    text), and R1 reference-photo assembly is unchanged (existing R1 tests stay green).
  - Existing tests (HTTP error, timeout, missing key, aggregate parse) pass unchanged —
    the legacy `{sku, quantity, confidence}` body still parses.
- **Regression:** `ScanCounterTest`, `DraftViewModelTest`, and the draft/E2E suites pass
  untouched — proof the seam held and the UI never saw per-instance entries (X-2, SCAN-10).
- **Not in CI:** whether the live default model returns *usable* boxes / better stack counts
  — that is the R9 eval-set measurement (count MAE on stacks), informed but not gated here.

## Boundaries

Inherited from `docs/spec/SPEC.md`, plus R2-specific:

- **Always:** aggregate per-instance detections into one line per SKU before the draft is
  built; keep prices strictly from the catalog (X-1); degrade a malformed/missing box to
  `boundingBox = null` without dropping the detection or failing the scan; keep the
  aggregate-quantity parse path working as a fallback.
- **Ask first:** changing the confidence-aggregation rule from `min`; storing boxes on
  `DraftLine`/`DraftReceipt` (that is R7 and a domain-model change); adding `occluded` /
  `possiblyMore` / `alternates` (that is R3); changing the `Recognizer` signature.
- **Never:** let a box value or instance count influence price/total/tax; drop a detection
  because its box was unparseable; surface raw per-instance lines (multiple same-SKU lines)
  to the cashier; request or accept prices from the model.

## Acceptance Criteria

- **R2-1:** The recognition prompt requests one entry per physical instance, each with a
  normalized `[left, top, right, bottom]` box; the response DTO accepts an optional `box`.
- **R2-2:** A well-formed per-instance response populates `RecognizedItem.boundingBox` with
  the corresponding normalized corners; a malformed/out-of-range/wrong-length box yields
  `boundingBox = null` for that detection, which is still counted.
- **R2-3:** `priceDraft` produces exactly one `DraftLine` per identified SKU with
  `quantity` equal to the number of detected instances of that SKU, and one
  `UnidentifiedItem` per unidentified raw SKU grouped the same way (SCAN-7). No SKU ever
  produces two lines.
- **R2-4:** Line `confidence` is the minimum across that SKU's instances and `lowConfidence`
  derives from it vs `CONFIDENCE_THRESHOLD` (SCAN-6).
- **R2-5:** A legacy aggregate response (`{sku, quantity, confidence}`, no box) yields a
  draft byte-identical to today's behavior (backward compatible).
- **R2-6:** The `Recognizer` interface, `DraftLine`/`DraftReceipt`/Room schema, `ScanCounter`,
  and all `ui/` code are unchanged (X-2); `FakeRecognizer` flows (SCAN-10) and the draft/E2E
  suites pass without edits.
- **R2-7:** No reference-photo behavior from R1 regresses; no per-box condition fails a scan
  (SCAN-9 preserved).

## Success Criteria

- All acceptance criteria pass; new + existing unit and instrumented suites green;
  `ktlintCheck` and `detekt` clean.
- A manual device check with a small stack (e.g., 3–4 identical units) shows the request
  prompting for per-instance boxes and the draft showing the right quantity on one line —
  a smoke check, not a benchmark.
- Measured numbers recorded in the PR: for a few stack/multi-item captures, how many boxes
  the default model returns vs. the true instance count (the count-error signal the R9
  harness will formalize), plus any request/latency delta from the prompt change.

## Resolved Decisions

Settled at spec review (2026-06-13):

1. **Keep the default model; do not pull R8 forward (Open Question 1).** R2 is built and
   verified against the configured default (`nemotron` free tier). Whether that model
   returns usable boxes is left as a known unknown — the parser-fallback (Assumption 5)
   means a box-poor model degrades to today's aggregate behavior rather than regressing,
   so shipping is safe either way. The accuracy *gain* is measured under R9, not gated
   here. No model change and no model spike inside R2; model strategy stays R8's scope.
2. **Grouped-line confidence = `min` across instances (Open Question 2).** Confirmed as
   spec'd: a stack flagged low by any one uncertain instance flags the whole line.
   Conservative is correct for "draft, don't decide" (SCAN-6). Locks Assumption 2.
3. **R2 proceeds ahead of R9 (Open Question 3).** As R1 did, R2 ships now with
   unit/instrumented correctness coverage; the formal accuracy numbers (stack count MAE on
   the golden set) wait for R9. Stack-count smoke numbers are recorded in the R2 PR. R2 is
   implemented in isolation — no other recommendation (R3–R10) is pulled into this change.

## Open Questions

None — resolved above. Next phase: implementation per the Testing Strategy and
Acceptance Criteria above.
