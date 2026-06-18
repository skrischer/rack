# Spec: Recomp UX Overhaul (living-spec)

> Created: 2026-06-18

Bring every Android screen up to the refreshed Recomp design language captured
in `docs/design/` — a launcher-style home, set-table session logging, a pull-up
bottom-nav dock, and an end-to-end polish pass — implemented in Jetpack Compose
off the normative theme. This is a **living-spec** (workflow.md track): an open
milestone marked `Track: living-spec` that accretes per-screen / per-pattern
issues over time and is never archived; acceptance is this spec merged on the
default branch with that open milestone.

## Outcome

What is true while this theme is active (each screen flips its own box as it
lands; the spec itself is not "done"):

- [x] The Compose theme exposes the full Recomp component vocabulary used by the
      designs (set table, nav dock/off-canvas, stat strip, launcher day rows,
      segmented toggle, stepper, chips, badges, empty/loading/error) — all from
      `docs/design-tokens.md` tokens, no ad-hoc colors.
- [x] App navigation is the pull-up **bottom-nav dock**: quick-nav tiles (Plan,
      Verlauf, Statistik, Artifacts) + a draggable off-canvas overflow (Übungen,
      API-Keys, Einstellungen, Account/Abmelden). Detail/flow screens hide it.
- [x] Plan/Home is a **launcher**: today-hero + "Session starten" CTA + compact
      plan-day list; no inline per-set inputs on the overview.
- [x] The session player logs via a **set table** (`Satz | Vorher | kg | Wdh |
      RIR | ✓`) with a previous-performance column and a per-set check that
      auto-starts the rest timer.
- [x] Every remaining screen matches its `docs/design/screens/<screen>.html`
      reference, including loading/empty/error states: session summary
      (`session-summary`), Übersicht/Statistik (`home`), Verlauf (`calendar`,
      `history-expanded`), exercise detail/progress (`exercise-detail`,
      `exercise-progress`), settings (`settings`), API-keys (`keys`,
      `key-reveal`), artifacts + viewer (`artifacts`, `artifact-viewer`), plate
      calculator (`plate-calc`), login (`login`), and the shared loading/empty/
      error states (`states`). The off-canvas overflow (`menu`) is covered by
      the nav-dock outcome above.
- [x] When a screen is implemented its design scaffolding is retired: once all
      screen issues close, `docs/design/` is removed and the architecture /
      CLAUDE.md / roadmap references are cleaned up (see Decision log / the
      terminal cleanup issue).

## Scope

### In scope

- Re-implementing the **visual + interaction layer** of existing Android screens
  to match `docs/design/screens/*.html` and the new patterns (launcher,
  set-table, nav dock/off-canvas, polished states).
- Extending the Compose Recomp theme with the reusable components the designs
  introduce.
- Retiring the `docs/design/` scaffolding after the screens are implemented.

### Out of scope

- New product features, new data model, new backend/MCP tools, or schema
  changes — this is a UX re-skin/re-flow of existing functionality only. A
  screen needing new data is split out to its own feature spec, not this theme.
