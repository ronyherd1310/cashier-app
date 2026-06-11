# Repository Guidelines

## Project Structure & Module Organization

This is a single-module native Android project. The app module lives in `app/`, with production Kotlin under `app/src/main/java/com/cashierapp/photocheckout`. Key packages are `data` for Room/storage, `domain` for models and use cases, `di` for Hilt wiring, and `ui` for Compose screens, navigation, camera UI, and theme.

Resources are in `app/src/main/res`. JVM tests are in `app/src/test/java`; emulator tests are in `app/src/androidTest/java`. Room schemas are checked in under `app/schemas`.

## Current Folder Structure

- `app/` — Android application module.
- `app/src/main/java/com/cashierapp/photocheckout/` — production Kotlin source.
  - `data/` — Android and IO implementations, including Room database code, file storage, image processing, recognizer adapters, config, and telemetry.
  - `domain/` — Android-free business logic, models, money formatting, pricing, recognizer contracts, telemetry contracts, and use cases.
  - `di/` — Hilt modules and dependency wiring.
  - `ui/` — Compose UI organized by feature and shared components.
    - `catalog/` — catalogue list, add-product wizard, detail, edit, deactivate/reactivate flows.
    - `scan/` — scan capture, draft review, edit-line, add-item, and discarded-draft flows.
    - `common/` — reusable UI such as camera, dialogs, and processing overlays.
    - `settings/` — app/provider configuration UI.
    - `shell/` — app shell, destination state, and bottom navigation.
    - `theme/` — Compose Material theme tokens.
- `app/src/main/res/` — Android resources.
- `app/src/test/java/` — JVM unit tests.
- `app/src/androidTest/java/` — instrumentation, Room, and Compose UI tests.
- `app/schemas/` — exported Room schemas.
- `docs/` — single documentation root.
  - `spec/` — product and technical specifications, currently `docs/spec/SPEC.md`.
  - `plans/` — implementation plans and architecture notes.
  - `todos/` — execution checklists derived from plans.
  - `ideas/` — origin ideas and discovery notes.
  - `screenshots/` — UI references, mockups, and screen captures.
- `gradle/` — Gradle wrapper and shared build support.
- Root Gradle files (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`) — project build configuration.

## Build, Test, and Development Commands

- `./gradlew app:assembleDebug` builds the debug APK.
- `./gradlew app:installDebug` builds and installs the debug APK on a connected emulator or device.
- `./gradlew app:testDebugUnitTest` runs local JVM tests.
- `./gradlew app:connectedDebugAndroidTest` runs instrumentation and Compose UI tests.
- `./gradlew app:ktlintCheck` checks Kotlin formatting.
- `./gradlew app:ktlintFormat` formats Kotlin files.
- `./gradlew app:detekt` runs static analysis with `detekt.yml`.

If `adb` is not on `PATH`, use the SDK path from `local.properties`, for example `/home/rony-home/Android/Sdk/platform-tools/adb devices`.

## Coding Style & Naming Conventions

Kotlin uses official style (`kotlin.code.style=official`) with 4-space indentation. Keep package names lowercase and layer-aligned. Use `PascalCase` for classes, Compose screens, and test classes; use `camelCase` for functions and properties. Follow file patterns such as `CatalogListRoute.kt`, `CatalogListScreen.kt`, and `CatalogListViewModel.kt`.

Prefer small domain use cases in `domain/usecase` and keep UI state models close to their screen package. Formatting is enforced with ktlint; detekt allows Compose preview naming and UI numeric constants.

## Testing Guidelines

Use JUnit for local tests and AndroidX/JUnit4, Espresso, Room testing, and Compose UI testing for instrumentation tests. Name tests after the subject, ending in `Test`, for example `SkuGeneratorTest`.

Add local tests for domain logic and use cases. Add instrumentation tests for Room, storage, camera, navigation, and Compose UI behavior. Run connected tests when Android framework behavior changes.

## Commit & Pull Request Guidelines

Recent commits use short imperative summaries, for example `Add catalogue list screen` and `Fix connected Android tests`. Start with a verb, keep the subject concise, and describe one coherent change.

Pull requests should include a brief description, test commands run, and screenshots or recordings for visible UI changes. Link related issues or task documents. Call out schema, permission, or emulator-only changes.

## Security & Configuration Tips

Do not commit local secrets or machine-specific SDK paths beyond `local.properties`. Keep camera permission changes in `AndroidManifest.xml` deliberate and document new runtime permission flows in the PR.
