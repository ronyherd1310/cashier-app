# Spec: Photo Checkout (Visual Cashier)

> Source idea: `docs/ideas/photo-checkout.md`
> Status: Living document. Specify complete; Catalogue plan decisions synced.
> Last updated: 2026-06-06

## Objective

An Android app for a cashier at a counter. The cashier photographs a group of **our own catalog products** (which have **no barcodes**), and the app returns an **itemized, priced draft receipt** that the cashier confirms or edits before committing the sale.

**Why:** Items have no barcodes, so they can't be scanned conventionally. Manual entry is slow and error-prone for multi-item orders. A single photo collapses that into one step.

**Primary user:** A cashier ringing up a customer's order at a fixed counter, indoors, on a single Android phone.

**Two rules that define the product (non-negotiable):**
1. **The recognizer identifies; the database prices.** The vision model returns only `{sku, quantity, confidence}`. The app applies prices from the local catalog and computes the total. The model never sees or sets prices.
2. **Draft, don't decide.** The app proposes a draft receipt; the cashier confirms, edits a quantity, removes a wrong item, or adds a missed one before committing. Sales never auto-commit.

**Architecture seam:** Recognition sits behind a single `Recognizer` interface (image + catalog in → `[{sku, qty, confidence, bbox?}]` out). Phase 1 implements it with a cloud vision LLM; Phase 2 adds an on-device implementation to drive per-scan cost toward zero. The vendor is a swappable config choice — never hardcoded in domain or UI.

### Acceptance criteria (Phase 1 / MVP)

The MVP is organized into three modules — **Catalogue**, **Scan**, **Sales** — plus cross-cutting criteria that span all three. Each criterion is independently testable.

#### Module 1 — Catalogue
*Manage the closed set of products, their prices, and reference photos. This module is the prerequisite for Scan: nothing can be recognized or priced without it.*

- **CAT-1:** A cashier can create a product with a name, a price (IDR), and one reference photo captured via an in-app freeform camera flow. (MVP captures a single photo; the data model supports more for Phase 2.)
- **CAT-2:** Each product is assigned a stable, unique SKU id at creation — format: fixed `SKU-` prefix + 4-digit zero-padded counter (e.g. `SKU-0001`), immutable. This is the id the `Recognizer` maps detections to and the catalog uses for pricing.
- **CAT-3:** Prices are entered and stored as integer minor units; input is validated (non-negative, no floating-point drift, currency-aware).
- **CAT-4:** Reference photos are stored on-device, linked to the product, and reusable as Phase-1 prompt context and the Phase-2 embedding gallery. MVP enrollment captures one photo; the detail screen can add up to **3** total (schema supports multiple).
- **CAT-5:** The cashier can view, search (by name and SKU), filter (status), and sort the catalog list, and open a product to edit its name, price, and photos.
- **CAT-6:** The cashier can deactivate (soft-delete) a product so it no longer appears in scan/draft results, while preserving its presence in historical sales; deactivated products are viewable via the Inactive filter and can be **reactivated**.
- **CAT-7:** The catalog persists locally (Room), survives app restart, and remains responsive with 50–300 active items.

#### Module 2 — Scan
*Photograph the counter, recognize catalog items, and build an editable, priced draft receipt. Recognition output is identity + quantity + confidence only; pricing is deterministic.*

