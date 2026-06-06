# Implementation Plan: Catalogue Module (Photo Checkout)

> Scope: **Catalogue module only** (CAT-1…7) plus the shared project/app bootstrap it's the first to need.
> Source: `SPEC.md` · UI designs: `docs/screen-capture/01..06`
> Status: Plan for review (Spec-driven Phase 2). No code written yet.
> Created: 2026-06-06

## Overview

Build the Catalogue module: the on-device product catalogue (name, IDR price, reference photo[s], auto-generated SKU) that every later module depends on. Nothing can be scanned or priced until products exist, so Catalogue is built first — which means this plan also carries the one-time project foundation (Gradle/Compose/Hilt/Room, app shell with bottom-nav). Delivery is sliced vertically: each slice carries data → domain → UI for one cashier-visible capability (list, add, detail/edit, deactivate, search/filter).

## Design findings folded into this plan (designs vs. SPEC)

These come from `docs/screen-capture/` and refine the spec. Items marked **(decision)** are adopted here; **(open)** need your confirmation (see Open Questions).

- **Add Product = 3-step wizard** — Basic Info (photo, name, SKU preview) → Pricing → Review/Save (screens 02–04). **(decision: follow design)**
- **Product Detail is its own screen** (06) with photo carousel `1/3`, inline "Edit" price, "Edit Product", red "Deactivate". **(decision)**
- **Up to 3 photos/product** — enrollment captures 1; detail screen adds more (≤3). **(decision)**
- **SKU** = **fixed `SKU-` prefix + 4-digit global counter** (`SKU-0001`); auto-generated, immutable. **(decision)** Note: this diverges from the screenshots' mnemonic letters (`NG-0001`); the wizard/detail will show `SKU-000N`, not name-derived codes.
- **Filter & Sort sheet** (05): Status = All / Active Only / Inactive Only; Sort = Name A–Z, Z–A, Price ↑, Price ↓, Newest, Oldest. **(decision)**
- **List** (01): header with live "N active items" count, "+ Add Product" button, search bar (name **+ SKU**), cards (thumbnail, name, SKU, `IDR 25.000`, Active badge, ⋮ overflow). **(decision)**
- **IDR formatting**: thousands separator `.` (Indonesian), no decimals; store `Long` rupiah. **(decision)**
- **Bottom nav = 5 tabs** (Home · Catalogue · Scan · Sales · More), Settings under "More" — adopting design 01. **(decision; SPEC to be updated.)** Only the Catalogue tab is functional in this plan; the rest are placeholders.
- **Reactivation** of deactivated products is **in scope** (detail-screen action; reachable via the Inactive Only filter). **(decision)**
- **Max 3 photos** per product — enroll 1, add up to 3 in detail (design 06). **(decision)**

## Architecture Decisions

- **Clean-ish layering** per spec project structure: `domain/` (pure Kotlin, no Android) ← `data/` (Room + file storage) ← `ui/` (Compose + ViewModels). Hilt wires them.
- **Money** is `Long` rupiah end-to-end (X-1). A dedicated `IdrFormat` handles display/parse; no float ever.
- **SKU generated at Save** as fixed `SKU-` prefix + a DB-backed monotonic 4-digit counter, persisted with a UNIQUE index; wizard shows a live *preview* before commit. Generating at save (not on wizard-open) avoids reserved-but-abandoned gaps on a single-device local app.
- **Photos** stored as files in app-internal storage; the DB row holds the relative path(s). Order preserved for the carousel. Soft-deactivated products keep their photos.
- **Soft-delete** = `active` boolean (+ `deactivatedAt`). The active-catalogue query filters `active = 1`; history/detail can still resolve inactive rows (CAT-6).
- **Gradle version catalog** (`libs.versions.toml`) with pinned versions to keep the greenfield bootstrap reproducible.
- **Camera (C1)** is built minimally here (product photo) behind an interface with an injectable image source, so Scan can reuse it and tests can bypass the real camera.

## Dependency Graph

