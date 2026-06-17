# Spec: Phase 4 — Live sync & edit highlighting

> Created: 2026-06-17

Subscribe the Android app to per-user Supabase Realtime row changes so agent writes (`source='agent'`) land live and highlighted in under one second on the same network, while the app's own writes apply optimistically and the subscription survives reconnects — the wow loop, end to end. **Built and verified now against the local Supabase stack** (`supabase start` → API on `http://localhost:55321`, emulator reaches it at `http://10.0.2.2:55321`) with the rack-MCP running as a plain Node process on `http://localhost:<port>`; managed Supabase, VPS/Caddy/TLS, and Firebase/FCM remain the eventual production target and are **deferred (hosted deploy)**.

## Outcome

- [ ] A write made through the rack-MCP (`source='agent'`) to a row the signed-in user owns appears in the running app, visibly highlighted, in **< 1 s on the local stack / same network**, without any manual refresh. (Verified locally: emulator on `http://10.0.2.2:55321` against `supabase start`, real `source='agent'` write driven by a local MCP client connected to the localhost MCP.)
- [ ] Highlighting is driven by `source`: only rows whose latest mutation carries `source='agent'` are highlighted; rows last touched by the app (`source='app'`) are not.
- [ ] The highlight is transient — it draws attention when the change lands and fades after a bounded interval (3 s), and a row re-highlights if the agent touches it again.
- [ ] The Realtime channel is **per-user**: it only ever delivers changes to rows the signed-in user owns; a second user's agent write never reaches this user's channel (enforced by RLS-authorized Realtime, not by client-side filtering alone). Verified against the **local stack's** Realtime with two local Auth users.
- [ ] The app's own set-log and any app-side writes apply **optimistically** — the UI reflects them immediately on submit, and the subsequent Realtime echo of the user's own write neither duplicates the row nor highlights it.
- [ ] The subscription **reconnects automatically** after transient network loss or app foregrounding, re-syncing current server state on resubscribe so no agent change made while disconnected is missed.
- [ ] Realtime delivers row payloads for the relevant user-owned tables (`plans`, `plan_days`, `plan_exercises`, `set_logs`) — i.e. those tables are in the Realtime publication with the replica identity Realtime needs, shipped as a migration with their RLS already in place. The migration applies to the **local Docker DB** (`supabase db reset` / migration up); `db push` to the managed project is **(deferred — hosted deploy)**.
- [ ] All Realtime/subscription/highlight logic lives behind the repository + ViewModel layers; Composables only render highlight state from `StateFlow`, holding no business logic and making no Supabase calls.

## Scope

### In scope

- A per-user Supabase Realtime subscription in the Android app over the user-owned training tables (`plans`, `plan_days`, `plan_exercises`, `set_logs`), authorized with the user's JWT so RLS scopes the stream. Built against the **local stack** (anon key from `supabase start`; emulator endpoint `http://10.0.2.2:55321`, physical device on the host LAN IP); the local anon key goes into `android/local.properties`. Local Realtime is included in `supabase start` and works without any managed project.
- `source`-based, transient highlighting of incoming agent edits in the existing plan/day/exercise and logging UI from Phase 3 (Recomp visual language). The real `source='agent'` write that drives this is produced by a **local MCP client connected to the localhost rack-MCP** (Node process over Streamable HTTP).
- Optimistic UI for the app's own writes, with de-duplication of the Realtime echo of those same writes (last-write-wins reconciliation against server-authoritative payloads — no CRDT).
- Reconnect/resubscribe handling: lifecycle-aware subscribe/unsubscribe, automatic reconnect, and a full re-sync of current server state on resubscribe.
- The database migration enabling Realtime for those tables: publication membership and `REPLICA IDENTITY` such that RLS-authorized Realtime emits the rows. The tables and their RLS policies are created in Phase 1 — this migration only enables Realtime on already-existing tables (it adds no table and no policy). It applies and is verified against the **local Docker DB**; applying it to the managed project via `db push` is **(deferred — hosted deploy)**.

### Out of scope