- **SCAN-1:** Tapping "Scan" opens a CameraX single-photo capture of the counter (items spread flat, non-overlapping, top-down); camera-permission denial is handled with a clear, recoverable prompt (no crash). Multi-shot capture is out of MVP.
- **SCAN-2:** The captured image is downscaled before recognition to control cost and latency.
- **SCAN-3:** Recognition runs through the `Recognizer` interface and returns within a usable latency budget (target ≤ ~5s), with an explicit loading state.
- **SCAN-4:** Recognized SKUs are mapped to the catalog and a draft receipt is built deterministically — unit prices and line totals come from the catalog, never from the model.
- **SCAN-5:** Each draft line shows item name, quantity, unit price, and line total; the draft shows subtotal and total (no tax in MVP).
- **SCAN-6:** Lines below the confidence threshold are visually flagged as low-confidence.
- **SCAN-7:** Detections that don't map to an active catalog item surface as an "unidentified item" prompt — no detected item is ever silently dropped.
- **SCAN-8:** Before confirming, the cashier can edit a line's quantity, remove a line, and manually add a catalog item to the draft.
- **SCAN-9:** Empty/error states (no items found, provider/network error, timeout) resolve to a clear, recoverable state with no partial sale committed.
- **SCAN-10:** A `FakeRecognizer` can drive the entire capture→draft→edit flow in tests with no network or live model.

#### Module 3 — Sales
*Commit a confirmed draft into an immutable sale, produce a receipt, and keep history. Sales never auto-commit.*

- **SALE-1:** A sale is committed only by an explicit cashier confirmation action on the draft — there is no path that auto-commits.
- **SALE-2:** A committed sale persists locally with its line items, the unit prices captured at sale time, computed subtotal/tax/total, and a timestamp.
- **SALE-3:** Prices on a committed sale are a snapshot — later edits to catalog prices do not alter any historical receipt.
- **SALE-4:** After commit, a receipt is displayed and can be re-viewed and shared via the system share sheet.
- **SALE-5:** Discarding a draft before confirmation leaves no sale record.
- **SALE-6:** A sales-history list shows past sales and lets the cashier open any sale to view its receipt detail.

#### Cross-cutting
*Properties that must hold across all three modules.*

- **X-1 (Money integrity):** All prices and totals are computed in the domain layer in integer minor currency units; floating-point money is never used.
- **X-2 (Vendor-agnostic):** Swapping the recognition provider requires changing only configuration plus one `Recognizer` implementation — no changes to `domain/`, `pricing/`, or `ui/`.
- **X-3 (Telemetry):** Per-scan cloud cost, latency, and item-level accuracy are logged for every scan during the prototype window (feeds the accuracy benchmark and go/no-go decision).

## Screens & UI

**8 primary screens + 1 app shell + 4 reusable components.** Navigation host is a bottom-nav scaffold with five tabs (Home · Catalogue · Scan · Sales · More); Settings lives under More. Screens map to the three modules; the camera capture flow and catalog picker are shared.

### App shell
- **Shell — Bottom-nav scaffold.** Hosts five tabs — Home (default landing) · Catalogue · Scan · Sales · More — and holds navigation state; no business logic. Catalogue → S3, Scan → S1, Sales → S6, More → S7 (Settings). The Home landing screen's content is not yet specified (placeholder for MVP).

### Catalogue module
- **S3 — Catalog List.** *Purpose:* browse/manage the product catalog (CAT-5, CAT-7).
  *Interactions:* top bar with title + live "N active items" count and two trailing icons (search, filter-funnel); full-width **"+ Add Product"** button; search field (matches name + SKU); scrollable product cards (thumbnail, name, SKU, IDR price, Active badge, ⋮ overflow); tap a row → S4·Detail; ⋮ → Edit / Deactivate (CAT-6, confirm dialog). The filter icon opens a **Filter & Sort sheet** (Status: All/Active/Inactive; Sort: Name A–Z/Z–A, Price ↑/↓, Newest/Oldest); Inactive Only surfaces deactivated items for reactivation. Empty state prompts first enrollment.
- **S4 — Add Product (3-step wizard).** *Purpose:* create a product (CAT-1–CAT-4).
  *Interactions:* stepper Basic Info → Pricing → Review. Step 1: reference-photo capture (C1) + name + live SKU preview (`SKU-000N`); Step 2: IDR price input + preview (CAT-3, X-1); Step 3: review → Save (runs EnrollProduct, generates SKU — CAT-2) → back to S3. Back/cancel discards with no write.
