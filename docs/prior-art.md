# Prior Art

> Descriptive, living document. Indexed BY CONCERN, not by project. Add
> entries whenever new references surface; gaps are fine.

## Open exercise data & training domain model

### wger-project/wger

- Path: https://github.com/wger-project/wger — exercise dataset, `wger/exercises/`
- License: code AGPL-3.0-or-later; **exercise & ingredient data CC-BY-SA (3.0/4.0)**
- Verdict: reuse (data only) — seed Rack's exercise catalog from the CC-BY-SA dataset; never reuse the AGPL code.
- Date: 2026-06-17
- Notes:
  - ADOPT: the 400+ exercise catalog (name, muscles, equipment, category, plus per-exercise images and step-by-step instructions — coverage is uneven, so gaps are filled per exercise) as Rack's seed data, with attribution; the proven domain model concepts — routines → days → sets, supersets/circuits, double progression, rest timers.
  - AVOID: pulling in any AGPL-3.0 code. AGPL is viral for networked services — reusing wger's backend would force Rack's whole hosted stack under AGPL. Build the backend fresh. Keep CC-BY-SA attribution + share-alike on any derivative dataset.

## Programmable agent access (MCP write-through to fitness data)

### Juxsta/wger-mcp

- Path: https://github.com/Juxsta/wger-mcp
- License: MIT
- Verdict: reference-only — closest prior art for "agent creates routines"; its limits define Rack's USP.
- Date: 2026-06-17
- Notes:
  - ADOPT: tool granularity as a design reference — search exercises (read) + create/update/delete routines, days, exercises, sets (write); 12 tools total is a sane surface.
  - AVOID: stdio transport (local, single-user), env-var single API key, and request-response only with **no push to any client app**. Rack is the inverse: hosted remote MCP, per-user API keys, and every write propagates live to the native app.

## Read-only fitness-data MCPs (the opposite direction — anti-pattern for us)

### garmin / whoop / strava / trainingpeaks / xert / boostcamp MCP servers

- Path: e.g. https://github.com/Taxuspt/garmin_mcp, https://github.com/nissand/whoop-mcp-server-claude, https://github.com/Milofax/xert-mcp, https://github.com/alex-keyes/boostcamp-mcp
- License: mixed (mostly MIT)
- Verdict: reference-only — confirm the gap, do not copy the model.
- Date: 2026-06-17
- Notes:
  - ADOPT: nothing structurally; useful only to confirm the landscape.
  - AVOID: their whole shape — pulling closed third-party data INTO the LLM for analysis. Rack writes user-authored data OUT to an app the user owns, in real time. Different direction, different value.

## Native Android workout-logging UX

### noahjutz/GymRoutines & Flexify

- Path: https://f-droid.org/packages/com.noahjutz.gymroutines/ ; Flexify (F-Droid)
- License: GPL-family / FOSS (verify per app before copying any code)
- Verdict: reference-only — UX reference for native logging, not a code dependency.
- Date: 2026-06-17
- Notes:
  - ADOPT: fast set/rep/weight logging interaction, local-first responsiveness, exercise-library browsing patterns. The Recomp `artifact.html` prototype already encodes Rack's own opinionated take (kg + per-set reps + RIR + history disclosure).
  - AVOID: their local-only, no-backend, no-programmable-layer ceiling — exactly what Rack adds.

## Remote MCP authentication (multi-user, per-user API key)

### MCP spec — remote server auth & Streamable HTTP transport

- Path: MCP 2025+ spec; remote-MCP hosting/auth best-practice writeups
- License: n/a (spec/guidance)
- Verdict: reference — informs the rack-MCP auth design.
- Date: 2026-06-17
- Notes:
  - ADOPT: Streamable HTTP transport for a hosted multi-user server; static **per-user API key** issued in-app, sent server-side, mapping key → user so every tool call is scoped to that user's data; keys never in client code.
  - AVOID: full OAuth 2.1 + PKCE — correct for public multi-tenant SaaS, over-engineered for a friends-and-family circle on one VPS. A rotatable per-user API key over HTTPS is proportionate.

## Real-time sync & live edit highlighting

### Collaborative-editing patterns (WebSocket presence, Yjs/CRDT)

- Path: Yjs, CRDT/collaborative-editor system designs, WebSocket presence patterns
- License: n/a (patterns)
- Verdict: reference — adopt the visible-remote-edit idea, reject the heavy machinery.
- Date: 2026-06-17
- Notes:
  - ADOPT: WebSocket push of server-authoritative changes; visual highlight of remote/agent edits as they land; optimistic UI on the app's own writes.
  - AVOID: CRDT/Yjs conflict-free merging. Rack's concurrency is light — a single owner plus their own agent editing the owner's data from two surfaces. Server-authoritative state + per-entity version + last-write-wins is enough; CRDTs would be cost without benefit.

