# Code Review: PR #9 — Implement R5 Two-Pass Crop Escalation

## Summary

This PR implements the R5 two-pass recognition strategy: a first pass runs the cloud recognizer on the downscaled image; uncertain boxed detections (low confidence, occluded, null SKU, or confusion-group members) are cropped from the retained full-resolution source and re-verified against a narrowed sub-catalog in parallel, capped by `MAX_ESCALATIONS`. The implementation is a clean `Recognizer` decorator (`TwoPassRecognizer`) that composes behind the existing seam.

**Overall assessment: Strong implementation, well-tested, aligns with spec. A few actionable findings below.**

---

## Findings

### 1. `TwoPassRecognizer.kt` — Sub-catalog construction for `sku == null` includes **all** active items, but the spec says "full active catalog" for unidentified detections. This is correct per spec, but consider whether a smaller sub-catalog (e.g., only items with reference photos) would reduce cost/latency without losing accuracy for unidentified crops.

**File:** `app/src/main/java/com/cashierapp/photocheckout/data/recognizer/TwoPassRecognizer.kt:73-78`

```kotlin
private fun RecognizedItem.candidateSubCatalog(...): List<CatalogItem> {
    if (sku == null) {
        return activeCatalog  // Returns ALL active items
    }
    ...
}
```

**Suggestion:** Add a comment explaining why the full active catalog is used for `sku == null`, or consider filtering to items with `photos.isNotEmpty()` if reference photos are the primary signal for crop verification.

---

### 2. `TwoPassRecognizer.kt` — The `bestFor` function filters verifier results to SKUs present in the sub-catalog. However, if the verifier returns a recognized item with a SKU **not** in the sub-catalog (e.g., hallucination), it is silently dropped and the pass-1 detection is kept. This is correct per spec (SCAN-4), but consider logging/auditing such events for future tuning.

**File:** `app/src/main/java/com/cashierapp/photocheckout/data/recognizer/TwoPassRecognizer.kt:113-119`

```kotlin
private fun List<RecognizedItem>.bestFor(subCatalog: List<CatalogItem>): RecognizedItem? {
    val allowedSkus = subCatalog.mapTo(HashSet()) { item -> item.sku }
    return asSequence()
        .filter { item -> item.sku in allowedSkus }
        .maxByOrNull { item -> item.confidence }
}
```

**Suggestion:** Add a debug log when verifier returns items outside the allowed SKU set, to detect model drift or prompt issues.

---

### 3. `AndroidImageCropper.kt` — `CROP_PADDING_FRACTION` is a `Float` constant (0.1f = 10%). The padding is applied symmetrically in X and Y. For very wide or very tall boxes, this may over-pad one dimension. The spec mentions "~8–12% padding" as a heuristic to tune. Current implementation is correct but the constant name could be more specific.

**File:** `app/src/main/java/com/cashierapp/photocheckout/data/image/AndroidImageCropper.kt:51-52`

```kotlin
val padX = width * CROP_PADDING_FRACTION
val padY = height * CROP_PADDING_FRACTION
```

**Suggestion:** Rename to `CROP_PADDING_FRACTION_OF_BOX_DIMENSION` or add KDoc clarifying it's a fraction of the box's own width/height (not image dimensions).

---

### 4. `TwoPassRecognizer.kt` — The `needsVerify` function checks `sku in groupBySku`. The `groupBySku` map is built from active confusion groups with ≥2 members. This means a detection with `sku` in a confusion group of size 1 (i.e., the only active member of that group) is **not** escalated. This matches the spec ("active confusion group of ≥2 members"), but the comment on line 192 could be more explicit.

**File:** `app/src/main/java/com/cashierapp/photocheckout/data/recognizer/TwoPassRecognizer.kt:92-93`

```kotlin
private fun RecognizedItem.needsVerify(groupBySku: Map<String, String>): Boolean =
    confidence < confidenceThreshold ||
        occluded ||
        sku == null ||
        sku in groupBySku
```

**Suggestion:** Update the comment to explicitly state "active confusion group with ≥2 members" for clarity.

---

### 5. `CapturedImage.kt` — The `original` field is excluded from `equals`/`hashCode` (correct, per spec: "existing image-equality assertions hold"). However, the `copy()` function will copy `original` by default. If a test or downstream code does `image.copy(bytes = newBytes)`, the `original` reference is preserved, which may be unintended.

**File:** `app/src/main/java/com/cashierapp/photocheckout/domain/model/CapturedImage.kt`

**Suggestion:** Add a comment on the `original` field noting that `copy()` preserves it, and callers replacing bytes should explicitly pass `original = null` if they want to detach.

---

