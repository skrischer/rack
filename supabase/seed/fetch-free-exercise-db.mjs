#!/usr/bin/env node
// Fetch the free-exercise-db public-domain exercise catalog and store the raw
// export under supabase/seed/free-exercise-db/exercises.json.
//
// This is the ONLINE data-acquisition step for the TEXT-ONLY gap-filler used by
// enrich-exercises.mjs. The enrichment routine reads the saved file OFFLINE so
// its transform is reproducible without network access. Re-run this only to
// refresh the source data.
//
// Data source: free-exercise-db (https://github.com/yuhonas/free-exercise-db),
// released into the public domain under the Unlicense. Only its instruction
// TEXT and primary/secondary muscle lists are consumed — NEVER its images, whose
// license is unresolved (see docs/specs/spec-exercise-detail.md, prior-art AVOID).

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const SELF_DIR = dirname(fileURLToPath(import.meta.url));
const OUT_FILE = join(SELF_DIR, "free-exercise-db", "exercises.json");
const SOURCE =
  "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json";

async function main() {
  const res = await fetch(SOURCE, { headers: { Accept: "application/json" } });
  if (!res.ok) {
    throw new Error(`free-exercise-db ${res.status} ${res.statusText} for ${SOURCE}`);
  }
  const all = await res.json();
  if (!Array.isArray(all) || all.length === 0) {
    throw new Error(`Unexpected shape from ${SOURCE}: expected a non-empty JSON array`);
  }
  mkdirSync(dirname(OUT_FILE), { recursive: true });
  writeFileSync(OUT_FILE, JSON.stringify(all, null, 2));
  console.log(`Wrote ${all.length} exercises to ${OUT_FILE}`);
}

main().catch((err) => {
  console.error(`fetch-free-exercise-db failed: ${err.message}`);
  process.exit(1);
});
