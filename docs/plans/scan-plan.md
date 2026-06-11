# Implementation Plan: Scan Module (Photo Checkout)

> Scope: **Scan module only** (SCAN-1…10) plus the recognition/pricing seam and provider config the module is the first to need (X-1, X-2, X-3 as they touch Scan).
> Source: `docs/spec/SPEC.md` · Builds on the completed Catalogue module (`docs/plans/catalogue-plan.md`).
> Status: Draft for review.
> Created: 2026-06-07
> Updated: 2026-06-07 — synchronized with Scan mockups `docs/screenshots/07…18` (now the design source of truth).
>
> **UI copy convention:** the app currently ships **English** hardcoded strings (the Catalogue module). The Scan mockups are rendered in **Indonesian** for illustration; Scan is implemented in **English** to stay consistent with Catalogue, and a full Bahasa-Indonesia localization pass (string resources) is deferred to a later, app-wide task. Throughout the wireframes below the mockup's Indonesian label is shown in parentheses for traceability, e.g. **Add item** *(“Tambah Item”)*.

## Overview

Build the Scan module: photograph the counter, recognize catalog items through the vendor-agnostic `Recognizer` seam, and assemble an **editable, deterministically-priced draft receipt** that the cashier reviews before confirming. Recognition returns identity + quantity + confidence only; every price and total is computed in the domain from the local catalog (the two non-negotiable rules).

Scan is the second module and depends entirely on Catalogue (it consumes `CatalogRepository.observeActiveProducts()` for SKU→price mapping and the C1 camera flow). It is also the **first module to need** three shared seams, so this plan carries them: the `Recognizer` interface + result types, the deterministic `pricing/` module, and the provider config/secure-storage layer.

Delivery is sliced vertically. The whole capture→draft→edit flow is built and proven end-to-end against a `FakeRecognizer` first (no network, fully testable — SCAN-10), then the real OpenRouter cloud `Recognizer` is dropped in behind the same interface (X-2) without touching `domain/`, `pricing/`, or `ui/`.

## Module boundary (what is and isn't in this plan)

Scan ends at a **confirmed, finalized `DraftReceipt`**. The S2 "Confirm" action in the spec reads "Confirm → commit sale → S5", but `CommitSale`, `Sale` persistence, the Receipt screen (S5), and Sales History (S6) are **Module 3 (Sales)**.

- **In scope:** capture (S1), recognition pipeline, priced draft, draft review + edit (S2 up to and including the Confirm/Discard *gate*), error/empty states, the cloud Recognizer + provider config, Scan telemetry.
- **Out of scope (Module 3 seam):** the actual commit, `Sale` Room persistence, receipt rendering/sharing, history. "Confirm" is wired to an injected `onConfirm(DraftReceipt)` hand-off that Module 3 will implement; for this plan it resolves to a temporary stub (toast + return to capture) so the flow is demonstrable. "Discard" (SALE-5) is fully in scope because it must guarantee *no* record is produced — which here means simply discarding in-memory state.
  - **Mockup boundary callout:** the captured flow includes two screens *past* the Scan seam — **S9 “Konfirmasi Penjualan”** (`15`, a sale-review screen with a **payment-method** picker) and **S10 “Penjualan Dikonfirmasi”** (`16`, the post-commit success with *“Sale committed and receipt saved. (Handoff to Sales module)”* and a **View Receipt** button). Both are **Module 3 (Sales)**, not Scan: Scan's **Confirm** button on the draft (S3) is exactly the `onConfirm(DraftReceipt)` hand-off, and everything from the payment-method review onward is built in the Sales module. They are reproduced in the mockup set only to show the seam in context. Payment method is a Sales concern and is *not* part of `DraftReceipt`.
- **Settings (S7):** a full Settings screen is the Settings module's job. Scan only needs the **config seam** (read API key + model id + threshold from secure storage). This plan builds that seam plus a **minimal** key/model entry so a real scan can run on a device; the polished S7 is deferred.

## Decisions folded into this plan (resolving SPEC "Open Questions")

Items marked **(decision)** are adopted here with rationale; **(confirm)** want your sign-off (see Open Questions at the end).

- **Candidate-narrowing signal** — MVP sends the **full active catalog as a compact `{sku, name}` text list** (no reference thumbnails) in the prompt. Item names are cheap tokens; even 300 items is a small payload, and this removes a whole class of "the right item wasn't in the shortlist" recognition failures. Pre-narrowing (recent/frequent shortlist, thumbnails) becomes a cost optimization revisited *after* L6 produces real per-scan cost data. The narrowing point is isolated behind one function so it can change without touching the pipeline. **(decision)**
- **Downscale target** — longest edge **1024 px**, JPEG **quality 80**, before upload (SCAN-2). A defensible accuracy/cost starting point; the exact target is a single tunable constant fed by the L6 benchmark. **(decision, tune later)**
- **OpenRouter request shape** — OpenAI-compatible `POST /chat/completions`; image sent as a base64 `data:` URL in a `image_url` content part; catalog sent as a text content part; structured output requested via `response_format: { type: "json_schema" }` constraining a `{ items: [{ sku, quantity, confidence }] }` array. The model id and key come from config; nothing vendor-specific leaks past `data/recognizer/`. **(decision)**
- **Recognition→draft mapping** — SKUs the model returns that are **not** in the active catalog become `UnidentifiedItem` entries (never dropped, SCAN-7); confidence `< 0.6` flags the line (SCAN-6). Quantities are clamped to `>= 1`. **(decision)**
- **Confidence is surfaced numerically per line** — the Draft mockups (`09`, `14`) show the raw confidence value (e.g. `0.92`, `0.45`, `0.18`) on **every** line, not just a flag on low ones. The draft line therefore renders the numeric confidence as secondary text, and lines below `CONFIDENCE_THRESHOLD` additionally get the amber warning-triangle treatment (SCAN-6). `DraftLine` already carries `confidence`-derived state; we keep the raw `confidence: Float` on the line for display. **(decision, from mockups)**
- **Per-line note** — the Edit-Item mockup (`10`) has an optional **“Catatan (opsional)”** (note) field. `DraftLine` gains an optional `note: String?` (display/edit only; it never affects pricing and is not sent to the recognizer). Whether the note rides along to the Sales `onConfirm` payload is a Module-3 concern; Scan simply preserves it on the line. **(decision, from mockups)**
- **Confidence threshold** — reuse `CONFIDENCE_THRESHOLD = 0.6f` from SPEC; surfaced as an optional config override. **(decision)**
- **Secure storage** — `EncryptedSharedPreferences` (androidx.security:security-crypto) for the API key, per SPEC's Boundaries. **(confirm — library is in maintenance mode; acceptable for MVP?)**

## Architecture Decisions

