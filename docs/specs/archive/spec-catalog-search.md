# Spec: Phase 14 — Exercise catalog quality & search

> Created: 2026-06-19

Make the exercise catalog agent-resolvable: relevance-ranked, multi-word,
filterable search over a curated catalog with aliases — replacing the literal
`ilike '%q%'` + alphabetical + limit that, in the MCP beta
(`beta-test-protocol.md` §4.1, §4.6), truncated results, failed on multi-word
queries, could not target equipment, and could not bridge the German plan to the
English catalog. This spec carries no lifecycle state — acceptance is the spec
merged on the default branch with a milestone and issues.

## Outcome

- [ ] `search_exercises` matches multi-word queries by token-AND, so
      `shoulder press dumbbell` returns the dumbbell shoulder-press rows instead
      of an empty list.
- [ ] Results are relevance-ranked (best match first), and the default limit no
      longer drops relevant rows — a search for `press` surfaces Leg Press and
      Shoulder Press, not only the alphabetical A–F head.
- [ ] `search_exercises` accepts `equipment` and `muscle` filters in addition to
      `category`, and they narrow results correctly.
- [ ] The common base movements called out in the beta (Lateral Raise, Lat
      Pulldown, Triceps Pushdown, Shoulder Press) each resolve to a canonical
      catalog entry that ranks first for its plain name.
- [ ] Exercises carry `aliases`; a German alias query (e.g. `Seitheben`) and a
      naming variant both match the right catalog row.
- [ ] With `q` omitted, `search_exercises` browses by `equipment` / `muscle` /
      `category` filters alone (e.g. all dumbbell shoulder exercises).
- [ ] Non-English / junk rows (e.g. `lento avanti seduto`) no longer pollute the
      top of relevant result sets.
- [ ] `make verify` is green, including integration tests over the new search
      behavior.

## Scope

### In scope

- A migration enabling `pg_trgm` and `unaccent`, adding `aliases text[]` and
  `is_canonical boolean not null default false` to `public.exercises`, and
  creating a GIN trigram index covering name and aliases. No `tsvector` / FTS —
  the catalog is short names, so trigram similarity carries both matching and
  ranking.
- Catalog curation in the seed pipeline: populate `aliases` (from wger
  non-English translations plus a curated set for the common movements), mark
  canonical base entries, normalize the equipment vocabulary, and down-rank or
  flag non-English / junk rows.
- Rebuilding the `search_exercises` MCP tool: optional `q` (omitted → a
  filter-only browse), token-AND multi-word matching over name + aliases,
  similarity ranking, `equipment` + `muscle` filters (alongside `category`),
  canonical-first tiebreak, a sane default limit, a SELECT projection that
  includes `aliases` and `is_canonical`, and a structured tool description.
- Vitest integration coverage for the rebuilt search (multi-word, ranking,
  filters, alias match).

### Out of scope

- Custom / user-authored exercises (`is_custom` / non-null `user_id`) — Phase 15.
- Structured prescription, RIR ranges, supersets/circuits — Phase 15.
- Nested transactional authoring, the cross-tool annotation/discovery pass, and
  the `resolve_exercise` helper — Phase 16.
- Android exercise-picker UI redesign — the app consumes the same catalog rows;
  no client UI work beyond rendering the existing fields.

## Constraints

- Search stays in Postgres over the owned, seeded catalog — no runtime
  dependency on a third-party search API (constitution: dependency discipline).
- The migration is additive and reuses the existing `muscles` /
  `primary_muscles` / `secondary_muscles` / `equipment` columns; it drops no
  Phase 1 / Phase 7 column (precedent: the enrich migration explicitly reused
  them).
- `exercises` remains the one public-read catalog (anon + authenticated SELECT);
  the same-migration RLS policy is re-asserted (precedent: enrich migration). No
  `user_id` is added in this phase.
- `search_exercises` keeps running on the per-request user-scoped Supabase client
  (the catalog is public-read); its input stays Zod-validated at the MCP boundary
  (constitution).
- Extensions are enabled via `create extension if not exists` and must be
  available in the local Supabase Docker stack.

