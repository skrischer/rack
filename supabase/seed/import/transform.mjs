// Pure transforms for the wger catalog seed importer (import-wger.mjs).
//
// No I/O, no network, no DB: these functions turn the raw wger export into the
// idempotent seed rows and SQL, so the importer is deterministic and
// unit-testable (mirrors supabase/seed/enrich/transform.mjs). See
// docs/specs/spec-catalog-search.md.
//
// The English (language=2) translation name is the catalog name; every OTHER
// translation name becomes a search alias. A small curated canonical layer marks
// the common base movements (Lateral Raise, Lat Pulldown, Triceps Pushdown,
// Shoulder Press) is_canonical and gives them curated cross-language aliases, so
// they resolve and rank first; the long tail stays searchable but uncurated.
// Cable/Machine are best-effort derived from the name (raw equipment retained).

export const LANGUAGE_EN = 2;
export const DEFAULT_LICENSE = "CC-BY-SA 4.0";
export const DEFAULT_AUTHOR = "wger.de";

/** Throws with a clear, fail-loud message — there is no silent partial seed. */
export function fail(message) {
  throw new Error(`import-wger: ${message}`);
}

/** Normalizes a catalog name to its idempotent match key (the seed key). */
export function nameKey(name) {
  return String(name).trim().toLowerCase();
}

// Curated canonical base movements. Each is guaranteed is_canonical = true and
// carries cross-language / variant aliases beyond what the wger translations
// supply, so the beta-named movements resolve and rank first (spec acceptance).
// When a catalog row already exists for the name, curation marks it canonical and
// UNIONS the curated aliases + equipment in (additive; the row's real
// category/muscles are left untouched). When no row exists, the movement is
// inserted as a new canonical base row from the curated fields.
export const CURATED_CANONICAL = [
  {
    name: "Lateral Raise",
    category: "Shoulders",
    muscles: ["Shoulders"],
    equipment: ["Dumbbell"],
    aliases: [
      "Seitheben",
      "Side Lateral Raise",
      "Side Raise",
      "Élévations latérales",
      "Elevazioni laterali",
      "Elevaciones laterales",
    ],
  },
  {
    name: "Lat Pulldown",
    category: "Back",
    muscles: ["Lats"],
    equipment: ["Cable", "Machine"],
    aliases: [
      "Latzug",
      "Lat Pull-Down",
      "Pulldown",
      "Tirage vertical",
      "Lat machine verticale",
      "Jalón al pecho",
    ],
  },
  {
    name: "Triceps Pushdown",
    category: "Arms",
    muscles: ["Triceps"],
    equipment: ["Cable"],
    aliases: [
      "Trizepsdrücken am Kabel",
      "Tricep Pushdown",
      "Pushdown",
      "Cable Pushdown",
      "Pressa ai cavi per tricipiti",
      "Extensión de tríceps en polea",
    ],
  },
  {
    name: "Shoulder Press",
    category: "Shoulders",
    muscles: ["Shoulders"],
    equipment: ["Dumbbell"],
    aliases: [
      "Schulterdrücken",
      "Overhead Press",
      "Military Press",
      "Press de hombros",
      "Développé épaules",
    ],
  },
];

// A name that mentions "cable" or "machine" gains that equipment value
// (best-effort). Word-boundary anchored so "machine" matches but a longer word
// would not over-trigger.
const EQUIPMENT_FROM_NAME = [
  { token: /\bcable\b/i, value: "Cable" },
  { token: /\bmachine\b/i, value: "Machine" },
];

/** De-duplicated, trimmed, non-empty list preserving first-seen order. */
function dedupePreserve(values) {
  const out = [];
  const seen = new Set();
  for (const value of values || []) {
    const text = (value === null || value === undefined ? "" : String(value)).trim();
    if (!text) continue;
    const key = text.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(text);
  }
  return out;
}

function assertArray(value, label, id) {
  if (value !== undefined && value !== null && !Array.isArray(value)) {
    fail(`exercise ${id}: expected '${label}' to be an array, got ${typeof value}`);
  }
}

