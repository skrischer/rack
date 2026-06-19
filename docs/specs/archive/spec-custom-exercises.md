# Spec: Phase 17 — Custom / user-authored exercises

> Created: 2026-06-19

Let a plan reference an exercise that is not in the seeded catalog: a custom
exercise is an `exercises` row the user owns, authored via the MCP and usable in
a plan exactly like a catalog row. Closes the MCP beta gap
(`beta-test-protocol.md` §4.2): a planned movement with no catalog entry could
only be mapped to a substitute variant. Split out of Phase 15 at its acceptance
gate. This spec carries no lifecycle state — acceptance is the spec merged on
the default branch with a milestone and issues.

## Outcome

- [ ] An MCP client creates a custom exercise (a row the caller owns) via a
      `create_exercise` tool and gets back its id.
- [ ] A plan exercise can reference that custom exercise's id like any catalog
      row (`plan_exercises.exercise_id`), with no change to `plan_exercises`.
- [ ] `search_exercises` returns the caller's own custom exercises alongside the
      catalog, and the result marks which rows are custom.
- [ ] A user can only read/write their own custom exercises; the shared catalog
      stays read-only to users; one user never sees another user's customs
      (verified by a cross-user isolation test).
- [ ] `make verify` is green.

## Scope

### In scope

- A migration adding `user_id uuid null references auth.users (id) on delete
  cascade` to `public.exercises` (null = shared catalog, non-null = a user's
  custom), with distinct RLS policies (not one `FOR ALL`): replace the SELECT
  policy with `FOR SELECT using (user_id IS NULL OR user_id = auth.uid())`; add
  `FOR INSERT with check (user_id = auth.uid())`; `FOR UPDATE using (user_id =
  auth.uid()) with check (user_id = auth.uid())`; `FOR DELETE using (user_id =
  auth.uid())`. Grants extended to INSERT/UPDATE/DELETE for `authenticated`. A
  partial index `on exercises (user_id) where user_id is not null` for own-row
  lookup.
- A `create_exercise` MCP write tool: user-scoped, injects `user_id =
  auth.uid()`, Zod-validated; the catalog row it produces is searchable. The
  camelCase input maps to the real columns (`name`, `category`, `equipment`,
  `muscles`/`primary_muscles` per the Phase 7 enrich schema, `aliases` per
  Phase 14) — the implementer confirms the column set against the migrations.
- `search_exercises` (Phase 14) returns own customs via RLS without a query
  change, but its projection (`EXERCISE_COLUMNS`, `mcp/src/tools/exercises.ts`,
  the constant Phase 14 already extends) **must** add `user_id` or a computed
  `is_custom` so customs are marked.
- Tests: MCP integration for create + reference-in-plan; a cross-user isolation
  test (a custom exercise is invisible and unwritable to another user).

### Out of scope

- App-side authoring of custom exercises — authoring is the agent's job
  (`docs/vision.md`); the app renders a custom exercise's name via the existing
  catalog join with no change. A "custom" badge is a Recomp UX Overhaul
  living-spec concern.
- Structured prescription / grouping — Phase 15 (a plan references a custom
  exercise regardless of prescription shape).
- Catalog search internals — Phase 14 (this phase only widens what RLS returns
  and adds the custom indicator).
- `update_exercise` / `delete_exercise` for managing own customs — deferred (a
  later nicety; the FK RESTRICT already protects a referenced custom).

## Constraints

- The custom exercise reuses the existing `exercises` schema and the existing
  `plan_exercises.exercise_id` foreign key (already `on delete restrict`, so a
  custom exercise referenced by a plan cannot be deleted) — no `plan_exercises`
  change (constitution: minimal; architecture: custom exercise = same table with
  `user_id`).
- RLS ships in the same migration; the shared catalog (`user_id IS NULL`) stays
  read-only to every user, custom rows are owner-scoped, and `anon` (whose
  `auth.uid()` is null) sees only the catalog (constitution: per-table RLS).
- The MCP `create_exercise` runs as the resolved user, injects `user_id` from the
  auth context (never from input), and is Zod-validated (constitution: no
  `user_id` from the caller; strict boundary).
- No service-role write to `exercises` for user rows (constitution).

## Prior art

