# MoneyBook Android

Phase 2A connects the Compose app to Supabase email/password authentication and a
two-person household. Phase 2B-1 adds the database foundation for categories,
cards, shared/private transactions, partial refunds, aggregate card performance,
transaction soft deletion, and automatic household default categories. Android
transaction entry, monthly history, editing, refund, and soft-delete restore are
implemented in Phase 2B-2. Phase 3 rebuilds Home and monthly Statistics on that
foundation. Notification import remains later work.

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

Database changes live in versioned files under `supabase/migrations/`. Test locally:

```sh
supabase start
supabase db reset --local --no-seed
supabase db lint --local --schema public,moneybook_private --level warning --fail-on warning
supabase test db --local
```

Do not use `supabase db push` until the migration has been reviewed for the target
project.

The Phase 2B card performance RPC returns household-visible aggregates only. It does
not expose a partner's personal transaction rows or separate personal spending total.
Transactions are soft-deleted through RPCs, remain available for authorized restore,
and retain their refund history and notification fingerprint. New households receive
12 expense and 5 income categories atomically; the same defaults are backfilled into
existing households without replacing or reactivating matching categories.

## Structure and dependencies

The app follows UI → ViewModel → repository → Supabase. Auth and household
repositories are the only Phase 2 data abstractions.

Supabase Kotlin Auth restores its stored session. On launch the app resolves Login,
Household Setup, or Home from the real session and membership query. Owners can
create a 24-hour, single-use invitation in Settings. Both household members can see
the household and its membership list.

The bottom navigation remains intact. Phase 3A Home reads the visible current-month
transactions and the existing `get_monthly_summary` RPC, showing income, net expense,
balance, and up to five recent transactions. Its title uses memo, then category, then
"거래". Home reloads on return to the tab and app resume. Notification permission
onboarding belongs to a later phase.

Phase 3B Statistics uses the same monthly summary RPC. Category amounts use only
visible expense transactions in the selected Seoul month, excluding pending and
soft-deleted rows and subtracting confirmed refunds from the original transaction
month. The month buttons stop at the current month; income-only months still show
the monthly totals. Separate queries can briefly disagree if data changes between
requests.

## Validation

Android and database validation reports are generated under `app/build/reports/`
and by the Supabase CLI respectively.

Phase 3A rebuild on the home Mac: `./gradlew :app:testDebugUnitTest --console=plain`
passed. Build, lint, and device checks are reported with the completed Phase 3B work.

Phase 3B rebuild on the home Mac: `./gradlew :app:testDebugUnitTest :app:assembleDebug
:app:lintDebug :app:assembleDebugAndroidTest --console=plain` passed. A final
`./gradlew :app:testDebugUnitTest --console=plain` run reported 39 tests,
0 failures, 0 errors, and 0 skipped; lint reported 0 errors and 13 warnings.
No Android device was connected, so instrumented tests were compiled but not run
on a device.

Phase 2A was validated on 2026-09-21 with a Samsung SM-G986N running Android 13:

* `./gradlew assembleDebug` — passed
* `./gradlew testDebugUnitTest` — 11/11 passed
* `./gradlew connectedDebugAndroidTest` — 7/7 passed
* `supabase test db --local` — 34/34 pgTAP assertions passed

Manual device validation also covered email sign-up and sign-in, household creation,
invitation and two-person joining, rejection of a third member, session and household
restoration after app restart, and persistent sign-out after app restart.

Phase 2B-1 database validation on 2026-09-21 passed 128/128 local pgTAP assertions
(34 Phase 2A regression assertions and 94 Phase 2B assertions). A full local database
reset reapplied all migrations successfully, and database lint reported no schema errors.

Phase 2B-2 adds the Android transaction domain/data layer and Compose transaction
entry/history flows. Automated tests cover form validation, income without a card,
duplicate-save protection, shared/personal filtering, editing, soft-delete/restore,
partial refunds, full-refund status, empty state, and repository errors. Validation
passed 29/29 unit tests and 9/9 instrumented tests. Manual acceptance testing on a
Samsung SM-G986N running Android 13 verified the actual Supabase flow for shared and
personal income/expenses, partner visibility, creation, editing, partial/full refunds,
delete/Undo, restart persistence, and the absence of payer-selection UI.
