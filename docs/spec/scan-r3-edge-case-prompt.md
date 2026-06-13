# Spec: R3 — Edge-Case-Explicit Prompt with Structured Uncertainty

> Source idea: `docs/ideas/scan-accuracy-improvements.md` (R3 only)
> Parent spec: `docs/spec/SPEC.md` (Photo Checkout) — this spec inherits its commands,
> project structure, code style, and boundaries; only deltas are stated here.
> Sibling specs: `docs/spec/scan-r1-reference-photos.md` (R1, implemented) and
> `docs/spec/scan-r2-per-instance-detections.md` (R2). **R3 depends on R2** — it extends
> the same response DTO, prompt, `RecognizedItem`, and `priceDraft` grouping R2 introduces.
> Status: Approved — all open questions resolved at review (2026-06-13); ready to implement.
> Date: 2026-06-13

## Objective

Replace the single-sentence recognition prompt with **explicit edge-case policies** for the
three known failure modes — occluded items, stacked items, look-alike variants — and give
the model **structured places to put its uncertainty** so that uncertainty survives into the
draft instead of being lost to model whim. Three response fields extend `RecognizedItem`:

- `occluded: Boolean` — the instance is only partially visible.
- `possiblyMore: Boolean` — more units of this SKU may be hidden behind/under what is visible.
- `alternates: List<String>` — runner-up catalog SKUs when the model was torn between
  near-identical variants.

Plus one routing change: a detection the model can see but **cannot identify** arrives as
`sku: null` (with a box) and flows into the existing `UnidentifiedItem` path (SCAN-7) instead
of being silently dropped or guessed.

**Why:** Today the one-sentence prompt (`OpenRouterRecognizer.PROMPT`) leaves every edge-case
policy undefined — occluded items are sometimes dropped (silent undercount), sometimes guessed
at high confidence; stacks come back as a single over-confident count; look-alike confusion has
nowhere to register except a wrong line the cashier must hunt down in the picker. Spelling out
the policy and giving each kind of doubt a structured channel is the cheapest lever that touches
**all three** edge cases at once (idea doc, sequencing step 2, alongside R2).

**User:** The cashier, indirectly. R3 ships **no UI change** — exactly as R2 populated boxes
without drawing them. The new fields are populated on `RecognizedItem` at the recognizer
boundary; rendering them (stack "verify count" badges, one-tap `alternates` swap chips,
unidentified-crop resolution) is R7's job. R3's *visible* win is the `sku: null` → unidentified
routing and fewer silently-dropped occluded items. This is a `data/recognizer/` + a minimal
`domain/` change behind the existing `Recognizer` seam (X-2).

**Success looks like:** the outgoing prompt states the occlusion / stack / look-alike policies;
the response schema accepts and the domain model carries `occluded` / `possiblyMore` /
`alternates` / nullable `sku`; a model that omits all of them parses exactly as R2 does
(graceful degradation); no pricing-math change (X-1); no Draft Review change.

## Assumptions

Resolved from the code, the idea doc, and the R1/R2 specs; correct them at review if wrong.

1. **R3 lands on top of R2.** R3 assumes R2 is merged: the response DTO already carries an
   optional `box`, the prompt already requests one entry per physical instance, and `priceDraft`
   already groups detections by SKU into one line. R3 only *adds fields and policy wording* to
   those same surfaces. If R2 is not yet merged when R3 begins, R2 ships first (idea-doc step 2
   pairs them); R3 does **not** re-implement per-instance enumeration or aggregation.
2. **`RecognizedItem.sku` becomes nullable (`String?`).** This is the one load-bearing domain
   change. The idea doc's occlusion policy says "if you can see *something* but cannot identify
   it, return it as `sku: null` with a box." `UnidentifiedItem.rawSku` is *already* `String?`
   for exactly this case, but `RecognizedItem.sku` is currently non-null, so a null can't flow
   through. R3 makes `RecognizedItem.sku` and `RecognizedItemDto.sku` nullable; a null sku routes
   to `UnidentifiedItem(rawSku = null)` in `priceDraft`. `groupBy { it.sku }` (R2) handles a null
   key correctly — null is a valid group. (This is an "Ask first: changing the domain model" item
   — flagged in Boundaries and Open Questions.)
