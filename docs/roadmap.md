# Rack — Roadmap

> Living document: the sequenced queue of phases. The hand-off to `/plan`, which
> picks the next phase, creates its spec + issues, and links them back here.
> No status markers — progress lives in the GitHub issues and milestones each
> phase links to. Specs (created by `/plan`) carry no lifecycle state either;
> a spec is "accepted" once merged on the default branch with a milestone and
> issues.

## Phase overview

| Phase | Name | Spec | Milestone |
|---|---|---|---|
| 1 | Foundation & data backbone | [spec](specs/spec-foundation.md) | [#1](https://github.com/skrischer/rack/milestone/1) |
| 2 | rack-MCP: auth + tools + hosting | [spec](specs/spec-rack-mcp.md) | [#2](https://github.com/skrischer/rack/milestone/2) |
| 3 | Android: auth + plan view + logging | [spec](specs/spec-android-app.md) | [#3](https://github.com/skrischer/rack/milestone/3) |
| 4 | Live sync & edit highlighting | [spec](specs/spec-live-sync.md) | [#4](https://github.com/skrischer/rack/milestone/4) |
| 5 | In-app API-key management & onboarding | [spec](specs/spec-api-key-management.md) | [#5](https://github.com/skrischer/rack/milestone/5) |
| 6 | Agent visualization artifacts | [spec](specs/spec-visualization-artifacts.md) | [#6](https://github.com/skrischer/rack/milestone/6) |
| 7 | Exercise execution detail & images | [spec](specs/spec-exercise-detail.md) | [#7](https://github.com/skrischer/rack/milestone/7) |
| 8 | Rest & session timers | [spec](specs/spec-timers.md) | [#8](https://github.com/skrischer/rack/milestone/8) |
| 9 | Guided session player | [spec](specs/spec-session-player.md) | [#9](https://github.com/skrischer/rack/milestone/9) |
| 10 | Push notifications & reminders | [spec](specs/spec-push-notifications.md) | [#10](https://github.com/skrischer/rack/milestone/10) |
| 11 | Overviews & dashboards | [spec](specs/spec-dashboards.md) | [#11](https://github.com/skrischer/rack/milestone/11) |
| 12 | Settings & units | [spec](specs/spec-settings.md) | [#12](https://github.com/skrischer/rack/milestone/12) |
| 13 | Plate calculator & 1RM estimates | [spec](specs/spec-plate-calc-1rm.md) | [#13](https://github.com/skrischer/rack/milestone/13) |
| 14 | Exercise catalog quality & search | [spec](specs/spec-catalog-search.md) | [#15](https://github.com/skrischer/rack/milestone/15) |
| 15 | Structured plan prescription & grouping | [spec](specs/spec-structured-prescription.md) | [#16](https://github.com/skrischer/rack/milestone/16) |
| 16 | MCP authoring ergonomics & stability | [spec](specs/spec-mcp-authoring.md) | [#17](https://github.com/skrischer/rack/milestone/17) |
| 17 | Custom / user-authored exercises | [spec](specs/spec-custom-exercises.md) | [#18](https://github.com/skrischer/rack/milestone/18) |

A phase gets a Spec link once `/plan` drafts it, and a Milestone link once the
spec is merged. The milestone (open/closed + issue progress) is where status
lives.

Phase intent:

- **1 — Foundation & data backbone.** Repo scaffold (`/android`, `/mcp`,
  `/supabase`, `/docs`), Supabase wiring, schema + RLS for
  plans/days/exercises/sets/logs/api_keys, exercise catalog seeded from wger
  (CC-BY-SA, with attribution).
- **2 — rack-MCP: auth + tools + hosting.** Node MCP server (Streamable HTTP),
  API-key → user resolution, user-scoped read/write tools, Zod schemas,
  key-mint admin endpoint, Docker + Caddy deployment on the VPS.
- **3 — Android: auth + plan view + logging.** Supabase Auth login, repository
  layer, plan/day/exercise view, set logging + history in the Recomp UI.
- **4 — Live sync & edit highlighting.** Realtime subscription and
  `source`-based highlighting of agent edits — the wow loop, end to end.
- **5 — In-app API-key management & onboarding.** Create/revoke MCP API keys via
  the admin endpoint; guide the user through connecting an MCP client.
- **6 — Agent visualization artifacts.** Artifact tool(s) in the MCP + Storage +
  in-app display.

Standard, proven fitness-app features that round Rack out into a complete daily
trainer (appended after the USP core; order is a living-doc concern `/plan` may
re-prioritize):

- **7 — Exercise execution detail & images.** Exercise detail screen: how-to
  instructions, target muscles, equipment, and images. Sourced from the wger
  seed (CC-BY-SA images + step-by-step instructions), whose coverage is uneven —
  this phase fills the gaps for exercises that lack them. Depends on Phase 1
  (seed) and Phase 3 (app).
- **8 — Rest & session timers.** Per-set rest timer that auto-starts on set log,
  with defaults by exercise type (compound 2–3 min, isolation 60–90 s, superset
  60–90 s, circuit 30–45 s — from the Recomp notes), a running session-duration
  timer, and superset/circuit rotation cues. Depends on Phase 3.
- **9 — Guided session player.** An active workout mode that steps through a
  day's exercises, ticking sets, with the rest timer and superset/circuit
  rotation integrated into the logging flow. Builds on Phases 7 and 8.
- **10 — Push notifications & reminders.** FCM for rest-timer-done while
  backgrounded and workout reminders/scheduling (local scheduled where
  possible), plus an optional "your agent updated your plan" push (server-sent
  via a Supabase Edge Function on change) that reinforces the live-sync USP.
  Depends on Phase 3 (and Phase 4 for the agent-edit push).
- **11 — Overviews & dashboards.** Home overview (weekly volume per muscle/tag,
  training streak, recent sessions), per-exercise progress, and a
  calendar/history view over the logged data. Depends on Phase 3.
- **12 — Settings & units.** kg/lb unit switching, rest-timer defaults, theme,
  and profile. Depends on Phase 3.
- **13 — Plate calculator & 1RM estimates.** A barbell plate calculator per
  target weight and a 1RM estimate from load × reps. Depends on Phase 3.

Beta-test hardening (from `beta-test-protocol.md`, 2026-06-19 — the MCP beta run
against the shipped server; see the prior-art harvest and the architecture
deltas). These fold the lived-experience gaps back into the queue:

- **14 — Exercise catalog quality & search.** Curate the wger seed (canonical
  base entries, `aliases[]` for German→English and variant naming, a normalized
  equipment vocabulary incl. Cable/Machine, drop non-English/junk rows); rebuild
  `search_exercises` as a Postgres hybrid (`pg_trgm` trigram + `tsvector`
  ranking, token-AND multi-word, equipment/muscle filters, canonical-first) —
  optionally a `resolve_exercise` name→best-match helper. Addresses beta §4.1,
  §4.6. Depends on Phase 1 (catalog/seed) and Phase 2 (MCP search tool).
- **15 — Structured plan prescription & grouping.** Migrate `plan_exercises` to
  typed fields (`sets`, `rep_min`, `rep_max`, `rir_low`, `rir_high`,
  `rest_seconds`), replace the implicit `superset_label` with an explicit
  `superset_id` + group type (superset / circuit) + group rest, and add custom
  user exercises (`is_custom` / non-null `user_id`, RLS) so a plan can reference
  an exercise absent from the catalog. App + MCP + migrations move together.
  Addresses beta §4.2. Depends on Phases 1, 2, 3; ripples into the Recomp UX
  Overhaul living-spec (set-table rendering) and the session player.
- **16 — MCP authoring ergonomics & stability.** A transactional nested
  `create_plan` (plan → days → exercises → sets in one validated call), tool
  annotations + structured descriptions for one-step discovery, and the
  `get_plan`-after-writes stability investigation. Addresses beta §4.3, §4.4,
  §4.5. Depends on Phase 2; the nested create writes the Phase 15 structured
  fields, so plan 15 before 16. The stability fix is independent and may be
  pulled forward as a `track:adhoc` fast-lane issue.
- **17 — Custom / user-authored exercises.** Let a plan reference an exercise
  absent from the catalog (beta §4.2 mandatory-catalog gap): a custom exercise is
  an `exercises` row with `user_id` set (mixed public-read + owner RLS),
  referenced by `plan_exercises.exercise_id` like any catalog row, authored via a
  `create_exercise` MCP tool and surfaced in `search_exercises`. Split out of
  Phase 15 at its acceptance gate to keep the prescription migration focused.
  Depends on Phase 14 (the `exercises` table + search) only — a plan references a
  custom exercise regardless of prescription structure.

## Living-spec themes

> Ongoing themes (workflow.md "living-spec" track): a spec plus an open
> milestone that accretes issues over time and is never archived.
> Human-initiated; `/loopkit:plan` keeps the links current.

| Theme | Spec | Milestone |
|---|---|---|
| Recomp UX Overhaul | [spec](specs/spec-recomp-ux-overhaul.md) | [#14](https://github.com/skrischer/rack/milestone/14) |

- **Recomp UX Overhaul.** Bring every app surface up to the refreshed Recomp
  design language (normative tokens in `docs/design-tokens.md`): the Strong/Hevy
  **launcher** model for Plan/Home (today-hero + Start
  CTA + compact plan list), **set-table** logging in the session player
  (`Satz | Vorher | kg | Wdh | RIR | ✓`, a previous-performance column, a per-set
  check that auto-starts the rest timer), a pull-up **bottom-nav dock** with an
  off-canvas overflow (quick-nav tiles in the first row, all other links as full
  rows below), and an end-to-end polish pass (spacing, empty/loading/error
  states, motion) across all screens. Living-spec: per-screen / per-pattern
  issues on an open milestone, not archived. Depends on Phase 3 and the relevant
  feature phase per screen.

## North star

Every phase moves toward the loop where the user's own agent writes training
data through the rack-MCP and the change appears live and highlighted in the
native Android app.
