# Architecture

> Structural, living document — the most volatile artifact. Update whenever a
> change alters components, boundaries, or flows. Greenfield: this is a seed.

## Component map

| Component | Responsibility |
| --------- | -------------- |
| Android app (Kotlin/Compose) | UI: plan/day/exercise view, set logging, history, API-key management. Repository layer over `supabase-kt`. Realtime subscription that highlights agent edits. |
| Supabase (managed) | Postgres + Auth + Realtime + RLS + Storage. Owns the schema, policies, and seed data. |
| rack-MCP (Node/TS, on the VPS) | MCP server (Streamable HTTP). Read + write tools. API-key → user auth; acts as the resolved user via `supabase-js`. Zod-validates inputs. Admin endpoint that mints API keys. |
| Caddy (VPS) | TLS reverse proxy in front of the rack-MCP (HTTPS for remote MCP). |
| Recomp design system | Normative visual language: tokens (`docs/design-tokens.md`), encoded once in the Compose Recomp theme; every UI phase consumes the theme, never ad-hoc colors. |

## Data model (seed)

- `exercises` — global catalog seeded from wger (CC-BY-SA): name, muscles,
  equipment, category, `license`, `license_author` (attribution). Public read.
- `plans` — user-owned: `user_id`, name, kind, `source`, `updated_at`.
- `plan_days` — `plan_id`, position, title, focus, tag (push/pull/legs),
  `source`.
- `plan_exercises` — `day_id`, position, `exercise_id` → catalog, target
  (sets×reps), rir, cue, `superset_label`, `source`.
- `set_logs` — history: `user_id`, `plan_exercise_id`, date, weight, `reps[]`,
  rir, `logged_at`, `source`.
- `api_keys` — `user_id`, hashed key, name, `last_used_at`, `revoked`.
- `artifacts` — `user_id`, name, type, Storage reference, `source`.

Every user-owned table: RLS policy on `user_id = auth.uid()`. Every mutation
carries `source` ('app' | 'agent') and `updated_at`.

## Boundaries

- The Android app talks only to Supabase (anon key + user JWT). It never holds
  the service-role key and never calls the rack-MCP except the JWT-authenticated
  admin endpoint for key management.
- The rack-MCP is the only self-hosted code. It resolves an API key to a user
  and acts as that user; it does not bypass RLS with the service-role key for
  user data.
- Training-domain invariants live in the database (constraints, RLS, triggers
  for `source`/`updated_at`) so both adapters — app and MCP — inherit them.

## Key flows

1. Login — app signs in via Supabase Auth → JWT → reads its own data under RLS.
2. Live agent edit (the wow loop) — MCP client calls a rack-MCP write tool with
   the user's API key → MCP resolves key → user, Zod-validates, writes as that
   user (`source='agent'`) → Postgres change → Supabase Realtime → the user's
   channel → the app highlights the changed row.
3. App logging — app writes a set via `supabase-kt` (`source='app'`) →
   persisted, immediately visible to the MCP client on read-back.
4. API-key creation — app calls the rack-MCP admin endpoint (with the user JWT)
   → key generated, hash stored in `api_keys`, plaintext returned once → user
   pastes it into their MCP client config.
5. Plan authoring — MCP client composes a full plan across several write tools →
   each write streams live into the app.
6. Artifact generation — MCP client reads data via read tools, builds a
   visualization, optionally stores it via an artifact tool → app displays it.

## Where new code goes

- New MCP capability → `/mcp/src/tools/*`: a tool with a Zod schema, always
  user-scoped, registered on the server.
- New screen / UI → an `/android` feature; data access through a repository,
  never Supabase calls inside Composables.
- New / reworked UI visuals → follow the Recomp design system: tokens in
  `docs/design-tokens.md` (normative) and the Compose Recomp theme components.
  Encode tokens once in the Compose theme; compose screens from that theme,
  never ad-hoc colors.
- New table or policy → `/supabase/migrations`, with its RLS policy in the same
  migration.
- A rule both app and MCP must obey → enforce it in the database (constraint,
  RLS, or trigger), not in one client.
