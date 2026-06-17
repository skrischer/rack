# Spec: Phase 10 — Push notifications & reminders

> Created: 2026-06-17

Deliver Android notification infrastructure — runtime permission, channels, local workout reminders (WorkManager), per-user FCM token storage, and a server-sent "your agent updated your plan" push via a Supabase Edge Function triggered by a Database Webhook — so device-local events fire locally and agent edits reach a backgrounded app, reinforcing the live-sync USP.

## Outcome

- [ ] Each signed-in device registers its FCM token to a per-user `device_tokens` table (one row per device, idempotent upsert on the token); the token is refreshed on `onNewToken` and, on sign-out, the device's row is flagged `active=false` (a soft deactivate, not a hard delete) so the Edge Function's active-tokens read skips it.
- [ ] An agent write (`source='agent'`) to a user's plan-structure data — the `plans`, `plan_days`, and `plan_exercises` tables only — inserts a row into a `notifications` table; an app write (`source='app'`) does not, and agent edits to `set_logs` and `artifacts` do not.
- [ ] A `notifications` INSERT fires a Database Webhook that invokes the `push-on-notification` Edge Function, which sends an FCM HTTP v1 message to every active token of that notification's owner (`user_id`) and to no other user's tokens.
- [ ] With the app backgrounded or killed, a plan change made via the rack-MCP produces a system-tray notification on the user's device titled to the effect of "Your agent updated your plan"; tapping it opens the app at the changed plan (`plan_id` from the payload).
- [ ] The Edge Function removes any token that FCM reports as `UNREGISTERED`/`NOT_FOUND` (hard-delete of the dead row) so stale tokens do not accumulate.
- [ ] A workout reminder can be enabled with a weekday set and time; it fires a local notification at that time via WorkManager with no server round-trip, and survives app restart and device reboot.
- [ ] A `rest_timer` notification channel and a local `showRestTimerDone()` helper exist (importance high, vibrate + sound) for Phase 8/9 to call; this phase wires the channel and helper, not the timer logic.
- [ ] On Android 13+ the app requests `POST_NOTIFICATIONS` at the right moment and degrades gracefully (no crash, reminders simply do not show) if the user denies it.
- [ ] `make verify` and `make build` are green; the migration ships RLS for every new user-owned table in the same file.

## Scope

### In scope

- A `/supabase/migrations` migration adding `device_tokens` (user-owned, RLS) and `notifications` (user-owned, RLS) with the column contract defined under **Data contract** below, plus a trigger that inserts a `notifications` row on agent-sourced plan-structure changes, and the Database Webhook wiring documented for the human to create.
- A Supabase Edge Function `push-on-notification` (Deno/TypeScript, strict, Zod-validated webhook payload) that reads the owner's active tokens (service-role, server infra) and calls FCM HTTP v1 with a Google service-account OAuth2 token, including stale-token cleanup and webhook-secret verification.
- Android: `POST_NOTIFICATIONS` runtime permission flow; notification channels (`agent_update`, `workout_reminder`, `rest_timer`); a `FirebaseMessagingService` for token register/refresh and foreground/background message handling with tap deep-link to the changed plan; a `DeviceTokenRepository` over `supabase-kt`.
- Android: workout-reminder scheduling via WorkManager (weekday + time), persisted in DataStore (device-local prefs), with reboot rescheduling; a minimal in-app notifications section to toggle the reminder and pick days/time.
- A local `showRestTimerDone()` helper + its channel for Phase 8/9 to call.

### Out of scope

- The rest/session timer itself and its foreground service (Phase 8) and the guided session player (Phase 9) — this phase only provides the channel + helper they call. Why: those phases own the timer; cross-phase dependency is avoided.
- A full settings surface (units, theme, profile) — Phase 12. Why: only the reminder toggle is needed here; the rest lives in `docs/specs/spec-settings.md`.
- Server-sent push for the app's own (`source='app'`) writes, and any non-plan-structure notification types — agent edits to `set_logs` and `artifacts` do not push in this phase. Why: notifying a user of their own edits is noise, and the deep-link surface here is the plan view; only agent plan-structure edits reinforce the USP and have a target to open. Logged-set / artifact pushes are deferred until those surfaces have a deep-link target.
- Third-party push SaaS (OneSignal etc.), iOS/web push. Why: direct FCM HTTP v1 is sufficient and one fewer dependency (prior art AVOID); the product is Android-only.

## Data contract

This is the load-bearing contract three issues build on (the trigger writes it, the Edge Function reads it for the FCM title/body and `data` payload, the Android tap reads `plan_id` to deep-link). It is pinned here so no issue has to guess.

