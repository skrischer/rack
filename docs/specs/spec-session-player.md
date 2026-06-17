# Spec: Phase 9 — Guided session player

> Created: 2026-06-17

A full-screen active workout mode that steps through one plan day's exercises and sets in focus, ticks each set, auto-prompts the Phase 8 rest timer between sets, gives superset/circuit rotation guidance inside the logging flow, and ends with a session summary — all client-side Android UI over existing `set_logs`, no new tables.

## Outcome

- [ ] From a plan day, the user can start a guided session that opens a full-screen player and steps through that day's exercises in `plan_exercises.position` order, one exercise/set in focus at a time.
- [ ] The player surfaces, for the focused exercise, the same data the static day view shows: name, target (`sets×reps`), RIR, cue, and the last logged set as reference ("last time"), reusing the Phase 3/Phase 7 display data.
- [ ] Each target set is a tickable step; ticking a set records per-set reps for that set into the session's working state, alongside one per-exercise weight (kg) and one per-exercise RIR, pre-filled from the target / last-logged values and editable before ticking.
- [ ] Weight (kg) and RIR are single per-exercise values (not per-set): the player edits one kg field and one RIR field for the focused exercise that apply to every ticked set of that exercise, because `set_logs` stores scalar `weight` and scalar `rir` with a per-set `reps[]` (one row per `plan_exercise_id`, per architecture.md). Per-set reps vary; per-set kg/RIR do not.
- [ ] Ticking a set auto-prompts the Phase 8 rest timer with the default duration for that exercise's type (compound 2–3 min, isolation 60–90 s, superset 60–90 s, circuit 30–45 s), reusing the Phase 8 timer component — the player does not reimplement timer logic.
- [ ] The exercise type that selects the rest default is classified by the Phase 9 `classifyExerciseType(exercise)` rule defined in *Prior decisions* (catalog `category`/`equipment` → compound | isolation), with the superset/circuit group context overriding to the group default; the player passes the resolved type to the Phase 8 timer, which owns only the type → duration map.
- [ ] For a superset/circuit group (consecutive `plan_exercises` sharing a `superset_label`), the player rotates between the group's exercises set-by-set (set 1 of each, then set 2 of each, …) and shows an explicit rotation cue ("Next: <exercise>"), using the superset (60–90 s) / circuit (30–45 s, 3+ stations) rest default after each rotation step.
- [ ] Uneven set counts within a group rotate by set index: when a group member has no remaining set, it is skipped in that and later rounds while the others continue rotating.
- [ ] Non-superset exercises are stepped straight through (all sets of one exercise, then the next), with the compound/isolation rest default.
- [ ] On finishing the last step the player shows a session summary: per-exercise sets done vs. target, total volume (Σ weight × reps over ticked sets, using the per-exercise weight), session duration (from the Phase 8 session-duration timer), and a confirm-and-save action.
- [ ] Confirming the summary writes every logged exercise to `set_logs` via the repository with `source='app'` — one row per logged `plan_exercise_id` carrying the scalar `weight`, scalar `rir`, and the `reps[]` of its ticked sets — exactly as the existing Phase 3 day-logging path does; the writes are immediately visible via the MCP read-back.
- [ ] The session can be abandoned (back / cancel) with a confirmation; an abandoned session writes nothing to `set_logs`.
- [ ] In-progress session state survives process death / backgrounding via the existing local cache, so a returning user resumes the same step rather than losing ticked sets (logging is offline-resilient per the constitution).
- [ ] No business logic lives in Composables: session stepping, rotation, classification, and summary aggregation run in a `SessionPlayerViewModel` exposing `StateFlow`; all `set_logs` access goes through the existing repository.

## Scope

### In scope

- A `SessionPlayer` Android feature (screen + `SessionPlayerViewModel`) launched from an existing plan-day view, driving one day's `plan_exercises` as an ordered step sequence.
- Step-sequence + rotation model that orders sets within an exercise and rotates within a `superset_label` group (2 exercises = superset, 3+ = circuit), skipping exhausted members on uneven counts.
- Per-set tick interaction with editable per-set reps plus one per-exercise kg and one per-exercise RIR, pre-filled from target and last-logged values, in the Recomp design language.
- The Phase 9 `classifyExerciseType` rule mapping catalog `category`/`equipment` to compound | isolation for the base-case rest default.
- Integration with the Phase 8 rest timer (auto-prompt on tick, type-based default duration) and the Phase 8 session-duration timer (drives summary duration).
- Session summary screen and a confirm action that persists logged exercises through the existing repository to `set_logs` (`source='app'`).
- Abandon flow (confirmation, no write) and in-progress resume via the existing local cache.

### Out of scope

