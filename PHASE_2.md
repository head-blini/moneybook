# MoneyBook Phase 2 — Supabase & Household

## Goal

Connect the existing Android MoneyBook app to Supabase and implement the minimum backend/auth/household functionality required for a two-person household expense tracker.

At the end of this phase:

1. A user can sign up and sign in.
2. A signed-in user can create a household.
3. The household owner can generate an invitation code.
4. A second user can join using that code.
5. A household cannot have more than two users.
6. Users cannot access another household's data.
7. Login and household state survive app restart.
8. The existing Home screen becomes reachable through real authentication/onboarding state.

Keep the implementation appropriate for a small two-person personal app.

Do not build infrastructure for hypothetical future scale.

---

## 1. Supabase Database

Create version-controlled Supabase migrations for these tables.

### profiles

* id UUID PRIMARY KEY referencing auth.users(id)
* display_name TEXT
* created_at TIMESTAMPTZ

Create the profile automatically when a Supabase Auth user is created.

### households

* id UUID PRIMARY KEY
* name TEXT NOT NULL
* created_by UUID NOT NULL
* created_at TIMESTAMPTZ

### household_members

* household_id UUID
* user_id UUID
* role TEXT
* joined_at TIMESTAMPTZ

Roles:

* OWNER
* MEMBER

Rules:

* household_id + user_id must be unique
* one user can belong to only one household
* one household can contain at most two users

### invitations

Keep this simple.

Required information:

* id
* household_id
* code
* created_by
* expires_at
* used_at
* created_at

Invitation codes are temporary and single-use.

Do not introduce a complex invitation subsystem.

---

## 2. Household Operations

Implement the minimum server-side operations necessary for household membership.

### create_household(name)

The authenticated user creates a household and becomes OWNER.

Reject the request if the user already belongs to a household.

### create_invitation()

OWNER creates an invitation code for their household.

Reject if:

* caller is not OWNER
* household already has two members

The invitation should expire.

### join_household(code)

Authenticated user joins the household associated with a valid invitation.

Reject if:

* code is invalid
* code expired
* code was already used
* user already belongs to a household
* household already contains two members

The joined user becomes MEMBER.

Mark the invitation as used.

These operations should use the authenticated Supabase user rather than accepting arbitrary user IDs from Android.

---

## 3. RLS

Enable RLS on the application tables.

Implement only the access rules required by the product.

### profiles

A user can read and update their own profile.

### households

A user can read their own household.

A user cannot read another household.

### household_members

A user can read the members of their own household.

A user cannot read another household's members.

Membership creation should happen through the household operations rather than arbitrary Android inserts.

### invitations

Do not expose invitations from unrelated households.

Joining by invitation must not require making all invitation rows readable.

Avoid complicated policy/helper structures unless PostgreSQL/Supabase actually requires them to implement these rules correctly.

---

## 4. Android Supabase Integration

Connect the Android app to Supabase.

Use the current supported Supabase Kotlin approach compatible with the existing project.

Do not guess dependency versions. Use versions compatible with the project's current Kotlin/Gradle setup.

Configuration must provide:

* Supabase project URL
* Supabase client publishable/anon key

Do not put:

* database password
* service_role key

in the Android project.

Local machine configuration must not commit secrets.

Provide an example configuration file if useful for another development machine.

---

## 5. Authentication

Implement email/password authentication for Phase 2.

Required:

* Sign up
* Sign in
* Sign out
* Session restoration

Google login is not part of this phase.

The existing temporary Phase 1 login flow must be replaced with real auth state.

---

## 6. Household Onboarding

After authentication:

If user has no household:

Show household setup.

The user can:

* create a household
* join with an invitation code

If user already belongs to a household:

Go to the main app.

After creating a household, provide a simple way to create/show an invitation code.

After the second user joins, both users should be able to see the household and its two members.

Do not over-design these screens.

Functional UI is enough for this phase.

---

## 7. App Entry Flow

App launch should resolve real state:

Unauthenticated
→ Login

Authenticated + no household
→ Household Setup

Authenticated + household
→ Home

Remove the Phase 1 fake onboarding state where it conflicts with this real state.

Notification permission onboarding is not implemented in this phase.

It belongs to the later notification-import phase.

---

## 8. Architecture

Continue using the existing project structure and CODEX.md rules.

Keep:

UI
→ ViewModel
→ Repository
→ Supabase data source

Use Coroutines and Flow/StateFlow consistently with the existing app.

Do not add abstractions unless they solve an actual Phase 2 requirement.

Do not perform unrelated refactors.

---

## 9. Basic Tests

Add useful tests for the functionality introduced in this phase.

At minimum cover important business rules where practical:

* unauthenticated app routes to Login
* authenticated user without household routes to Household Setup
* authenticated household member routes to Home
* user cannot create multiple household memberships
* household cannot exceed two members
* invalid/expired/used invitation cannot be joined
* another household's data is not accessible

Do not build an extensive enterprise security test framework.

Use the testing capabilities already available in the project/Supabase setup.

---

## 10. Out of Scope

Do not implement:

* transactions
* categories
* payment methods
* budgets
* statistics backend
* notification listener
* payment notification parsing
* merchant classification
* Room/offline cache
* AI
* OCR
* bank/card APIs
* Google login
* web app
* iOS app

These belong to later phases.

---

## 11. Remote Database Safety

Create and test migrations locally first where practical.

Do not reset the linked remote database.

Do not run destructive commands against the remote Supabase project.

Do not run `supabase db push` automatically.

Stop after the implementation and local verification so the changes can be reviewed before they are applied to the remote project.

---

## 12. Completion Report

When finished, report:

* files added
* files changed
* database changes
* Android changes
* tests executed
* test results
* anything that could not be tested
* any manual setup still required

Do not begin Phase 3.