- The deferred hosted/`blocked:human` work (managed Supabase #7, VPS/Caddy #16,
  FCM push #63/#64/#65).
- The normative tokens themselves: `docs/design-tokens.md` stays the source of
  truth and is not rewritten here (only consumed/encoded).

## Constraints

- Compose/MVVM per `docs/constitution.md`: no business logic in Composables, UI
  state via ViewModel + StateFlow, Supabase access behind the repository layer —
  the overhaul touches the UI layer, not these boundaries.
- Encode tokens once in the Compose theme (`RecompColors`/`RecompType`/shape);
  build screens from the theme, never ad-hoc hex (`docs/architecture.md`).
- `make verify` (ktlint/detekt + MCP checks) is the per-iteration gate;
  `make build` (`assembleDebug`) for app-affecting changes (`docs/workflow.md`).
- Functions ≤ 50 lines, files ≤ 400 lines; English; no emojis in UI.
- The designs are reference only; `docs/design-tokens.md` remains normative where
  the two could diverge.

## Prior art

- [App navigation & logging-screen UX](../prior-art.md#app-navigation--logging-screen-ux-recomp-ux-overhaul)
  — Strong/Hevy conventions: bottom-tab quick-nav + overflow, the set table with
  a previous column + per-set check, the launcher home that separates
  plan-overview from active-session logging.
- [Native Android workout-logging UX](../prior-art.md#native-android-workout-logging-ux)
  — fast set/rep logging, local-first responsiveness (GymRoutines/Flexify).
- [Guided workout session — active player UX (Phase 9)](../prior-art.md#guided-workout-session--active-player-ux-phase-9)
  — step-through flow the set-table player refines.
- [Charts & dashboards — Jetpack Compose (Phase 11)](../prior-art.md#charts--dashboards--jetpack-compose-phase-11)
  — Vico, for the dashboard/progress refresh.

## Human prerequisites

- none — the design reference is committed in `docs/design/`; everything is
  implemented and verified locally. The Claude Design project is the author's
  external mirror and is not required to build any screen.

## Prior decisions

| Decision | Rationale | Date |
|---|---|---|
| Design source = `docs/design/` (17 screens + `kit.css`) + normative `docs/design-tokens.md` | Committed in-repo so worktree subagents can read it; tokens stay normative | 2026-06-18 |
| Launcher home, set-table logging, bottom-nav dock + off-canvas | de-facto Strong/Hevy conventions, see Prior art | 2026-06-18 |
| Bottom-nav = 4 tiles Plan/Verlauf/Statistik/Artifacts; overflow = Übungen/API-Keys/Einstellungen/Account | matches the committed designs; daily loop in the bar, config in the overflow | 2026-06-18 |
| Set table keeps the RIR column (`Satz\|Vorher\|kg\|Wdh\|RIR\|✓`) | RIR is Rack's signature logging dimension (vision/constitution) | 2026-06-18 |
| "Vorher" = the matching set index from the last logged session of that exercise | progressive-overload reference, Strong/Hevy precedent | 2026-06-18 |
| Visual/interaction layer only — no data-model/backend change | keeps the theme bounded; data-needing screens split to feature specs | 2026-06-18 |
| Scaffolding is temporary: retire `docs/design/` once screens are implemented | author's intent; the in-repo bundle is a build reference, not shipped | 2026-06-18 |
| Cleanup on completion: remove `docs/design/` **entirely** (screens + `kit.css` + README) and clean its architecture/CLAUDE.md/roadmap references; **keep** the external Claude Design project as an archive | acceptance gate; `docs/design-tokens.md` stays the normative source, the bundle was build scaffolding | 2026-06-18 |

## Tracking

The decomposition into steps lives as GitHub issues under an **open**
`Track: living-spec` milestone — one issue per screen/pattern, plus a terminal
cleanup issue depending on all of them. New screens accrete into the same
milestone (re-run `/loopkit:plan recomp-ux-overhaul`). This spec owns the design;
the issues own progress.

- Milestone: created on acceptance (stays open)
- Issues: one per implementable screen/pattern + a terminal scaffolding-cleanup

## Verification

Per screen/pattern issue (the living-spec QA gate runs per closed-issue batch,
no archive):

- [ ] `make verify` passes; `make build` (`assembleDebug`) green for the change.
- [ ] The implemented screen matches its `docs/design/screens/<screen>.html`
      reference (layout, tokens, states) — visual smoke check at the QA gate.
- [ ] Bottom-nav dock: tiles route correctly, handle pulls the off-canvas open,
      overflow links reachable; detail/flow screens hide the dock.
- [ ] Session set-table: previous column shows last session's matching set;
      checking a set persists it and auto-starts the rest timer.
- [ ] Plan launcher: "Session starten" opens the session player for today's day.
- [ ] No ad-hoc colors — colors resolve through the Compose Recomp theme.
- [ ] Terminal: after all screen issues close, `docs/design/` is removed and the
      architecture/CLAUDE.md/roadmap references are updated; `make verify` green.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Scope creep into new features while "refreshing" a screen | Out-of-scope rule: data/feature changes split to their own feature spec |
| Theme drift / one-off colors per screen | All color via the Compose Recomp theme; review checks for ad-hoc hex |
| Nav-dock drag interaction is non-trivial in Compose | Implement via `ModalBottomSheet`/`AnchoredDraggable`, peek = dock height, expanded = full overflow (design README notes) |
| Premature removal of `docs/design/` while screens still reference it | Cleanup issue `Depends on` every screen issue; runs only when all are closed |

## Decision log

- 2026-06-18: Opened as a living-spec (open milestone, `Track: living-spec`) —
  an ongoing re-skin theme, not a finite phase.
- 2026-06-18: `docs/design/` is build scaffolding; a terminal cleanup issue
  removes it and its doc references once every screen issue is closed.
- 2026-06-18: Acceptance gate — cleanup resolved: the terminal issue removes
  `docs/design/` in full (screens + `kit.css` + README); the external Claude
  Design project is kept as an archive (not deleted).
- 2026-06-18 (#159 nav dock): the off-canvas "Übungen" overflow row was OMITTED
  — the app has no exercise-catalog screen and the milestone forbids new
  screens, so a disabled row would violate "overflow links reachable". A future
  exercise-catalog feature spec re-adds the link.
- 2026-06-18 (#160 plan launcher): a minimal edit to `ui/RackNavHost.kt`
  (PlanRoute) was permitted to drop the plan-overview inline-logging/timer
  plumbing — the nav-scaffold lane rule was a concurrency guard against the
  (already-merged) #159 agent; shared ViewModels/timer classes were kept (still
  used by the session player).
- 2026-06-18 (#160 deferrals, out of scope, documented in PR #185): the
  "Einzelnen Satz loggen" quick-log row (no single-set logging surface
  post-#161) and the plan-day footer "· aktualisiert <date>" (the Plan model has
  no `updated_at`).
