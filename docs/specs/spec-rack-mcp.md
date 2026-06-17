# Spec: Phase 2 — rack-MCP: auth + tools + hosting

> Created: 2026-06-17

A hosted Node + TypeScript MCP server over Streamable HTTP that resolves a per-user opaque API key to a Supabase user, acts as that user via a user-scoped token (never service-role for user data), exposes Zod-validated read/write tools over plans, days, exercises, sets, and logs, mints API keys behind a JWT-authenticated admin endpoint, and ships as Docker Compose + Caddy auto-TLS on the VPS.

## Outcome

- [ ] An MCP client configured with the rack-MCP Streamable HTTP URL and a valid per-user API key connects and lists the available tools.
- [ ] A request with a missing, malformed, revoked, or unknown API key is rejected (401) and reaches no tool logic.
- [ ] Every tool call resolves the API key to exactly one Supabase user and operates only on that user's data; no tool accepts a `user_id` or any account selector from the caller.
- [ ] The server acts as the resolved user via a user-scoped Supabase token so RLS applies to every read and write; the service-role key is never used for any user-data read or write.
- [ ] Read tools return the caller's plans, a plan's days, a day's exercises, set logs (filterable by exercise and date range), and a search over the global exercise catalog — each shaped as documented MCP tool results.
- [ ] Write tools create/update/delete plans, plan days, plan exercises, and set logs for the resolved user; every successful mutation persists `source='agent'` and a fresh `updated_at` (enforced by the DB, inherited by the MCP).
- [ ] Every tool input is parsed with a Zod schema at the boundary; invalid input is rejected with a structured validation error and no DB write occurs.
- [ ] A JWT-authenticated admin endpoint (called with a Supabase user JWT) mints a new API key: it stores only the key's hash in `api_keys` and returns the plaintext key exactly once; the plaintext is never persisted or logged.
- [ ] An API key minted for user A cannot read or write user B's data (cross-user isolation holds because RLS is enforced under the resolved user, verified by a cross-user check).
- [ ] `make verify` (lint + typecheck + MCP tests) passes; the MCP image builds and runs under Docker Compose behind Caddy with auto-TLS on the configured domain, reachable over HTTPS.
- [ ] No secret (service-role key, JWT secret, API-key pepper, plaintext API keys) is committed to the repo; all live only in the server-side `.env`.

## Scope

### In scope