```
T1 Project bootstrap (Gradle/Compose/Hilt/Room/theme/CI)
  └─ T2 App shell (bottom-nav host; Catalogue tab live, others placeholder)

T3 Domain models + IdrFormat + SkuGenerator (pure Kotlin)         ← T1
T4 Room entities/DAO/DB + ProductRepository + PhotoStorage         ← T3

Vertical slices (each: data→domain→ui):
  T5  Slice A — Catalog List (S3)            ← T2, T4
  T6  Slice B1 — Camera capture (C1)+storage ← T1, T4
  T7  Slice B2 — Add Product wizard + Enroll ← T3, T4, T6  (verify via T5 list)
  T8  Slice C  — Product Detail + Edit       ← T4, T7
  T9  Slice D  — Deactivate (soft-delete)    ← T4, T5, T8
  T10 Slice E  — Search + Filter & Sort      ← T4, T5

Hardening:
  T11 Catalogue integration tests (CAT-INT-1..4) ← T4,T7,T8,T9,T10
  T12 Scale (300 items) + polish + ./gradlew check ← all
```

Build order follows this bottom-up. Risk-forward note: camera (T6) is the riskiest tech — a thin camera spike may be pulled into Checkpoint 1 if device behavior is uncertain.

---

## Task List

### Phase 0 — Project & App Foundation (shared bootstrap)

#### Task 1: Bootstrap the Android project
**Description:** Create the Gradle/Android project skeleton with all module dependencies and quality tooling so everything else can compile and run.

**Acceptance criteria:**
- [ ] Kotlin + Compose app module at package `com.cashierapp.photocheckout`, minSdk 26, target latest stable; Hilt, Room, CameraX, Retrofit/OkHttp, kotlinx.serialization, Coil declared via a version catalog.
- [ ] Material3 theme implementing the **UI Styling & Design System** section as `Color.kt` / `Type.kt` / `Shape.kt` / `Dimens.kt` (teal primary, Inter/Roboto, token scale); empty `MainActivity` hosts a themed Compose surface.
- [ ] ktlint + detekt configured; `.gitignore` excludes secrets/keystore/build.

**Verification:**
- [ ] `./gradlew assembleDebug` succeeds; `./gradlew installDebug` launches a blank themed screen.
- [ ] `./gradlew ktlintCheck detekt` run clean.

**Dependencies:** None · **Files:** `settings.gradle.kts`, `build.gradle.kts`(root+app), `gradle/libs.versions.toml`, `AndroidManifest.xml`, theme files, `MainActivity.kt` · **Scope:** M

#### Task 2: App shell (bottom-nav scaffold)
**Description:** A Compose navigation host with the bottom nav from design 01; only the **Catalogue** tab is functional, others are labelled placeholders.

**Acceptance criteria:**
- [ ] Bottom nav renders 5 tabs (Home · Catalogue · Scan · Sales · More); selecting Catalogue shows a Catalogue placeholder screen; the other tabs (and Settings under More) are non-crashing placeholders.
- [ ] Nav state survives rotation/process recreation (rememberSaveable/nav state).
- [ ] Hilt `@HiltAndroidApp` + activity entry wired.

**Verification:**
- [ ] Compose UI test: Catalogue tab selected by default route, tab switch works.
- [ ] Manual: app opens to nav shell, Catalogue tab active.

**Dependencies:** T1 · **Files:** `ui/shell/*` (Scaffold, NavGraph, BottomBar), `di/AppModule.kt` · **Scope:** M

> ### ✅ Checkpoint 1 — Foundation
> - [ ] `assembleDebug` + `ktlintCheck` + `detekt` clean; app launches to the nav shell.
> - [ ] (Optional) Camera spike validated on a device if T6 risk feels high.
> - [ ] Human review before proceeding.

---

### Phase 1 — Catalogue Domain & Data Foundation

#### Task 3: Domain models + IDR formatting + SKU generator (pure Kotlin)
**Description:** The vendor/Android-free core the whole module builds on: `CatalogItem`/`ProductPhoto` models, `IdrFormat` (format/parse Indonesian rupiah to/from `Long`), and `SkuGenerator` (prefix + zero-padded sequence).