- **The seam is one interface.** `domain/recognizer/Recognizer.recognize(image, catalog): Result<List<RecognizedItem>>` exactly as SPEC specifies. `FakeRecognizer` (test) and `OpenRouterRecognizer` (`data/recognizer/`) are the only implementations; vendor/DTO/model-id code lives **only** in `data/recognizer/` (X-2).
- **Pricing is pure and deterministic.** `domain/pricing/Pricing.priceDraft(...)` takes recognized items + a catalog map and returns a `DraftReceipt`; the model's payload never reaches it as a price (X-1, SCAN-4). Money stays `Long` minor units end-to-end; reuse `IdrFormat` for display only.
- **`ScanCounter` orchestrates, doesn't decide.** The use case calls the injected `Recognizer`, maps SKUs against the active catalog (Room), runs `priceDraft`, and folds unidentified/low-confidence into the result. It is the single place the pipeline is assembled and the unit under most integration tests.
- **Downscaling is a port.** `ImageDownscaler` is a small interface; the Android (Bitmap) impl lives in `data/`, a fake in tests. The Scan pipeline downscales *before* invoking the Recognizer so SCAN-INT-2 can assert the recognizer received a reduced-size `CapturedImage`.
- **Reuse C1 as-is.** `CameraCaptureRoute` already returns `ByteArray` via `onPhotoCaptured` and centralizes permission handling — S1 reuses it directly with a counter-framing hint; no camera rewrite. `ImageSource` stays injectable so E2E/integration bypass hardware.
- **Navigation mirrors the existing pattern.** `AppShell` drives sub-screens with a `rememberSaveable` enum (`CatalogMode`). The mockups reveal the Scan flow is **more than two screens** — capture, a full-screen draft, a **full-screen Edit-Item** (`10`), a **full-screen Add-Item** picker (`11`/`12`), and a **post-discard “Draft Discarded”** screen (`18`). So `ScanMode` is `{ Capture, Draft, EditLine, AddItem, Discarded }` (Edit/Add carry the target line / selection in the ViewModel, not the enum). The Processing overlay (`08`) and the Discard-confirm dialog (`17`) are overlays over Draft, not enum states. No migration to Navigation-Compose in this plan.
- **Provider selection via DI + config.** A Hilt binding picks `FakeRecognizer` vs `OpenRouterRecognizer` from config; swapping providers is config + one impl, with zero edits to `domain/`/`pricing/`/`ui/` (X-2 success criterion).
- **The processing overlay shows staged progress, not a bare spinner.** Mockup `08` is a **determinate** experience: a percentage ring plus a three-step checklist — *sending image → recognizing items → building draft* — and a Cancel. So the capture ViewModel exposes a small `ScanStage { Uploading, Recognizing, Pricing }` state (with the step it has reached) that `ScanCounter` advances as it walks the pipeline; the overlay renders the checklist from it. The percentage can be coarse/indeterminate-mapped (it need not be byte-accurate) — the stages are the contract. This keeps `ScanCounter` UI-free (it emits stage callbacks/Flow; the overlay is pure UI).
- **Telemetry through a recording port.** `ScanTelemetry` records per-scan latency, cost, and item-level fields (X-3); MVP impl logs locally and never transmits images.

## Dependency Graph

```
Catalogue module (DONE): CatalogRepository, C1 camera, PhotoStorage, IdrFormat

T0 Test/deps tooling (MockWebServer, Turbine, MockK, security-crypto)      ← (none)

Phase 1 — recognition domain & pricing (pure Kotlin):
  T1 Models + Recognizer interface (CapturedImage, RecognizedItem,
     DraftReceipt/DraftLine (+confidence,+note), UnidentifiedItem,
     ScanStage, BoundingBox, CONFIDENCE_THRESHOLD)                          ← (none)
  T2 Pricing module (priceDraft) + exhaustive unit tests                   ← T1
  T3 ScanCounter use case + FakeRecognizer + unit tests                    ← T1, T2

Phase 2 — capture→draft→edit UI (FakeRecognizer-driven, no network):
  T4 Slice — Scan Capture (S1): reuse C1 + downscale + overlay (C3)        ← T3
  T5 Slice — Draft Review (S2) read-only render                            ← T3, T4
  T6 Edit-Item screen (qty/remove/note) + Confirm/Discard gate (+Discarded) ← T5
  T7 Add-Item full-screen picker (C2: quick-add + multi-select)            ← T5, T6
  T8 Slice — Empty/error/timeout states (recoverable)                      ← T4, T5

Phase 3 — cloud Recognizer (OpenRouter) + config + telemetry:
  T9  RecognizerConfig + secure storage + minimal S7 key/model entry       ← T1
  T10 OpenRouterRecognizer impl + DTOs + MockWebServer tests               ← T1, T9
  T11 Scan telemetry (latency/cost/accuracy recording)                     ← T3, T10
  T12 DI provider selection + vendor-swap demonstration                    ← T10, T11

Phase 4 — hardening:
  T13 Scan integration tests SCAN-INT-1…5                                  ← T3, T10, T11
  T14 Polish, full ./gradlew check, Module 3 hand-off seam                 ← all
```

Build order is bottom-up. Risk-forward note: the OpenRouter request/response shape (T10) is the riskiest unknown — the whole flow is proven on the Fake first (Phases 1–2) so T10 is the *only* thing in doubt when we get there, and it's covered by MockWebServer fixtures, not live calls.

### Execution tracks (split plans for tiered LLMs)

For execution this plan is split by task difficulty into two standalone executor docs (this master remains the source of truth for shared context — mockups, architecture, folder structure):

- **`docs/plans/scan-plan-easy.md`** — low-reasoning, well-specified, mechanical tasks for a **cheaper model**: **T0, T1, T11, T12**.
- **`docs/plans/scan-plan-complex.md`** — correctness-critical / multi-screen / integration tasks for a **high-reasoning model**: **T2, T3, T4, T5, T6, T7, T8, T9, T10, T13, T14**.

The two tracks **interleave by the dependency order above** (neither runs fully before the other). Both split docs carry the same *Global execution order* table with the cross-track handoffs. T1's output is a verify-gate (the contract for the Complex track); the Easy track's T11/T12 wait on Complex T3/T10.

---

## Folder Structure (new code)

New code follows the existing package layout (`com.cashierapp.photocheckout`): `domain/` is pure Kotlin (no `android.*`), `data/` holds Android/IO implementations + DTOs, `ui/<feature>/<screen>/` groups `Route`/`Screen`/`UiState`/`ViewModel` (the Catalogue idiom), `di/` holds Hilt modules. Folders introduced by this plan are marked **(new)**; existing folders we add files to are marked *(edit)*. The owning task is in brackets.