- **S4·Detail — Product Detail & Edit.** *Purpose:* view/edit a product and manage photos (CAT-4, CAT-5, CAT-6).
  *Interactions:* photo carousel (≤3, "1/3" indicator), name, read-only SKU, price with inline Edit, Photos section (add up to 3 / remove), **Edit Product**, and **Deactivate** (red, confirm). An inactive product instead shows **Reactivate**.

### Scan module
- **S1 — Scan Capture.** *Purpose:* capture one counter photo (SCAN-1, SCAN-2).
  *Interactions:* large "Scan" shutter; live CameraX preview with framing hint ("spread items flat"); camera-permission request/denied state (recoverable); on capture → downscale → show processing overlay (C3) while `Recognizer` runs (SCAN-3) → S2. Provider/network error → recoverable error state (SCAN-9), no navigation to draft.
- **S2 — Draft Review.** *Purpose:* review, edit, and confirm the priced draft (SCAN-4–SCAN-8, SALE-1).
  *Interactions:* list of draft lines (item, qty, unit price, line total); low-confidence lines visually flagged (SCAN-6, threshold 0.6); "unidentified item" entries surfaced for resolution (SCAN-7); per-line edit qty / remove; "Add item" → Catalog picker (C2) for manual add; subtotal + total (no tax); **Confirm** → commit sale → S5; **Discard** → confirm dialog → back to S1, no record (SALE-5).

### Sales module
- **S5 — Receipt.** *Purpose:* show a committed sale's receipt (SALE-2, SALE-4); reused as history detail.
  *Interactions:* itemized receipt with snapshot prices + totals + timestamp; **Share** via system share sheet; "Done" → S1 (after commit) or back → S6 (from history).
- **S6 — Sales History.** *Purpose:* list past sales (SALE-6).
  *Interactions:* reverse-chronological list (timestamp, item count, total); tap a sale → S5 (detail, read-only snapshot — SALE-3). Empty state when no sales yet.

### Settings
- **S7 — Settings.** *Purpose:* configuration the app can't run without (X-2).
  *Interactions:* OpenRouter API key entry (stored in secure storage, masked); model id field (config string, e.g. `google/gemini-...`); optional confidence-threshold override (default 0.6); shows currency = IDR (read-only in MVP). Validates key presence before Scan can call the provider.

### Reusable components (not standalone screens)
- **C1 — Camera capture flow.** Shared CameraX capture used by both S1 (counter scan) and S4 (reference photo). Returns an image to the caller; centralizes permission handling.
- **C2 — Catalog picker (bottom sheet).** Searchable active-catalog list for manual "Add item" in S2; returns a selected SKU.
- **C3 — Processing overlay.** Loading state shown during recognition (S1→S2) and other async waits; cancelable where safe.
- **C4 — Confirm dialogs.** Reusable confirm/discard dialogs (deactivate product, discard draft).

> Out of MVP (noted as seams): a dedicated variant-disambiguation screen, multi-shot capture UI, and any onboarding/login screens.

## Tech Stack

