# Implementation Plan: R5 — Two-Pass Recognition (detect, then zoom-and-verify)

> Plan for `docs/spec/scan-r5-two-pass-crop-escalation.md` (Draft, 2026-06-13).
> Sequenced into thin, independently-testable slices per the increment cycle:
> implement → test → verify → commit → next slice.
> Depends on R2 (boxes), R3 (`occluded`/`sku: null`/`alternates`), R4 (`confusionGroup`) — all on `main`.

## Key ordering insight

**The decorator is the deliverable, but it cannot land first — it has two prerequisites that are
each independently testable.** R5 plumbs full-res bytes to the seam (Slice 1), adds a crop port
(Slice 2), then composes both inside the `TwoPassRecognizer` decorator (Slice 3), and finally wires
DI (Slice 4). Slices 1 and 2 are pure foundation with zero behavior change above the seam; Slice 3
is where escalation behavior appears but stays invisible to `ui/` because the decorator *is* a
`Recognizer`.

**Slices 1 and 2 are independent of each other and of Slice 3's logic** — Slice 1 touches
`CapturedImage` + the downscaler; Slice 2 adds a brand-new port + impl. They can be built in either
order (or in parallel). **Slice 3 depends on both** (it crops `image.original` via the cropper) and
is the riskiest, most-tested unit. **Slice 4 depends on Slice 3** (it constructs the decorator).

**Backward-compatibility is structural:** `CapturedImage.original` is defaulted `null` and excluded
from `equals`/`hashCode`, so every existing image-equality assertion and fixture compiles and passes
unchanged. The decorator only escalates when doubt signals fire *and* a box exists; an all-confident
scan runs exactly one primary call (R5-1) — today's path, byte-for-byte.

**Branch:** `implement/scan-r5-two-pass-crop-escalation` (off `main`)
**Pre-req each run:** `export JAVA_HOME=<java-21>` before `./gradlew` (Gradle needs JDK 17+).