```
app/src/main/java/com/cashierapp/photocheckout/
├── domain/                                  pure Kotlin — no android.* imports
│   ├── model/                               (edit)
│   │   ├── CapturedImage.kt                 [T1] bytes, width, height, mimeType
│   │   ├── DraftReceipt.kt                  [T1] DraftReceipt + DraftLine(+confidence,+note) + UnidentifiedItem
│   │   └── ScanStage.kt                     [T1] Uploading/Recognizing/Pricing (drives the staged overlay 08)
│   ├── recognizer/                          (new)
│   │   └── Recognizer.kt                    [T1] interface + RecognizedItem + BoundingBox + CONFIDENCE_THRESHOLD
│   ├── pricing/                             (new)
│   │   └── Pricing.kt                       [T2] priceDraft(recognized, catalog, taxRateBps) — deterministic
│   ├── image/                               (new)
│   │   └── ImageDownscaler.kt               [T4] downscale port (interface)
│   ├── telemetry/                           (new)
│   │   └── ScanTelemetry.kt                 [T11] latency/cost/item-count recording port
│   └── usecase/                             (edit)
│       └── ScanCounter.kt                   [T3] recognize → map catalog → priceDraft; emits ScanStage
│
├── data/
│   ├── image/                               (new)
│   │   └── AndroidImageDownscaler.kt        [T4] Bitmap impl of ImageDownscaler
│   ├── config/                              (new)
│   │   └── RecognizerConfig.kt              [T9] API key (EncryptedSharedPreferences) + model id + threshold
│   ├── recognizer/                          (new)  ← all vendor/DTO/model-id code lives ONLY here (X-2)
│   │   ├── OpenRouterRecognizer.kt          [T10] Recognizer impl (transport + parse)
│   │   ├── OpenRouterApi.kt                 [T10] Retrofit interface
│   │   └── dto/                             (new)
│   │       ├── ChatCompletionRequest.kt     [T10]
│   │       └── ChatCompletionResponse.kt    [T10]
│   └── telemetry/                           (new)
│       └── LoggingScanTelemetry.kt          [T11] local-only impl (never transmits images)
│
├── di/
│   ├── AppModule.kt                         (edit) [T4,T9] bind downscaler, config, telemetry
│   └── RecognizerModule.kt                  (new)  [T12] select Fake vs OpenRouter from config
│
├── ui/
│   ├── scan/                                (new)  ← the Scan feature
│   │   ├── capture/                         (new)  [T4] S1 Scan Capture (07) + staged overlay host (08)
│   │   │   ├── ScanCaptureRoute.kt
│   │   │   ├── ScanCaptureScreen.kt
│   │   │   ├── ScanCaptureUiState.kt
│   │   │   └── ScanCaptureViewModel.kt
│   │   ├── draft/                           (new)  [T5,T6,T8] S2 Draft Review (09/13/14)
│   │   │   ├── DraftScreen.kt
│   │   │   ├── DraftUiState.kt
│   │   │   ├── DraftViewModel.kt            owns draft state across Edit/Add/Discard sub-screens
│   │   │   └── DraftLineRow.kt              line composable (thumbnail, confidence, low-conf flag)
│   │   ├── edit/                            (new)  [T6] Edit-Item screen (10) — qty + note + remove/save
│   │   │   ├── EditItemScreen.kt
│   │   │   └── EditItemViewModel.kt
│   │   ├── additem/                         (new)  [T7] Add-Item full screen (11/12) — quick-add + multi-select
│   │   │   ├── AddItemScreen.kt
│   │   │   ├── AddItemUiState.kt
│   │   │   └── AddItemViewModel.kt
│   │   └── discarded/                       (new)  [T6] Draft Discarded screen (18)
│   │       └── DraftDiscardedScreen.kt
│   ├── settings/                            (new)  [T9] minimal key/model entry (not full S7)
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   ├── common/
│   │   ├── overlay/                         (new)  [T4] C3 staged processing overlay (08)
│   │   │   └── ProcessingOverlay.kt
│   │   ├── dialogs/                         (new)  [T6] C4 reusable confirm/discard dialog (17)
│   │   │   └── ConfirmDialog.kt
│   │   └── camera/                          (edit) [T4] flash + gallery-import + info affordances (07)
│   └── shell/                               (edit) [T4] ScanMode enum + wiring; Scan tab functional
│       ├── AppShell.kt
│       └── AppDestination.kt
│
└── AndroidManifest.xml                      (edit) [T9] INTERNET permission

app/src/test/java/com/cashierapp/photocheckout/         unit tests (JVM)
├── domain/pricing/PricingTest.kt            [T2]
├── domain/usecase/ScanCounterTest.kt        [T3]
└── recognizer/FakeRecognizer.kt             [T3] test-source Recognizer (also used by UI tests)

app/src/androidTest/java/com/cashierapp/photocheckout/  instrumented tests
├── ui/scan/                                 [T4–T8] Compose UI tests (capture, draft, edit, additem)
├── data/recognizer/OpenRouterRecognizerTest.kt  [T10] MockWebServer fixtures
├── data/config/RecognizerConfigTest.kt      [T9]
└── scan/                                    [T13] SCAN-INT-1…5 integration suite
```

**Naming/placement notes:**
- `FakeRecognizer` lives in **test source** (not `data/`), so production never ships a fake (SCAN-10).
- The Add-Item picker is placed under `ui/scan/additem/` (its only consumer is Scan); if a second consumer appears, promote it to `ui/common/picker/`. The reusable bits (`ProcessingOverlay`, `ConfirmDialog`) go straight to `ui/common/` because they're shared by design.
- `ScanStage` sits in `domain/model/` to stay Android-free and visible to both the use case (emit) and the overlay (render).
- One `DraftViewModel` owns the draft and is shared by the Edit/Add/Discard sub-screens (they mutate the same draft); they are `ScanMode` states routed by `AppShell`, not independent nav graphs.

---

## Task List

### Phase 0 — Tooling

#### Task 0: Test & dependency tooling
**Description:** Add the test/runtime dependencies the Scan module needs that the Catalogue bootstrap didn't: MockWebServer (cloud impl tests), Turbine (Flow assertions), MockK (fakes), and androidx.security-crypto (secure storage).

**Acceptance criteria:**
- [ ] `mockwebserver`, `turbine`, `mockk` (+ `mockk-android` where needed) added to the version catalog and wired into `test`/`androidTest` configs.
- [ ] `androidx.security:security-crypto` added to `implementation`.
- [ ] Project still builds; no version conflicts.

**Verification:**
- [ ] `./gradlew assembleDebug testDebugUnitTest` succeeds (existing tests still green).