- **Language/UI:** Kotlin + Jetpack Compose (native Android).
- **Min/Target SDK:** minSdk 26, targetSdk = latest stable.
- **Architecture:** MVVM + unidirectional data flow; Kotlin Coroutines/Flow.
- **DI:** Hilt.
- **Currency:** IDR — single currency. Money stored as integer rupiah (minor unit = 1 IDR; no sub-unit). No tax in MVP (`taxRateBps = 0`).
- **Persistence:** Room (SQLite), local-only. No backend in MVP.
- **Camera:** CameraX.
- **Networking (cloud Recognizer impl):** Retrofit + OkHttp + kotlinx.serialization.
- **Image handling:** Coil for display; downscale captures before sending (cost/latency control).
- **Recognition (Phase 1 impl):** A cloud `Recognizer` backed by the **OpenRouter API** (OpenAI-compatible chat-completions with image input + structured/JSON output). This gives two layers of vendor-agnosticism: the **model is a config string** (e.g. `google/gemini-...`, `openai/...-mini`) swappable on OpenRouter without code changes, and the whole cloud impl sits behind the `Recognizer` interface so an on-device engine can replace it later. The cheapest vision model meeting the accuracy bar is selected/confirmed via the L6 benchmark. The OpenRouter API key lives in secure storage; no model id is referenced outside `data/recognizer/`.
- **Catalog context strategy:** Per scan, pre-narrow the catalog to a candidate shortlist (rather than sending all 50–300 items) to cut token cost. The narrowing *signal* (recent/frequent items, cashier category pick, or a cheap first-pass) is a Plan-phase decision.
- **Recognition (Phase 2, out of MVP):** On-device image embeddings matching counter crops against the enrolled reference-photo gallery.

## Commands

```
Build (debug APK):     ./gradlew assembleDebug
Install on device:     ./gradlew installDebug
Unit tests:            ./gradlew testDebugUnitTest
Instrumented/UI tests: ./gradlew connectedDebugAndroidTest
Android lint:          ./gradlew lintDebug
Format (ktlint):       ./gradlew ktlintFormat
Lint check (ktlint):   ./gradlew ktlintCheck
Static analysis:       ./gradlew detekt
Full check:            ./gradlew check
```

## Project Structure

```
app/src/main/java/com/cashierapp/photocheckout/
  ui/             → Compose screens, components, ViewModels, navigation
    shell/        → Bottom-nav scaffold + navigation graph (Shell)
    catalog/      → Catalog List (S3), Add wizard (S4), Product Detail & Edit
    scan/         → Scan Capture (S1) + Draft Review (S2)
    sales/        → Receipt (S5) + Sales History (S6)
    settings/     → Settings (S7) — OpenRouter key/model, threshold
    common/       → Reusable components: camera flow (C1), catalog picker (C2),
                    processing overlay (C3), confirm dialogs (C4)
  domain/         → Pure Kotlin: models, Recognizer interface, pricing, use cases
    model/        → CatalogItem, RecognizedItem, DraftReceipt, Money, Sale
    recognizer/   → Recognizer interface + result types (NO vendor code here)
    pricing/      → Deterministic pricing/total/tax logic
    usecase/      → ScanCounter, CommitSale, EnrollProduct, etc.
  data/           → Implementations
    db/           → Room entities, DAOs, database, repositories
    recognizer/   → Cloud Recognizer impl(s) + DTOs (vendor code lives ONLY here)
    config/       → Provider selection, API key access (secure storage)
  di/             → Hilt modules

app/src/test/                → JVM unit tests (domain, pricing, fakes)
app/src/androidTest/         → Room DAO, Compose UI, and end-to-end journey tests
  e2e/                       → Cross-module E2E (Hilt + FakeRecognizer + seeded DB)
  benchmark/                 → On-demand accuracy/cost harness (real Recognizer, not CI)
docs/ideas/photo-checkout.md → Origin idea one-pager
SPEC.md                      → This document
```

## Code Style

- Kotlin official style; ktlint-enforced. 4-space indent. Explicit visibility on public API.
- Domain layer is pure Kotlin with **no Android imports** — keeps it unit-testable and vendor-agnostic.
- Money is always integer minor units (`Long`), never `Float`/`Double`/`BigDecimal` in transit.
- `Recognizer` returns identity + quantity + confidence only — never price.