- New database tables, columns, migrations, or RLS — the player only writes to the existing `set_logs` table on confirm (no schema change → no migration in this phase). *Why: the session is ephemeral client UI; persisting in-progress sessions server-side is unneeded for a single owner and would add a table with no agent or cross-device requirement.*
- Per-set kg or per-set RIR persistence. *Why: `set_logs` stores scalar `weight` and scalar `rir` (architecture.md); the phase forbids schema change, so kg/RIR are per-exercise — see the persistence decision below.*
- Any MCP tool or rack-MCP change. *Why: the player is consumption/logging UI; the agent authors plans, it does not drive the live session.*
- Editing the plan, target sets/reps, or `superset_label` from the player. *Why: plan authoring is the agent's job (vision: in-app focus is consumption + logging).*
- Rest-timer engine, foreground-service, completion vibrate/sound, the session-duration timer, and the type → duration map themselves. *Why: delivered by Phase 8; this phase only consumes them and supplies the resolved exercise type.*
- Exercise instructions/images/detail rendering. *Why: delivered by Phase 7; the player links to / reuses it, it does not rebuild it.*
- Push notifications for rest-done while backgrounded. *Why: Phase 10.*
- Plate calculator / 1RM suggestions in the player. *Why: Phase 13.*

## Constraints

Bound by `docs/constitution.md`: Android has no business logic in Composables, UI state via ViewModel + `StateFlow`, and all Supabase access behind the repository layer; every `set_logs` mutation carries `source` ('app') and `updated_at`; the client uses the anon key + user JWT only (no service-role, no secrets); functions ≤ 50 lines and files ≤ 400 lines (guideline); Kotlin ktlint/detekt clean; English identifiers/comments/commits. No new user-data table is introduced, so the "RLS in the same migration" rule is satisfied vacuously (existing `set_logs` RLS already applies to the writes). No CRDT, no `user_id` plumbing on the client (RLS scopes by `auth.uid()`). The `set_logs` row shape (`weight` scalar, `rir` scalar, `reps[]` array, one row per `plan_exercise_id`) from architecture.md is the binding persistence contract this phase writes to unchanged.

## Prior art

