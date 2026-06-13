# Spec: R4 — Visual Descriptions + Confusion Groups (data-layer slice)

> Source idea: `docs/ideas/scan-accuracy-improvements.md` (R4 only)
> Parent spec: `docs/spec/SPEC.md` (Photo Checkout) — this spec inherits its commands,
> project structure, code style, and boundaries; only deltas are stated here.
> Sibling specs: `docs/spec/scan-r1-reference-photos.md` (R1, implemented),
> `docs/spec/scan-r2-per-instance-detections.md` (R2),
> `docs/spec/scan-r3-edge-case-prompt.md` (R3). R4 is **independent of R2/R3** — it touches
> `catalogContext()` text and `priceDraft`'s flag, neither of which R2/R3 change. It composes
> with whatever recognizer shape is current (aggregate or per-instance).
> Status: Draft — pending review. Date: 2026-06-13

## Objective

Give the model and the trust policy two cheap, persisted signals for the **similar-packaging**
edge case (look-alike flavor/color/size variants):

1. **Per-SKU visual description** — a short distinguishing-feature line stored on each catalog
   item (`brown wrapper, red "CHOCO" band, 24g sachet`) that `catalogContext()` appends after the
   SKU name, so the model anchors on *our* packaging in text instead of imagining what a bare name
   looks like.
2. **Confusion groups** — a tag marking a set of look-alike SKUs. Two uses: (a) the prompt warns
   the model that those SKUs are near-identical and must be distinguished carefully; (b) a **trust
   policy** — when a detected SKU belongs to a confusion group with ≥2 active members, `priceDraft`
   **forces the low-confidence flag** regardless of the model's reported confidence. Models are
   reliably *overconfident* between near-identical variants, so group membership is a better
   uncertainty signal than the model's own number.

**This spec is the data-layer slice only** (decided at scope review — see Resolved Decisions):
Room schema fields + `catalogContext()` enrichment + the forced-flag change. The fields are
populated through the existing repository round-trip (and seeds/tests); **no new enrollment/detail
UI** to edit a description or tag a group, and **no one-time VLM call** to auto-generate
descriptions. Those two are explicitly deferred to later R4 slices (R4-UI, R4-autogen).

**Why now:** the idea doc pairs R1 (done) with R4 as sequencing step 3 — cheap text-token upgrades
that move the similar-packaging case measurably while staying close to `data/recognizer/`. The
forced flag is the single highest-value behavioral lever in R4 (idea doc §R4.2).

**User:** The cashier, indirectly. R4 ships **one visible behavioral change**: a detected line
whose SKU is in a populated confusion group is flagged low-confidence even when the model was
confident — turning a likely look-alike misread into a "check this" cue (SCAN-6) the cashier
already knows how to act on. The description text and group prompt-hint are invisible to the
cashier; they only change what the model sees. Rendering a *distinct* "verify identity" badge,
the `alternates` swap chip, and any editing UI are R7/later slices.

**Success looks like:** `catalogContext()` emits a description line for every item that has one and
a group-warning line for every confusion group with ≥2 active members; `priceDraft` forces
`lowConfidence = true` for any line whose SKU is in such a group; items without a description or
group behave exactly as today (graceful degradation); the Room migration adds the two nullable
columns without data loss; no pricing-math change (X-1); no `Recognizer` signature change (X-2).

## Assumptions

Resolved from the code, the idea doc, and the R1–R3 specs; correct them at review if wrong.

1. **Confusion group = a shared nullable string tag, not a join table.** A group is modeled as a
   nullable `confusionGroup: String?` on the catalog item. Two SKUs are in the same group iff they
   carry the **same non-null, non-blank** tag. This avoids a new table/relation for the MVP; a
   richer model (R10 auto-derived groups from correction telemetry) can migrate to a join later. A
   `null`/blank tag means "ungrouped."