- [Exercise catalog — search relevance, filters, aliases, custom exercises](../prior-art.md#exercise-catalog--search-relevance-filters-aliases-custom-exercises)
  — Hevy's `is_custom` user-created templates; the model for letting a plan
  reference an exercise absent from the canonical catalog.
- [Programmable plan authoring — nested / transactional write (the bulk-authoring concern)](../prior-art.md#programmable-plan-authoring--nested--transactional-write-the-bulk-authoring-concern)
  — Hevy exposes `create-exercise-template`; `create_exercise` is the equivalent.

## Human prerequisites

- [ ] `none` — migration + MCP changes only; the local Supabase stack covers
      development. No secret or account.

## Prior decisions

| Decision | Rationale | Date |
|---|---|---|
| A custom exercise is an `exercises` row with `user_id` set, referenced by `plan_exercises.exercise_id` like any catalog row; no `plan_exercises` change | Architecture doc already states this; reuses the existing FK (`on delete restrict`); prior-art Hevy `is_custom`; minimal | 2026-06-19 |
| RLS as distinct per-action policies (not `FOR ALL`): SELECT `user_id IS NULL OR user_id = auth.uid()`; INSERT/UPDATE/DELETE scoped to `user_id = auth.uid()` (UPDATE carries both `using` and `with check`); `anon` keeps catalog-only read | A `FOR ALL` policy would narrow SELECT too and hide the catalog from authenticated users; catalog stays public read-only, customs owner-scoped, no cross-user leak (constitution per-table RLS) | 2026-06-19 |
| `create_exercise` input: `name` required; `category`, `equipment`, `primaryMuscles`, `aliases` optional; `is_canonical` stays false; `user_id` injected from auth | A custom exercise needs to be nameable and searchable; canonical is a curated catalog property, not a user one | 2026-06-19 |
| Custom-ness is derived from `user_id` (no separate `is_custom` column); `search_exercises` surfaces it in the projection | No redundant column; the user-scoped search already returns own customs via RLS | 2026-06-19 |
| Depends on Phase 14 (#15) only — the `exercises` search + projection — not Phase 15 | A plan references a custom exercise irrespective of prescription structure; this corrects the roadmap intent's over-stated Phase 15 edge | 2026-06-19 |
| `exercises` stays plain reference data — no `source`/`updated_at`, not added to the Realtime publication; the referencing `plan_exercises` row already highlights live | Spec-acceptance gate — keeps the catalog table clean; custom-exercise highlighting via the plan row is enough | 2026-06-19 |
| Tool surface is `create_exercise` only; `update_exercise`/`delete_exercise` are deferred | Spec-acceptance gate — create closes the core gap (reference a missing exercise); management is a later nicety, delete is FK-RESTRICT-protected anyway | 2026-06-19 |

## Tracking

- Milestone: created when this spec merges.
- Issues: one per implementable step (migration + RLS; `create_exercise` tool +
  search projection; isolation/integration tests), created after merge.

Each issue references this spec path in its body.

## Verification

- [ ] `make verify` passes.
- [ ] `create_exercise` writes a row owned by the caller; a `create_plan_exercises`
      (or `create_plan`) referencing its id succeeds and reads back.
- [ ] `search_exercises` returns the caller's custom alongside the catalog and
      marks it custom.
- [ ] Cross-user isolation: user B cannot read, update, or delete user A's custom
      exercise; neither can read/write the catalog rows as writes.
- [ ] `anon` sees only catalog rows (no customs).
- [ ] Deleting a custom exercise referenced by a plan is refused (FK RESTRICT).

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| The RLS change turns a pure-public table into a mixed one and could leak customs | Single combined SELECT policy `user_id IS NULL OR user_id = auth.uid()`; explicit cross-user isolation test in the acceptance set |
| Phase 14 also alters `exercises` (aliases, is_canonical, index) | Both migrations are additive on distinct columns; Phase 17 depends on Phase 14 so it lands after, on top of the Phase 14 schema |
| Adding `source`/Realtime to `exercises` (if chosen) affects catalog rows too | Resolve at the gate; if taken, the trigger/publication apply uniformly and catalog rows simply never change `source` |

## Decision log

- 2026-06-19: Spec drafted from the beta §4.2 mandatory-catalog gap and the
  Phase 15 acceptance-gate decision to split custom exercises into their own
  phase.
- 2026-06-19: Review (APPROVE) non-blocking findings folded in — distinct
  per-action RLS policies (UPDATE `using`+`with check`, SELECT untouched/compound),
  the partial index predicate (`where user_id is not null`), the explicit
  `EXERCISE_COLUMNS` projection change, and the camelCase→column mapping.
- 2026-06-19: Spec-acceptance gate — `exercises` stays plain reference data (no
  source/Realtime); tool surface is `create_exercise` only. Human prerequisites:
  none. Spec accepted.

## Implementation outcome (accepted 2026-06-19)

Built and merged (milestone #18: #202, #203); MCP suite green.
- #202: `exercises.user_id uuid null references auth.users on delete cascade`; distinct per-action RLS (compound public-read SELECT `user_id IS NULL OR user_id = auth.uid()` reusing the `exercises_public_read` name; owner-scoped INSERT/UPDATE/DELETE); partial index `where user_id is not null`; cross-user isolation verified.
- #203: `create_exercise` MCP tool (in `exercises.ts`, not the source-injecting `writeTools.ts`, since `exercises` carries no `source`/`updated_at`); injects `user_id = auth.uid()` from auth (never input), `is_canonical` stays false. Custom-ness surfaced via `user_id` added to BOTH `EXERCISE_COLUMNS` and the `search_exercises` RPC `RETURNS TABLE` (kept in sync; adding the column needed DROP+CREATE of the function). FK RESTRICT refuses deleting a referenced custom. `update_exercise`/`delete_exercise` remain deferred.
