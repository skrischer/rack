#!/usr/bin/env node
// Transform the raw wger CC-BY-SA exercise export into committed seed SQL.
//
// Reads the OFFLINE raw export saved by fetch-wger.mjs at
// supabase/seed/wger/exercises.json and emits idempotent INSERTs into
// public.exercises at supabase/seed.sql (the single path config.toml's
// [db.seed] sql_paths already loads on `supabase db reset`).
//
// The pure transform lives in ./import/transform.mjs (deterministic, unit-tested
// without I/O); this runner is just the read-export / write-seed glue. It fails
// loudly (non-zero exit, clear message) if the export is missing or malformed —
// there is no silent partial seed. Non-English-only entries (no language=2
// translation) are skipped, not a failure: their other-language names already
// seed the matching English row's aliases.
//
// Data source: wger Workout Manager (https://wger.de), exercise database
// licensed CC-BY-SA 4.0. See supabase/ATTRIBUTION.md.

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { buildRows, buildSql, fail } from "./import/transform.mjs";

const SELF_DIR = dirname(fileURLToPath(import.meta.url));
const SUPABASE_DIR = join(SELF_DIR, "..");
const RAW_FILE = join(SELF_DIR, "wger", "exercises.json");
const OUT_FILE = join(SUPABASE_DIR, "seed.sql");

function readExport() {
  let raw;
  try {
    raw = readFileSync(RAW_FILE, "utf8");
  } catch {
    fail(
      `raw export not found at ${RAW_FILE}. ` +
        `Run 'node supabase/seed/fetch-wger.mjs' first. ` +
        `Expected a JSON array of wger exerciseinfo objects ` +
        `(each with translations[], category, muscles[], equipment[], license, license_author).`,
    );
  }
  try {
    return JSON.parse(raw);
  } catch (err) {
    fail(`raw export at ${RAW_FILE} is not valid JSON: ${err.message}`);
  }
}

function main() {
  const exercises = readExport();
  const { rows, stats } = buildRows(exercises);
  writeFileSync(OUT_FILE, buildSql(rows));
  console.log(
    `Wrote ${rows.length} exercises to ${OUT_FILE} ` +
      `(${stats.source} source items, ${stats.dupes} duplicate names collapsed, ` +
      `${stats.skipped} non-English skipped, ${stats.canonical} canonical).`,
  );
}

try {
  main();
} catch (err) {
  console.error(err.message || String(err));
  process.exit(1);
}
