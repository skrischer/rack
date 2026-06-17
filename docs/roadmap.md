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
| 1 | Foundation & data backbone | — | — |
| 2 | rack-MCP: auth + tools + hosting | — | — |
| 3 | Android: auth + plan view + logging | — | — |
| 4 | Live sync & edit highlighting | — | — |
| 5 | In-app API-key management & onboarding | — | — |
| 6 | Agent visualization artifacts | — | — |
| 7 | Exercise execution detail & images | — | — |
| 8 | Rest & session timers | — | — |
| 9 | Guided session player | — | — |
| 10 | Push notifications & reminders | — | — |
| 11 | Overviews & dashboards | — | — |
| 12 | Settings & units | — | — |
| 13 | Plate calculator & 1RM estimates | — | — |

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

## North star

Every phase moves toward the loop where the user's own agent writes training
data through the rack-MCP and the change appears live and highlighted in the
native Android app.