**Acceptance criteria:**
- [ ] `IdrFormat` formats `25000L → "25.000"` and parses back, rejecting decimals/negatives/garbage; round-trips exhaustively (X-1, CAT-3).
- [ ] `SkuGenerator` produces `SKU-0001` (fixed `SKU-` prefix + 4-digit zero-padded sequence), deterministic given the sequence number; uniqueness guaranteed by the counter (CAT-2).
- [ ] No `android.*` imports in `domain/`.

**Verification:**
- [ ] `./gradlew testDebugUnitTest` — unit tests for formatter (incl. edge cases) and SKU generator green; domain coverage ≥90%.

**Dependencies:** T1 · **Files:** `domain/model/*`, `domain/money/IdrFormat.kt`, `domain/catalog/SkuGenerator.kt`, tests · **Scope:** M

#### Task 4: Room persistence + repository + photo storage
**Description:** Room schema (product + photo tables), DAO, database, `ProductRepository`, and `PhotoStorage` (save/delete image files in app-internal storage, return relative paths).

**Acceptance criteria:**
- [ ] `ProductEntity` (id, sku UNIQUE, name, priceMinor, active, createdAt, deactivatedAt) + `ProductPhotoEntity` (productId FK, path, position); schema version 1 exported.
- [ ] Repository exposes: observe active products (Flow), get by id/sku, insert, update, set-active=false; `PhotoStorage` writes/reads/deletes files by path.
- [ ] SKU UNIQUE constraint enforced; price stored as `Long`.

**Verification:**
- [ ] Instrumented Room tests (in-memory DB): insert→read round-trips, UNIQUE violation on dup SKU, active filter excludes deactivated; `PhotoStorage` write→read→delete.
- [ ] `./gradlew connectedDebugAndroidTest` (Room tests) green.

**Dependencies:** T3 · **Files:** `data/db/*` (entities, DAO, Database), `data/db/ProductRepository.kt`, `data/storage/PhotoStorage.kt`, tests · **Scope:** M

> ### ✅ Checkpoint 2 — Domain & Data
> - [ ] Unit + Room tests green; schema v1 exported. Human review.

---

### Phase 2 — Catalogue Vertical Slices

#### Task 5: Slice A — Catalog List (S3, design 01)
**Description:** The catalogue landing: live "N active items" count, "+ Add Product", search bar (wired in T10), product cards (thumbnail/name/SKU/IDR price/Active badge/⋮), and an empty state.

**Acceptance criteria:**
- [ ] Active products render as cards matching design 01; header count reflects active total; empty state prompts first enrollment (CAT-5 view, CAT-7).
- [ ] List is backed by the repository Flow (updates reactively) and renders thumbnails via Coil from stored photo paths.
- [ ] ⋮ overflow opens a menu (Edit / Deactivate) — actions wired in T8/T9.

**Verification:**
- [ ] Compose UI test with seeded DB: cards shown, count correct, empty state when none.
- [ ] Manual: scroll a seeded list smoothly.

**Dependencies:** T2, T4 · **Files:** `ui/catalog/list/*` (Screen, ViewModel, Card, EmptyState) · **Scope:** M

#### Task 6: Slice B1 — Camera capture (C1) + photo persistence
**Description:** Reusable CameraX capture flow with permission handling; captured image saved via `PhotoStorage`. Designed for reuse by Scan; uses an injectable image source so tests bypass hardware.

**Acceptance criteria:**
- [ ] Tapping "Add Photo" (design 02) opens camera; capture returns a saved file path to the caller (CAT-1, CAT-4).
- [ ] Permission denied → clear, recoverable rationale UI; no crash (mirrors SCAN-1 handling).
- [ ] Image source is injectable (fake provides a fixture image in tests).

**Verification:**
- [ ] Unit/instrumented test with fake image source returns a path; permission-denied path renders rationale.
- [ ] Manual device QA: real capture saves and displays.

**Dependencies:** T1, T4 · **Files:** `ui/common/camera/*` (CameraCapture, PermissionHandler), wiring to `PhotoStorage` · **Scope:** M

