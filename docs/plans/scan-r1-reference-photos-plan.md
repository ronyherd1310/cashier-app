# Implementation Plan: R1 — Reference Photos in the Recognition Prompt

> Scope: **R1 only** — attach enrolled reference-photo thumbnails to the OpenRouter
> recognition request, labeled by SKU (tier (a): all active SKUs under a cap of 30).
> Source spec: `docs/spec/scan-r1-reference-photos.md` (Specify complete, decisions resolved 2026-06-12).
> Parent: `docs/spec/SPEC.md` · Builds on the completed Scan module (`docs/plans/scan-plan.md`).
> Status: Draft for review.
> Created: 2026-06-12

## Overview

Make the recognizer *see* our products. `OpenRouterRecognizer` gains a reference-photo
section in its single user message — per active SKU, a text label (`SKU-0001 — Choco
Wafer:`) followed by a ~256 px thumbnail image part — ahead of the counter capture,
which stays the final image part. Thumbnails come from a new lazily-populated disk
cache (`ReferenceThumbnailStore`) over the photos already stored by enrollment.

Everything lands in `data/image/` + `data/recognizer/` + `di/`. The `Recognizer`
interface, domain models, pricing, UI, and `FakeRecognizer` tests are untouched (X-2,
SCAN-10) — that invariance is itself a verified deliverable (T5).

Delivery is risk-forward: the thumbnail store is proven standalone first, then request
assembly is proven against MockWebServer fixtures, then the look-alike scenario is
simulated end-to-end, and only then does the on-demand real-model A/B run. No live
calls anywhere in CI.

## Decisions folded into this plan (refining the spec)

- **Cap is constructor-injected with a constant default.** `OpenRouterRecognizer`
  takes `referencePhotoSkuCap: Int = REFERENCE_PHOTO_SKU_CAP` (constant `30` in
  `OpenRouterDefaults.kt`). Tests exercise the over-cap path without seeding 31
  products, and the on-demand A/B (T4) gets its "references off" arm by constructing
  with cap `0` — no config surface, no test-only production flag. **(decision)**
- **Thumbnail cache home:** `context.filesDir/reference_thumbnails/`, owned entirely by
  `ReferenceThumbnailStore` (not a subdirectory of `PhotoStorage`'s `product_photos/`,
  whose flat UUID namespace stays untouched). Cache file name = the source photo's
  relative path (already a UUID), so photo replacement — which always mints a new
  UUID — can never serve a stale thumbnail. App-private storage per the spec boundary.
  **(decision)**
- **Thumbnail parameters:** longest edge **256 px**, JPEG **quality 70**, mirroring
  `AndroidImageDownscaler`'s decode→scale→re-encode shape on `Dispatchers.Default`.
  Tunable constants local to the store; feed R9 later. **(decision, tune later)**
- **Fixtures are real photos, used by both test layers.** `androidTest/assets/lookalikes/`
  holds one look-alike pair (two reference shots + one counter shot of a single
  variant), used by the CI simulation (T3) and the on-demand A/B (T4). The owner
  supplies real variant photos (see Prerequisites); synthetic stand-ins (same bitmap,
  different label color, generated in test code) are acceptable for T3 *only* if real
  photos aren't available by then — T4 requires real ones. **(decision)**
- **On-demand A/B lives in androidTest, gated, not a new source set.** The parent plan's
  `benchmark/` source-set idea stays deferred; `LookalikeAccuracyCheck` is a normal
  instrumented class that self-skips (JUnit `Assume`) unless an instrumentation
  argument supplies a real API key — CI stays green and call-free with zero Gradle
  surgery. **(decision)**

## Architecture Decisions

- **Resolution of photo→bytes stays in `data/`.** `recognize(image, catalog)` already
  carries `CatalogItem.photos` (paths); `OpenRouterRecognizer` resolves them through
  the injected `ReferenceThumbnailStore`. No `Recognizer` signature change (parent
  "Ask first" boundary respected); `FakeRecognizer` and `StubRecognizer` compile
  unchanged.
- **Reference failures degrade, never propagate.** `thumbnailFor()` returns `null`
  (never throws) for missing/corrupt sources; the recognizer drops that SKU's image
  part and keeps its catalog text line. The only `Result.failure` paths remain the
  existing transport/parse ones (SCAN-9 semantics unchanged).