> ⚠️ **Out of scope (spec §Scope, Boundaries):** no `ui/`/`ScanStage`/Draft Review change; no
> stronger-model verifier (R8); no `MAX_EDGE_PX = 1536` bump (Assumption 9 — record, don't build);
> no `Recognizer`/`priceDraft` signature change; no `ScanTelemetryEvent` field (X-3/R10); no
> `possiblyMore` reactive second-shot (R6). `possiblyMore` is **not** an escalation trigger.

---

## Slice 1 — Full-res routing: `CapturedImage.original` + downscaler populates it (risk-first)

The load-bearing data plumbing (spec Assumption 1, §"Where full-res comes from"). Additive and
defaulted; no recognizer, pricing, or `ui/` change. After this slice the downscaled image *carries*
its full-res source, but nothing reads it yet.

- **Files:** `domain/model/CapturedImage.kt`, `data/image/AndroidImageDownscaler.kt`
- **Change:**
  - `CapturedImage`: add `val original: CapturedImage? = null` (defaulted — every existing
    constructor/fixture compiles unchanged). **Exclude `original` from `equals`/`hashCode`** so
    existing image-equality assertions and `SCAN-INT-2` hold.
  - `AndroidImageDownscaler.downscale`: build the full-res `CapturedImage` from the input
    `jpegBytes` + decoded `source.width`/`source.height` and set it as `original` on the returned
    downscaled image. Read source dimensions **before** `source.recycle()`. When decode fails (the
    early `width=0,height=0` return) leave `original = null` — degrade, never crash. When the image
    is already ≤ `MAX_EDGE_PX` (re-encoded but not scaled), still set `original` to the pre-encode
    source bytes/dims.
- **Tests** (`AndroidImageDownscalerTest.kt`, androidTest — extend if present, else add):
  - The returned downscaled image carries a non-null `original` whose dimensions match the source
    JPEG and whose bytes decode to a bitmap.
  - A small (≤ 1024 px) source still yields a populated `original`.
  - Undecodable bytes → `original == null` (fail-soft), no crash.
  - Existing downscaler/image-equality tests stay green (proves the `equals` exclusion works).
- **Verify:** `./gradlew app:connectedDebugAndroidTest` (Bitmap decode needs a device/emulator);
  `app:testDebugUnitTest` for the `CapturedImage` compile/regression.
- **Acceptance:** R5-2
- **Commit:** `feat(image): retain pre-downscale full-res source on CapturedImage (R5)`

## Slice 2 — Crop port: `ImageCropper` + `AndroidImageCropper`

A new domain port mirroring `ImageDownscaler` (spec Assumption 2, §"Crop port"). Pure-Kotlin
contract in `domain/`, Bitmap impl in `data/`. Independent of Slices 1 and 3.

- **Files:** `domain/image/ImageCropper.kt` (NEW), `data/image/AndroidImageCropper.kt` (NEW),
  `data/recognizer/OpenRouterDefaults.kt` (or a new `data/image` consts file) for the padding
  fraction, `di/AppModule.kt` (binding — see note).
- **Change:**
  - `ImageCropper`: `public suspend fun crop(image: CapturedImage, box: BoundingBox): CapturedImage?`
    — pure Kotlin, no Android types. Returns `null` on any failure.
  - `AndroidImageCropper` (`@Inject constructor`): decode `image.bytes`; map the normalized
    `BoundingBox` (0f..1f, top-left origin) to pixel bounds; expand by a small padding fraction
    (default ~10%, a named const) so labels at the box edge survive; **clamp to image bounds**;
    `Bitmap.createBitmap(...)` the region; re-encode JPEG; return a `CapturedImage`. Run decode/crop
    off the main thread (`withContext(Dispatchers.Default)`, matching the downscaler). Return `null`
    on undecodable bytes, a degenerate box (`left >= right || top >= bottom`), or a zero-area clamped
    rect.
  - `AppModule`: add `provideImageCropper(cropper: AndroidImageCropper): ImageCropper = cropper`
    (mirrors `provideImageDownscaler`).
- **Tests** (`AndroidImageCropperTest.kt`, androidTest):
  - A known normalized box maps to the expected pixel rectangle within padding tolerance (assert the
    cropped image's width/height ≈ box·source ± padding).
  - Padding expands the crop without exceeding image bounds (a box at the edge does not throw / clamps).
  - A malformed/degenerate box returns `null`; undecodable bytes return `null` (fail-soft).
  - The crop re-encodes to a decodable JPEG (`mimeType == "image/jpeg"`, bytes decode).
- **Verify:** `./gradlew app:connectedDebugAndroidTest`
- **Acceptance:** R5-3
- **Commit:** `feat(image): add ImageCropper port + Android Bitmap crop impl (R5)`

## Slice 3 — `TwoPassRecognizer` decorator: select / sub-catalog / parallel verify / merge

The core deliverable and the riskiest logic — exhaustive JVM unit coverage (spec §"The decorator",
Testing Strategy). Pure orchestration: reads no prices, calls two `Recognizer`s and one
`ImageCropper`, returns a normal `List<RecognizedItem>`.

- **Files:** `data/recognizer/TwoPassRecognizer.kt` (NEW),
  `data/recognizer/OpenRouterDefaults.kt` (add `MAX_ESCALATIONS`, default ~6; confidence threshold
  reuses `CONFIDENCE_THRESHOLD` from `domain/recognizer/Recognizer.kt`).
- **Change** — `TwoPassRecognizer(primary, verifier, cropper, confidenceThreshold = CONFIDENCE_THRESHOLD,
  maxEscalations = MAX_ESCALATIONS) : Recognizer`:
  - **Pass 1:** `primary.recognize(image, catalog)`; on failure return `Result.failure` with **no
    pass 2** (today's behavior, SCAN-9).
  - **Source:** `image.original ?: image` (full-res when retained, else the downscaled image).
  - **Active confusion groups:** compute from `catalog` — group tags non-blank and shared by ≥2
    **active** members (reuse the exact rule already in `OpenRouterRecognizer.catalogContext` /
    `Pricing`; build a `sku → confusionGroup` map for `catalogGroupOf`).
  - **Selection** (`needsVerify`): `confidence < confidenceThreshold || occluded || sku == null ||
    (sku != null && catalogGroupOf(sku) in activeGroups)` **and** `boundingBox != null`.
    `possiblyMore` is **not** a trigger (Assumption 4). Sort selected by `confidence` ascending,
    `take(maxEscalations)` (most-uncertain first, cap).
  - **Sub-catalog** (`candidateSubCatalog`): detected SKU + its `alternates` + same-confusion-group
    siblings, intersected with the active catalog; for `sku == null`, the **full active catalog**.
    Always a subset ⇒ pass 2 can never introduce an off-catalog SKU (SCAN-4).
  - **Parallel verify:** `coroutineScope { ... async { ... }.awaitAll() }` (no `parallelMap` helper
    exists — introduce inline). Per crop: `cropper.crop(source, box) ?: return@async d`; then
    `verifier.recognize(crop, sub).getOrNull()?.bestFor(crop) ?: return@async d`; on a usable best,
    `d.copy(sku = best.sku, confidence = best.confidence, alternates = best.alternates)`.
    `bestFor` picks the verifier's single best detection for the crop (highest confidence; ignore
    results whose `sku` is outside `sub`).
  - **Merge:** replace only escalated instances by identity/position; **box, `occluded`,
    `possiblyMore`, quantity, and all non-escalated detections stay from pass 1**; preserve output
    order so `priceDraft` grouping stays deterministic. A crop/verifier failure keeps the pass-1
    detection verbatim (SCAN-7).
- **Test fake:** the standard `FakeRecognizer` records only the *last* image/catalog, but the
  decorator calls the verifier once per crop in parallel. Add a small **recording fake verifier**
  (test-only, e.g. `RecordingRecognizer`) that captures every `(image, catalog)` call and returns a
  per-input canned result, plus a fake `ImageCropper` that records its input image and returns a
  marker `CapturedImage` (or `null` to exercise fallback). Keep `FakeRecognizer` unchanged for the
  primary.
- **Tests** (`recognizer/TwoPassRecognizerTest.kt`, JVM unit — cover exhaustively):
  - **No escalation:** all pass-1 detections confident, identified, un-occluded, ungrouped ⇒ verifier
    **never called**; output `==` pass 1 exactly (cheap common case, R5-1).
  - **Selection:** a low-confidence, an `occluded`, a `sku: null`, and a confusion-group detection
    are each escalated; a confident plain detection and a `possiblyMore`-only detection are not.
  - **No box ⇒ no escalation:** an uncertain detection with `boundingBox == null` kept verbatim.
  - **Sub-catalog:** verifier receives detected SKU + `alternates` + group siblings (full active
    catalog for `sku: null`) — assert via the recording verifier's captured catalog per call.
  - **Merge:** pass 2's corrected SKU/confidence/alternates replaces only that instance; box and all
    other detections untouched; output order preserved.
  - **Fallbacks:** crop `null`, verifier empty / error / off-catalog-only SKU ⇒ pass-1 detection
    kept; a pass-1 **failure** ⇒ scan fails with no pass 2 (SCAN-9).
  - **Cap:** more uncertain detections than `MAX_ESCALATIONS` ⇒ only the N most-uncertain verified;
    the rest keep pass-1 values.
  - **Full-res routing:** `image.original` set ⇒ cropper receives `original`; absent ⇒ cropper
    receives the downscaled `image` (assert the fake cropper's recorded input).
- **Verify:** `./gradlew app:testDebugUnitTest` (JVM-only, fast); `app:ktlintCheck`/`app:detekt` on
  the new file.
- **Acceptance:** R5-1, R5-4, R5-5, R5-6
- **Commit:** `feat(recognizer): add TwoPassRecognizer crop-and-verify decorator (R5)`

## Slice 4 — DI wiring: wrap the cloud recognizer

Make the decorator live for the real model only (spec §"DI wiring", Assumption 3). One swap point
(X-2); no consumer type change.

- **Files:** `di/RecognizerModule.kt` (`provideRecognizer`), `di/AppModule.kt` (cropper binding from
  Slice 2 — land it here if not already in Slice 2).
- **Change:** in `provideRecognizer`, when `config.hasApiKey()` build
  `TwoPassRecognizer(primary = openRouter.get(), verifier = openRouter.get(), cropper = ...)`
  (primary = verifier = the same OpenRouter impl / same model in R5 — stronger-model is R8). The
  no-key path stays a bare `StubRecognizer` (no second pass). Inject `ImageCropper`. The `Recognizer`
  consumed by `ScanCounter` is unchanged in **type**; only construction changes.
- **Tests:** DI is exercised by existing wiring + the regression suite (Slice 5). No new unit test
  for the Hilt graph; confirm the app compiles and the stub path is untouched.
- **Verify:** `./gradlew app:testDebugUnitTest assembleDebug`
- **Acceptance:** R5-7 (construction half — stub path unchanged, type unchanged)
- **Commit:** `feat(di): wrap cloud recognizer in TwoPassRecognizer (R5)`

## Slice 5 — Regression gate, docs, manual smoke, PR metrics

No new behavior — proof the seam held + close-out (spec §Testing Strategy, Success Criteria).

- **Regression (R5-7):** `ScanCounterTest`, `PricingTest`, `DraftViewModelTest`,
  `OpenRouterRecognizerTest`, `FakeRecognizer` flows (SCAN-10), and the draft/E2E suites pass
  **untouched** — proof the UI never saw two passes and pricing is unchanged (X-1, X-2). `SCAN-INT-2`
  (downscaled image reaches the recognizer) still holds — the recognizer now *also* gets `original`,
  but the consumed image is still the reduced one. Full
  `./gradlew app:testDebugUnitTest app:connectedDebugAndroidTest`, then
  `app:ktlintFormat && app:ktlintCheck` and `app:detekt` clean.
- **Docs:** note the two-pass strategy in `docs/spec/SPEC.md` if needed; mark the R5 spec implemented.
- **Manual smoke (device):** scan a pair of look-alike products and an occluded item; confirm via
  logs (a) only the uncertain detections trigger a second crop/verify call, (b) at least one
  corrected identity differs from pass 1, and (c) wall-clock stays within the SCAN-3 budget (≤ ~5 s).
  A smoke check, not a benchmark.
- **PR body:** record escalation count and added latency/cost for a few representative captures
  against the SCAN-3 budget, plus any qualitative change in look-alike/occlusion correctness (the
  signal the R9 harness will formalize). Note the `MAX_EDGE_PX = 1536` lever as a recorded follow-up
  (Assumption 9), not built here.
- **Commits:** `docs: mark R5 two-pass crop escalation implemented` (+ a separate commit for any
  SPEC.md amend — one logical thing per commit).

---

## Acceptance coverage

| Acceptance | Slice |
|---|---|
| R5-1 all-confident scan = exactly one call, draft byte-identical | S3 |
| R5-2 `CapturedImage.original` populated by downscaler; signature unchanged | S1 |
| R5-3 `ImageCropper`/`AndroidImageCropper` crop + padding + fail-soft `null` | S2 |
| R5-4 escalate only uncertain+boxed; not `possiblyMore`/boxless; capped, most-uncertain first | S3 |
| R5-5 crop from full-res (or downscaled fallback); sub-catalog subset; no off-catalog SKU | S3 |
| R5-6 merge overwrites only escalated instance; failures keep pass-1; pass-1 failure fails scan | S3 |
| R5-7 `priceDraft`/Room/`ScanCounter`/`ScanStage`/`ui/` unchanged; stub path no second pass | S4, S5 |

Each slice leaves the build green and is independently revertible. Slice 3 is JVM-only (fast); Slices
1 and 2 need a device/emulator for the Bitmap decode/crop instrumented tests. Slices 1 and 2 are
independent and may be built in parallel; Slice 3 depends on both; Slice 4 depends on Slice 3.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `original` leaks into image-equality / blows up memory assertions | Med | Exclude from `equals`/`hashCode` (Slice 1); released when scan completes — one extra in-flight JPEG (Assumption 1, spec Open Question: confirm on minSdk 26). |
| Parallel verify fans out into many serial round-trips | Med | `coroutineScope`/`awaitAll` + `MAX_ESCALATIONS` cap, most-uncertain first (Assumption 7, SCAN-3). |
| A crop/verify failure drops a detection | High | Per-crop fail-soft: any `null`/error/empty/off-catalog ⇒ keep pass-1 verbatim; never lose a detection (SCAN-7, SCAN-9). |
| Pass 2 introduces an off-catalog SKU | High | Sub-catalog is always a subset of the active catalog; `bestFor` ignores off-`sub` results (SCAN-4). |

## Open questions (from spec — non-blocking, defer to R9)

- `MAX_ESCALATIONS` (~6) and crop padding (~8–12%) are guesses to tune on the R9 harness.
- Verifier prompt: R5 reuses the pass-1 enumeration prompt on the crop (a tiny sub-catalog focuses
  it). A dedicated "identify this single item among these candidates" prompt is deferred to R8/R9.
- `MAX_EDGE_PX = 1536` vs. two-pass — recorded as a follow-up experiment, arbitrated by R9.
- Memory ceiling of carrying full-res through one scan on low-end (minSdk 26) devices.