### `notifications` table

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid PK (default `gen_random_uuid()`) | |
| `user_id` | uuid, not null, FK `auth.users` | The owner; drives the `user_id = auth.uid()` RLS policy and the Edge Function's per-owner token read. Resolved by the trigger (see below). |
| `type` | text, not null | `'agent_plan_update'` for this phase (a single enum value; future types extend it). Lets the Edge Function and app branch on category. |
| `plan_id` | uuid, not null | The plan whose structure changed; carried into the FCM `data` block for tap deep-linking. Resolved by the trigger (see below). |
| `title` | text, not null | Display title, e.g. "Your agent updated your plan". Written by the trigger so the Edge Function does not embed copy. |
| `body` | text, null | Optional display body (e.g. the plan name); may be null. |
| `source` | text, not null | Always `'agent'` for rows this trigger inserts; carries the existing `source` convention. |
| `created_at` | timestamptz, not null, default `now()` | Ordering / audit. |

RLS: owner policy `user_id = auth.uid()` in the same migration. (The trigger runs `SECURITY DEFINER` so its insert is not blocked by RLS; the Edge Function reads with the service-role key.)

### `device_tokens` table

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid PK (default `gen_random_uuid()`) | |
| `user_id` | uuid, not null, FK `auth.users` | Owner; drives RLS and the per-owner active-tokens read. |
| `token` | text, not null, unique | The FCM registration token; upsert target (idempotent per device). |
| `platform` | text, not null, default `'android'` | Future-proofing; Android-only this phase. |
| `active` | boolean, not null, default `true` | Sign-out sets `false` (soft deactivate); the Edge Function reads only `active=true`. A re-sign-in upsert flips it back to `true`. |
| `created_at` | timestamptz, not null, default `now()` | |
| `updated_at` | timestamptz, not null, default `now()` | Bumped on upsert/refresh. |

RLS: owner policy `user_id = auth.uid()` in the same migration.

### Trigger: which tables, and how `user_id` / `plan_id` are resolved

The notification-producing trigger fires **only on the three plan-structure tables** — `plans`, `plan_days`, `plan_exercises` — on `INSERT` and `UPDATE`, and **only when `NEW.source = 'agent'`**. It is deliberately **not** placed on `set_logs` or `artifacts` (those also carry `source='agent'`, but a logged-set or artifact edit has no plan-view deep-link target this phase and would be push noise; see Out of scope).

Because `plan_days` and `plan_exercises` do not carry `user_id` or (for `plan_exercises`) `plan_id`, the trigger resolves both fields by walking up to `plans`:

- On `plans`: `plan_id := NEW.id`; `user_id := NEW.user_id` (both present on the row).
- On `plan_days`: `plan_id := NEW.plan_id`; `user_id := (SELECT user_id FROM plans WHERE id = NEW.plan_id)`.
- On `plan_exercises`: `plan_id := (SELECT plan_id FROM plan_days WHERE id = NEW.day_id)`; `user_id := (SELECT p.user_id FROM plan_days d JOIN plans p ON p.id = d.plan_id WHERE d.id = NEW.day_id)`.

The trigger writes the resolved `user_id` and `plan_id` into the `notifications` row, sets `type='agent_plan_update'`, `source='agent'`, `title='Your agent updated your plan'`, and `body` to the resolved plan name (or null). It writes only to `notifications`, never back to a plan table (no recursion).

### Coalescing stance (per-row, no debounce)

The trigger fires **per agent-sourced row INSERT/UPDATE**, so a single multi-write plan-authoring session (architecture key-flow 5) can produce several `notifications` rows — i.e. several pushes. This phase **accepts per-change pushes and does not debounce or coalesce**: building a server-side debounce window is out of proportion for a friends-and-family circle, and the app de-dupes on tap (all rows of a session point at the same `plan_id`, so any tap opens the same plan). Coalescing is explicitly deferred; it is not an open question for this phase.

## Constraints

Honors the constitution and architecture without restating them: TypeScript strict with no `any`/`as unknown as`/`@ts-ignore` in the Edge Function; Zod validates the webhook payload at the function boundary; both new tables are user-owned and ship their `user_id = auth.uid()` RLS policy in the same migration; the Android client uses only the anon key + user JWT and holds no service-role key; the Edge Function's service-role read is Supabase server infra (Realtime-class), not the rack-MCP, so the "MCP never uses service-role / never accepts `user_id`" rules are not crossed — the MCP is untouched by this phase, and the function never accepts a `user_id` from an external caller (it comes from the DB webhook payload); the notification-producing trigger respects the existing `source`/`updated_at` convention and runs `SECURITY DEFINER` so its `notifications` insert is RLS-safe; no business logic in Composables, reminder/token state behind a repository or a WorkManager worker, UI state via ViewModel + StateFlow; functions ≤ 50 lines, files ≤ 400 lines; no new server is self-hosted (the Edge Function runs on managed Supabase, the rack-MCP and its Caddy/VPS are unaffected).

