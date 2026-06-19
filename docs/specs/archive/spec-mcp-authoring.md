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

- A `create_plan` MCP write tool backed by a plpgsql function (one `jsonb`
  argument) called via the user-scoped `auth.supabase.rpc('create_plan', …)`: it
  inserts the plan, its days, and each day's exercises (the Phase 15 typed
  prescription + grouping) in one transaction and writes every row
  `source='agent'`. RLS applies because the user-scoped client sends the user's
  JWT to PostgREST, which sets `auth.uid()` for the function body — the function
  is declared `SECURITY INVOKER` (runs as the `authenticated` role, does **not**
  escalate); it must **not** be `SECURITY DEFINER`/`SET ROLE`. The whole nested
  input is Zod-validated at the MCP boundary before the RPC runs.
- A `create_plan_day` MCP write tool (same transactional RPC pattern): appends
  one day plus its exercises to an existing plan (`planId`) without rewriting the
  whole tree. Shares the day/exercise input shape and Zod validation with
  `create_plan`.
- Tool annotations + descriptions: `readOnlyHint: true` on `ping`, `list_plans`,
  `get_plan`, `search_exercises`, and the set-log reads; structured descriptions
  across the surface.
- Diagnose and fix the `get_plan`-after-writes hang (beta §4.5). Lead hypothesis:
  per-request Supabase-client / connection handling under a write burst (the hang
  appears *after* writes, not during). Note the transport is fully stateless
  (`http.ts` `sessionIdGenerator: undefined`), so there is **no** held-open SSE
  GET stream to blame — do not chase that first.
- Tests: `create_plan` happy-path + atomicity (mid-tree failure rolls back);
  annotation presence; a regression test for the read-after-writes path if the
  root cause is reproducible in the suite.

### Out of scope

- Custom / user-authored exercises — Phase 17.
- Structured prescription columns themselves — Phase 15 (this phase writes them).
- Catalog search internals — Phase 14.
- A bulk `update_plan` / nested edit — only nested **create** (`create_plan`,
  `create_plan_day`) is in scope; the existing per-row update/delete tools remain
  for edits.
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

## `create_plan` input contract

Validated by Zod at the boundary, passed to the RPC as one `jsonb` argument
(camelCase at the MCP boundary, mapped to `snake_case` columns inside the
function). Shape:

```
{
  name: string, kind?: string,
  days: [{
    position: int, title?: string, focus?: string, tag?: string,
    exercises: [{
      exerciseId: uuid, position: int,
      sets?, repMin?, repMax?, rirLow?, rirHigh?, restSeconds?, cue?,
      supersetId?: int,            // per-day-local group key (Phase 15 semantics); null = ungrouped
      groupType?: 'superset'|'circuit'
    }]
  }]
}
```

- Grouping is signalled by the caller pre-assigning a per-day-local `supersetId`
  (a small integer, null = ungrouped) plus `groupType` on each grouped exercise —
  the same per-day numeric key Phase 15 defines, not a global id.
- The exercise fields reuse the Phase-15-rewritten `CREATE_SHAPES.plan_exercises`
  Zod shape (typed fields), so this tool **must** follow the Phase 15
  migration+MCP work (issue-level `Depends on`); it must not reintroduce the
  dropped legacy `target`/`rir`/`superset_label` fields.
- Returns the created plan's id plus created day/exercise counts; the client
  reads the full tree back via `get_plan`.
