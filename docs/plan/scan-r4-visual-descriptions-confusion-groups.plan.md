# Implementation Plan: R4 — Visual Descriptions + Confusion Groups (data-layer slice)

> Plan for `docs/spec/scan-r4-visual-descriptions-confusion-groups.md` (Draft, 2026-06-13 —
> scope-review decisions locked: data-layer slice + forced flag in, no UI / no auto-gen VLM call).
> Sequenced into thin, independently-testable slices per the increment cycle:
> implement → test → verify → commit → next slice.

## Key ordering insight

**R4 is independent of R2/R3.** It edits `catalogContext()` text and the `priceDraft` flag —
neither of which R2/R3 touch — and reads no `box`/`occluded`/`alternates` field (spec
Assumption 8). It composes with whatever recognizer shape is current and can land before or after
R2/R3 on `main`.

**Within R4, the schema change is the load-bearing, ripple-prone step and must land first.**
Adding `description`/`confusionGroup` to `CatalogItem` + `ProductEntity` and shipping
`Migration(1, 2)` (Slice 1) is what every other slice depends on: `priceDraft` (Slice 2) reads
`confusionGroup`, and `catalogContext()` (Slice 3) reads both. Both new fields default to `null`,
so Slice 1 is fully backward-compatible — undescribed/ungrouped items behave exactly as today, and
the forced flag (Slice 2) and prompt text (Slice 3) stay inert until data is actually populated.

**Branch:** `implement/scan-r4-visual-descriptions-confusion-groups` (off `main`)
**Pre-req each run:** `export JAVA_HOME=<java-21>` before `./gradlew` (Gradle needs JDK 17+).

> ✅ **Scope locked (spec Resolved Decisions, 2026-06-13):** data-layer slice only; forced-flag
> trust policy **in**; confusion group = shared nullable tag (no join table); forced flag requires
> ≥2 active members. No `ui/` change, no `insert(...)`/EnrollProduct change, no auto-gen VLM call.
> Remaining non-blocking open question: how descriptions/groups get seeded for the manual smoke
> check (propose a small seed helper) — does not gate any slice.

---

## Slice 1 — Domain + Room schema + migration (risk-first)

The load-bearing change (spec Assumptions 1, 4, 5). Pure model/persistence, fully
backward-compatible via `null` defaults. No prompt or pricing behavior changes yet — this slice
only makes the data *able* to carry descriptions/groups and persist them.

- **Files:** `domain/model/CatalogItem.kt`, `data/db/ProductEntity.kt`,
  `data/db/CashierDatabase.kt`, `di/AppModule.kt`, `data/db/ProductRepository.kt`
- **Change:**
  - `CatalogItem`: add `description: String? = null`, `confusionGroup: String? = null` (defaulted —
    every existing constructor/fixture compiles unchanged).
  - `ProductEntity`: add `description: String? = null`, `confusionGroup: String? = null` columns.
  - `CashierDatabase`: bump `@Database(version = 2)`; add `MIGRATION_1_2` running two
    `ALTER TABLE products ADD COLUMN … TEXT` (both nullable).
  - `AppModule.provideDatabase`: `.addMigrations(MIGRATION_1_2)` on the builder.
  - `ProductRepository`: `toDomain()` maps both columns onto `CatalogItem`; `update(CatalogItem)`
    writes them back. **`insert(...)` is unchanged** (new rows get `null`/`null`).
  - Regenerate the exported schema JSON (`app/schemas/.../2.json`).
- **Tests** (`ProductDaoTest.kt`, androidTest):
  - Migration 1→2 on a v1 DB seeded with a product row: the row survives and reads back with
    `description = null`, `confusionGroup = null`.
  - Insert then `update()` a product with non-null `description`/`confusionGroup` → values persist
    and read back identical (round-trip through `update()` + `toDomain()`).
  - Existing DAO tests stay green.
- **Verify:** `./gradlew app:connectedDebugAndroidTest` (migration test needs a device/emulator);
  `app:testDebugUnitTest` for the domain compile/regression.
- **Acceptance:** R4-1, R4-2
- **Commit:** `feat(catalog): add description and confusionGroup fields + Room migration 1→2 (R4)`

## Slice 2 — `priceDraft`: forced low-confidence flag for confusion-group SKUs

The only pricing change in R4. Pure domain, JVM-testable, leaves money math untouched
(spec Assumptions 2, 6, 7).

- **File:** `domain/pricing/Pricing.kt`
- **Change:** compute active confusion groups from `catalog.values` —
  tags that are non-blank and shared by ≥2 items. For each **identified** line, set
  `lowConfidence = detection.confidence < CONFIDENCE_THRESHOLD || inActiveConfusionGroup`. No
  signature change (the active `catalog` map is already a parameter). `UnidentifiedItem` (SCAN-7)
  is untouched.
