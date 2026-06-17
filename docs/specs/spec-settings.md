# Spec: Phase 12 — Settings & units

> Created: 2026-06-17

A per-user, Supabase-persisted settings store that drives kg/lb display and entry conversion, configurable rest-timer defaults, theme, and a minimal profile — surfaced through a Recomp-styled Settings screen backed by a repository.

## Outcome

- [ ] A `user_settings` table exists, one row per user, with RLS (`user_id = auth.uid()`) shipped in the same migration; every mutation sets `source` and `updated_at`.
- [ ] A new user has working settings without any manual setup: the row is created on first access with defaults (unit `kg`, theme `dark`, the four rest-timer defaults from the Recomp notes, empty display name).
- [ ] A Settings screen is reachable from the app and renders in the Recomp design language (dark theme, volt accent, Archivo + Spline Sans Mono).
- [ ] Switching the weight unit to `lb` makes every weight in the app (logging input, last-time history, exercise loads) display in pounds; switching back to `kg` restores kilograms — with no change to any stored value.
- [ ] Entering a weight while the unit is `lb` persists the canonical kilogram value (the entered pounds converted to kg), and reading it back in `lb` shows the same pounds the user typed, within the chosen rounding increment.
- [ ] Rest-timer defaults (compound, isolation, superset, circuit) are editable in Settings and persisted; their stored values are the single source the rest-timer feature reads (the timer itself is out of scope here).
- [ ] The theme setting persists and is applied on app launch; the profile display name is editable and persisted, and the account email is shown read-only from Supabase Auth.
- [ ] Settings survive app restart and are read from Supabase (not only local cache), so a fresh install for the same account restores them.
- [ ] All Supabase access for settings is behind a repository; Composables hold no business logic and read state via a ViewModel + StateFlow.
- [ ] `make verify` and `make build` are green.

## Scope

### In scope

- A `user_settings` table + RLS policy + the `source`/`updated_at` trigger contract, in one migration under `/supabase/migrations`.
- A `SettingsRepository` over `supabase-kt` and a `SettingsViewModel` exposing settings as `StateFlow`.
- A Recomp-styled Settings screen with: weight unit (kg/lb), theme, rest-timer defaults (four values), and profile (editable display name, read-only email).
- Unit conversion utilities (kg ↔ lb) and a display/entry layer so existing weight UI honours the selected unit while storage stays canonical kg.
- Default-row provisioning on first access (upsert-on-read).

### Out of scope

- The rest timer itself (start/foreground service/notification) — Phase 8 (`docs/specs/spec-timers.md`) owns it; this phase only stores its defaults.
- Any MCP tool for settings — no agent-authored settings this phase; settings are app-authored only (the table still carries `source`/`updated_at` for uniformity).
- Migrating already-stored weights or `set_logs` columns — stored weights are and remain canonical kg (introduced in `docs/specs/spec-android-app.md` / Phase 3); only display/entry converts.
- Additional profile fields (avatar, bodyweight, locale, notifications) — not needed for this phase's intent.
- Theme variants beyond what the Recomp prototype defines — the prototype is dark-only; light theme is not designed and is not built here.

## Constraints

Binds to `docs/constitution.md`: every user-owned table ships its RLS policy (`user_id = auth.uid()`) in the same migration and every mutation sets `source` + `updated_at`; Android keeps all Supabase access behind a repository with no business logic in Composables and UI state via ViewModel + StateFlow; Kotlin is idiomatic Compose under ktlint/detekt; the Android client uses only the anon key + user JWT (no service-role, no secrets); functions ≤ 50 lines and files ≤ 400 lines (guideline). No CRDT, no service-role writes, no `user_id` accepted from any caller.

## Prior art

- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — the Recomp prototype already fixes kg + per-set reps + RIR as the logging contract; this phase's unit switch must keep that interaction intact, only changing the displayed unit.
- [Rest & session timers — Android, backgrounded (Phase 8)](../prior-art.md#rest--session-timers--android-backgrounded-phase-8) — defines the per-type rest defaults (compound 2–3 min, isolation 60–90 s, superset 60–90 s, circuit 30–45 s) this phase makes user-editable; the timer behaviour itself is Phase 8.

## Human prerequisites

- none (the managed Supabase project, keys, and Auth are provisioned in Phase 1 / Phase 3; this phase adds a table and a screen to that existing stack).

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Persist settings in a `user_settings` Supabase table (one row per user, PK = `user_id`), not device-local prefs | Vision requires settings "persisted per user" and read-back across a fresh install; server-side per-user storage with RLS is the constitution's data model, and keeps the option open for the agent to read context later | 2026-06-17 |
| Store weights canonically in kilograms; `lb` is display + entry conversion only | The Recomp prototype logs kg; `set_logs.weight` is kg (Phase 3). Converting only at the UI boundary avoids a data migration and keeps one canonical unit in the DB | 2026-06-17 |
| Conversion factor `1 kg = 2.2046226218 lb`; display rounds to 0.5 lb and kg entry rounds to 0.25 kg; round-trip tolerates one rounding step | A documented constant and fixed increments make the round-trip test deterministic and match plate-friendly gym increments | 2026-06-17 |
| Provision the settings row lazily via upsert-on-read with defaults, not via an auth trigger | Keeps the change inside this phase's repository, avoids touching Phase 3's auth flow, and guarantees a new user always has a usable row | 2026-06-17 |
| Defaults: unit `kg`, theme `dark`, rest defaults compound 150 s / isolation 90 s / superset 90 s / circuit 45 s, empty display name | kg and dark match the Recomp prototype; the rest values are the midpoints/upper bounds of the prior-art ranges | 2026-06-17 |
| Theme is dark-only this phase; the setting is a `dark`/`system` choice that resolves to the Recomp dark palette | The prototype defines only the dark palette; a light theme is undesigned, so building one would be inventing visual language out of scope | 2026-06-17 |
| Settings rows carry `source = 'app'` and `updated_at`; no MCP settings tool | The constitution's mutation rule is uniform across user tables; no agent path to settings exists this phase, so `source` is always `'app'` | 2026-06-17 |
| Profile = editable display name + read-only Auth email; no extra fields | Minimal surface that satisfies "profile" in the phase intent without scope creep | 2026-06-17 |
| Unit conversion lives in a shared Kotlin utility consumed by the repository/ViewModel layer, not in Composables | Keeps business logic out of the UI per the constitution and makes conversion unit-testable independent of Compose | 2026-06-17 |

## Tracking

- Milestone: **Phase 12: Settings & units** (`Depends on milestone: #<phase-3-milestone>`).
- Issues: one GitHub issue per implementable step below, each carrying `Goal:`, `Acceptance:`, optional `Depends on: #N`, and `Spec: docs/specs/spec-settings.md`. No step list is kept in this spec — the issues are the steps.

## Verification

Machine gates (from `docs/workflow.md`): `make verify` (lint + typecheck + Android static checks) green per iteration; `make build` (`assembleDebug`) green before the PR. `Test` is `none yet`, so the behavioral checks below are run at the human milestone-QA gate.

Behavioral checks (human QA):

1. Sign in as a fresh account, open Settings: a settings row is present with defaults (kg, dark, compound 150 / isolation 90 / superset 90 / circuit 45, empty name) and no error.
2. Switch unit to `lb`: logging inputs, the "last time" history line, and the exercise target loads all read in pounds; switch back to `kg` and the same screens read in kilograms, with no logged value altered.
3. With unit `lb`, log a set at e.g. 135 lb; reopen the entry and confirm it still reads 135 lb (within the rounding increment); inspect the DB and confirm the stored `weight` is the kilogram equivalent (~61.25 kg).
4. Edit each rest-timer default, leave Settings, return: the edited values persist; confirm they are the values the rest-timer feature would read (assert against the stored row).
5. Change theme, force-stop the app, relaunch: the theme is applied on launch from the persisted setting.
6. Edit the display name and confirm it persists across restart; confirm the email field shows the Auth account email and is not editable.
7. Reinstall (or clear local cache) and sign in to the same account: unit, theme, rest defaults, and name are restored from Supabase.
8. Cross-user check: confirm a second account cannot read or write the first account's settings row (RLS denies it).

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Lossy kg↔lb round-trip frustrates the user (typed 135 lb reads back 134.9) | Fixed conversion constant + defined rounding increments (0.5 lb / 0.25 kg) with a round-trip test asserting equality within one step |
| Unit toggle misses a weight surface (history, target load) and shows mixed units | Route every weight render through the shared conversion utility; QA check 2 enumerates each surface |
| Stored kg weights accidentally rewritten on unit switch | Switching unit changes only display state, never issues a write; QA check 2 verifies no logged value changes |
| Lazy upsert-on-read creates a race / duplicate row on first login | PK on `user_id` makes the row idempotent; use upsert (insert-or-ignore-then-select) so concurrent first reads converge |
| Phase 8 reads rest defaults from a different place than this phase writes | This spec names `user_settings` as the single source; Phase 8's spec references it rather than re-storing defaults |
| Theme setting implies a light theme that does not exist | Theme choice resolves only to the Recomp dark palette this phase; documented as dark-only in scope |

## Decision log

- Persist settings in a per-user `user_settings` Supabase table (PK `user_id`, RLS `user_id = auth.uid()`) rather than device prefs (auto-resolved under autonomous planning mandate, 2026-06-17).
- Store weights canonically in kg; treat `lb` as a display/entry conversion with no data migration (auto-resolved under autonomous planning mandate, 2026-06-17).
- Fix the conversion constant at `2.2046226218` and round to 0.5 lb / 0.25 kg with a one-step round-trip tolerance (auto-resolved under autonomous planning mandate, 2026-06-17).
- Provision the settings row lazily via upsert-on-read with defaults instead of an auth trigger (auto-resolved under autonomous planning mandate, 2026-06-17).
- Default to kg, dark theme, rest defaults compound 150 / isolation 90 / superset 90 / circuit 45 s, empty display name (auto-resolved under autonomous planning mandate, 2026-06-17).
- Ship theme as dark-only (a `dark`/`system` choice resolving to the Recomp dark palette); do not design a light theme (auto-resolved under autonomous planning mandate, 2026-06-17).
- Stamp settings mutations `source = 'app'` + `updated_at`; build no MCP settings tool this phase (auto-resolved under autonomous planning mandate, 2026-06-17).
- Scope profile to an editable display name plus a read-only Auth email (auto-resolved under autonomous planning mandate, 2026-06-17).
- Place conversion logic in a shared, unit-testable Kotlin utility consumed by the repository/ViewModel, never in Composables (auto-resolved under autonomous planning mandate, 2026-06-17).