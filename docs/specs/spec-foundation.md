# Spec: Phase 1 — Foundation & data backbone

> Created: 2026-06-17

Stand up the repo scaffold (`/android`, `/mcp`, `/supabase`, `/docs`), the root `Makefile` (bootstrap/verify/build), Supabase wiring, and the schema + RLS + seed that every later phase reads and writes — so both the app and the MCP inherit `source`/`updated_at` and per-user isolation from the database, not from client code.

## Outcome

- [ ] The repo has the four top-level areas wired: `/android` (Gradle app), `/mcp` (Node/TS), `/supabase` (migrations + seed), `/docs` (already present), with a root `Makefile`.
- [ ] `make bootstrap` runs `(cd mcp && npm ci)` and copies `.env.example` to `.env` (and `android/local.properties` from its template) when absent, leaving the worktree ready to verify.
- [ ] `make verify` runs the MCP gate `(cd mcp && npm run verify)` = lint + typecheck + test, then the Android static checks (ktlint + detekt), and exits non-zero if any fails.
- [ ] `make build` runs the MCP build and `(cd android && ./gradlew assembleDebug)` and produces a debug APK.
- [ ] The measured wall-clock duration of `make verify` on the scaffold is recorded in `docs/workflow.md`, replacing the `to be measured in Phase 1` placeholder.
- [ ] The MCP package typechecks under TypeScript `strict` with no `any`, and ESLint + Prettier are wired into `npm run verify`; a placeholder/no-op test exists so `npm run test` is green.
- [ ] Supabase is linked to the managed project; `supabase db push` applies all Phase 1 migrations cleanly against it, and a local `supabase db reset` applies migrations + seed without error.
- [ ] Migrations exist under `supabase/migrations/` creating `exercises`, `plans`, `plan_days`, `plan_exercises`, `set_logs`, `api_keys`, and `artifacts` with the columns named in `docs/architecture.md`.
- [ ] Every user-owned table (`plans`, `plan_days`, `plan_exercises`, `set_logs`, `api_keys`, `artifacts`) has RLS enabled with an owner policy resolving to `auth.uid()` in the SAME migration that creates the table.
- [ ] `exercises` is public-read (anon + authenticated SELECT) and carries `license` + `license_author` attribution columns; it has no per-user owner policy because it is a global catalog.
- [ ] Every mutation-carrying table has a `source` column of type `edit_source` (`'app' | 'agent'`) and an `updated_at` column, both enforced by the database (a shared `set_updated_at` trigger; `source` defaulted and constrained) so the app and the MCP inherit them without client code.
- [ ] The `exercises` catalog is seeded from the wger CC-BY-SA dataset via a committed, reproducible importer + committed seed SQL written to `supabase/seed.sql` (the single path the existing `config.toml` `[db.seed] sql_paths` already references), with `license`/`license_author` populated per row and a CC-BY-SA attribution notice committed in `supabase/`.
- [ ] A cross-table integrity check (foreign keys: `plan_days.plan_id`, `plan_exercises.day_id`, `plan_exercises.exercise_id`, `set_logs.plan_exercise_id`/`user_id`, all `*.user_id` to `auth.users`) is present and enforced by the schema.

## Scope

### In scope

