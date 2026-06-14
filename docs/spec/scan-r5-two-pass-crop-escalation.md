# Spec: R5 — Two-Pass Recognition (detect, then zoom-and-verify)

> Source idea: `docs/ideas/scan-accuracy-improvements.md` (R5 only)
> Parent spec: `docs/spec/SPEC.md` (Photo Checkout) — this spec inherits its commands,
> project structure, code style, and boundaries; only deltas are stated here.
> Sibling specs: `docs/spec/scan-r1-reference-photos.md` (R1, implemented),
> `docs/spec/scan-r2-per-instance-detections.md` (R2, implemented — boxes),
> `docs/spec/scan-r3-edge-case-prompt.md` (R3, implemented — `occluded`/`possiblyMore`/`alternates`),
> `docs/spec/scan-r4-visual-descriptions-confusion-groups.md` (R4, implemented — `confusionGroup`,
> descriptions, forced flag). **R5 depends on R2** (it crops the boxes R2 produces) and **consumes
> R3 + R4 signals** to decide what to escalate; all three are on `main`.
> Status: Implemented. Date: 2026-06-13. Implementation branch:
> `feat/scan-r5-two-pass-crop-escalation`.

## Objective

Add a second, targeted recognition pass that re-examines only the **uncertain** detections from the
first pass at **full resolution**, so the resolution thrown away by the downscaler is spent exactly
where it decides the answer — on look-alikes, occluded items, and stacks.

The downscaler reduces the longest edge to 1024 px (`AndroidImageDownscaler.MAX_EDGE_PX`). Over a
10-item counter each item is ~150–250 px and label text is gone — the precise pixels that separate
"Choco Wafer" from "Vanilla Wafer." The original full-resolution JPEG still exists at capture time
(`ScanCaptureViewModel.onPhotoCaptured` receives `jpegBytes` before downscaling) but is discarded
today. R5 keeps it, and for each low-confidence / occluded / confusion-group / `sku: null`
detection, **crops that detection's box out of the full-res bytes** and re-asks a focused question
("which of these candidate SKUs is this?") with only the relevant reference photos (R1) attached.

This is implemented as a `Recognizer` **decorator** — `TwoPassRecognizer(primary, verifier, cropper)`
— so it composes behind the existing seam (X-2): pass 1 and pass 2 are each ordinary `Recognizer`
calls, and `FakeRecognizer`/`StubRecognizer` and every downstream test stay valid because the
decorator *is* a `Recognizer` returning the same `List<RecognizedItem>`.

**Why:** R2/R3/R4 squeezed the single 1024 px call (enumeration, edge-case prompt, visual text). R5
is the first recommendation to spend more *pixels* rather than more *prompt*, which the idea doc
calls "the biggest accuracy ceiling" for all three edge cases. The common case — every pass-1
detection already confident — pays nothing extra: zero crops, one cheap call, exactly today's path.

**User:** The cashier, indirectly — fewer look-alike misreads and fewer occluded/stack
miscounts reach the draft. **No `ui/` change in R5** (no new `ScanStage`, no Draft Review change);
the second pass happens entirely inside the decorator's `recognize`. The evidence UI is R7.

**Success looks like:** an all-confident scan runs exactly one primary call and is byte-identical to
today's draft; a scan with uncertain detections escalates only those, crops them from full-res, and
returns a corrected `List<RecognizedItem>` that prices through the unchanged `priceDraft`; total
latency stays within the SCAN-3 budget (≤ ~5 s) because crops run in parallel and are capped; a
crop/verify failure degrades to the pass-1 detection and never fails the scan (SCAN-9); the
`Recognizer` signature and `priceDraft` are unchanged (X-1, X-2).

## Assumptions

Resolved from the code, the idea doc, and the R1–R4 specs; correct them at review if wrong.

