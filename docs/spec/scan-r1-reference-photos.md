# Spec: R1 — Enrolled Reference Photos in the Recognition Prompt

> Source idea: `docs/ideas/scan-accuracy-improvements.md` (R1 only)
> Parent spec: `docs/spec/SPEC.md` (Photo Checkout) — this spec inherits its commands,
> project structure, code style, and boundaries; only deltas are stated here.
> Status: Specify complete — open questions resolved at review 2026-06-12. Next: Plan.
> Date: 2026-06-12

## Objective

Attach the catalog's enrolled reference photos to the recognition request as labeled
`image_url` parts, so the model matches counter items against **our actual packaging**
instead of imagining what a product name looks like.

**Why:** The recognizer currently knows each product only as a text line
(`SKU-0001 - Choco Wafer`). Near-identical variants ("Choco Wafer" vs "Vanilla Wafer")
differ by a small label patch the model has never seen for our products — the known
failure mode for the *similar packaging* edge case. Reference photos are already
enrolled and stored on `CatalogItem.photos` (CAT-4 explicitly reserved them as
"Phase-1 prompt context"), but the recognizer never sees them. R1 closes that gap.

**User:** The cashier, indirectly — fewer wrong-variant lines in the draft means fewer
edits per scan. No UI changes; this is entirely a `data/recognizer/` + `data/image/`
change behind the existing `Recognizer` seam (X-2).

**Success looks like:** look-alike SKUs are distinguished correctly more often, with no
regression to scan latency (SCAN-3, ≤ ~5 s) and no new scan failure modes.

## Assumptions

These were resolved from the code and the idea doc; correct them at review if wrong.

1. **Tier (a) only.** The idea doc's tiered rollout — (a) attach all SKUs while the
   catalog is small, (b) confusion-group filtering (R4), (c) two-pass (R5) — lands here
   as tier (a) with a hard SKU cap. Tiers (b)/(c) are out of scope.
2. **One photo per SKU.** Products can have up to 3 photos (CAT-4); R1 attaches only the
   primary one (lowest `position`). Multi-photo attachment multiplies cost for unclear
   gain — revisit with benchmark data.
3. **Lazy thumbnail generation with a persistent disk cache**, not enrollment-time
   pre-generation. A path-keyed cache covers already-enrolled products with no
   migration and no changes to the enrollment flow; after the first scan it is
   equivalent to pre-generation. (The idea doc said "pre-generate at enrollment" — the
   intent is *not per scan*, which the cache satisfies. Flip this at review if you want
   eager generation too.)
4. **No `Recognizer` interface change.** `CatalogItem.photos` already travels through
   `recognize(image, catalog)`; resolving paths to bytes happens inside the data layer.
   This respects the parent spec's "Ask first: changing the `Recognizer` signature".
5. **No settings UI.** The SKU cap and thumbnail size are constants in
   `data/recognizer/` / `data/image/`, tunable in code (and later via benchmark, R9).
6. **Privacy:** reference photos leave the device, but only to the *same configured
   provider* that already receives the counter capture — allowed by the parent
   boundaries without a new approval, flagged here for visibility.

## Scope

**In:** request assembly in `OpenRouterRecognizer`, prompt text update, a thumbnail
component in `data/image/`, DI wiring, tests.

**Out (non-goals):** bounding boxes / per-instance schema (R2), edge-case prompt
policies (R3), visual descriptions and confusion groups (R4), two-pass recognition
(R5), any capture or Draft Review UI change (R6, R7), model benchmarking (R8), the
eval harness (R9 — see Open Questions), correction telemetry (R10), thumbnail
garbage-collection for deleted photos (orphans are ~10 KB each; revisit if it matters),
`ScanTelemetryEvent` schema changes.

## Design

### Request layout

Today the request is one user message: `[text: PROMPT + catalog list] [image: capture]`.
With R1, when reference photos are attached, the message becomes:

```
[text]  PROMPT (updated) + catalog list
[text]  "Reference photos follow, one per labeled SKU. Then the counter photo."
[text]  "SKU-0001 — Choco Wafer:"     [image: 256px thumbnail data URL]
[text]  "SKU-0002 — Vanilla Wafer:"   [image: 256px thumbnail data URL]
...
[text]  "Counter photo to itemize:"   [image: capture data URL]
```

The PROMPT gains one instruction: reference photos show the actual packaging of
catalog products; match items in the counter photo against them. Identity-only output
(`{sku, quantity, confidence}`) and the never-price rule are unchanged.

### Attachment policy

- Attach reference thumbnails only when the active catalog has ≤ `REFERENCE_PHOTO_SKU_CAP`
  items (constant in `OpenRouterDefaults.kt`, initial value **30**). Above the cap the
  request is byte-for-byte today's text-only shape — the cap is the tier-(a) guardrail
  until R4's confusion groups exist.