// Friendly English muscle name, trimmed; "" when unusable.
function muscleName(m) {
  const n = (m && (m.name_en || m.name)) || "";
  return n.trim();
}

/** The English (language=2) translation name for an exercise, or null. */
export function englishName(exercise) {
  assertArray(exercise.translations, "translations", exercise.id);
  const en = (exercise.translations || []).find(
    (t) => t && t.language === LANGUAGE_EN && typeof t.name === "string" && t.name.trim(),
  );
  return en ? en.name.trim() : null;
}

/**
 * Every non-English translation name (deduped case-insensitively and minus the
 * English catalog name) becomes a search alias for the row.
 */
export function extractAliases(exercise, english) {
  assertArray(exercise.translations, "translations", exercise.id);
  const enKey = nameKey(english);
  const foreign = (exercise.translations || [])
    .filter((t) => t && t.language !== LANGUAGE_EN && typeof t.name === "string")
    .map((t) => t.name);
  return dedupePreserve(foreign).filter((n) => nameKey(n) !== enKey);
}

/**
 * Best-effort equipment derivation: a name mentioning "cable"/"machine" gains
 * that equipment value. The raw equipment list is RETAINED (deduped); derived
 * values are only appended when absent.
 */
export function deriveEquipment(name, equipment) {
  const out = dedupePreserve(equipment);
  const present = new Set(out.map((e) => e.toLowerCase()));
  for (const { token, value } of EQUIPMENT_FROM_NAME) {
    if (token.test(String(name)) && !present.has(value.toLowerCase())) {
      out.push(value);
      present.add(value.toLowerCase());
    }
  }
  return out;
}

/**
 * Maps one wger export entry to a seed row. Returns null when the entry has no
 * English (language=2) translation — EXPECTED for non-English-only exercises in
 * the full-language export, so they are skipped, not a hard failure. Still fails
 * loud on a genuinely malformed shape (non-array translations/muscles/equipment).
 */
export function toRow(exercise) {
  const name = englishName(exercise);
  if (!name) return null;
  if (
    exercise.category !== null &&
    exercise.category !== undefined &&
    typeof exercise.category !== "object"
  ) {
    fail(`exercise ${exercise.id}: expected 'category' to be an object or null`);
  }
  assertArray(exercise.muscles, "muscles", exercise.id);
  assertArray(exercise.equipment, "equipment", exercise.id);

  const category =
    exercise.category && exercise.category.name ? exercise.category.name.trim() : null;
  const muscles = dedupePreserve((exercise.muscles || []).map(muscleName));
  const rawEquipment = dedupePreserve(
    (exercise.equipment || []).map((e) => (e && e.name ? e.name.trim() : "")),
  );
  const equipment = deriveEquipment(name, rawEquipment);
  const aliases = extractAliases(exercise, name);
  const license =
    (exercise.license && exercise.license.short_name && exercise.license.short_name.trim()) ||
    DEFAULT_LICENSE;
  const licenseAuthor =
    (typeof exercise.license_author === "string" && exercise.license_author.trim()) ||
    DEFAULT_AUTHOR;

  return { name, category, muscles, equipment, license, licenseAuthor, aliases, isCanonical: false };
}

/**
 * Applies the curated canonical layer to the de-duplicated row map (keyed by
 * nameKey). Existing rows are marked canonical with curated aliases + equipment
 * unioned in (additive; raw category/muscles kept). Missing movements are
 * inserted as new canonical base rows. Mutates and returns `byKey`.
 */
export function applyCuration(byKey) {
  for (const entry of CURATED_CANONICAL) {
    const key = nameKey(entry.name);
    const existing = byKey.get(key);
    if (existing) {
      existing.isCanonical = true;
      existing.aliases = dedupePreserve([...existing.aliases, ...entry.aliases]);
      existing.equipment = dedupePreserve([...existing.equipment, ...entry.equipment]);
    } else {
      byKey.set(key, {
        name: entry.name,
        category: entry.category,
        muscles: dedupePreserve(entry.muscles),
        equipment: dedupePreserve(entry.equipment),
        license: DEFAULT_LICENSE,
        licenseAuthor: DEFAULT_AUTHOR,
        aliases: dedupePreserve(entry.aliases),
        isCanonical: true,
      });
    }
  }
  return byKey;
}