- The `/mcp` Node + TypeScript project: `@modelcontextprotocol/sdk` server over the Streamable HTTP transport, started from `/mcp/src`.
- API-key authentication middleware: extract the bearer/header key, hash-and-match against `api_keys`, reject unknown/revoked keys, resolve to a `user_id`, and update `last_used_at`.
- Acting-as-user: mint a short-lived user-scoped Supabase access token (signed with `SUPABASE_JWT_SECRET`, `sub = user_id`, `role = authenticated`) and create a per-request `supabase-js` client carrying it, so all data access runs under RLS as that user.
- Read tools over: plans, plan days, plan exercises, set logs (with exercise + date-range filters), and exercise-catalog search.
- Write tools over: plans, plan days, plan exercises, set logs (create/update/delete), each setting `source='agent'`.
- Zod schemas for every tool input and the admin-endpoint payload; one shared validation pattern at the boundary.
- A JWT-authenticated admin HTTP endpoint that mints an API key: generate a random opaque key, store its hash (peppered) + name + `user_id` in `api_keys`, return plaintext once.
- The `api_keys` mint/resolve contract: any column shape this phase needs beyond Phase 1's table (e.g. hash format, `name`, `last_used_at`, `revoked`) ships as a migration in `/supabase/migrations` with its RLS policy in the same migration.
- Deployment: `Dockerfile` for the MCP, a `docker-compose.yml` (MCP + Caddy), and a `Caddyfile` for auto-TLS reverse proxy to the MCP; `.env.example` already carries the required keys.
- MCP unit/integration tests covering auth resolution, RLS scoping, Zod rejection, and the mint endpoint (these become the project's first `Test` command — see Verification).

### Out of scope

- Artifact tools and Supabase Storage — that is Phase 6 (`docs/specs/spec-visualization-artifacts.md`); the `artifacts` table is not touched here.
- The Android client, its repository layer, and the in-app key-management UI that calls the admin endpoint — that is Phase 3 (`docs/specs/spec-android-app.md`) and Phase 5 (`docs/specs/spec-api-key-management.md`). This phase exposes the endpoint; the app consuming it comes later.
- Realtime subscription / edit-highlighting consumption — Phase 4 (`docs/specs/spec-live-sync.md`). This phase only guarantees `source='agent'` + `updated_at` are written, which Phase 4 reads.
- The database schema, RLS policies, and wger seed for plans/days/exercises/sets/logs — owned by Phase 1 (`docs/specs/spec-foundation.md`); this phase consumes them and only adds `api_keys`-shape migrations it specifically needs.
- OAuth 2.1 / PKCE and any public multi-tenant auth — explicitly rejected for a friends-and-family circle (see Prior art).

## Constraints

Bound by `docs/constitution.md` and `CLAUDE.md`. The load-bearing ones for this phase: the MCP never writes user data with the service-role key and always acts as the resolved user; no tool accepts `user_id` from the caller (the API key is the only identity source); every mutation sets `source` + `updated_at`; TypeScript strict with no `any` / no `as unknown as X` / no unjustified `@ts-ignore`; Zod at the MCP boundary; every user-owned table ships its RLS policy (`user_id = auth.uid()`) in the same migration; secrets server-side only; functions ≤ 50 lines, files ≤ 400 lines (guideline); Conventional Commits; repo layout `/mcp` and `/supabase`. Quality gates from the constitution apply (typecheck + lint + tests green before merge; no secrets committed).

## Prior art

- [Programmable agent access (MCP write-through to fitness data)](../prior-art.md#programmable-agent-access-mcp-write-through-to-fitness-data) — `Juxsta/wger-mcp` is the closest prior art; ADOPT its tool granularity (search exercises + CRUD over routines/days/exercises/sets, ~12 tools as a sane surface), AVOID its stdio/single-user/env-var-single-key/no-push ceiling — Rack inverts it to a hosted, per-user-keyed remote MCP.
- [Remote MCP authentication (multi-user, per-user API key)](../prior-art.md#remote-mcp-authentication-multi-user-per-user-api-key) — directly informs the auth design: Streamable HTTP transport, a static per-user API key mapping key → user, keys never in client code; AVOID full OAuth 2.1 + PKCE as over-engineered for this circle.
- [Open exercise data & training domain model](../prior-art.md#open-exercise-data--training-domain-model) — relevant only as the source/shape of the catalog the exercise-search read tool queries; the data and attribution are Phase 1's, this phase just reads them.

## Human prerequisites

- [ ] Managed Supabase project provisioned (Phase 1) with its URL, anon key, service-role key, and project JWT secret available in the deployment `.env` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_JWT_SECRET`).
- [ ] A VPS host (SSH access, Docker + Docker Compose installed) to run the rack-MCP container and Caddy.
- [ ] A domain or subdomain with a DNS A/AAAA record pointing at the VPS, and ports 80 + 443 open, so Caddy can obtain auto-TLS certificates (set `MCP_PUBLIC_URL` accordingly).
- [ ] A generated long random `API_KEY_PEPPER` value placed in the deployment `.env`.
- [ ] At least one Supabase Auth user (email/password) created in the project so the JWT-authenticated admin key-mint endpoint can be exercised end-to-end.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Act-as-user by minting a short-lived user-scoped JWT signed with `SUPABASE_JWT_SECRET` (`sub = user_id`, `role = authenticated`), used by a per-request `supabase-js` client | Makes RLS (`user_id = auth.uid()`) apply automatically to every read/write without the service-role key; `.env.example` already provisions `SUPABASE_JWT_SECRET` for exactly this; satisfies the constitution's "never service-role for user data, always act as the resolved user". | 2026-06-17 |
| API key format: opaque random secret with a non-secret lookup prefix (e.g. `rack_` + random id) plus a secret portion; store only a peppered hash of the secret portion | A prefix lets the server locate the candidate row without table-scanning every hash; peppering (`API_KEY_PEPPER`, server-side only) hardens against DB-dump offline attacks; plaintext returned once, never stored. | 2026-06-17 |
| Hash algorithm: SHA-256 over `pepper + secret`, constant-time compared | API keys are high-entropy random strings (not low-entropy passwords), so a fast keyed hash is sufficient and avoids a bcrypt/argon2 dependency; pepper provides the secret-at-rest protection. Keeps dependencies minimal per the constitution. | 2026-06-17 |
| API key transmitted as `Authorization: Bearer <key>` on the Streamable HTTP request | Standard, what MCP remote clients send; one header to read; clearly distinct from the user JWT used by the admin endpoint. | 2026-06-17 |
| Admin key-mint endpoint authenticated by the Supabase user JWT (verified against `SUPABASE_JWT_SECRET`), not by an API key | The app holds a user JWT, not an API key, when a user creates their first key (bootstrapping); the JWT's `sub` is the only identity the mint trusts, so the endpoint also takes no `user_id` from the caller. | 2026-06-17 |
| Tool surface scoped to plans, plan_days, plan_exercises, set_logs (read+write CRUD) plus exercise-catalog search (read) and "list my plans"/"get plan tree" reads | Matches the phase intent and the wger-mcp ~12-tool reference; excludes artifacts (Phase 6); covers everything an agent needs to author a full multi-day plan and log/inspect sets. | 2026-06-17 |
| `source='agent'` and `updated_at` set by a DB trigger (Phase 1) where possible; the MCP passes `source='agent'` explicitly on insert/update as the authoritative value | Architecture mandates DB-level invariants both adapters inherit; the MCP still sets `source='agent'` so app vs agent edits are distinguishable for Phase 4 highlighting even if a trigger defaults it. | 2026-06-17 |
| Streamable HTTP transport via `@modelcontextprotocol/sdk`, single Node process, stateless per-request auth | The mature, spec-current remote transport; per-request key resolution keeps the server stateless and horizontally simple for one VPS. | 2026-06-17 |
| Deployment = Docker Compose with two services (rack-MCP, Caddy); Caddy terminates TLS (auto-HTTPS via Let's Encrypt) and reverse-proxies to the MCP on `MCP_PORT` | Constitution's hosting choice verbatim; only the MCP is self-hosted and it needs HTTPS for a remote MCP; Caddy auto-TLS is the lowest-ceremony option. | 2026-06-17 |
| `delete` tools perform hard deletes scoped by RLS (the row's `user_id` must match); no soft-delete/tombstone in this phase | Single-owner model, no CRDT, no undo requirement in scope; RLS guarantees a delete can only hit the caller's own rows. Phase 4 sync is server-authoritative and tolerates deletes. | 2026-06-17 |
| `last_used_at` updated on each successful auth resolution; `revoked` keys rejected at auth time | Gives Phase 5 key-management meaningful "last used" data and immediate revocation semantics with no extra mechanism. | 2026-06-17 |
| Error contract: auth failures → HTTP 401 before any tool runs; Zod failures → MCP tool error result naming the invalid field; RLS/not-found → empty result or a clear "not found / not yours" tool error, never a leak of another user's row existence | Keeps the boundary's failure modes predictable for the agent and prevents cross-user existence leaks. | 2026-06-17 |
| MCP tests (Vitest) become the project's first `Test` command and are wired into `make verify`; `docs/workflow.md` Test line is updated from "none yet" accordingly | Workflow contract says the first real test command is established when one exists; this phase produces it, so it should register it. | 2026-06-17 |

## Tracking

Full-spec track. Milestone: **Phase 2: rack-MCP: auth + tools + hosting** (`Depends on milestone: #1`). Issues below are one-per-step, dependency-ordered; each references this spec at `docs/specs/spec-rack-mcp.md`. No step list lives here — the issues own the steps.

## Verification

Machine gates (from `docs/workflow.md`):

- `make verify` — lint + typecheck (TypeScript strict, no `any`) + MCP tests, green.
- `make build` — MCP image builds.
- `Test`: this phase introduces the MCP test suite (`cd mcp && npm test`), which `make verify` runs; update the `Test` line in `docs/workflow.md` from "none yet".

Behavioral checks a human/QA runs at the milestone gate (the MCP/backend QA default is review + these scripted checks):

1. **Connect:** point an MCP client (or the MCP inspector) at the deployed HTTPS URL with a valid key → tools list returns; an invalid/missing/revoked key → 401, no tools.
2. **Read scoping:** with user A's key, list plans / get a plan tree / list set logs → only A's rows; search the exercise catalog → catalog rows returned.
3. **Write + source:** with A's key, create a plan, a day, an exercise, and a set log → rows appear under A with `source='agent'` and a fresh `updated_at`; update one → `updated_at` advances, `source` stays `agent`; delete one → gone.
4. **No `user_id` acceptance:** confirm no tool schema exposes a `user_id`/account field; passing extra identity fields is ignored or rejected by Zod, never honored.
5. **Service-role audit:** grep/inspect that user-data reads/writes go through the user-scoped client, and the service-role client is used (if at all) only for the auth-resolution lookup against `api_keys`, never for plan/day/exercise/set data.
6. **Zod rejection:** send a malformed tool input (wrong type, missing required field, out-of-range reps) → structured validation error, no DB row created.
7. **Mint endpoint:** call the admin endpoint with a valid user JWT → a plaintext key returned once + a hashed row in `api_keys`; call with no/invalid JWT → 401; confirm the plaintext is absent from server logs and the DB.
8. **Cross-user isolation:** mint a key for B; with A's key attempt to read/update/delete a B-owned row by id → not found / denied; repeat inverted → still isolated.
9. **TLS/deploy:** `docker compose up` on the VPS → Caddy serves the domain over HTTPS with a valid cert; HTTP redirects to HTTPS; the MCP is not reachable except through Caddy.
10. **Secret hygiene:** no service-role key, JWT secret, pepper, or plaintext key in the repo or in committed compose/Caddy files (only in `.env`).

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| The MCP accidentally uses the service-role client for user data, silently bypassing RLS | Single chokepoint: only the auth-resolution lookup may use a service-role/anon admin client; all tool handlers receive only the user-scoped client via a per-request context; a verification grep + test asserts no service-role data access. |
| A tool exposes or honors a caller-supplied `user_id`, breaking isolation | Zod schemas omit any identity field by construction; identity comes solely from the resolved context; check 4 + cross-user test 8 guard it. |
| Minted plaintext key leaks via logs or persistence | Plaintext is generated, returned in the response, and never logged or stored; only the peppered hash is persisted; a log-grep is part of QA check 7. |
| `SUPABASE_JWT_SECRET` mismatch makes minted user tokens fail RLS or be rejected by Supabase | Pin the token claims (`sub`, `role: authenticated`, `aud`, short `exp`) to what Supabase expects; an integration test resolves a key and performs a real round-trip read/write under RLS. |
| Caddy fails to obtain a cert (DNS not propagated, ports closed) | Documented human prerequisites (DNS A record, ports 80/443, `MCP_PUBLIC_URL`); deploy step verifies cert issuance before sign-off. |
| Hard deletes remove data an agent did not intend to lose | Deletes are RLS-scoped to the caller's own rows and require an explicit id; out of scope to add undo this phase; the single-owner model accepts last-write-wins per the constitution (no CRDT). |
| Phase 1's `api_keys` table shape differs from what mint/resolve needs | This phase ships any needed `api_keys` migration (with its RLS policy in the same migration) rather than mutating Phase 1's; depends-on-milestone #1 guarantees the base table exists first. |

## Decision log

All resolved autonomously under the autonomous planning mandate, 2026-06-17 — no human spec-acceptance gate this run.

- **Act-as-user via a self-minted user-scoped JWT** signed with `SUPABASE_JWT_SECRET` (`sub`/`role`/short `exp`), used by a per-request `supabase-js` client, so RLS applies and the service-role key is never used for user data. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **API key = non-secret prefix + secret portion; store only a peppered SHA-256 hash, constant-time compared;** plaintext returned once. Fast keyed hash chosen over bcrypt/argon2 because keys are high-entropy, keeping dependencies minimal. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Key transport = `Authorization: Bearer` on the Streamable HTTP request;** the admin mint endpoint instead uses the Supabase **user JWT** (its `sub` is the only identity), so neither path accepts a `user_id`. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Tool surface = CRUD over plans/plan_days/plan_exercises/set_logs + exercise-catalog search + plan-tree/list reads;** artifacts excluded (Phase 6). Mirrors the wger-mcp ~12-tool reference. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **`source='agent'` set explicitly by the MCP on every mutation; `updated_at` DB-managed;** distinguishes agent edits for Phase 4 highlighting. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Hard deletes, RLS-scoped, no soft-delete/undo this phase;** single-owner, no-CRDT model. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Deployment = Docker Compose (rack-MCP + Caddy), Caddy auto-TLS reverse proxy on `MCP_PUBLIC_URL`;** only the MCP is self-hosted, HTTPS required for remote MCP. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Error contract:** auth → 401 pre-tool; Zod → field-named tool error, no write; not-yours/not-found → no cross-user existence leak. (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Vitest suite becomes the project's first `Test` command, wired into `make verify`, and `docs/workflow.md` is updated** from "none yet". (auto-resolved under autonomous planning mandate, 2026-06-17)
- **Any `api_keys` shape this phase needs beyond Phase 1 ships as a migration with its RLS policy in the same migration,** rather than editing Phase 1's migration. (auto-resolved under autonomous planning mandate, 2026-06-17)