- A SKU whose photo is missing, unreadable, or undecodable keeps its text line and
  silently loses its image part. **A reference-photo problem must never fail a scan**
  (the capture image path keeps SCAN-9 semantics unchanged).
- Sizing: at ~256 px / JPEG q70 a thumbnail is ~8–20 KB (~11–27 KB base64), so a full
  30-SKU catalog adds ≲ 1 MB to the request — within the SCAN-3 budget on counter
  Wi-Fi, but it must be measured (see Success Criteria).

### New component: `ReferenceThumbnailStore` (`data/image/`)

```kotlin
/**
 * Resolves an enrolled reference-photo path to a small JPEG thumbnail for prompt
 * attachment (R1). Thumbnails (~256 px longest edge, q70) are generated on first
 * use and cached on disk keyed by the source photo's relative path — photo
 * replacement yields a new UUID path, so the cache never serves a stale image.
 * Returns null (never throws) when the source is missing or undecodable.
 */
public class ReferenceThumbnailStore @Inject constructor(
    private val photoStorage: PhotoStorage,
) {
    public suspend fun thumbnailFor(photoPath: String): CapturedImage?
}
```

- Decode/scale/encode runs on `Dispatchers.Default`, mirroring `AndroidImageDownscaler`.
- Cache lives in a `thumbs/` sibling of the `product_photos/` directory (exact home
  decided in Plan; it must stay inside app-private storage).
- `OpenRouterRecognizer` gets it constructor-injected; `RecognizerModule` wires it.
  `AndroidImageDownscaler` is untouched (its 1024 px constant serves the capture path).

### What does NOT change

`Recognizer` interface, `RecognizedItem`, `ScanCounter`, `priceDraft`, all of `ui/`,
`FakeRecognizer`-driven tests (SCAN-10), `StubRecognizer`, the Room schema, settings.

## Tech Stack

Inherited from `docs/spec/SPEC.md`. No new dependencies (Android `Bitmap` APIs and the
existing Retrofit/OkHttp/kotlinx.serialization stack suffice).

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
  data/image/ReferenceThumbnailStore.kt        → NEW: thumbnail generation + disk cache
  data/recognizer/OpenRouterRecognizer.kt      → request assembly: labeled reference parts
  data/recognizer/OpenRouterDefaults.kt        → REFERENCE_PHOTO_SKU_CAP constant
  di/RecognizerModule.kt (or AppModule)        → wiring
app/src/androidTest/java/com/cashierapp/photocheckout/
  data/image/ReferenceThumbnailStoreTest.kt    → NEW
  data/recognizer/OpenRouterRecognizerTest.kt  → extended (MockWebServer, fixtures)
  data/recognizer/LookalikeRecognizerTest.kt   → NEW: look-alike pair simulation
  benchmark/LookalikeAccuracyCheck.kt          → NEW: on-demand real-model A/B (not CI)
app/src/androidTest/assets/lookalikes/         → NEW: look-alike reference/counter fixtures
docs/spec/SPEC.md                              → amend "Catalog context strategy" note
                                                 after this spec is approved