2. **A "confusion group" only matters at size ≥ 2 active members.** A lone tagged item is not a
   look-alike risk: it gets no prompt warning and does **not** trigger the forced flag. Membership
   count is computed over the **active catalog** the scan already loads (deactivated items, CAT-6,
   don't count). This keeps the forced flag from firing on a singleton tag left over after a sibling
   was deactivated.
3. **Description is free text, ≤ ~120 chars, advisory only.** It is injected verbatim into the
   prompt after `SKU - name`. It never affects pricing, SKU mapping, or validation. A null/blank
   description simply omits the `| …` suffix for that item.
4. **Fields are populated via the existing repository round-trip + seeds/tests — no new UI.**
   `CatalogItem` gains `description` and `confusionGroup`; `ProductRepository.update(CatalogItem)`
   already persists a whole `CatalogItem`, so the new fields round-trip through `update()` and
   `toDomain()` with no signature change. `insert(...)` is **unchanged** — new products start with
   `null`/`null` and are enriched later (R4-UI or a seed). The S4 enrollment flow is untouched.
5. **Room schema goes v1 → v2 with an additive migration; no destructive fallback.** `exportSchema`
   is on and no migrations exist yet. R4 adds `Migration(1, 2)` that runs two `ALTER TABLE products
   ADD COLUMN … TEXT` statements (both nullable, default null) and registers it via
   `.addMigrations(...)` in `AppModule`. Existing rows survive; the exported schema JSON updates to
   version 2. (This is an "Ask first: database schema change" item — approved at scope review.)
6. **The forced flag reuses the existing `DraftLine.lowConfidence` channel — no new field.** R4
   ORs group membership into the existing flag: `lowConfidence = confidence < CONFIDENCE_THRESHOLD
   || skuInActiveConfusionGroup`. It does **not** add a separate "verify identity vs verify count"
   badge — that distinct *action* is R7. R4's visible effect is strictly "more lines flagged."
7. **`priceDraft` needs no new parameter.** It already receives `catalog: Map<String, CatalogItem>`
   (the active catalog). Since `CatalogItem` now carries `confusionGroup`, `priceDraft` computes
   group sizes from `catalog.values` and forces the flag locally — no signature change, X-2 intact.
8. **R4 is recognizer-shape-agnostic.** It edits `catalogContext()` (text) and the `priceDraft`
   flag. It does not read or write `box`/`occluded`/`possiblyMore`/`alternates` (R2/R3) and does not
   depend on per-instance detections. It works whether the recognizer returns aggregate quantities
   or per-instance rows.

## Scope

**In:** two nullable columns on `ProductEntity` (`description`, `confusionGroup`) + `Migration(1,2)`
+ `AppModule` registration + exported-schema bump; the matching `CatalogItem.description` /
`CatalogItem.confusionGroup` fields and `ProductWithPhotos.toDomain()` mapping; `ProductRepository.update`
persisting the two fields; `catalogContext()` emitting a description suffix and group-warning lines;
`priceDraft` forcing `lowConfidence` for SKUs in an active confusion group (≥2 members); tests.

**Out (non-goals):** any enrollment/detail UI to edit a description or tag a confusion group (R4-UI);
the one-time VLM call that auto-generates a description from reference photos (R4-autogen);
auto-deriving confusion groups from cashier-correction telemetry (R10); a distinct "verify identity"
badge / `alternates` swap chip / any Draft Review change (R7); reference-photo assembly changes
(R1, done); per-instance boxes (R2); edge-case prompt policies / `occluded` / `possiblyMore` /
`alternates` (R3); two-pass crop escalation (R5); capture-side gates (R6); model
benchmarking/escalation (R8); the eval harness and golden set (R9); any `DraftReceipt`/`DraftLine`
field addition, `ScanCounter`, `ScanTelemetryEvent`, or settings-UI change; changing `insert(...)`
or the EnrollProduct use case.

## Design

### Domain model (`domain/model/CatalogItem.kt`)

```kotlin
public data class CatalogItem(
    val id: Long,
    val sku: String,
    val name: String,
    val priceMinor: Long,
    val active: Boolean,
    val photos: List<ProductPhoto>,
    val createdAtEpochMillis: Long,
    val deactivatedAtEpochMillis: Long? = null,
    val description: String? = null,      // R4: distinguishing-feature line, advisory
    val confusionGroup: String? = null,   // R4: shared tag → look-alike group
)
```

Both default to `null`, so every existing constructor call, `StubRecognizer`/`FakeRecognizer`
fixture, and test compiles unchanged.

### Room (`data/db/ProductEntity.kt`, `CashierDatabase.kt`, `di/AppModule.kt`)

```kotlin
@Entity(tableName = "products", indices = [Index(value = ["sku"], unique = true)])
public data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sku: String,
    val name: String,
    val priceMinor: Long,
    val active: Boolean,
    val createdAt: Long,
    val deactivatedAt: Long?,
    val description: String? = null,      // R4
    val confusionGroup: String? = null,   // R4
)
```

`@Database(version = 2, exportSchema = true)`. Additive migration:

```kotlin
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE products ADD COLUMN confusionGroup TEXT")
    }
}
// AppModule.provideDatabase: Room.databaseBuilder(...).addMigrations(MIGRATION_1_2).build()
```

The exported schema JSON (`app/schemas/.../2.json`) is regenerated. `ProductWithPhotos.toDomain()`
maps the two new columns onto `CatalogItem`; `ProductRepository.update(CatalogItem)` writes them
back. `insert(...)` is untouched (new rows get `null`/`null`).

### Catalog context (`OpenRouterRecognizer.catalogContext`)

```
Catalog:
SKU-0001 - Choco Wafer | brown wrapper, red "CHOCO" band, 24g sachet
SKU-0007 - Vanilla Wafer | cream wrapper, blue "VANILLA" band, 24g sachet
SKU-0003 - Mineral Water 600ml

Look-alike groups (distinguish carefully by label color/text):
- SKU-0001, SKU-0007
```

Rules:
- Description suffix `| <description>` is appended only when `description` is non-null and non-blank.
- The "Look-alike groups" block is appended only when at least one confusion group has **≥2 active
  members**; each such group is listed as its member SKUs. Singleton/blank tags are skipped. If no
  qualifying group exists, the block is omitted entirely (byte-for-byte today's output plus any
  description suffixes).
- `PROMPT` gains one sentence acknowledging the description/look-alike lines (final wording tuned at
  implementation); the "use ONLY catalog SKUs / never invent SKUs / never include prices" rules and
  the R1 reference-photo wording are unchanged. Response format stays `json_object`.

### Pricing trust policy (`domain/pricing/Pricing.kt`)

```kotlin
// Active confusion groups = tags shared by ≥2 active catalog items.
val groupSizes = catalog.values
    .mapNotNull { it.confusionGroup?.takeIf(String::isNotBlank) }
    .groupingBy { it }.eachCount()

// per line:
val inConfusionGroup = item.confusionGroup
    ?.takeIf(String::isNotBlank)
    ?.let { (groupSizes[it] ?: 0) >= 2 } == true
lowConfidence = detection.confidence < CONFIDENCE_THRESHOLD || inConfusionGroup
```

Only **identified** lines (SKU resolves to a catalog item) can be force-flagged — an
`UnidentifiedItem` (SCAN-7) has no group and is unchanged. Money math (X-1), quantities, unit/line
totals, subtotal, and tax are untouched.

### What does NOT change

`Recognizer` interface signature, `BoundingBox`, R2/R3 fields, `DraftLine`/`DraftReceipt` shape,
`ScanCounter`, `DraftViewModel` and all of `ui/`, `insert(...)`/EnrollProduct, the R1 reference-photo
assembly, and `CONFIDENCE_THRESHOLD` (SCAN-6). `FakeRecognizer`/`StubRecognizer` keep compiling
(new `CatalogItem` fields are defaulted).

## Tech Stack

Inherited from `docs/spec/SPEC.md`. No new dependencies (Room migration uses existing
`androidx.room.migration.Migration`; everything else is plain Kotlin + kotlinx.serialization already
present).

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
  domain/model/CatalogItem.kt              → add description, confusionGroup (defaulted)
  data/db/ProductEntity.kt                 → add description, confusionGroup columns
  data/db/CashierDatabase.kt               → version = 2; MIGRATION_1_2
  data/db/ProductRepository.kt             → map both fields in toDomain() + update()
  di/AppModule.kt                          → .addMigrations(MIGRATION_1_2)
  data/recognizer/OpenRouterRecognizer.kt  → catalogContext() description suffix + group block; PROMPT note
  domain/pricing/Pricing.kt                → force lowConfidence for active-confusion-group SKUs
app/src/test/java/com/cashierapp/photocheckout/
  domain/pricing/PricingTest.kt            → forced-flag cases; singleton/ungrouped no-op
app/src/androidTest/java/com/cashierapp/photocheckout/
  data/db/ProductDaoTest.kt                → migration 1→2 + read/write new columns
  data/recognizer/OpenRouterRecognizerTest.kt → catalogContext description + group block in request
app/schemas/.../2.json                     → regenerated exported schema
docs/spec/SPEC.md                          → note R4 fields after approval (if needed)
```

## Code Style

Inherited (ktlint, explicit public API, KDoc citing criteria IDs, vendor detail only in
`data/recognizer/`, integer-minor-unit money). Pricing forces the flag; context formats text:

```kotlin
// OpenRouterRecognizer.catalogContext — description is advisory, omitted when blank.
catalog.forEach { item ->
    append(item.sku).append(" - ").append(item.name)
    item.description?.takeIf(String::isNotBlank)?.let { append(" | ").append(it) }
    append('\n')
}
```

## Testing Strategy

`PricingTest` is JVM unit (L1); DAO/migration and recognizer-request assertions are instrumented
(L2/L3 — `ProductDaoTest`, `OpenRouterRecognizerTest` already live there).

- **`PricingTest` (JVM unit):**
  - Two active items sharing a non-blank `confusionGroup` → both detected lines get
    `lowConfidence = true` even at confidence `0.95` (forced flag).
  - A singleton tag (only one active member) and a `null`/blank tag → **no** forced flag; the
    SCAN-6 threshold rule alone decides `lowConfidence`.
  - A grouped SKU whose sibling is **deactivated** (absent from the active `catalog` map) → not
    force-flagged (group size < 2).
  - Forced flag never changes price/quantity/line total/subtotal/tax (X-1); an `UnidentifiedItem`
    is unaffected.
- **`ProductDaoTest` (androidTest):**
  - Migration 1→2 on a v1 DB seeded with a row: the row survives and reads back with
    `description = null`, `confusionGroup = null`.
  - Insert/update a product with non-null `description`/`confusionGroup` → values persist and read
    back identical (round-trip through `update()` + `toDomain()`).
- **`OpenRouterRecognizerTest` (androidTest, MockWebServer):**
  - The outgoing request's catalog text contains `SKU-0001 - Choco Wafer | <description>` for an
    item with a description, and omits the `| …` suffix for one without.
  - A catalog with two items sharing a group tag emits the "Look-alike groups" block listing both
    SKUs; a catalog with no qualifying group omits the block entirely.
  - Existing tests (reference-photo assembly R1, HTTP error/timeout, missing key) pass unchanged.
- **Regression:** `ScanCounterTest`, `DraftViewModelTest`, `FakeRecognizer` flows (SCAN-10), and the
  draft/E2E suites pass untouched — proof the seam held and `ui/` never saw the new fields.
- **Not in CI:** whether the description text / group warning actually lowers the live model's
  look-alike confusion rate — that is R9 (look-alike confusion metric), informed but not gated here.

## Boundaries

Inherited from `docs/spec/SPEC.md`, plus R4-specific:

- **Always:** keep prices strictly from the catalog (X-1, SCAN-4); force the low-confidence flag
  only for identified lines whose SKU is in an **active** group of ≥2 (Assumption 2); treat
  description/group as advisory text that never alters SKU mapping or money; keep both fields
  optional with `null` defaults so ungrouped/undescribed items behave exactly as today.
- **Ask first:** the Room schema change (v1→2 migration — approved at scope review); adding any new
  `DraftLine`/`DraftReceipt` field or a distinct "verify identity" badge (that is R7); changing
  `insert(...)`/EnrollProduct or adding enrollment UI (that is R4-UI); changing the `Recognizer` or
  `priceDraft` signatures.
- **Never:** let a confusion group or description influence price/total/tax or override the catalog
  SKU mapping; force-flag an `UnidentifiedItem` or a singleton/ungrouped line; ship a destructive
  Room migration that drops existing products; surface raw group/description text to the cashier in
  R4 (still R7).

## Acceptance Criteria

- **R4-1:** `CatalogItem`, `ProductEntity`, and the Room schema carry nullable `description` and
  `confusionGroup`; `Migration(1, 2)` adds both columns additively and an existing v1 row survives
  with both reading `null`.
- **R4-2:** `description`/`confusionGroup` round-trip through `ProductRepository.update()` and
  `toDomain()`; `insert(...)`/EnrollProduct are unchanged and produce `null`/`null`.
- **R4-3:** `catalogContext()` appends `| <description>` for items that have one (and only those),
  and emits a "Look-alike groups" block listing member SKUs for every confusion group with ≥2 active
  members (and omits the block when none qualifies).
- **R4-4:** `priceDraft` forces `lowConfidence = true` for any identified line whose SKU is in an
  active confusion group (≥2 members), regardless of reported confidence; singleton, ungrouped, and
  deactivated-sibling cases are not force-flagged.
- **R4-5:** The forced flag changes no line's price, quantity, or totals; pricing math (X-1) and the
  `CONFIDENCE_THRESHOLD` rule for the non-forced path (SCAN-6) are unchanged; `UnidentifiedItem`
  handling (SCAN-7) is unchanged.
- **R4-6:** The `Recognizer` and `priceDraft` signatures, `DraftLine`/`DraftReceipt` shape,
  `ScanCounter`, and all `ui/` code are unchanged (X-2); `FakeRecognizer` flows (SCAN-10) and the
  draft/E2E suites pass without edits; `StubRecognizer` still compiles.

## Success Criteria

- All acceptance criteria pass; new + existing unit and instrumented suites green; `ktlintCheck` and
  `detekt` clean; the exported Room schema is committed at version 2.
- A manual device check: seed two look-alike products with descriptions and a shared group tag, scan
  them, and confirm (a) the outgoing request carries the description + group lines and (b) both draft
  lines show the low-confidence flag even when the model reported high confidence — a smoke check,
  not a benchmark.
- Recorded in the PR: the request/token delta from the description + group text against the SCAN-3
  budget, and any qualitative change in look-alike misreads on a few captures (the signal the R9
  harness will formalize).

## Resolved Decisions

Settled at scope review (2026-06-13):

1. **Data-layer slice only.** R4 ships the Room fields + `catalogContext()` enrichment + forced
   flag. Enrollment/detail UI for editing descriptions and tagging groups (R4-UI) and the one-time
   VLM auto-generate-description call (R4-autogen) are deferred to separate slices, keeping R4 close
   to the R1–R3 footprint (no `ui/` change, one bounded Room migration).
2. **Include the forced-flag trust policy.** The forced low-confidence flag for confusion-group
   members is the highest-value lever in R4 and is in scope (Assumption 6). It reuses the existing
   `lowConfidence` channel; the distinct "verify identity" badge stays R7.
3. **Confusion group = shared nullable tag, not a join table (Assumption 1).** Simplest model that
   carries the signal; a relational model can come with R10's auto-derived groups.
4. **Forced flag requires ≥2 active members (Assumption 2).** A singleton or deactivated-sibling tag
   is not a look-alike risk and must not flag.

## Open Questions

- **Group tag provenance for now.** With no R4-UI, how do descriptions/groups get set in practice
  during the prototype — a dev seed, a temporary debug action, or only via tests? (Does not block the
  data-layer code; affects only how the manual smoke check is staged.) → propose a small seed helper.
- **Description length cap.** Hard-cap at the data layer (e.g. truncate > ~120 chars) or trust the
  future editor UI to constrain it? (Default: no hard cap in R4; revisit with R4-UI.)
- Next phase: Tasks, then Implement — to be captured in `docs/plan/scan-r4-visual-descriptions-confusion-groups.plan.md`.