**Dependencies:** None · **Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts` · **Scope:** S

---

### Phase 1 — Recognition Domain & Pricing (pure Kotlin)

#### Task 1: Recognition + draft domain models and the `Recognizer` interface
**Description:** The Android-free core the module builds on: the `Recognizer` seam, its input/output types, and the draft model. Exactly the shapes SPEC pins in its Code Style block.

**Acceptance criteria:**
- [ ] `domain/recognizer/Recognizer.kt`: `suspend fun recognize(image: CapturedImage, catalog: List<CatalogItem>): Result<List<RecognizedItem>>`; `RecognizedItem(sku, quantity, confidence, boundingBox?)`; `BoundingBox`; `CONFIDENCE_THRESHOLD = 0.6f`. No vendor code, no model id.
- [ ] `domain/model/`: `CapturedImage(bytes, width, height, mimeType)`, `DraftReceipt(lines, unidentified, subtotalMinor, taxMinor, totalMinor)`, `DraftLine(sku, name, quantity, unitPriceMinor, lineTotalMinor, confidence, lowConfidence, note?)`, `UnidentifiedItem(rawSku?, quantity, confidence)`. (`confidence: Float` retained for the per-line numeric display in mockups `09`/`14`; `note: String?` for the Edit-Item note field in `10`.)
- [ ] `ScanStage` progress type (e.g. `enum ScanStage { Uploading, Recognizing, Pricing }`) so the staged processing overlay (`08`) can render which step is active. Pure Kotlin, no Android.
- [ ] No `android.*` imports anywhere in `domain/`.

**Verification:**
- [ ] `./gradlew testDebugUnitTest` compiles; a trivial model test passes; (manual) grep confirms no Android imports under `domain/`.

**Dependencies:** None · **Files:** `domain/recognizer/Recognizer.kt`, `domain/model/CapturedImage.kt`, `domain/model/DraftReceipt.kt`, `domain/model/ScanStage.kt` · **Scope:** M

#### Task 2: Deterministic pricing module
**Description:** `domain/pricing/Pricing.kt` with `priceDraft(recognized, catalog, taxRateBps = 0)` building a `DraftReceipt` — unit prices and line totals strictly from the catalog map, low-confidence flagged, unidentified SKUs collected separately.

**Acceptance criteria:**
- [ ] Unit price/line total/subtotal/total computed in `Long` minor units; `taxRateBps` defaults to 0 (MVP) but the math is correct for non-zero (X-1, SCAN-4, SCAN-5).
- [ ] A recognized SKU absent from the catalog map produces an `UnidentifiedItem`, never a dropped or zero-priced line (SCAN-7); `confidence < CONFIDENCE_THRESHOLD` sets `lowConfidence` (SCAN-6).
- [ ] Quantity ≤ 0 from the model is normalized to ≥ 1; no floating-point anywhere.

**Verification:**
- [ ] `./gradlew testDebugUnitTest` — exhaustive pricing tests (empty, single, multi-qty, low-confidence, unidentified, tax non-zero, large totals to the minor unit); domain+pricing coverage ≥ 90%.

**Dependencies:** T1 · **Files:** `domain/pricing/Pricing.kt`, `app/src/test/.../domain/pricing/PricingTest.kt` · **Scope:** M

#### Task 3: `ScanCounter` use case + `FakeRecognizer`
**Description:** The use case that assembles the pipeline (recognize → map active catalog → price) and the canned `FakeRecognizer` that drives every later test without a network (SCAN-10).

**Acceptance criteria:**
- [ ] `ScanCounter(image): Result<DraftReceipt>` pulls the active catalog (via `CatalogRepository`), calls the injected `Recognizer`, runs `priceDraft`, and returns a `Result` — recognizer failure maps to `Result.failure` with no partial draft (SCAN-9).
- [ ] `FakeRecognizer` (test source) returns configurable canned `RecognizedItem` lists incl. wrong qty, low-confidence, unidentified, and error/timeout modes (supports E2E-2/E2E-5).
- [ ] Unit tests prove: prices come from the catalog not the fake payload; unidentified surfaced; low-confidence flagged (SCAN-4, SCAN-6, SCAN-7).

**Verification:**
- [ ] `./gradlew testDebugUnitTest` — `ScanCounter` tests green using `FakeRecognizer` + a fake `CatalogRepository`.

**Dependencies:** T1, T2 · **Files:** `domain/usecase/ScanCounter.kt`, `app/src/test/.../recognizer/FakeRecognizer.kt`, `app/src/test/.../domain/usecase/ScanCounterTest.kt` · **Scope:** M

> ### ✅ Checkpoint 1 — Domain & pricing
> - [ ] Unit tests for models, pricing, and `ScanCounter` green; ≥90% domain/pricing coverage; no Android imports in `domain/`. Human review.

---

### Phase 2 — Capture → Draft → Edit (FakeRecognizer-driven)

#### Task 4: Slice — Scan Capture (S1, mockup `07`) + staged Processing overlay (`08`)
**Description:** Make the Scan tab functional: reuse the C1 camera with a counter-framing hint, downscale the capture (SCAN-2) behind an injectable `ImageDownscaler`, show the **staged** processing overlay (C3) while `ScanCounter` runs (SCAN-3), then navigate to Draft. Permission denial handled by C1's existing recoverable state (SCAN-1).

**Acceptance criteria:**
- [ ] Scan tab shows live preview + framing hint + shutter; capture → downscale to ≤1024 px longest edge → `CapturedImage` → `ScanCounter` (FakeRecognizer) → Draft (S2). `AppShell` gains `ScanMode { Capture, Draft, EditLine, AddItem, Discarded }`.
- [ ] **Capture chrome matches mockup `07`:** close (✕, top-start) and **flash toggle** (top-end); a translucent framing-hint pill near the bottom (mockup copy *“Pastikan item terlihat jelas, tidak bertumpuk, dan pencahayaan cukup”* → English: “Make sure items are clearly visible, not stacked, and well-lit”); a 72dp shutter; and the two flanking affordances shown — a **gallery-import** icon (bottom-start) and an **info/help** icon (bottom-end). *Flash + close + shutter ship in T4. Gallery-import is a **separate image source** (pick an existing photo instead of capturing) — see note below; info/help is a small static sheet. If either grows T4, split per the risk table.*
- [ ] **Processing overlay (C3) is the staged design of mockup `08`:** percentage ring + a 3-step checklist (*sending image → recognizing items → building draft*) driven by `ScanStage`, plus a **Cancel** ("Batalkan") action. Covers the recognize wait; permission-denied path is recoverable, no crash (SCAN-1).
- [ ] `ImageDownscaler` is an injectable port (Android Bitmap impl + fake); the recognizer receives the reduced-size image. Gallery-imported images run through the **same** downscale → `ScanCounter` path.

> **Scope note — gallery import:** mockup `07` adds a gallery-pick affordance not in the original plan. It is a thin second `ImageSource` (photo picker → bytes), reusing the entire downscale→recognize→draft pipeline unchanged. If it risks overrunning T4, ship capture-only first and add gallery import as a fast follow; it does not block the draft flow.

**Verification:**
- [ ] Compose UI test with a fake `ImageSource` + `FakeRecognizer`: capture → overlay shown → draft appears.
- [ ] Manual device QA: real capture reaches the draft.

**Dependencies:** T3 · **Files:** `ui/scan/capture/*` (Route, Screen, ViewModel), `ui/common/overlay/ProcessingOverlay.kt` (C3, staged), `data/image/AndroidImageDownscaler.kt`, `domain/image/ImageDownscaler.kt`, `ui/common/camera/*` (flash/gallery affordances), `ui/shell/AppShell.kt`, `di/AppModule.kt` · **Scope:** L

#### Task 5: Slice — Draft Review render (S2, mockups `09`/`13`/`14`)
**Description:** The read-only draft: a line per item (thumbnail, name, SKU, qty × unit price, line total, numeric confidence), subtotal + total (no tax), low-confidence visual flag, and "unidentified item" entries surfaced for resolution. Matches mockup `09` (top bar `Draft (N item) · Review and edit before confirming` with an overflow `⋯` menu).

**Acceptance criteria:**
- [ ] **Line layout per mockup `09`:** product **thumbnail** (Coil), name (titleMedium), SKU (bodySmall, secondary), `qty × IDR unit` and the **line total** via `IdrFormat`, and the **numeric confidence** (e.g. `0.92`) as secondary text on every line. Subtotal + total shown; no tax row (SCAN-5).
- [ ] Lines with `lowConfidence` get the amber **warning-triangle** treatment of mockup `14` (in addition to the numeric value); `UnidentifiedItem`s render as distinct "unidentified" entries — none silently dropped (SCAN-6, SCAN-7).
- [ ] Top bar shows the live **item count** (`Draft (6 item)` → `Draft (7 item)` after an add, per `09`→`13`) and an overflow `⋯` menu (host for Discard / remove-all, wired in T6).
- [ ] Draft state survives rotation (`rememberSaveable`/ViewModel).

**Verification:**
- [ ] Compose UI test feeds a `DraftReceipt` with a low-confidence + unidentified entry and asserts both render with their flags and the numeric confidence value.

**Dependencies:** T3, T4 · **Files:** `ui/scan/draft/*` (Screen, ViewModel, line composables) · **Scope:** M

#### Task 6: Slice — Edit-Item screen + Confirm/Discard gate (mockups `10`, `17`, `18`)
**Description:** Make the draft editable before commit. The mockups put editing on a **dedicated full-screen Edit-Item view** (`10`) reached by tapping a line — *not* inline steppers on the draft. "Confirm" (explicit, no auto-commit) hands the finalized `DraftReceipt` to the Module 3 seam; "Discard" runs a confirm dialog (`17`) then a "Draft Discarded" success screen (`18`), leaving no record.

**Acceptance criteria:**
- [ ] **Edit-Item screen (`ScanMode.EditLine`, mockup `10`):** tapping a draft line opens a full screen with the product header (thumbnail, name, SKU, unit price `/pcs`), a **quantity stepper** (`– n +`), an optional **Note** field *(“Catatan (opsional)”)* bound to `DraftLine.note`, and two actions — **Remove Item** *(“Hapus Item”,* destructive/red*)* and **Save** *(“Simpan”,* primary teal*)*. Save re-prices the line and returns to Draft; Remove deletes the line and returns. Edits recompute subtotal/total deterministically in the domain (re-price, not model) (SCAN-8 partial, X-1).
- [ ] "Confirm" *(“Konfirmasi”)* calls injected `onConfirm(DraftReceipt)` (stub for now) — there is no auto-commit path (SALE-1 gate); the payment-review screen (`15`) and beyond are Module 3 and are **not** built here.
- [ ] **Discard (SALE-5):** an entry (overflow `⋯` / remove-all) opens the **C4 confirm dialog** of mockup `17` *(“Buang Draft?” — warning “all items will be deleted and no sale is created”, Cancel / Discard)*; confirming clears state and shows the **"Draft Discarded" screen** of mockup `18` *(“Draft Dibuang”, trash icon, **New Scan** / **Back to Home**)* — New Scan → S1, Back to Home → Catalogue/home. Nothing is persisted (SCAN-9, SALE-5).
- [ ] C4 confirm/discard dialog reused (or created if Catalogue didn't leave a reusable one).

**Verification:**
- [ ] Compose UI test: tap line → Edit-Item screen → change qty → Save → total updates; Remove Item → line gone, total updates; Discard → confirm dialog → "Draft Discarded" screen → New Scan lands on capture with draft cleared.

**Dependencies:** T5 · **Files:** `ui/scan/edit/*` (Edit-Item Screen/ViewModel), `ui/scan/draft/*`, `ui/scan/discarded/*` (or a Draft sub-state), `ui/common/dialogs/ConfirmDialog.kt` (reuse/create) · **Scope:** L

#### Task 7: Slice — Catalog picker (C2) full-screen "Add Item" (mockups `11`/`12`)
**Description:** A searchable active-catalog **full screen** (`ScanMode.AddItem`, *“Tambah Item”*) so the cashier can add missed items to the draft. The mockups show **two affordances**: a per-row quick-add **`+`** (mockup `11`) and a **multi-select** mode with row checkboxes and a batch **"Add to Draft"** button (`12`, header shows *“N dipilih”* / Cancel). Selected SKUs are priced from the catalog and appended as normal lines; the draft item count updates (`09`→`13`).

**Acceptance criteria:**
- [ ] "Add Item" opens a **full screen** (not a bottom sheet) listing active products with **thumbnail + name + SKU + IDR price**, searchable by name+SKU *(“Cari produk…”)*, backed by `CatalogRepository.observeActiveProducts()` (C2, SCAN-8).
- [ ] **Quick add (`11`):** the row `+` appends a priced `DraftLine` (qty 1, full confidence) and recomputes totals; selecting an already-present SKU increments its qty.
- [ ] **Multi-select (`12`):** entering selection mode shows per-row checkboxes and a header count *(“N dipilih”)* with Cancel *(“Batal”)*; **"Add to Draft"** *(“Tambah ke Draft”)* appends all selected as priced lines in one action and returns to Draft.
- [ ] Prices/line totals come from the catalog, never typed.

**Verification:**
- [ ] Compose UI test with a seeded catalog: open picker → search → quick-add one item (line added, correct catalog price, totals update); multi-select two → Add to Draft → both lines added, count and totals update.

**Dependencies:** T5, T6 · **Files:** `ui/scan/additem/*` (Screen/ViewModel) or `ui/common/picker/CatalogPickerScreen.kt` (C2), `ui/scan/draft/*` wiring · **Scope:** M

#### Task 8: Slice — Empty / error / timeout states
**Description:** Every non-happy recognition outcome resolves to a clear, recoverable state with no navigation to a partial draft.

**Acceptance criteria:**
- [ ] No items found → "no items" state on the draft with a retry/back to capture; provider/network error + timeout → recoverable error on S1, *no* navigation to draft (SCAN-9).
- [ ] Processing overlay is cancelable where safe; cancel returns to capture cleanly.
- [ ] No path produces or persists a partial sale.

**Verification:**
- [ ] Compose UI test with `FakeRecognizer` error/timeout modes: error state shown, stays on capture; empty result shows the no-items state.

**Dependencies:** T4, T5 · **Files:** `ui/scan/capture/*`, `ui/scan/draft/*` · **Scope:** M

> ### ✅ Checkpoint 2 — Full flow on the Fake
> - [ ] On device: capture → priced draft → edit qty/remove → manual add → confirm (stub) / discard, plus error/empty states — all driven by `FakeRecognizer`, no network. Compose UI tests green; `ktlintCheck` clean. Human review.

---

### Phase 3 — Cloud Recognizer (OpenRouter) + Config + Telemetry

#### Task 9: RecognizerConfig + secure storage + minimal key/model entry
**Description:** The provider config seam: read API key + model id + optional threshold from secure storage, validate key presence before a real scan, and a **minimal** Settings entry (not the full S7) to set them on-device.

**Acceptance criteria:**
- [ ] `data/config/RecognizerConfig` reads/writes API key (EncryptedSharedPreferences) + model id + threshold; key never logged, never a literal (Boundaries).
- [ ] **All three values live in one EncryptedSharedPreferences file** (the threshold rides in the same blob — no separate plaintext prefs), keyed exactly as below:

  | Key constant | Pref key string | Type | Default when absent |
  |---|---|---|---|
  | `KEY_API_KEY` | `openrouter_api_key` | `String` | `null` → triggers the "set your API key" prompt |
  | `KEY_MODEL_ID` | `openrouter_model_id` | `String` | `DEFAULT_MODEL_ID` (a constant in `data/recognizer/`, **not** hard-coded in config) |
  | `KEY_CONFIDENCE_THRESHOLD` | `confidence_threshold` | `Float` (stored as bits) | `CONFIDENCE_THRESHOLD` (0.6f, from `domain/`) |

  - Pref file name: **`recognizer_config`**, created via `EncryptedSharedPreferences.create(...)` with `AES256_SIV` (key) / `AES256_GCM` (value) and a `MasterKey` (`AES256_GCM` scheme). The file name + key strings are private `const val`s in `RecognizerConfig`; nothing outside `data/config/` references the raw strings.
  - `RecognizerConfig` exposes a typed surface — e.g. `apiKey: String?`, `modelId: String`, `confidenceThreshold: Float`, `hasApiKey(): Boolean`, and writers — so callers never touch pref keys. The **threshold default comes from `domain/`'s `CONFIDENCE_THRESHOLD`**, keeping the override (T9) and the domain constant (T1) in one source of truth.
- [ ] A minimal Settings screen (under the "More" tab) enters/masks the key and model id; Scan checks key presence (`hasApiKey()`) and shows a recoverable "set your API key" prompt if missing (no crash, no call).
- [ ] No model id or vendor string (incl. `DEFAULT_MODEL_ID`) referenced outside `data/`.

**Verification:**
- [ ] Instrumented test: write key/model/threshold → read back the typed values; absent key → `hasApiKey()` false and Scan surfaces the config prompt; absent model/threshold → fall back to `DEFAULT_MODEL_ID` / `CONFIDENCE_THRESHOLD`.
- [ ] (manual) inspect the on-disk prefs file is encrypted — no plaintext key/model strings.

**Dependencies:** T1 · **Files:** `data/config/RecognizerConfig.kt`, `ui/settings/*` (minimal), `di/AppModule.kt`, `AndroidManifest.xml` (INTERNET) · **Scope:** M

#### Task 10: OpenRouter cloud `Recognizer` implementation
**Description:** The real `Recognizer` behind the interface: Retrofit/OkHttp + kotlinx.serialization, OpenAI-compatible chat-completions with base64 image + compact catalog text + json_schema structured output, parsed into `RecognizedItem`s. Errors/timeouts map to `Result.failure`.

**Acceptance criteria:**
- [ ] `data/recognizer/OpenRouterRecognizer` builds the request shape (decision above), sends downscaled image + `{sku,name}` catalog list, parses `{items:[{sku,quantity,confidence}]}` → `List<RecognizedItem>`; HTTP/parse error + timeout → `Result.failure`, no partial draft (SCAN-3, SCAN-9).
- [ ] All DTOs + model id live only in `data/recognizer/`; key injected from `RecognizerConfig` (X-2).
- [ ] Candidate-narrowing isolated in one function (full-catalog for MVP) so it can change without touching the parser/transport.

**Verification:**
- [ ] `./gradlew connectedDebugAndroidTest` — MockWebServer tests: outgoing request encodes image+catalog correctly; success fixture parses; 4xx/5xx/timeout → failure (SCAN-INT-4). No live calls in CI.
- [ ] Manual device QA: a real scan against OpenRouter returns a draft within budget (SCAN-3).

**Dependencies:** T1, T9 · **Files:** `data/recognizer/OpenRouterRecognizer.kt`, `data/recognizer/dto/*`, `data/recognizer/OpenRouterApi.kt`, tests · **Scope:** L

#### Task 11: Scan telemetry (X-3)
**Description:** Record per-scan latency, cost, and item-level accuracy fields through a real recording path, feeding the L6 benchmark and go/no-go decision. Never transmits images.

**Acceptance criteria:**
- [ ] `ScanTelemetry` port records latency (ms), reported/estimated cost, and item counts per scan; wired into the `ScanCounter`/recognizer path (X-3).
- [ ] MVP impl persists/logs locally only; no image leaves the device via telemetry (Boundaries).

**Verification:**
- [ ] Integration test asserts a scan records latency + cost + item-level fields through the real path (SCAN-INT-5).

**Dependencies:** T3, T10 · **Files:** `domain/telemetry/ScanTelemetry.kt`, `data/telemetry/*`, wiring · **Scope:** M

#### Task 12: DI provider selection + vendor-swap demonstration
**Description:** Bind the active `Recognizer` from config (Fake vs OpenRouter), proving a provider swap is config + one impl with zero domain/pricing/ui edits.

**Acceptance criteria:**
- [ ] A Hilt binding selects the `Recognizer` impl from config; default build uses OpenRouter, tests inject Fake (X-2).
- [ ] Swapping is demonstrably config-only: document the one-line change; no edits to `domain/`, `pricing/`, `ui/`.

**Verification:**
- [ ] App runs with the real provider on device; test variant runs with the Fake; both via the same interface.

**Dependencies:** T10, T11 · **Files:** `di/RecognizerModule.kt`, `di/AppModule.kt` · **Scope:** S

> ### ✅ Checkpoint 3 — Real scan works
> - [ ] On device with an API key set: photo → OpenRouter → priced draft → confirm/discard. MockWebServer + config tests green; key handling secure. Human review.

---

### Phase 4 — Hardening

#### Task 13: Scan integration tests (SCAN-INT-1…5)
**Description:** Wire real collaborators per the spec's integration plan (use cases + Room + real pricing; fake only the external boundary).

**Acceptance criteria:**
- [ ] SCAN-INT-1 `ScanCounter`+Fake → real Room mapping → real pricing: prices from catalog, never the fake payload.
- [ ] SCAN-INT-2 downscale runs before the recognizer (assert reduced-size `CapturedImage`).
- [ ] SCAN-INT-3 absent SKU → unidentified (never dropped); sub-threshold flagged.
- [ ] SCAN-INT-4 OpenRouter impl vs MockWebServer fixtures (success + error/timeout).
- [ ] SCAN-INT-5 telemetry fields recorded through the real path.

**Verification:**
- [ ] `./gradlew connectedDebugAndroidTest` — Scan integration suite green.

**Dependencies:** T3, T10, T11 · **Files:** `app/src/androidTest/.../scan/*` · **Scope:** M

#### Task 14: Polish, full check & Module 3 hand-off
**Description:** Finish states/strings/contentDescriptions, confirm latency target on device, and leave a clean `onConfirm(DraftReceipt)` seam for Sales.

**Acceptance criteria:**
- [ ] Loading/empty/error/low-confidence/unidentified states polished; latency ≤ ~5s target observed on device (SCAN-3).
- [ ] `onConfirm` seam documented for Module 3 (`CommitSale` will replace the stub); no auto-commit anywhere.
- [ ] `./gradlew check` (unit + lint + detekt + ktlint + wired instrumented) green.

**Verification:**
- [ ] Manual end-to-end smoke on device; `./gradlew check` passes.

**Dependencies:** All · **Files:** polish across `ui/scan/*`, `ui/common/*` · **Scope:** M

> ### ✅ Checkpoint 4 — Module complete
> - [ ] SCAN-1…10 + SCAN-INT-1…5 satisfied; `./gradlew check` green; X-1/X-2/X-3 (Scan parts) hold. Human sign-off before Module 3 (Sales).

---

## Screens & UI Design

**Scan mockups now exist and are the design source of truth.** `docs/screenshots/` now contains **twelve Scan captures, `07`–`18`**, alongside the six Catalogue ones (01–06). The wireframes in this section have been **reconciled against those mockups**; where the mockup and the original wireframe disagreed, the mockup wins. The mockups are rendered in Indonesian — Scan is built in **English** (see the UI-copy convention at the top of this plan), so each screen lists its mockup's Indonesian label for traceability. They still reuse the existing design system verbatim (no new visual language):

**Mockup index (designer's screen numbering → this plan):**

| File | Designer caption | This plan |
|---|---|---|
| `07-scan-camera` | S1. Scan – Camera | **S1 Scan Capture** |
| `08.scan-progress` | S2. Processing | **C3 Processing overlay** (staged) |
| `09-scan-result` | S3. Draft Receipt | **S2 Draft Review** |
| `10-scan-edit-result` | S4. Edit Item (Quantity) | **Edit-Item screen** (full-screen) |
| `11-scan-add-items` | S5. Add Item – Search | **C2 Add Item** (quick-add) |
| `12-scan-add-items-selected` | S6. Add Item – Selected | **C2 Add Item** (multi-select) |
| `13-scan-draft-updated` | S7. Draft Updated | S2 after an add (count/total update) |
| `14.scan-low-confidence-indicator` | S8. Low Confidence Indicator | S2 low-confidence treatment |
| `15.scan-order-confirmation` | S9. Confirm – Review | **Module 3 (Sales)** — payment review, *not Scan* |
| `16.scan-order-confirmed` | S10. Confirmed (Handoff to Sales) | **Module 3 (Sales)** — commit success, *not Scan* |
| `17-scan-remove-all` | S11. Discard Confirmation | **C4 Discard dialog** |
| `18-scan-items-removed` | S12. Discarded | **Draft Discarded screen** |

> Note the numbering clash: the **mockups’ S1…S12** are the designer's sequence and are *not* the same as **SPEC's S1/S2/S5/S7**. This plan keeps SPEC's names (S1 Capture, S2 Draft) and references mockups by file number to avoid ambiguity.

Reuse the existing theme tokens verbatim:

- **Color** (`ui/theme/Color.kt`): `TealPrimary #009B8F`, `TealPrimaryDark #00786F`, `TealContainer #D9F6F1`, `SoftBlueBackground #EAF8FF`, `SurfaceWhite #FBFDFF`, `TextPrimary #09111F`, `TextSecondary #657386`, `DividerBlue #D4E4EF`, `NeutralBadge #EEF1F4`, `DangerRed #E53935`.
- **Spacing/shape** (`ui/theme/AppDimens.kt`): `spaceXs 4 · spaceSm 8 · spaceMd 16 · spaceLg 24 · spaceXl 32`; `screenPadding 24`; `cardRadius 24`; `controlRadius 18`.
- **Type** (`ui/theme/Type.kt`) and the Material3 component conventions already used in `ui/catalog/*` (Card + 1dp outline, pill badges, full-width primary buttons).

### What exists vs. what we build

| Element | Status | Action in this plan |
|---|---|---|
| C1 — Camera capture flow | **Exists** (`ui/common/camera/`) | Reuse `CameraCaptureRoute`; add framing hint + flash + gallery-import + info affordances (T4, mockup `07`) |
| Theme tokens (color/dimens/type) | **Exists** (`ui/theme/`) | Consume tokens, no literals |
| C3 — Processing overlay (staged) | **Missing** | Build (T4) — % ring + 3-step checklist + Cancel (mockup `08`); `ui/common/overlay/` |
| C2 — Add Item picker (full screen) | **Missing** | Build (T7) — full screen, quick-add + multi-select (mockups `11`/`12`); **not a bottom sheet** |
| C4 — Discard dialog | **Missing** | Build/confirm (T6) — mockup `17`; `ui/common/dialogs/` |
| S1 — Scan Capture | **New screen** | Design + build (T4, mockup `07`) |
| S2 — Draft Review | **New screen** | Design + build (T5–T8, mockups `09`/`13`/`14`) |
| Edit-Item screen (full-screen, +note) | **New screen** | Build (T6, mockup `10`) — replaces inline editing |
| Draft Discarded screen | **New screen** | Build (T6, mockup `18`) |
| Minimal Settings (key/model) | **New, minimal** | Build (T9) — not the full S7 |
| S9/S10 Confirm-review + Confirmed | **Module 3 (Sales)** | *Not built here* (mockups `15`/`16`) — past the `onConfirm` seam |

### High-level workflow (multi-screen)

The Scan flow is **four screens** (Capture, Draft, Edit-Item, Add-Item) plus two overlays (Processing, Discard dialog) and a Discarded success screen — driven by the `AppShell` enum-state pattern (`ScanMode { Capture, Draft, EditLine, AddItem, Discarded }`); the Scan tab is the entry point. Tapping a draft line opens the full-screen Edit-Item; "Add Item" opens the full-screen picker.

```
 [Scan tab]
     │
     ▼
 ┌─────────────────┐  capture /   ┌──────────────────┐ downscale +  ┌─────────────────┐
 │ S1 Scan Capture │  gallery     │ C3 Processing    │ ScanCounter  │ S2 Draft Review │
 │ (reuses C1 cam, │ ───────────▶ │ overlay (staged: │ ───────────▶ │ (priced draft,  │
 │ flash+gallery)  │              │ %ring+3 steps)   │              │ confidence/line)│
 └─────────────────┘              └──────────────────┘              └─────────────────┘
     │  ▲                              │ error/timeout                  │   │   │
 permission                           │ (SCAN-9)                        │   │   │
 denied (C1)                          ▼                                 │   │   │
     │                       back to S1 (recoverable,                   │   │   │
     ▼                        no navigation to draft)                   │   │   │
 recoverable prompt                                                     │   │   │
                                          tap line ─▶ Edit-Item screen ─┘   │   │
                                          (`10`: qty stepper + note +        │   │
                                           Remove / Save) ─re-priced─▶ S2     │   │
                                                                              │   │
                            "Add Item" ─▶ C2 Add Item full screen (`11`/`12`):│   │
                            quick-add `+` or multi-select ▶ "Add to Draft" ───┘   │
                                                                                  │
                ┌─────────────────────────────────────────────────────────────────┘
                ▼                                                                 ▼
   Discard ▶ C4 dialog (`17`) ▶ Draft Discarded (`18`)               Confirm ▶ onConfirm(DraftReceipt)
   "New Scan"▶S1 / "Back to Home"  (no record — SALE-5)              ▶ [Module 3 (Sales): S9 payment review
                                                                       (`15`) → commit → S10 confirmed (`16`)]
```

State edge: if no API key is set (real provider), S1 shows a recoverable "set your API key" prompt linking to the minimal Settings entry instead of calling the provider (T9).

### S1 — Scan Capture (new · mockup `07`)
*Purpose:* one top-down counter photo (SCAN-1, SCAN-2). Full-bleed camera, dark chrome (matches C1's existing black capture surface).

```
┌──────────────────────────────┐
│ ✕                         ⚡ │  close (TopStart) · flash toggle (TopEnd)
│                              │
│      [ live camera preview ] │  AndroidView/PreviewView (C1)
│                              │
│   ┌────────────────────────┐ │
│   │ Make sure items are     │ │  framing hint — translucent pill, bottom-center
│   │ visible, not stacked,   │ │  (mockup: "Pastikan item terlihat jelas,
│   │ and well-lit            │ │   tidak bertumpuk, dan pencahayaan cukup")
│   └────────────────────────┘ │
│  🖼          ( ◉ )         ⓘ │  gallery-import · 72dp shutter · info/help
└──────────────────────────────┘
```
- **Affordances (per `07`):** close (✕), **flash toggle** (⚡), **gallery-import** (🖼 — pick an existing photo, routes through the same downscale→recognize path), **info/help** (ⓘ — small static tips sheet). Flash + close + shutter are the core T4 deliverable; gallery-import is a thin second `ImageSource` and info is a static sheet (see T4 scope note).
- Permission-denied → C1's existing recoverable rationale state (no new design).
- On shutter (or gallery pick): downscale → show **C3 overlay** over this screen → navigate to S2 on success, or return here with an inline error (SCAN-9).
- States: ready · permission-denied · processing (overlay) · error (recoverable banner) · missing-API-key prompt.

### C3 — Processing overlay (new, reusable · mockup `08`)
*Staged, not a bare spinner.* Mockup `08` shows a **determinate percentage ring** ("42%") under a title ("Memproses gambar…" / "Mencari produk di katalog" → EN: *“Processing image… · Looking up products in the catalog”*) and a **three-step checklist**:

```
        Processing image…
     Looking up products in catalog
            ◜ 42% ◝
   ✓  Sending image          (done)
   ✓  Recognizing items      (done)
   ○  Building draft         (pending)
        [   Cancel   ]            ("Batalkan")
```
- Driven by the `ScanStage` state the capture ViewModel exposes as `ScanCounter` walks the pipeline; the ✓/○ marks follow the active stage. Percentage may be coarse — the **steps** are the contract.
- **Cancel** ("Batalkan") returns to S1, nothing committed. Reusable for other async waits.

### S2 — Draft Review (new · mockups `09` / `13` / `14`)
*Purpose:* review, edit, confirm the priced draft (SCAN-4–8, SALE-1 gate). Light surface, Catalogue list idiom. **Each row is a tappable list item** (thumbnail + text + numeric confidence) — editing happens on a separate screen, not inline.

```
┌──────────────────────────────┐
│ ‹  Draft (6 item)         ⋯ │  title + live item count · overflow (Discard/remove-all)
│    Review and edit before…  │  subtitle (mockup: "Periksa dan edit sebelum konfirmasi")
├──────────────────────────────┤
│ 🖼 Nasi Goreng Spesial       │  row → tap opens Edit-Item (`10`)
│    SKU • NG-0001       IDR 50.000
│    2 × IDR 25.000        0.92 │  qty×unit · line total · numeric confidence
│ 🖼 Es Kopi Susu              │
│    SKU • EK-0002       IDR 30.000
│    2 × IDR 15.000        0.88 │
│ 🖼 ⚠ Sate Ayam               │  low-confidence (`14`): amber warning triangle
│    SKU • SA-0004       IDR 40.000
│    2 × IDR 20.000        0.45 │  …still shows the numeric value
│ ❓ Unidentified item          │  distinct NeutralBadge card — "Pick from catalog" / remove
├──────────────────────────────┤
│ Subtotal            131.000  │  bodyLarge
│ Total               131.000  │  headlineSmall bold, no tax row (SCAN-5)
│ ┌────────────┐ ┌───────────┐ │
│ │ + Add Item │ │ Confirm   │ │  Add Item=secondary (→ C2) · Confirm=primary teal
│ └────────────┘ └───────────┘ │
└──────────────────────────────┘
```
- **Row:** thumbnail, name, SKU, `qty × unit`, line total, and the **numeric confidence** on every line (mockup `09`). Tapping the row opens the **Edit-Item screen** (`10`) — qty/remove/note are edited there, not inline. Edits re-price in the domain, totals + item count update live (`09`→`13`) (X-1).
- **Low-confidence (SCAN-6):** amber **warning triangle** on the row (mockup `14`); still fully editable.
- **Unidentified (SCAN-7):** visually distinct card with "Pick from catalog" (→ C2) or remove; never silently dropped.
- **Confirm** (primary) → `onConfirm(DraftReceipt)` → Module 3 picks up at the **payment-review screen S9 (`15`)**; nothing past the seam is built here. **Discard** lives under the `⋯` overflow → C4 dialog (`17`) → Draft Discarded (`18`) (SALE-5).
- States: populated · empty/"no items found" (recoverable) · all-low-confidence (still usable) · edited.

### Edit-Item screen (new · full screen · mockup `10`)
*Purpose:* edit one draft line. Reached by tapping a row on S2; **replaces the inline stepper/trash** from the earlier wireframe.

```
┌──────────────────────────────┐
│ ‹  Edit Item                 │
│ ┌──────────────────────────┐ │
│ │ 🖼 Es Kopi Susu           │ │  product header (thumbnail, name,
│ │    SKU • EK-0002          │ │   SKU, unit price / pcs)
│ │    IDR 15.000 / pcs       │ │
│ └──────────────────────────┘ │
│ Quantity ("Jumlah")          │
│   [ – ]     2     [ + ]      │  stepper
│ Note (optional)              │  ("Catatan (opsional)") → DraftLine.note
│ ┌──────────────────────────┐ │
│ │ Add a note…               │ │
│ └──────────────────────────┘ │
│ 🗑 Remove Item    [  Save  ] │  Remove ("Hapus Item", red) · Save ("Simpan", teal)
└──────────────────────────────┘
```
- **Save** re-prices the line and returns to S2; **Remove Item** deletes it and returns. The optional **Note** binds to `DraftLine.note` (display/edit only — never priced, never sent to the recognizer).

### C2 — Add Item picker (new · full screen · mockups `11` / `12`)
*Not a bottom sheet — a full screen* ("Tambah Item") with two affordances:
- **Quick-add (`11`):** search field ("Cari produk…") matching name+SKU; rows = thumbnail (Coil) + name + SKU + IDR price + a row **`+`** button that appends a priced line (qty 1; duplicate increments qty).
- **Multi-select (`12`):** selection mode swaps the `+` for per-row checkboxes and a header count ("N dipilih") with Cancel; a bottom **"Add to Draft"** ("Tambah ke Draft") appends all selected priced lines in one action and returns to S2.
- Mirrors the Catalogue list row idiom; prices always from the catalog.

### C4 — Discard dialog + Draft Discarded screen (new/shared · mockups `17` / `18`)
- **C4 dialog (`17`):** reusable `AlertDialog` — title ("Buang Draft?" → *“Discard draft?”*), an amber warning body ("All items will be deleted and no sale is created · A discarded draft cannot be recovered"), destructive confirm ("Discard", `DangerRed`) / Cancel. Written generically so Catalogue's deactivate can adopt it too.
- **Draft Discarded screen (`18`):** after confirming — red trash icon, "Draft Discarded" ("Draft Dibuang"), "You can start a new scan", and two actions: **New Scan** ("Scan Baru") → S1, **Back to Home** ("Kembali ke Beranda") → home. Guarantees no record (SALE-5).

### Minimal Settings entry (new, not full S7)
- Under the "More" tab: masked API-key field, model-id field, optional threshold; `OutlinedTextField` idiom from the Add-Product wizard. Just enough for a real scan; the polished S7 is the Settings module's job.

### States to implement (don't skip)
Capture: ready · permission-denied · processing (staged overlay) · error · missing-key. Draft: populated · empty/no-items · low-confidence · unidentified · edited · confirming. Edit-Item: editing · removing. Add-Item picker: list · search-empty · selection-mode. Discarded: success. All actionable icons get `contentDescription`; min touch target 48dp; verify contrast on teal (WCAG AA).

> **Design note:** The wireframes above have been **reconciled with the Scan mockups `07`–`18`** (2026-06-07) — the mockups are the design source of truth; this section translates them to English copy and the existing tokens. If the mockups change again, reconcile this section before building T4–T7. SPEC's S1/S2 names ≠ the mockups' S1…S12 captions (see the mockup index).

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| OpenRouter request/response shape wrong or model-dependent | High | Prove whole flow on `FakeRecognizer` first (Phases 1–2); pin shape with MockWebServer fixtures; model is config so it can be swapped |
| Recognition accuracy below usable bar | High | Not a Module-2 blocker by design — low-confidence flag + cashier edit + manual add absorb errors; L6 benchmark + telemetry (X-3) drive the go/no-go |
| Per-scan cost from sending full catalog | Med | Names are cheap; downscale image; narrowing is isolated behind one function to optimize after L6 cost data |
| API key leakage | Med | EncryptedSharedPreferences; never logged; never a literal; no image in telemetry |
| Latency > ~5s budget | Med | Downscale before upload; explicit overlay; measure via telemetry; tune downscale target/model |
| Module 2/3 boundary bleed (commit logic creeping into Scan) | Med | Hard stop at `onConfirm(DraftReceipt)` seam; commit/persistence is Module 3 |
| security-crypto in maintenance mode | Low | Isolated behind `RecognizerConfig`; swap implementation later without API ripple |
| Scan capture larger than one session (T4) | Med | Mockup `07` adds flash + gallery-import + info and `08` a staged overlay — more than the original wireframe. C1 still reused; split T4 if needed: (a) capture-wiring + flash + staged overlay (core), (b) gallery-import `ImageSource` + info sheet as a fast follow. |
| Edit/Add became full screens, not inline/sheet (T6/T7) | Low | Mockups moved editing to a dedicated screen (`10`) and Add Item to a full screen with multi-select (`11`/`12`); `ScanMode` carries the extra states. Domain re-pricing is unchanged — only the UI surface grew; T6 re-scoped M→L. |

## Open Questions (for sign-off)

- ~~**Scan mockups** — none exist (only Catalogue 01–06).~~ **Resolved 2026-06-07:** twelve Scan mockups (`07`–`18`) now exist and are the design source of truth; the *Screens & UI Design* section has been reconciled against them. Remaining sub-questions surfaced by the mockups:
  - **UI language** — mockups are Indonesian; **resolved: build Scan in English** to match the shipped Catalogue, with an app-wide Bahasa-Indonesia localization pass deferred. *(confirm the deferral is acceptable for the demo/MVP audience)*
  - **Gallery import** (`07`) — the camera adds a gallery-pick affordance not in SPEC. Plan treats it as a thin second `ImageSource`, shippable as a fast follow if it overruns T4. *(confirm in-scope for this module vs. defer)*
  - **Confirm/payment boundary** — mockups `15`/`16` (payment-method review + commit success) are placed in **Module 3 (Sales)**, past Scan's `onConfirm(DraftReceipt)` seam; payment method is not on `DraftReceipt`. *(confirm this boundary)*
- **Secure storage library** — `EncryptedSharedPreferences` is in maintenance mode. Accept for MVP, or prefer an alternative (e.g., Tink/Keystore directly)? *(plan assumes accept)*
- **Candidate narrowing** — confirm "send full active catalog `{sku,name}`" for MVP (vs. building a recent/frequent shortlist now). *(plan assumes full-catalog, optimize after L6)*
- **Downscale target** — confirm 1024 px / quality 80 as the starting point. *(tunable constant)*
- **Confirm hand-off** — confirm Scan stops at `onConfirm(DraftReceipt)` and the commit/Receipt (S5) is built in Module 3. *(plan assumes yes)*

## Parallelization

- **Sequential:** T1→T2→T3 (foundation) before any UI; T9→T10 (config before cloud impl).
- **Parallelizable after T3:** T4 (capture) and T5 (draft render) can start together once the `DraftReceipt` shape is fixed in T1. Phase 3 (T9–T12, cloud) can proceed in parallel with Phase 2 polish since both sit behind the `Recognizer` interface.
- **Coordinate:** lock the `DraftReceipt`/`DraftLine` (incl. `confidence`, `note`) / `UnidentifiedItem` / `ScanStage` contract (T1) before splitting T5/T6/T7 across sessions — the mockups make `note` (Edit-Item `10`), per-line `confidence` (`09`/`14`), and `ScanStage` (overlay `08`) load-bearing for the UI.