#### Task 7: Slice B2 — Add Product wizard + EnrollProduct
**Description:** The 3-step Add wizard (designs 02–04): Basic Info (photo + name + live SKU preview), Pricing (IDR input + preview), Review (Save). `EnrollProduct` use case persists product + photo and generates the final SKU; the new product appears in the list.

**Acceptance criteria:**
- [ ] Wizard enforces required fields per step (photo+name, valid price) and shows SKU preview + IDR price preview (CAT-1, CAT-3).
- [ ] Save runs `EnrollProduct`: generates unique SKU (CAT-2), stores price as `Long`, persists the photo, returns to list where the product now appears (CAT-4).
- [ ] Back/forward preserves entered state; cancel discards with no DB write.

**Verification:**
- [ ] Unit tests for `EnrollProduct` (SKU uniqueness, price persistence, validation rejects).
- [ ] Compose UI test drives all 3 steps → Save → assert product in list (fake camera image).

**Dependencies:** T3, T4, T6 (verify against T5) · **Files:** `ui/catalog/add/*` (WizardScreen+steps, ViewModel), `domain/usecase/EnrollProduct.kt` · **Scope:** L *(if needed, split into 7a wizard UI/state and 7b enroll+persist)*

> ### ✅ Checkpoint 3 — Add & List works end-to-end
> - [ ] Enroll a product (with photo) → it appears in the list and survives app restart.
> - [ ] Unit + UI tests green; `ktlintCheck` clean. Human review.

#### Task 8: Slice C — Product Detail + Edit (design 06)
**Description:** Product Detail screen: photo carousel (≤3), name, SKU (read-only), inline price "Edit", "Edit Product" (name/photos), add/remove photos up to 3. `UpdateProduct` use case.

**Acceptance criteria:**
- [ ] Detail renders per design 06 (Active badge, `1/3` carousel, price + Edit, Photos section, Edit Product / Deactivate buttons) (CAT-5 edit).
- [ ] Editing name/price/photos persists via `UpdateProduct`; SKU never changes; photos respect the ≤3 cap (CAT-4).
- [ ] Removing a photo deletes its file; reordering preserves positions.

**Verification:**
- [ ] Unit tests for `UpdateProduct` (immutability of SKU, photo cap).
- [ ] Compose UI test: edit price → reflected in list; add/remove photo updates carousel.

**Dependencies:** T4, T7 · **Files:** `ui/catalog/detail/*` (Screen, ViewModel), `domain/usecase/UpdateProduct.kt` · **Scope:** L *(optionally split: detail-view vs. edit-actions)*

#### Task 9: Slice D — Deactivate / Reactivate (soft-delete toggle)
**Description:** Deactivate from Detail (red button) and List (⋮ → Deactivate), guarded by a reusable confirm dialog (C4). Deactivated products drop out of the active list but remain in the DB; from the Inactive Only view, a deactivated product's detail offers **Reactivate** to restore it.

**Acceptance criteria:**
- [ ] Confirm dialog before deactivation; on confirm, product disappears from active list and header count drops (CAT-6).
- [ ] Row persists with `active=false` + `deactivatedAt`; still resolvable by id/sku for history/detail; no hard delete; photos retained.
- [ ] An inactive product's detail shows **Reactivate**; confirming restores it to the active list (clears `deactivatedAt`).

**Verification:**
- [ ] Repository/integration test: deactivate excludes from active query (still gettable by id); reactivate returns it to the active query.
- [ ] Compose UI test: deactivate from list and detail; reactivate from an inactive product's detail.

**Dependencies:** T4, T5, T8 · **Files:** `ui/common/dialogs/ConfirmDialog.kt`, list+detail wiring, `domain/usecase/SetProductActive.kt` (deactivate+reactivate) · **Scope:** M

#### Task 10: Slice E — Search + Filter & Sort (designs 01, 05)
**Description:** Search bar (name + SKU) and the Filter & Sort bottom sheet: Status (All/Active/Inactive) and six sort orders; Reset/Apply.