```kotlin
// domain/recognizer/Recognizer.kt — the one seam every engine implements.
interface Recognizer {
    /** Identify catalog items present in [image]. Pricing is NOT this layer's job. */
    suspend fun recognize(
        image: CapturedImage,
        catalog: List<CatalogItem>,
    ): Result<List<RecognizedItem>>
}

data class RecognizedItem(
    val sku: String,
    val quantity: Int,
    val confidence: Float,           // 0f..1f; UI flags anything below threshold
    val boundingBox: BoundingBox? = null,
)

// Below this, a draft line is flagged for cashier review. Tune from L6 benchmark data.
const val CONFIDENCE_THRESHOLD = 0.6f

// domain/pricing/Pricing.kt — deterministic, model output never reaches here as a price.
// Money is integer rupiah (IDR, minor unit = 1). taxRateBps defaults to 0 (no tax in MVP).
fun priceDraft(
    recognized: List<RecognizedItem>,
    catalog: Map<String, CatalogItem>,
    taxRateBps: Int = 0,             // basis points; 0 = no tax (MVP)
): DraftReceipt {
    val lines = recognized.mapNotNull { r ->
        catalog[r.sku]?.let { item ->
            DraftLine(
                sku = item.sku,
                name = item.name,
                quantity = r.quantity,
                unitPriceMinor = item.priceMinor,        // from DB, not the model
                lineTotalMinor = item.priceMinor * r.quantity,
                lowConfidence = r.confidence < CONFIDENCE_THRESHOLD,
            )
        }
    }
    val subtotal = lines.sumOf { it.lineTotalMinor }
    val tax = subtotal * taxRateBps / 10_000
    return DraftReceipt(lines = lines, subtotalMinor = subtotal, taxMinor = tax, totalMinor = subtotal + tax)
}
```

## Testing Strategy

- **Frameworks:** JUnit5 + kotlin.test for JVM unit tests; Turbine for Flow; MockK for fakes; Compose UI test + Room in-memory DB + Hilt test rules for instrumented tests; Espresso where Compose interop is needed.

Tests form a pyramid — many fast unit tests, fewer instrumented tests, a small set of E2E journeys:

- **L1 — Unit (most tests, highest priority):** Pure JVM tests for `domain/` and `pricing/`. Pricing, tax, totals, rounding, low-confidence flagging, and SKU→catalog mapping have exhaustive cases. Money math verified to the minor unit. ViewModels tested with fakes.
- **L2 — Recognizer contract:** A `FakeRecognizer` returns canned `RecognizedItem` lists so the scan→draft→confirm flow is exercisable without a network or live model. The cloud impl is tested separately against recorded request/response fixtures — no live calls in CI.
- **L3 — Persistence:** Room DAO/repository tests on an in-memory database, including the soft-delete (CAT-6) and price-snapshot (SALE-3) behaviors.
- **L4 — Component UI:** Compose tests per screen — catalog enrollment/edit, draft-review (edit qty, remove line, manual add, low-confidence + unidentified-item rendering), receipt/history.
- **L5 — End-to-end (cross-module journeys):** Full-app instrumented tests on emulator/device with Hilt injecting a `FakeRecognizer` and a seeded local DB, driving real navigation across all three modules. Minimum journeys:
  - **E2E-1 (happy path):** Enroll products → Scan → priced draft appears → confirm → receipt persisted → visible in sales history. Covers the Catalogue→Scan→Sales dependency chain end to end.
  - **E2E-2 (edit-before-commit):** Scan → fake returns a wrong qty + one low-confidence + one unidentified item → cashier edits qty, removes a line, manually adds an item → confirm → committed totals match the edited draft (not the raw recognition).
  - **E2E-3 (discard):** Scan → draft → discard → assert no sale record exists (SALE-5).
  - **E2E-4 (price-snapshot):** Commit a sale → change the product's catalog price → reopen the historical receipt → original price is unchanged (SALE-3).
  - **E2E-5 (recognition failure):** Fake returns an error/timeout → recoverable error state, no partial sale committed (SCAN-9).
  - CameraX capture is stubbed via an injected image source in E2E (real camera is verified in manual device QA, not automated).