1. **The load-bearing change is making the full-res bytes reach the seam — without changing the
   `Recognizer` signature.** `Recognizer.recognize(image, catalog)` receives only the *downscaled*
   `CapturedImage`; the full-res JPEG lives only in `ScanCaptureViewModel.onPhotoCaptured` and is
   dropped after `downscaler.downscale(jpegBytes)`. R5 carries it forward by adding an optional
   `original: CapturedImage? = null` to `CapturedImage` (the downscaled image points back at its
   full-res source). `AndroidImageDownscaler` populates it (it already decodes the source bitmap, so
   it has the original bytes + dimensions). The `Recognizer` interface is **untouched** — this
   respects the parent "Ask first: changing the `Recognizer` signature." The decorator crops from
   `image.original` when present and falls back to cropping the downscaled `image` itself when it is
   absent (e.g. a `FakeRecognizer` test image), so behavior degrades, never breaks.
2. **Cropping is a new domain port (`ImageCropper`) with an Android impl, mirroring `ImageDownscaler`.**
   A `domain/image/ImageCropper.kt` pure-Kotlin contract — `suspend fun crop(image, box): CapturedImage`
   — keeps the domain Android-free; `data/image/AndroidImageCropper.kt` decodes the source bytes,
   maps the normalized `BoundingBox` to pixels (with a small padding margin so a tight box doesn't
   clip the label), crops, and re-encodes JPEG. A decode/crop failure returns `null`-equivalent
   (the decorator then keeps the pass-1 detection).
3. **The verifier is just another `Recognizer`; pass 2 reuses the seam.** `TwoPassRecognizer` holds a
   `verifier: Recognizer` and calls `verifier.recognize(crop, candidateSubCatalog)` for each
   escalated detection. Reusing the seam means: (a) the OpenRouter verifier automatically attaches
   R1 reference photos **for just the candidate SKUs** (because `referenceParts` is built from the
   passed catalog) — exactly the idea doc's "only the relevant reference photos"; (b)
   `FakeRecognizer` can stand in for the verifier in tests. In R5 the verifier defaults to the **same**
   OpenRouter recognizer / same model as pass 1 — escalating to a *stronger* model is R8 and is out
   of scope here (the seam already makes it a later config change).
4. **Escalation trigger = the R2/R3/R4 doubt signals, and only detections that have a box.** A
   detection is escalated when **any** of: `confidence < threshold` (SCAN-6),
   `occluded == true` (R3), `sku == null` (R3 unidentified), or its SKU is in an **active confusion
   group** of ≥2 members (R4) — **and** it carries a `boundingBox` (no box ⇒ nowhere to crop ⇒ keep
   the pass-1 detection unchanged). `possiblyMore` alone does **not** trigger escalation: it signals
   *more hidden units*, which a tighter crop of the visible pile can't resolve — that is R6's reactive
   second-shot, not R5's job.
5. **The candidate sub-catalog for a crop = the detected SKU + its `alternates` + its confusion-group
   siblings.** For a `sku: null` detection there is no anchor, so the sub-catalog is the **full active
   catalog** (the crop is a focused "identify this one item" call). The sub-catalog is always a subset
   of the active catalog, so pass 2 can never introduce a SKU the catalog doesn't have (SCAN-4 holds).
6. **Merge rule: pass 2 overwrites identity/confidence for the escalated instance; pass 1 keeps
   everything else.** The verifier's best result for a crop replaces that instance's `sku`,
   `confidence`, and `alternates`; the **box stays from pass 1** (it is the location in the original
   frame). `occluded`/`possiblyMore` are carried from pass 1 unless pass 2 contradicts them. If pass 2
   returns nothing usable (empty, error, all SKUs outside the sub-catalog), the **pass-1 detection is
   kept** verbatim — escalation can only improve or no-op, never lose a detection (SCAN-7, SCAN-9).
7. **Latency stays within SCAN-3 by running crops in parallel and capping their count.** Escalated
   crops are verified concurrently (`async`/`awaitAll` on the recognize calls); the number of
   escalations per scan is capped (`MAX_ESCALATIONS`, default TBD ~6, the most-uncertain first) so a
   pathological all-uncertain scan can't fan out into 10+ serial round-trips. Pass 1 plus one parallel
   wave of small crops keeps the wall-clock near a two-call budget.
