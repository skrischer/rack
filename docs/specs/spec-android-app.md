# Spec: Phase 3 — Android: auth + plan view + logging

> Created: 2026-06-17

Build the native Android app (Kotlin + Jetpack Compose, MVVM, Coroutines/Flow) that logs the user into Supabase Auth, reads their plans/days/exercises under RLS through a `supabase-kt` repository layer, and lets them log sets (kg + per-set reps + RIR) with dated history in the Recomp design language — with a light local cache for unsynced logs and `assembleDebug` building green.

## Outcome

- [ ] A Gradle Android project exists under `/android` and `cd android && ./gradlew assembleDebug` builds a debug APK from a clean checkout.
- [ ] The app reads `supabase.url` and `supabase.anonKey` from `android/local.properties` (gitignored) into `BuildConfig` at build time; no Supabase URL/key and no service-role key or other secret is committed anywhere under `/android`.
- [ ] A single `SupabaseClient` is constructed once (Auth + Postgrest + Realtime + Storage installed) with the anon key and reused app-wide; no Composable constructs a client or calls Supabase directly.
- [ ] Launching the app while signed out shows a login screen; entering valid email/password signs in via Supabase Auth, persists the session across app restarts, and routes to the plan view; invalid credentials show an inline error and no crash.
- [ ] A visible sign-out action clears the session and returns to the login screen.
- [ ] The plan view lists the signed-in user's plans and, for the selected plan, its days in order; each day shows its title, focus, and tag (push/pull/legs) color-coded per the Recomp palette; only data the RLS owner policy permits is returned (the app never sends or filters on a `user_id` itself).
- [ ] Each day lists its `plan_exercises` in `position` order, showing exercise name, the target (sets×reps) and RIR badge, and the cue line; consecutive exercises sharing a `superset_label` are grouped under a superset/circuit header with the violet (`--ss`) treatment (2 exercises = superset, 3+ = circuit), matching the prototype.
- [ ] For each exercise the app shows a "last time" summary (date, weight, reps) from the most recent `set_logs` row, a tap-to-disclose history list of prior dated entries (most-recent first), and per-set reps inputs plus a single kg input — the set count derived from the exercise target — mirroring the prototype's logging row.
- [ ] Logging a set writes a `set_logs` row for the signed-in user with `weight`, `reps[]`, `rir`, `date`/`logged_at`, and `source = 'app'`; the new entry appears in that exercise's history immediately and is readable back by an MCP client.
- [ ] Set entries typed while offline (or while a write fails) are held in a light local cache and flushed to Supabase when connectivity/auth returns, with no duplicate rows on retry; the cache holds only unsynced logs, not a full mirror of server data.
- [ ] UI state is exposed by ViewModels as `StateFlow` (loading / content / error); Composables observe state and emit events only — no business logic, no Supabase access, and no `runBlocking` inside Composables.
- [ ] The app renders in the Recomp dark theme: background `#0d0e11`, volt/lime accent `#c8f23a`, Archivo for display/body and Spline Sans Mono for labels/metrics, push `#c8f23a` / pull `#54c7ec` / legs `#ff8a5b` / superset `#a78bfa` color coding.
- [ ] `make verify` (Android static checks: ktlint/detekt) and `make build` (`assembleDebug`) pass on the branch.

## Scope

### In scope

- A new Gradle Android app under `/android`: Kotlin, Jetpack Compose, Material 3, MVVM, Coroutines/Flow, minimum SDK 26.
- `supabase-kt` integration: a single shared `SupabaseClient` (Auth, Postgrest, Realtime, Storage installed) configured from `BuildConfig` values sourced from `android/local.properties`.
- Email/password authentication against Supabase Auth: login screen, session persistence across restarts, sign-out, signed-in vs signed-out routing.
- A repository layer over `supabase-kt` exposing plans, days, exercises, and set logs as suspend functions / Flows; all Supabase access lives here.
- Read-only plan view matching the prototype: plan selector, day cards (number, title, focus, tag color), ordered exercises with target + RIR + cue, superset/circuit grouping.
- Set logging UI: per-set reps inputs + kg input + RIR, a "log" action that persists a `set_logs` row (`source = 'app'`), and dated history with the tap-to-disclose "last time" / full-history pattern.
- A light local cache (Room or DataStore) holding only unsynced log writes, with a flush-on-reconnect path and idempotent retry.
- The Recomp design system as Compose theme tokens (colors, the two type families, shape/spacing) reused across screens.
- `assembleDebug` building; ktlint/detekt static checks green; root `Makefile` `verify`/`build` targets covering Android (extending whatever Phase 1 established).

