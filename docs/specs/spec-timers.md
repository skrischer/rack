# Spec: Phase 8 — Rest & session timers

> Created: 2026-06-17

A per-set rest timer that auto-starts on set log and a running session-duration timer, both driven by a single foreground service with a persistent notification so they survive backgrounding and Doze, with vibrate/sound on rest completion, exercise-type defaults, and superset/circuit rotation cues — all embedded in the existing Phase 3 logging flow.

## Service lifecycle (single source of truth)

The foreground service is **session-scoped**: it starts when the session begins (the first set logged) and runs until the user **explicitly** ends the session. The running session-duration timer is itself an "active" timer, so the service stays alive — and its persistent notification stays up — for the entire session, including the gaps between rests when no rest countdown is running. The rest timer is merely an *additional* active timer that comes and goes within that session window; a rest reaching zero or being skipped never stops the service. The service stops in exactly one place: explicit session end (which also clears the notification). There is no "auto-idle when no rest is running" behavior — that reading is explicitly rejected because it would kill the session timer and its notification between sets. Every Outcome, Scope, Prior-decision, Decision-log, and Verification line below is written against this single behavior.

## Outcome

- [ ] Logging a set (the existing Phase 3 log-set action) auto-starts a rest countdown whose initial duration is chosen by exercise type: compound 150 s, isolation 75 s, superset member 75 s, circuit member 38 s.
- [ ] The running rest countdown is visible in the app while logging continues (a non-blocking, dismissible rest bar/pill in the Recomp design language), and is also visible in a persistent system notification with the remaining time.
- [ ] The rest timer keeps counting accurately when the app is backgrounded, the screen is off, or the device is in Doze, because it runs in the session-scoped foreground service anchored by the persistent notification.
- [ ] When the rest countdown reaches zero the device vibrates and plays a short sound; if the app is backgrounded the same completion is surfaced via the notification.
- [ ] The user can, from the rest bar and the notification, add 15 s, subtract 15 s, skip (end now), and restart the current rest with one tap; duration clamps to a non-negative value.
- [ ] A session-duration timer starts at the first set logged of a session and counts up; it is shown in the app and in the persistent notification, and persists across backgrounding (the foreground service keeps it and its notification alive the whole session, including the gaps between rests) the same way the rest timer does.
- [ ] The session is ended only by an **explicit** session-end action, which stops the session timer, clears the foreground notification, and stops the service. Until that explicit end, the service stays alive even when no rest is running, because the running session timer counts as an active timer.
- [ ] For a superset or circuit group (derived from `superset_label`), logging a set shows a rotation cue naming the next exercise/station to perform in the group before the next rest, distinguishing superset (2 members) from circuit (3+ members). The cue is rendered on the same log action that auto-starts the rest; sequencing is fixed below so there is no UI fork.
- [ ] Rest defaults are built-in constants for this phase (no persistence, no settings UI); per-user override of defaults is explicitly deferred to Phase 12 (`docs/specs/spec-settings.md`).
- [ ] `make verify` and `make build` pass; the timer feature adds no business logic to Composables and routes all state through a ViewModel + StateFlow, with no new Supabase tables.

## Scope

### In scope

- A rest-timer engine and a session-timer running in a single, **session-scoped** Android foreground service with a persistent, updating notification (survives backgrounding/Doze) — the prior-art-mandated reliability mechanism. The service lifetime is the session: it starts on the first logged set and stops only on explicit session end, with the running session timer counting as an active timer that keeps it alive between rests.
- Auto-start of the rest timer on the existing set-log action, with initial duration resolved from exercise type (catalog `category` for compound/isolation; `superset_label` group size for superset vs circuit).
- Built-in exercise-type default constants (compound 150 s, isolation 75 s, superset 75 s, circuit 38 s) — midpoints of the Recomp ranges (compound 2–3 min, isolation 60–90 s, superset 60–90 s, circuit 30–45 s).
- Rest controls: +15 s, −15 s, skip, restart — from both the in-app rest bar and the notification actions.
- Vibrate + short sound on rest completion, via a dedicated notification channel; Android 13+ `POST_NOTIFICATIONS` runtime-permission handling.
- A count-up session-duration timer that starts on the first logged set and ends on explicit session end (the sole stop trigger for the service).
- Superset/circuit rotation cues in the log flow, derived from `superset_label` grouping (2 = superset, 3+ = circuit). Group size = the number of `plan_exercises` in the **same `plan_day`** that share the **same `superset_label`** value; the grouping scope is the day, not the whole plan.
- In-app surface in the Recomp design language (dark theme, volt accent `#c8f23a`, Archivo + Spline Sans Mono, superset accent `#a78bfa`), embedded in the existing plan/day/log screen from Phase 3.