8. **`priceDraft`, `ScanCounter`, and all of `ui/` are unchanged.** The decorator returns a normal
   `List<RecognizedItem>` with the same shape; pricing groups and flags it exactly as today (R2/R4
   aggregation + forced flag still apply to the *corrected* identities). No new `ScanStage`, no Draft
   Review change — the two passes are invisible above the seam. The evidence UI (R7) and escalation
   telemetry (X-3/R10) are separate later slices.
9. **The `MAX_EDGE_PX = 1536` single-constant experiment is a companion, not the core of R5.** The
   idea doc pairs two-pass with "also simply test raising `MAX_EDGE_PX` to 1536." That is a one-line
   change to `AndroidImageDownscaler`, measurable independently on the R9 harness, and competes with
   (not composes into) two-pass. R5's core deliverable is the decorator; the constant bump is recorded
   as an alternative lever and an Open Question, not built as part of this slice.

## Scope

**In:** an optional `CapturedImage.original` field + `AndroidImageDownscaler` populating it; a new
`ImageCropper` domain port + `AndroidImageCropper` impl (normalized-box crop with padding, JPEG
re-encode, fail-soft); a `TwoPassRecognizer(primary, verifier, cropper)` `Recognizer` decorator
(escalation selection, sub-catalog assembly, parallel capped verify, merge); DI wiring so the
configured recognizer is wrapped in `TwoPassRecognizer` when the cloud impl is active; tests for the
cropper, the decorator's selection/merge/fallback/cap logic, and the no-escalation pass-through.

**Out (non-goals):** drawing boxes / numbered detections / a "verify" affordance in Draft Review
(R7); a new `ScanStage` or any `ui/` change; capture-side quality gates and the reactive
second-shot for `possiblyMore` stacks (R6); escalating pass 2 to a **stronger model** and the
cheap-then-strong escalation policy (R8); the eval harness / golden set that *measures* R5's gain
(R9); escalation/cost telemetry fields (X-3/R10 — `ScanTelemetryEvent` is unchanged); the
`MAX_EDGE_PX = 1536` experiment (Assumption 9 — recorded, not built); any `Recognizer` signature
change, `DraftLine`/`DraftReceipt`/Room change, or pricing-math change.

## Design

### Where full-res comes from (the central change)

```kotlin
// domain/model/CapturedImage.kt — additive, defaulted; existing constructors compile.
public data class CapturedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val original: CapturedImage? = null,   // R5: pre-downscale full-res source, when retained
) { /* equals/hashCode: original excluded so existing image-equality assertions hold */ }
```

`AndroidImageDownscaler.downscale` already decodes the source bitmap; it now also constructs the
full-res `CapturedImage` (from the input `jpegBytes` + the decoded source dimensions) and sets it as
`original` on the returned downscaled image. Memory cost: one extra in-flight JPEG (a few MB) for the
duration of one scan — acceptable for a single capture; released when the scan completes.

### Crop port (`domain/image/ImageCropper.kt` + `data/image/AndroidImageCropper.kt`)

```kotlin
// domain — pure Kotlin, no Android types (mirrors ImageDownscaler).
public interface ImageCropper {
    /** Crop the normalized [box] region out of [image] and return it re-encoded. */
    public suspend fun crop(image: CapturedImage, box: BoundingBox): CapturedImage?
}
```

`AndroidImageCropper` decodes `image.bytes`, converts the normalized box to pixel bounds (clamped to
the image, expanded by a small padding fraction so labels at the box edge survive), crops, and
re-encodes JPEG. Returns `null` on any decode/crop failure so the decorator can fall back.

### The decorator (`data/recognizer/TwoPassRecognizer.kt`)