- Repo scaffold: `/android` minimal Gradle/Compose app module (enough for `assembleDebug`, ktlint, detekt to run), `/mcp` Node/TS package (`tsconfig` strict, ESLint, Prettier, Zod dep, a `verify` script, a placeholder test), root `Makefile`.
- Supabase project wiring: `supabase link`, the `.env`/`local.properties` template flow, and `db push` to the managed project.
- Schema + RLS + triggers migrations for all seven tables, RLS in the same migration as each table.
- The shared `source`/`updated_at` enforcement (enum + trigger) installed once and applied to every mutation-carrying table.
- wger exercise-catalog import: a committed transform from the raw CC-BY-SA export into the committed `supabase/seed.sql` (the path `config.toml`'s `sql_paths` loads on `db reset`), with attribution columns and notice.
- Recording the `make verify` duration in `docs/workflow.md`.

### Out of scope

- Any MCP tool, server, transport, or API-key resolution logic — Phase 2 (`docs/specs/spec-rack-mcp.md`). This phase only creates the `api_keys` table.
- Any Android UI, ViewModel, repository, or Supabase client wiring beyond the bare module needed to build and lint — Phase 3 (`docs/specs/spec-android-app.md`).
- Realtime subscription / edit highlighting — Phase 4 (`docs/specs/spec-live-sync.md`). Realtime is already enabled in `config.toml`; no per-table publication tuning here beyond what RLS requires.
- Storage bucket creation for artifacts and the artifact write path — Phase 6 (`docs/specs/spec-visualization-artifacts.md`). This phase only creates the `artifacts` table row shape.
- Exercise images / step-by-step instruction enrichment — Phase 7 (`docs/specs/spec-exercise-detail.md`). This phase seeds text catalog fields only.
- Docker Compose + Caddy hosting — Phase 2.

## Constraints

Binding rules from `docs/constitution.md` this phase is wholly governed by: RLS on every user-owned table in the same migration; the database (constraints/RLS/triggers) is where rules both adapters must obey live; `source` + `updated_at` on every mutation; TypeScript strict with no `any`/`as unknown as`/`@ts-ignore`; Zod is a dependency of the MCP boundary (its use lands in Phase 2); only wger CC-BY-SA data with attribution, never AGPL wger code; no CRDT library; secrets server-side only — the Android client carries the anon key + user JWT and never the service-role key; repo layout `/android`, `/mcp`, `/supabase`, `/docs`; DB columns `snake_case`. Quality gates: typecheck + lint + tests green, `assembleDebug` builds, every new user-data table ships its RLS policy in the same migration, no secrets committed.

## Prior art

- [Open exercise data & training domain model](../prior-art.md#open-exercise-data--training-domain-model) — the wger CC-BY-SA dataset is the seed source for `exercises`; ADOPT the catalog + the routines→days→sets/superset domain shape, AVOID any AGPL wger code and preserve CC-BY-SA attribution + share-alike on the derivative seed.
- [Programmable agent access (MCP write-through to fitness data)](../prior-art.md#programmable-agent-access-mcp-write-through-to-fitness-data) — informs the table shapes (plans/days/exercises/sets) the Phase 2 tools will write through; this phase only models them.
- [Exercise media — images & step-by-step instructions (Phase 7)](../prior-art.md#exercise-media--images--step-by-step-instructions-phase-7) — noted only to scope image/instruction enrichment OUT of this phase; the seed here carries text fields, attribution, and leaves media gaps for Phase 7.

## Human prerequisites

- [ ] Managed Supabase project provisioned; `SUPABASE_URL`, `SUPABASE_PROJECT_REF`, anon key, and service-role key delivered into the worktree `.env` (template `.env.example`).
- [ ] `SUPABASE_ACCESS_TOKEN` (personal access token) and `SUPABASE_DB_PASSWORD` available for `supabase link` and `supabase db push` against the managed project.
- [ ] wger exercise dataset obtained under CC-BY-SA and dropped at `supabase/seed/wger/` as the raw export the importer transforms; license text + attribution author confirmed. The importer writes its generated output to `supabase/seed.sql` (the single path `config.toml`'s `sql_paths` already references), so a local `db reset` loads it.
- [ ] Confirmation that the managed Supabase project may be `link`ed and `db push`ed from worktrees/CI (no destructive `db reset` is run against it; only additive `db push`).

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| One migration per table-group with its RLS + triggers inline; ordered, timestamp-prefixed files | Constitution requires the RLS policy in the SAME migration as the table; keeping each table with its policy and FKs in one file makes that auditable | 2026-06-17 |
| `source` modeled as a Postgres enum `edit_source` (`'app'`,`'agent'`), defaulted `'app'`, applied to every mutation-carrying table | Type-safe at the DB boundary; a check-constrained text column would also work but the enum is stricter and self-documenting | 2026-06-17 |
| `updated_at` maintained by a single shared `set_updated_at()` BEFORE-UPDATE trigger installed once and attached to each table | Both adapters inherit it from the DB per the constitution; one trigger function avoids per-table drift | 2026-06-17 |
| `exercises` is public-read (anon + authenticated SELECT), no owner policy; it is the only table without a user_id owner policy | It is a global shared catalog, not user-owned; architecture says "Public read" | 2026-06-17 |
| The generated seed SQL is committed to `supabase/seed.sql`, the single literal path the existing `config.toml` `[db.seed] sql_paths = ["./seed.sql"]` references; `sql_paths` is NOT changed | `db reset` only loads the seed paths `config.toml` names; writing the importer output anywhere else (e.g. `supabase/seed/wger.sql`) would make `db reset` silently load no seed and the `count(*) > 0` check fail with no error. Pinning the output path closes that silent-failure fork | 2026-06-17 |
| wger import is a committed transform script + committed seed SQL (`supabase/seed.sql`); the raw export is a human-delivered prerequisite at `supabase/seed/wger/`, not committed wholesale | `supabase db reset` must be reproducible from the committed `seed.sql`; the raw upstream export is large/external and is the human's to fetch under CC-BY-SA | 2026-06-17 |
| Android module is a minimal Compose app stub (single empty Activity) wired with ktlint + detekt, enough to `assembleDebug` and lint; it also commits the `android/local.properties` template carrying only `supabase.url` + `supabase.anonKey` | `make verify`/`make build` must actually exercise the Android static checks and APK build this phase establishes; the committed template is the source `make bootstrap` copies from; real UI is Phase 3 | 2026-06-17 |
| MCP `verify` = `eslint` + `tsc --noEmit` + `vitest run` (or `node --test`) with a placeholder test; `build` = `tsc` | Establishes the Phase 1 gate commands referenced in `docs/workflow.md`; a green placeholder test keeps the test step real before Phase 2 tools land | 2026-06-17 |
| `bootstrap` copies `.env` from `.env.example` and `android/local.properties` from a committed template only when absent (never overwrites real secrets) | Idempotent bootstrap; secrets stay gitignored and human-supplied | 2026-06-17 |
| `set_logs` stores per-set reps as an integer array `reps integer[]`; `weight` as `numeric`; `rir` as a smallint | Matches `docs/architecture.md` (`reps[]`) and the Recomp logging model (kg + per-set reps + RIR) | 2026-06-17 |
| `plan_exercises.superset_label`, `plan_days.tag`, and `plans.kind` are nullable/free text (tag values push/pull/legs, superset grouping, and plan kind recomp/block per the Recomp prototype) | Architecture lists these fields; the prototype color-codes push/pull/legs/superset and distinguishes plan kinds, so the values are free text validated at the MCP boundary in Phase 2, not constrained in the schema here | 2026-06-17 |
| All `user_id` foreign keys reference `auth.users(id)` with `on delete cascade` | Per-user isolation anchors on Supabase Auth identity; cascading delete keeps orphans out when an account is removed | 2026-06-17 |

## Tracking

This spec is the single source of truth for the Phase 1 design. It is realized by the milestone **Phase 1: Foundation & data backbone** (depends on no prior milestone) and its dependency-ordered issues, one per implementable step. The issues never restate this design; this spec never lists steps. The `Outcome` list above is the done-criteria the milestone QA gate derives its scenarios from.

## Verification

Machine gates (from `docs/workflow.md`):

- `make bootstrap` succeeds from a clean worktree (after the human prerequisites are delivered into `.env`).
- `make verify` exits 0: MCP `eslint` + `tsc --noEmit` + test all pass; Android ktlint + detekt pass.
- `make build` exits 0 and yields a debug APK plus a compiled MCP `dist/`.

Behavioral checks a human QA runs (Test is `none yet`, so these are explicit at the milestone-QA gate). Note: `supabase db reset` is hard-denied to the autonomous implementer by `.claude/settings.json`, so the implementer never runs it inside the loop — these reset-based seed checks are run by the human at this QA gate, while the machine gates (`make verify`/`make build`) above never invoke reset and so are not blocked:

- `supabase db reset` (local) applies all migrations and then loads `supabase/seed.sql` with no error; `select count(*) from exercises` returns the seeded catalog count (> 0, matching the imported dataset size).
- `supabase db push` applies the same migrations to the managed project without error.
- Every user-owned table reports `rowsecurity = true` (`select relname, relrowsecurity from pg_class where relname in ('plans','plan_days','plan_exercises','set_logs','api_keys','artifacts')`), and each has exactly the owner policy on `auth.uid()` (inspect `pg_policies`).
- RLS isolation: signed in as user A, selecting/inserting rows for user B's `plans`/`set_logs` returns nothing / is rejected; `exercises` is selectable by anon.
- Inserting a row into any mutation-carrying table without specifying `source` defaults it to `'app'`; updating any such row bumps `updated_at` via the trigger.
- `select license, license_author from exercises limit 5` shows CC-BY-SA attribution populated; the attribution notice file is present under `supabase/`.
- `docs/workflow.md` shows a concrete measured `make verify` duration, not the placeholder.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| wger raw export format/columns differ from what the importer expects | Importer is a small committed transform with a documented input shape; if the human-delivered export shape differs, the importer fails loudly and the issue parks `blocked:human` naming the expected shape — no silent partial seed |
| Generated seed written to a path `config.toml` does not load, so `db reset` seeds an empty catalog silently | The importer output path is pinned to `supabase/seed.sql` — the single literal path the existing `[db.seed] sql_paths` already lists; the QA `count(*) > 0` check after a local `db reset` confirms the seed actually loaded |
| RLS policy omitted from a table's migration (constitution violation) | Each table-group migration is reviewed against the "RLS in same migration" rule; the verification check enumerates `pg_class.relrowsecurity` for all six user-owned tables |
| `make verify` runs but Android static checks are effectively no-ops on an empty module | The Android stub includes at least one real Kotlin source so ktlint/detekt have something to lint; verification confirms a deliberately-bad format is caught locally before wiring |
| Service-role key leaks into the Android client | Service-role key lives only in `.env` (gitignored, server-side); `android/local.properties` template carries only `supabase.url` + `supabase.anonKey`; no secret is committed |
| `supabase db push` mutates the shared managed project unexpectedly | Only additive migrations are pushed; the deny-list forbids `supabase db reset`; the human prerequisite explicitly confirms push is permitted |
| `reps integer[]` array column complicates later Realtime/RLS handling | Array is a plain column, orthogonal to RLS; it matches `architecture.md` and avoids an extra child table the single-owner model does not need |

## Decision log

- Per-table-group migrations with inline RLS + FKs + triggers; timestamp-ordered (auto-resolved under autonomous planning mandate, 2026-06-17).
- `source` as enum `edit_source('app','agent')` defaulted `'app'`; `updated_at` via one shared `set_updated_at()` trigger attached per table (auto-resolved under autonomous planning mandate, 2026-06-17).
- `exercises` public-read with no owner policy; the only non-user-owned table; carries `license`/`license_author` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Generated seed committed to `supabase/seed.sql`, the single path the existing `config.toml` `[db.seed] sql_paths` references; `sql_paths` left unchanged so a local `db reset` deterministically loads the seed and `count(*) > 0` holds (auto-resolved under autonomous planning mandate, 2026-06-17).
- wger seed = committed transform script + committed `supabase/seed.sql`; raw export at `supabase/seed/wger/` is a human prerequisite, not committed (auto-resolved under autonomous planning mandate, 2026-06-17).
- Android module is a minimal Compose stub wired for ktlint + detekt + `assembleDebug` and committing the `local.properties` template (url + anonKey only); real UI deferred to Phase 3 (auto-resolved under autonomous planning mandate, 2026-06-17).
- MCP `verify` = eslint + `tsc --noEmit` + placeholder test; `build` = `tsc`; Zod added as a dependency now, used at the boundary in Phase 2 (auto-resolved under autonomous planning mandate, 2026-06-17).
- `bootstrap` copies `.env`/`local.properties` from templates only when absent, never overwriting (auto-resolved under autonomous planning mandate, 2026-06-17).
- `set_logs.reps integer[]`, `weight numeric`, `rir smallint`; `user_id` FKs to `auth.users(id) on delete cascade` (auto-resolved under autonomous planning mandate, 2026-06-17).
- `plans.kind`, `plan_days.tag`, `plan_exercises.superset_label` kept as free/unconstrained text in the schema, validated at the MCP boundary in Phase 2 (auto-resolved under autonomous planning mandate, 2026-06-17).