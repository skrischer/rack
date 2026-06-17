// Pure transforms for the exercise enrichment routine (enrich-exercises.mjs).
//
// No I/O, no network, no DB: these functions turn the raw wger + free-exercise-db
// exports into idempotent enrichment records and SQL so the routine is fully
// deterministic and unit-testable. See docs/specs/spec-exercise-detail.md.
//
// Source precedence is wger-first: free-exercise-db (public domain) only fills
// EMPTY wger instruction lists and never overwrites wger text. Images come ONLY
// from wger CC-BY-SA; any free-exercise-db image path is rejected by guard.

const LANGUAGE_EN = 2;

/** Throws with a clear, fail-loud message — there is no silent partial enrich. */
export function fail(message) {
  throw new Error(`enrich-exercises: ${message}`);
}

/** Normalizes a catalog name to its idempotent match key (the seed key). */
export function nameKey(name) {
  return String(name).trim().toLowerCase();
}

/** The English (language=2) wger translation for an exercise, or null. */
function englishTranslation(exercise) {
  return (
    (exercise.translations || []).find(
      (t) => t && t.language === LANGUAGE_EN && typeof t.name === "string" && t.name.trim(),
    ) || null
  );
}

// Strip one layer of HTML tags and decode the handful of entities wger emits.
function stripHtml(fragment) {
  return fragment
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/​/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Parses a wger translation `description` (HTML) into an ordered list of
 * instruction steps. Prefers explicit list items (`<li>`); falls back to
 * paragraphs/line breaks. Returns [] when no usable text is present.
 */
export function parseWgerSteps(descriptionHtml) {
  if (typeof descriptionHtml !== "string" || !descriptionHtml.trim()) return [];
  const listItems = [...descriptionHtml.matchAll(/<li\b[^>]*>([\s\S]*?)<\/li>/gi)].map((m) =>
    stripHtml(m[1]),
  );
  const candidates =
    listItems.length > 0
      ? listItems
      : descriptionHtml
          .replace(/<\/(p|div|br)\s*>/gi, "\n")
          .replace(/<br\s*\/?>/gi, "\n")
          .split(/\n+/)
          .map(stripHtml);
  return candidates.filter((s) => s.length > 0);
}

/** Friendly muscle name from a wger muscle object; "" when unusable. */
function muscleName(m) {
  return ((m && (m.name_en || m.name)) || "").trim();
}

/** De-duplicated list of mapped, non-empty names preserving order. */
function names(list, pick) {
  const out = [];
  const seen = new Set();
  for (const item of list || []) {
    const n = pick(item);
    if (n && !seen.has(n)) {
      seen.add(n);
      out.push(n);
    }
  }
  return out;
}

/** The main wger image URL for an exercise, or null when none exists. */
export function wgerMainImageUrl(exercise) {
  const images = Array.isArray(exercise.images) ? exercise.images : [];
  if (images.length === 0) return null;
  const main = images.find((i) => i && i.is_main) || images[0];
  const url = main && typeof main.image === "string" ? main.image.trim() : "";
  return url || null;
}

/**
 * Guard: a value is a free-exercise-db image reference if it looks like that
 * project's relative `Exercise_Name/0.jpg` path — a non-wger directory and a
 * bare integer index filename (wger media keys are `<numeric-id>/<uuid>.ext`).
 * The enrichment routine NEVER ingests free-exercise-db images (unresolved
 * license); this guard lets it assert that no image_path traces back to it.
 */
export function isFreeExerciseDbImage(value) {
  if (typeof value !== "string") return false;
  const v = value.trim();
  if (!v || /^https?:\/\//i.test(v)) return false;
  const match = v.match(/^([^/]+)\/.+\.(jpe?g|png|gif|webp)$/i);
  if (!match) return false;
  // wger media keys are derived from /media/exercise-images/<numeric-id>/<file>,
  // so their leading directory is ALWAYS the purely-numeric exercise id.
  // free-exercise-db paths are 'Exercise_Name/0.jpg' — a word-named directory.
  // Anything whose directory is not purely numeric is treated as non-wger.
  return !/^\d+$/.test(match[1]);
}

// Reduce a wger image URL to a stable storage key (path within the bucket).
export function imageStorageKey(url) {
  const marker = "/exercise-images/";
  const idx = url.indexOf(marker);
  const tail = idx >= 0 ? url.slice(idx + marker.length) : url.replace(/^https?:\/\/[^/]+\//i, "");
  return tail.replace(/^\/+/, "");
}

/** Maps one wger export entry to its enrichment fields, keyed by name. */
export function wgerRecord(exercise) {
  const en = englishTranslation(exercise);
  if (!en) return null;
  const imageUrl = wgerMainImageUrl(exercise);
  return {
    key: nameKey(en.name),
    name: en.name.trim(),
    instructions: parseWgerSteps(en.description),
    primaryMuscles: names(exercise.muscles, muscleName),
    secondaryMuscles: names(exercise.muscles_secondary, muscleName),
    imageUrl,
    imageKey: imageUrl ? imageStorageKey(imageUrl) : null,
  };
}

/** Maps one free-exercise-db entry to its TEXT-ONLY enrichment fields. */
export function freeExerciseDbRecord(exercise) {
  if (!exercise || typeof exercise.name !== "string" || !exercise.name.trim()) return null;
  const instructions = (Array.isArray(exercise.instructions) ? exercise.instructions : [])
    .map((s) => String(s).trim())
    .filter((s) => s.length > 0);
  const primaryMuscles = (Array.isArray(exercise.primaryMuscles) ? exercise.primaryMuscles : [])
    .map((s) => String(s).trim())
    .filter(Boolean);
  const secondaryMuscles = (
    Array.isArray(exercise.secondaryMuscles) ? exercise.secondaryMuscles : []
  )
    .map((s) => String(s).trim())
    .filter(Boolean);
  return { key: nameKey(exercise.name), instructions, primaryMuscles, secondaryMuscles };
}

/**
 * Merges the wger and free-exercise-db records for the SAME catalog name into a
 * single enrichment patch, applying wger-first precedence: free-exercise-db text
 * fills ONLY an empty wger instruction list and never overwrites wger text;
 * muscles are backfilled from free-exercise-db only where wger has none.
 */
export function mergeRecords(wger, fed) {
  const w = wger || {};
  const f = fed || {};
  const instructions = (w.instructions && w.instructions.length ? w.instructions : f.instructions) || [];
  const primaryMuscles =
    (w.primaryMuscles && w.primaryMuscles.length ? w.primaryMuscles : f.primaryMuscles) || [];
  const secondaryMuscles =
    (w.secondaryMuscles && w.secondaryMuscles.length ? w.secondaryMuscles : f.secondaryMuscles) ||
    [];
  return {
    instructions,
    primaryMuscles,
    secondaryMuscles,
    imageKey: w.imageKey || null,
    instructionsSource: w.instructions && w.instructions.length ? "wger" : f.instructions && f.instructions.length ? "free-exercise-db" : "none",
  };
}

function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function sqlTextArray(values) {
  if (!values || values.length === 0) return "array[]::text[]";
  return `array[${values.map(sqlString).join(", ")}]::text[]`;
}

function sqlNullableString(value) {
  return value === null || value === undefined ? "null" : sqlString(value);
}

/**
 * Builds an idempotent, additive enrichment SQL statement. Updates only existing
 * catalog rows matched by lower(name); never inserts or deletes. coalesce keeps
 * any pre-existing non-empty value, so a second run is a no-op diff. The image
 * guard MUST have run before this — patches carry only wger image keys.
 */
export function buildEnrichSql(patches) {
  for (const p of patches) {
    if (p.imageKey && isFreeExerciseDbImage(p.imageKey)) {
      fail(`refusing to write a free-exercise-db image path for '${p.name}': ${p.imageKey}`);
    }
  }
  const header = [
    "-- public.exercises enrichment — GENERATED by supabase/seed/enrich-exercises.mjs.",
    "-- Do not edit by hand; re-run 'node supabase/seed/enrich-exercises.mjs' to regenerate.",
    "--",
    "-- Idempotent + additive: matches existing rows by lower(name), never inserts",
    "-- or deletes, and uses coalesce/nullif so re-running produces no diff. wger",
    "-- (CC-BY-SA) is the primary source; free-exercise-db (public domain) fills only",
    "-- empty instruction lists. NO free-exercise-db images. See supabase/ATTRIBUTION.md.",
    "",
    "update public.exercises e set",
    "  instructions = coalesce(nullif(e.instructions, array[]::text[]), v.instructions),",
    "  primary_muscles = coalesce(nullif(e.primary_muscles, array[]::text[]), v.primary_muscles),",
    "  secondary_muscles = coalesce(nullif(e.secondary_muscles, array[]::text[]), v.secondary_muscles),",
    "  image_path = coalesce(e.image_path, v.image_path)",
    "from (values",
  ];
  const lines = patches.map((p, i) => {
    const tuple =
      `  (${sqlString(p.key)}, ${sqlTextArray(p.instructions)}, ` +
      `${sqlTextArray(p.primaryMuscles)}, ${sqlTextArray(p.secondaryMuscles)}, ` +
      `${sqlNullableString(p.imageKey)})`;
    return tuple + (i === patches.length - 1 ? "" : ",");
  });
  const footer = [
    ") as v(name_key, instructions, primary_muscles, secondary_muscles, image_path)",
    "where lower(e.name) = v.name_key;",
    "",
  ];
  return [...header, ...lines, ...footer].join("\n");
}

/**
 * Builds the keyed patch list from the two exports. Each patch carries the
 * match key (lower name), the merged enrichment, and the source image URL/key so
 * the routine can upload it. Pure — no upload happens here.
 */
export function buildPatches(wgerExport, fedExport) {
  if (!Array.isArray(wgerExport) || wgerExport.length === 0) {
    fail("wger export must be a non-empty array");
  }
  if (!Array.isArray(fedExport)) {
    fail("free-exercise-db export must be an array");
  }
  const wgerByKey = new Map();
  for (const ex of wgerExport) {
    const rec = wgerRecord(ex);
    if (rec && !wgerByKey.has(rec.key)) wgerByKey.set(rec.key, rec);
  }
  const fedByKey = new Map();
  for (const ex of fedExport) {
    const rec = freeExerciseDbRecord(ex);
    if (rec && !fedByKey.has(rec.key)) fedByKey.set(rec.key, rec);
  }
  const keys = new Set([...wgerByKey.keys(), ...fedByKey.keys()]);
  const patches = [];
  for (const key of keys) {
    const wger = wgerByKey.get(key) || null;
    const fed = fedByKey.get(key) || null;
    const merged = mergeRecords(wger, fed);
    patches.push({
      key,
      name: (wger && wger.name) || key,
      ...merged,
      imageUrl: (wger && wger.imageUrl) || null,
    });
  }
  patches.sort((a, b) => (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
  return patches;
}
