# Spec: Phase 16 — MCP authoring ergonomics & stability

> Created: 2026-06-19

Make agent authoring fast, discoverable, and reliable: a single transactional
`create_plan` that writes a whole plan tree in one call, read-only annotations
and structured descriptions so a client resolves the tools in one step, and a
fix for the `get_plan`-after-writes hang. Closes the MCP beta gaps
(`beta-test-protocol.md` §4.3 round-trip-per-row + ID chaining, §4.4 tool
discovery, §4.5 read timeout). This spec carries no lifecycle state — acceptance
is the spec merged on the default branch with a milestone and issues.

## Outcome

- [ ] An MCP client authors a complete multi-day plan in **one** `create_plan`
      call carrying the nested tree (plan → days → exercises), with no
      hand-chained `planId → dayId → exerciseId` round-trips.
- [ ] `create_plan` is atomic: a validation or write failure anywhere in the tree
      leaves **nothing** partially written.
- [ ] Every read tool advertises `readOnlyHint` and carries a structured
      description (purpose + parameter constraints + output shape); a client's
      tool-search resolves the working set in one step.
- [ ] The `get_plan`-after-a-write-burst hang no longer reproduces (or, if the
      root cause needs hosted-infra changes, it is documented with a fix plan and
      the residual fix filed as a `track:adhoc` issue).
- [ ] `make verify` is green, including a `create_plan` atomicity test.

## Scope

### In scope

- A `create_plan` MCP write tool backed by a `SECURITY INVOKER` plpgsql function
  called via `supabase.rpc()`: it inserts the plan, its days, and each day's
  exercises (the Phase 15 typed prescription + grouping) in one transaction,
  running as the resolved user so RLS applies; the whole nested input is
  Zod-validated at the MCP boundary and every row is written `source='agent'`.
- Tool annotations + descriptions: `readOnlyHint: true` on `ping`, `list_plans`,
  `get_plan`, `search_exercises`, and the set-log reads; structured descriptions
  across the surface.
- Diagnose and fix the `get_plan`-after-writes hang (beta §4.5). Candidate
  hypotheses to start from: a stateless-mode SSE GET stream held open with no
  session/messages; per-request Supabase-client / connection handling under a
  write burst.
- Tests: `create_plan` happy-path + atomicity (mid-tree failure rolls back);
  annotation presence; a regression test for the read-after-writes path if the
  root cause is reproducible in the suite.

### Out of scope

- Custom / user-authored exercises — Phase 17.
- Structured prescription columns themselves — Phase 15 (this phase writes them).
- Catalog search internals — Phase 14.
- A bulk `update_plan` / nested edit — only nested **create** is in scope; the
  existing per-row update/delete tools remain for edits.
- Set-log bulk writes — `create_plan` covers prescription, not logged sets.

## Constraints

- `create_plan` runs as the resolved user via a `SECURITY INVOKER` function — no
  service-role write to user tables, no `user_id` accepted from the caller
  (constitution); RLS enforces ownership inside the transaction.
- The whole nested input is Zod-validated at the MCP boundary, including the
  Phase 15 range invariants (`rep_min ≤ rep_max`, `rir_low ≤ rir_high`)
  (constitution).
- The stability fix must not introduce server-side sessions backed by shared
  mutable state or any service-role data path — the per-request, user-scoped,
  stateless model stays (architecture: boundaries).
- Annotations use the MCP SDK `registerTool` annotation field; descriptions
  follow the prior-art structure (one-line purpose + param constraints + output).

## Prior art

- [Programmable plan authoring — nested / transactional write (the bulk-authoring concern)](../prior-art.md#programmable-plan-authoring--nested--transactional-write-the-bulk-authoring-concern)
  — Hevy's single nested `create-routine`; Rack's `create_plan` is the same shape,
  backed by a transactional RPC.
- [MCP tool-surface ergonomics & discovery](../prior-art.md#mcp-tool-surface-ergonomics--discovery)
  — `readOnlyHint`, structured descriptions, a balanced/well-named surface.

## Human prerequisites

- [ ] `none` — MCP + migration (the RPC function) changes only; the local
      Supabase stack covers development. No secret or account. (The §4.5 root
      cause may turn out to be hosted-only; if so it is documented and filed, not
      blocked here.)

## Prior decisions

| Decision | Rationale | Date |
|---|---|---|
| `create_plan` is backed by a `SECURITY INVOKER` plpgsql function called via `supabase.rpc()` | Gives one-transaction atomicity while still running as the resolved user so RLS applies — no service-role path (constitution); prior-art Hevy nested create | 2026-06-19 |
| `create_plan` writes plan → days → exercises only (no nested set-logs) | A plan has no per-set rows; the prescription lives on `plan_exercises` (Phase 15). Logged sets are a separate, app-written concern | 2026-06-19 |
| The whole nested input is Zod-validated at the boundary (incl. Phase 15 range invariants) before the RPC runs | Constitution: strict boundary validation; fail loudly, not Hevy's silent-drop | 2026-06-19 |
| Read tools gain `readOnlyHint: true` + structured descriptions; existing per-row create/update/delete tools stay | MCP spec + prior-art; the beta needed several tool-searches and missed `ping` | 2026-06-19 |
| Phase 16 depends on Phase 15 at the issue level (the `create_plan` tree writes the typed fields), not at the milestone level — the annotation and stability work is independent and can run in parallel | Maximizes the unblocked frontier; only the `create_plan` issue carries the cross-milestone `Depends on` | 2026-06-19 |
| OPEN — nested surface: ship only `create_plan` (whole tree), or also an incremental nested `create_plan_day` (a day + its exercises) for appending to an existing plan | resolved at the spec-acceptance gate | — |
| OPEN — §4.5 stability: investigate-and-fix within this phase, or investigate-only here and file the fix as a `track:adhoc` issue if the root cause needs hosted-infra changes | resolved at the spec-acceptance gate | — |

## Tracking

- Milestone: created when this spec merges.
- Issues: `create_plan` (RPC + tool + tests); tool annotations + descriptions;
  the §4.5 stability investigation/fix — created from this spec after merge.

Each issue references this spec path in its body.

## Verification

- [ ] `make verify` passes.
- [ ] `create_plan` with a 2-day, multi-exercise nested tree writes the whole
      plan in one call; `get_plan` reads it back intact.
- [ ] A `create_plan` whose tree contains one invalid exercise writes **nothing**
      (atomic rollback) and returns a clear error.
- [ ] `tools/list` shows `readOnlyHint: true` on the read tools and structured
      descriptions across the surface.
- [ ] The `get_plan`-after-write-burst path returns promptly (regression test if
      reproducible), or the root cause + fix plan is documented and any residual
      fix filed as `track:adhoc` (human QA gate — review).

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| The transactional RPC is complex / hard to keep ≤ the size guideline | Keep it a single insert-tree function with input as JSON; cover atomicity with a rollback test |
| The §4.5 root cause is not reproducible locally | Time-box the investigation, start from the listed hypotheses; if it is hosted-only, document it and file a `track:adhoc` fix rather than block the phase |
| `create_plan` duplicates validation already in the per-row tools | Share the Zod field shapes (`CREATE_SHAPES`) between the per-row tools and the nested input |

## Decision log

- 2026-06-19: Spec drafted from the beta-test findings and the prior-art harvest.