### Out of scope

- Realtime subscription and `source`-based agent-edit highlighting — that is the wow loop of [Phase 4 (live-sync)](docs/specs/spec-live-sync.md); this phase only writes `source = 'app'` and reads, it does not subscribe or highlight.
- In-app plan/day/exercise authoring or editing — the agent authors (vision Out); the app is read + log only.
- In-app API-key management and MCP-client onboarding — [Phase 5 (api-key-management)](docs/specs/spec-api-key-management.md).
- Visualization artifacts display — [Phase 6 (visualization-artifacts)](docs/specs/spec-visualization-artifacts.md).
- Exercise detail / images, rest & session timers, the guided session player, push notifications, dashboards, settings/units, plate-calc/1RM — Phases 7–13; each depends on this phase but is not built here.
- Sign-up / password-reset / email-confirmation flows beyond what a pre-provisioned test user needs — friends-and-family accounts are provisioned out of band (vision: no OAuth/compliance build-out).
- The MCP server, hosting, and the database schema/RLS themselves — owned by Phases 1 and 2; this phase consumes the schema from [Phase 1 (foundation)](docs/specs/spec-foundation.md).

## Constraints

Honors the constitution (no restatement): Android has no business logic in Composables, UI state via ViewModel + StateFlow, all Supabase access behind a repository layer; the client uses the anon key + a user JWT only and never holds the service-role key or any other secret; functions ≤ 50 lines, files ≤ 400 lines (guideline); Kotlin lints via ktlint/detekt with idiomatic Compose; DB columns are `snake_case` while Kotlin is `camelCase`; every mutation carries `source` (here always `'app'`) and `updated_at` is set DB-side; no CRDT library for the offline cache; the app talks only to Supabase (it never calls the rack-MCP in this phase). RLS is the isolation boundary — the app relies on the owner policy and never sends a `user_id`. New code lands under `/android` per the architecture's "where new code goes".

## Prior art