- **L6 — Accuracy benchmark (not a pass/fail CI gate):** A harness runs the *real* cloud Recognizer over a fixed set of ≥50 representative counter photos and reports item-level precision/recall, cashier-correction rate, latency, and per-scan cost. Validates X-3 and the riskiest assumption; run on demand, not in CI.
- **Coverage expectation:** Domain + pricing ≥ 90% line coverage; each module's critical flow covered by at least one E2E journey; overall app measured by meaningful flow coverage, not a single global number.

#### Integration test plan (per module)

Integration tests sit between the isolated tests (L1–L4) and the full-app journeys (L5). They wire a single module's **real** collaborators together — use cases + repositories + Room (in-memory) + real pricing — and fake only at the module's external boundary (the `Recognizer`, the camera, the cloud network). No screen navigation; assertions are on state/data, not pixels. Run as instrumented or Robolectric tests so Room/Hilt are real.

**Catalogue — integration:**
- **CAT-INT-1:** `EnrollProduct` use case → repository → Room + photo file storage: creating a product writes a DB row *and* persists the reference photo file; the product is retrievable by its generated SKU and survives a DB reopen. (CAT-1, CAT-2, CAT-4, CAT-7)
- **CAT-INT-2:** Price entry flows from validated input → stored integer minor units → read back identical; invalid input (negative / non-integer) is rejected before persistence. (CAT-3, X-1)
- **CAT-INT-3:** Soft-delete: deactivating a product removes it from the active-catalog query that Scan consumes, while it remains resolvable for historical lookups. (CAT-6)
- **CAT-INT-4:** Search/filter over a seeded 50–300 item catalog returns correct results and stays responsive. (CAT-5, CAT-7)

**Scan — integration:**
- **SCAN-INT-1:** `ScanCounter` pipeline with `FakeRecognizer` → real SKU→catalog mapping (Room) → real pricing → `DraftReceipt`: prices/line totals/subtotal/tax come from the catalog, never the fake's payload. (SCAN-4, SCAN-5, X-1, X-2)
- **SCAN-INT-2:** Image downscale stage runs before the `Recognizer` is invoked (assert the recognizer receives the reduced-size `CapturedImage`). (SCAN-2)
- **SCAN-INT-3:** A returned SKU absent from the active catalog produces an "unidentified item" entry — never silently dropped; a confidence below threshold is flagged on the resulting draft. (SCAN-6, SCAN-7)
- **SCAN-INT-4:** Cloud `Recognizer` impl against **MockWebServer** + recorded fixtures: outgoing request encodes image + catalog context correctly; success responses parse into `RecognizedItem`s; HTTP error/timeout map to a recoverable failure (no partial draft). (SCAN-3, SCAN-9, X-2)
- **SCAN-INT-5:** Telemetry: a scan records per-scan latency, cost, and item-level accuracy fields through the real recording path. (X-3)

**Sales — integration:**
- **SALE-INT-1:** `CommitSale` → repository → Room: a confirmed `DraftReceipt` persists a `Sale` with line items, captured unit prices, computed subtotal/tax/total, and timestamp; re-reads identical. (SALE-1, SALE-2)
- **SALE-INT-2:** Price-snapshot integrity across repos: commit a sale → mutate the product price via the Catalogue repository → reload the stored sale → snapshot prices/totals unchanged. (SALE-3)
- **SALE-INT-3:** Discard path: abandoning a draft persists no `Sale` row. (SALE-5)
- **SALE-INT-4:** Sales-history query returns committed sales in order and each resolves to full receipt detail. (SALE-6)
- **SALE-INT-5:** Receipt share payload is constructed correctly from a persisted sale (formatting/content), independent of the Android share-sheet UI. (SALE-4)

## Boundaries

**Always:**
- Keep all recognition behind the `Recognizer` interface; vendor/DTO code lives only in `data/recognizer/`.
- Compute every price and total in the domain layer from the local catalog, in integer minor units.
- Surface low-confidence and unidentified items to the cashier; never silently drop a detected item.
- Require explicit cashier confirmation before a sale is committed.
- Run `./gradlew testDebugUnitTest` and `ktlintCheck` before a commit.
- Store API keys in secure storage (e.g., EncryptedSharedPreferences); read from config, never literals.

