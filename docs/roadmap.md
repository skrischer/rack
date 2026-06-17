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

## North star

Every phase moves toward the loop where the user's own agent writes training
data through the rack-MCP and the change appears live and highlighted in the
native Android app.
