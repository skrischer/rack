# Spec: Phase 5 — In-app API-key management & onboarding
> Created: 2026-06-17

A self-service in-app surface where the owner-lifter mints, lists, and revokes their own rack-MCP API keys against JWT-authenticated MCP admin endpoints — seeing each plaintext key exactly once — and an onboarding flow that walks them through pasting that key into an MCP client config.

## Outcome

- [ ] A signed-in user can create a named API key from within the Android app; the plaintext key is shown exactly once, on creation, and is never retrievable again.
- [ ] The plaintext key is displayed with an explicit copy-to-clipboard action and a one-time-only warning; dismissing or navigating away discards the plaintext irrecoverably.
- [ ] The app lists the user's API keys showing `name`, masked key prefix, `created_at`, `last_used_at` (or "never used"), and revoked state; revoked keys are visually distinct.
- [ ] A user can revoke any of their non-revoked keys; a revoked key is immediately rejected by the rack-MCP on its next tool call.
- [ ] All three operations (create, list, revoke) hit rack-MCP admin endpoints authenticated by the caller's Supabase user JWT; the MCP resolves identity from the JWT alone and never reads a `user_id`/account selector from the request body.
- [ ] A cross-user attempt — calling an admin endpoint with user A's JWT to list or revoke user B's keys — returns no data and mutates nothing (verified by test).
- [ ] An onboarding flow guides a first-time user from "no keys" to a working MCP-client connection: it shows the hosted MCP endpoint URL, the freshly-minted key, and a ready-to-paste MCP client config snippet, with a copy action per field.
- [ ] The plaintext key is never written to the Supabase `api_keys` row, never logged, and never persisted in the Android client beyond the in-memory lifetime of the reveal screen.
- [ ] The `api_keys` table carries a `key_prefix` (non-secret display fragment) and a `revoked_at` column, added by a `/supabase/migrations` migration in this phase that alters — not re-creates — the Phase 1 table and leaves its RLS policy unchanged.
- [ ] The key-management and onboarding screens render in the Recomp visual language (dark theme, volt accent `#c8f23a`, Archivo + Spline Sans Mono, mono for key/code strings).
- [ ] `make verify` and `make build` pass on the merged branch.

## Scope

### In scope

- A `/supabase/migrations` migration that **alters** the existing Phase 1 `api_keys` table — adding `key_prefix` (non-secret display fragment, stored because the plaintext is irretrievable so the list cannot derive a fragment at read time) and `revoked_at` (timestamp set on soft-delete) — and leaves the Phase 1 RLS policy on `user_id = auth.uid()` unchanged. Why: the architecture data model lists `api_keys` as `user_id, hashed key, name, last_used_at, revoked`; the list-display and soft-delete-revoke contracts this phase needs require two more columns, and the constitution requires schema changes to land in `/supabase/migrations`.
- Android key-management screen: list keys, create-with-name, reveal-once, copy, revoke — all through the repository layer, no Supabase or HTTP calls inside Composables.
- Android onboarding flow: first-run guidance from zero keys to a connected MCP client (endpoint URL + key + paste-ready config snippet).
- rack-MCP admin endpoints for **list** and **revoke**, authenticated by the Supabase user JWT, extending the **create/mint** endpoint delivered in [spec-rack-mcp.md](spec-rack-mcp.md). Why: Phase 2 minted keys; the in-app lifecycle (see, list, revoke) is this phase's job. The create/mint endpoint is also extended here to populate the new `key_prefix` column and return it in the create response.
- JWT verification + user resolution on the admin endpoints, and the `api_keys` row writes/reads performed as the resolved user under RLS.
- Masking/prefix display contract so the list can show a recognizable fragment without exposing the secret.

### Out of scope

- The `api_keys` table's **initial** creation, the key hashing scheme, and the base RLS policy — created in [spec-foundation.md](spec-foundation.md) (Phase 1); the MCP key-mint endpoint's initial implementation — delivered in [spec-rack-mcp.md](spec-rack-mcp.md) (Phase 2). This phase consumes those and **alters** the table (adds two columns) and **extends** the mint endpoint; it does not re-create either.
- Tool-call auth (resolving an API key → user on a tool request) — that is the MCP's runtime auth from [spec-rack-mcp.md](spec-rack-mcp.md); here we only assert that a revoked key is rejected.
- Key rotation as a distinct operation — covered functionally by create + revoke; no dedicated rotate endpoint.
- Realtime highlighting of key changes — keys are user-authored from one surface; the live-sync loop ([spec-live-sync.md](spec-live-sync.md)) is not extended to `api_keys`.
- Settings/profile screen housing — that is [spec-settings.md](spec-settings.md); this phase ships the key-management surface reachable from the app, wherever Phase 12 later docks it.
- Multi-MCP-client per-client config presets beyond a single generic Streamable-HTTP snippet.

