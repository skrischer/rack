# Spec: Phase 13 — Plate calculator & 1RM estimates

> Created: 2026-06-17

Two on-device lifting utilities in the Recomp design language — a barbell plate calculator that splits a target weight into a per-side plate list against a configurable bar weight and plate inventory, and an Epley 1RM estimate from a logged set's load and reps, both client-only and explicitly labeled as estimates.

## Outcome

- [ ] Given a target weight, a bar weight, and an available plate inventory, the app shows the per-side plate breakdown needed to load the bar (e.g. `60 kg → bar 20 + 2×20 per side`), greedily using the largest plates first, with each plate used in pairs.
- [ ] When the target weight cannot be hit exactly with the available inventory, the app shows the closest loadable weight at or below the target and labels the unreachable remainder (a `+x.x kg short` indicator), rather than silently rounding or failing.
- [ ] When the target weight is below the bar weight, the app shows an explicit "below bar weight" state and no plate list.
- [ ] The bar weight is configurable (default 20 kg) and the plate inventory is configurable (default a standard kg set: 25/20/15/10/5/2.5/1.25 kg, each treated as available in pairs); both persist locally on the device across app restarts and are read back unchanged.
- [ ] From a logged set (heaviest weight × its rep count), the app shows a 1RM estimate computed by Epley `1RM = w·(1 + reps/30)`, rounded to one decimal place, with the formula name and the word "estimate" visible next to the number.
- [ ] The 1RM estimate for a single rep equals the lifted weight (Epley reduces to `w` at reps=1), and the estimate is suppressed (not shown, or shown as "—") for a set with 0 reps or 0/blank weight.
- [ ] The plate calculator and the 1RM estimate are reachable from the exercise/logging surface introduced in Phase 3 and render in the Recomp visual language (dark theme, volt `#c8f23a` accent, Archivo + Spline Sans Mono, monospace numerics).
- [ ] Both features work entirely offline with no Supabase write, no MCP call, and no new database table; `make verify` and `make build` are green.

## Scope

### In scope

- A pure-Kotlin plate-math function: `targetWeight, barWeight, inventory → per-side plate list + loadable weight + shortfall`, greedy largest-first, plates consumed in pairs, with deterministic, unit-tested behavior at the boundaries (target < bar, exact hit, unreachable remainder, empty inventory).
- A pure-Kotlin 1RM function implementing Epley with rounding to one decimal, plus the reps=1 and reps=0 / blank-weight edge handling.
- Local persistence of the two plate-calculator preferences (bar weight, plate inventory) on the device.
- A Compose UI to open the plate calculator for a target weight and to surface the 1RM estimate from a logged set, both styled to the Recomp prototype and wired through a ViewModel + StateFlow with the math behind the repository/use-case boundary (no logic in Composables).

### Out of scope

