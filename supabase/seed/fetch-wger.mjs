#!/usr/bin/env node
// Fetch the full wger exercise catalog (CC-BY-SA 4.0) and store the raw export
// under supabase/seed/wger/exercises.json.
//
// This is the ONLINE data-acquisition step. The committed importer
// (import-wger.mjs) reads the saved file OFFLINE, so the SQL transform is
// reproducible without network access. Re-run this only to refresh the source
// data from https://wger.de/api/v2/.
//
// ALL languages are fetched (no `language=` filter): the importer keeps the
// English (language=2) translation as the catalog name and turns every other
// translation name into a search alias (issue #189). Without the filter the
// per-exercise translations[] carries every language; with it, only English.
// Entries with no English translation are skipped by the importer.
//
// Data source: wger Workout Manager (https://wger.de) — exercise database
// licensed CC-BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/).

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const SELF_DIR = dirname(fileURLToPath(import.meta.url));
const OUT_FILE = join(SELF_DIR, "wger", "exercises.json");
const API = "https://wger.de/api/v2/exerciseinfo/";
const PAGE_SIZE = 100;

async function fetchPage(offset) {
  const url = `${API}?limit=${PAGE_SIZE}&offset=${offset}`;
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  if (!res.ok) {
    throw new Error(`wger API ${res.status} ${res.statusText} for ${url}`);
  }
  return res.json();
}

async function main() {
  const all = [];
  let offset = 0;
  let total = null;

  for (;;) {
    const page = await fetchPage(offset);
    if (!Array.isArray(page.results)) {
      throw new Error(`Unexpected page shape at offset ${offset}: no results[]`);
    }
    if (total === null) total = page.count;
    all.push(...page.results);
    process.stdout.write(`\rFetched ${all.length}/${total} exercises`);
    if (!page.next) break;
    offset += PAGE_SIZE;
  }
  process.stdout.write("\n");

  mkdirSync(dirname(OUT_FILE), { recursive: true });
  writeFileSync(OUT_FILE, JSON.stringify(all, null, 2));
  console.log(`Wrote ${all.length} exercises to ${OUT_FILE}`);
}

main().catch((err) => {
  console.error(`fetch-wger failed: ${err.message}`);
  process.exit(1);
});