- Authoring tools, new MCP write tools, or any change to the rack-MCP surface — agent writes are produced by Phase 2 (spec TBD); this phase only consumes their effect.
- The logging screen, plan/day/exercise views, repository, and auth themselves — built in Phase 3 (spec TBD); this phase wires highlighting onto them.
- Server-sent "your agent updated your plan" push notifications — Phase 10 (spec TBD); FCM/Firebase delivery is a hosted concern and not in this phase.
- API-key creation/management UI — Phase 5 (spec TBD).
- `artifacts` table live sync — Phase 6 (spec TBD); only the training tables sync here.
- Conflict-free merge machinery (CRDT/Yjs) — explicitly excluded by the constitution; server-authoritative last-write-wins suffices for single-owner-plus-agent.
- **Hosted deploy of this phase** — `db push` of the enabling migration to a managed Supabase project, and any VPS/Caddy/TLS/domain fronting of the rack-MCP — is **deferred (hosted deploy)**, validated separately when the project goes hosted. The local stack is the build-and-verify target for this phase.

## Constraints

Binds to `docs/constitution.md`: Android has no business logic in Composables, UI state via ViewModel + StateFlow, all Supabase access (including Realtime) behind the repository layer; the app uses the anon key + user JWT only and holds no service-role key; every user-owned table keeps its RLS policy (`user_id = auth.uid()`) — this phase adds none but relies on Phase 1's policies for stream scoping; no CRDT library; Kotlin idiomatic Compose under ktlint/detekt; functions ≤ 50 lines, files ≤ 400 lines (guideline). The `source` ('app' | 'agent') and `updated_at` columns this phase reads on every payload are guaranteed present by the constitution's "every mutation sets `source` and `updated_at`" rule.

Local-first dev path: development and verification run against the **local Supabase stack** (`supabase start`) — its generated anon key, service_role key, JWT secret, and Postgres URL replace any human-delivered managed keys for local work, so no managed project and no human-delivered secret is needed to build this phase. The schema (Phase 1's tables + RLS, plus this phase's enabling migration) is applied to the local Docker DB via `supabase db reset` / migration up (safe — wipes only the local DB). The Android emulator reaches the stack at `http://10.0.2.2:55321` (physical device: host LAN IP); only the **local anon key** goes into `android/local.properties`. Local Auth test users are created by signing up against the local Auth (no dashboard). The constitution's hosting choices (managed Supabase, VPS + Caddy + domain/TLS, Firebase/FCM) remain the eventual production target — **deferred (hosted deploy)**, not removed.

Known local caveat: the rack-MCP runs as a plain Node process on `http://localhost:<port>` with **no Caddy, no TLS, no domain** for local dev. Some MCP clients want HTTPS even for localhost; resolve with a local `cloudflared` quick-tunnel or a local Caddy with a localhost cert. This is a recorded local caveat, **not a blocker** — local-stack Realtime and the localhost MCP are sufficient to drive a real `source='agent'` write end to end.

