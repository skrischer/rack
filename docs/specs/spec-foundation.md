# Spec: Phase 1 — Foundation & data backbone

> Created: 2026-06-17

Stand up the repo scaffold (`/android`, `/mcp`, `/supabase`, `/docs`), the root `Makefile` (bootstrap/verify/build), the **local Supabase stack** (`supabase start`), and the schema + RLS + seed that every later phase reads and writes — so both the app and the MCP inherit `source`/`updated_at` and per-user isolation from the database, not from client code. This phase is built and verified entirely against the **local stack**; the managed Supabase project, VPS + Caddy + domain/TLS, and Firebase/FCM remain the eventual production target and are **deferred to hosted deploy** (the constitution's hosting choices stay valid, just not exercised now).

> **Local port pinning.** The committed `supabase/config.toml` deliberately overrides the Supabase CLI defaults: `[api] port = 55321` and `[db] port = 55322` (Studio `55323`, Inbucket `55324`). So `supabase start` binds the local REST/Auth/Realtime/Storage API on **`http://localhost:55321`** and Postgres on **`localhost:55322`** — NOT the default `55321`/`55322`. Every `.env`/`local.properties` value below is taken from the `supabase start` output (which prints these `55xxx` ports because it honours `config.toml`); no port is hardcoded to a default that contradicts the committed config.

## Outcome

- [ ] The repo has the four top-level areas wired: `/android` (Gradle app), `/mcp` (Node/TS), `/supabase` (migrations + seed), `/docs` (already present), with a root `Makefile`.
- [ ] `make bootstrap` runs `(cd mcp && npm ci)` and copies `.env.example` to `.env` (and `android/local.properties` from its template) when absent, leaving the worktree ready to verify. The copied `.env`/`local.properties` are populated from the **local** `supabase start` output (API URL `http://localhost:55321` per the committed `config.toml` `[api] port`, generated anon + service-role keys, JWT secret, and the local Postgres URL on port `55322` per `[db] port`) — no human-delivered managed keys are needed to develop.
- [ ] `supabase start` brings up the full local Supabase stack (Postgres + Auth + Realtime + Storage) in Docker on the ports pinned in `config.toml` (API `55321`, DB `55322`) and prints the local API URL, anon key, service-role key, JWT secret, and Postgres URL that drive local development.
- [ ] `make verify` runs the MCP gate `(cd mcp && npm run verify)` = lint + typecheck + test, then the Android static checks (ktlint + detekt), and exits non-zero if any fails.
- [ ] `make build` runs the MCP build and `(cd android && ./gradlew assembleDebug)` and produces a debug APK.
- [ ] The measured wall-clock duration of `make verify` on the scaffold is recorded in `docs/workflow.md`, replacing the `to be measured in Phase 1` placeholder.
- [ ] The MCP package typechecks under TypeScript `strict` with no `any`, and ESLint + Prettier are wired into `npm run verify`; a placeholder/no-op test exists so `npm run test` is green.
- [ ] A local `supabase db reset` applies all Phase 1 migrations + seed against the local Docker DB without error (safe — wipes only the local DB). This is the primary, checkable schema-apply path for this phase.
- [ ] (deferred — hosted deploy) Supabase is linked to the managed project and `supabase db push` applies all Phase 1 migrations cleanly against it. The migrations themselves are authored and proven locally now; the push to the managed project is exercised at hosted deploy.
- [ ] Migrations exist under `supabase/migrations/` creating `exercises`, `plans`, `plan_days`, `plan_exercises`, `set_logs`, `api_keys`, and `artifacts` with the columns named in `docs/architecture.md`.
- [ ] Every user-owned table (`plans`, `plan_days`, `plan_exercises`, `set_logs`, `api_keys`, `artifacts`) has RLS enabled with an owner policy resolving to `auth.uid()` in the SAME migration that creates the table.
- [ ] `exercises` is public-read (anon + authenticated SELECT) and carries `license` + `license_author` attribution columns; it has no per-user owner policy because it is a global catalog.
- [ ] Every mutation-carrying table has a `source` column of type `edit_source` (`'app' | 'agent'`) and an `updated_at` column, both enforced by the database (a shared `set_updated_at` trigger; `source` defaulted and constrained) so the app and the MCP inherit them without client code.
- [ ] The `exercises` catalog is seeded from the wger CC-BY-SA dataset via a committed, reproducible importer + committed seed SQL written to `supabase/seed.sql` (the single path the existing `config.toml` `[db.seed] sql_paths` already references), with `license`/`license_author` populated per row and a CC-BY-SA attribution notice committed in `supabase/`.
- [ ] A cross-table integrity check (foreign keys: `plan_days.plan_id`, `plan_exercises.day_id`, `plan_exercises.exercise_id`, `set_logs.plan_exercise_id`/`user_id`, all `*.user_id` to `auth.users`) is present and enforced by the schema.

## Scope

### In scope

- Repo scaffold: `/android` minimal Gradle/Compose app module (enough for `assembleDebug`, ktlint, detekt to run), `/mcp` Node/TS package (`tsconfig` strict, ESLint, Prettier, Zod dep, a `verify` script, a placeholder test), root `Makefile`.
- Local Supabase wiring: `supabase start` brings the stack up locally on the `config.toml`-pinned ports (API `55321`, DB `55322`); the `.env`/`local.properties` template flow is populated from the local stack output; schema is applied via `supabase db reset` / migration up against the local Docker DB.
- Schema + RLS + triggers migrations for all seven tables, RLS in the same migration as each table.
- The shared `source`/`updated_at` enforcement (enum + trigger) installed once and applied to every mutation-carrying table.
- wger exercise-catalog import: a committed transform from the raw CC-BY-SA export into the committed `supabase/seed.sql` (the path `config.toml`'s `sql_paths` loads on `db reset`), with attribution columns and notice.
- Recording the `make verify` duration in `docs/workflow.md`.

### Out of scope

- Any MCP tool, server, transport, or API-key resolution logic — Phase 2 (`docs/specs/spec-rack-mcp.md`). This phase only creates the `api_keys` table. (When Phase 2 lands, the rack-MCP runs as a plain Node process on `http://localhost:<port>` over Streamable HTTP for local dev — no Caddy/VPS/TLS.)
- Any Android UI, ViewModel, repository, or Supabase client wiring beyond the bare module needed to build and lint — Phase 3 (`docs/specs/spec-android-app.md`). (When the app wires to the local stack, an emulator reaches the local API at `http://10.0.2.2:55321` — the emulator-host alias for `localhost:55321` per the pinned `[api] port` — and a physical device via the host LAN IP on the same `55321` port; only the local anon key goes into `android/local.properties`.)
- Realtime subscription / edit highlighting — Phase 4 (`docs/specs/spec-live-sync.md`). Realtime is already enabled in `config.toml` and runs in the local stack; no per-table publication tuning here beyond what RLS requires.
- Storage bucket creation for artifacts and the artifact write path — Phase 6 (`docs/specs/spec-visualization-artifacts.md`). Storage is included in the local stack; this phase only creates the `artifacts` table row shape.
- Exercise images / step-by-step instruction enrichment — Phase 7 (`docs/specs/spec-exercise-detail.md`). This phase seeds text catalog fields only.
- Docker Compose + Caddy hosting, VPS, domain/TLS — production deploy (deferred). Local dev needs none of it.
- Linking + `db push` to the managed Supabase project — deferred to hosted deploy (see issue #7).

## Constraints

Binding rules from `docs/constitution.md` this phase is wholly governed by: RLS on every user-owned table in the same migration; the database (constraints/RLS/triggers) is where rules both adapters must obey live; `source` + `updated_at` on every mutation; TypeScript strict with no `any`/`as unknown as`/`@ts-ignore`; Zod is a dependency of the MCP boundary (its use lands in Phase 2); only wger CC-BY-SA data with attribution, never AGPL wger code; no CRDT library; secrets server-side only — the Android client carries the anon key + user JWT and never the service-role key. Locally, the service-role key + JWT secret are the ones `supabase start` generates and live only in the gitignored `.env`; the Android `local.properties` carries only the local URL (`http://10.0.2.2:55321` for the emulator) + local anon key. Repo layout `/android`, `/mcp`, `/supabase`, `/docs`; DB columns `snake_case`. Quality gates: typecheck + lint + tests green, `assembleDebug` builds, every new user-data table ships its RLS policy in the same migration, no secrets committed.

The constitution's hosting choices (managed Supabase, VPS + Caddy + domain/TLS, Firebase/FCM) remain the eventual production target — they are **deferred**, not dropped.

## Prior art

- [Open exercise data & training domain model](../prior-art.md#open-exercise-data--training-domain-model) — the wger CC-BY-SA dataset is the seed source for `exercises`; ADOPT the catalog + the routines→days→sets/superset domain shape, AVOID any AGPL wger code and preserve CC-BY-SA attribution + share-alike on the derivative seed.
- [Programmable agent access (MCP write-through to fitness data)](../prior-art.md#programmable-agent-access-mcp-write-through-to-fitness-data) — informs the table shapes (plans/days/exercises/sets) the Phase 2 tools will write through; this phase only models them.
- [Exercise media — images & step-by-step instructions (Phase 7)](../prior-art.md#exercise-media--images--step-by-step-instructions-phase-7) — noted only to scope image/instruction enrichment OUT of this phase; the seed here carries text fields, attribution, and leaves media gaps for Phase 7.

## Human prerequisites

Local development needs **no human-delivered secrets or managed infra**. `supabase start` generates the local API URL (`http://localhost:55321`), anon key, service-role key, JWT secret, and Postgres URL (port `55322`) from the committed `config.toml`; a Supabase Auth test user is created by signing up against the local Auth (no dashboard); random secrets (e.g. `API_KEY_PEPPER`) are generated by the implementer locally. The only human inputs are dev tooling and one data download:

- [ ] Local dev tooling installed: Docker (for `supabase start`), the Supabase CLI, Node/npm (for the MCP), and the Android SDK + an emulator or device (for `assembleDebug`/lint).
- [ ] wger exercise dataset obtained under CC-BY-SA and dropped at `supabase/seed/wger/` as the raw export the importer transforms; license text + attribution author confirmed. The importer writes its generated output to `supabase/seed.sql` (the single path `config.toml`'s `sql_paths` already references), so a local `db reset` loads it. (This is a DATA input, not infra.)

### Deferred — hosted deploy

Recorded for the eventual production deploy; **not blocking local work**:

- [ ] Managed Supabase project provisioned; `SUPABASE_URL`, `SUPABASE_PROJECT_REF`, anon + service-role keys, plus `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD` for `supabase link` and `supabase db push` (only additive push; no destructive `db reset` against it). — used by issue #7.
- [ ] VPS + domain + DNS + TLS (Caddy) to host the rack-MCP over HTTPS for remote MCP clients. (Local MCP runs as a plain Node process on `http://localhost:<port>`.)
- [ ] Firebase project + FCM service account + a Play-services device for real push delivery.

> Known local caveat (not a blocker): some MCP clients require HTTPS even for `localhost`. When that surfaces (Phase 2+), it is solvable locally via a `cloudflared` quick-tunnel or a local Caddy with a localhost cert — no VPS/domain needed.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Local-first dev path is primary: build + verify against the local Supabase stack (`supabase start`, local `db reset`); hosted (managed project, VPS + Caddy + domain/TLS, Firebase/FCM) deferred to a production deploy | The whole phase is local-implementable; `supabase start` supplies URL/anon/service-role/JWT-secret with no human secret, so no managed infra gates development. The constitution's hosting choices stay valid as the eventual target | 2026-06-17 |
| Local ports follow the committed `config.toml` overrides — API `http://localhost:55321` (`[api] port = 55321`), Postgres `localhost:55322` (`[db] port = 55322`) — NOT the Supabase CLI defaults `55321`/`55322`; the emulator reaches the local API at `http://10.0.2.2:55321` | `supabase start` honours the committed `config.toml`, so it binds `55321`/`55322`; an `.env`/`local.properties` carrying `:55321` could not reach the local stack. Pinning every documented URL and the committed `android/local.properties` template to `55321` keeps the copied-out template correct on first `make bootstrap` | 2026-06-17 |
| Schema applies via local `supabase db reset` / migration up against the local Docker DB; managed `db push` is deferred (issue #7) | The migrations are authored and proven locally first; pushing to the managed project is a hosted-deploy step, not a development gate | 2026-06-17 |
| One migration per table-group with its RLS + triggers inline; ordered, timestamp-prefixed files | Constitution requires the RLS policy in the SAME migration as the table; keeping each table with its policy and FKs in one file makes that auditable | 2026-06-17 |
| `source` modeled as a Postgres enum `edit_source` (`'app'`,`'agent'`), defaulted `'app'`, applied to every mutation-carrying table | Type-safe at the DB boundary; a check-constrained text column would also work but the enum is stricter and self-documenting | 2026-06-17 |
| `updated_at` maintained by a single shared `set_updated_at()` BEFORE-UPDATE trigger installed once and attached to each table | Both adapters inherit it from the DB per the constitution; one trigger function avoids per-table drift | 2026-06-17 |
| `exercises` is public-read (anon + authenticated SELECT), no owner policy; it is the only table without a user_id owner policy | It is a global shared catalog, not user-owned; architecture says "Public read" | 2026-06-17 |
| The generated seed SQL is committed to `supabase/seed.sql`, the single literal path the existing `config.toml` `[db.seed] sql_paths = ["./seed.sql"]` references; `sql_paths` is NOT changed | `db reset` only loads the seed paths `config.toml` names; writing the importer output anywhere else (e.g. `supabase/seed/wger.sql`) would make `db reset` silently load no seed and the `count(*) > 0` check fail with no error. Pinning the output path closes that silent-failure fork | 2026-06-17 |
| wger import is a committed transform script + committed seed SQL (`supabase/seed.sql`); the raw export is a human-delivered prerequisite at `supabase/seed/wger/`, not committed wholesale | `supabase db reset` must be reproducible from the committed `seed.sql`; the raw upstream export is large/external and is the human's to fetch under CC-BY-SA | 2026-06-17 |
| Android module is a minimal Compose app stub (single empty Activity) wired with ktlint + detekt, enough to `assembleDebug` and lint; it also commits the `android/local.properties` template carrying only `supabase.url` + `supabase.anonKey` (pointing at the local stack — `http://10.0.2.2:55321` for the emulator per the pinned `[api] port`, host LAN IP on `55321` for a device) | `make verify`/`make build` must actually exercise the Android static checks and APK build this phase establishes; the committed template is the source `make bootstrap` copies from; real UI is Phase 3 | 2026-06-17 |
| MCP `verify` = `eslint` + `tsc --noEmit` + `vitest run` (or `node --test`) with a placeholder test; `build` = `tsc` | Establishes the Phase 1 gate commands referenced in `docs/workflow.md`; a green placeholder test keeps the test step real before Phase 2 tools land | 2026-06-17 |
| `bootstrap` copies `.env` from `.env.example` and `android/local.properties` from a committed template only when absent (never overwrites real secrets); locally the values come from `supabase start` output | Idempotent bootstrap; secrets stay gitignored and locally generated | 2026-06-17 |
| `set_logs` stores per-set reps as an integer array `reps integer[]`; `weight` as `numeric`; `rir` as a smallint | Matches `docs/architecture.md` (`reps[]`) and the Recomp logging model (kg + per-set reps + RIR) | 2026-06-17 |
| `plan_exercises.superset_label`, `plan_days.tag`, and `plans.kind` are nullable/free text (tag values push/pull/legs, superset grouping, and plan kind recomp/block per the Recomp prototype) | Architecture lists these fields; the prototype color-codes push/pull/legs/superset and distinguishes plan kinds, so the values are free text validated at the MCP boundary in Phase 2, not constrained in the schema here | 2026-06-17 |
| All `user_id` foreign keys reference `auth.users(id)` with `on delete cascade` | Per-user isolation anchors on Supabase Auth identity; cascading delete keeps orphans out when an account is removed | 2026-06-17 |

## Tracking

This spec is the single source of truth for the Phase 1 design. It is realized by the milestone **Phase 1: Foundation & data backbone** (depends on no prior milestone) and its dependency-ordered issues, one per implementable step. The issues never restate this design; this spec never lists steps. The `Outcome` list above is the done-criteria the milestone QA gate derives its scenarios from.

## Verification

Machine gates (from `docs/workflow.md`), all run against the **local stack**:

- `make bootstrap` succeeds from a clean worktree (the local `.env`/`local.properties` are filled from `supabase start` output — API `http://localhost:55321`, anon/service-role keys, JWT secret, Postgres on `55322`; no human-delivered managed secrets required).
- `make verify` exits 0: MCP `eslint` + `tsc --noEmit` + test all pass; Android ktlint + detekt pass.
- `make build` exits 0 and yields a debug APK plus a compiled MCP `dist/`.

Behavioral checks a human QA runs locally (Test is `none yet`, so these are explicit at the milestone-QA gate). Note: the deny-list forbids only `supabase db reset --linked` (the destructive reset against the shared **remote** project); a plain local `supabase db reset` is allowed and is the primary local reseed path, so these reset-based seed checks run in the loop against the **local** Docker DB (safe — wipes only the local DB). The machine gates (`make verify`/`make build`) never invoke reset:

- `supabase status` (or the `supabase start` banner) reports the API on `http://localhost:55321` and the DB on `localhost:55322`, matching the committed `config.toml`; the copied `.env`/`local.properties` carry those same `55xxx` ports (no stray `55321`).
- `supabase db reset` (local) applies all migrations and then loads `supabase/seed.sql` with no error; `select count(*) from exercises` returns the seeded catalog count (> 0, matching the imported dataset size).
- Every user-owned table reports `rowsecurity = true` (`select relname, relrowsecurity from pg_class where relname in ('plans','plan_days','plan_exercises','set_logs','api_keys','artifacts')`), and each has exactly the owner policy on `auth.uid()` (inspect `pg_policies`).
- RLS isolation (against the local Auth, using a signed-up local test user): signed in as user A, selecting/inserting rows for user B's `plans`/`set_logs` returns nothing / is rejected; `exercises` is selectable by anon.
- Inserting a row into any mutation-carrying table without specifying `source` defaults it to `'app'`; updating any such row bumps `updated_at` via the trigger.
- `select license, license_author from exercises limit 5` shows CC-BY-SA attribution populated; the attribution notice file is present under `supabase/`.
- `docs/workflow.md` shows a concrete measured `make verify` duration, not the placeholder.

Deferred — hosted deploy (run when the managed project exists, not in this phase):

- `supabase db push` applies the same migrations to the managed project without error (issue #7).

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| `.env`/`local.properties` carry the Supabase default port `55321` and cannot reach the local stack, which `config.toml` pins to `55321`/`55322` | The committed `.env.example` and `android/local.properties` templates hardcode the `config.toml` ports (API `55321`, emulator `http://10.0.2.2:55321`, DB `55322`); the QA `supabase status` check confirms the banner ports match the copied-out templates, catching any drift before wiring |
| wger raw export format/columns differ from what the importer expects | Importer is a small committed transform with a documented input shape; if the human-delivered export shape differs, the importer fails loudly and the issue parks `blocked:human` naming the expected shape — no silent partial seed |
| Generated seed written to a path `config.toml` does not load, so `db reset` seeds an empty catalog silently | The importer output path is pinned to `supabase/seed.sql` — the single literal path the existing `[db.seed] sql_paths` already lists; the QA `count(*) > 0` check after a local `db reset` confirms the seed actually loaded |
| RLS policy omitted from a table's migration (constitution violation) | Each table-group migration is reviewed against the "RLS in same migration" rule; the verification check enumerates `pg_class.relrowsecurity` for all six user-owned tables |
| `make verify` runs but Android static checks are effectively no-ops on an empty module | The Android stub includes at least one real Kotlin source so ktlint/detekt have something to lint; verification confirms a deliberately-bad format is caught locally before wiring |
| Service-role key leaks into the Android client | The local service-role key + JWT secret (from `supabase start`) live only in `.env` (gitignored); `android/local.properties` template carries only the local `supabase.url` (`http://10.0.2.2:55321`) + `supabase.anonKey`; no secret is committed |
| Migrations authored locally fail later against the managed project on `db push` | Migrations are additive and standard Postgres applied via local `db reset` first; the deferred `db push` (issue #7) re-runs the same files at hosted deploy, where the human prerequisite confirms push is permitted |
| `reps integer[]` array column complicates later Realtime/RLS handling | Array is a plain column, orthogonal to RLS; it matches `architecture.md` and avoids an extra child table the single-owner model does not need |

## Decision log

- Local-first dev path adopted 2026-06-17; hosted deploy deferred (managed Supabase + `db push`, VPS + Caddy + domain/TLS, Firebase/FCM recorded as the eventual production target, not exercised this phase).
- Local ports pinned to the committed `config.toml` overrides — API `http://localhost:55321`, Postgres `localhost:55322`, emulator `http://10.0.2.2:55321` — not the CLI defaults `55321`/`55322`; the committed `.env.example` + `android/local.properties` templates carry these `55xxx` values so `make bootstrap` copies a reachable config (auto-resolved under autonomous planning mandate, 2026-06-17).
- Schema applies via local `supabase db reset`; managed `db push` deferred to issue #7 (auto-resolved under autonomous planning mandate, 2026-06-17).
- Per-table-group migrations with inline RLS + FKs + triggers; timestamp-ordered (auto-resolved under autonomous planning mandate, 2026-06-17).
- `source` as enum `edit_source('app','agent')` defaulted `'app'`; `updated_at` via one shared `set_updated_at()` trigger attached per table (auto-resolved under autonomous planning mandate, 2026-06-17).
- `exercises` public-read with no owner policy; the only non-user-owned table; carries `license`/`license_author` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Generated seed committed to `supabase/seed.sql`, the single path the existing `config.toml` `[db.seed] sql_paths` references; `sql_paths` left unchanged so a local `db reset` deterministically loads the seed and `count(*) > 0` holds (auto-resolved under autonomous planning mandate, 2026-06-17).
- wger seed = committed transform script + committed `supabase/seed.sql`; raw export at `supabase/seed/wger/` is a human prerequisite, not committed (auto-resolved under autonomous planning mandate, 2026-06-17).
- Android module is a minimal Compose stub wired for ktlint + detekt + `assembleDebug` and committing the `local.properties` template (local url `http://10.0.2.2:55321` + anonKey only); real UI deferred to Phase 3 (auto-resolved under autonomous planning mandate, 2026-06-17).
- MCP `verify` = eslint + `tsc --noEmit` + placeholder test; `build` = `tsc`; Zod added as a dependency now, used at the boundary in Phase 2 (auto-resolved under autonomous planning mandate, 2026-06-17).
- `bootstrap` copies `.env`/`local.properties` from templates only when absent, never overwriting; values sourced from `supabase start` (auto-resolved under autonomous planning mandate, 2026-06-17).
- `set_logs.reps integer[]`, `weight numeric`, `rir smallint`; `user_id` FKs to `auth.users(id) on delete cascade` (auto-resolved under autonomous planning mandate, 2026-06-17).
- `plans.kind`, `plan_days.tag`, `plan_exercises.superset_label` kept as free/unconstrained text in the schema, validated at the MCP boundary in Phase 2 (auto-resolved under autonomous planning mandate, 2026-06-17).