# Constitution

> Normative and binding. Every principle must be verifiable and specific.
> Keep to ~1 page; this file is permanently loaded via CLAUDE.md. No status
> marker — foundation docs carry none.

## Tech stack

| Area | Choice | Rationale |
| ---- | ------ | --------- |
| Android app | Kotlin + Jetpack Compose, MVVM, Coroutines/Flow | The native standard; Compose fits the opinionated Recomp UI |
| App ↔ backend | `supabase-kt` (Auth, Postgrest, Realtime, Storage) + light local cache for unsynced logs | App talks to Supabase directly; offline-resilient logging |
| rack-MCP | Node + TypeScript, `@modelcontextprotocol/sdk`, Streamable HTTP transport | Most mature MCP ecosystem; matches strict-TS rules; remote multi-user |
| MCP ↔ data | `supabase-js`, acting as the resolved user (user-scoped token) | Writes flow through RLS like any app write |
| Database | Supabase (managed PostgreSQL) | Already provisioned; relational training data |
| Auth | Supabase Auth (email/password) for the app; per-user opaque API key (hashed) for the MCP | One identity system; API key → Supabase user |
| Realtime | Supabase Realtime (per-user row subscriptions) | IS the socket layer for live edit highlighting; no custom WS server |
| Isolation | Postgres Row Level Security on `auth.uid()` | Per-user isolation enforced in the DB, not app code |
| Hosting | Docker Compose on a VPS for the rack-MCP + Caddy (auto-TLS); Supabase is managed | Only the MCP is self-hosted; MCP needs HTTPS |
| Validation | Zod at the MCP boundary; DB constraints + RLS at the data boundary | Strict, shared input schemas |

## Architecture principles

- Every user-data table has RLS enabled with an owner policy on `auth.uid()`; a
  table without RLS is a bug.
- The rack-MCP never writes user data with the service-role key — it always
  acts as the resolved user (a user-scoped token) so RLS applies automatically.
- No MCP tool accepts a `user_id` or account selector from the caller; the API
  key is the only identity source.
- Every mutation sets `source` ('app' | 'agent') and `updated_at` so the app
  can highlight agent edits.
- Plan prescription is structured and typed — sets, rep range, RIR range,
  rest, and explicit exercise groups are first-class columns shared by the app
  and the MCP; free-text prescription is never the source of truth.
- An MCP client can author a whole multi-row structure (a full plan) in one
  transactional call; no caller hand-chains parent ids across round-trips.
- TypeScript strict, no `any`; every MCP tool input is Zod-validated.
- Android: no business logic in Composables; UI state via ViewModel +
  StateFlow; all Supabase access behind a repository layer.
- Functions ≤ 50 lines, files ≤ 400 lines (guideline, enforced in review).
- Secrets (service-role key, API-key hashes) live only server-side in the MCP;
  the app uses the anon key + a user JWT only.

## Conventions

- Code, comments, identifiers, logs, and commit messages in English.
- Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`).
- TypeScript: ESLint + Prettier; Zod at all external boundaries.
- Kotlin: ktlint / detekt; idiomatic Compose.
- Database columns `snake_case`; TypeScript `camelCase`.
- Repo layout: `/android` (Gradle app), `/mcp` (Node/TS rack-MCP),
  `/supabase` (migrations, RLS policies, seed), `/docs`.

## Quality gates

- TypeScript: typecheck + lint + tests green before merge.
- Android: `assembleDebug` builds.
- Every new user-data table ships its RLS policy in the same migration.
- No secrets (service-role key, API keys) committed to the repo.

## Don'ts

- No AGPL code from wger — only its CC-BY-SA exercise data, with attribution.
- No `any`, no `as unknown as X`, no `@ts-ignore`/`@ts-expect-error` without a
  justifying comment.
- No CRDT library — the single-owner concurrency model does not need it.
- No service-role writes to user-owned tables from the MCP.
- No `user_id` accepted from MCP callers.
- No service-role key or secret shipped in the Android client.
- No `supabase db reset` or destructive reset against the shared Supabase
  project.
