# Scan Module — Task List

> Scope: Scan module (SCAN-1…10) + the recognition/pricing seam and provider config Scan is first to need. Full detail in `docs/plans/scan-plan.md`.
> Builds on the completed Catalogue module. Order is top-to-bottom (dependency-ordered). Don't cross a checkpoint until it's green + reviewed.

## Phase 0 — Tooling
- [x] **T0** Add test/runtime deps: MockWebServer, Turbine, MockK, security-crypto — *S* · deps: none

## Phase 1 — Recognition Domain & Pricing (pure Kotlin)
- [x] **T1** Models + `Recognizer` interface (`CapturedImage`, `RecognizedItem`, `DraftReceipt`/`DraftLine`, `UnidentifiedItem`, `BoundingBox`, `CONFIDENCE_THRESHOLD`) — *M* · deps: none · [X-2]
- [x] **T2** Deterministic `pricing/` module `priceDraft` + exhaustive unit tests — *M* · deps: T1 · [X-1, SCAN-4, SCAN-5, SCAN-6]
- [x] **T3** `ScanCounter` use case + `FakeRecognizer` + unit tests — *M* · deps: T1,T2 · [SCAN-4, SCAN-6, SCAN-7, SCAN-9, SCAN-10]

### ✅ Checkpoint 1 — Domain & pricing
- [ ] Models/pricing/`ScanCounter` unit tests green; ≥90% domain+pricing coverage; no Android imports in `domain/`
- [ ] Human review

## Phase 2 — Capture → Draft → Edit (FakeRecognizer-driven)
- [x] **T4** Slice — Scan Capture (S1) *(flash/gallery/info affordances deferred as fast-follow)*: reuse C1 camera + downscale + processing overlay (C3); wire Scan tab — *L* · deps: T3 · [SCAN-1, SCAN-2, SCAN-3]
- [x] **T5** Slice — Draft Review (S2) read-only render (lines, subtotal/total, low-confidence + unidentified) — *M* · deps: T3,T4 · [SCAN-5, SCAN-6, SCAN-7]
- [x] **T6** Slice — Draft editing (qty/remove) + Confirm/Discard gate (C4) — *M* · deps: T5 · [SCAN-8, SCAN-9, SALE-1 gate, SALE-5]
- [x] **T7** Slice — Catalog picker (C2) manual "Add item" — *M* · deps: T5,T6 · [SCAN-8]
- [x] **T8** Slice — Empty/error/timeout states (recoverable, no partial draft) — *M* · deps: T4,T5 · [SCAN-9]

### ✅ Checkpoint 2 — Full flow on the Fake
- [ ] On device: capture → priced draft → edit → manual add → confirm(stub)/discard + error/empty, all on `FakeRecognizer` (no network)
- [ ] Compose UI tests green; `ktlintCheck` clean · Human review

## Phase 3 — Cloud Recognizer (OpenRouter) + Config + Telemetry
- [x] **T9** `RecognizerConfig` + secure storage (EncryptedSharedPreferences) + minimal key/model entry *(Scan-side missing-key prompt deferred to T12 when OpenRouter becomes active)* — *M* · deps: T1 · [X-2]
- [x] **T10** `OpenRouterRecognizer` impl + DTOs + MockWebServer tests *(json_object response_format vs full json_schema)* — *L* · deps: T1,T9 · [SCAN-3, SCAN-9, X-2, SCAN-INT-4]
- [x] **T11** Scan telemetry (latency/cost/accuracy recording, no images) — *M* · deps: T3,T10 · [X-3, SCAN-INT-5]
- [x] **T12** DI provider selection + vendor-swap demonstration *(stub fallback when no key, so no hard key-gate prompt needed)* — *S* · deps: T10,T11 · [X-2]

### ✅ Checkpoint 3 — Real scan works
- [ ] On device with API key: photo → OpenRouter → priced draft → confirm/discard; secure key handling
- [ ] MockWebServer + config tests green · Human review

## Phase 4 — Hardening
- [x] **T13** Scan integration tests SCAN-INT-1…5 — *M* · deps: T3,T10,T11
- [x] **T14** Polish, full `./gradlew check`, Module 3 hand-off seam (`onConfirm(DraftReceipt)`) — *M* · deps: all

### ✅ Checkpoint 4 — Module complete
- [ ] SCAN-1…10 + SCAN-INT-1…5 satisfied; `./gradlew check` green; X-1/X-2/X-3 (Scan parts) hold
- [ ] Human sign-off before Module 3 (Sales)

---

## Decisions adopted (see scan-plan.md)
- [x] D1 Candidate narrowing = send full active catalog `{sku,name}` for MVP; optimize after L6 cost data
- [x] D2 Downscale target = 1024 px longest edge, JPEG quality 80 (tunable)
- [x] D3 OpenRouter shape = OpenAI-compatible chat/completions, base64 image + text catalog + json_schema `{items:[{sku,quantity,confidence}]}`
- [x] D4 Confidence threshold = 0.6 (config-overridable)
- [x] D5 Secure storage = EncryptedSharedPreferences

## UI design
No Scan mockups exist (only Catalogue 01–06). Screens are designed in `scan-plan.md` → **Screens & UI Design** (S1 Capture, S2 Draft Review + C2 picker / C3 overlay / C4 dialog), built on the existing theme tokens. That section is the design source of truth for this module unless mockups are produced first.

## Open questions (need sign-off)
- [ ] Q0 Proceed with in-plan Scan wireframes (no new mockups) as design source of truth?
- [ ] Q1 Accept EncryptedSharedPreferences (maintenance mode) for MVP?
- [ ] Q2 Confirm full-catalog narrowing for MVP (vs. shortlist now)
- [ ] Q3 Confirm downscale target 1024px / q80
- [ ] Q4 Confirm Scan stops at `onConfirm(DraftReceipt)`; commit/Receipt (S5) is Module 3

## Module boundary reminder
Scan ends at a **finalized `DraftReceipt`** handed to `onConfirm`. `CommitSale`, `Sale` persistence, Receipt (S5), History (S6) = **Module 3 (Sales)**. Discard (SALE-5) is in scope (drops in-memory state, leaves no record).