**Ask first:**
- Adding or switching a cloud provider/dependency, or any new third-party SDK.
- Changing the Room schema (provide a migration).
- Sending images anywhere other than the single configured recognition provider, or adding telemetry that transmits images.
- Introducing a backend / network sync.
- Changing the `Recognizer` interface signature (ripples to every impl).

**Never:**
- Let the model output or influence prices, totals, or tax.
- Hardcode a vendor name or model id in `domain/` or `ui/`.
- Commit API keys, secrets, or `.env`/keystore files.
- Auto-commit a sale without cashier confirmation.
- Use floating-point types for money.
- Delete or skip failing tests without approval.

## Success Criteria

- All Phase 1 acceptance criteria pass across the three modules (CAT-1…7, SCAN-1…10, SALE-1…6) plus cross-cutting (X-1…3).
- Domain + pricing test suites green with ≥90% coverage; full `./gradlew check` passes.
- The accuracy benchmark produces a measured cashier-correction rate and per-scan cost on the ≥50-photo set, enabling a go/no-go on the recognition assumption.
- A provider swap is demonstrated by adding a second `Recognizer` impl + config change, with zero edits to `domain/`, `pricing/`, or `ui/`.
- The photo→draft→confirm→commit loop works end-to-end on a real device with a 50–300 item catalog.

## Resolved Decisions

Settled during the Specify phase (2026-06-06):

- **Currency & tax:** IDR, single currency, money as integer rupiah (minor unit = 1). No tax in MVP (`taxRateBps = 0`).
- **Catalog context:** Pre-narrow to a candidate shortlist per scan to cut token cost (vs. sending all 50–300 items).
- **Reference photos:** One freeform photo per product at enrollment; schema supports more for Phase 2.
- **Confidence threshold:** `CONFIDENCE_THRESHOLD = 0.6` to start; tune from L6 benchmark data.
- **Capture flow:** Single top-down photo of items spread flat/non-overlapping. Multi-shot out of MVP.
- **Provider:** Cloud `Recognizer` via OpenRouter (OpenAI-compatible); model is a config string. Cheapest viable vision model confirmed by L6 benchmark.
- **Variants:** Near-identical variant disambiguation is out of MVP — handled by low-confidence flagging + cashier correction; leave the seam for a later disambiguation UI.

Settled during Catalogue planning (2026-06-06):

- **SKU format:** Fixed `SKU-` prefix + 4-digit global counter (`SKU-0001`), auto-generated at save, immutable. (Note: differs from the screenshots' mnemonic `NG-0001` letters.)
- **Navigation:** Bottom nav has 5 tabs (Home · Catalogue · Scan · Sales · More); Settings lives under More. Home landing content TBD.
- **Add flow:** 3-step wizard (Basic Info → Pricing → Review); Product Detail is a separate screen with inline edit + deactivate/reactivate.
- **Reactivation:** In scope — reachable from an inactive product's detail (via the Inactive filter).
- **Search scope:** Matches product name + SKU.
- **Max photos:** 3 per product (enroll 1; add up to 3 in detail).

## Open Questions (remaining — for Plan phase)

- **Candidate-narrowing signal:** What drives the per-scan shortlist — recent/frequent items, a cashier category pick, or a cheap first-pass model? Decide in Plan.
- **Downscale target:** Resolution/quality the capture is downscaled to before upload (accuracy vs. token cost). Tune with the benchmark.
- **OpenRouter request shape:** Exact prompt + JSON schema for structured `{sku, qty, confidence}` output, and how the candidate shortlist (names ± reference thumbnails) is encoded.
- **Receipt format:** Fields/layout for the shared receipt (SALE-4) — plain text vs. formatted image/PDF.