- **Tests** (`PricingTest.kt`, JVM unit):
  - Two active items sharing a non-blank `confusionGroup` → both detected lines `lowConfidence =
    true` even at confidence `0.95` (forced).
  - Singleton tag (one active member) and `null`/blank tag → no forced flag; SCAN-6 threshold alone
    decides.
  - Grouped SKU whose sibling is deactivated (absent from the active `catalog` map) → not flagged
    (group size < 2).
  - Forced flag changes no price/quantity/line total/subtotal/tax (X-1); `UnidentifiedItem`
    unaffected.
  - All existing `PricingTest` cases stay green.
- **Verify:** `./gradlew app:testDebugUnitTest`
- **Acceptance:** R4-4, R4-5
- **Commit:** `feat(pricing): force low-confidence flag for confusion-group SKUs (R4)`

## Slice 3 — `catalogContext()`: description suffix + look-alike group block

The recognizer learns to *emit* the new signals. Behavior is data-driven and degrades to today's
exact output when nothing is described/grouped (spec §Catalog context).

- **File:** `data/recognizer/OpenRouterRecognizer.kt`
- **Change:**
  - In `catalogContext()`, append `| <description>` after `SKU - name` only when `description` is
    non-null/non-blank.
  - After the catalog list, append a "Look-alike groups" block listing member SKUs for every
    confusion group with ≥2 active members; omit the block entirely when none qualifies (output is
    byte-for-byte today's plus any description suffixes).
  - Add one sentence to `PROMPT` acknowledging the description / look-alike lines; keep the "ONLY
    catalog SKUs / never invent / never include prices" rules and R1 reference-photo wording.
    `responseFormat` stays `json_object`.
- **Tests** (`OpenRouterRecognizerTest.kt`, androidTest/MockWebServer):
  - Outgoing catalog text contains `SKU-0001 - Choco Wafer | <description>` for a described item
    and omits the `| …` suffix for an undescribed one.
  - Two items sharing a group tag → the "Look-alike groups" block lists both SKUs; no qualifying
    group → block omitted.
  - Existing tests (R1 reference-photo assembly, HTTP error, timeout, missing key) stay green.
- **Verify:** `./gradlew app:connectedDebugAndroidTest`
- **Acceptance:** R4-3
- **Commit:** `feat(recognizer): emit visual descriptions and look-alike groups in catalog context (R4)`

## Slice 4 — Regression gate, docs, manual smoke, PR metrics

No new behavior — proof the seam held + close-out.

- **Regression:** `ScanCounterTest`, `DraftViewModelTest`, `FakeRecognizer` flows (SCAN-10),
  draft/E2E suites pass untouched (R4-6). Full
  `./gradlew app:testDebugUnitTest app:connectedDebugAndroidTest`, then
  `app:ktlintFormat && app:ktlintCheck` and `app:detekt` clean. Confirm the exported Room schema is
  committed at version 2.
- **Seed helper (open question):** add a minimal dev seed (or test-only fixture) that sets a
  description + shared group tag on two look-alike products so the smoke check can be staged without
  R4-UI. Keep it out of the production enrollment path.
- **Docs:** note the R4 fields in `docs/spec/SPEC.md` if needed; mark the spec implemented.
- **Manual smoke:** device check — seed two look-alike products with descriptions and a shared
  group tag, scan them, confirm (a) the request carries the description + group lines and (b) both
  draft lines show the low-confidence flag even at high reported confidence.
- **PR body:** record the request/token delta from the added text against the SCAN-3 budget and any
  qualitative change in look-alike misreads on a few captures (Success Criteria; pre-R9 signal).
- **Commits:** `docs: mark R4 visual descriptions + confusion groups implemented` (+ a separate
  commit for any SPEC.md amend — one logical thing per commit).

---

## Acceptance coverage

| Acceptance | Slice |
|---|---|
| R4-1 fields + migration 1→2, v1 row survives | S1 |
| R4-2 round-trip via update()/toDomain(), insert unchanged | S1 |
| R4-3 catalogContext description suffix + group block | S3 |
| R4-4 forced flag for active group ≥2 | S2 |
| R4-5 flag inert to money math; SCAN-6/SCAN-7 unchanged | S2 |
| R4-6 seam/UI untouched | S4 |

Each slice leaves the build green and is independently revertible. Slice 2 is JVM-only (fast);
Slices 1 and 3 need a device/emulator for the migration + MockWebServer instrumented tests.
