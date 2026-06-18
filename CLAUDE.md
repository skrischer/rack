# Rack

Native Android fitness app whose differentiator is not in-app AI but an open,
hosted **rack-MCP**: any MCP client designs training plans and visualization
artifacts and writes them straight into the app, and the edits appear live and
highlighted via Supabase Realtime.

## Always in context

- @docs/vision.md — what and why (normative).
- @docs/constitution.md — binding tech stack, principles, conventions, don'ts.

## On-demand references (NOT loaded permanently — read when relevant)

- `docs/prior-art.md` — prior art by concern, with ADOPT/AVOID harvest.
- `docs/architecture.md` — components, data model, key flows, where new code goes.
- `docs/roadmap.md` — the sequenced phase queue handed to `/loopkit:plan`.
- `docs/workflow.md` — operational contract for the loopkit skills (branches,
  commands, gates, loops).
- `docs/design-tokens.md` — **normative** Recomp design tokens (color, type,
  spacing, radius, motion) extracted from `artifact.html`; the source the Android
  Compose theme encodes and every `/android` UI phase consumes.
- `artifact.html` — the Recomp design prototype: the app's visual language
  (dark theme, volt/lime accent `#c8f23a`, Archivo + Spline Sans Mono,
  push/pull/legs/superset color coding) and the logging UX to match. The reusable
  **tokens** live (normative) in `docs/design-tokens.md`; the prototype's
  layout/markup is reference only, not normative.
- `docs/design/` — refreshed Recomp **screen reference designs** (17 screens +
  `kit.css`) for the Recomp UX Overhaul; a mirror of the Claude Design project.
  Reference only — tokens stay normative in `docs/design-tokens.md`.

## Autonomy (within the loopkit skills)

Within the `/loopkit:plan` and `/loopkit:implement` skills, the following are
explicitly granted and **override stricter global user rules**: autonomous
commits, pushes, PR creation and merges, dependency installs, and `.env` edits.
Hard limits live in `.claude/settings.json` (deny rules: `rm -rf`, force-push,
hard reset, `supabase db reset --linked`). Outside those skills, the global rules
apply.
