# Design tokens

> Normative. The Recomp visual language as reusable tokens, extracted verbatim
> from `artifact.html` (the project's own prototype — the source of truth for the
> theme). This file owns the **tokens** (color, type, spacing, radius, motion);
> `artifact.html` owns the prototype layout/markup, which is **not** normative.
> The Android Compose theme (Phase 3, issue #19) encodes these values exactly;
> every `/android` UI phase consumes them through that theme. Hex values here must
> match `artifact.html`'s `:root` and style block.

## Color palette

Raw values (from `artifact.html` `:root`):

| Token | Hex | Notes |
|---|---|---|
| `bg` | `#0d0e11` | App background; also the foreground color on volt-filled surfaces |
| `panel` | `#15171c` | Primary surface (cards, notes, tabs) |
| `panel-2` | `#1b1e24` | Elevated surface (card head/foot bands) |
| `line` | `#2a2e37` | Borders and dividers (1px) |
| `txt` | `#e8eaed` | Primary text |
| `dim` | `#8b909b` | Secondary/muted text, captions, focus labels |
| `volt` | `#c8f23a` | Single accent (volt/lime): active state, CTAs, key values, links |
| `volt-dim` | `#7e9120` | Muted accent: cue glyph, chevrons, "logged" confirmation |
| `muted-empty` | `#5a5f6a` | "No history / empty" placeholder text (one step below `dim`) |

Semantic roles (map the raw tokens; do not introduce new hues):

- **Accent / primary action** → `volt`. Text/icon *on* a volt fill → `bg`
  (`#0d0e11`), never white.
- **Surface elevation** → `bg` < `panel` < `panel-2`.
- **Border / divider** → `line` at 1px.
- **Text** → primary `txt`, secondary `dim`, disabled/empty `muted-empty`.

## Tag / category palette

Training-split color coding (push/pull/legs) and the superset accent:

| Token | Hex | Used for |
|---|---|---|
| `push` | `#c8f23a` | Push day (identical to `volt`) |
| `pull` | `#54c7ec` | Pull day (cyan) |
| `legs` | `#ff8a5b` | Legs day (orange); also the warning-band accent |
| `ss` (superset) | `#a78bfa` | Superset/circuit grouping (violet) |

Derived tints:

- Superset group background: `ss` at 5% — `rgba(167, 139, 250, 0.05)`, with a
  `3px` solid `ss` left border.
- Ambient page glow: `volt` at 6% — `radial-gradient(circle at 50% -10%,
  rgba(200, 242, 58, 0.06), transparent 55%)`.
- Warning band: background `#33231a`, border `legs`, text `#ffc6a3`.

## Typography

Two families, loaded from Google Fonts
(`Archivo:wght@400;600;800;900` + `Spline Sans Mono:wght@400;500;600`):

- **Archivo** (sans-serif) — display and body. Weights: 400 body, 600 semibold,
  800 section titles, 900 hero. Bundle these weights with the app (do not depend
  on a runtime font fetch).
- **Spline Sans Mono** (monospace) — labels, captions, numeric values, history,
  buttons. Weights: 400, 500, 600. Mono is the "instrument panel" voice: it is
  used for everything uppercase + letter-spaced and for all logged numbers.

Base: `line-height: 1.5`; hero `line-height: 0.95`. Anti-aliased.

Type scale (px → role), as used in the prototype:

| Size | Family / weight | Role |
|---|---|---|
| `clamp(34,9vw,58)` | Archivo 900, `-0.02em`, UPPER | Hero (`h1`) |
| 19 | Archivo 800, `-0.01em`, UPPER | Day/section title |
| 16 | Archivo 600 | Exercise name |
| 15 | Archivo 400 / Mono | Intro text; numeric input |
| 14 | Archivo 400 | Body paragraph |
| 13.5 | Archivo 400 | Exercise cue |
| 13 | Mono 600 / Archivo | Load value; day number; note heading |
| 12 | Mono 600, UPPER, tracked | Kicker, tabs, buttons, footer, labels |
| 11.5 | Mono 400 | "Last time" history line |
| 11 | Mono 400 | Day focus, expanded history, "logged", reset |
| 10.5 | Mono 600, UPPER | Superset header |
| 10 | Mono 400, UPPER | Tab sublabel, field caption |

Letter-spacing (tracking) by element: kicker `0.22em`; note heading `0.12em`;
tab sublabel / field caption `0.08em`–`0.1em`; superset header `0.07em`; day
focus / button / reset `0.06em`; footer `0.05em`; tabs `0.02em`; hero `-0.02em`;
day title `-0.01em`. Rule of thumb: uppercase mono labels are positively
tracked; large Archivo headings are negatively tracked.

Casing: all mono labels, tabs, buttons, kicker, and titles are `UPPERCASE`.

## Radius

| Token | px | Used for |
|---|---|---|
| `radius-sm` | 8 | Inputs, history box, reset button |
| `radius-md` | 9 | Day-number chip, primary button |
| `radius-lg` | 10 | Tabs |
| `radius-xl` | 16 | Day cards, note cards |

## Spacing

Loose 2px-grid scale (px) seen in the prototype:
`4, 6, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 26, 38`.

- Screen gutter: `18`. Content max width: `780` (phone-first; the app is a single
  column).
- Card insets: head `18 / 20`, row `15 / 20`, foot `14 / 20`, note `22`.
- Common element gaps: `8` (inputs/tabs), `12`–`14` (rows/sections).

## Motion

- Standard transition: `all 0.15s ease`.
- Press feedback: `scale(0.97)` on active (buttons, reset).
- Pane/content enter: `0.25s ease`, `translateY(6px) → 0` with opacity `0 → 1`.

Keep motion minimal and functional — short, eased, no bounce.

## Decoration

- Borders are always `1px solid line`.
- One ambient `volt`-at-6% radial glow anchored top-center of the screen.
- Cue/secondary glyphs (the `→` exercise cue, list chevrons) use `volt-dim`.

## Compose mapping notes

- This is a **bespoke dark theme**, not Material 3 dynamic color. Encode the
  tokens as a custom theme object (a `RecompColors` palette + `RecompType` +
  dimension/shape tokens) exposed via `CompositionLocal`/`MaterialTheme`
  overrides; do **not** enable dynamic/wallpaper color.
- Single accent: `volt`. There is no secondary accent — `pull`/`legs`/`ss` are
  category semantics, not general theme colors.
- On-accent content color is `bg` (`#0d0e11`), not white.
- Bundle Archivo + Spline Sans Mono as app font resources at the weights above;
  do not fetch fonts at runtime.
- Dark theme only for now (no light variant in the prototype); a future light
  theme is out of scope until designed.