## Constraints

Bound by `docs/constitution.md` and `docs/architecture.md`. Load-bearing for this phase: the MCP never writes user data with the service-role key and never accepts `user_id` from the caller — it acts as the resolved user, so RLS governs every `api_keys` read/write; the `api_keys` table already carries its RLS policy on `user_id = auth.uid()` from Phase 1, and this phase's column-adding migration leaves that policy unchanged (every user-data table keeps RLS — a table without it is a bug); schema changes ship in `/supabase/migrations` (constitution: new tables/policies live there; here it is an `ALTER`, RLS untouched); TypeScript strict with no `any`/`as unknown as`/`@ts-ignore`, every admin-endpoint input Zod-validated; secrets (the key hash) live only in the database server-side, the plaintext never persists; the Android client uses the anon key + user JWT only and never holds the service-role key; no business logic in Composables, UI state via ViewModel + StateFlow, all access behind a repository; functions ≤ 50 lines, files ≤ 400 lines; Conventional Commits.

## Prior art

- [Remote MCP authentication (multi-user, per-user API key)](../prior-art.md#remote-mcp-authentication-multi-user-per-user-api-key) — directly normative: a static per-user API key issued in-app, sent server-side, key → user mapping, keys never in client code, and a deliberate rejection of OAuth 2.1/PKCE as over-engineered for a friends-and-family circle. This phase implements the in-app issuance/lifecycle half of that pattern.
- [Programmable agent access (MCP write-through to fitness data)](../prior-art.md#programmable-agent-access-mcp-write-through-to-fitness-data) — the wger-mcp's env-var single API key is the anti-pattern; Rack's per-user, rotatable, in-app-managed keys are the inversion this phase delivers.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — only for the Recomp visual conventions the new screens must match; no logging logic here.

## Human prerequisites

- none. This phase consumes infrastructure already provisioned by its dependencies: the managed Supabase project + JWT verification material and the hosted rack-MCP endpoint URL (Phase 2), and the `api_keys` table + RLS + the `/android`/`/mcp`/`/supabase` scaffold (Phase 1). No new secret, host, domain, or external account is introduced. The `api_keys` schema delta this phase needs is delivered by its own in-scope migration, not by a human.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| This phase adds `key_prefix` and `revoked_at` to `api_keys` via a `/supabase/migrations` migration that alters — not re-creates — the Phase 1 table, leaving the RLS policy unchanged. | The architecture data model lists `api_keys` as `user_id, hashed key, name, last_used_at, revoked`; the list-display contract needs a stored non-secret prefix (plaintext is irretrievable, so a fragment cannot be derived at read time) and soft-delete revoke needs `revoked_at`. The constitution requires schema changes in `/supabase/migrations` and an unchanged owner RLS policy; an `ALTER … ADD COLUMN` satisfies both without re-creating Phase 1's table. | 2026-06-17 |
| List and revoke are new rack-MCP admin endpoints, JWT-authenticated, alongside the Phase 2 mint endpoint — not direct Supabase Postgrest calls from the app. | Architecture flow 4 routes key creation through the MCP admin endpoint; keeping list/revoke on the same JWT-authenticated admin surface concentrates the hashing/masking contract server-side and keeps one auth path. The app talks to the MCP only for key management (architecture "Boundaries"). | 2026-06-17 |
| The admin endpoints resolve the user from the Supabase JWT and perform `api_keys` reads/writes as that resolved user under RLS — never service-role, never a body `user_id`. | Constitution: MCP never writes user data with service-role and never accepts `user_id`; RLS on `user_id = auth.uid()` then enforces isolation in the DB, satisfying the cross-user test for free. | 2026-06-17 |
| Plaintext key returned once in the create response only; the DB stores a hash plus the new `key_prefix` (a short non-secret display fragment); the list shows the prefix, never the secret. | Constitution: API-key hashes live server-side only, plaintext never persists. A stored prefix lets the user recognize "which key is which" without weakening the secret; it must be a column because the plaintext is gone after creation. | 2026-06-17 |
| Revoke is a soft-delete: set `revoked = true` and `revoked_at = now()`, the row is retained. | The data model's `api_keys.revoked` flag is a boolean state, not a row deletion; retaining the row preserves `last_used_at` audit context and keeps the list's "revoked" rendering possible; `revoked_at` (added by this phase's migration) records when. | 2026-06-17 |
| Onboarding shows the MCP endpoint URL + minted key + a single generic Streamable-HTTP MCP-client config snippet with per-field copy; no per-client (Claude Desktop vs other) presets. | Vision targets technically-minded lifters already using MCP clients; one correct, copyable Streamable-HTTP config is proportionate. Multiple presets is scope the friends-and-family circle does not need. | 2026-06-17 |
| The Android client reads the rack-MCP base URL from build config (the same value Phase 2 deploys behind Caddy), not from a user-entered field. | Secrets/endpoints stay out of user input; one hosted endpoint per deployment. The app already ships an anon-key/URL build config; the MCP URL joins it. | 2026-06-17 |
| Key `name` is required, trimmed, 1–64 chars, Zod-validated at the MCP boundary; duplicate names are allowed. | A human-readable label is the only way to tell keys apart in the list once plaintext is gone; uniqueness adds friction without value for a single owner. | 2026-06-17 |
| Reveal-once is enforced client-side by holding the plaintext only in ViewModel in-memory state, cleared on navigation away from the reveal screen; no re-fetch path exists because the server cannot return it. | Server-side irretrievability (hash-only storage) is the real guarantee; the client simply must not stash it. StateFlow holding transient plaintext, cleared on leave, matches the no-logic-in-Composables rule. | 2026-06-17 |
| `last_used_at` is surfaced read-only in the app; it is stamped by the MCP's tool-call auth path from Phase 2, not written by this phase. | Separation of concerns: this phase displays the audit field; the runtime auth that touches it belongs to the rack-MCP spec. | 2026-06-17 |
| Cross-user isolation is asserted by an automated MCP-level test (A's JWT cannot list/revoke B's keys), since the project's Test command is still `none yet` for the app. | The success criterion "per-user isolation, verified by a cross-user access test" is testable at the MCP boundary now; the app-side checks fall to the human QA gate. | 2026-06-17 |
| This milestone depends on Phase 1 (the `api_keys` table + RLS and the `/android`/`/mcp`/`/supabase` scaffold) and Phase 3 (the Android auth + repository layer) in addition to Phase 2 (the mint endpoint + hosted MCP). The `Depends on milestone` lines are written against the milestone numbers actually created at planning time, not assumed equal to phase numbers. | The admin surface reads/writes the Phase 1 table under its Phase 1 RLS and alters its schema; the Android work builds on the Phase 3 repository/ViewModel pattern and the Phase 1 scaffold. Declaring Phase 1 explicitly makes the implement loop's "workable once depended-on milestones are closed" gate block on the table/RLS/scaffold this phase consumes, rather than relying on a transitive Phase 2→Phase 1 edge this spec cannot guarantee. | 2026-06-17 |

## Tracking

- Milestone: **Phase 5: In-app API-key management & onboarding**. Milestone description carries a `Depends on milestone: #<n>` line for **each** of Phase 1 (foundation: `api_keys` table + RLS + scaffold), Phase 2 (mint endpoint + hosted MCP), and Phase 3 (Android auth + repository layer). The `#<n>` values are resolved at planning time against the milestone numbers actually created on GitHub — not assumed to equal the phase numbers — so the gate blocks on the real depended-on milestones.
- Issues: one GitHub issue per implementable step below, each carrying `Goal:`, `Acceptance:`, an optional `Depends on:` line, and `Spec: docs/specs/spec-api-key-management.md`. No step list lives in this spec — the issues own the steps.

## Verification

Machine gates: `make verify` (lint + typecheck + MCP tests + Android static checks) green per iteration, and `make build` (MCP build + `assembleDebug`) before the app-affecting PR.

Migration checks (run as part of `make verify` once the migration lands):

- The migration alters the existing `api_keys` table to add `key_prefix` and `revoked_at`; it does not drop or re-create the table, and it does not touch the Phase 1 RLS policy on `user_id = auth.uid()`.
- `api_keys` still has RLS enabled with the owner policy after the migration.

Automated behavioral checks (added to the MCP test suite — Test is `none yet` for the app, so these run at the MCP boundary):

- Create with a valid JWT returns a plaintext key once plus its `key_prefix`, and an `api_keys` row whose stored secret value is a hash, not the plaintext.
- A second read/list never returns the plaintext for an existing key; it returns the stored `key_prefix` only.
- List with user A's JWT returns only A's keys; revoke targeting B's key id with A's JWT mutates nothing and reports not-found/forbidden.
- Revoking a key flips `revoked` to true, sets `revoked_at`, and a subsequent tool call presenting that key is rejected.
- Every admin endpoint rejects a request with a missing/invalid/expired JWT.
- Zod rejects an empty or > 64-char name and any body containing a `user_id` field is ignored (identity comes only from the JWT).

Human QA-gate scenarios (app-facing, run at the milestone gate):

- Sign in, open key management with zero keys → onboarding/empty state appears and explains the next step.
- Create a key named e.g. "Claude Desktop" → plaintext shown once with a copy button and a "you won't see this again" warning; copy puts it on the clipboard.
- Leave and return to the screen → the new key appears in the list by name + prefix + "never used"; plaintext is gone.
- Complete onboarding → endpoint URL, key, and a paste-ready MCP config snippet are each copyable; pasting into a real MCP client yields a working connection that can read the user's data.
- Revoke a key → it renders as revoked and the connected client's next call fails.
- Visual pass: screens match the Recomp dark theme, volt accent, and mono treatment for key/code strings.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| The `key_prefix`/`revoked_at` columns are missing because no step owns the schema change. | A dedicated migration issue (ordered ahead of the admin-endpoint work) adds both columns in `/supabase/migrations`; the admin-endpoint and list-display issues depend on it; a migration check in `make verify` asserts the columns exist and RLS is unchanged. |
| Plaintext key leaks via logs, crash reports, or client persistence. | Server returns plaintext only in the create response; never logged at the MCP; client holds it in transient ViewModel state cleared on navigation; an explicit acceptance item and review check guard this. |
| App accidentally hits Supabase Postgrest directly for `api_keys`, bypassing the MCP's hashing/masking contract. | Decision fixes the single auth path through the JWT-authenticated MCP admin endpoints; repository layer exposes only those calls; review enforces no direct `api_keys` Postgrest access from the app. |
| Cross-user access through a forged or body-supplied `user_id`. | Identity is resolved from the JWT only; Zod ignores any `user_id` in the body; RLS on `user_id = auth.uid()` is the DB-level backstop; an automated cross-user test asserts it. |
| Revoked key keeps working because revocation isn't checked on the tool-call path. | The tool-call auth check (Phase 2) must reject `revoked = true`; this phase's test asserts a revoked key is rejected on the next call, catching a regression in either spec. |
| MCP base URL misconfigured in the Android build, breaking create/list/revoke silently. | URL comes from build config (one source); a smoke connection in QA confirms the onboarding snippet actually connects. |
| Onboarding snippet is subtly wrong for real MCP clients, so users can't connect. | The QA scenario requires pasting the generated config into a real client and getting a working read; the snippet is generated from the same endpoint constant the app uses. |
| Milestone runs before its real prerequisites are closed because milestone numbers were assumed equal to phase numbers. | The milestone declares an explicit `Depends on milestone` edge to Phases 1, 2, and 3, with the `#<n>` resolved against the actually-created milestones at planning time, so the implement loop's "workable once depended-on milestones are closed" gate blocks correctly. |

## Decision log

- Added a `/supabase/migrations` migration that alters (not re-creates) the Phase 1 `api_keys` table to add `key_prefix` and `revoked_at`, RLS unchanged. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Reused the Phase 2 mint endpoint and added list/revoke on the same JWT-authenticated admin surface rather than app-direct Postgrest; extended mint to populate/return `key_prefix`. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Admin endpoints act as the resolved user under RLS, never service-role, never a body `user_id`. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Plaintext returned once; DB stores hash + the non-secret `key_prefix` column; list shows the prefix. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Revoke is a soft-delete (`revoked = true` + `revoked_at = now()`), row retained for audit/display. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Onboarding offers one generic Streamable-HTTP config snippet, not per-client presets. (auto-resolved under autonomous planning mandate, 2026-06-17)
- MCP base URL sourced from Android build config, not user input. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Key `name` required, trimmed, 1–64 chars, duplicates allowed, Zod-validated. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Reveal-once enforced by transient in-memory ViewModel state cleared on navigation; server irretrievability is the real guarantee. (auto-resolved under autonomous planning mandate, 2026-06-17)
- `last_used_at` shown read-only; stamped by the Phase 2 tool-call auth path, not written here. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Cross-user isolation asserted by an automated MCP-level test; app-side checks fall to the human QA gate while the app Test command is `none yet`. (auto-resolved under autonomous planning mandate, 2026-06-17)
- Declared explicit `Depends on milestone` edges to Phases 1, 2, and 3 (not just Phase 2), with `#<n>` resolved at planning time against actually-created milestones rather than assuming number == phase. (auto-resolved under autonomous planning mandate, 2026-06-17)