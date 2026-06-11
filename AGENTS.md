# Repository Guidelines

## Project Structure & Module Organization

This is a single-module native Android project. The app module lives in `app/`, with production Kotlin under `app/src/main/java/com/cashierapp/photocheckout`. Key packages are `data` for Room/storage, `domain` for models and use cases, `di` for Hilt wiring, and `ui` for Compose screens, navigation, camera UI, and theme.

Resources are in `app/src/main/res`. JVM tests are in `app/src/test/java`; emulator tests are in `app/src/androidTest/java`. Room schemas are checked in under `app/schemas`.

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
