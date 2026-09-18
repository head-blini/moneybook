# MoneyBook Codex Instructions

## Role

You are the implementation agent for the MoneyBook Android project.

Follow the existing architecture and requirements.

Do not independently redesign the product.

---

# Source of Truth

Before implementing a task, read:

1. ARCHITECTURE.md
2. Current phase task document
3. Existing code

Priority:

Current explicit task

>

ARCHITECTURE.md

>

Existing implementation

If requirements conflict, stop and report the conflict instead of silently choosing one.

---

# Core Development Rule

Implement only the requested phase.

Do not implement future features unless they are strictly required for the current task.

Examples:

If implementing navigation:

Do not implement Supabase.

If implementing Supabase Auth:

Do not implement transactions.

If implementing transactions:

Do not implement AI.

---

# Architecture

Follow:

```text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository
 ↓
Data Source
```

Do not bypass layers for convenience when doing so introduces coupling.

UI must not directly access Supabase.

ViewModel must not directly access Supabase.

---

# Kotlin

Prefer idiomatic Kotlin.

Use:

* immutable data
* data classes
* sealed interfaces/classes where appropriate
* coroutines
* Flow / StateFlow
* dependency injection

Avoid:

* unnecessary inheritance
* global mutable state
* `!!`
* blocking calls
* God classes
* oversized ViewModels

---

# Compose

Composable functions should primarily render state and emit events.

Prefer:

```text
Screen
    ↓
UiState
    ↓
UiEvent
    ↓
ViewModel
```

Do not place database/network/business logic inside Composables.

Create reusable components only when actual reuse exists.

Do not create abstraction solely for hypothetical future requirements.

---

# Security

Treat Android as an untrusted client.

Never rely on UI checks for authorization.

Never include:

* Supabase service role keys
* database administrator credentials
* private server secrets

RLS is the final authorization boundary.

Never weaken an RLS policy simply to make an Android request succeed.

If an RLS policy prevents a legitimate operation, investigate the policy and data model.

---

# Database

Never make destructive schema changes without explicitly reporting them.

Prefer migrations.

Do not silently:

* drop tables
* remove columns
* delete user data
* disable RLS

All database changes must be reproducible.

---

# Privacy

PERSONAL transactions must never be exposed to another Household member at row level.

Do not fetch private rows and hide them in Android UI.

Privacy enforcement belongs on the backend.

---

# Money

Never represent KRW using Float or Double.

Use Long.

Example:

```text
₩54,900

54900L
```

---

# Notification Parsing

Financial notification text is untrusted input.

Parsers must fail safely.

A parser failure must not crash NotificationListenerService.

Do not create a confirmed transaction when parsing confidence is insufficient.

Use PENDING where appropriate.

---

# Testing

When implementing business logic, write tests.

Before marking a task complete:

1. compile
2. run relevant tests
3. inspect failures
4. fix failures
5. rerun tests

Do not report a task as complete when tests are failing.

---

# Dependency Policy

Before adding a dependency:

1. verify whether existing dependencies can solve the problem
2. explain why the dependency is needed
3. use a maintained library
4. avoid redundant libraries

Do not add large frameworks for trivial functionality.

---

# Scope Control

Avoid speculative features.

Do not add:

* AI
* investments
* OCR
* Open Banking
* analytics SDKs
* advertisements
* subscription/payment systems

unless explicitly requested.

---

# Refactoring

Do not perform unrelated large refactors while implementing a feature.

If significant technical debt is discovered:

Report:

```text
Problem
Impact
Recommended change
Affected files
```

before performing a large refactor.

---

# Completion Report

At the end of each task provide:

## Implemented

What was added.

## Files Changed

Important files created or modified.

## Tests

Tests executed and results.

## Decisions

Important implementation decisions.

## Issues

Known limitations or unresolved problems.

## Next

Recommended next task.

Do not claim success without verification.

