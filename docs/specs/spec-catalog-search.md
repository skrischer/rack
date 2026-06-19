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
- [ ] Non-English / junk rows (e.g. `lento avanti seduto`) no longer pollute the
      top of relevant result sets.
- [ ] `make verify` is green, including integration tests over the new search
      behavior.

## Scope

### In scope

- A migration enabling `pg_trgm` (and `unaccent` if needed), adding `aliases
  text[]` and a canonical marker to `public.exercises`, and creating the search
  index (GIN trigram on name + aliases; a generated `tsvector` for ranking if the
  hybrid needs it).
- Catalog curation in the seed pipeline: populate `aliases` (from wger
  non-English translations plus a curated set for the common movements), mark
  canonical base entries, normalize the equipment vocabulary, and down-rank or
  flag non-English / junk rows.
- Rebuilding the `search_exercises` MCP tool: token-AND multi-word matching,
  relevance ranking, `equipment` + `muscle` filters, canonical-first ordering, a
  sane default limit, and a structured tool description.
- Vitest integration coverage for the rebuilt search (multi-word, ranking,
  filters, alias match).

### Out of scope

- Custom / user-authored exercises (`is_custom` / non-null `user_id`) — Phase 15.
- Structured prescription, RIR ranges, supersets/circuits — Phase 15.
- Nested transactional authoring and the cross-tool annotation/discovery pass —
  Phase 16.
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
| Search is a Postgres trigram (`pg_trgm`) + token-AND match with relevance ranking, not `ilike '%q%'` + alphabetical | Prior-art harvest; the literal-substring + alpha + limit model is exactly what truncated and broke the beta queries | 2026-06-19 |
| `aliases text[]` on `exercises`, seeded from wger non-English translations plus a curated set for common movements | wger exports carry per-language names; free-exercise-db precedent for an alias field; bridges the German↔English mismatch (beta §4.6) | 2026-06-19 |
| The `muscle` filter reads the existing `primary_muscles` (fallback `muscles`) column | The enrich migration already distinguishes primary/secondary; no new muscle column | 2026-06-19 |
| Catalog stays public-read; the migration is additive and re-asserts the public SELECT policy | Constitution (catalog is the one public table) + enrich-migration precedent | 2026-06-19 |
| Custom / user exercises are deferred to Phase 15 | Roadmap boundary — custom exercises are an authoring concern, not catalog search | 2026-06-19 |
| OPEN — curation depth: full re-curation of all ~848 rows vs. a targeted canonical layer (alias + flag the common movements, leave the long tail) | resolved at the spec-acceptance gate | — |
| OPEN — equipment-normalization depth: derive Cable/Machine from names (best-effort) vs. a curated remap vs. only exposing a filter over existing values | resolved at the spec-acceptance gate | — |
| OPEN — ship a `resolve_exercise` (name → single best match) helper in this phase vs. defer it to Phase 16 (authoring ergonomics) | resolved at the spec-acceptance gate | — |

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
