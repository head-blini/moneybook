# MoneyBook Android

Phase 1 navigation foundation. All displayed financial figures are static examples.
No authentication, backend, permissions, notification collection, or persistence is implemented.

## Build and run

Use JDK 17 or 21, Android SDK Platform 36 and Build Tools 35.0.0.
Set `sdk.dir` in your untracked `local.properties`, or set `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# Start an Android emulator (API 26+) first:
./gradlew :app:connectedDebugAndroidTest
```

If multiple devices are connected, set `ANDROID_SERIAL` to the desired emulator.
Open the project in Android Studio and run `app`, or install
`app/build/outputs/apk/debug/app-debug.apk` with ADB.

## Structure and dependencies

The single app module uses `com.moneybook` with app/navigation, core/ui, and feature
packages. Empty core/common, core/model, data, domain, and notification directories
are reserved locally; Git does not preserve empty directories. Add their first files
when a later phase requires them. No speculative repository or use-case interfaces exist.

Compose Material 3 renders screens; Navigation Compose owns navigation/back stacks.
Hilt creates the application and Home ViewModel. Lifecycle Compose collects its
read-only StateFlow. Coroutines supplies Flow; no background work is needed yet.
JUnit and Compose UI testing cover state, navigation, recreation, and themes.
Versions are pinned to a tested AGP 8.13 / Kotlin 2.2.10 / Gradle 8.13 toolchain.
The Hilt setup follows https://developer.android.com/training/dependency-injection/hilt-android.

## Temporary behavior

Search `PHASE_1_DEV` for replacement points. Cold launch starts at Login; local buttons
advance through Household Setup and Notification Permission to Home. Entering Home
clears onboarding from the back stack. The four bottom tabs restore navigation state;
central Add opens a placeholder and closes/back-navigates to its caller. No data is saved.
System light/dark mode and Android 12+ dynamic color are supported.

## Validation

Validated on an API 34 ARM64 emulator: 2 JVM tests and 6 instrumentation tests.
Instrumentation covers all destinations, onboarding, tab selection, Add close/back,
return to Home, activity recreation, and static/dynamic light/dark Home rendering.
Reports are generated under `app/build/reports/`.
