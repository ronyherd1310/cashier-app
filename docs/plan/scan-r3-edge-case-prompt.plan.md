# Implementation Plan: R3 — Edge-Case-Explicit Prompt with Structured Uncertainty

> Plan for `docs/spec/scan-r3-edge-case-prompt.md` (Approved, 2026-06-13 — all open questions
> resolved to the spec defaults). Sequenced into thin, independently-testable slices per the
> increment cycle: implement → test → verify → commit → next slice.

## Key ordering insight

**R3 depends on R2 being merged.** R3 extends R2's response DTO (`box` field), prompt
(per-instance enumeration), and `priceDraft` SKU-grouping. The recognizer on the current
branch still maps only `{sku, quantity, confidence}`, so **R2 must land first**; this plan
assumes it has (spec Assumption 1). If R2 is not merged when R3 begins, ship R2 first — do
not re-implement enumeration/aggregation here.

**Within R3, the schema/domain change must land before the prompt asks for the new fields.**
Making `RecognizedItem.sku` nullable and adding the three optional fields (Slice 1) is the
load-bearing, ripple-prone change — `priceDraft`, `FakeRecognizer`, and `StubRecognizer` all
reference `sku`/`RecognizedItem`. It also degrades gracefully (defaults), so it is safe to
land before the prompt (Slice 3) ever asks the model to populate the fields.

**Branch:** `implement/scan-r3-edge-case-prompt` (off `main`, after R2 merges)
**Pre-req each run:** `export JAVA_HOME=<java-21>` before `./gradlew` (Gradle needs JDK 17+).

> ✅ **Domain shape locked (spec Resolved Decisions, 2026-06-13):** `sku` is nullable; the three
> new fields stop at `RecognizedItem` (not threaded into `DraftLine`); no forced occluded flag.
> Only remaining gate is **R2 must be merged first** (see Key ordering insight).

---

## Slice 1 — Domain + DTO: nullable `sku` and the three optional fields (risk-first)

The load-bearing, ripple-prone change (spec Assumptions 2–4). Pure model/serialization,
fully backward-compatible via defaults. No prompt change yet, so no model is asked to
populate anything — this slice only makes the types *able* to carry the data.

- **Files:** `domain/recognizer/Recognizer.kt`, `data/recognizer/dto/ChatCompletionResponse.kt`
- **Change:**
  - `RecognizedItem`: `sku: String?` (was non-null); add `occluded: Boolean = false`,
    `possiblyMore: Boolean = false`, `alternates: List<String> = emptyList()`.
  - `RecognizedItemDto`: `sku: String? = null`; add `occluded: Boolean = false`,
    `possiblyMore: Boolean = false`, `alternates: List<String> = emptyList()`.
  - Fix fallout from nullable `sku`: `FakeRecognizer` and `StubRecognizer` keep non-null
    literals (compile clean); resolve any non-null-`sku` assumptions the compiler surfaces
    **except** `priceDraft` (that is Slice 2).
- **Tests:** none new here beyond keeping the suite compiling/green; a serialization spot
  check can ride in Slice 3's recognizer tests. Existing `PricingTest`/`OpenRouterRecognizerTest`
  must still compile and pass (defaults preserve R2 behavior).
- **Verify:** `./gradlew app:testDebugUnitTest`
- **Acceptance:** R3-2 (types accept the optional fields with safe defaults)
- **Commit:** `feat(recognizer): add occluded/possiblyMore/alternates and nullable sku (R3)`

## Slice 2 — `priceDraft`: route a null `sku` to `UnidentifiedItem`

The only pricing change in R3. Pure domain, JVM-testable, leaves money math untouched.

- **File:** `domain/pricing/Pricing.kt`
- **Change:** with R2's `groupBy { it.sku }`, a `null` key is now a valid group; route it to
  `UnidentifiedItem(rawSku = null, quantity = group size, confidence = min)`. Use
  `sku?.let { catalog[it] }` so a null sku never indexes the catalog. **Do not** read or store
  `occluded`/`possiblyMore`/`alternates` (spec Assumption 3 — they ride on `RecognizedItem`
  for R7).
- **Tests** (`PricingTest.kt`, JVM unit):
  - `RecognizedItem(sku = null)` → one `UnidentifiedItem(rawSku = null)` with right
    quantity/confidence; never dropped, never priced (SCAN-7).
  - Mixed batch (identified instances + a null-sku detection) → grouped correctly both sides;
    identified-line money math (X-1) unchanged from R2.
  - Setting `occluded`/`possiblyMore`/`alternates` on inputs does **not** change any line's
    price, quantity, or `lowConfidence` (proves R3 pricing is inert to the new fields).
  - All existing `PricingTest` cases stay green.