```

## Code Style

Inherited (ktlint, explicit public API, KDoc that cites criteria IDs, vendor detail
only in `data/recognizer/`). Request-assembly sketch:

```kotlin
// OpenRouterRecognizer — content parts, reference photos before the capture (R1-2).
val parts = buildList {
    add(ContentPart(type = "text", text = PROMPT + "\n\n" + catalogContext(catalog)))
    val references = referenceParts(catalog)          // empty above cap (R1-3)
    addAll(references)
    if (references.isNotEmpty()) {
        add(ContentPart(type = "text", text = "Counter photo to itemize:"))
    }
    add(ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl(image))))
}
```

## Testing Strategy

All new coverage is instrumented (Bitmap and MockWebServer both need a device/emulator),
matching where `OpenRouterRecognizerTest` and `PhotoStorageTest` already live.

- **`ReferenceThumbnailStoreTest` (androidTest):** generates a ≤256 px JPEG from a
  stored photo; second call serves the cached file (no re-decode — assert via file
  mtime or a counting fake); missing path → null; corrupt bytes → null; replaced photo
  (new path) → new thumbnail.
- **`OpenRouterRecognizerTest` extensions (androidTest, MockWebServer):**
  - Catalog ≤ cap with stored photos → request body contains one labeled text part +
    one `data:image/jpeg;base64,` part per SKU, in catalog order, with the counter
    capture as the **final** image part.
  - Catalog > cap → request body shape identical to today's (no reference parts).
  - SKU with a missing photo file → its image part absent, its catalog text line
    present, call still succeeds.
  - Existing tests (parse, HTTP error, timeout, missing key) stay green unchanged.
- **Look-alike simulation (androidTest, MockWebServer):** seed a catalog with a
  look-alike pair — two SKUs with near-identical names ("Choco Wafer" / "Vanilla
  Wafer") and visually similar reference-photo fixtures (bundled under
  `androidTest/assets/lookalikes/`, e.g. the same packaging shot differing only in
  label color). Scan a counter image containing one variant and assert:
  - the request attaches **both** variants' thumbnails, each under its own correct
    SKU label (a mislabeled reference would actively teach the model the wrong
    mapping — this is the highest-risk wiring bug R1 can have);
  - a mocked response picking the correct variant flows through to a
    `RecognizedItem` with that SKU, and one picking the *wrong* variant still maps
    cleanly (it's a catalog SKU — the draft flags it by confidence, never crashes).
- **Look-alike accuracy check (benchmark source set, on-demand, NOT CI):** the same
  fixture pair run against the *real* configured provider, per the parent spec's L6
  harness slot: enroll both variants, submit a counter photo of one, and record
  whether the returned SKU is the correct variant — once with references attached,
  once with the cap forced to 0 (references off). This is the measured before/after
  for R1's whole purpose; it informs but never gates CI (model-dependent, costs
  money). Feeds the R9 eval set when that lands.
- **Regression:** `ScanCounterTest`, draft/E2E suites must pass untouched — proof the
  seam held (X-2, SCAN-10).
- **Not in CI:** the accuracy *numbers* themselves — the on-demand look-alike check
  above and, eventually, the L6 benchmark / R9 eval set (see Open Questions).

## Boundaries

Inherited from `docs/spec/SPEC.md`, plus R1-specific:

- **Always:** degrade to today's text-only request on any reference-photo failure or
  when over the cap — never fail or delay a scan because of reference photos; keep
  thumbnails in app-private storage; log attached-reference count and request size at
  debug level (cost visibility, X-3) without logging image bytes or the API key.
- **Ask first:** raising `REFERENCE_PHOTO_SKU_CAP` above 30; attaching more than one
  photo per SKU; adding a settings toggle for this feature; extending
  `ScanTelemetryEvent`.
- **Never:** send reference photos to any endpoint other than the configured
  OpenRouter provider; let reference parts displace the counter capture (it is always
  present and always last); request or accept prices.

## Acceptance Criteria

- **R1-1:** With an active catalog of ≤ cap items that have stored reference photos,
  the recognition request contains, per SKU, a text label (`SKU — name:`) followed by
  a base64 JPEG thumbnail part, ordered before the counter capture, which is the final
  image part.
- **R1-2:** Reference thumbnails are ≤ 256 px on the longest edge, generated at most
  once per stored photo (disk-cached across scans and app restarts).
- **R1-3:** With an active catalog above the cap, the request is identical in shape to
  the current text-only request.
- **R1-4:** A missing/corrupt reference photo degrades that SKU to text-only; the scan
  proceeds and can succeed. No reference-photo condition produces a scan failure.
- **R1-5:** The `Recognizer` interface, domain models, pricing, and all `ui/` code are
  unchanged (X-2); `FakeRecognizer` flows (SCAN-10) pass without edits.
- **R1-6:** End-to-end scan latency with a full ≤-cap catalog attached stays within the
  SCAN-3 budget (≤ ~5 s) on the reference device/network.

## Success Criteria

- All acceptance criteria pass; new + existing unit and instrumented suites green;
  `ktlintCheck` and `detekt` clean.
- A manual device check with at least one enrolled look-alike pair (e.g., two flavor
  variants side by side on the counter) shows the request attaching references and the
  draft naming the correct variants — a smoke check, not a benchmark.
- Measured numbers recorded in the PR: request payload size, scan latency, and (if the
  model reports usage) token delta with references on vs off — the inputs the R9
  harness will later formalize.

## Resolved Decisions

Settled at spec review (2026-06-12):

1. **Cap value: 30.** `REFERENCE_PHOTO_SKU_CAP = 30` as proposed. Raising it later is
   an "Ask first" one-constant change; above it R1 degrades to text-only until R4/R5.
2. **R1 proceeds now; R9 stays separate.** Verification is the look-alike smoke check
   plus the on-demand `LookalikeAccuracyCheck` A/B; the formal eval harness and golden
   set remain R9's scope. Measured numbers from R1's PR feed R9 when it lands.
3. **Default model only.** R1 is built and verified against the configured default
   (`nemotron` free tier); no stronger-model verification or default switch inside R1
   — model strategy is entirely R8's scope. *Accepted risk:* if the free-tier model
   handles multi-image prompts poorly, R1's gain may not show on it; the on-demand A/B
   will reveal this, and the remedy is a config-string change owned by R8.
4. **Lazy thumbnail cache only.** No eager generation at enrollment; the one-time
   first-scan warm-up (~1–1.5 s for a 30-item catalog) is accepted. Assumption 3
   stands as written.

## Open Questions

None — resolved above. Next phase: Plan.