## Prior art

- [Exercise catalog — search relevance, filters, aliases, custom exercises](../prior-art.md#exercise-catalog--search-relevance-filters-aliases-custom-exercises)
  — the core: pg_trgm + FTS hybrid, token-AND, equipment/muscle filters, aliases,
  catalog curation.
- [Open exercise data & training domain model](../prior-art.md#open-exercise-data--training-domain-model)
  — wger is the seed source and carries per-language translations usable as the
  alias source.
- [Exercise media — images & step-by-step instructions (Phase 7)](../prior-art.md#exercise-media--images--step-by-step-instructions-phase-7)
  — free-exercise-db's alias field and broader coverage complement wger.
- [MCP tool-surface ergonomics & discovery](../prior-art.md#mcp-tool-surface-ergonomics--discovery)
  — structured tool description so the rebuilt search is self-explaining to a
  client.

## Human prerequisites

- [ ] `none` — no secret or account is required. The alias re-fetch reads the
      public wger API (`wger.de`, no auth) and free-exercise-db is already
      committed; the local Supabase stack from `docs/workflow.md` provides
      everything else.

## Prior decisions

| Decision | Rationale | Date |
|---|---|---|
| Each whitespace token of `q` is matched against name + aliases via `pg_trgm` similarity (token-AND: every token must match a row); results order by summed token similarity, with `is_canonical` desc as the tiebreak; a GIN trigram index backs it. No `tsvector` / FTS | Prior-art (trigram fits short titles); one index/ranking system, not two; directly fixes the beta's empty multi-word results and alphabetical truncation | 2026-06-19 |
| `q` is optional; when omitted, the tool is a filter-only browse (equipment / muscle / category) ordered `is_canonical` desc then name | Beta §4.1 needed "all dumbbell shoulder exercises" with no good text term; the filters make browse meaningful | 2026-06-19 |
| `aliases text[]` on `exercises`, seeded from wger non-English translations plus a curated set for common movements; matched in search alongside name | wger exports carry per-language names; free-exercise-db precedent for an alias field; bridges the German↔English mismatch (beta §4.6) | 2026-06-19 |
| The `muscle` filter matches when the value is contained in `coalesce(nullif(primary_muscles, '{}'), muscles)` — primary muscles if populated, else the legacy union; secondary muscles are not matched | Deterministic, reuses the enrich-migration columns, and closes the silent-NULL gap a fallback would otherwise leave | 2026-06-19 |
| Canonical marker is `is_canonical boolean not null default false` | Names the column the migration adds and the ranking tiebreak | 2026-06-19 |
| Enable `unaccent` alongside `pg_trgm` | German aliases carry umlauts (ä/ö/ü); `unaccent` folds them so `Rückenstrecker` and `Ruckenstrecker` both match | 2026-06-19 |
| The `search_exercises` SELECT projection gains `aliases` and `is_canonical` (current `EXERCISE_COLUMNS`, `mcp/src/tools/exercises.ts:15`) | The rebuilt tool must return the new fields, not silently drop them | 2026-06-19 |
| Catalog stays public-read; the migration is additive and re-asserts the public SELECT policy | Constitution (catalog is the one public table) + enrich-migration precedent | 2026-06-19 |
| Custom / user exercises are deferred to Phase 15 | Roadmap boundary — custom exercises are an authoring concern, not catalog search | 2026-06-19 |
| Targeted canonical layer: the common movements get a canonical entry + aliases (aliases seeded automatically from wger translations); the long tail stays searchable but uncurated. The four beta-named movements (Lateral Raise, Lat Pulldown, Triceps Pushdown, Shoulder Press) are guaranteed canonical, so the Verification tests are stable | Spec-acceptance gate — pragmatic, covers the beta cases without a full manual sweep | 2026-06-19 |
| Equipment normalization is best-effort: derive Cable/Machine from the exercise name, keep the raw equipment value, expose the filter regardless | Spec-acceptance gate — makes Cable/Machine reachable (beta §4.1) without a full curated remap | 2026-06-19 |
| `resolve_exercise` is deferred to Phase 16 (authoring ergonomics); Phase 14's canonical-first ranking already returns the best match as the top result | Spec-acceptance gate — keeps Phase 14 to catalog + search; the helper fits the nested-authoring phase | 2026-06-19 |

## Tracking

- Milestone: created when this spec merges.
- Issues: one per implementable step (migration; curation/seed; search rebuild;
  tests), created from this spec after merge.

Each issue references this spec path in its body.

## Verification

- [ ] `make verify` passes.
- [ ] `search_exercises("shoulder press dumbbell")` returns dumbbell
      shoulder-press rows (token-AND, not empty).
- [ ] `search_exercises("press")` includes Leg Press and Shoulder Press, not
      only alphabetical A–F rows.
- [ ] `search_exercises("lateral raise")` ranks the canonical Lateral Raise
      first.
- [ ] `search_exercises` with `equipment=Dumbbell` and `muscle=Shoulders`
      narrows to dumbbell shoulder work.
- [ ] `search_exercises` with no `q` and `equipment=Dumbbell`,
      `muscle=Shoulders` lists dumbbell shoulder exercises (filter-only browse).
- [ ] A German alias query (`Seitheben`) matches Lateral Raise via `aliases`.
- [ ] Human QA gate (review): top-result relevance for a sample of the beta's
      planned exercises is materially better than the alphabetical baseline (no
      machine check covers subjective ranking quality).

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Full catalog re-curation is a large manual effort | Resolve curation depth at the gate; automate alias seeding from wger translations; a targeted canonical layer covers the common movements first |
| `pg_trgm` / `unaccent` unavailable in the local stack | Verify in the migration's first run against local Supabase; both ship with Supabase Postgres |
| Deriving Cable/Machine from names is imperfect | Best-effort derivation, keep the raw equipment value, expose the filter regardless |
| A wger re-fetch changes catalog rows / ids | Keep the idempotent name-key seed pattern (existing `import-wger` model); additive only |

## Decision log

- 2026-06-19: Spec drafted from the beta-test findings and the prior-art harvest.
- 2026-06-19: Review (REQUEST_CHANGES) addressed — committed the trigram
  token-AND search formula (no FTS), the `muscle`-filter `coalesce` semantics,
  the `is_canonical` column, optional-`q` filter browse, and the
  `resolve_exercise` contract.
- 2026-06-19: Spec-acceptance gate resolved the three open decisions — targeted
  canonical layer, best-effort equipment derivation, `resolve_exercise` deferred
  to Phase 16. Human prerequisites: none. Spec accepted.

## Implementation outcome (accepted 2026-06-19)

Built and merged (milestone #15: #188, #189, #190); MCP suite green.
- #188 migration: `pg_trgm` + `unaccent`; `exercises.aliases text[]` + `is_canonical bool`; an IMMUTABLE `public.exercises_search_text(name, aliases)` helper indexed by a GIN `gin_trgm_ops` index (array isn't directly trigram-indexable, so the flattening helper is the single viable form); public-read RLS re-asserted.
- #189 seed: aliases sourced from a wger all-language re-fetch (the `language=2` filter restricts translations to English — dropped it) plus a curated set; four beta movements guaranteed canonical; Cable/Machine best-effort derived from names (raw retained); junk down-ranked via the `is_canonical` tiebreak, not a junk-detector column. NOTE: `primary_muscles` is enriched separately (not in `seed.sql`), so it is empty after a plain `db reset` — the muscle filter relies on its `coalesce(...,'muscles')` fallback.
- #190 search: rebuilt as a SECURITY INVOKER SQL RPC `public.search_exercises(...)` (PostgREST cannot express token-AND + summed similarity); token-AND `word_similarity >= 0.4`; rank by summed similarity, then `is_canonical`, then name-similarity tiebreak; equipment/muscle/category filters; optional-`q` filter browse; limit clamped 1..100 in SQL and Zod. `unaccent` intentionally not in the search path (would defeat the GIN index). `resolve_exercise` remains deferred.