- [Native Android workout-logging UX](docs/prior-art.md#native-android-workout-logging-ux) — GymRoutines/Flexify are the reference for fast set/rep/weight logging and local-first responsiveness; the Recomp `artifact.html` prototype already encodes Rack's opinionated take (kg + per-set reps + RIR + history disclosure) this phase must match. Reference-only, not a code dependency.
- [Real-time sync & live edit highlighting](docs/prior-art.md#real-time-sync--live-edit-highlighting) — informs the boundary: adopt optimistic UI on the app's own writes here; defer WebSocket push + remote-edit highlighting to Phase 4; reject CRDT for the offline cache (server-authoritative + last-write-wins is enough).

## Human prerequisites

- [ ] Managed Supabase project URL and anon (public) key for `android/local.properties` (gitignored) — the only credentials the app needs (no service-role key in the client).
- [ ] A Supabase Auth email/password test user (created in the Supabase dashboard or via a confirmed sign-up) so QA can log in — email confirmation either disabled for the test user or the confirmation completed.
- [ ] At least one plan with days, `plan_exercises`, and (ideally) some prior `set_logs` seeded for the test user under RLS (authored via the Phase 2 rack-MCP or inserted in the dashboard), so the plan view and history disclosure render real data during QA.
- [ ] A machine that can build a debug APK: JDK 17 and the Android SDK (or Android Studio) available so `./gradlew assembleDebug` runs; a device or emulator (API 26+) to install and smoke-test the app.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Single Activity + Compose Navigation, no fragments | Idiomatic modern Compose (constitution: idiomatic Compose); minimal navigation surface for login → plan view; avoids fragment/XML overhead. | 2026-06-17 |
| Supabase credentials read from `android/local.properties` → `BuildConfig`, never committed | The Phase-1 `.env.example` already prescribes `supabase.url` / `supabase.anonKey` in `local.properties`; constitution forbids secrets in the client and only the anon key is client-safe. | 2026-06-17 |
| Manual constructor-injection / a small DI container (no Hilt/Koin) | Over-engineering avoidance (CLAUDE.md, constitution's minimal-dependency stance); the graph (client → repositories → viewmodels) is tiny. Hilt can be added later if the graph grows. | 2026-06-17 |
| Room for the light unsynced-log cache | Constitution names "a light local cache for unsynced logs"; Room gives a typed queue with idempotent upsert/delete and is the standard Android persistence library — proportionate vs hand-rolled file/DataStore for structured rows. | 2026-06-17 |
| Cache holds only pending (unsynced) writes, flushed then deleted; reads always come from Supabase | Matches the constitution's "light local cache for unsynced logs"; avoids a full offline mirror and the CRDT machinery the single-owner model rejects (prior-art real-time entry). | 2026-06-17 |
| Optimistic insert into the in-memory history on log, reconciled against the server row | Prior-art real-time entry: "optimistic UI on the app's own writes"; keeps the logging interaction instant like the prototype while the write completes. | 2026-06-17 |
| Idempotency via a client-generated row `id` (UUID) per pending log | Lets retries upsert the same row, so a flaky network never duplicates a logged set; no server-side dedupe logic needed. | 2026-06-17 |
| `set_logs.reps` modeled as an array (`reps[]`) of per-set integers, `weight` a single value per logged session, `rir` a single value | The architecture data model defines `set_logs` with `reps[]`; the prototype uses one kg + one RIR target per exercise with per-set reps — this is the native parity target from the vision. | 2026-06-17 |
| Set count for the reps inputs derived from the exercise target (e.g. "4 × 5–8" → 4), defaulting to 3 when unparseable | Mirrors the prototype's `setCount()` helper exactly so the input row matches the plan's prescribed sets. | 2026-06-17 |
| Superset/circuit grouping computed client-side by scanning consecutive equal `superset_label` values | The prototype groups consecutive same-label exercises; ≥3 in a run = circuit header, 2 = superset header — replicated in the repository/UI-mapping layer, not stored. | 2026-06-17 |
| Plan-tag color coding limited to push/pull/legs (+ superset violet); no separate "fullbody" color | The architecture lists tag as push/pull/legs and the prototype reuses those three accent colors even for fullbody days; keeping the palette to the four defined tokens avoids inventing colors. | 2026-06-17 |
| Login UI is email/password only; no sign-up/reset in-app this phase | Vision: friends-and-family, no OAuth/compliance build-out; accounts are provisioned out of band, so the app only needs sign-in + sign-out. | 2026-06-17 |
| App language/UI copy in English (code rule); the German prototype text is design reference only | Constitution: identifiers, logs, and code in English; the prototype's German copy demonstrates layout/UX, not final strings. | 2026-06-17 |
| `minSdk = 26`, `compileSdk`/`targetSdk` at the current stable, JDK 17 | API 26 covers the friends-and-family devices with full Compose/Coroutines support and modern Java APIs; standard current toolchain. | 2026-06-17 |
| Connectivity-change flush triggered by a `ConnectivityManager` callback plus a flush attempt on app foreground/login | Reliable, dependency-free reconnect signal; WorkManager deferred unless background flush proves necessary (avoid over-engineering). | 2026-06-17 |

## Tracking

Milestone: **Phase 3: Android: auth + plan view + logging** (depends on milestone Phase 1). Issues below are dependency-ordered; each carries a `Goal:`, an `Acceptance:` checklist, an optional `Depends on:` line, and a `Spec:` path pointing at `docs/specs/spec-android-app.md`. No step list lives here — the issues own the steps.

## Verification

Machine gates (from `docs/workflow.md`): `make verify` (Android ktlint/detekt static checks) green, and `make build` (`cd android && ./gradlew assembleDebug`) produces a debug APK. `Test` is `none yet`, so the following behavioral checks are run by a human at the milestone-QA gate on a device/emulator (API 26+) with the test user signed in:

- Clean-checkout build: with valid `android/local.properties`, `make build` produces an installable debug APK; `git status` shows no secret files would be committed (`local.properties` is gitignored).
- Login: signed-out launch shows the login screen; valid credentials route to the plan view; wrong password shows an inline error and no crash; killing and relaunching the app stays signed in; sign-out returns to login.
- Plan view: the signed-in user's plans load; selecting a plan shows its days in order with the correct tag color (push lime, pull blue, legs orange); a second test user (or no seeded plan) sees only their own / empty data, never another user's plan.
- Exercise rendering: exercises appear in `position` order with name, "sets × reps" target, RIR badge, and cue; a run of same-`superset_label` exercises renders under a violet superset header (2) or circuit header (3+) per the prototype.
- History disclosure: an exercise with prior `set_logs` shows the "last time" summary; tapping it discloses the dated history most-recent-first; an exercise with none shows the empty-state.
- Logging: entering kg + per-set reps and tapping log writes a row, the entry appears immediately in that exercise's history, and a separate MCP read (or a Supabase dashboard query) confirms the row with `source = 'app'` and the correct `weight`/`reps[]`/`rir`/date.
- Offline cache: enable airplane mode, log a set (it appears optimistically), restore connectivity — the row flushes to Supabase exactly once (no duplicate) and survives an app restart if not yet flushed.
- Design conformance: dark `#0d0e11` background, volt accent on primary actions, Archivo + Spline Sans Mono in use, matching the Recomp prototype visually.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| `supabase-kt` API/version drift breaks Auth/Postgrest/Realtime wiring at build time. | Pin a known-good `supabase-kt` BOM/version in the Gradle catalog; isolate all SDK calls in the repository layer so an upgrade touches one place; `assembleDebug` is the gate. |
| Phase 1's schema (`set_logs` columns, `reps[]` type, `source` default, RLS) differs from this spec's assumptions. | Read the merged Phase 1 migration in `/supabase/migrations` before implementing the repository; map Kotlin models to the actual `snake_case` columns; treat any mismatch as a `needs:planning` escalation rather than a workaround. |
| Postgres array (`reps[]`) (de)serialization through Postgrest/`supabase-kt` is fiddly. | Model `reps` as `List<Int>` with an explicit serializer matching the column type; cover it in the manual read-back QA check (log then read via MCP/dashboard). |
| Offline cache retries duplicate logged sets. | Client-generated UUID per pending log + upsert-on-id semantics make flush idempotent; QA explicitly tests the airplane-mode-then-reconnect path for a single resulting row. |
| Email-confirmation enabled on the Supabase project blocks the test user from signing in. | Listed as a human prerequisite (confirm or disable for the test user); login screen surfaces the auth error message so the cause is visible, not a silent failure. |
| Secret leakage into the repo (URL/anon key hard-coded). | `local.properties` is gitignored and the only source of the values via `BuildConfig`; a QA step asserts no Supabase values are tracked by git. |
| Scope creep into Realtime highlighting (Phase 4). | Explicitly out of scope; the repository exposes plain reads/writes with `source='app'`, leaving the subscription seam for Phase 4. |

## Decision log

All decisions below were auto-resolved under the autonomous planning mandate, 2026-06-17 — there is no human spec-acceptance gate this run; each is grounded in the vision, constitution, architecture, and the Recomp prototype.

- Single Activity + Compose Navigation; no fragments. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Supabase URL/anon key flow `android/local.properties` → `BuildConfig`; nothing secret committed. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Manual/small-container DI instead of Hilt or Koin, given the tiny object graph. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Room for the unsynced-log cache, holding only pending writes (flush-then-delete), reads always from Supabase. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Optimistic in-memory history insert on log, reconciled with the server row; client-generated UUID per log for idempotent retry. (auto-resolved under autonomous planning mandate, 2026-06-17)
- `set_logs` modeled as one weight + `reps[]` + one rir per logged session; reps input count derived from the parsed target (default 3). (auto-resolved under autonomous planning mandate, 2026-06-17)
- Superset/circuit grouping computed client-side from consecutive equal `superset_label` (2 = superset, 3+ = circuit). (auto-resolved under autonomous planning mandate, 2026-06-17)
- Tag color palette limited to push/pull/legs + superset violet; no separate fullbody color. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Email/password sign-in + sign-out only; no in-app sign-up/reset/confirmation this phase. (auto-resolved under autonomous planning mandate, 2026-06-17)
- English UI copy in code; the German prototype text is layout/UX reference only. (auto-resolved under autonomous planning mandate, 2026-06-17)
- `minSdk = 26`, current stable `compile`/`targetSdk`, JDK 17. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Reconnect flush via `ConnectivityManager` callback + a flush attempt on foreground/login; WorkManager deferred. (auto-resolved under autonomous planning mandate, 2026-06-17)