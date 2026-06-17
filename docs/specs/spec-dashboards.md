# Spec: Phase 11 — Overviews & dashboards

> Created: 2026-06-17

Read-only overview surfaces over the user's already-logged training data — a home dashboard (weekly volume per muscle/tag, training streak, recent sessions), a per-exercise progress view, and a calendar/history view — rendered in the Recomp design language with Vico charts.

## Outcome

- [ ] A Home/overview screen shows, for the current ISO week: total working sets and total volume (sum of weight × reps across all logged sets), broken down per muscle group and per plan-day tag (push/pull/legs), as a Vico chart using the Recomp tag/muscle color coding.
- [ ] The Home screen shows the current training streak — the count of consecutive prior weeks (including the current week) in which at least one session was logged — and the longest streak on record, as plain numeric stats.
- [ ] The Home screen shows a "recent sessions" list: the last logged days (a session = all `set_logs` sharing one logged date), each row showing the date, the day title/tag, and a session-volume summary, newest first, tappable to open that day in history.
- [ ] A per-exercise progress screen, reachable from an exercise, shows that exercise's logged history as a Vico line chart over time: estimated-1RM-free metrics — top-set weight and total per-session volume — across the logged dates, plus a tabular fallback of the same data points.
- [ ] A calendar/history screen shows logged sessions on a month calendar; days with logged sets are marked, the selected day expands to its logged exercises (weight + per-set reps + RIR), and the month is navigable backward/forward over the available history.
- [ ] All three screens read exclusively through the repository layer (no Supabase calls in Composables), expose state via a ViewModel + `StateFlow`, render empty/loading states, and contain no business logic in Composables.
- [ ] Empty states are handled: with zero logged sets each screen shows a Recomp-styled empty state rather than a blank or crashing chart.
- [ ] `make verify` and `make build` pass; the screens render in the Recomp dark theme with Archivo + Spline Sans Mono and the volt/push/pull/legs/superset palette.

## Scope

### In scope

- A new Android `dashboards` feature: three screens (Home overview, per-exercise progress, calendar/history) plus their ViewModels and aggregation logic.
- Read-only aggregation queries over existing `set_logs` joined to `plan_exercises` → `exercises` and `plan_days` (for tag and muscle attribution), added to the repository layer.
- Adoption of Vico (`compose-m3` module, Apache-2.0) as the charting dependency and a thin Recomp-themed chart styling wrapper.
- Navigation entry points: Home as an overview destination, exercise → progress, and a calendar/history destination, wired into the existing app navigation.
- Aggregation/streak/volume computation in Kotlin (in the repository/ViewModel layer), with unit-testable pure functions.

### Out of scope

- Any new database table, column, migration, or RLS change — this phase only reads data that Phase 3 already persists (why: dashboards are a pure read-side consumption surface; the constitution forbids tables without RLS, but none are needed here).
- Any rack-MCP tool, MCP read tool, or backend change (why: aggregation is client-side over the user's own RLS-scoped rows; no agent surface is in this phase's intent).
- 1RM estimation and plate math (why: owned by [spec-plate-calc-1rm](spec-plate-calc-1rm.md), Phase 13).
- Exercise execution detail / images (why: owned by [spec-exercise-detail](spec-exercise-detail.md), Phase 7).
- kg/lb unit switching and theme/profile settings (why: owned by [spec-settings](spec-settings.md), Phase 12 — this phase displays in the unit Phase 3 already stores, kg).
- Live-highlight of agent edits on these screens (why: the highlight mechanism is [spec-live-sync](spec-live-sync.md), Phase 4; dashboards re-query on resume and need no realtime subscription of their own).
- Agent-authored visualization artifacts (why: owned by [spec-visualization-artifacts](spec-visualization-artifacts.md), Phase 6).

## Constraints

Per the constitution: Android has no business logic in Composables, UI state via ViewModel + `StateFlow`, and all Supabase access behind the repository layer; Kotlin is idiomatic Compose under ktlint/detekt; functions ≤ 50 lines and files ≤ 400 lines (guideline). The Android client uses only the anon key + the user JWT and reads only its own rows under existing RLS — it never holds the service-role key. No new user-owned table is introduced; if one ever were, it would ship its `user_id = auth.uid()` RLS policy in the same migration — not applicable this phase. Vico is added under its Apache-2.0 license (compatible; not AGPL). Conventional Commits and English identifiers/comments/logs apply.

## Prior art

