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

- An additive migration on `plan_exercises`: add `sets smallint`, `rep_min
  smallint`, `rep_max smallint`, `rir_low smallint`, `rir_high smallint`,
  `rest_seconds int`, `superset_id smallint`, `group_type` (a checked
  `superset`/`circuit` value); re-assert the owner RLS policy.
- MCP write tools (`create_plan_exercises`, `update_plan_exercises`): accept the
  typed fields with Zod range validation; `toColumns`/`RETURNING` updated.
- Android: extend `PlanExercise` (and the repository row mapping) with the typed
  fields and render them in the plan and session views — minimal, correctness
  rendering; deep visual polish is the Recomp UX Overhaul living-spec's job.
- Tests: MCP integration over the typed write/read; an Android mapping test.

### Out of scope

- Deep set-table / plan visual redesign for the new fields — Recomp UX Overhaul
  living-spec (this phase renders them correctly, it does not re-style).
- `set_logs.rir` stays a single value — a logged set records the actual RIR, not
  a target range; only the *prescription* gains a range.
- Catalog search and aliases — Phase 14 (merged).
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
| Typed columns: `sets`, `rep_min`, `rep_max`, `rir_low`, `rir_high`, `rest_seconds` on `plan_exercises` | Prior-art (Hevy `rep_range` + `rest_seconds`); beta §4.2 needed rep- and RIR-*ranges* as evaluable fields | 2026-06-19 |
| Grouping = `superset_id smallint` + `group_type` (`superset`/`circuit`) columns on `plan_exercises`, replacing `superset_label` for grouping; `rest_seconds` is per-exercise | Hevy precedent (superset_id on the exercise) + minimal (no extra table) for a single-owner model; group_type adds the superset/circuit distinction the beta wanted | 2026-06-19 |
| `set_logs.rir` stays a single value | A logged set is the actual effort, not a target range; only the prescription gains a range | 2026-06-19 |
| Structured fields are authoritative; any retained `target` is display-only | Constitution: free-text is never the source of truth | 2026-06-19 |
| Zod validates `rep_min ≤ rep_max`, `rir_low ≤ rir_high`, `rest_seconds ≥ 0` | Constitution: strict boundary validation; prevents inverted ranges | 2026-06-19 |
| App renders the typed fields minimally this phase; visual restyle is the Recomp UX Overhaul living-spec | Keeps Phase 15 to data-model + correctness; UX polish has its own track | 2026-06-19 |
| OPEN — legacy-field strategy: keep `target` + `superset_label` as display-only legacy and have agents re-author into the typed fields, vs. best-effort parse-backfill of `target`→`sets/rep_*`, vs. drop the legacy columns after backfill | resolved at the spec-acceptance gate | — |
| OPEN — custom user exercises (`exercises.user_id` + mixed public/owner RLS + a `create_exercise` MCP tool, so a plan can reference an exercise absent from the catalog — beta §4.2): include in Phase 15 or split to its own phase | resolved at the spec-acceptance gate | — |

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
- [ ] Zod rejects `rep_min > rep_max` and `rir_low > rir_high` with a clear
      error.
- [ ] An existing pre-migration plan still loads in the app (no data loss).
- [ ] The app renders a rep range, an RIR range, a rest, and a superset/circuit
      badge for a typed plan exercise (human QA gate — UI check).

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Migrating off `superset_label` breaks the app's current grouping rendering | Land the migration, MCP, and app changes together in dependency order; keep `superset_label` until the app reads `superset_id` |
| Parsing free-text `target` for a backfill is unreliable | Resolve at the gate; the default is keep-legacy + agent re-author, not a risky parse |
| Custom exercises change the catalog's pure-public RLS to mixed | If included, the migration adds an owner policy for `user_id` rows beside the public-read policy for `user_id IS NULL`; covered by an isolation test |
| App ripple is broad (domain model, repository, plan/session views) | One Android issue scoped to mapping + minimal rendering; visual polish deferred to the living-spec |

## Decision log

- 2026-06-19: Spec drafted from the beta-test findings and the prior-art harvest.
