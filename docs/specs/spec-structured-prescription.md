# Spec: Phase 15 — Structured plan prescription & grouping

> Created: 2026-06-19

Replace the free-text plan prescription with typed, evaluable fields and make
exercise grouping explicit — so an agent can author (and the app can render) a
set/rep scheme, an RIR range, a rest, and a superset/circuit as data, not as a
string. Closes the MCP beta gaps (`beta-test-protocol.md` §4.2): `target` was
unstructured text, `rir` a single value, and supersets were carried only by a
shared label. This spec carries no lifecycle state — acceptance is the spec
merged on the default branch with a milestone and issues.

## Outcome

- [ ] `plan_exercises` carries typed prescription: `sets`, `rep_min`, `rep_max`,
      `rir_low`, `rir_high`, `rest_seconds` — and a written plan exposes these as
      fields, not parsed from a string.
- [ ] Exercise grouping is explicit: `superset_id` binds members and a
      `group_type` distinguishes superset from circuit; the old implicit
      `superset_label` no longer carries grouping.
- [ ] The MCP `create_plan_exercises` / `update_plan_exercises` tools accept the
      typed fields, Zod-validated (`rep_min ≤ rep_max`, `rir_low ≤ rir_high`,
      non-negative `rest_seconds`).
- [ ] The Android app reads and renders the typed fields (rep range, RIR range,
      rest, group type) without regressing the existing plan/session views.
- [ ] Existing plans remain readable after the migration (no data loss).
- [ ] `make verify` and `make build` are green.

## Scope

### In scope

- A migration on `plan_exercises`: add (all nullable) `sets smallint`, `rep_min
  smallint`, `rep_max smallint`, `rir_low smallint`, `rir_high smallint`,
  `rest_seconds int`, `superset_id smallint`, and `group_type` (a named Postgres
  enum, `plan_group_type` = `superset` | `circuit`, mirroring the existing
  `edit_source` enum precedent); add DB CHECK constraints (`rep_min <= rep_max`,
  `rir_low <= rir_high`); **drop** the legacy `target` / `rir` / `superset_label`
  columns and clear the disposable beta `plan_exercises` rows; re-assert the
  owner RLS policy in the same migration.
- MCP write tools (`create_plan_exercises`, `update_plan_exercises`) and the read
  projections (`writeTools.ts` `RETURNING`, `plans.ts` `EXERCISE_COLUMNS`):
  rewritten to the typed fields only (legacy fields removed), with Zod range
  validation. Ships together with the migration so each PR stays build-green.
- Android: `PlanExercise`, the repository row mapping, and the plan/session
  rendering rewritten to the typed fields only — minimal correctness rendering;
  deep visual polish is the Recomp UX Overhaul living-spec's job.
- Tests: MCP integration over the typed write/read and the DB range constraints;
  an Android mapping test for the typed fields.

### Out of scope

- Deep set-table / plan visual redesign for the new fields — Recomp UX Overhaul
  living-spec (this phase renders them correctly, it does not re-style).
- `set_logs.rir` stays a single value — a logged set records the actual RIR, not
  a target range; only the *prescription* gains a range.
- Catalog search and aliases — Phase 14 (merged).
- Custom / user-authored exercises (`exercise_id` nullable, `exercises.user_id`,
  mixed RLS, `create_exercise`) — deferred to roadmap Phase 17.
- Nested transactional `create_plan`, tool annotations, `get_plan` stability —
  Phase 16.

## Constraints