### Out of scope

- Per-user configurable rest defaults, custom durations per exercise, and a settings UI — owned by **Phase 12** (`docs/specs/spec-settings.md`); this phase ships fixed constants only.
- The full guided, step-through-the-day session player (current-set focus, tick-through, summary screen) — owned by **Phase 9** (`docs/specs/spec-session-player.md`); this phase only embeds the timers into the existing logging UI.
- Server-sent / FCM rest-timer-done push for the fully-killed-process case and workout-reminder scheduling — owned by **Phase 10** (`docs/specs/spec-push-notifications.md`). This phase relies on a local foreground service, not a server round-trip.
- Any new database table, RLS policy, MCP tool, or Supabase write — the timers are ephemeral client-only state; nothing is persisted server-side. (`set_logs` writes remain exactly the Phase 3 behavior, untouched.)
- Wearable/health-platform timer mirroring (out per vision scope).

## Constraints

Bound by `docs/constitution.md`: Android has no business logic in Composables, UI state flows through a ViewModel + StateFlow, and all Supabase access stays behind the existing repository layer (this phase adds no Supabase access at all); functions ≤ 50 lines and files ≤ 400 lines (guideline); Kotlin passes ktlint/detekt as part of `make verify`; no secrets in the client. No new user-owned table is introduced, so the "RLS policy in the same migration" rule is satisfied vacuously (this phase ships no migration). The MCP, service-role, and `user_id`-from-caller rules are not engaged — this is a pure Android client phase.

## Prior art