- **Verify:** `./gradlew app:testDebugUnitTest`
- **Acceptance:** R3-4, R3-5
- **Commit:** `feat(pricing): route null-sku detections to unidentified items (R3)`

## Slice 3 — Recognizer mapping: populate new fields + validate `alternates`

Recognizer learns to *read* the new fields. Prompt still unchanged, so safe even if no model
volunteers them (fields default).

- **File:** `data/recognizer/OpenRouterRecognizer.kt`
- **Change:** in the `recognize` mapping, set `occluded = dto.occluded`,
  `possiblyMore = dto.possiblyMore`, and `alternates = dto.alternates.filter { it in catalogSkus }`
  where `catalogSkus = catalog.mapTo(HashSet()) { it.sku }`; pass `dto.sku` straight through
  (now nullable). Validation never throws and never fails the scan (SCAN-9).
- **Tests** (`OpenRouterRecognizerTest.kt`, androidTest/MockWebServer):
  - Response with `occluded`/`possiblyMore`/`alternates` → `RecognizedItem` carries them.
  - `alternates` with a non-catalog SKU → filtered out; a catalog SKU → kept.
  - `sku: null` with a box → `RecognizedItem(sku = null, boundingBox != null)`.
  - R2-shaped body (no new fields) → all three default; scan succeeds (backward compatible).
  - Existing tests (HTTP error, timeout, missing key, R2 per-instance/box parse) stay green.
- **Verify:** `./gradlew app:connectedDebugAndroidTest` (needs device/emulator)
- **Acceptance:** R3-2, R3-3, R3-4 (parse half)
- **Commit:** `feat(recognizer): parse uncertainty fields and validate alternates (R3)`

## Slice 4 — `PROMPT`: add occlusion / stack / look-alike policies (behavior flip)

With the domain (S1), routing (S2), and parsing (S3) in place, asking the model to populate
the fields is now safe end-to-end.

- **File:** `data/recognizer/OpenRouterRecognizer.kt` (`PROMPT`)
- **Change:** append the three policy paragraphs (spec §Prompt delta): occlusion (incl.
  `sku: null` for seen-but-unidentifiable), stacks (`possiblyMore`), look-alikes
  (`alternates`, catalog SKUs only). Keep the R2 per-instance/box wording, R1 reference-photo
  wording, and "ONLY catalog SKUs / never invent / never include prices" rules.
  `responseFormat` stays `json_object`.
- **Tests:** assert the outgoing prompt contains the occlusion / stack / look-alike policy
  text; assert R1 reference-photo and R2 per-instance assembly unchanged (those tests green).
- **Verify:** `./gradlew app:connectedDebugAndroidTest`
- **Acceptance:** R3-1
- **Commit:** `feat(recognizer): make prompt edge-case-explicit with uncertainty policies (R3)`

## Slice 5 — Regression gate, docs, manual smoke, PR metrics

No new behavior — proof the seam held + close-out.

- **Regression:** `ScanCounterTest`, `DraftViewModelTest`, `FakeRecognizer` flows (SCAN-10),
  draft/E2E suites pass untouched (R3-6). Full
  `./gradlew app:testDebugUnitTest app:connectedDebugAndroidTest`, then
  `app:ktlintFormat && app:ktlintCheck` and `app:detekt` clean.
- **Docs:** note the R3 fields in `docs/spec/SPEC.md` if needed; mark the spec implemented.
- **Manual smoke:** device check — request prompt carries the three policies; a partially
  hidden, unidentifiable item surfaces as an unidentified entry rather than vanishing.
- **PR body:** record whether/how often the default model set `occluded`/`possiblyMore`/
  `alternates` on a few edge-case captures, plus any request/latency delta from the longer
  prompt (Success Criteria; pre-R9 signal).
- **Commits:** `docs: mark R3 edge-case prompt implemented` (+ separate commit for any SPEC.md
  amend — one logical thing per commit).

---

## Acceptance coverage

| Acceptance | Slice |
|---|---|
| R3-1 prompt states 3 policies | S4 |
| R3-2 optional fields + defaults | S1 (types) + S3 (parse) |
| R3-3 alternates validated, no scan fail | S3 |
| R3-4 null sku → unidentified | S2 (route) + S3 (parse) |
| R3-5 fields inert to pricing | S2 |
| R3-6 seam/UI untouched | S5 |

Each slice leaves the build green and is independently revertible. Slices 1–2 are JVM-only
(fast); Slices 3–4 need a device/emulator for the MockWebServer instrumented tests.
```
