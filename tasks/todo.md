# Catalogue Module — Task List

> Scope: Catalogue module (CAT-1…7) + shared bootstrap. Full detail in `tasks/plan.md`.
> Order is top-to-bottom (dependency-ordered). Don't cross a checkpoint until it's green + reviewed.

## Phase 0 — Project & App Foundation
- [ ] **T1** Bootstrap Android project (Gradle, Compose, Hilt, Room, CameraX, deps, theme, ktlint/detekt) — *M*
- [ ] **T2** App shell: bottom-nav scaffold (5 tabs: Home·Catalogue·Scan·Sales·More), Catalogue tab functional (others placeholder) — *M* · deps: T1

### ✅ Checkpoint 1 — Foundation
- [ ] `assembleDebug` + `ktlintCheck` + `detekt` clean; app launches to nav shell
- [ ] Human review

## Phase 1 — Domain & Data Foundation
- [ ] **T3** Domain models + `IdrFormat` + `SkuGenerator` (fixed `SKU-` prefix + counter; pure Kotlin, unit-tested) — *M* · deps: T1 · [CAT-2, CAT-3, X-1]
- [ ] **T4** Room entities/DAO/DB + `ProductRepository` + `PhotoStorage` (Room tests) — *M* · deps: T3 · [CAT-4, CAT-7]

### ✅ Checkpoint 2 — Domain & Data
- [ ] Unit + Room tests green; schema v1 exported
- [ ] Human review

## Phase 2 — Vertical Slices
- [ ] **T5** Slice A — Catalog List (S3 / design 01): cards, count, empty state — *M* · deps: T2,T4 · [CAT-5, CAT-7]
- [ ] **T6** Slice B1 — Camera capture (C1) + photo persistence, permission handling — *M* · deps: T1,T4 · [CAT-1, CAT-4]
- [ ] **T7** Slice B2 — Add Product 3-step wizard (designs 02–04) + `EnrollProduct` — *L* · deps: T3,T4,T6 · [CAT-1, CAT-2, CAT-3, CAT-4]

### ✅ Checkpoint 3 — Add & List end-to-end
- [ ] Enroll a product with photo → appears in list → survives restart
- [ ] Unit + UI tests green; `ktlintCheck` clean · Human review

- [ ] **T8** Slice C — Product Detail + Edit (design 06): carousel ≤3, inline edit, `UpdateProduct` — *L* · deps: T4,T7 · [CAT-5, CAT-4]
- [ ] **T9** Slice D — Deactivate / Reactivate (soft-delete toggle) + confirm dialog (C4) — *M* · deps: T4,T5,T8 · [CAT-6]
- [ ] **T10** Slice E — Search + Filter & Sort sheet (designs 01, 05) — *M* · deps: T4,T5 · [CAT-5, CAT-7]

### ✅ Checkpoint 4 — Full CRUD + Search/Filter
- [ ] Add → list → detail/edit → deactivate → search/filter all work on device
- [ ] Compose UI tests green · Human review

## Phase 3 — Hardening
- [ ] **T11** Catalogue integration tests CAT-INT-1…4 — *M* · deps: T4,T7,T8,T9,T10
- [ ] **T12** Scale (300 items) + polish states + `./gradlew check` green — *M* · deps: all

### ✅ Checkpoint 5 — Module complete
- [ ] CAT-1…7 + CAT-INT-1…4 satisfied; `./gradlew check` green
- [ ] Human sign-off before next module

---

## Resolved decisions (see plan.md)
- [x] Q1 SKU = fixed `SKU-` prefix + 4-digit counter (`SKU-0001`)
- [x] Q2 Bottom nav = 5 tabs (Home·Catalogue·Scan·Sales·More), Settings under More
- [x] Q3 Reactivation in scope (from inactive product detail)
- [x] Q4 Search scope = name + SKU
- [x] Q5 Max photos = 3