- On any failure (Zod, FK on `exerciseId`, a DB CHECK such as `rep_min > rep_max`,
  or RLS) the transaction rolls back entirely and the tool surfaces a specific,
  loud error string (constitution: fail loudly, not Hevy's silent drop).

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
| `create_plan` is a plpgsql function (one `jsonb` arg) called via the user-scoped `auth.supabase.rpc()`; RLS applies because PostgREST sets `auth.uid()` from the user JWT on that call, not because of the function's security attribute. The function is `SECURITY INVOKER` (runs as `authenticated`, no escalation) — explicitly **not** `SECURITY DEFINER`/`SET ROLE` | One-transaction atomicity with no service-role path (constitution); prior-art Hevy nested create. Correct causal model so no one passes a token into the body or escalates the function | 2026-06-19 |
| `create_plan` input shape is the contract in "`create_plan` input contract"; grouping via a caller-assigned per-day-local `supersetId` + `groupType` | Removes the day-one ambiguity over the nested shape and how supersets are signalled | 2026-06-19 |
| Any failure rolls the whole transaction back; the tool returns a specific, loud error string | Constitution: fail loudly; an agent needs an actionable message, not a silent partial write | 2026-06-19 |
| `create_plan` reuses the Phase-15-rewritten `CREATE_SHAPES.plan_exercises` Zod shape; `writeTools.ts` arrives already typed-only from Phase 15 and must not regress the legacy columns | Shared validation, no drift; makes the cross-issue ordering load-bearing and explicit | 2026-06-19 |
| `create_plan` writes plan → days → exercises only (no nested set-logs) | A plan has no per-set rows; the prescription lives on `plan_exercises` (Phase 15). Logged sets are a separate, app-written concern | 2026-06-19 |
| Read tools gain `readOnlyHint: true` + structured descriptions; existing per-row create/update/delete tools stay | MCP spec + prior-art; the beta needed several tool-searches and missed `ping` | 2026-06-19 |
| Phase 16 depends on Phase 15 at the issue level (the `create_plan` tree writes the typed fields), not at the milestone level — the annotation and stability work is independent and can run in parallel | Maximizes the unblocked frontier; only the `create_plan` issue carries the cross-milestone `Depends on` | 2026-06-19 |
| Ship both `create_plan` (whole tree) and `create_plan_day` (append a day + its exercises to an existing plan), sharing the day/exercise input shape and Zod validation | Spec-acceptance gate — incremental append is wanted without a full-tree rewrite | 2026-06-19 |
| §4.5 is investigate-**and-fix** within this phase; only a root cause that proves hosted-infra-only falls back to a `track:adhoc` follow-up | Spec-acceptance gate — the lead hypothesis (connection handling) is fixable in the MCP | 2026-06-19 |

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
- 2026-06-19: Review (REQUEST_CHANGES) addressed — corrected the RLS causal model
  (user JWT on the PostgREST call, not `SECURITY INVOKER`), added the explicit
  `create_plan` input contract + per-day `supersetId` grouping + rollback/error
  contract, made the Phase-15 `CREATE_SHAPES` reuse/ordering explicit, refocused
  the §4.5 hypotheses on connection handling (the stateless transport has no
  held-open SSE stream), and gave OPEN-1 a decision criterion.
- 2026-06-19: Spec-acceptance gate — ship both `create_plan` and
  `create_plan_day`; §4.5 is investigate-and-fix in this phase. Human
  prerequisites: none. Spec accepted.

## Implementation outcome (accepted 2026-06-19)

Built and merged (milestone #17: #197, #198, #199); MCP suite green.
- #197: transactional `create_plan` + `create_plan_day` as SECURITY INVOKER plpgsql functions called via the user-scoped `auth.supabase.rpc()` (no service-role path; RLS via the user JWT); whole-tree atomic rollback on any Zod/FK/CHECK/RLS failure; reuses the Phase-15 typed shapes via exported `planExerciseFields`/`planDayFields`; a shared `insert_plan_day` helper carries an RLS-scoped ownership guard.
- #198: `readOnlyHint: true` on the read tools (incl. `list_artifacts`) + structured descriptions across the surface.
- #199: the `get_plan`-after-write-burst hang root cause = unbounded per-request `fetch` (no timeout) causing dead keep-alive socket reuse to stall the post-burst read; trigger is hosted-only / not loopback-reproducible. Fixed in the MCP via a per-request `AbortSignal.timeout` in `supabase.ts` (stateless; no sessions/shared-state/service-role). Residual hosted-only end-to-end verification + optional keep-alive tuning filed as `track:adhoc` #208.
