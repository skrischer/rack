# Spec: Phase 7 — Exercise execution detail & images

> Created: 2026-06-17
> Local-first dev path adopted 2026-06-17; hosted deploy deferred.

Add an exercise detail screen to the Android app — how-to instructions, target muscles, equipment, and images — backed by an enriched, public-read exercise catalog seeded from wger (CC-BY-SA), with text gaps filled from the public-domain free-exercise-db JSON and a deterministic placeholder where no licensed image exists. Built and verified against the **local Supabase stack** (`supabase start`): the enrichment migration and Storage bucket apply to the local Dockerized Postgres + Storage, and the Android emulator reads the enriched catalog over the local anon key at `http://10.0.2.2:55321`. The managed Supabase project, VPS, and any hosted deploy are the eventual production target, **deferred** (see Human prerequisites).

## Outcome

- [ ] Tapping an exercise in the plan/day view opens an exercise detail screen showing: exercise name, category, primary and secondary target muscles, required equipment, ordered step-by-step how-to instructions, and an image (or a deterministic muscle-group placeholder when no licensed image exists).
- [ ] The `exercises` catalog is enriched with new public-read fields — `instructions text[]`, `primary_muscles text[]`, `secondary_muscles text[]`, and `image_path text` — while **reusing the existing Phase 1 columns** `muscles`, `equipment`, `category`, `license`, and `license_author` (per `../architecture.md` data model); the enrichment migration ships in `/supabase/migrations` and applies to the **local stack** via `supabase db reset` / migration up against the local Docker DB.
- [ ] The detail screen renders a visible CC-BY-SA attribution line (`license` + `license_author`) for any exercise whose instruction text or image originates from wger.
- [ ] Instruction text coverage is complete: every catalog exercise has at least one instruction step, sourced from wger where present and complemented from free-exercise-db (public-domain) where wger is thin; exercises with no source in either show an explicit "No instructions available yet" empty state rather than a blank panel.
- [ ] Image coverage uses only wger CC-BY-SA images or self-produced/explicitly-licensed images; NO free-exercise-db images are imported or shipped (their license is unresolved). Exercises without a licensed image resolve to a deterministic placeholder keyed on the exercise's primary muscle, falling back to category, then to a fixed `generic` bucket when neither is populated.
- [ ] wger CC-BY-SA images are stored in a dedicated public-read Supabase Storage bucket on the **local stack** (Storage is included in `supabase start`); the catalog's `image_path` resolves to a public URL the Android emulator loads with the **local anon key** only. (Deferred — hosted deploy: the same bucket + objects are recreated against the managed project on `db push` / hosted upload, served from the managed Storage URL.)
- [ ] The detail screen matches the Recomp visual language (dark `#0d0e11`, volt accent `#c8f23a`, Archivo + Spline Sans Mono, push/pull/legs/superset tag coloring) consistent with the plan/day view from Phase 3.
- [ ] `make verify` and `make build` pass on the branch; all Supabase access on the detail screen flows through the repository layer (no Postgrest/Storage calls inside Composables).

## Scope

### In scope

- A `/supabase/migrations` migration that enriches the global `exercises` catalog with `instructions`, `primary_muscles`, `secondary_muscles`, and `image_path`, **reusing** the existing `muscles`/`equipment`/`category`/`license`/`license_author` columns from Phase 1 (no duplicate `muscles` or `equipment` column is added), keeping the catalog public-read (no owner RLS — it is global seed data, not user-owned). Applied and tested against the local Docker DB.
- A Supabase Storage public bucket for wger CC-BY-SA exercise images, created via migration on the local stack, with a read policy the anon role can use.
- A repeatable seed/enrichment routine (under `/supabase`) that runs against the local DB and: imports wger instructions + images with attribution, complements instruction text from the free-exercise-db public-domain JSON only where wger is missing, and explicitly excludes free-exercise-db images. The wger and free-exercise-db datasets are **data downloads** (reused from Phase 1 where possible), not hosted infra.
- An Android exercise detail feature (screen + ViewModel + repository method) reachable from the existing plan/day/exercise view, in the Recomp design language, pointed at the local stack (`http://10.0.2.2:55321` from the emulator; host LAN IP from a physical device) with the local anon key in `android/local.properties`.
- A deterministic image placeholder strategy for exercises lacking a licensed image.