## Exercise media — images & step-by-step instructions (Phase 7)

### yuhonas/free-exercise-db

- Path: https://github.com/yuhonas/free-exercise-db — `exercises.json` + `exercises/` images
- License: **data Unlicense (public domain)**; **image license unstated/unresolved** (open issue #2)
- Verdict: reuse (data) / avoid (images) — broaden text coverage where wger is thin; do not ship its images.
- Date: 2026-06-17
- Notes:
  - ADOPT: the ~800-exercise public-domain JSON (name, primary/secondary muscles, equipment, category, step-by-step instructions) to complement wger and fill text gaps.
  - AVOID: shipping its exercise images — the image license is undocumented. Keep wger's CC-BY-SA images as the primary set and fill remaining gaps with self-produced or explicitly-licensed media.

### ExerciseDB API

- Path: https://github.com/ExerciseDB/exercisedb-api — hosted API, 1300+/11000+ exercises with gifs/images
- License: open-source API; media licensing/cost unclear
- Verdict: reference-only — do not take a runtime dependency on a third-party API for core data.
- Date: 2026-06-17
- Notes:
  - ADOPT: nothing structurally; a coverage benchmark only.
  - AVOID: a networked dependency on a third-party catalog for Rack's core exercise data — Rack owns a seeded, self-hosted catalog.

## Rest & session timers — Android, backgrounded (Phase 8)

### TimeR Machine / Just Another Workout Timer / Privacy Friendly Interval Timer

- Path: F-Droid `io.github.deweyreed.timer.other`, `com.blockbasti.justanotherworkouttimer`, `org.secuso.privacyfriendlyintervaltimer`
- License: FOSS (GPL-family; verify per app before copying code)
- Verdict: reference-only — pattern reference for a reliable Android timer.
- Date: 2026-06-17
- Notes:
  - ADOPT: run the rest timer in a **foreground service** with a persistent notification so it survives backgrounding/Doze; vibrate/sound on completion; per-set defaults by exercise type (compound 2–3 min, isolation 60–90 s, superset 60–90 s, circuit 30–45 s).
  - AVOID: building a standalone interval-timer app — Rack's timer is embedded in the logging flow, not a separate surface.

### LiftLog / GymRoutines / FitoTrack (built-in rest timer)

- Path: F-Droid `com.noahjutz.gymroutines`, `de.tadris.fitness`; LiftLog
- License: FOSS (verify per app)
- Verdict: reference-only — UX reference for an embedded rest timer.
- Date: 2026-06-17
- Notes:
  - ADOPT: auto-start the rest timer on set completion; tap-based, minimal interaction inside the log flow.
  - AVOID: their local-only ceiling — Rack adds sync + agent authoring around the same logging UX.

## Guided workout session — active player UX (Phase 9)

### Strong / Hevy (closed-source, de-facto UX standard) + LiftLog / GymRoutines (FOSS flow)

- Path: Strong / Hevy (commercial); FOSS `com.noahjutz.gymroutines`, LiftLog
- License: closed (Strong/Hevy); FOSS (the trackers)
- Verdict: reference-only — UX reference for the session player.
- Date: 2026-06-17
- Notes:
  - ADOPT: step-through-the-day flow — current exercise/set in focus, tick sets, rest timer auto-prompts between sets, superset/circuit rotation guidance, a session summary on finish.
  - AVOID: feature bloat (social feeds, marketplace) — keep the player to the active-session loop.

## Push notifications & reminders — Supabase + FCM (Phase 10)

### Supabase official guide — Edge Function + Database Webhook + FCM HTTP v1

- Path: https://supabase.com/docs/guides/functions/examples/push-notifications
- License: docs (CC); the pattern is reusable
- Verdict: reuse (the pattern) — matches the Supabase-full architecture.
- Date: 2026-06-17
- Notes:
  - ADOPT: store an `fcm_token` per user; a Database Webhook on INSERT into a `notifications` table triggers an Edge Function that calls FCM HTTP v1 — exactly how the optional "your agent updated your plan" push fits the live-sync USP.
  - ADOPT: for rest-timer-done and workout reminders, prefer **local scheduled notifications** (WorkManager/AlarmManager) — no server round-trip needed.
  - AVOID: a third-party push SaaS (OneSignal) — direct FCM via the Edge Function is enough; one less dependency and account.

## Charts & dashboards — Jetpack Compose (Phase 11)

### patrykandpatrick/vico

- Path: https://github.com/patrykandpatrick/vico — `compose-m3` module
- License: Apache-2.0
- Verdict: reuse (candidate library) — Compose-native charts.
- Date: 2026-06-17
- Notes:
  - ADOPT: Vico for volume/progress/streak charts — Compose-native, Material 3, actively maintained.
  - AVOID: MPAndroidChart (View-system, wrapped in AndroidView) unless a chart type Vico lacks forces it.

## 1RM estimation (Phase 13)

### Epley / Brzycki formulas

- Path: well-known formulas (no repo) — Epley `1RM = w·(1 + reps/30)`, Brzycki `1RM = w·36/(37−reps)`
- License: n/a (public formulas)
- Verdict: reference — implement directly, no dependency.
- Date: 2026-06-17
- Notes:
  - ADOPT: compute an estimate from the heaviest logged set; present it as guidance.
  - AVOID: over-engineering (multi-formula averaging, ML) — one documented formula, labeled an estimate, is enough.

## App navigation & logging-screen UX (Recomp UX Overhaul)

### Strong / Hevy (de-facto UX standard) + GymRoutines / LiftLog (FOSS flow)

- Path: Strong / Hevy (commercial, closed); FOSS cross-ref `com.noahjutz.gymroutines`, LiftLog
- License: closed (Strong/Hevy); FOSS (the trackers)
- Verdict: reference-only — UX conventions for app navigation and set/rep logging; informs the Recomp UX Overhaul living-spec.
- Date: 2026-06-18
- Notes:
  - ADOPT: bottom-tab **quick-nav** for 3–5 frequent destinations with an overflow for the rest (Material-aligned); the per-exercise **set table** (`set | previous | kg | reps | ✓`) with a previous-performance column and a per-set check that auto-starts the rest timer; a **launcher** home (today/next-workout + a single Start CTA) that separates plan-overview from active-session logging.
  - AVOID: a left burger-drawer as primary navigation (off-pattern for modern fitness apps); cramming always-open per-set inputs into the plan overview (the old `artifact.html` model); feature bloat (social feeds, marketplace).

## Programmable plan authoring — nested / transactional write (the bulk-authoring concern)

> Surfaced by the MCP beta test (`beta-test-protocol.md` §4.3): authoring a
> 5-day / 31-exercise plan meant one `create_*` round-trip per row plus a
> sequential `planId → dayId → exerciseId` ID chain. Hevy already solved the
> shape.

### Hevy public API & chrisdoc/hevy-mcp

- Path: https://github.com/chrisdoc/hevy-mcp (`openapi-spec.json`); Hevy public REST API
- License: code MIT (hevy-mcp); Hevy app/API closed, commercial
- Verdict: reference-only — adopt the nested-write *shape*, never a runtime dependency on a closed competitor's API.
- Date: 2026-06-19
- Notes:
  - ADOPT: a single `create-routine` call carrying the **full nested tree** — `title` + `exercises[]` (each `exercise_template_id`, nullable `superset_id`, `rest_seconds`, `notes`) + nested `sets[]` (`type`, `weight_kg`, `reps`, `rep_range {start,end}`, `rpe`). This collapses Rack's sequential round-trip chain into one transactional write — the direct fix for the beta's authoring friction. Also: a dedicated `search-exercise-templates` tool distinct from list, and an in-memory exercise-template cache.
  - AVOID: Hevy's "fragile, silent-failure" set-type/wrapper conventions (a wrong field is silently dropped) — Rack's Zod boundary must fail **loudly** with a specific message. Also avoid Hevy's exercise-template create-only limitation (no edit/delete via API).

## Structured set prescription — rep range, effort (RIR/RPE), rest

> Surfaced by the beta (§4.2): `target` was unstructured free text (`3 × 6–8`)
> and `rir` a single integer, so planned rep- and RIR-*ranges* could not be
> stored as evaluable fields.

### Hevy set model + RIR/RPE practice (Fitbod, TrainingPeaks, N1 Training)

- Path: Hevy `openapi-spec.json` set object; https://fitbod.zendesk.com (RiR), https://help.trainingpeaks.com (programming rest/RPE/rep-range/tempo/RIR), https://n1.training/effort-estimation-rpe-vs-rir/
- License: n/a (API shape + domain practice)
- Verdict: reference — implement the typed fields directly; no dependency.
- Date: 2026-06-19
- Notes:
  - ADOPT: `rep_range {start, end}` as the structured target instead of a string; per-exercise `rest_seconds`; effort as a typed field. For Rack's RIR-range requirement, model `rir_low`/`rir_high` (RPE↔RIR is well defined — RPE 7≈3 RIR, 8≈2, 9≈1 — so an RPE-only model like Hevy's loses the range Rack needs). TrainingPeaks treats rest, RPE, rep-range, tempo and RIR as first-class per-set fields — the target completeness bar.
  - ADOPT (research): in a superset/circuit the effort/RIR prompt belongs **after** all member sets complete; between-exercise rest in supersets is 0–60 s.
  - AVOID: shipping Hevy's full set-type richness (`warmup`/`normal`/`failure`/`dropset`) up front — start with range + rest + effort; add set types behind a scope flag only when a real plan needs them.