- [Guided workout session — active player UX (Phase 9)](../prior-art.md#guided-workout-session--active-player-ux-phase-9) — directly defines this phase: step-through-the-day flow, current exercise/set in focus, tick sets, rest auto-prompts, superset/circuit rotation guidance, session summary on finish; ADOPT that loop, AVOID feature bloat (social, marketplace).
- [Rest & session timers — Android, backgrounded (Phase 8)](../prior-art.md#rest--session-timers--android-backgrounded-phase-8) — the rest-timer defaults by exercise type and the session-duration timer this player auto-prompts and consumes; the player must reuse, not reimplement, and supplies only the resolved exercise type.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — fast set/rep/weight logging interaction and local-first responsiveness the tick flow inherits; the Recomp `artifact.html` already encodes Rack's opinionated kg + per-set reps + RIR take (one weight, a per-set reps array).

## Human prerequisites

- none. (This phase is client-side Android over the existing Supabase `set_logs` schema and Phases 7/8 components; it needs no new managed-Supabase change, no VPS/DNS/Caddy work, no FCM, no wger import, and no Play signing.)

## Prior decisions

| Decision | Rationale | Date |
| -------- | --------- | ---- |
| Session is ephemeral client-side UI state; no in-progress session table | Single owner, no cross-device/agent need; persisting mid-session server-side would add an unused table — violates minimal-solution / no-over-engineering. Crash resilience comes from the existing local cache. | 2026-06-17 |
| **kg and RIR are per-exercise single values, not per-set; only reps vary per set.** The player exposes one editable kg field and one editable RIR field per focused exercise, applied to all of its ticked sets; per-set tick edits only reps. | `set_logs` stores scalar `weight`, scalar `rir`, and array `reps[]` with one row per `plan_exercise_id` (architecture.md), and the phase forbids any schema change. This matches the Phase 3 day-logging model and the prototype's `rec.w \|\| rec.r.some(x)` shape (one weight, a reps array). No per-set kg/RIR collapse rule is needed because per-set kg/RIR is never captured. | 2026-06-17 |
| Persist only on summary confirm, writing to the existing `set_logs` via the Phase 3 repository path (`source='app'`): one row per logged `plan_exercise_id` with the exercise's scalar `weight`, scalar `rir`, and `reps[]` of its ticked sets | Reuses the proven logging mutation and its RLS/`source`/`updated_at` guarantees; one row per exercise with `reps[]` matches the day model and the documented `set_logs` shape; read-back via MCP is automatic. | 2026-06-17 |
| Superset vs. circuit threshold = group size: 2 exercises → superset (60–90 s default), 3+ → circuit (30–45 s default) | Matches the `artifact.html` prototype's own rule (`cnt>=3 ? Zirkel : Supersatz`) and the Phase 8 / Recomp rest defaults. | 2026-06-17 |
| Rotation is set-synchronized round-robin within a `superset_label` group (set 1 of all, then set 2 of all, …) | Matches the prototype's "abwechselnd" guidance and antagonist/independent superset model; rest default applied after each rotation step. | 2026-06-17 |
| Rest-timer default duration is selected by exercise type and auto-prompted on tick by reusing the Phase 8 timer component; **Phase 9 owns the `category`/`equipment` → type classification, Phase 8 owns only type → duration** | Constitution + Phase 8 own the timer engine and the type → duration map; this phase only classifies the exercise and prompts, keeping the timer single-sourced and the classification in one place. | 2026-06-17 |
| **`classifyExerciseType(exercise)` (Phase 9) maps the catalog `category`/`equipment` already on the focused exercise to `compound` or `isolation` as follows.** Default to `isolation`; classify as `compound` when the wger `category` is one of `Legs`, `Back`, or `Chest`, **except** downgrade to `isolation` when the exercise's `equipment` is a single-joint accessory implement — `Cable` or `Machine (kettlebell-style isolation)` — *and* the category is not `Legs`. Categories `Arms`, `Shoulders`, `Abs`, and `Calves` are always `isolation`. `compound` selects the 2–3 min default; `isolation` selects the 60–90 s default. The superset/circuit group context, when present, overrides this to the group default. | The wger catalog has no compound/isolation field; its `category` (muscle-group) plus `equipment` are the only loaded signals (architecture.md:17), so a deterministic rule over those fields gives the base-case default with no new field and no extra query. Big multi-joint muscle groups (legs/back/chest) trained with barbell/dumbbell/bodyweight rest long; cable/machine accessory work and small muscle groups rest short. Group context wins because a compound inside a superset still rests at the superset cadence (prototype: "gleiche Last, weniger Gesamtzeit"). | 2026-06-17 |
| kg / reps / RIR are pre-filled (target + last-logged) and editable before ticking; a set with no entered reps and no entered exercise weight is treated as skipped (not logged), mirroring the prototype's `rec.w \|\| rec.r.some(x)` guard | Fast logging per the prior-art UX; avoids writing empty sets; keeps the tick a one-tap action in the common case. | 2026-06-17 |
| Abandon (back/cancel) requires a confirmation and discards all ticked sets without writing | The only persistence point is the summary confirm; an accidental back must not partially log. | 2026-06-17 |
| Session duration shown in the summary is read from the Phase 8 session-duration timer, started when the player opens | Single source for elapsed time; no parallel clock in the player. | 2026-06-17 |
| No new MCP tool, migration, or table; the player is purely an Android feature | Keeps the phase tightly bounded; honors "where new code goes" (a new `/android` feature, data via repository). | 2026-06-17 |

## Tracking

- Milestone: **Phase 9: Guided session player** (`Depends on milestone: #7`, `#8`).
- Issues: the implementable steps live as GitHub issues under this milestone, each with a `Goal:`, an `Acceptance:` checklist, an optional `Depends on:` line, and `Spec: docs/specs/spec-session-player.md`. This spec lists no steps — the issues own them; the issues restate no design — this spec owns it.

## Verification

Per `docs/workflow.md`: `make verify` (MCP lint+typecheck+test plus Android ktlint/detekt) green, and `make build` (`assembleDebug`) green before the app-affecting PR. Test is `none yet`, so the following behavioral checks are run at the milestone-QA gate by a human:

- Start a session from a plan day → a full-screen player opens on the first exercise/first set, showing name, target, RIR, cue, and last-logged reference.
- Tick a non-superset exercise's set → the rest timer auto-prompts with the compound/isolation default chosen by `classifyExerciseType` (e.g. a `Legs`/barbell exercise → 2–3 min; an `Arms`/cable exercise → 60–90 s); advancing reaches the next set, then the next exercise in `position` order.
- Tick within a superset group (2 exercises) → focus rotates exercise→exercise per set with a "Next: <exercise>" cue and a 60–90 s rest default; a 3+ exercise group rotates as a circuit with a 30–45 s default.
- Set up a group with uneven set counts (e.g. 4×5 next to 3×8) → once the 3-set member is exhausted it is skipped in later rounds while the 4-set member continues; the skipped member's done sets still appear in the summary.
- Edit the exercise's kg and RIR, and edit per-set reps, before ticking → the edited per-exercise kg and RIR and the per-set reps appear in the summary and in the saved `set_logs` row (one row, scalar `weight`, scalar `rir`, `reps[]`).
- Confirm that no per-set kg or per-set RIR field exists in the player — kg and RIR are single per-exercise inputs.
- Leave a set blank (no reps, no exercise weight) and tick → it is treated as skipped and not written.
- Finish the last step → the summary shows per-exercise sets-done-vs-target, total volume (per-exercise weight × Σ reps), and the session duration from the Phase 8 timer.
- Confirm the summary → the logged exercises appear in `set_logs` (`source='app'`, one row per `plan_exercise_id`); reading the same plan day via the MCP client returns them immediately (read-back consistency).
- Background / kill the app mid-session and reopen → the player resumes the same step with ticked sets intact; nothing is in `set_logs` yet.
- Abandon via back/cancel → confirmation appears; on confirm, the player closes and `set_logs` is unchanged.
- Cross-user check (regression): a confirmed session writes only the signed-in user's rows (RLS), no `user_id` is sent from the client.

## Risks and mitigations

| Risk | Mitigation |
| ---- | ---------- |
| Rotation logic for uneven set counts within a superset group (e.g. 4×5 next to 3×8) is ambiguous | Rotate by set index; when a group member has no remaining set, it is skipped in that and later rounds while others continue — pinned in an Outcome bullet and exercised by the dedicated uneven-count Verification check. |
| The per-set UI implies per-set kg/RIR that `set_logs` cannot store | Decision fixes kg/RIR as single per-exercise values applied to all ticked sets; only reps are per-set; the player exposes no per-set kg/RIR field — verified by the "no per-set kg/RIR field" check. |
| The compound/isolation classification is under-specified, blocking the base-case rest default | `classifyExerciseType` is fully specified over `category`/`equipment` in *Prior decisions*; Phase 9 owns it, Phase 8 owns only type → duration, so the map is not duplicated — verified by the non-superset rest-default check. |
| Re-implementing timer logic instead of reusing Phase 8, drifting the two surfaces | Acceptance pins "reuse the Phase 8 timer component" and "supply only the resolved type"; review rejects any new countdown/foreground-service code in this feature. |
| Partial-save on accidental back loses or corrupts logs | Single persistence point (summary confirm); abandon discards; resume restores from the local cache — covered by the background/kill and abandon checks. |
| `ViewModel`/Composable boundary erosion (stepping/classification logic creeping into the UI) | Constitution check in review: stepping/rotation/classification/aggregation in `SessionPlayerViewModel`, Composables render `StateFlow` only. |
| Writing empty/skipped sets pollutes history | Skip guard (no reps and no exercise weight) mirrored from the prototype; tested by the "leave a set blank" check. |
| Phase 7 / Phase 8 contracts not yet final at implementation time | This spec consumes them by their public surface only (Phase 7 display data; Phase 8 timer auto-prompt API + type → duration map + session-duration timer) and supplies the resolved exercise type into Phase 8; if either contract shifts, the integration issue escalates `needs:planning` rather than forking silently. |

## Decision log

All decisions in *Prior decisions* were auto-resolved under the autonomous planning mandate (2026-06-17), there being no human spec-acceptance gate this run:

- Ephemeral client-side session, no in-progress table (auto-resolved under autonomous planning mandate, 2026-06-17).
- kg and RIR are per-exercise single values (not per-set); only reps vary per set, matching the scalar `weight`/`rir` + `reps[]` shape of `set_logs` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Persist only on summary confirm, via the existing Phase 3 `set_logs` repository path with `source='app'`, one row per `plan_exercise_id` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Superset/circuit threshold by group size (2 → superset, 3+ → circuit) with the prototype's rest defaults (auto-resolved under autonomous planning mandate, 2026-06-17).
- Set-synchronized round-robin rotation within a `superset_label` group, skipping exhausted members on uneven counts (auto-resolved under autonomous planning mandate, 2026-06-17).
- Rest default by exercise type, auto-prompted by reusing the Phase 8 timer component; Phase 9 owns `category`/`equipment` → type classification, Phase 8 owns type → duration (auto-resolved under autonomous planning mandate, 2026-06-17).
- `classifyExerciseType` mapping `category`/`equipment` → compound | isolation as specified in *Prior decisions* (auto-resolved under autonomous planning mandate, 2026-06-17).
- Editable, pre-filled kg/reps/RIR with a skip guard for empty sets (auto-resolved under autonomous planning mandate, 2026-06-17).
- Abandon requires confirmation and writes nothing (auto-resolved under autonomous planning mandate, 2026-06-17).
- Session duration read from the Phase 8 session-duration timer (auto-resolved under autonomous planning mandate, 2026-06-17).
- No new MCP tool, migration, or table; purely an Android feature (auto-resolved under autonomous planning mandate, 2026-06-17).