```kotlin
public class TwoPassRecognizer(
    private val primary: Recognizer,
    private val verifier: Recognizer,
    private val cropper: ImageCropper,
    private val confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
    private val maxEscalations: Int = MAX_ESCALATIONS,
) : Recognizer {
    override suspend fun recognize(image, catalog): Result<List<RecognizedItem>> {
        val pass1 = primary.recognize(image, catalog).getOrElse { return Result.failure(it) }
        val source = image.original ?: image                      // full-res if retained
        val activeGroups = activeConfusionGroups(catalog)          // tag -> size ≥ 2 (R4)

        // Pick uncertain detections that have a box; cap to the most-uncertain N.
        val toEscalate = pass1.filter { it.needsVerify(activeGroups) && it.boundingBox != null }
            .sortedBy { it.confidence }.take(maxEscalations)

        // Verify each in parallel; merge results back by identity.
        val verified = toEscalate.parallelMap { d ->
            val crop = cropper.crop(source, d.boundingBox!!) ?: return@parallelMap d
            val sub = candidateSubCatalog(d, catalog, activeGroups)  // detected + alternates + siblings
            val best = verifier.recognize(crop, sub).getOrNull()?.bestFor(crop) ?: return@parallelMap d
            d.copy(sku = best.sku, confidence = best.confidence, alternates = best.alternates)
        }
        return Result.success(pass1.mergeReplacing(verified))
    }
}
```

- **Selection** (`needsVerify`): `confidence < threshold || occluded || sku == null || skuInActiveGroup`.
  `possiblyMore` is intentionally **not** a trigger (Assumption 4).
- **Sub-catalog** (`candidateSubCatalog`): detected SKU + `alternates` + same-confusion-group SKUs;
  for `sku == null`, the full active catalog.
- **Merge**: only escalated instances are replaced (Assumption 6); everything else is pass-1 verbatim.
  Output order is preserved so `priceDraft` grouping stays deterministic.
- **Fallbacks**: pass-1 failure ⇒ whole scan fails (today's behavior). Any per-crop failure (no box,
  crop `null`, verifier error/empty) ⇒ keep that pass-1 detection. Pass 2 never fails the scan.

### DI wiring (`di/RecognizerModule.kt`, `di/AppModule.kt`)

`provideRecognizer` wraps the active cloud recognizer in `TwoPassRecognizer` (primary = verifier =
the OpenRouter impl in R5; `ImageCropper` injected). The stub path (no API key) stays a bare
`StubRecognizer` — two-pass only matters for the real model. The `Recognizer` consumed by
`ScanCounter` is unchanged in *type*; only its construction changes (X-2: one swap point).

### What does NOT change

`Recognizer` interface signature, `RecognizedItem`/`BoundingBox` shape, `priceDraft` and all pricing
math (X-1), `DraftLine`/`DraftReceipt`/Room schema, `ScanCounter`, `ScanStage`, `DraftViewModel` and
all of `ui/`, `ScanTelemetryEvent`, the R1 reference-photo assembly, R2 aggregation, R3 prompt, and
R4 forced flag. `FakeRecognizer`/`StubRecognizer` keep compiling and driving the flow (SCAN-10).

## Tech Stack

Inherited from `docs/spec/SPEC.md`. No new dependencies — cropping uses the same `android.graphics`
Bitmap APIs the downscaler/thumbnail store already use; parallelism uses the coroutines already
present.

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
  domain/model/CapturedImage.kt              → add optional `original` (defaulted); equals/hashCode note
  domain/image/ImageCropper.kt               → NEW pure-Kotlin crop port
  data/image/AndroidImageDownscaler.kt       → set `original` on the returned image
  data/image/AndroidImageCropper.kt          → NEW Bitmap crop impl (padding, JPEG, fail-soft)
  data/recognizer/TwoPassRecognizer.kt       → NEW Recognizer decorator (select/sub-catalog/verify/merge)
  data/recognizer/OpenRouterDefaults.kt      → MAX_ESCALATIONS (+ optional padding/threshold consts)
  di/RecognizerModule.kt                     → wrap cloud recognizer in TwoPassRecognizer; provide verifier
  di/AppModule.kt                            → provide ImageCropper binding
app/src/test/java/com/cashierapp/photocheckout/
  recognizer/TwoPassRecognizerTest.kt        → NEW selection/merge/fallback/cap with FakeRecognizer + fake cropper
app/src/androidTest/java/com/cashierapp/photocheckout/
  data/image/AndroidImageCropperTest.kt      → NEW box→pixel crop, padding, malformed/fail-soft
