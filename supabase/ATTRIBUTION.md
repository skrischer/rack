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
