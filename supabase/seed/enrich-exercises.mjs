#!/usr/bin/env node
// Idempotent enrichment routine for the public.exercises catalog (issue #48).
//
// Fills the Phase 7 detail fields (instructions, primary_muscles,
// secondary_muscles, image_path) on EXISTING catalog rows from two OFFLINE
// exports:
//   - wger (CC-BY-SA 4.0): instructions, primary/secondary muscles, and images.
//   - free-exercise-db (public domain): instruction TEXT and muscles, used ONLY
//     to fill empty wger instruction lists. Its IMAGES are NEVER ingested.
//
// Run order:
//   1. node supabase/seed/fetch-wger.mjs            (raw wger export, gitignored)
//   2. node supabase/seed/fetch-free-exercise-db.mjs (raw fed export, gitignored)
//   3. node supabase/seed/enrich-exercises.mjs       (this routine)
//
// Idempotent + additive (keyed on the stable lower(name); wger id de-dupes the
// source): re-running uploads no duplicate object and the enrichment SQL
// coalesces, so a second run produces no diff. Fails loud on bad input — there
// is no silent partial enrich. See docs/specs/spec-exercise-detail.md.

import { readFileSync, existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import {
  buildPatches,
  buildEnrichSql,
  isFreeExerciseDbImage,
  fail,
} from "./enrich/transform.mjs";

const SELF_DIR = dirname(fileURLToPath(import.meta.url));
const WGER_FILE = join(SELF_DIR, "wger", "exercises.json");
const FED_FILE = join(SELF_DIR, "free-exercise-db", "exercises.json");
const BUCKET = "exercise-images";

const SUPABASE_URL = process.env.SUPABASE_URL || "http://127.0.0.1:55321";
const SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
// How to apply enrichment SQL as the DB owner locally. The harness exposes the
// local Postgres via the supabase_db_rack container; override with RACK_PSQL.
const PSQL_CMD = (process.env.RACK_PSQL || "docker exec -i supabase_db_rack psql -U postgres -d postgres")
  .split(" ")
  .filter(Boolean);

function readExport(file, hint) {
  if (!existsSync(file)) {
    fail(`raw export not found at ${file}. Run '${hint}' first.`);
  }
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (err) {
    fail(`raw export at ${file} is not valid JSON: ${err.message}`);
  }
}

function runPsql(sql) {
  const [cmd, ...args] = PSQL_CMD;
  const res = spawnSync(cmd, [...args, "-v", "ON_ERROR_STOP=1", "-q"], {
    input: sql,
    encoding: "utf8",
  });
  if (res.error) fail(`failed to run psql (${cmd}): ${res.error.message}`);
  if (res.status !== 0) fail(`psql exited ${res.status}: ${res.stderr || res.stdout}`);
  return res.stdout;
}

function queryScalar(sql) {
  const out = runPsql(`select ${sql} as v \\gset\n\\echo :v`);
  const line = out.trim().split("\n").filter(Boolean).pop();
  return line ? line.trim() : "";
}

async function uploadImage(url, key) {
  if (isFreeExerciseDbImage(key) || isFreeExerciseDbImage(url)) {
    fail(`refusing to upload a free-exercise-db image: ${url}`);
  }
  const exists = await fetch(
    `${SUPABASE_URL}/storage/v1/object/info/public/${BUCKET}/${key}`,
  );
  if (exists.ok) return "skipped";
  const source = await fetch(url);
  if (!source.ok) {
    console.warn(`  ! skip image (source ${source.status}) for ${key}`);
    return "missing";
  }
  const body = Buffer.from(await source.arrayBuffer());
  const contentType = source.headers.get("content-type") || "image/png";
  const res = await fetch(`${SUPABASE_URL}/storage/v1/object/${BUCKET}/${key}`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": contentType,
      "x-upsert": "true",
    },
    body,
  });
  if (!res.ok) fail(`storage upload failed (${res.status}) for ${key}: ${await res.text()}`);
  return "uploaded";
}

async function uploadImages(patches) {
  if (!SERVICE_ROLE_KEY) {
    fail("SUPABASE_SERVICE_ROLE_KEY is required to upload wger images to Storage.");
  }
  const withImages = patches.filter((p) => p.imageUrl && p.imageKey);
  let uploaded = 0;
  let skipped = 0;
  let missing = 0;
  for (const p of withImages) {
    const result = await uploadImage(p.imageUrl, p.imageKey);
    if (result === "uploaded") uploaded += 1;
    else if (result === "skipped") skipped += 1;
    else {
      missing += 1;
      p.imageKey = null; // drop unresolved image so image_path is not set to a 404
    }
  }
  console.log(
    `Images: ${uploaded} uploaded, ${skipped} already present, ${missing} unresolved ` +
      `(of ${withImages.length} wger images).`,
  );
}

function assertNoFreeExerciseDbObjects() {
  const traces = queryScalar(
    "(select count(*) from public.exercises where image_path is not null and image_path !~ '^[0-9a-f]')",
  );
  // image_path is the wger media key, e.g. '1962/<uuid>.png' (numeric exercise
  // id segment). A free-exercise-db image would be 'Exercise_Name/0.jpg'.
  const fed = queryScalar(
    "(select count(*) from public.exercises where image_path ~ '/[0-9]+\\.(jpg|jpeg|png|gif|webp)$' and image_path !~ '^[0-9]+/')",
  );
  if (Number(fed) > 0) {
    fail(`${fed} catalog rows have a free-exercise-db-shaped image_path; guard breached.`);
  }
  console.log(`Guard: no free-exercise-db image_path in the catalog (non-wger-keyed: ${traces}).`);
}

function reportCoverage() {
  const total = queryScalar("(select count(*) from public.exercises)");
  const withInstr = queryScalar(
    "(select count(*) from public.exercises where instructions is not null and array_length(instructions,1) >= 1)",
  );
  const empty = Number(total) - Number(withInstr);
  console.log(
    `Coverage: ${withInstr}/${total} exercises have >= 1 instruction; ` +
      `${empty} are explicitly flagged with no instructions (UI shows the empty state).`,
  );
}

async function main() {
  const wgerExport = readExport(WGER_FILE, "node supabase/seed/fetch-wger.mjs");
  const fedExport = readExport(FED_FILE, "node supabase/seed/fetch-free-exercise-db.mjs");
  const patches = buildPatches(wgerExport, fedExport);
  console.log(`Built ${patches.length} enrichment patches (keyed on lower(name)).`);

  await uploadImages(patches);

  const sql = buildEnrichSql(patches);
  runPsql(sql);
  console.log("Applied enrichment SQL (idempotent, additive, matched on lower(name)).");

  assertNoFreeExerciseDbObjects();
  reportCoverage();
  console.log("Enrichment complete.");
}

main().catch((err) => {
  console.error(err.message || String(err));
  process.exit(1);
});