- **Request assembly is one pure-ish function.** A `referenceParts(catalog)` builder
  next to the existing `catalogContext(catalog)` narrowing seam returns `emptyList()`
  when over cap — so the over-cap request is byte-shape-identical to today's, by
  construction rather than by branching through the whole method.
- **Prompt gains one sentence, schema gains nothing.** The PROMPT explains that labeled
  reference photos of our actual packaging precede the counter photo; the response
  contract stays `{items:[{sku, quantity, confidence}]}` — R2/R3 territory is not
  entered.
- **Cost visibility without schema churn.** The recognizer's existing debug logging
  adds attached-reference count and request byte size; `ScanTelemetryEvent` is not
  extended (spec "Ask first").

## Dependency Graph

```
Scan module (DONE): OpenRouterRecognizer, PhotoStorage, RecognizerConfig,
                    MockWebServer tooling, AndroidImageDownscaler (pattern donor)

P0  Look-alike fixture photos (owner-supplied, see Prerequisites)   ← (none, parallel)

T1  ReferenceThumbnailStore + DI + instrumented tests               ← (none)
T2  Request assembly: cap + prompt + referenceParts + test extension ← T1
T3  Look-alike simulation test (MockWebServer + fixtures)           ← T2, P0
T4  LookalikeAccuracyCheck — on-demand real-model A/B (not CI)      ← T2, P0
T5  Regression sweep, device numbers, SPEC.md amendment             ← T2, T3, T4
```

T1 and P0 run in parallel; T3 and T4 are independent of each other once T2 lands.
Risk-forward note: the riskiest unknown is whether the free-tier default model accepts
a multi-image message at all — T2's manual device QA surfaces this within the first
real scan, long before the A/B (accepted risk, remedy owned by R8 per the spec's
Resolved Decision 3).

---

## Task List

### Task 1: `ReferenceThumbnailStore` — thumbnail generation + disk cache

**Description:** The standalone thumbnail component: resolve an enrolled photo's
relative path to a ≤256 px JPEG `CapturedImage`, generating on first use and caching
under `filesDir/reference_thumbnails/`. Null-on-failure contract; no callers yet.

**Acceptance criteria:**
- [ ] `data/image/ReferenceThumbnailStore.kt` per the spec sketch: constructor-injected
  (`PhotoStorage` + `@ApplicationContext`), `suspend fun thumbnailFor(photoPath: String): CapturedImage?`;
  decode/scale/encode on `Dispatchers.Default`; constants `THUMB_MAX_EDGE_PX = 256`,
  `THUMB_JPEG_QUALITY = 70` local to the file.
