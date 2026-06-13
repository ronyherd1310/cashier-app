# Implementation Plan: R2 — Per-Instance Detections

> Plan for `docs/spec/scan-r2-per-instance-detections.md` (Approved, 2026-06-13).
> Sequenced into thin, independently-testable slices per the increment cycle:
> implement → test → verify → commit → next slice.

## Key ordering insight

**Aggregation must land before the prompt asks for per-instance entries.** Until
`priceDraft` groups by SKU, a per-instance response (N entries, `quantity=1` each) would
emit N draft lines for one SKU and break every SKU-keyed UI mutation
(`DraftViewModel.updateLine/removeLine/addCatalogItem`). So aggregation is both the
riskiest logic *and* the dependency gate — it goes first.

**Branch:** `implement/scan-r2-per-instance-detections` (off `main`)
**Pre-req each run:** `export JAVA_HOME=<java-21>` before `./gradlew` (Gradle needs JDK 17+).

---

## Slice 1 — SKU aggregation in `priceDraft` (load-bearing, risk-first)

The central change (Assumption 1). Pure domain, JVM-testable in isolation, fully
backward-compatible.

- **File:** `domain/pricing/Pricing.kt`
- **Change:** Replace the per-detection loop with `recognized.groupBy { it.sku }`
  (preserving first-appearance order — `groupBy` does), then per group:
  `quantity = sum(quantities).coerceAtLeast(1)`, `confidence = minOf { it.confidence }`,
  route to `DraftLine` or `UnidentifiedItem` exactly as today. Prices still strictly from
  `catalog`.
- **Tests** (`PricingTest.kt`, JVM unit):
  - 3×`quantity=1` same SKU → one line `quantity=3`.
  - Mixed identified + unidentified per-instance entries → grouped correctly both sides.
  - Min-confidence (0.5 + 0.9 + 0.9 → line 0.5, `lowConfidence=true`; all ≥0.6 → not flagged).
  - Legacy aggregate (`quantity=2`, no box) → byte-identical line to today.
  - Money: `lineTotalMinor == unitPriceMinor * summed qty`, integer minor units.
  - Deterministic first-appearance SKU ordering.
  - **All existing `PricingTest` cases stay green.**
- **Verify:** `./gradlew app:testDebugUnitTest`
- **Acceptance:** R2-3, R2-4, R2-5
- **Commit:** `feat(pricing): group detections by SKU into one draft line (R2)`

## Slice 2 — DTO `box` field + parse/validate + populate `boundingBox`

Recognizer learns to *read* boxes. Prompt unchanged here, so this is safe even if no model
volunteers a box (field defaults null).

- **Files:** `data/recognizer/dto/ChatCompletionResponse.kt`, `data/recognizer/OpenRouterRecognizer.kt`
- **Change:** Add `val box: List<Float>? = null` to `RecognizedItemDto`. Add private
  `List<Float>.toBoundingBoxOrNull()` — exactly 4 finite values, each `in 0f..1f`,
  `left < right`, `top < bottom`, else `null` (never throws). Wire
  `boundingBox = dto.box?.toBoundingBoxOrNull()` into the `recognize` mapping.
- **Tests** (`OpenRouterRecognizerTest.kt`, androidTest/MockWebServer):
  - Two same-SKU entries with valid boxes → two `RecognizedItem`s, corners correct.
  - Malformed box (len≠4 / value>1 / `left>=right`) → detection present with
    `boundingBox=null`, **scan succeeds**.
  - Existing tests (HTTP error, timeout, missing key, aggregate parse) stay green.
- **Verify:** `./gradlew app:connectedDebugAndroidTest` (needs device/emulator)
- **Acceptance:** R2-2, R2-7 (no scan failure on bad box)
- **Commit:** `feat(recognizer): parse and populate normalized bounding boxes (R2)`

## Slice 3 — `PROMPT` rewrite to per-instance enumeration (behavior flip)

With aggregation (S1) and box parsing (S2) in place, flipping the request is now safe
end-to-end.

- **File:** `data/recognizer/OpenRouterRecognizer.kt` (`PROMPT`)
- **Change:** Swap the "how many of each" wording for the spec's per-instance wording
  (§Prompt delta): one entry per physical instance, `box` as `[left,top,right,bottom]`
  0..1 top-left origin, matching SKU, confidence; keep "ONLY catalog SKUs / never invent /
  never include prices" and R1 reference-photo wording. `responseFormat` stays
  `json_object`.
- **Tests:** assert outgoing prompt requests per-instance + a box; assert R1
  reference-photo assembly unchanged (existing R1 tests green).
- **Verify:** `./gradlew app:connectedDebugAndroidTest`
- **Acceptance:** R2-1, R2-7
- **Commit:** `feat(recognizer): request one entry per instance with a box (R2)`

## Slice 4 — Regression gate, docs, manual smoke, PR metrics

No new behavior — proof the seam held + close-out.

- **Regression:** `ScanCounterTest`, `DraftViewModelTest`, draft/E2E suites pass untouched
  (R2-6). Full `./gradlew app:testDebugUnitTest app:connectedDebugAndroidTest`, then
  `app:ktlintFormat && app:ktlintCheck` and `app:detekt` clean.
- **Docs:** amend `docs/spec/SPEC.md` X-2 / catalog-context notes.
- **Manual smoke:** device check with a 3–4 unit stack → request prompts per-instance,
  draft shows right quantity on one line.
- **PR body:** record boxes-returned vs true count on a few captures + any request/latency
  delta (Success Criteria; pre-R9 signal).
- **Commits:** `docs: mark R2 per-instance detections implemented` (+ separate commit for
  the SPEC.md amend per Rule 1: one logical thing per commit).

---

## Acceptance coverage

| Acceptance | Slice |
|---|---|
| R2-1 prompt + DTO box | S2 (DTO) + S3 (prompt) |
| R2-2 populate/degrade box | S2 |
| R2-3 one line per SKU | S1 |
| R2-4 min-confidence flag | S1 |
| R2-5 legacy backward-compat | S1 |
| R2-6 seam/UI untouched | S4 |
| R2-7 no R1 regress, no scan fail | S2 + S3 |

Each slice leaves the build green and is independently revertible.