3. **R3 stops at the `RecognizedItem` boundary — exactly as R2 stopped boxes there.** The three
   new fields live on `RecognizedItem` up to the pricing boundary and are **not** threaded onto
   `DraftLine`/`UnidentifiedItem`, and **nothing renders them**. Threading them (with R2's boxes)
   into the draft model and the Draft Review UI is R7. R3's `priceDraft` change is limited to
   routing a null sku to `UnidentifiedItem`; it does not store `occluded`/`possiblyMore`/
   `alternates` on any draft type. (Alternative: thread them into `DraftLine` now — see Open
   Questions. Default is "stop at the boundary," matching R2.)
4. **Fields degrade gracefully when omitted.** `occluded` and `possiblyMore` default to `false`;
   `alternates` defaults to empty. A model that returns the R2-shaped body (`{sku, box,
   confidence}`) parses identically to R2 — the new fields are additive and optional.
5. **`alternates` are validated against the catalog; invalid entries are dropped, never fatal.**
   An alternate SKU not present in the active catalog is silently removed from the list (it can't
   power a swap chip). An empty result after filtering = no alternates. A malformed `alternates`
   value never fails the scan (SCAN-9), mirroring R2's malformed-box-degrades rule.
6. **`occluded` only informs (model lowers its own confidence per the policy); R3 adds no forced
   low-confidence flag.** The idea doc's *trust policy* that forces the low-confidence flag
   regardless of the model's number belongs to **confusion groups (R4)**, not R3. R3 keeps the
   existing `confidence < CONFIDENCE_THRESHOLD` rule (SCAN-6) unchanged. An occluded instance is
   expected to arrive with lower confidence because the prompt tells the model to lower it.
7. **`alternates` and a null `sku` are mutually exclusive in practice.** `alternates` are
   runners-up to a *chosen* SKU; a null sku means "couldn't identify at all." If a model returns
   both, R3 keeps the null-sku routing (→ unidentified) and ignores `alternates` on that
   detection. No special error.
8. **Default model only.** Built and verified against the configured default (`nemotron` free
   tier). Whether that model honors the new policies or returns usable `occluded`/`possiblyMore`/
   `alternates` is an R9-harness question; the graceful-degradation rule (Assumption 4) means a
   policy-ignoring model degrades to R2 behavior rather than regressing.

## Scope

**In:** the edge-case prompt wording (occlusion / stack / look-alike policies); three new
optional DTO fields (`occluded`, `possiblyMore`, `alternates`) and nullable `sku` on
`RecognizedItemDto`; the matching `RecognizedItem` fields + nullable `sku`; recognizer mapping
that populates them and validates `alternates`; the `priceDraft` change to route a null sku to
`UnidentifiedItem(rawSku = null)`; tests.

**Out (non-goals):** drawing badges/chips/crops or any Draft Review change, and threading the
new fields onto `DraftLine`/`UnidentifiedItem` (all R7); confusion groups, visual descriptions,
and the forced-flag trust policy (R4); reference-photo assembly changes (R1, done); per-instance
enumeration and SKU grouping themselves (R2 — R3 only extends them); two-pass crop escalation
(R5); capture-side quality gates (R6); model benchmarking/escalation (R8); the eval harness and
golden set (R9 — see Open Questions); correction telemetry (R10); any `DraftReceipt`/`DraftLine`/
Room/`ScanTelemetryEvent` schema change; any settings UI.

## Design

### Requested schema

R2 asks for one entry per physical instance with a box. R3 keeps that and adds optional
uncertainty fields plus a nullable `sku`:

```json
{"items": [
  {"sku": "SKU-0001", "box": [0.10, 0.22, 0.34, 0.51], "confidence": 0.92},
  {"sku": "SKU-0001", "box": [0.12, 0.18, 0.33, 0.27], "confidence": 0.55, "occluded": true},
  {"sku": "SKU-0003", "box": [0.40, 0.30, 0.62, 0.70], "confidence": 0.70, "possiblyMore": true},
  {"sku": "SKU-0007", "box": [0.66, 0.20, 0.88, 0.55], "confidence": 0.61, "alternates": ["SKU-0009"]},
  {"sku": null,       "box": [0.05, 0.60, 0.20, 0.80], "confidence": 0.30}
]}
```

- `occluded` / `possiblyMore` — booleans, default `false`.
- `alternates` — array of catalog SKUs, default empty; runners-up to the chosen `sku`.
- `sku: null` — "I can see an item here but cannot identify it" → routes to `UnidentifiedItem`.

### Prompt delta (`OpenRouterRecognizer.PROMPT`)

Add the three edge-case policies after the existing per-instance (R2) and reference-photo (R1)
wording. Indicative text (final wording tuned at implementation):

> **Occlusion:** Count an item if you can identify it from the visible part. If it is only
> partially visible, set `"occluded": true` and lower its confidence. If you can see an item but
> cannot identify which catalog product it is, return it with `"sku": null` and a box — do not
> guess and do not omit it.
>
> **Stacks:** Items may be stacked or in rows. Count each visible unit by its rim/edge/lid. If
> more units of the same product may be hidden behind or under what you can see, set
> `"possiblyMore": true` on one of that product's entries.
>
> **Look-alikes:** Some catalog products differ only by flavor, color, or size. When you are
> torn between specific SKUs, pick the best match and list the close runners-up in `"alternates"`
> (catalog SKUs only).

The R2 per-instance/box instruction, the R1 reference-photo wording, and the "use ONLY catalog
SKUs / never invent SKUs / never include prices" rules are unchanged. Response format stays
`json_object`.

### DTO (`data/recognizer/dto/ChatCompletionResponse.kt`)

```kotlin
@Serializable
public data class RecognizedItemDto(
    val sku: String? = null,             // R3: nullable — "saw it, can't identify" → unidentified
    val quantity: Int = 1,               // R2 aggregate fallback
    val confidence: Float = 0f,
    val box: List<Float>? = null,        // R2: [left, top, right, bottom], normalized 0..1
    val occluded: Boolean = false,       // R3
    val possiblyMore: Boolean = false,   // R3
    val alternates: List<String> = emptyList(), // R3: runner-up catalog SKUs
)
```

All new fields are optional with safe defaults, so R2-shaped and R1-shaped responses parse
unchanged.

### Domain model (`domain/recognizer/Recognizer.kt`)

```kotlin
public data class RecognizedItem(
    val sku: String?,                    // R3: nullable (null = identified-as-present-but-unknown)
    val quantity: Int,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,        // R2
    val occluded: Boolean = false,               // R3
    val possiblyMore: Boolean = false,           // R3
    val alternates: List<String> = emptyList(),  // R3
)
```

### Recognizer mapping (`OpenRouterRecognizer.recognize`)

```kotlin
payload.items.map { dto ->
    RecognizedItem(
        sku = dto.sku,
        quantity = dto.quantity,
        confidence = dto.confidence,
        boundingBox = dto.box?.toBoundingBoxOrNull(),   // R2
        occluded = dto.occluded,
        possiblyMore = dto.possiblyMore,
        alternates = dto.alternates.filter { it in catalogSkus },  // drop non-catalog alternates
    )
}
```

`catalogSkus` is the set of active catalog SKUs already available to `recognize`. Validation
never throws and never fails the scan (SCAN-9).

### Pricing (`domain/pricing/Pricing.kt`) — minimal change

R2 made `priceDraft` group detections by SKU. R3's only change is that a **null** sku is a valid
group routed to an unidentified entry:

- `groupBy { it.sku }` now includes a possible `null` key. The null group becomes
  `UnidentifiedItem(rawSku = null, quantity = group size, confidence = min)` — surfaced, never
  dropped (SCAN-7).
- `occluded` / `possiblyMore` / `alternates` are **not** read or stored by `priceDraft` in R3
  (Assumption 3). They remain on `RecognizedItem` for R7 to consume.

### What does NOT change

`Recognizer` interface signature, `BoundingBox` shape, `DraftLine`/`DraftReceipt`/Room schema,
`ScanCounter`, `DraftViewModel` and all of `ui/`, the R1 reference-photo assembly, and R2's box
parsing + aggregation. `FakeRecognizer` (SCAN-10) and `StubRecognizer` keep compiling — the new
`RecognizedItem` fields are defaulted, and their `sku` literals stay non-null.

## Tech Stack

Inherited from `docs/spec/SPEC.md`. No new dependencies (kotlinx.serialization handles the
optional fields; `alternates` validation is plain Kotlin).

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
  data/recognizer/dto/ChatCompletionResponse.kt   → add occluded/possiblyMore/alternates; sku nullable
  data/recognizer/OpenRouterRecognizer.kt          → PROMPT policies + map new fields + alternates validation
  domain/recognizer/Recognizer.kt                  → add fields to RecognizedItem; sku nullable
  domain/pricing/Pricing.kt                         → route null sku → UnidentifiedItem(rawSku = null)
app/src/test/java/com/cashierapp/photocheckout/
  domain/pricing/PricingTest.kt                     → null-sku routing; graceful-default cases
app/src/androidTest/java/com/cashierapp/photocheckout/
  data/recognizer/OpenRouterRecognizerTest.kt       → parse new fields, alternates filter, null-sku, prompt text
docs/spec/SPEC.md                                   → note R3 fields after approval (if needed)
```

## Code Style

Inherited (ktlint, explicit public API, KDoc citing criteria IDs, vendor detail only in
`data/recognizer/`, integer-minor-unit money). Mapping sketch — recognizer maps, pricing routes:

```kotlin
// OpenRouterRecognizer — alternates restricted to known catalog SKUs (Assumption 5).
val catalogSkus = catalog.mapTo(HashSet()) { it.sku }
...
alternates = dto.alternates.filter { it in catalogSkus }

// Pricing.kt — a null sku is the "seen but unidentifiable" channel (SCAN-7).
val item = sku?.let { catalog[it] }
if (item == null) {
    unidentified += UnidentifiedItem(rawSku = sku, quantity = quantity, confidence = confidence)
}
```

## Testing Strategy

`PricingTest` is JVM unit (L1); recognizer parsing is instrumented (L2 / SCAN-INT-4,
MockWebServer) where `OpenRouterRecognizerTest` already lives.

- **`OpenRouterRecognizerTest` (androidTest, MockWebServer):**
  - A response with `occluded`/`possiblyMore`/`alternates` parses into a `RecognizedItem`
    carrying those values.
  - `alternates` containing a non-catalog SKU → that SKU is filtered out; a catalog SKU is kept.
  - `sku: null` parses to `RecognizedItem(sku = null, …)` with its box populated.
  - An R2-shaped body (no new fields) parses with all three defaulting (`false`/`false`/empty) —
    backward compatible.
  - The outgoing prompt contains the occlusion / stack / look-alike policy text (assert wording);
    R1 reference-photo assembly and R2 per-instance text are unchanged.
  - Existing tests (HTTP error, timeout, missing key, R2 per-instance/box parse) pass unchanged.
- **`PricingTest` (JVM unit):**
  - A `RecognizedItem(sku = null)` produces an `UnidentifiedItem(rawSku = null)` with the right
    quantity/confidence; it is never dropped and never priced.
  - Mixed batch — identified instances + one null-sku detection — groups correctly on both sides;
    money math (X-1) on the identified lines is unchanged from R2.
  - `occluded`/`possiblyMore`/`alternates` on input do **not** alter any line's price, quantity,
    or `lowConfidence` (proves Assumption 3/6: R3 pricing is inert to the new fields).
- **Regression:** `ScanCounterTest`, `DraftViewModelTest`, `FakeRecognizer` flows (SCAN-10), and
  the draft/E2E suites pass untouched — proof the seam held and `ui/` never saw the new fields.
- **Not in CI:** whether the live default model honors the policies / returns useful
  `occluded`/`possiblyMore`/`alternates` — that is R9 (look-alike confusion rate, occlusion
  recall), informed but not gated here.

## Boundaries

Inherited from `docs/spec/SPEC.md`, plus R3-specific:

- **Always:** route a null `sku` to an `UnidentifiedItem` (never drop a seen-but-unidentified
  detection — SCAN-7); validate `alternates` against the active catalog; keep every new field
  optional with a safe default so policy-ignoring models degrade to R2 behavior; keep prices
  strictly from the catalog (X-1).
- **Ask first:** making `RecognizedItem.sku` nullable (a domain-model change — Assumption 2);
  threading `occluded`/`possiblyMore`/`alternates` onto `DraftLine`/`UnidentifiedItem` (that is
  R7); adding any forced low-confidence flag for occluded items (that is R4); changing the
  `Recognizer` signature.
- **Never:** let `occluded`/`possiblyMore`/`alternates` or a null sku influence price/total/tax;
  fail a scan because a new field was malformed (SCAN-9); surface raw per-instance data to the
  cashier (still R7); accept an `alternates` SKU into pricing as if it were the chosen line.

## Acceptance Criteria

- **R3-1:** The recognition prompt states explicit occlusion, stack, and look-alike policies
  (asserted in the outgoing request text), in addition to the R1/R2 wording.
- **R3-2:** The response DTO and `RecognizedItem` accept optional `occluded` (default false),
  `possiblyMore` (default false), and `alternates` (default empty); a response omitting all three
  parses identically to R2 (backward compatible).
- **R3-3:** `alternates` values not present in the active catalog are filtered out; a malformed
  `alternates` value never fails the scan (SCAN-9).
- **R3-4:** A `sku: null` detection parses to `RecognizedItem(sku = null)` and `priceDraft` routes
  it to `UnidentifiedItem(rawSku = null)` — surfaced, never dropped (SCAN-7), never priced.
- **R3-5:** The new fields do not affect any `DraftLine`'s price, quantity, or `lowConfidence`;
  pricing math (X-1) and the confidence rule (SCAN-6) are unchanged from R2.
- **R3-6:** The `Recognizer` interface, `DraftLine`/`DraftReceipt`/Room schema, `ScanCounter`, and
  all `ui/` code are unchanged (X-2); `FakeRecognizer` flows (SCAN-10) and the draft/E2E suites
  pass without edits; `StubRecognizer` still compiles.

## Success Criteria

- All acceptance criteria pass; new + existing unit and instrumented suites green;
  `ktlintCheck` and `detekt` clean.
- A manual device check shows the request prompt carrying the three policies and a scan with a
  partially-hidden, unidentifiable item surfacing as an unidentified entry rather than vanishing —
  a smoke check, not a benchmark.
- Recorded in the PR: for a few occlusion / stack / look-alike captures, whether the default model
  actually sets `occluded`/`possiblyMore`/`alternates` and how often (the signal the R9 harness
  will formalize), plus any request/latency delta from the longer prompt.

## Resolved Decisions

Settled at spec review (2026-06-13):

1. **Make `RecognizedItem.sku` (and `RecognizedItemDto.sku`) nullable (Open Q1 → Assumption 2).**
   The clean way to carry the `sku: null` "seen but unidentifiable" channel into the already
   -nullable `UnidentifiedItem.rawSku`. A sentinel string was rejected (leaks a magic value into
   every consumer). This is an accepted "Ask first" domain-model change.
2. **R3 stops at the `RecognizedItem` boundary (Open Q2 → Assumption 3).** The three new fields
   ride on `RecognizedItem` only; they are **not** added to `DraftLine`/`UnidentifiedItem` and
   nothing renders them. R7 threads them (with R2's boxes) into the draft model + UI together.
   Keeps R3 small, additive, with no draft-model or Room change — mirrors how R2 left boxes
   unpainted.
3. **No forced low-confidence flag for occluded instances (Open Q3 → Assumption 6).** The
   existing `confidence < CONFIDENCE_THRESHOLD` rule (SCAN-6) is untouched; the prompt tells the
   model to lower its own confidence for occluded items. The "force the flag regardless of the
   model's number" trust policy stays scoped to confusion groups (R4).
4. **R3 proceeds ahead of R9 (Open Q4 → Assumption 8).** As R1 and R2 did, R3 ships with
   unit/instrumented correctness coverage and records edge-case smoke numbers in its PR; formal
   accuracy measurement (look-alike confusion rate, occlusion recall) waits for the R9 harness.
5. **Full policy paragraphs in the prompt (Open Q5).** Use the spec's §Prompt delta wording — a
   clear paragraph per policy — to give the small default model the best chance of honoring them.
   The token/latency delta is recorded in the PR against the SCAN-3 budget; condense later only if
   the harness shows it is needed.

## Open Questions

None — resolved above. Next phase: Tasks, then Implement, per the plan
(`docs/plan/scan-r3-edge-case-prompt.plan.md`).