- [ ] Cache hit reads the stored thumbnail without re-decoding the original; cache
  survives process restart (it's just files). Cache file name = source relative path.
- [ ] Missing source file, unreadable file, or undecodable bytes → `null`, never a
  throw (R1-4 groundwork).
- [ ] `@Singleton` provision wired in DI (`AppModule` or `RecognizerModule`, matching
  the existing style).

**Verification:**
- [ ] `connectedDebugAndroidTest` — `ReferenceThumbnailStoreTest`: generated thumbnail
  decodes with longest edge ≤ 256 px; second call serves the cached file (assert no
  regeneration via file mtime or by deleting the original after first call); missing
  path → null; corrupt bytes → null; distinct paths → distinct thumbnails (R1-2).

**Dependencies:** None · **Files:** `data/image/ReferenceThumbnailStore.kt`,
`di/AppModule.kt` (or `RecognizerModule.kt`),
`androidTest/.../data/image/ReferenceThumbnailStoreTest.kt` · **Scope:** M

---

### Task 2: Request assembly — cap, prompt, labeled reference parts

**Description:** The heart of R1. `OpenRouterRecognizer` attaches per-SKU labeled
thumbnail parts when the active catalog is at/under the cap, updates the PROMPT, and
logs count + payload size. Over cap or on any thumbnail failure, behavior degrades
exactly to today's request.

**Acceptance criteria:**
- [ ] `REFERENCE_PHOTO_SKU_CAP = 30` in `OpenRouterDefaults.kt`;
  `OpenRouterRecognizer` takes `referencePhotoSkuCap: Int = REFERENCE_PHOTO_SKU_CAP`.
- [ ] `referenceParts(catalog)`: for catalogs ≤ cap, per SKU in catalog order — text
  part `"<SKU> — <name>:"` + image part (thumbnail data URL) for the **primary** photo
  (lowest `position`), resolved via `ReferenceThumbnailStore`; SKUs with no photos or
  a `null` thumbnail contribute no parts; preceded by the framing text part
  ("Reference photos follow, one per labeled SKU…"). Over cap → `emptyList()`.
- [ ] Message layout per the spec: `[prompt+catalog] [reference parts…] ["Counter
  photo to itemize:"] [capture]` — the capture is always present and always the final
  image part; the "Counter photo" label appears only when references were attached
  (over-cap request is byte-shape-identical to today's, R1-3).
- [ ] PROMPT updated with the one-sentence reference-photo instruction; response
  schema, parsing, and error mapping untouched.
- [ ] Debug log line: attached-reference count + request body byte size; no image
  bytes, no key (spec Always-boundary).

**Verification:**
- [ ] `connectedDebugAndroidTest` — `OpenRouterRecognizerTest` extensions: ≤-cap
  catalog with stored photos → one label + one `data:image/jpeg;base64,` part per
  SKU, in order, capture last (R1-1); catalog over cap (constructed with a small cap,
  e.g. 1, two items) → no reference parts (R1-3); SKU with a missing photo file →
  image part absent, text line present, call succeeds (R1-4); all five existing tests
  green unchanged.
- [ ] Manual device QA: one real scan with an enrolled catalog — confirms the default
  model accepts a multi-image message and the draft still arrives in budget (early
  R1-6 read; record the latency).

**Dependencies:** T1 · **Files:** `data/recognizer/OpenRouterRecognizer.kt`,
`data/recognizer/OpenRouterDefaults.kt`,
`androidTest/.../data/recognizer/OpenRouterRecognizerTest.kt` · **Scope:** M

> ### ✅ Checkpoint 1 — Wiring proven
> - [ ] Thumbnail store + request shape verified against MockWebServer; existing
>   recognizer/scan suites green; one real device scan succeeded with references
>   attached. Human review before the look-alike layer.

---

### Task 3: Look-alike simulation test (CI-safe)

**Description:** The scenario test from the spec: a seeded look-alike pair scanned
end-to-end through the real `OpenRouterRecognizer` against MockWebServer, asserting
the request teaches the model the *correct* SKU↔photo mapping and that both correct-
and wrong-variant responses flow cleanly.

**Acceptance criteria:**
- [ ] `androidTest/assets/lookalikes/` holds the fixture pair (two reference shots +
  one counter shot; real photos from P0, or documented synthetic stand-ins as
  fallback); a small helper enrolls them through `PhotoStorage` into a two-SKU
  catalog ("Choco Wafer" / "Vanilla Wafer").
- [ ] `LookalikeRecognizerTest` asserts on the recorded request: **both** variants'
  thumbnails attached, each directly following its **own** SKU label — explicitly
  assert label→image adjacency/pairing, not mere presence (a swapped label is R1's
  worst wiring bug).
- [ ] Mocked correct-variant response → `RecognizedItem` with that SKU; mocked
  wrong-variant response → still maps cleanly to the other catalog SKU (draft-level
  confidence flagging is existing behavior; no crash, nothing dropped).

**Verification:**
- [ ] `connectedDebugAndroidTest` — `LookalikeRecognizerTest` green; suite runs with
  no network beyond MockWebServer.

**Dependencies:** T2, P0 · **Files:** `androidTest/assets/lookalikes/*`,
`androidTest/.../data/recognizer/LookalikeRecognizerTest.kt` · **Scope:** M

---

### Task 4: `LookalikeAccuracyCheck` — on-demand real-model A/B (not CI)

**Description:** The measured before/after for R1's purpose. Same fixtures, real
configured provider: submit the counter shot of one variant with references attached
(default cap) and with references off (cap 0); record which SKU came back in each arm.
Informs, never gates.

**Acceptance criteria:**
- [ ] `LookalikeAccuracyCheck` (androidTest) self-skips via JUnit `Assume` unless an
  instrumentation argument (e.g. `-Pandroid.testInstrumentationRunnerArguments.openrouterApiKey=…`)
  supplies a key — CI runs it as skipped, zero live calls.
- [ ] Two arms against the real provider with the enrolled look-alike pair:
  references **on** (default cap) vs **off** (recognizer constructed with cap 0);
  each arm logs returned SKU, confidence, latency, and request size in one
  grep-friendly summary line per arm.
- [ ] The test asserts only mechanics (a parseable response per arm) — correctness of
  the variant is *recorded*, not asserted (model-dependent; spec Resolved Decision 3).
- [ ] A short how-to-run note in the class KDoc (command line incl. the key argument).

**Verification:**
- [ ] Run once on a device with a real key; both arms complete and the summary lines
  appear in logcat. Numbers go into the T5 PR record.

**Dependencies:** T2, P0 · **Files:**
`androidTest/.../data/recognizer/LookalikeAccuracyCheck.kt` (spec listed this under a
`benchmark/` path — landing it in `data/recognizer/` per the source-set decision
above) · **Scope:** S–M

---

### Task 5: Regression sweep, device numbers, spec sync

**Description:** Prove the seam held, capture the measurements the spec demands, and
sync the living documents.

**Acceptance criteria:**
- [ ] Full suites green with **zero edits** outside the planned files:
  `testDebugUnitTest` (incl. `ScanCounterTest`), `connectedDebugAndroidTest` (incl.
  draft/E2E `FakeRecognizer` flows), `ktlintCheck`, `detekt` (R1-5).
- [ ] Device measurements recorded in the PR description: scan latency with a full
  ≤-cap catalog attached (R1-6, ≤ ~5 s), request payload size, token usage delta
  references-on vs -off if the provider reports usage, and the T4 A/B summary lines.
- [ ] `docs/spec/SPEC.md` "Catalog context strategy" amended: text list **plus**
  labeled reference thumbnails under a 30-SKU cap (R1), pre-narrowing still the
  at-scale path; `docs/spec/scan-r1-reference-photos.md` status flipped to
  Implemented.
- [ ] First-scan warm-up sanity: with a cold thumbnail cache and a full catalog, the
  first scan stays within budget (spec Resolved Decision 4's accepted ~1–1.5 s).

**Verification:**
- [ ] `./gradlew check` + connected suite green; PR contains the numbers; manual
  smoke: enroll look-alike pair → scan → draft names the right variant (success-
  criteria smoke check).

**Dependencies:** T2, T3, T4 · **Files:** `docs/spec/SPEC.md`,
`docs/spec/scan-r1-reference-photos.md`, PR description · **Scope:** S

> ### ✅ Checkpoint 2 — R1 complete
> - [ ] R1-1…R1-6 satisfied; all suites + lint green; measurements recorded; spec
>   docs synced. Human sign-off.

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Free-tier default model mishandles multi-image messages (ignores references, garbles output, or rejects the request) | Medium–High | Surfaced at T2's manual QA, quantified by T4's A/B. **Accepted** per spec decision 3 — remedy is a config-string model change owned by R8; R1's wiring is still correct and ready. |
| Swapped SKU↔photo labeling actively teaches the model wrong mappings | Low, catastrophic if missed | T3 asserts label→image adjacency explicitly; catalog-order construction in one builder function. |
| Payload/latency blows the SCAN-3 budget at full cap | Low at 30×~20 KB (≲1 MB) | Cap constant + R1-6 measured on device (T2 early read, T5 formal); cap reduction is a one-constant rollback. |
| Stale thumbnail after photo replacement | Very low | Path-keyed cache + UUID-per-save means replacement can't collide; covered in T1 tests. |
| First-scan warm-up spikes latency on a cold cache | Low | Bounded (~20–50 ms × catalog size, once); T5 sanity-checks it inside budget. |
| Fixture photos unavailable when T3 starts | Medium | P0 is flagged now as owner-supplied and parallel; T3 has a documented synthetic fallback, T4 simply waits on real photos. |

## Prerequisites (owner-supplied, can start now — P0)

- **Look-alike fixture photos:** two real flavor/color variant products — one
  enrollment-style reference shot of each, plus one counter-style shot containing one
  of them. Phone camera quality is fine; they land in
  `app/src/androidTest/assets/lookalikes/`.
- **A real OpenRouter key on the test device** (existing Settings flow) for T2 manual
  QA and the T4 A/B run.

## Open Questions

None — all spec-level questions were resolved 2026-06-12 (see the spec's Resolved
Decisions). Plan-level refinements adopted above are flagged **(decision)**; flip any
at review.
