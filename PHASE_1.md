# Phase 1 — Android Project Foundation

## Objective

Create the initial MoneyBook Android application foundation.

At the end of Phase 1, the application must compile and run and provide the complete navigation skeleton for the future MoneyBook features.

No backend functionality should be implemented yet.

---

# Requirements

Create an Android application using:

* Kotlin
* Jetpack Compose
* Material 3
* Navigation Compose
* Hilt
* Coroutines
* StateFlow

Use the package:

```text
com.moneybook
```

---

# Project Structure

Create the initial structure:

```text
com.moneybook

app/
    navigation/

core/
    common/
    ui/
    model/

data/

domain/

feature/
    auth/
    onboarding/
    home/
    transaction/
    statistics/
    settings/

notification/
```

Empty packages do not need placeholder classes unless necessary.

---

# Screens

Implement placeholder versions of:

```text
LoginScreen

HouseholdSetupScreen

NotificationPermissionScreen

HomeScreen

TransactionsScreen

AddTransactionScreen

StatisticsScreen

SettingsScreen
```

The purpose is navigation and architecture validation.

Do not implement real business logic yet.

---

# Main Navigation

After onboarding, the application should expose:

```text
Home

Transactions

Add

Statistics

Settings
```

Home, Transactions, Statistics and Settings should be accessible from bottom navigation.

Add should behave as the central transaction creation action.

---

# Temporary Development Flow

Because authentication does not exist yet, use a temporary development entry flow.

```text
Login
 ↓
Household Setup
 ↓
Notification Permission
 ↓
Home
```

Buttons may advance to the next screen using local navigation only.

Clearly mark temporary development behavior so it can be removed in Phase 2.

---

# Home Placeholder

Create a realistic static MoneyBook home screen so the navigation structure can be evaluated.

Example data:

```text
September

Total spending
₩2,431,500

Shared spending
₩1,080,000 / ₩1,500,000

Recent transactions

쿠팡
-₩54,900
나 · 공동 · 육아

스타벅스
-₩6,500
나 · 개인 · 카페

이마트
-₩87,200
상대 · 공동 · 식비
```

Static preview/mock data is allowed.

Do not connect a database.

---

# UI Requirements

Use Material 3.

Prefer a clean financial application style.

Prioritize:

* readability
* clear money hierarchy
* simple navigation
* large touch targets
* Korean text readability

Avoid excessive visual decoration.

Do not spend excessive implementation time on animations.

---

# Theme

Support:

* Light mode
* Dark mode

Use Material 3 dynamic color where appropriate.

Do not create a complex custom design system in Phase 1.

---

# Architecture Preparation

Create the minimum reusable structures necessary for future ViewModels and repositories.

Do not prematurely implement repositories that have no functionality yet.

Avoid placeholder abstractions that exist only for future speculation.

---

# Tests

At minimum verify:

* project builds successfully
* navigation destinations can be reached
* bottom navigation works
* application does not crash when navigating through the temporary onboarding flow

Add automated tests where practical.

---

# Explicitly Out of Scope

Do NOT implement:

* Supabase
* authentication
* PostgreSQL
* RLS
* transactions persistence
* NotificationListenerService
* card notification parsing
* merchant classification
* statistics calculations
* budgets
* AI
* Room

These belong to later phases.

---

# Completion Criteria

Phase 1 is complete only when:

1. Android project compiles.
2. App launches.
3. Temporary onboarding flow works.
4. All five main destinations work.
5. Home placeholder renders.
6. Light/dark theme works.
7. Relevant tests pass.
8. No future-phase functionality was unnecessarily implemented.

Provide a completion report following CODEX.md.

