# Prior Art

> Descriptive, living document. Indexed BY CONCERN, not by project. Add
> entries whenever new references surface; gaps are fine.

## Open exercise data & training domain model

### wger-project/wger

- Path: https://github.com/wger-project/wger — exercise dataset, `wger/exercises/`
- License: code AGPL-3.0-or-later; **exercise & ingredient data CC-BY-SA (3.0/4.0)**
- Verdict: reuse (data only) — seed Rack's exercise catalog from the CC-BY-SA dataset; never reuse the AGPL code.
- Date: 2026-06-17
- Notes:
  - ADOPT: the 400+ exercise catalog (name, muscles, equipment, category) as Rack's seed data, with attribution; the proven domain model concepts — routines → days → sets, supersets/circuits, double progression, rest timers.
  - AVOID: pulling in any AGPL-3.0 code. AGPL is viral for networked services — reusing wger's backend would force Rack's whole hosted stack under AGPL. Build the backend fresh. Keep CC-BY-SA attribution + share-alike on any derivative dataset.

## Programmable agent access (MCP write-through to fitness data)

### Juxsta/wger-mcp

- Path: https://github.com/Juxsta/wger-mcp
- License: MIT
- Verdict: reference-only — closest prior art for "agent creates routines"; its limits define Rack's USP.
- Date: 2026-06-17
- Notes:
  - ADOPT: tool granularity as a design reference — search exercises (read) + create/update/delete routines, days, exercises, sets (write); 12 tools total is a sane surface.
  - AVOID: stdio transport (local, single-user), env-var single API key, and request-response only with **no push to any client app**. Rack is the inverse: hosted remote MCP, per-user API keys, and every write propagates live to the native app.

## Read-only fitness-data MCPs (the opposite direction — anti-pattern for us)

### garmin / whoop / strava / trainingpeaks / xert / boostcamp MCP servers

- Path: e.g. https://github.com/Taxuspt/garmin_mcp, https://github.com/nissand/whoop-mcp-server-claude, https://github.com/Milofax/xert-mcp, https://github.com/alex-keyes/boostcamp-mcp
- License: mixed (mostly MIT)
- Verdict: reference-only — confirm the gap, do not copy the model.
- Date: 2026-06-17
- Notes:
  - ADOPT: nothing structurally; useful only to confirm the landscape.
  - AVOID: their whole shape — pulling closed third-party data INTO the LLM for analysis. Rack writes user-authored data OUT to an app the user owns, in real time. Different direction, different value.

## Native Android workout-logging UX

### noahjutz/GymRoutines & Flexify

- Path: https://f-droid.org/packages/com.noahjutz.gymroutines/ ; Flexify (F-Droid)
- License: GPL-family / FOSS (verify per app before copying any code)
- Verdict: reference-only — UX reference for native logging, not a code dependency.
- Date: 2026-06-17
- Notes:
  - ADOPT: fast set/rep/weight logging interaction, local-first responsiveness, exercise-library browsing patterns. The Recomp `artifact.html` prototype already encodes Rack's own opinionated take (kg + per-set reps + RIR + history disclosure).
  - AVOID: their local-only, no-backend, no-programmable-layer ceiling — exactly what Rack adds.

## Remote MCP authentication (multi-user, per-user API key)

### MCP spec — remote server auth & Streamable HTTP transport

- Path: MCP 2025+ spec; remote-MCP hosting/auth best-practice writeups
- License: n/a (spec/guidance)
- Verdict: reference — informs the rack-MCP auth design.
- Date: 2026-06-17
- Notes:
  - ADOPT: Streamable HTTP transport for a hosted multi-user server; static **per-user API key** issued in-app, sent server-side, mapping key → user so every tool call is scoped to that user's data; keys never in client code.
  - AVOID: full OAuth 2.1 + PKCE — correct for public multi-tenant SaaS, over-engineered for a friends-and-family circle on one VPS. A rotatable per-user API key over HTTPS is proportionate.

## Real-time sync & live edit highlighting

### Collaborative-editing patterns (WebSocket presence, Yjs/CRDT)

- Path: Yjs, CRDT/collaborative-editor system designs, WebSocket presence patterns
- License: n/a (patterns)
- Verdict: reference — adopt the visible-remote-edit idea, reject the heavy machinery.
- Date: 2026-06-17
- Notes:
  - ADOPT: WebSocket push of server-authoritative changes; visual highlight of remote/agent edits as they land; optimistic UI on the app's own writes.
  - AVOID: CRDT/Yjs conflict-free merging. Rack's concurrency is light — a single owner plus their own agent editing the owner's data from two surfaces. Server-authoritative state + per-entity version + last-write-wins is enough; CRDTs would be cost without benefit.