/**
 * Builds the de-duplicated, curated seed row list from the raw export. The first
 * English name wins per key (wger ids de-dupe the source); non-English-only
 * entries are skipped and counted. Curation runs last so it can promote or
 * insert the canonical base movements.
 */
export function buildRows(exercises) {
  if (!Array.isArray(exercises) || exercises.length === 0) {
    fail("export must be a non-empty array");
  }
  const byKey = new Map();
  let dupes = 0;
  let skipped = 0;
  for (const exercise of exercises) {
    if (!exercise || typeof exercise !== "object") {
      fail("export contains a non-object entry; shape unexpected");
    }
    const row = toRow(exercise);
    if (!row) {
      skipped += 1;
      continue;
    }
    const key = nameKey(row.name);
    if (byKey.has(key)) {
      dupes += 1;
      continue;
    }
    byKey.set(key, row);
  }
  applyCuration(byKey);
  const rows = [...byKey.values()];
  if (rows.length === 0) {
    fail("no exercises produced from the export; refusing to write an empty seed");
  }
  const canonical = rows.filter((r) => r.isCanonical).length;
  return { rows, stats: { source: exercises.length, dupes, skipped, canonical } };
}

function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function sqlNullableString(value) {
  return value === null || value === undefined ? "null" : sqlString(value);
}

function sqlTextArray(values) {
  if (!values || values.length === 0) return "array[]::text[]";
  return `array[${values.map(sqlString).join(", ")}]::text[]`;
}

// Empty aliases → a typed NULL (the migration's "unseeded row has no aliases").
// The explicit ::text[] pins the VALUES column type even when the first row is
// null, so Postgres never infers `unknown` for the column.
function sqlNullableTextArray(values) {
  return !values || values.length === 0 ? "null::text[]" : sqlTextArray(values);
}

/**
 * Builds the idempotent, additive seed INSERT. Inserts only names not already
 * present (`where not exists`), so re-running the seed — or loading it on a
 * populated DB — adds no duplicates and touches no FK rows.
 */
export function buildSql(rows) {
  const header = [
    "-- public.exercises catalog seed — GENERATED by supabase/seed/import-wger.mjs.",
    "-- Do not edit by hand; re-run 'node supabase/seed/import-wger.mjs' to regenerate.",
    "--",
    "-- Data source: wger Workout Manager (https://wger.de) exercise database,",
    "-- licensed CC-BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/).",
    "-- This seed is a derivative; share-alike applies. See supabase/ATTRIBUTION.md.",
    "--",
    `-- ${rows.length} exercises. name = wger English (language=${LANGUAGE_EN}) translation;`,
    "-- aliases = the other-language translation names plus a curated cross-language",
    "-- set for the canonical movements; is_canonical marks the curated base entries.",
    "-- Cable/Machine are best-effort derived from the name (raw equipment retained).",
    "",
    "-- Idempotent: inserts only names not already present, so re-running the seed",
    "-- (or loading it on a populated DB) adds no duplicates and touches no FK rows.",
    "insert into public.exercises (name, category, muscles, equipment, license, license_author, aliases, is_canonical)",
    "select v.name, v.category, v.muscles, v.equipment, v.license, v.license_author, v.aliases, v.is_canonical",
    "from (values",
  ];

  const valueLines = rows.map((r, i) => {
    const tuple =
      `  (${sqlString(r.name)}, ${sqlNullableString(r.category)}, ${sqlTextArray(r.muscles)}, ` +
      `${sqlTextArray(r.equipment)}, ${sqlString(r.license)}, ${sqlString(r.licenseAuthor)}, ` +
      `${sqlNullableTextArray(r.aliases)}, ${r.isCanonical ? "true" : "false"})`;
    return tuple + (i === rows.length - 1 ? "" : ",");
  });

  const footer = [
    ") as v(name, category, muscles, equipment, license, license_author, aliases, is_canonical)",
    "where not exists (select 1 from public.exercises e where e.name = v.name);",
    "",
  ];

  return [...header, ...valueLines, ...footer].join("\n");
}