- [Charts & dashboards — Jetpack Compose (Phase 11)](../prior-art.md#charts--dashboards--jetpack-compose-phase-11) — directly normative: adopt Vico `compose-m3` for volume/progress/streak charts (Compose-native, Material 3, Apache-2.0); avoid MPAndroidChart unless a chart type Vico lacks forces an `AndroidView` wrapper.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — relevant for the history/recent-sessions disclosure and per-set reps + RIR presentation, matching the Recomp prototype's history pattern.
- [1RM estimation (Phase 13)](../prior-art.md#1rm-estimation-phase-13) — referenced only to scope it OUT: the progress chart plots logged top-set weight and volume, not an estimated 1RM (that is Phase 13).

## Human prerequisites

- none — this phase reads data the Phase 3 Supabase project already holds; it needs no new secret, provider, dataset, or signing material. (Vico installs via Gradle, an autonomous dependency add under the loopkit autonomy grant.)

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Charting library is Vico `compose-m3`. | Constraint-determined by prior-art + constitution: Compose-native, Material 3, Apache-2.0, actively maintained; MPAndroidChart avoided. | 2026-06-17 |
| Phase is read-only; no new table, column, migration, MCP tool, or RLS change. | Constraint-determined: all required data (`set_logs`, `plan_exercises`, `exercises`, `plan_days`) exists from Phase 3 and is RLS-scoped; dashboards aggregate client-side. | 2026-06-17 |
| A "session" is defined as all `set_logs` for the user sharing one logged calendar date. | The Phase 3 / Recomp model logs per-day in one action stamping a date; there is no session_id, so the logged date is the natural session key. | 2026-06-17 |
| "Volume" = Σ(weight × reps) over a set's `reps[]` array; "working sets" = count of logged set entries. | Standard hypertrophy volume metric; matches the kg + per-set reps the prototype records. Bodyweight/time-only sets (weight null) contribute set count but zero weight-volume. | 2026-06-17 |
| Muscle attribution comes from the catalog `exercises.muscles`; tag attribution from `plan_days.tag`. A set's volume is credited fully to each listed muscle (no fractional split). | The seed exposes `muscles` and `tag` already; full-credit-per-muscle is the simplest defensible attribution and avoids unfounded weighting. | 2026-06-17 |
| Training streak is counted in ISO weeks (consecutive weeks with ≥1 logged session, including the current week), not consecutive days. | Weekly cadence matches the prototype (3–4×/week); a day-streak would misrepresent a normal training split as a broken streak. | 2026-06-17 |
| The week boundary is the ISO-8601 week (Monday start) in the device's local timezone. | Matches European convention (the prototype is German) and `set_logs.date` semantics; deterministic for streak/weekly-volume math. | 2026-06-17 |
| Per-exercise progress plots two series — top-set weight and per-session total volume — over logged dates, with a table fallback. | Two complementary progress signals without introducing 1RM (Phase 13); the table satisfies acceptance when a chart is visually ambiguous. | 2026-06-17 |
| Aggregation runs in Kotlin (repository/ViewModel) over rows fetched via Postgrest, not in SQL views/RPC. | Keeps the phase free of any migration/backend change; data volume (a friends-and-family circle) is small enough to aggregate client-side; logic stays unit-testable as pure functions. | 2026-06-17 |
| Dashboards refresh on screen resume (re-query), with no dedicated Realtime subscription. | Live highlighting is Phase 4's concern; an overview that re-reads on resume is sufficient and avoids duplicating the realtime layer. | 2026-06-17 |
| Values are displayed in kilograms only, with no unit toggle. | Phase 3 stores kg; the kg/lb toggle is Phase 12. Dashboards render the stored unit to avoid pre-empting that phase. | 2026-06-17 |
| Empty/loading/error are first-class states on every screen; an empty dataset shows a Recomp empty state, never a crashing or blank chart. | Pre-mortem: zero-data is the first state a fresh install hits; Vico must not be handed an empty series unguarded. | 2026-06-17 |
| Recent-sessions list length is capped at the last 10 sessions on Home; full history lives in the calendar/history screen. | Keeps the overview scannable; the calendar is the complete record. | 2026-06-17 |

## Tracking

- Milestone: **Phase 11: Overviews & dashboards** (`Depends on milestone: #3`).
- Issues: created from the steps below, each carrying `Goal:`, `Acceptance:`, optional `Depends on: #N`, and `Spec: docs/specs/spec-dashboards.md`. The step list lives only in the issues; this spec lists outcomes, not steps.

## Verification

- `make verify` is green (lint + typecheck + tests, incl. ktlint/detekt for `/android`).
- `make build` is green (`./gradlew assembleDebug` builds with the Vico dependency resolved).
- Test is `none yet`, so the following are run by human QA at the milestone gate, plus any added Kotlin unit tests for the pure aggregation functions:
  - With several logged days across the current and prior weeks: the Home weekly-volume chart shows per-muscle and per-tag bars whose totals equal a hand-computed Σ(weight × reps); switching no inputs, the numbers are stable across screen resume.
  - The streak stat equals the number of consecutive ISO weeks (ending this week) that contain ≥1 session; logging a session in a week that was previously empty extends or resets the streak as expected; the longest-streak stat is ≥ the current streak.
  - The recent-sessions list shows the last (≤10) logged sessions newest-first with correct date, day title/tag, and a volume summary; tapping a row opens that date in the calendar/history view.
  - The per-exercise progress screen, opened for an exercise with ≥2 logged dates, draws an upward/var line for top-set weight and for session volume; the table fallback lists the same (date, top-set weight, session volume) rows.
  - The calendar marks exactly the dates that have logged sets; selecting a marked day expands its logged exercises with weight + per-set reps + RIR; month navigation moves across the full range of logged history and shows unmarked months as empty.
  - Fresh-install / zero-data path: each of the three screens shows its Recomp-styled empty state and no chart crash.
  - Visual check: dark theme, Archivo + Spline Sans Mono, volt `#c8f23a`, and push `#c8f23a` / pull `#54c7ec` / legs `#ff8a5b` / superset `#a78bfa` color coding match the prototype.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Vico's API or empty-series handling differs from expectation and crashes on no data. | Guard every chart behind a non-empty-data check; render the empty state otherwise; pin the Vico version and confirm in `make build`. |
| Client-side aggregation over `set_logs` is slow or memory-heavy as history grows. | Scope is a friends-and-family circle (small data); fetch only the date range a screen needs (current screen's month / exercise) and keep aggregation as O(n) pure functions; revisit a SQL view only if measured slow. |
| Muscle/tag attribution is ambiguous when an exercise lists several muscles or a logged set lacks a plan-day tag. | Decision: full credit per listed muscle; sets with no `muscles` fall into an "Other/unspecified" bucket; sets whose plan-day tag is null are bucketed as "untagged" rather than dropped. |
| "Session" keyed on logged date is wrong if a user logs two distinct workouts in one day. | Accepted limitation for this circle; the date is the available key (no session_id in the schema). Documented in the recent-sessions behavior; a session_id is out of scope. |
| Streak math drifts across timezone/week-boundary edge cases. | Fix the boundary to local-timezone ISO weeks (Monday start), unit-test the streak function with fixtures spanning year/week boundaries. |
| Bodyweight or time-based sets (no weight) skew volume to zero. | They count toward working-set count but contribute zero weight-volume by design; the metric labels make this explicit ("volume" = weighted). |

## Decision log

All decisions below were auto-resolved under the autonomous planning mandate, 2026-06-17 (no human spec-acceptance gate this run); each is grounded in the vision, constitution, prior-art, roadmap, and the Recomp prototype.

- Charting library = Vico `compose-m3` (Apache-2.0); MPAndroidChart avoided. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Phase is strictly read-only: no new table/column/migration/MCP tool/RLS change; all data sourced from Phase 3's RLS-scoped tables. (auto-resolved under autonomous planning mandate, 2026-06-17)
- "Session" = all `set_logs` sharing one logged calendar date (no session_id in schema). (auto-resolved under autonomous planning mandate, 2026-06-17)
- "Volume" = Σ(weight × reps); "working sets" = count of logged set entries; weight-null sets contribute set count, zero volume. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Muscle attribution from `exercises.muscles` (full credit per muscle), tag attribution from `plan_days.tag`; null muscles → "Other", null tag → "untagged". (auto-resolved under autonomous planning mandate, 2026-06-17)
- Streak counted in consecutive ISO weeks (Monday start, local timezone) including the current week; longest streak also reported. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Per-exercise progress plots top-set weight and per-session volume over time with a table fallback; no 1RM (Phase 13). (auto-resolved under autonomous planning mandate, 2026-06-17)
- Aggregation in Kotlin (repository/ViewModel) over Postgrest reads, not SQL views/RPC. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Dashboards re-query on resume; no dedicated Realtime subscription (Phase 4 owns highlighting). (auto-resolved under autonomous planning mandate, 2026-06-17)
- Values shown in kg only; no unit toggle (Phase 12 owns kg/lb). (auto-resolved under autonomous planning mandate, 2026-06-17)
- Empty/loading/error are first-class states; empty dataset renders a Recomp empty state, never a blank/crashing chart. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Recent-sessions list capped at last 10 on Home; full record in the calendar/history screen. (auto-resolved under autonomous planning mandate, 2026-06-17)