## Superset / circuit grouping — explicit

> Surfaced by the beta (§4.2): grouping was carried only by a shared
> `superset_label` string; superset-vs-circuit and a group rest were not
> expressible.

### Hevy `superset_id`

- Path: Hevy `openapi-spec.json` exercise object (`superset_id`: integer, nullable)
- License: n/a (API shape)
- Verdict: reference — adopt the explicit-group model.
- Date: 2026-06-19
- Notes:
  - ADOPT: an explicit numeric group id on the exercise (Hevy's `superset_id`) to bind members, extended for Rack with a group **type** (superset vs circuit) and a group-level **rest** — replacing the implicit shared-label string that could express neither.
  - AVOID: inferring group kind from member count (the beta's implicit model); make type explicit.

## Exercise catalog — search relevance, filters, aliases, custom exercises

> Surfaced by the beta (§4.1, §4.6): `search_exercises` ran literal
> `ilike '%q%'` + alphabetical order + `limit`, so multi-word queries returned
> nothing, relevant rows were truncated, equipment variants (Cable/Machine)
> were unreachable, and the German plan could not be matched to the
> English-only catalog.

### Strong / Hevy / Exercise.com / WorkoutX / FitPros (search UX) + yuhonas/free-exercise-db (schema) + Postgres pg_trgm + FTS

- Path: https://www.exercise.com/support/search-app-exercise-database/, https://workoutxapp.com/blog/top-10-exercise-databases-fitness-apps.html, https://github.com/yuhonas/free-exercise-db; pg_trgm / `tsvector` docs
- License: search patterns n/a; free-exercise-db data Unlicense (images unresolved — see Phase 7 entry)
- Verdict: reference (patterns) / reuse (free-exercise-db aliases & coverage, data only)
- Date: 2026-06-19
- Notes:
  - ADOPT: instant search **filterable by muscle group + equipment + movement pattern** is table-stakes across the field (Exercise.com, WorkoutX, FitPros) — `search_exercises` must gain equipment/muscle filters, not only `category`. Per-exercise **aliases / alt-names** (free-exercise-db schema) to bridge German→English and variant naming. An **`is_custom`** user-created exercise (Hevy templates) so a plan can reference an exercise absent from the canonical catalog — removes the beta's "must substitute a variant" workaround.
  - ADOPT: Postgres **hybrid search** — `pg_trgm` GIN trigram similarity (typo/partial/fuzzy) combined with full-text `tsvector` ranking, with **token-AND** so `shoulder press dumbbell` matches — replacing literal substring + alphabetical + limit. Return canonical/most-relevant first.
  - ADOPT: catalog **curation** — normalize the equipment vocabulary (the wger seed has no clean `Cable`/`Machine` values; they live inside exercise names), mark canonical base exercises (`Lateral Raise`, `Lat Pulldown`, `Triceps Pushdown`), and filter non-English / junk rows (`lento avanti seduto`).
  - AVOID: a runtime dependency on a third-party search API — search stays in Postgres over the owned, seeded catalog.

## MCP tool-surface ergonomics & discovery

> Surfaced by the beta (§4.4): the rack tools were not immediately usable —
> several client tool-searches were needed to assemble the working set, and
> `ping` (which exists on the server) was never surfaced.

### MCP spec tool annotations + AWS / Apigene tool-design guidance

- Path: https://modelcontextprotocol.io/specification/2025-06-18/server/tools, https://docs.aws.amazon.com/prescriptive-guidance/latest/mcp-strategies/mcp-tool-strategy.html, https://apigene.ai/blog/mcp-tools
- License: spec / guidance
- Verdict: reference — informs the tool-surface polish.
- Date: 2026-06-19
- Notes:
  - ADOPT: **annotate every tool** (`readOnlyHint` on the read tools, idempotency hints where true); **structured descriptions** (one-line purpose + exact parameter constraints + output shape) so a client's tool-search resolves the right tools in one step; keep the surface balanced and well-named. Consider richer descriptions / a small index so discovery is not a multi-search hunt.
  - AVOID: over-fine granularity that explodes the tool count and confuses selection, and over-coarse tools that hide capability — Hevy-mcp's ~30 grouped tools is a sane ceiling.