### Out of scope

- Animated GIFs / video demonstrations and the ExerciseDB hosted API (a runtime third-party dependency on core data — explicitly rejected in prior art). Why: out of phase intent; static image + text is the bar.
- Any free-exercise-db image files. Why: their license is unresolved (open issue #2); shipping them would violate the licensing constraint.
- Surfacing the new catalog fields through the MCP catalog read tools. Why: the roadmap's Phase 2 intent ("user-scoped read/write tools") and `../architecture.md` do not establish a Phase-2 *catalog* read projection to extend, and this phase's intent is the in-app exercise detail screen — the MCP surface is left to the phase that owns the catalog read tool. Avoids a dependency on a possibly-nonexistent artifact.
- In-app editing of exercise instructions/images, and any user-owned per-exercise notes. Why: the catalog is global agent/seed-authored, and plan authoring is the agent's job (vision: in-app is consumption + logging).
- Rest timers, the guided session player, dashboards, and exercise-search/library browsing UI — owned by Phases 8, 9, 11 respectively. Why: phase boundary.
- Re-running the base wger catalog import (Phase 1 owns the initial seed); this phase only enriches the existing rows. Why: dependency boundary on Phase 1.
- **Deferred — hosted deploy:** `db push` of the migration to the managed Supabase project, uploading the image bucket objects to managed Storage, and serving the catalog from the managed project/VPS. The constitution's managed-Supabase + VPS hosting choices remain the production target; this phase ships and verifies the equivalent on the local stack.

## Constraints

Honors the constitution and architecture without restating them: the catalog is public-read seed data so it carries no `auth.uid()` owner policy, but the migration explicitly documents and enables the public-read policy in the same migration; only wger CC-BY-SA **data** is reused with attribution, never wger AGPL code; the Android client uses the anon key + Storage public URLs only and routes all Supabase access through the repository layer with no business logic in Composables; functions ≤ 50 lines / files ≤ 400 lines as a guideline. See `../constitution.md` and `../architecture.md`.

Local-first execution: the phase is built and verified against the local stack started by `supabase start` (local API at `http://localhost:55321`; emulator reaches it at `http://10.0.2.2:55321`). The generated local anon/service_role keys and JWT secret printed by `supabase start` replace any human-delivered managed keys for local development — no managed project and no human-delivered secret is needed to develop this phase. The migration and bucket apply via `supabase db reset` / migration up against the local Docker DB (safe — wipes only the local DB). Local Realtime and Storage work out of the box. The constitution's hosted hosting choices (managed Supabase, VPS + Caddy + domain/TLS) remain the production target and are **deferred**, not removed.

## Prior art

- [Open exercise data & training domain model](../prior-art.md#open-exercise-data--training-domain-model) — wger is the source for CC-BY-SA images + step-by-step instructions and the attribution requirement; coverage is uneven, which is exactly the gap this phase fills.
- [Exercise media — images & step-by-step instructions (Phase 7)](../prior-art.md#exercise-media--images--step-by-step-instructions-phase-7) — the explicit ADOPT (free-exercise-db public-domain JSON to fill text gaps) / AVOID (its images, unresolved license; and ExerciseDB API as a runtime dependency) harvest this phase implements verbatim.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — the detail screen is reached from and visually consistent with the plan/day/exercise logging UX the Recomp prototype already encodes.

## Human prerequisites

Local development needs only dev tooling and data downloads — no managed project, no human-delivered secret.

- [ ] Local dev tooling available: Docker + Supabase CLI (`supabase start` brings up the full local stack — Postgres, Auth, Realtime, Storage — and prints the local API URL, anon key, service_role key, and JWT secret) and an Android emulator (or a physical device on the same LAN). The implementer puts the printed local anon key into `android/local.properties` and generates any random local secrets (e.g. `API_KEY_PEPPER`) themselves.
- [ ] Provide the wger export (CC-BY-SA images + instruction texts) reachable by the seed-enrichment script — a **data download** reused from Phase 1 where possible. Confirm the Phase 1 wger import already populated `exercises` with `name`, `muscles`, `equipment`, `category`, `license`, and `license_author` (per `../architecture.md`) in the local DB so this phase enriches existing rows rather than seeding from scratch.
- [ ] Provide the free-exercise-db `exercises.json` (Unlicense / public-domain) snapshot for text-only gap-filling — a **data download**; confirm NO image files from free-exercise-db are included (image license unresolved).
- [ ] Decide and supply any self-produced or explicitly-licensed replacement images for exercises that have neither a wger image nor an acceptable placeholder, if a real image (not the muscle-group placeholder) is required for specific exercises.

### Deferred — hosted deploy

Recorded so they are not lost, but not blocking local work:

- [ ] A managed Supabase project (URL + anon + service-role keys + access token) and `supabase db push` to apply this phase's migration and bucket to the hosted project.
- [ ] Upload of the wger CC-BY-SA image objects to the managed Storage bucket and verification that the hosted public URLs resolve.
- [ ] A VPS + domain + DNS + TLS (Caddy) for any hosted serving path that fronts this data (not required for the in-app detail screen against local stack).

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| Build and verify this phase against the local Supabase stack (`supabase start`); defer the managed project + `db push` and any VPS/hosted serving | The local stack provides Postgres, Auth, Realtime, and Storage with generated keys, so no managed project or human-delivered secret is needed to develop; the constitution's hosted choices remain the production target, deferred. | 2026-06-17 |
| Enrich the existing global `exercises` catalog with new fields rather than create a user-owned table | The catalog is global seed data shared by all users (architecture data model); detail content is not user-owned, so it stays public-read with no `auth.uid()` owner policy. | 2026-06-17 |
| Reuse the existing Phase 1 `equipment`, `category`, `license`, `license_author` columns; add `primary_muscles`/`secondary_muscles`; retain the existing `muscles` column | `../architecture.md` line 17 already defines `exercises` with `muscles`, `equipment`, `category`, `license`, `license_author`. To avoid a redundant or colliding column, the migration adds only `primary_muscles`/`secondary_muscles` (the wger/free-exercise-db sources distinguish them) and a single new `equipment` is **not** added — the existing one is reused. The Phase 1 `muscles` column is retained (not dropped — that would be a destructive change to Phase 1 data) and used as the legacy union; the enrichment backfills `primary_muscles`/`secondary_muscles` from source where the distinction is available, leaving `muscles` untouched. | 2026-06-17 |
| Catalog stays public-read; migration enables a public SELECT policy in the same migration | RLS-per-table is mandatory, but the catalog's policy is "public read", not owner-scoped; shipping the policy in the migration satisfies the same-migration gate. | 2026-06-17 |
| Store instruction steps as an ordered text array column on `exercises` (`instructions text[]`), not a child table | A single-owner read-only step list does not need a join table; an ordered array matches the "no over-engineering" rule and the wger/free-exercise-db source shape (list of strings). | 2026-06-17 |
| Store target muscles as `primary_muscles text[]` / `secondary_muscles text[]` text-array columns | Matches both wger and free-exercise-db source shapes and the Recomp need to color/label target muscles; avoids premature normalization for a read-only catalog. | 2026-06-17 |
| wger images go into a dedicated public-read Supabase Storage bucket (local stack now, managed Storage on hosted deploy); catalog stores a relative `image_path`, the app resolves the public URL | Storage is the architecture's chosen media store and is included in the local stack; a public bucket lets the anon client load images without a service-role key or signed URLs. The column name is `image_path` everywhere. | 2026-06-17 |
| free-exercise-db is text-source-only; an import guard rejects/ignores any image path from it | Its image license is unresolved (prior art AVOID); enforcing text-only in the importer prevents accidental image inclusion. | 2026-06-17 |
| Exercises without a licensed image get a deterministic placeholder keyed on primary muscle, then category, then a fixed `generic` bucket | Keeps the screen complete without inventing licensed media; deterministic keying makes the same exercise always show the same placeholder, and the `generic` fallback guarantees a placeholder even when both primary muscle and category are empty. | 2026-06-17 |
| Instruction precedence: wger first, free-exercise-db only to fill empties; never overwrite existing wger text | Preserves the attributed CC-BY-SA primary source and uses the public-domain source strictly as a gap-filler (prior art ADOPT). | 2026-06-17 |
| Attribution (`license` + `license_author`) is rendered on the detail screen whenever wger text or image is shown | CC-BY-SA share-alike/attribution obligation from the wger prior-art entry and the constitution don't. | 2026-06-17 |
| The MCP catalog read projection is **not** extended in this phase | Neither `../roadmap.md` (Phase 2 = "user-scoped read/write tools", no catalog read tool enumerated) nor `../architecture.md` establishes a Phase-2 *catalog* read projection to extend; depending on it would block this work on an unestablished artifact. The MCP surface is deferred to the phase that owns the catalog read tool. | 2026-06-17 |
| Exercise→detail navigation extends the Phase 3 plan/day/exercise view (tap an exercise row) | Reuses the existing navigation host rather than building a separate library browser (which is out of scope), keeping the entry point where the user already is. | 2026-06-17 |
| Seed enrichment is idempotent and additive, keyed on the **stable wger id** (with the exercise **name** as the fallback key for free-exercise-db rows that carry no wger id), never a base re-import | Phase 1 owns the base seed; re-running enrichment must not duplicate rows or destroy Phase 1 data and must be safe to re-run during QA. A single primary key (wger id) with a defined name fallback makes the run-twice verification deterministic. | 2026-06-17 |
| If enriched CC-BY-SA-derived data is committed under `/supabase` (e.g. a seed/export file), it ships with a LICENSE/attribution note for that data file | CC-BY-SA is share-alike: redistributing derived wger data in the repo requires attribution at the data-file level, not only on the rendered screen. | 2026-06-17 |

## Tracking

Milestone: **Phase 7: Exercise execution detail & images** (`Depends on milestone: #1`, `Depends on milestone: #3`). Issues are listed in this milestone; each carries a `Spec:` path to this file and an `Acceptance:` checklist mirroring the Verification section. The spec lists no step sequence — the issues own the steps.

## Verification

Per the workflow contract, `Test` is `none yet`, so machine gates plus enumerated human-QA checks apply. All checks run against the local stack (`supabase start`); the equivalent hosted verification (`db push` + managed Storage) is **deferred — hosted deploy**.

- Machine: `make verify` passes (MCP lint + typecheck + test; Android ktlint/detekt).
- Machine: `make build` passes (`./gradlew assembleDebug` for the new detail feature).
- Machine/data: the enrichment routine runs idempotently against the **local Supabase DB** twice with no row duplication and no diff on the second run (keyed on wger id, with name as fallback); an assertion confirms zero exercises end with empty `instructions` after the free-exercise-db fill pass, OR the remaining empties are exactly the ones flagged "No instructions available yet".
- Machine/data: an assertion confirms no Storage object and no `image_path` in the catalog originates from free-exercise-db (importer guard test).
- Machine/data: an assertion confirms the migration adds no duplicate `muscles` or `equipment` column and that the existing Phase 1 `muscles`/`equipment`/`category`/`license`/`license_author` values are preserved.
- Human QA (local): with `supabase start` running and the emulator pointed at `http://10.0.2.2:55321` with the local anon key, open the app, navigate plan → day → tap an exercise; the detail screen opens showing name, category, primary/secondary muscles, equipment, ordered instruction steps, and an image or placeholder.
- Human QA (local): pick an exercise known to come from wger — its detail screen shows a visible CC-BY-SA attribution line with the author.
- Human QA (local): pick an exercise where wger text was missing — its instructions are present (from free-exercise-db) and the screen renders without a blank panel.
- Human QA (local): pick an exercise with no licensed image — it shows the deterministic placeholder, and the same exercise shows the same placeholder on re-entry.
- Human QA (local): the detail screen matches the Recomp dark theme, volt accent, fonts, and tag coloring of the plan/day view; images load from the **local Storage public URL** over the local anon key with no auth error.
- Deferred — hosted deploy: re-run the migration and image upload against the managed Supabase project via `db push`, and confirm the hosted public Storage URLs resolve and the detail screen loads against the managed project.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Schema collision with Phase 1's existing `muscles`/`equipment` columns | Migration reuses the existing `equipment`/`category`/`license`/`license_author` columns, adds only `primary_muscles`/`secondary_muscles`/`instructions`/`image_path`, retains (does not drop) `muscles`, and an assertion confirms no duplicate column and preserved Phase 1 values. |
| Accidentally importing free-exercise-db images (unresolved license) | Importer is text-only for that source with an explicit guard + a verification assertion that no `image_path`/Storage object traces to free-exercise-db. |
| Reusing wger AGPL code instead of just its CC-BY-SA data | Enrichment consumes only the exported dataset (JSON/images), writes fresh import code; no wger backend code is vendored — checked in review. |
| Missing CC-BY-SA attribution on derived content | `license`/`license_author` persisted per exercise and rendered on the detail screen whenever wger content is shown; if derived data is committed to `/supabase`, a data-file LICENSE/attribution note ships with it; QA check enforces the screen line. |
| Enrichment re-run duplicates rows or clobbers Phase 1 seed | Routine is idempotent/additive keyed on the stable wger id (name fallback); run-twice verification against the local DB; no base re-import. |
| Coverage still uneven after both sources | Deterministic image placeholder (primary muscle → category → `generic`) + explicit "No instructions available yet" empty state guarantee a complete, non-blank screen for every exercise. |
| Public Storage bucket exposes more than intended | Bucket holds only attributed CC-BY-SA exercise images; read-only public policy, no user data in the bucket; same policy applies on local and hosted. |
| Catalog table mistakenly given an owner RLS policy and breaking public reads | Migration documents and ships a public SELECT policy; QA confirms the local anon client reads detail data without a JWT. |
| Local behavior diverges from hosted on deploy | Migration + bucket policy are identical artifacts applied via `db push` on the managed project later; the deferred hosted verification re-confirms parity before production. |

## Decision log

- Adopt the local-first dev path (`supabase start` provides Postgres + Auth + Realtime + Storage with generated keys; MCP/app run against localhost / `10.0.2.2`); hosted deploy (managed Supabase + `db push`, VPS + Caddy + domain/TLS) deferred as the production target, not removed (local-first dev path adopted 2026-06-17; hosted deploy deferred).
- Enrich the existing global `exercises` catalog (public-read, no owner policy) rather than add a user-owned table — exercise detail is global seed content, not per-user data (auto-resolved under autonomous planning mandate, 2026-06-17).
- Reconcile the Phase 1 schema: reuse the existing `equipment`/`category`/`license`/`license_author` columns, add only `primary_muscles`/`secondary_muscles`/`instructions`/`image_path`, and retain (do not drop) the existing `muscles` column — avoids a redundant/colliding column and a destructive change to Phase 1 data (auto-resolved under autonomous planning mandate, 2026-06-17).
- Ship a public SELECT policy for the catalog in the enrichment migration to satisfy the same-migration RLS gate while keeping the catalog readable by all (auto-resolved under autonomous planning mandate, 2026-06-17).
- Model instructions as an ordered `text[]` column and target muscles as `primary_muscles`/`secondary_muscles text[]` columns instead of child tables — proportional to a read-only catalog and matches both source shapes (auto-resolved under autonomous planning mandate, 2026-06-17).
- Use a dedicated public-read Supabase Storage bucket for wger CC-BY-SA images with a relative `image_path` resolved to a public URL client-side, on the local stack now and managed Storage on hosted deploy — `image_path` is the column name everywhere (auto-resolved under autonomous planning mandate, 2026-06-17).
- Treat free-exercise-db as a text-only gap-filler with an importer guard rejecting its images; wger text/images take precedence and are never overwritten (auto-resolved under autonomous planning mandate, 2026-06-17).
- Render a deterministic placeholder keyed on primary muscle → category → fixed `generic` bucket for exercises lacking a licensed image, and an explicit empty state for missing instructions, so every exercise yields a complete screen (auto-resolved under autonomous planning mandate, 2026-06-17).
- Render CC-BY-SA attribution on the detail screen whenever wger-sourced text or image is shown, and ship a data-file LICENSE/attribution note if derived CC-BY-SA data is committed to `/supabase` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Do not extend the MCP catalog read projection in this phase — no Phase-2 catalog read tool is established in the roadmap or architecture to extend, so the MCP surface is deferred (auto-resolved under autonomous planning mandate, 2026-06-17).
- Reach the detail screen by tapping an exercise row in the Phase 3 plan/day/exercise view, reusing the existing navigation host (auto-resolved under autonomous planning mandate, 2026-06-17).
- Make the enrichment routine idempotent and additive against existing rows, keyed on the stable wger id with the name as fallback, never re-importing the base catalog (Phase 1 owns that) (auto-resolved under autonomous planning mandate, 2026-06-17).