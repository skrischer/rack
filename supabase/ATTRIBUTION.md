# Attribution

## Exercise catalog — wger (CC-BY-SA 4.0)

The `public.exercises` catalog seeded by `supabase/seed.sql` is derived from the
exercise database of the **wger Workout Manager** project.

- Source: https://wger.de — exercise database via the public REST API
  (`https://wger.de/api/v2/exerciseinfo/?language=2`).
- License: **Creative Commons Attribution-ShareAlike 4.0 International
  (CC-BY-SA 4.0)** — https://creativecommons.org/licenses/by-sa/4.0/
- Per-row attribution: each seeded row carries `license` and `license_author`
  taken from the upstream record (defaulting to `CC-BY-SA 4.0` / `wger.de` when
  the upstream datum is absent). A small number of upstream entries are licensed
  CC-BY-SA 3.0 or CC0; the per-row `license` column records the actual license.

### Share-alike

This seed is a **derivative** of the wger exercise data. Under CC-BY-SA 4.0, any
redistribution of this catalog (or works derived from it) must:

1. Credit the wger project (https://wger.de) as the source.
2. Retain this attribution and the CC-BY-SA 4.0 license.
3. Distribute any adaptations under the same CC-BY-SA 4.0 (share-alike) terms.

Only the wger **data** is used here under CC-BY-SA. No wger **code** (which is
AGPL-licensed) is incorporated into this project.

### Regenerating the seed

1. `node supabase/seed/fetch-wger.mjs` — fetches the English catalog from the
   wger API into `supabase/seed/wger/exercises.json` (the raw export; gitignored,
   ~4 MB).
2. `node supabase/seed/import-wger.mjs` — transforms that export into the
   committed `supabase/seed.sql`.

## Exercise detail enrichment — wger images + instructions (CC-BY-SA 4.0)

`supabase/seed/enrich-exercises.mjs` enriches the existing catalog rows (Phase 7)
with how-to instructions, primary/secondary muscles, and images. wger is the
PRIMARY source: instruction text is parsed from the wger translation
`description`, target muscles from `muscles`/`muscles_secondary`, and the main
image is uploaded to the public `exercise-images` Storage bucket and referenced
by `image_path`.

- These wger images and instructions are **CC-BY-SA 4.0** — the same share-alike
  terms above apply. The detail screen renders the per-row `license` +
  `license_author` whenever wger-sourced text or an image is shown.
- The routine writes directly to the local DB and Storage; it commits no derived
  CC-BY-SA data file to the repo (the raw export and uploaded image objects are
  gitignored / live in Storage), so no separate data-file LICENSE note is needed
  beyond this attribution. If a derived CC-BY-SA seed/export file is ever
  committed under `/supabase`, it must ship with this same CC-BY-SA note.

## Instruction gap-filler — free-exercise-db (Public Domain / Unlicense)

Where a wger exercise has no instruction text, the routine fills it from the
**free-exercise-db** project, released into the public domain.

- Source: https://github.com/yuhonas/free-exercise-db (`dist/exercises.json`).
- License: **Unlicense** (public domain) — https://unlicense.org.
- Used as a TEXT-ONLY gap-filler: only its instruction text and muscle lists are
  consumed, and only to fill an EMPTY wger instruction list — wger text is never
  overwritten. Its **images are NEVER ingested** (their license is unresolved);
  an importer guard rejects any free-exercise-db-shaped image path.

### Running the enrichment

1. `node supabase/seed/fetch-wger.mjs` — raw wger export (gitignored).
2. `node supabase/seed/fetch-free-exercise-db.mjs` — raw free-exercise-db export
   into `supabase/seed/free-exercise-db/exercises.json` (gitignored).
3. `node supabase/seed/enrich-exercises.mjs` — idempotent, additive enrichment of
   the existing catalog rows (keyed on `lower(name)`); re-running produces no row
   duplication and no diff. Needs `SUPABASE_SERVICE_ROLE_KEY` (for Storage
   uploads) and the local Postgres reachable via `docker exec supabase_db_rack
   psql` (override with `RACK_PSQL`).
