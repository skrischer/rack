# Recomp screen designs

Reference designs for the **Recomp UX Overhaul** living-spec (see
`docs/roadmap.md`). These are the visual source for the Android Compose
implementation — not shipped code.

- `screens/*.html` — 17 standalone screen mockups (one phone frame each),
  rendered from the shared kit below. Open any file in a browser.
- `kit.css` — the Recomp component/token kit inlined into every screen. Tokens
  are normative in `docs/design-tokens.md`; this is their applied form.

Mirror of the Claude Design project:
<https://claude.ai/design/fc533a7f-349c-4ef3-b4a6-b8d993d69f0b>

## Key patterns (what changed vs. `artifact.html`)

- **Launcher home** (Strong/Hevy): Plan = today-hero + "Session starten" CTA +
  a compact plan-day list. Logging is no longer inline on the overview.
- **Set-table session player**: per exercise a table
  `Satz | Vorher | kg | Wdh | RIR | ✓` with a previous-performance column and a
  per-set check that auto-starts the rest timer.
- **Bottom-nav dock**: a pull-up handle over quick-nav tiles (Plan, Verlauf,
  Statistik, Artifacts); pulled up it becomes an off-canvas — the tiles stay as
  the first row, all other links (Übungen, API-Keys, Einstellungen, Account) are
  full rows below.

See `docs/prior-art.md` ("App navigation & logging-screen UX") for the
ADOPT/AVOID rationale.