## Prior art

- [Push notifications & reminders — Supabase + FCM (Phase 10)](../prior-art.md#push-notifications--reminders--supabase--fcm-phase-10) — the adopted pattern: per-user FCM token storage, a Database Webhook on a `notifications` INSERT triggering an Edge Function that calls FCM HTTP v1, local scheduling for reminders, and the explicit AVOID of a third-party push SaaS.
- [Real-time sync & live edit highlighting](../prior-art.md#real-time-sync--live-edit-highlighting) — the agent-edit push is the backgrounded complement to the in-app Realtime highlight delivered in Phase 4 (`docs/specs/spec-live-sync.md`); same `source`-driven signal, different transport.
- [Rest & session timers — Android, backgrounded (Phase 8)](../prior-art.md#rest--session-timers--android-backgrounded-phase-8) — informs the `rest_timer` channel and the local-notification choice for the timer-done event handled by Phase 8 (`docs/specs/spec-timers.md`).

> Roadmap note: the roadmap's Phase 10 line says "FCM for rest-timer-done while backgrounded". This spec intentionally overrides that looser phrasing and makes rest-timer-done a **local** notification — which the prior-art entry (`prior-art.md`: "prefer local scheduled notifications … no server round-trip needed") and the roadmap's own "(local scheduled where possible)" clause both endorse. FCM is reserved for the cross-device agent-update push, the only trigger that is genuinely server-side.

## Human prerequisites

- [ ] Firebase project for the Android app: `google-services.json` placed in `/android/app/` (gitignored), with Cloud Messaging (FCM) enabled.
- [ ] FCM HTTP v1 service-account JSON (Firebase console → Project settings → Service accounts → Generate new private key) supplied as the Supabase secret `FCM_SERVICE_ACCOUNT` (never committed).
- [ ] Supabase Edge Functions enabled on the managed project and the Supabase CLI authenticated (`SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF`) so `supabase functions deploy` and `supabase secrets set` can run.
- [ ] Supabase Database Webhook (Database → Webhooks) created on INSERT of `public.notifications` pointing at the deployed Edge Function URL, with the function's verification header/secret value set as `PUSH_WEBHOOK_SECRET`.
- [ ] A physical Android device or emulator with Google Play services (FCM does not deliver on a Play-services-less image) for the round-trip QA check.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| FCM only for the cross-device agent-update push; rest-timer-done and reminders are local notifications (no FCM). | "Prefer local where possible" (roadmap/prior-art): an in-device event needs no server round-trip; FCM earns its keep only when the trigger is server-side (an agent write from another client). | 2026-06-17 |
| Use a `device_tokens` table (one row per device) rather than a single `fcm_token` column on a profile. | A user may sign in on multiple devices; a per-token table makes register/refresh an idempotent upsert and stale-token deletion a row delete, and keeps RLS trivially per-user. | 2026-06-17 |
| The agent-update push is driven by a `notifications` table populated by a DB trigger on agent-sourced plan-structure changes, with the webhook on that table. | Matches the prior-art ADOPT exactly; decouples "what changed" from "send a push", gives a clean RLS surface and an audit trail, and lets the webhook filter (only agent plan-structure edits insert a row) instead of firing on every plan mutation. | 2026-06-17 |
| The trigger sits on `plans`, `plan_days`, `plan_exercises` only — not `set_logs`/`artifacts`. | Those three are the plan-structure tables whose change has a plan-view deep-link target. `set_logs`/`artifacts` also carry `source='agent'` but have no plan-view tap target this phase and would be push noise; deferred until those surfaces exist. | 2026-06-17 |
| The trigger resolves `user_id` and `plan_id` by joining up to `plans`. | `plan_days` carries only `plan_id` and `plan_exercises` only `day_id`; neither has `user_id`. The trigger walks `plan_exercises → plan_days → plans` to derive both the owner (for the row + RLS) and the `plan_id` used for the tap deep-link, so every inserted row is well-formed. | 2026-06-17 |
| The `notifications` row carries `user_id, type, plan_id, title, body, source, created_at`. | The Edge Function builds the FCM message from `title`/`body`/`plan_id` (no copy embedded in the function), the app deep-links on `plan_id`, and `type` lets both branch on category. Pinning the columns removes the implementer fork the three downstream issues shared. | 2026-06-17 |
| The trigger inserts a notification only when `source='agent'`, running `SECURITY DEFINER`. | The user should not be pushed about their own app edits; only agent writes reinforce the live-sync USP. `SECURITY DEFINER` lets the trigger insert the owner's `notifications` row without tripping the `user_id = auth.uid()` RLS policy. | 2026-06-17 |
| Per-row pushes, no server-side debounce/coalesce. | A multi-write authoring session yields several rows/pushes; coalescing is over-engineering for a friends-and-family circle, and the app de-dupes on tap (all rows share one `plan_id`). Explicitly deferred, not an open question. | 2026-06-17 |
| The Edge Function reads `device_tokens` with the service-role key. | The Edge Function is managed Supabase server infrastructure (same tier as Realtime), not the rack-MCP; the constitution's service-role/`user_id` prohibitions bind the MCP, which this phase does not touch. The webhook payload carries the owning `user_id`; the function never accepts a `user_id` from an external client. | 2026-06-17 |
| FCM messages are sent as a `notification` + `data` payload (not data-only). | A `notification` payload is rendered by the system tray even when the app is killed/backgrounded — required for the "agent updated your plan while backgrounded" outcome — while the `data` block carries the `plan_id` for tap deep-linking. | 2026-06-17 |
| Reminder preferences (enabled, weekdays, time) live in Android DataStore, not a Supabase table. | They are device-local scheduling prefs with no cross-client or RLS need; a server table would be over-engineering and would wrongly imply server-side scheduling. | 2026-06-17 |
| Reminders use WorkManager (not AlarmManager). | WorkManager is the modern, Doze-aware, reboot-persistable scheduler; exact-alarm permissions and `BOOT_COMPLETED` plumbing of AlarmManager are unnecessary for a coarse daily/weekly reminder. | 2026-06-17 |
| Three channels: `agent_update`, `workout_reminder`, `rest_timer`. | Android channels are user-tunable per category; separating them lets the user silence reminders without losing agent pushes, and gives Phase 8 its `rest_timer` channel ready-made. | 2026-06-17 |
| Sign-out soft-deactivates (`active=false`); FCM-reported `UNREGISTERED`/`NOT_FOUND` hard-deletes the row. | Two distinct lifecycles: sign-out is reversible (a re-sign-in upsert flips `active` back to `true`, preserving one row per device), whereas a token FCM no longer recognises is permanently dead and is removed. The Edge Function reads only `active=true` rows, so a deactivated token is skipped without being lost. | 2026-06-17 |
| `google-services.json` and the FCM service-account JSON stay gitignored / in Supabase secrets. | Constitution: no secrets in the repo; the Android app ships only the anon key + user JWT. | 2026-06-17 |
| A minimal in-app notifications section (reminder toggle + day/time) ships here, to be absorbed by Phase 12 Settings later. | The reminder needs a control surface and Settings is a later phase; a self-contained section avoids blocking on Phase 12 while keeping the footprint small. | 2026-06-17 |

## Tracking

Full-spec track. Milestone **Phase 10: Push notifications & reminders** (`Depends on milestone: #3`, `#4`). Issues are created from this spec, dependency-ordered, each linking back to `docs/specs/spec-push-notifications.md`; no step list is kept here.

## Verification

- `make verify` green (MCP lint/typecheck/test unaffected; Edge Function lint/typecheck; Android ktlint/detekt).
- `make build` green (`assembleDebug` builds with the Firebase Messaging dependency and `google-services.json` present).
- Human QA (Test is `none yet`, so these are run at the milestone-QA gate on a Play-services device):
  - Sign in on a device; confirm a `device_tokens` row appears for the user with `active=true`; sign out and confirm the row is flagged `active=false` (not deleted); sign back in and confirm the same row flips to `active=true`.
  - With the app backgrounded or killed, make a plan-structure edit via the rack-MCP (agent, `source='agent'`) on a `plans`/`plan_days`/`plan_exercises` row; a system-tray "Your agent updated your plan" notification appears; tapping it opens the app at the changed plan (`plan_id` from the `data` payload) and the changed row is highlighted (Phase 4 path).
  - Make an edit in the app itself (`source='app'`); confirm no push is delivered.
  - Make an agent edit to `set_logs` or `artifacts`; confirm no `notifications` row is inserted and no push is delivered (only plan-structure tables push).
  - Confirm a second user's edit never produces a notification on the first user's device (cross-user isolation).
  - Force a stale token (uninstall/reinstall or revoke) and confirm a subsequent send hard-deletes the dead `device_tokens` row rather than erroring repeatedly.
  - Enable a workout reminder for a near-future weekday/time; confirm the local notification fires at that time with the app closed, and still fires after a device reboot.
  - On Android 13+, deny `POST_NOTIFICATIONS` and confirm the app does not crash and simply shows no notifications; grant it and confirm delivery resumes.
  - Trigger the `showRestTimerDone()` helper directly (debug affordance) and confirm it posts on the `rest_timer` channel with sound/vibration.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| FCM does not deliver on emulators/devices without Google Play services, masking a working pipeline as broken. | QA prerequisite mandates a Play-services device; document this in the human prerequisites. |
| The webhook fires on every `notifications` INSERT, allowing a forged or replayed call to spam pushes. | The Edge Function verifies `PUSH_WEBHOOK_SECRET` on every request and rejects mismatches; the table is RLS-protected so only the owner (or the `SECURITY DEFINER` trigger) can insert. |
| A trigger on three plan-structure tables that inserts a notification row could recurse or double-fire across plans/days/exercises. | The trigger only writes to `notifications` (never back to plan tables) and is scoped to `source='agent'`; per-row pushes for one authoring session are accepted (see Coalescing stance) and de-duped by the app on tap (one `plan_id`). |
| The `user_id`/`plan_id` join for `plan_days`/`plan_exercises` could yield NULL if a parent row is mid-transaction. | The walk uses the row's own FK (`plan_id`/`day_id`) which references an already-committed parent; the trigger fires after the child write, so the parent exists. A defensive NULL guard skips inserting a malformed notification rather than erroring. |
| Stale tokens cause repeated failed FCM sends and noise. | The function hard-deletes `UNREGISTERED`/`NOT_FOUND` tokens on the failing send. |
| Android 13+ permission denial silently breaks reminders and pushes. | Request `POST_NOTIFICATIONS` at a sensible moment, degrade without crashing, and surface the state in the notifications section. |
| The service-role read in the Edge Function is mistaken for an MCP violation in review. | The spec documents that the Edge Function is managed Supabase server infra, not the rack-MCP, and the MCP code is untouched this phase. |
| WorkManager coalescing/Doze delays a reminder past its slot. | Accept coarse timing for a workout reminder (not a precise alarm); schedule the next occurrence on fire and reschedule on `BOOT_COMPLETED`/app start. |

## Decision log

- FCM scoped to the cross-device agent-update push only; rest-timer-done and reminders are local (auto-resolved under autonomous planning mandate, 2026-06-17).
- `device_tokens` per-device table over a single `fcm_token` column (auto-resolved under autonomous planning mandate, 2026-06-17).
- `notifications` table + DB trigger on agent-sourced plan-structure changes + webhook on that table (auto-resolved under autonomous planning mandate, 2026-06-17).
- Trigger placed on `plans`/`plan_days`/`plan_exercises` only, excluding `set_logs`/`artifacts` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Trigger resolves `user_id`/`plan_id` by joining up to `plans` (auto-resolved under autonomous planning mandate, 2026-06-17).
- `notifications` columns pinned: `user_id, type, plan_id, title, body, source, created_at` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Trigger inserts only when `source='agent'`, runs `SECURITY DEFINER` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Per-row pushes, no server-side debounce/coalesce (auto-resolved under autonomous planning mandate, 2026-06-17).
- Edge Function reads tokens with service-role as managed server infra, never accepting a caller `user_id` (auto-resolved under autonomous planning mandate, 2026-06-17).
- FCM `notification`+`data` payload for kill-state delivery plus tap deep-link (auto-resolved under autonomous planning mandate, 2026-06-17).
- Sign-out soft-deactivates (`active=false`); FCM-dead tokens hard-delete (auto-resolved under autonomous planning mandate, 2026-06-17).
- Reminder prefs in DataStore, not a server table (auto-resolved under autonomous planning mandate, 2026-06-17).
- WorkManager over AlarmManager for reminders (auto-resolved under autonomous planning mandate, 2026-06-17).
- Three channels `agent_update`/`workout_reminder`/`rest_timer` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Edge Function deletes `UNREGISTERED`/`NOT_FOUND` tokens (auto-resolved under autonomous planning mandate, 2026-06-17).
- Secrets kept gitignored / in Supabase secrets; app holds only anon key + JWT (auto-resolved under autonomous planning mandate, 2026-06-17).
- Minimal in-app notifications section here, to be absorbed by Phase 12 (auto-resolved under autonomous planning mandate, 2026-06-17).