The Android client is `supabase-kt` (per the constitution), so the concrete Realtime API is `supabase-kt`'s, not `supabase-js`'s: the channel is a `RealtimeChannel` created via the `Realtime` plugin, its Postgres-changes flows consumed with `channel.postgresChangeFlow`, and JWT authorization applied with the `supabase-kt` channel-auth call (`realtime.setAuth(token)` on the `Realtime` plugin, kept in sync with the auth session's access token). Where this spec names `realtime.setAuth` it refers to that `supabase-kt` call. The decision is unchanged: authorize the channel with the user JWT and rely on RLS-authorized Realtime for isolation — never a client-side `user_id` filter as the isolation boundary.

## Prior art

- [Real-time sync & live edit highlighting](../prior-art.md#real-time-sync--live-edit-highlighting) — directly normative: ADOPT WebSocket push of server-authoritative changes, visual highlight of agent edits as they land, and optimistic UI on the app's own writes; AVOID CRDT/Yjs — server-authoritative state + last-write-wins is the sanctioned model for this phase.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — relevant for keeping the highlight consistent with the local-first, responsive logging interaction Phase 3 established.

## Human prerequisites

### Local (primary — what building this phase actually needs)

- [ ] Local dev tooling: Docker + Supabase CLI (`supabase start`) and the Android SDK with an **emulator** (or a physical device on the host LAN). No managed project, no human-delivered Supabase keys — the local stack generates the anon/service_role/JWT secrets.
- [ ] A **local MCP client** (e.g. Claude Desktop or any Streamable-HTTP MCP client) to connect to the localhost rack-MCP and drive a real `source='agent'` write for the round-trip scenario. Mind the localhost-HTTPS caveat above (cloudflared quick-tunnel or local Caddy cert if the client demands HTTPS).

No human-delivered secrets are required for local development: the local Auth test user is created by signing up against the local Auth, and random secrets (e.g. `API_KEY_PEPPER`) are generated locally by the implementer.

### Deferred — hosted deploy (recorded, not blocking local work)

- [ ] Managed Supabase project + access token, with the enabling migration applied via `db push` and project-level Realtime confirmed (Postgres Changes on by default).
- [ ] VPS + domain + DNS + TLS via Caddy fronting the rack-MCP (HTTPS for remote MCP clients).
- [ ] (Out of this phase, noted for the round-trip wow loop in production) Firebase project + FCM service account + Play-services device — only relevant to Phase 10 push, not to Realtime highlighting.
- [ ] A physical Android device or emulator on the **same network as the managed Supabase project** for a production < 1 s round-trip measurement (the local equivalent is measured on the local stack).

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| **Build and verify this phase on the local Supabase stack (`supabase start`), with the rack-MCP on localhost; managed Supabase, VPS/Caddy/TLS, and Firebase/FCM deferred to hosted deploy.** | Local-stack Realtime/Storage/Auth are included in `supabase start` and reproduce the full wow loop with no external resource; hosted infra is genuinely external and unnecessary to prove the loop. Keeps the constitution's hosting choices intact as the eventual production target. | 2026-06-17 |
| Use Supabase Realtime **Postgres Changes** (per-table row events) as the sync transport, not Broadcast or a custom WS server. | The constitution names Supabase Realtime as the socket layer; Postgres Changes carries the row payload with `source`/`updated_at` directly, which is exactly what highlighting needs. | 2026-06-17 |
| Subscribe over the four training tables `plans`, `plan_days`, `plan_exercises`, `set_logs`; exclude `exercises` (public catalog, immutable for users), `api_keys` (Phase 5), `artifacts` (Phase 6). | These are the only user-owned tables an agent edit lands in for the plan/logging surfaces this phase highlights; the rest are out of phase scope. | 2026-06-17 |
| Authorize the Realtime channel with the user JWT and rely on RLS-authorized Realtime (the `supabase-kt` channel-auth call, `realtime.setAuth`) so the server filters rows by ownership; do **not** rely on a client-side `user_id=eq.` filter as the isolation boundary. | The constitution puts isolation in the DB, not app code; RLS-authorized Realtime is the per-user isolation guarantee the success-criterion cross-user test checks. A client filter alone could be bypassed and would not satisfy "enforced in the DB". | 2026-06-17 |
| Highlight is driven solely by the payload's `source` field being `'agent'`, not by inferring "this row is new to me". | The constitution makes `source` the explicit highlight signal; reading it directly is unambiguous and avoids guessing provenance from diffing. | 2026-06-17 |
| Highlight is transient: a row glows on the volt/lime accent (`#c8f23a`, the Recomp accent) and fades after 3 s; re-touch re-triggers it. | Matches the Recomp visual language (volt accent already signals "live/active" values in the prototype); a transient cue draws the eye without permanently cluttering the list. 3 s is long enough to notice on a glance, short enough not to linger. | 2026-06-17 |
| Optimistic UI: app writes update local state immediately; the Realtime echo of the user's own write is reconciled by primary key (last-write-wins) and never highlighted (its `source='app'`). | The prior-art harvest mandates optimistic UI on own writes; `source='app'` on the echo naturally excludes self-edits from highlighting, and PK reconciliation prevents duplicate rows. | 2026-06-17 |
| On (re)subscribe — including app foreground and reconnect — perform a full repository re-read of current server state, then attach the live stream, rather than replaying a missed-event backlog. | Supabase Postgres Changes does not buffer missed events across a dropped socket; a re-read of server-authoritative state is the correct, simplest catch-up and fits last-write-wins. Changes that arrived while disconnected are picked up by the re-read (not highlighted, since they are reconciled as current state, which is acceptable — the live highlight is a same-session cue, not a persisted "unread" marker). | 2026-06-17 |
| Subscription lifecycle is tied to the screen/app lifecycle: subscribe when the relevant screen is active/app foregrounded, unsubscribe (or pause) on background, reconnect on return. | Avoids a leaked socket and battery drain in the background; foreground return triggers the re-read+resubscribe above. | 2026-06-17 |
| Set `REPLICA IDENTITY FULL` on the four synced tables in the enabling migration. | RLS-authorized Realtime evaluates the row against the policy and needs full row data for `UPDATE`/`DELETE` to apply the WAL row to the authenticated user's policy; `FULL` is the documented requirement for RLS-scoped Postgres Changes. | 2026-06-17 |
| Reconnect/highlight tunables (3 s highlight fade, reconnect/backoff handling) live in the repository/ViewModel layer as named constants, not in Composables. | Keeps business logic out of the UI per the constitution; makes the timing a single reviewable value. | 2026-06-17 |

## Tracking

Tracked as milestone **Phase 4: Live sync & edit highlighting** (`Depends on milestone: #1`, `#2`, `#3`). The dependency on **Phase 1 (Foundation & data backbone)** is load-bearing: Phase 1 creates the `plans`, `plan_days`, `plan_exercises`, `set_logs` tables and their RLS policies (confirmed against `docs/roadmap.md` and `docs/architecture.md`; `supabase/migrations/` is empty until then), and this phase's enabling migration only adds those existing tables to the Realtime publication and sets `REPLICA IDENTITY FULL` against the local DB — it must not run before they exist. The dependencies on **Phase 2** (agent writes that produce `source='agent'` payloads) and **Phase 3** (the app, repository, and plan/logging UI this phase wires highlighting onto) remain. All of this phase's issues are implementable against the local stack; none is blocked on hosted infra. Implementable steps live as that milestone's GitHub issues, each tracing back to this spec; this section intentionally lists no steps (the spec is design + done-criteria, the issues are the steps).

## Verification

Gate commands from `docs/workflow.md`: `make verify` (lint + typecheck + tests, plus Android ktlint/detekt) must be green before merge, and `make build` (`./gradlew assembleDebug` + MCP build) must succeed since this phase is app-affecting. `Test` is `none yet`, so the following behavioral checks are run by a human at the milestone-QA gate, **against the local stack** (`supabase start`, migration applied via `supabase db reset`, app on the emulator at `http://10.0.2.2:55321`, agent writes from a local MCP client connected to the localhost rack-MCP):

- **Round-trip latency:** with the app open on a plan, trigger a `source='agent'` write via the connected local MCP client (e.g. edit a plan exercise's target or insert a set log). The change appears and is highlighted in the app in **< 1 s** on the local stack, no refresh. (Mirrors Outcome 1.) Production same-network measurement against the managed project is **(deferred — hosted deploy)**.
- **Source-based highlighting:** the agent-written row is highlighted; a row the user just logged from the app (`source='app'`) is not. (Outcome 2.)
- **Transient highlight:** the agent-edit highlight fades within ~3 s; a second agent touch of the same row re-highlights it. (Outcome 3.)
- **Per-user isolation:** signed in as local user A, an agent write to local user B's data (via B's API key) never appears or highlights in A's app — verified against the local stack's RLS-authorized Realtime with two local Auth users. (Outcome 4.)
- **Optimistic write + echo de-dup:** logging a set from the app shows it instantly; when its Realtime echo arrives the row is not duplicated and is not highlighted. (Outcome 5.)
- **Reconnect & catch-up:** disable network on the emulator/device, make an agent write via the local MCP client, re-enable network (or background then foreground the app) — the app reconnects automatically and shows the agent change after re-sync; backgrounding does not leak the socket. (Outcome 6.)
- **Publication coverage:** an agent write to each of `plans`, `plan_days`, `plan_exercises`, `set_logs` produces a live UI update — confirming each table is in the local Realtime publication. (Outcome 7.)
- **Layering review (code review at the gate):** no Realtime/highlight business logic in Composables; subscription and reconciliation sit in the repository, UI state exposed via `StateFlow`. (Outcome 8.)

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| RLS-authorized Realtime silently drops rows (no JWT set on the channel, or `REPLICA IDENTITY` not `FULL`), so nothing arrives. | Enabling migration sets `REPLICA IDENTITY FULL` and publication membership against the local DB; the repository applies the user JWT to the channel via the `supabase-kt` channel-auth call (`realtime.setAuth`) before subscribing; publication-coverage QA scenario on the local stack catches a missing table. |
| The enabling migration runs before Phase 1 has created the four tables, so it fails (or, worse, is written against absent tables). | The milestone declares `Depends on milestone: #1`; `/loopkit:implement` will not start this migration until Phase 1's schema is merged. Applying it locally via `supabase db reset` rebuilds the full schema deterministically. |
| The Realtime echo of the app's own write duplicates the row or wrongly highlights it. | Reconcile incoming payloads by primary key (upsert, last-write-wins); gate highlighting strictly on `source='agent'`, which the app's own echoes never carry. |
| Missed events during a dropped socket leave the UI stale (Postgres Changes does not replay backlog). | Full repository re-read of server state on every (re)subscribe and on foreground; live stream attached after the re-read completes. |
| Latency exceeds the < 1 s target under real network conditions. | Locally, latency is near-zero (loopback); subscribe narrowly (per-user, four tables only) to minimize payload and server filtering cost. The same-network production measurement is deferred with hosted deploy; if exceeded there, escalate rather than work around (no custom transport — constitution forbids a custom WS server). |
| A local MCP client refuses a plain-HTTP localhost endpoint and demands HTTPS. | Known local caveat (not a blocker): front the localhost MCP with a `cloudflared` quick-tunnel or a local Caddy localhost cert; recorded in Constraints. |
| A leaked/duplicated subscription on every recomposition drains battery or multiplies events. | Subscription owned by the repository/ViewModel and bound to lifecycle (single channel per session), never created inside a Composable. |
| JWT expiry mid-session de-authorizes the channel and the stream goes quiet. | On token refresh, re-apply the new JWT via the `supabase-kt` channel-auth call (`realtime.setAuth`) and resubscribe; treat an auth-revoked channel like a reconnect (re-read + resubscribe). |

## Decision log

All decisions below were auto-resolved under the autonomous planning mandate, 2026-06-17 — there was no human spec-acceptance gate this run; each is grounded in the vision, constitution, and the Recomp prototype.

- **Local-first dev path adopted 2026-06-17; hosted deploy deferred** — this phase is built and verified on the local Supabase stack (`supabase start`, emulator at `http://10.0.2.2:55321`, rack-MCP on localhost) with no managed project or human-delivered secret; managed Supabase + `db push`, VPS/Caddy/TLS, and Firebase/FCM stay the eventual production target, marked `(deferred — hosted deploy)`.
- Transport is Supabase Realtime **Postgres Changes** over the four user-owned training tables, not Broadcast or a custom socket — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Synced table set = `plans`, `plan_days`, `plan_exercises`, `set_logs`; `exercises`/`api_keys`/`artifacts` excluded as out of phase scope — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- The milestone depends on Phase 1 (tables + RLS), Phase 2 (agent writes), and Phase 3 (app + repository + UI); the enabling migration only enables Realtime on Phase 1's already-existing tables — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Isolation is enforced by **RLS-authorized Realtime** (channel authorized with the user JWT via the `supabase-kt` channel-auth call, `realtime.setAuth`), not by a client-side `user_id` filter — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Highlight trigger is the payload `source === 'agent'`, read directly — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Highlight is transient on the volt/lime accent (`#c8f23a`) and fades after **3 s**, re-triggering on re-touch — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- App writes are optimistic; the Realtime echo is reconciled by primary key (last-write-wins) and never highlighted (`source='app'`) — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Catch-up after disconnect is a full repository re-read of server state on (re)subscribe, not backlog replay; changes arriving while disconnected reconcile as current state (not highlighted) — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Subscription lifecycle is bound to screen/app lifecycle: subscribe on active/foreground, unsubscribe/pause on background, reconnect on return — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- The enabling migration sets `REPLICA IDENTITY FULL` and adds the four tables to the Realtime publication — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.
- Timing/reconnect tunables are named constants in the repository/ViewModel layer, never in Composables — *(auto-resolved under autonomous planning mandate, 2026-06-17)*.