**Acceptance criteria:**
- [ ] Typing filters the list by name/SKU in real time (CAT-5).
- [ ] Filter sheet applies Status + Sort; "Inactive Only" surfaces deactivated products (tapping one opens its detail, where Reactivate lives — T9); selections persist until Reset (CAT-5, CAT-7).
- [ ] Sorting matches: Name A–Z/Z–A, Price ↑/↓, Newest/Oldest.

**Verification:**
- [ ] Unit tests for the query/sort builder (each option).
- [ ] Compose UI test: search narrows results; each filter/sort applied.

**Dependencies:** T4, T5 · **Files:** `ui/catalog/list/*` (search + filter sheet, state), repository query params · **Scope:** M

> ### ✅ Checkpoint 4 — Full Catalogue CRUD + Search/Filter
> - [ ] Add → list → detail/edit → deactivate → search/filter all work on device.
> - [ ] All Compose UI tests green. Human review.

---

### Phase 3 — Catalogue Hardening

#### Task 11: Catalogue integration tests (CAT-INT-1…4)
**Description:** Wire real collaborators (use cases → repository → Room → PhotoStorage) per the spec's integration plan.

**Acceptance criteria:**
- [ ] CAT-INT-1 enroll writes row + photo file, retrievable by SKU, survives DB reopen.
- [ ] CAT-INT-2 price validation → stored minor units → read back; invalid rejected.
- [ ] CAT-INT-3 soft-delete excludes from active query, resolvable for history.
- [ ] CAT-INT-4 search/filter over a seeded 50–300 item catalog correct.

**Verification:**
- [ ] `./gradlew connectedDebugAndroidTest` — integration suite green.

**Dependencies:** T4, T7, T8, T9, T10 · **Files:** `app/src/androidTest/.../catalog/*` · **Scope:** M

#### Task 12: Scale, polish & full check
**Description:** Validate responsiveness at scale, finish empty/error/loading states, and get the whole module green through `./gradlew check`.

**Acceptance criteria:**
- [ ] Seed 300 active items; list scroll + search stay responsive (CAT-7).
- [ ] Empty/error/loading states polished across list/add/detail; strings/contentDescriptions in place.
- [ ] `./gradlew check` (unit + lint + detekt + ktlint + instrumented where wired) green.

**Verification:**
- [ ] Manual: 300-item smoke; `./gradlew check` passes.

**Dependencies:** All · **Files:** polish across `ui/catalog/*`, perf tweaks · **Scope:** M

> ### ✅ Checkpoint 5 — Module complete
> - [ ] CAT-1…7 + CAT-INT-1…4 satisfied; `./gradlew check` green. Human review / sign-off before next module.

---

## UI Styling & Design System (from `docs/screen-capture/`)

This section translates the six Catalogue mockups into implementation-ready tokens for a Compose **Material3** theme. **All hex/dp values are visual approximations** — if a Figma/source file exists, confirm exact values against it. Implement these as theme files in **T1** (`Color.kt`, `Type.kt`, `Shape.kt`, `Dimens.kt`) so every UI slice (T5–T10) consumes tokens, not literals.

> Two corrections the designs make to earlier notes: the **"+ Add Product" is a full-width button** at the top of the list (design 01), **not** a FAB; and the list top bar has **two trailing icon buttons** — search (magnifier) and filter (funnel/tune); the funnel opens the Filter & Sort sheet (design 05).

### Color tokens → Material3 roles

| Token | Approx hex | M3 role | Used for |
|---|---|---|---|
| Primary (brand teal) | `#0F766E` | `primary` | Filled buttons, active tab, active step, selected radio, links |
| Primary pressed | `#0B5E57` | (state) | Pressed/!enabled filled buttons |
| On primary | `#FFFFFF` | `onPrimary` | Text/icon on teal |
| Badge mint | `#E3F3EE` | `primaryContainer` | "Active" badge background |
| Badge text | `#0F766E` | `onPrimaryContainer` | "Active" badge text |
| Background | `#FFFFFF` | `background`/`surface` | Screen + card surfaces |
| Subtle fill | `#F3F5F7` | `surfaceVariant` | Inputs, search field, secondary-button bg, "Add Photo" dropzone |
| Outline | `#E5E7EB` | `outline` | Card/input borders, dividers |
| Outline variant | `#CBD5E1` | `outlineVariant` | Dashed dropzone border, stepper inactive ring/connector |
| Text primary | `#111827` | `onSurface` | Titles, product names, prices |
| Text secondary | `#6B7280` | `onSurfaceVariant` | SKU, helper text, subtitles, inactive tab |
| Text placeholder | `#9CA3AF` | (variant) | Input placeholders |
| Error/destructive | `#DC2626` | `error` | "Deactivate" button text+icon |
| Scrim | `#000000` @ 40% | `scrim` | Bottom-sheet / processing-overlay backdrop |