- Structured fields are the source of truth; any retained free-text
  (`target`) is display-only (constitution: "free-text prescription is never
  the source of truth").
- The migration is additive and re-asserts the `plan_exercises` owner RLS policy
  in the same migration (constitution: every user-data table ships its RLS with
  its migration; precedent: the enrich migration).
- MCP tool inputs stay Zod-validated at the boundary; no `user_id` from the
  caller; writes set `source='agent'` (constitution).
- Grouping is modelled with columns on `plan_exercises` (`superset_id` +
  `group_type`), not a separate group table — the single-owner model does not
  need the extra relation (constitution: minimal; prior-art: Hevy carries
  `superset_id` on the exercise).
- Android: no business logic in Composables; the typed fields flow through the
  repository and ViewModel (constitution).

## Prior art

- [Structured set prescription — rep range, effort (RIR/RPE), rest](../prior-art.md#structured-set-prescription--rep-range-effort-rirrpe-rest)
  — `rep_range {start,end}`, per-exercise `rest_seconds`, effort as typed fields;
  RIR modelled as a `rir_low`/`rir_high` range (Hevy uses single RPE, which loses
  the range Rack needs).
- [Superset / circuit grouping — explicit](../prior-art.md#superset--circuit-grouping--explicit)
  — Hevy's explicit numeric `superset_id`, extended with a Rack group type and
  rest.

## Human prerequisites

- [ ] `none` — schema + app + MCP changes only; the local Supabase stack from
      `docs/workflow.md` covers development. No secret or account.

## Prior decisions

| Decision | Rationale | Date |
|---|---|---|
| Typed columns: `sets`, `rep_min`, `rep_max`, `rir_low`, `rir_high`, `rest_seconds` on `plan_exercises`, all nullable | Prior-art (Hevy `rep_range` + `rest_seconds`); beta §4.2 needed rep- and RIR-*ranges* as evaluable fields; nullable so the migration is non-destructive | 2026-06-19 |
| Grouping = `superset_id smallint` (a per-day group key, not a global row id — day N and day M may each have group `1`) + `group_type` enum on `plan_exercises`, replacing `superset_label` for grouping | Hevy precedent (superset_id on the exercise) + minimal (no extra table) for a single-owner model; group_type adds the superset/circuit distinction the beta wanted | 2026-06-19 |
| `group_type` is a named Postgres enum `plan_group_type` (`superset`/`circuit`) | Mirrors the existing `edit_source` enum precedent over an ad-hoc CHECK on a text column | 2026-06-19 |
| `rest_seconds` is per-exercise and means "rest after this exercise's set before the next exercise/set". In a superset/circuit the members carry the short intra-group transition rest (often 0) and the **last** member carries the group rest | Matches Hevy's per-exercise rest while still expressing one group rest; avoids a separate group-rest column | 2026-06-19 |
| Range invariants are enforced in the DB (CHECK `rep_min <= rep_max`, `rir_low <= rir_high`) as well as by Zod on full create inputs | Constitution: enforce shared rules in the DB so both adapters inherit them; a partial `update_plan_exercises` patch (only one side of a pair) cannot persist an inverted state because the CHECK holds regardless | 2026-06-19 |
| `set_logs.rir` stays a single value | A logged set is the actual effort, not a target range; only the prescription gains a range | 2026-06-19 |
| Clean break: the typed fields are the only prescription; the legacy `target` / `rir` / `superset_label` columns are dropped and the disposable beta rows cleared. No display-only legacy, no backward-compat projection | Spec-acceptance gate — the beta data is throwaway, so there is no data to preserve; this removes the transition machinery entirely | 2026-06-19 |
| The schema migration and the MCP rewrite ship in one PR (build-green); the Android rewrite follows it | With no data to preserve there is no transition window, but each PR must still pass Verify/Build — so the column drop and the code that reads those columns move together | 2026-06-19 |
| App renders the typed fields minimally this phase; visual restyle is the Recomp UX Overhaul living-spec | Keeps Phase 15 to data-model + correctness; UX polish has its own track | 2026-06-19 |
| Custom user exercises (beta §4.2 mandatory-catalog gap) are deferred to their own phase (new roadmap Phase 17); `exercise_id` stays NOT NULL in Phase 15 | Spec-acceptance gate — distinct concern (catalog ownership + mixed RLS on the public table); the Phase 14 search (canonical + aliases + filters) already reduces the substitution pain | 2026-06-19 |

## Tracking

- Milestone: created when this spec merges.
- Issues: one per implementable step (migration; MCP write tools; Android
  domain + rendering; tests), created from this spec after merge.

Each issue references this spec path in its body.

## Verification

- [ ] `make verify` and `make build` pass.
- [ ] `create_plan_exercises` with `sets=3, rep_min=6, rep_max=8, rir_low=1,
      rir_high=2, rest_seconds=120` round-trips those fields on read-back.
- [ ] Two exercises sharing a `superset_id` with `group_type=superset` read back
      as an explicit group; a three-member `circuit` reads back as a circuit.
- [ ] Both Zod (on full create input) and the DB CHECK constraint reject
      `rep_min > rep_max` and `rir_low > rir_high`; a partial update that would
      invert against the stored value is refused by the constraint.
- [ ] After the migration the legacy `target` / `rir` / `superset_label` columns
      are gone and no app/MCP code references them (clean break).
- [ ] The app renders a rep range, an RIR range, a rest, and a superset/circuit
      badge for a typed plan exercise (human QA gate — UI check).

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| A PR that drops the legacy columns while other code still reads them fails Verify/Build | Ship the migration + MCP rewrite in one PR; the Android rewrite follows; each PR is self-consistent and build-green |
| Ambiguous rest for a multi-member superset | `rest_seconds` semantics fixed: per-exercise "rest after this exercise"; the last group member carries the group rest, intra-group members carry ~0 |
| Beta data loss from dropping columns | Intentional — the beta `plan_exercises` data is disposable; the catalog seed is untouched and the managed project is not yet linked |
| App ripple is broad (domain model, repository, plan/session views) | One Android issue scoped to mapping + minimal rendering; visual polish deferred to the living-spec |

## Decision log

- 2026-06-19: Spec drafted from the beta-test findings and the prior-art harvest.
- 2026-06-19: Review (REQUEST_CHANGES) addressed — fixed the legacy-column fate
  (incl. `rir`), the no-broken-intermediate transition contract, per-exercise
  rest semantics (last member carries group rest), `group_type` as a named enum,
  DB CHECK range constraints, per-day `superset_id` scoping, and the concrete
  custom-exercise branch consequences.
- 2026-06-19: Spec-acceptance gate — clean break chosen (drop the legacy
  `target`/`rir`/`superset_label` columns, beta data disposable), simplifying
  away the transition machinery; custom exercises deferred to a new roadmap
  Phase 17. Human prerequisites: none. Spec accepted.