docs/spec/SPEC.md                            → note the two-pass strategy after approval (if needed)
```

## Code Style

Inherited (ktlint, explicit public API, KDoc citing criteria IDs, vendor/model detail only in
`data/recognizer/`, integer-minor-unit money). The decorator stays pure-orchestration and reads no
prices:

```kotlin
// data/recognizer/TwoPassRecognizer.kt — escalate only uncertain, boxed detections; never lose one.
private fun RecognizedItem.needsVerify(activeGroups: Set<String>): Boolean =
    confidence < confidenceThreshold ||
        occluded ||
        sku == null ||
        (sku != null && catalogGroupOf(sku) in activeGroups)
```

## Testing Strategy

Decorator logic is pure orchestration ⇒ **JVM unit (L1)** with `FakeRecognizer` for both passes and a
fake `ImageCropper`; the real Bitmap crop is **instrumented (L3)**. No live model in CI (L2/L6).

- **`TwoPassRecognizerTest` (JVM unit) — the riskiest logic, cover exhaustively:**
  - **No escalation:** all pass-1 detections confident, identified, no occlusion, no confusion group
    ⇒ verifier is **never called**, output equals pass 1 exactly (the cheap common case).
  - **Selection:** a low-confidence, an `occluded`, a `sku: null`, and a confusion-group detection are
    each escalated; a confident plain detection and a `possiblyMore`-only detection are **not**.
  - **No box ⇒ no escalation:** an uncertain detection with `boundingBox == null` is kept verbatim
    (nowhere to crop).
  - **Sub-catalog:** the verifier receives detected SKU + `alternates` + confusion-group siblings (and
    the full active catalog for `sku: null`) — assert via the fake verifier's `lastCatalog`.
  - **Merge:** pass 2's corrected SKU/confidence replaces only that instance; the box and all other
    detections are untouched; output order preserved.
  - **Fallbacks:** crop returns `null`, verifier returns empty / error / an off-catalog SKU ⇒ pass-1
    detection kept; a pass-1 **failure** ⇒ scan fails with no pass 2 (SCAN-9).
  - **Cap:** with more uncertain detections than `MAX_ESCALATIONS`, only the N most-uncertain are
    verified; the rest keep pass-1 values.
  - **Full-res routing:** when `image.original` is set, the cropper receives the original; when absent,
    it receives the downscaled image (assert the fake cropper's input).
- **`AndroidImageCropperTest` (androidTest):** a known box maps to the expected pixel rectangle
  (within padding tolerance); padding expands without exceeding image bounds; a malformed/empty box or
  undecodable bytes returns `null` (fail-soft).
- **`AndroidImageDownscalerTest` (androidTest, if present):** the returned downscaled image carries an
  `original` whose dimensions match the source and whose bytes decode.
- **Regression:** `ScanCounterTest`, `PricingTest`, `DraftViewModelTest`, `OpenRouterRecognizerTest`,
  `FakeRecognizer` flows (SCAN-10), and the draft/E2E suites pass untouched — proof the seam held, the
  UI never saw two passes, and pricing is unchanged (X-1, X-2). `SCAN-INT-2` (downscaled image reaches
  the recognizer) still holds — the recognizer now *also* gets `original`, but the consumed image is
  still the reduced one.
- **Not in CI (R9):** whether two-pass actually lowers look-alike confusion / count error and whether
  the latency stays in budget on the live model — measured on the golden set, informed but not gated
  here. Smoke numbers (escalation count, wall-clock for a few captures) recorded in the PR.

## Boundaries

Inherited from `docs/spec/SPEC.md`, plus R5-specific:

- **Always:** keep the second pass behind the `Recognizer` seam (the decorator *is* a `Recognizer`);
  crop from full-res when available and degrade to the downscaled image otherwise; restrict every pass-2
  sub-catalog to a subset of the active catalog (SCAN-4); keep prices strictly from the catalog (X-1);
  preserve every pass-1 detection on any per-crop failure (SCAN-7, SCAN-9); run crops in parallel and
  cap them (SCAN-3).
- **Ask first:** escalating pass 2 to a **different/stronger model** (that is R8); adding a new
  `ScanStage` or any Draft Review affordance (R6/R7); extending `ScanTelemetryEvent` with escalation
  fields (X-3/R10); changing the `Recognizer` or `priceDraft` signatures; raising `MAX_EDGE_PX`
  (Assumption 9 — a separate measured experiment).
- **Never:** let a crop or pass-2 result influence price/total/tax; introduce a SKU outside the active
  catalog via pass 2; drop a detection because its crop or verify failed; fail a scan on a pass-2
  error; block the scan on serial crop round-trips.

## Acceptance Criteria

- **R5-1:** A scan whose pass-1 detections are all confident, identified, un-occluded, and outside any
  active confusion group runs **exactly one** recognizer call; the draft is byte-identical to today's.
- **R5-2:** `CapturedImage` carries an optional `original`; `AndroidImageDownscaler` populates it with
  the pre-downscale source; the `Recognizer` interface signature is unchanged.
- **R5-3:** `ImageCropper`/`AndroidImageCropper` crop a normalized `BoundingBox` (with padding) out of
  an image and re-encode it; a malformed box or undecodable source returns `null` (fail-soft).
- **R5-4:** `TwoPassRecognizer` escalates exactly the detections that are low-confidence, `occluded`,
  `sku: null`, or in an active confusion group **and** have a box; it never escalates a `possiblyMore`-only
  or box-less detection; escalations are capped at `MAX_ESCALATIONS`, most-uncertain first.
- **R5-5:** Each escalation crops from full-res (or the downscaled image if `original` is absent) and
  calls the verifier with a sub-catalog of detected SKU + `alternates` + confusion-group siblings (full
  active catalog for `sku: null`); pass 2 cannot introduce an off-catalog SKU.
- **R5-6:** Pass 2 overwrites only the escalated instance's `sku`/`confidence`/`alternates` (box kept
  from pass 1); a crop/verify failure keeps the pass-1 detection; a pass-1 failure fails the scan with
  no pass 2 (SCAN-9). No detection is ever dropped (SCAN-7).
- **R5-7:** `priceDraft`, `DraftLine`/`DraftReceipt`/Room, `ScanCounter`, `ScanStage`, and all `ui/`
  code are unchanged (X-1, X-2); `FakeRecognizer` flows (SCAN-10) and the draft/E2E suites pass without
  edits; `StubRecognizer` still drives the no-key path with no second pass.

## Success Criteria

- All acceptance criteria pass; new + existing unit and instrumented suites green; `ktlintCheck` and
  `detekt` clean.
- A manual device check: scan a pair of look-alike products and an occluded item, confirm (a) only the
  uncertain detections trigger a second crop request (visible in logs), (b) at least one corrected
  identity differs from pass 1, and (c) wall-clock stays within the SCAN-3 budget — a smoke check, not
  a benchmark.
- Recorded in the PR: escalation count and added latency/cost for a few representative captures against
  the SCAN-3 budget, and any qualitative change in look-alike/occlusion correctness (the signal the R9
  harness will formalize).

## Open Questions

- **`MAX_EDGE_PX = 1536` vs. two-pass (Assumption 9).** Should the cheaper single-constant resolution
  bump be tried and measured **before** committing to the decorator? They target the same problem;
  the R9 harness is the right arbiter. Default: build the decorator (the larger ceiling), record the
  constant bump as a follow-up experiment.
- **`MAX_ESCALATIONS` and crop padding values.** Defaults (~6 escalations, ~8–12 % padding) are
  guesses to tune on the harness; firm them up with R9 latency/accuracy data.
- **Verifier prompt.** R5 reuses the pass-1 enumeration prompt on the crop (a tiny catalog naturally
  focuses it). Is a dedicated "identify this single item among these candidates" verify-prompt worth a
  second prompt mode in `OpenRouterRecognizer`, or does reuse suffice until R8/R9 say otherwise?
- **Memory ceiling.** Carrying the full-res JPEG through one scan is a few MB; confirm it's acceptable
  on low-end target devices (minSdk 26) or cap the retained original's resolution.
- Next phases: **Plan → Tasks → Implement**, to be captured in
  `docs/plan/scan-r5-two-pass-crop-escalation.plan.md` after this spec is approved.
```