- [Rest & session timers — Android, backgrounded (Phase 8)](../prior-art.md#rest--session-timers--android-backgrounded-phase-8) — directly normative: ADOPT a foreground service + persistent notification to survive backgrounding/Doze, vibrate/sound on completion, per-set defaults by exercise type (compound 2–3 min, isolation 60–90 s, superset 60–90 s, circuit 30–45 s); AVOID building a standalone interval-timer app — the timer is embedded in the logging flow.
- [Guided workout session — active player UX (Phase 9)](../prior-art.md#guided-workout-session--active-player-ux-phase-9) — ADOPT the auto-prompt-rest-between-sets and superset/circuit rotation-guidance ideas at the embedded level; the full player stays in Phase 9.
- [Push notifications & reminders — Supabase + FCM (Phase 10)](../prior-art.md#push-notifications--reminders--supabase--fcm-phase-10) — confirms the boundary: rest-timer-done while backgrounded uses **local** mechanisms (foreground service/WorkManager/AlarmManager), not a server round-trip; the FCM path is Phase 10.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — the embedded-in-the-log-flow, minimal-tap interaction this phase's rest controls must match.

## Human prerequisites

None. This phase is a pure on-device Android feature: no Supabase tables, no MCP, no VPS/domain/FCM, no external dataset or signing credentials beyond what Phase 3 already required. Notification and vibration permissions are requested at runtime from the user inside the app, not provisioned by a human ahead of time.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Rest + session timers run in one started Android **foreground service** with a persistent notification | Prior-art ADOPT; the only Android-reliable way to keep a timer counting across backgrounding/Doze without a server | 2026-06-17 |
| The foreground service is **session-scoped**: it starts on the first logged set and stops **only** on explicit session end; the running session timer counts as an "active" timer that keeps the service (and its notification) alive between rests | Resolves the lifecycle fork: a session-scoped service is the only contract under which the session count-up and its persistent notification survive backgrounding in the gaps between rests, as the persistence Outcome and the "keeps running independent of rest restarts" QA scenario both require. The rejected "auto-idle when no rest runs" reading would kill the session timer between sets | 2026-06-17 |
| Rest defaults are **built-in constants**, not persisted or user-configurable this phase | Phase 12 owns settings/overrides and is not a dependency; shipping fixed constants keeps scope tight and avoids a DB/migration this phase does not need | 2026-06-17 |
| Default durations are the **midpoints** of the Recomp ranges: compound 150 s, isolation 75 s, superset 75 s, circuit 38 s | The prototype gives ranges, not single values; a deterministic midpoint is the sensible single default an implementer needs and is trivially overridable in Phase 12 | 2026-06-17 |
| Exercise type resolves from the existing schema: `exercises.category` → compound/isolation; `plan_exercises.superset_label` group size → superset (2) / circuit (3+). Group size is the count of `plan_exercises` in the **same `plan_day`** sharing the **same `superset_label`** | These fields already exist (architecture data model); no schema change, no caller input needed; pinning the grouping scope to the day removes the only ambiguity in computing group size from a string tag | 2026-06-17 |
| Compound-vs-isolation is decided by a small **category-name map** with isolation as the safe fallback for unmapped categories | wger `category` is a coarse string (e.g. Legs/Chest/Arms) not a compound flag; a name map covers the common cases and the conservative fallback (shorter rest) avoids over-resting; this map lives in one constants file, easy to refine | 2026-06-17 |
| **No** new table, RLS policy, MCP tool, or Supabase write is added | Timer state is ephemeral and client-only; persistence is unnecessary and would pull in scope this phase does not need | 2026-06-17 |
| Rest controls are **+15 s / −15 s / skip / restart**, mirrored in the app bar and the notification actions, duration clamped ≥ 0 | Matches the minimal-tap embedded UX in prior art; ±15 s is the de-facto increment in the reference trackers; clamping prevents negative timers | 2026-06-17 |
| The rest bar is **non-blocking and dismissible**, layered over the existing Phase 3 log screen | The timer is embedded in the logging flow, not a standalone surface or a modal that blocks logging the next set | 2026-06-17 |
| The rotation cue is rendered on the **same log action** that auto-starts the rest, and is shown **alongside** the rest bar (not as a separate blocking step before it) | The cue and the rest both follow a logged set; co-locating the cue with the rest bar on one log action removes a possible cue-vs-rest sequencing fork and keeps the embedded flow single-tap | 2026-06-17 |
| The session timer **auto-starts on the first set log** and ends only on **explicit** session-end; explicit session-end is the **sole** trigger that stops the service | A running session is implied by logging; an explicit end is the only thing that marks "done"; making explicit end the single stop trigger guarantees the session timer and its notification survive backgrounding between rests | 2026-06-17 |
| Completion alert uses **vibrate + a short sound** via a dedicated high-importance notification channel | Prior-art ADOPT; a dedicated channel lets the OS/user manage the alert and gives Doze-safe delivery | 2026-06-17 |
| `POST_NOTIFICATIONS` (Android 13+) is requested at runtime; if denied, timers still run and alert via vibration, with an in-app rationale. This handling is owned by the service+notification step (#3), and the in-app rationale surface by the UI-embed step (#4) | The notification is the service anchor and completion surface; graceful degradation keeps the timer usable without it; pinning ownership removes any ambiguity about which step implements the permission flow | 2026-06-17 |
| No wear-OS / companion mirroring, no across-app-restart timer resume after full process kill | Out of vision scope and Phase-10 territory respectively; a foreground service already covers backgrounding/Doze, which is the stated requirement | 2026-06-17 |

## Tracking

- Milestone: **Phase 8: Rest & session timers** (depends on the Phase 3 milestone). The `Depends on milestone:` line is wired to the actual GitHub milestone number for Phase 3 at issue-creation time — milestone IDs are assigned sequentially across all created milestones and may not equal the phase number, so the implement loop confirms the mapping rather than assuming milestone #3 == Phase 3.
- Issues: created from the decomposition below, one GitHub issue per step, each carrying `Spec: docs/specs/spec-timers.md`, a `Goal:` line, an `Acceptance:` checklist, and `Depends on:` lines for intra-phase order. No step list is restated here.

## Verification

Machine gates (from `docs/workflow.md`): `make verify` (MCP lint+typecheck+test plus Android ktlint/detekt) green per iteration, and `make build` (`./gradlew assembleDebug`) green before the app-affecting PR. Project Test command is `none yet`, so the behavioral checks below are the human milestone-QA scenarios derived from this spec:

- Log a set on a **compound** exercise → a rest countdown starts at 150 s; on an **isolation** exercise → 75 s.
- Log a set on an exercise in a 2-member superset group → rest starts at 75 s and a cue names the other exercise as the next to perform; in a 3+ member circuit group → 38 s and the cue names the next station and labels the group a circuit. (Group size = count of `plan_exercises` in the same `plan_day` sharing the same `superset_label`.)
- While a rest is running, background the app / turn the screen off / let the device idle → re-open after the duration and confirm the timer counted down correctly (no drift to a stuck value), proving the foreground service kept it alive.
- Let a rest reach zero with the app backgrounded → device vibrates and plays a sound, and the notification shows completion; with the app foregrounded → the in-app bar shows done with the same alert.
- Tap +15 s, −15 s, skip, and restart from both the in-app rest bar and the notification actions → each behaves identically from both surfaces; −15 s past zero clamps (never negative).
- Log the first set of a session → the session timer begins counting up and appears in both the app and the notification; continue logging across several sets, letting some rests finish and some be skipped, with the app backgrounded between sets → the session timer keeps running and its persistent notification stays up the whole time, independent of rest restarts (the service does **not** stop when a rest ends or no rest is running).
- End the session **explicitly** → only then does the session timer stop, the persistent notification clear, and the foreground service stop (no lingering notification). Confirm there is no other path that stops the service.
- On a fresh install / Android 13+ device, the first timer start prompts for notification permission; deny it → timers still run and alert by vibration, and an in-app rationale explains the reduced behavior.
- Inspect the diff: no Supabase call or business logic inside a Composable, timer state exposed via a ViewModel `StateFlow`, no new migration/table, no new MCP tool.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Foreground-service / notification-permission rules differ across Android versions (13+ runtime permission, 14 service-type declarations) and the timer silently fails to keep running | Declare the correct `foregroundServiceType`, request `POST_NOTIFICATIONS` at runtime with a rationale, and QA the background/Doze scenario on a modern API level; degrade to vibration-only if notifications are denied |
| Clock drift if the countdown is driven by repeated UI ticks while backgrounded | Anchor the timer on a monotonic end-timestamp (elapsed-realtime) and compute remaining = end − now on each tick, so backgrounding never accumulates drift |
| Mis-reading the service lifecycle and auto-stopping the service when no rest is running, killing the session timer/notification between sets | The spec pins one contract: session-scoped service, explicit session end is the sole stop trigger, running session timer counts as active; the QA "continue logging backgrounded between sets" scenario verifies the notification survives the gaps |
| wger `category` strings do not cleanly map to compound/isolation, mis-picking a default | A single category→type map with isolation (shorter rest) as the conservative fallback, isolated in one constants file; Phase 12 makes it user-overridable |
| Scope creep into the Phase 9 session player (step-through, summary) or Phase 12 settings | Spec fixes the boundary: embed in the existing log screen only, constants only; both adjacent concerns are referenced to their sibling specs and kept out |
| Vibration/sound annoys or is muted, undermining the "done" signal | Use a dedicated high-importance channel the user can tune, and always pair sound with vibration so a silenced device still signals |

## Decision log

- Run both timers in one Android foreground service with a persistent notification (auto-resolved under autonomous planning mandate, 2026-06-17).
- Make the foreground service session-scoped — start on first set log, stop only on explicit session end, with the running session timer counting as active so the service and its notification survive backgrounding between rests; reject the "auto-idle when no rest runs" reading (auto-resolved under autonomous planning mandate, 2026-06-17).
- Ship rest defaults as built-in constants with no persistence or settings UI; defer user overrides to Phase 12 (auto-resolved under autonomous planning mandate, 2026-06-17).
- Use the Recomp-range midpoints as the single default durations — compound 150 s, isolation 75 s, superset 75 s, circuit 38 s (auto-resolved under autonomous planning mandate, 2026-06-17).
- Resolve exercise type from `exercises.category` and `plan_exercises.superset_label` group size with no schema change; group size = count of `plan_exercises` in the same `plan_day` sharing the same `superset_label` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Decide compound vs isolation via a category-name map with isolation as the safe fallback (auto-resolved under autonomous planning mandate, 2026-06-17).
- Add no new table, RLS policy, MCP tool, or Supabase write — timer state is ephemeral and client-only (auto-resolved under autonomous planning mandate, 2026-06-17).
- Provide +15 s / −15 s / skip / restart controls mirrored in app and notification, clamped ≥ 0 (auto-resolved under autonomous planning mandate, 2026-06-17).
- Keep the rest bar non-blocking and dismissible over the existing log screen, with the rotation cue rendered on the same log action alongside the rest bar (auto-resolved under autonomous planning mandate, 2026-06-17).
- Auto-start the session timer on first set log and stop the service only on explicit session end (auto-resolved under autonomous planning mandate, 2026-06-17).
- Alert on completion with vibrate + short sound via a dedicated high-importance notification channel (auto-resolved under autonomous planning mandate, 2026-06-17).
- Request `POST_NOTIFICATIONS` at runtime and degrade to vibration-only if denied; ownership pinned to the service step (#3) with the in-app rationale on the UI-embed step (#4) (auto-resolved under autonomous planning mandate, 2026-06-17).
- Exclude wear-OS mirroring and post-process-kill resume from this phase (auto-resolved under autonomous planning mandate, 2026-06-17).