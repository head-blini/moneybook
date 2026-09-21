# MoneyBook Android

Phase 2 connects the Compose app to Supabase email/password authentication and a
two-person household. Home transaction figures remain static examples; transaction
persistence and notification import are later phases.

## Build and run

Use JDK 17 or 21, Android SDK Platform 36 and Build Tools 35.0.0. Copy the values
from `local.properties.example` into untracked `local.properties`. Use a Supabase
publishable key or legacy anon key, never service_role.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# Start an Android emulator (API 26+) first:
./gradlew :app:connectedDebugAndroidTest
```

If multiple devices are connected, set `ANDROID_SERIAL` to the desired emulator.
Open the project in Android Studio and run `app`, or install
`app/build/outputs/apk/debug/app-debug.apk` with ADB.

Database changes live in
`supabase/migrations/20260918000000_phase_2_database_foundation.sql`. Test locally:

```sh
supabase start
supabase db reset --local --no-seed
supabase db lint --local --schema public,moneybook_private --level warning --fail-on warning
supabase test db --local
```

Do not use `supabase db push` until the migration has been reviewed for the target
project.

## Structure and dependencies

The app follows UI → ViewModel → repository → Supabase. Auth and household
repositories are the only Phase 2 data abstractions.

Supabase Kotlin Auth restores its stored session. On launch the app resolves Login,
Household Setup, or Home from the real session and membership query. Owners can
create a 24-hour, single-use invitation in Settings. Both household members can see
the household and its membership list.

The bottom navigation and static Home preview from Phase 1 remain intact. Notification
permission onboarding has been removed because it belongs to a later phase.

## Validation

Android and database validation reports are generated under `app/build/reports/`
and by the Supabase CLI respectively.

Phase 2A was validated on 2026-09-21 with a Samsung SM-G986N running Android 13:

* `./gradlew assembleDebug` — passed
* `./gradlew testDebugUnitTest` — 11/11 passed
* `./gradlew connectedDebugAndroidTest` — 7/7 passed
* `supabase test db --local` — 34/34 pgTAP assertions passed

Manual device validation also covered email sign-up and sign-in, household creation,
invitation and two-person joining, rejection of a third member, session and household
restoration after app restart, and persistent sign-out after app restart.