Light theme only for MVP (designs are light). Define a dark scheme later.

### Typography → Material3 type scale

Font: **Inter** preferred (bundle) or system Roboto fallback. Weights: Regular 400, Medium 500, SemiBold 600, Bold 700.

| Usage | M3 style | Size / weight | Examples |
|---|---|---|---|
| Screen large title | `headlineMedium` | ~28sp Bold | "Catalogue" |
| Centered top-bar title | `titleLarge` | ~20sp SemiBold | "Add Product", "Product Detail" |
| Section header | `headlineSmall` | ~24sp Bold | "Basic Information", "Pricing", "Review" |
| Section subtitle / helper | `bodyMedium` | 14sp Regular, secondary | "Enter the basic details…", field helper text |
| Product name (card) | `titleMedium` | 16sp SemiBold | "Nasi Goreng Spesial" |
| Large price value | `headlineSmall` | ~24sp Bold | "25.000" on Pricing/Detail |
| Price (card/list) | `titleSmall` | 15–16sp Bold | "IDR 25.000" |
| SKU & meta | `bodySmall` | 12–13sp, secondary | "SKU · NG-0001" |
| Field label | `labelMedium` | 12sp, secondary | "Product Name", "Price (IDR)" |
| Button label | `labelLarge` | 16sp Medium | "Next", "Save Product" |
| Badge | `labelSmall` | 11–12sp Medium | "Active" |
| Bottom-nav label | `labelSmall` | 11sp Medium | "Catalogue" |

### Shape & spacing

- **Corner radii:** xs 8dp · **sm 12dp** (buttons, inputs, thumbnails-ish) · **md 16dp** (cards, thumbnails) · **lg 24dp** (bottom-sheet top) · **full** (badges = pill; search field reads as a soft pill ~16–20dp).
- **Spacing scale (4dp base):** 4 · 8 · 12 · 16 · 20 · 24 · 32. Screen horizontal padding **20dp**; inter-section gap **24dp**; card inner padding **12–16dp**.
- **Elevation:** cards use a soft shadow (M3 `tonalElevation`/`shadowElevation` ~1–2dp) or a 1dp `outline` border; avoid heavy shadows.

### Component specs (→ Compose)