- Server-side storage of plate/bar preferences or 1RM values — these are derived, device-local, and need no new RLS table (why: they are a pure UI utility over data Phase 3 already persists; the constitution's "a user-data table without RLS is a bug" rule means we add neither table nor untyped state).
- kg ↔ lb unit switching and the global settings surface — that is Phase 12 (`docs/specs/spec-settings.md`); this phase works in kg (the prototype default) and reads no Phase-12 unit setting (why: Phase 13 depends only on Phase 3; coupling to Phase 12 would break the dependency contract).
- Any MCP tool, Zod schema, or backend change — no agent reads or writes plate/1RM data (why: nothing here is user-authored data the agent designs; it is a local consumption utility).
- A second 1RM formula, multi-formula averaging, Brzycki, or per-set/historical 1RM trend charts (why: prior art mandates one documented formula labeled an estimate; trends are a Phase 11 dashboards concern).
- A standalone "warmup ramp" or set-by-set loading planner (why: scope is one target weight at a time).

## Constraints

Bound by `../constitution.md`: Android has no business logic in Composables, UI state via ViewModel + StateFlow, all data access behind the repository layer (here the persisted preferences); functions ≤ 50 lines and files ≤ 400 lines; Kotlin via ktlint/detekt, idiomatic Compose; code/comments/identifiers/logs in English; Conventional Commits. The Android client uses only the anon key + user JWT and ships no secret — trivially satisfied here as the phase makes no network call. No new user-data table is introduced, so the "RLS in the same migration" rule is honored by adding no migration at all. The "every mutation sets `source` + `updated_at`" rule does not apply because this phase performs no Supabase mutation.

## Prior art

- [1RM estimation (Phase 13)](../prior-art.md#1rm-estimation-phase-13) — directly normative: implement Epley/Brzycki by hand, no dependency, compute from the heaviest logged set, present as an estimate, and explicitly AVOID multi-formula averaging/ML. This phase adopts Epley (`1RM = w·(1 + reps/30)`).
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux) — relevant for the Recomp logging surface these utilities attach to (kg + per-set reps + RIR + history disclosure); the plate calculator and 1RM badge must match that fast, local-first interaction.
- [Open exercise data & training domain model](../prior-art.md#open-exercise-data--training-domain-model) — relevant only for the standard kg plate set and bar-weight defaults as established gym conventions; no code reuse, data only.

## Human prerequisites

- none — this phase adds no table, no secret, no external provider, and no network call; it builds on the Phase 3 Android app and Supabase project already provisioned by earlier milestones. All decisions are resolved in this spec.

## Prior decisions

| Decision | Rationale | Date |
| -------- | --------- | ---- |
| Use Epley `1RM = w·(1 + reps/30)` as the single 1RM formula. | Prior art lists Epley first and mandates one documented formula labeled an estimate; Epley reduces cleanly to `w` at reps=1, matching lifter intuition. | 2026-06-17 |
| Compute the 1RM from the heaviest logged set of the exercise (max weight; ties broken by higher reps). | Prior art: "compute an estimate from the heaviest logged set." A single, well-defined source avoids ambiguity over which set drives the number. | 2026-06-17 |
| Round the 1RM estimate to one decimal place and render it in monospace next to the literal label "Epley · estimate". | Matches the prototype's `Spline Sans Mono` numerics and the constitution-adjacent honesty requirement to present it as guidance, not truth. | 2026-06-17 |
| Suppress the 1RM (show "—") when reps = 0 or weight is 0/blank; show `w` exactly when reps = 1. | Epley is undefined/meaningless without a positive load and rep; reps=1 must not inflate the lifted weight. | 2026-06-17 |
| Plate calculator works in kg only this phase; default bar 20 kg, default inventory 25/20/15/10/5/2.5/1.25 kg in pairs. | kg is the prototype default and the only unit Phase 3 logs; lb is Phase 12's concern and Phase 13 must not depend on Phase 12. | 2026-06-17 |
| Greedy largest-first split, plates always consumed in pairs (one per side), inventory limits each plate's pair count. | Standard barbell-loading mental model; greedy is optimal for the canonical denomination set and is the universally expected gym behavior. | 2026-06-17 |
| On an unreachable target, return the closest loadable weight ≤ target plus a labeled shortfall, never round up past inventory. | Loading past the target is physically impossible; rounding down with a visible `+x.x kg short` is honest and matches how lifters reason about available plates. | 2026-06-17 |
| Persist bar weight and plate inventory in device-local storage (Jetpack DataStore), not Supabase. | Derived UI preferences, single-device, no agent involvement; a server table would force a new RLS policy for no benefit and violate the minimal-surface principle. | 2026-06-17 |
| No new MCP tool, Zod schema, or migration; no Supabase read/write beyond the set logs Phase 3 already exposes. | Plate/1RM are pure client computations over existing data; adding backend surface would be over-engineering the constitution warns against. | 2026-06-17 |
| Attach both utilities to the existing Phase 3 exercise/logging surface (plate calc opened from a target/working weight, 1RM shown near logged-set history) rather than a new top-level screen. | Keeps the consumption-and-logging focus the vision sets; avoids a standalone tool surface prior art warns against. | 2026-06-17 |
| Plate split renders as a per-side list (e.g. `bar 20 + 20 + 10` per side) with a one-line total. | Per-side is how a lifter loads the bar; the bar weight is shown once and plates are the per-side stack. | 2026-06-17 |

## Tracking

Full-spec track. Milestone: **Phase 13: Plate calculator & 1RM estimates** (`Depends on milestone: #3`). Issues are dependency-ordered below; each carries `Goal:`, `Acceptance:`, optional `Depends on:`, and `Spec: docs/specs/spec-plate-calc-1rm.md`. No step list lives here — the issues own the steps.

## Verification

Run `make verify` (ktlint/detekt + MCP lint/typecheck/test) and `make build` (`./gradlew assembleDebug`); both must be green. Because Test is `none yet` for behavioral coverage, the pure-math functions ship with JVM unit tests (the natural home for deterministic logic) and the UI behaviors are confirmed at the human milestone-QA smoke test:

- Pure plate-math unit tests cover: target = bar (empty plate list, 0 shortfall); exact hit with several plate sizes; unreachable target (closest ≤ target + correct shortfall); target < bar ("below bar weight"); empty inventory (only bar loadable); inventory-limited plate that runs out mid-greedy.
- Pure 1RM unit tests cover: reps=1 returns `w`; a known case (e.g. 100 kg × 5 → 116.7 kg by Epley); reps=0 and blank/0 weight return the suppressed value; rounding to one decimal.
- QA smoke (Android): open the plate calculator for a working weight, confirm the per-side breakdown and total match the math; change the bar weight and the inventory, confirm the breakdown updates and the new settings survive an app restart; view a logged set and confirm the "Epley · estimate" 1RM number appears, matches the formula, and reads "—" for a 0-rep entry.
- Visual check: the calculator and 1RM badge use the dark theme, volt accent, Archivo headings, and Spline Sans Mono numerics from `../../artifact.html`.

## Risks and mitigations

| Risk | Mitigation |
| ---- | ---------- |
| Greedy split is not optimal for a non-standard (e.g. only 7.5 kg) inventory and overstates the shortfall. | Default inventory is the canonical kg set where greedy is provably optimal; the function returns the achieved-weight + explicit shortfall so any sub-optimality is visible, not hidden, and is unit-tested. |
| Users read the 1RM as a true max and load it. | The number is always rendered next to "Epley · estimate"; the spec's outcome requires the label to be visible, and QA checks it. |
| Phase 12 later adds lb units and the plate calculator's kg assumption breaks. | Phase 13 isolates units behind the math input; Phase 12 owns the unit toggle and will pass the active unit/inventory in. This phase documents the kg-only scope so the seam is explicit. |
| Floating-point weights (e.g. 2.5 kg plates) cause rounding drift in the split. | Compute in a fixed minor unit (e.g. integer grams or BigDecimal) inside the math function; render rounded; unit-test the 2.5/1.25 kg cases. |
| Persisted preferences read back stale or empty after a process restart. | DataStore is the standard, lifecycle-safe Android store; a round-trip persistence test (set → restart → read) is in the QA smoke and the repository is the single access point. |

## Decision log

- Epley chosen over Brzycki as the single documented 1RM formula; `1RM = w·(1 + reps/30)` (auto-resolved under autonomous planning mandate, 2026-06-17).
- 1RM source = heaviest logged set (max weight, ties by reps); rounded to 1 decimal; labeled "Epley · estimate"; reps=1 ⇒ `w`, reps=0 / blank weight ⇒ suppressed (auto-resolved under autonomous planning mandate, 2026-06-17).
- Plate calculator is kg-only this phase; bar default 20 kg; inventory default 25/20/15/10/5/2.5/1.25 kg in pairs; greedy largest-first, plates in pairs; unreachable ⇒ closest ≤ target + labeled shortfall; target < bar ⇒ "below bar weight" (auto-resolved under autonomous planning mandate, 2026-06-17).
- Bar weight and plate inventory persist device-local via Jetpack DataStore behind the repository layer; no Supabase table, no migration, no MCP tool, no Zod schema (auto-resolved under autonomous planning mandate, 2026-06-17).
- Both utilities attach to the existing Phase 3 exercise/logging surface (plate calc from a working weight, 1RM near logged-set history) rather than a new top-level screen; per-side plate list with a single total line (auto-resolved under autonomous planning mandate, 2026-06-17).