### 6. `RecognizerModule.kt` — The `TwoPassRecognizer` is constructed with `primary = cloudRecognizer` and `verifier = cloudRecognizer` (same instance). This is per spec (R5 uses same model for both passes; stronger verifier is R8). However, if the OpenRouter recognizer holds any request-scoped state, sharing the instance could cause issues. Current `OpenRouterRecognizer` appears stateless.

**File:** `app/src/main/java/com/cashierapp/photocheckout/di/RecognizerModule.kt:82-92`

**Suggestion:** Verify `OpenRouterRecognizer` is stateless (it appears to be). If it ever gains request-scoped state, use `Provider<OpenRouterRecognizer>` for both primary and verifier to get separate instances.

---

### 7. `AndroidImageDownscaler.kt` — When `BitmapFactory.decodeByteArray` returns null (corrupt input), the function returns a `CapturedImage` with width=0, height=0, and no `original`. This is existing behavior. The new test `corruptImageReturnsEmptyDimensionsWithoutOriginal` covers this. However, the downscaler now creates an `original` **before** checking if the decode succeeded. For corrupt input, `source` is null, but `original` was already constructed from `jpegBytes`. This `original` is then **not** attached to the result (correct). The extra allocation for corrupt input is negligible.

**File:** `app/src/main/java/com/cashierapp/photocheckout/data/image/AndroidImageDownscaler.kt:30-36`

---

### 8. Test Coverage — Excellent. `TwoPassRecognizerTest` covers: no escalation, selection logic, sub-catalog construction, merge behavior, fallbacks (crop null, verifier empty/error/off-catalog), pass-1 failure, cap behavior, and full-res routing. `AndroidImageCropperTest` covers normal crop, edge clamping, malformed boxes, undecodable images. `AndroidImageDownscalerTest` covers original retention for downscaled and non-downscaled images, and corrupt input. `ScanDomainModelsTest` adds equality regression for `original`.

**Minor gap:** No test for `TwoPassRecognizer` when `image.original` is present but the cropper returns null for the original (e.g., corrupt full-res bytes). The fallback to downscaled image is tested in `cropperUsesOriginalWhenPresentAndDownscaledFallbackOtherwise`, but only for successful crop. Consider adding a test where original crop fails and downscaled crop succeeds.

---

### 9. `OpenRouterDefaults.kt` — `MAX_ESCALATIONS = 6` and `CROP_PADDING_FRACTION = 0.1f` are hardcoded defaults. The spec marks these as "heuristics until the R9 harness tunes them." Consider making them configurable via `RecognizerConfig` (already has `confidenceThreshold`) so they can be tuned without code changes.

**File:** `app/src/main/java/com/cashierapp/photocheckout/data/recognizer/OpenRouterDefaults.kt:12-14`

```kotlin
public const val CROP_PADDING_FRACTION: Float = 0.1f
public const val MAX_ESCALATIONS: Int = 6
```

**Suggestion:** Add `cropPaddingFraction` and `maxEscalations` to `RecognizerConfig` with these as defaults, and plumb through `RecognizerModule`.

---

### 10. Spec Document — The spec status line still says "Draft — for review" at line 12, but the PR description says "Status: Implemented." Update the spec to "Implemented" and date.

**File:** `docs/spec/scan-r5-two-pass-crop-escalation.md:12`

---

## Positive Notes

- Clean decorator pattern — `TwoPassRecognizer` implements `Recognizer` and wraps the existing cloud recognizer without changing the seam.
- Full-res retention via `CapturedImage.original` is minimal and backward-compatible (default null, excluded from equality).
- Fail-soft design: crop/verify failures degrade to pass-1 detection; pass-1 failure fails the scan (existing behavior).
- Parallel verification with cap prevents latency explosion.
- Sub-catalog construction correctly limits pass-2 to active catalog subset (SCAN-4).
- Comprehensive test suite with clear test names matching acceptance criteria.
- No UI changes — the two passes are invisible above the seam, as intended.
- ktlint/detekt clean (per PR description).

---

## Final Recommendation

**Approve with minor comments.** The implementation is solid, well-tested, and faithfully follows the spec. The findings above are non-blocking suggestions for clarity, observability, and future configurability. Address items 1, 3, 4, 5, 9, 10 before merge; items 2, 6, 7, 8 are optional improvements.

---

## Review Metadata

- **PR:** #9
- **Title:** Implement R5 two-pass crop escalation
- **Author:** ronyherd1310
- **Branch:** feat/scan-r5-two-pass-crop-escalation
- **Target:** main
- **Reviewed at:** 2026-06-14T01:15:00Z
- **Review file:** reviews/pr-9-review.md