- **Primary button** — filled teal, full-width, height **52dp**, radius 12dp, `labelLarge` white, optional leading icon (`+`). States: pressed (darker), disabled (greyed). `Button`.
- **Secondary button** — "Back"/"Reset": `surfaceVariant` bg or `OutlinedButton` with `outline` border, dark text, same height/radius.
- **Destructive button** — "Deactivate": text+icon in `error` red, transparent/`surfaceVariant` bg. `TextButton`/`OutlinedButton`.
- **Text input** — `OutlinedTextField`, label above in `labelMedium` secondary, white fill, `outline` border (teal when focused), helper text below in `bodySmall` secondary; height ~56dp, radius 12dp.
- **Card** — `Surface`/`Card`, radius 16dp, white, 1dp `outline` or ~1dp shadow.
- **Product list item** (design 01) — Row in a card: thumbnail 56–64dp radius 12dp (Coil), then column {name `titleMedium`, "SKU · …" `bodySmall` secondary, "IDR 25.000" `titleSmall` bold}, trailing **Active badge** + `more_vert` ⋮.
- **Active/Inactive badge** — pill, `primaryContainer` bg + `onPrimaryContainer` text (Active); use a neutral grey container for Inactive. Padding 4dp×8dp, `labelSmall`.
- **Search field** (design 01) — filled `surfaceVariant`, leading `search` icon, placeholder "Search products…", pill-ish radius.
- **Stepper** (designs 02–04) — 3 numbered circles + connectors: active = filled teal + white number; completed = teal (✓ optional); inactive = `outlineVariant` ring + secondary number; connector line `outlineVariant`.
- **Add-Photo dropzone** (design 02) — card with **dashed** `outlineVariant` border, radius 16dp, centered `photo_camera` icon + "Add Photo" + helper "(1 photo required for MVP)".
- **Photo carousel + page indicator** (design 06) — image (radius 16dp) with a small dark translucent pill "1 / 3" bottom-right; horizontal pager.
- **Edit-Photo chip** (design 04) — small translucent/white pill with pencil icon over the review image.
- **Bottom sheet — Filter & Sort** (design 05) — `ModalBottomSheet`, top radius 24dp, drag handle; section labels ("Status", "Sort By") bold; **radio** lists (teal selected); footer Row { Reset (secondary) · Apply (primary) }.
- **Bottom nav** (design 01) — `NavigationBar`, 5 items (Home·Catalogue·Scan·Sales·More), active = teal icon+label, inactive = secondary grey; white bg.
- **Processing overlay (C3)** — centered spinner on a `scrim` backdrop (used S1→S2; defined here for reuse).

### Iconography & assets

- Material Symbols (Outlined): `search`, `tune`/`filter_list`, `photo_camera`, `add`, `more_vert`, `edit`, `chevron_left`, `check`, `ios_share`, plus a block/remove icon for Deactivate.
- All actionable icons get `contentDescription`; min touch target **48dp**; verify text/contrast meets WCAG AA on teal.

### States to implement (don't skip)

Empty (list first-run), loading (list/save/overlay), error (save/validation, recoverable), disabled (incomplete wizard step), selected/focused (tabs, radios, inputs), active vs inactive (badge + list filtering).

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| CameraX permission/device variance | Med | Isolate in C1 (T6) with injectable image source; recoverable deny UI; manual device QA; optional early spike at Checkpoint 1 |
| SKU collisions | Low | Fixed `SKU-` prefix + DB-backed monotonic counter + UNIQUE index; uniqueness unit/Room tests |
| IDR parse/format locale bugs (X-1 money) | Med | Dedicated `IdrFormat` with exhaustive round-trip unit tests; store `Long` only |
| Greenfield bootstrap (dependency/version churn) | Med | Pinned version catalog; get Checkpoint 1 green before feature work |
| Orphan photo files on edit/remove | Low–Med | `PhotoStorage.delete` on photo removal; soft-delete keeps files intentionally |
| Nav model mismatch (5 tabs vs 3+Settings) | Low | Only Catalogue tab functional now; reconcile when other modules planned |
| Wizard/Detail tasks larger than ideal | Low | Split notes provided (7a/7b, detail/edit) if a task exceeds one session |

## Resolved Decisions (plan phase — 2026-06-06)

1. **SKU** — fixed `SKU-` prefix + 4-digit global counter (`SKU-0001`). Diverges from screenshots' mnemonic letters; UI shows `SKU-000N`. (T3)
2. **Bottom nav** — adopt design's 5 tabs (Home · Catalogue · Scan · Sales · More), Settings under More; only Catalogue functional now. SPEC to be updated. (T2)
3. **Reactivation** — in scope: Reactivate action on an inactive product's detail, reachable via the Inactive Only filter. (T9/T10)
4. **Search scope** — name **+ SKU**. (T10)
5. **Max photos** — **3** per product (enroll 1, add up to 3 in detail). (T4/T7/T8)

## Parallelization

- **Sequential:** T1→T2; T3→T4; both before any slice.
- **Parallelizable after T4:** T5 (List) and T6 (Camera) are independent. T10 (Search/Filter) can proceed alongside T8/T9 once T5 lands.
- **Coordinate:** T7/T8/T9 share list/detail state — define the list item + detail state contracts (in T5) before splitting across sessions.
