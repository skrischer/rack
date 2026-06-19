/**
 * Unit tests for the pure seed-import transforms (issue #189). These cover the
 * catalog-curation rules WITHOUT touching the DB or network:
 *  - the English (language=2) translation is the catalog name; every other
 *    translation name becomes a deduped search alias,
 *  - Cable/Machine are best-effort derived from the name and the raw equipment is
 *    retained,
 *  - the curated canonical layer marks the four beta-named movements canonical
 *    (promoting an existing row or inserting a new base row) with curated aliases,
 *  - non-English-only entries are skipped (not a hard failure),
 *  - the generated SQL is additive/idempotent (name-key `where not exists`) and
 *    byte-identical across two builds of the same input.
 * See docs/specs/spec-catalog-search.md.
 */

import { describe, it, expect } from 'vitest';

import {
  CURATED_CANONICAL,
  englishName,
  extractAliases,
  deriveEquipment,
  toRow,
  applyCuration,
  buildRows,
  buildSql,
  nameKey,
  type WgerExercise,
  type SeedRow,
} from '../../supabase/seed/import/transform.mjs';

const lateralRaiseWger: WgerExercise = {
  id: 100,
  category: { name: 'Shoulders' },
  muscles: [{ name: 'Anterior deltoid', name_en: 'Shoulders' }],
  equipment: [{ name: 'Dumbbell' }],
  license: { short_name: 'CC-BY-SA 4' },
  license_author: 'someone',
  translations: [
    { language: 2, name: 'Side Lateral Raise (Cable)' },
    { language: 1, name: 'Seitheben am Kabel' },
    { language: 13, name: 'Alzate laterali ai cavi' },
    { language: 2, name: 'Side Lateral Raise (Cable)' },
  ],
};

describe('englishName', () => {
  it('returns the language=2 translation, trimmed', () => {
    expect(englishName(lateralRaiseWger)).toBe('Side Lateral Raise (Cable)');
  });

  it('returns null without an English translation', () => {
    expect(englishName({ translations: [{ language: 1, name: 'Bankdrücken' }] })).toBeNull();
  });
});

describe('extractAliases', () => {
  it('collects every non-English translation name, deduped, minus the English name', () => {
    const aliases = extractAliases(lateralRaiseWger, 'Side Lateral Raise (Cable)');
    expect(aliases).toEqual(['Seitheben am Kabel', 'Alzate laterali ai cavi']);
  });

  it('drops a non-English name identical to the English catalog name', () => {
    const aliases = extractAliases(
      { translations: [{ language: 2, name: 'Plank' }, { language: 1, name: 'plank' }] },
      'Plank',
    );
    expect(aliases).toEqual([]);
  });
});

describe('deriveEquipment', () => {
  it('derives Cable from the name and retains the raw equipment', () => {
    expect(deriveEquipment('Tricep Pushdown on Cable', [])).toEqual(['Cable']);
    expect(deriveEquipment('Cable Fly', ['Dumbbell'])).toEqual(['Dumbbell', 'Cable']);
  });

  it('derives Machine and never duplicates an existing value', () => {
    expect(deriveEquipment('Machine Chest Press', ['Machine'])).toEqual(['Machine']);
    expect(deriveEquipment('Seated Cable Row', ['Cable'])).toEqual(['Cable']);
  });

  it('leaves equipment untouched when the name mentions neither', () => {
    expect(deriveEquipment('Barbell Squat', ['Barbell'])).toEqual(['Barbell']);
  });
});

describe('toRow', () => {
  it('maps name, derived equipment, and aliases; defaults is_canonical false', () => {
    const row = toRow(lateralRaiseWger);
    expect(row).not.toBeNull();
    expect(row?.name).toBe('Side Lateral Raise (Cable)');
    expect(row?.category).toBe('Shoulders');
    expect(row?.muscles).toEqual(['Shoulders']);
    expect(row?.equipment).toEqual(['Dumbbell', 'Cable']); // derived from "(Cable)"
    expect(row?.aliases).toEqual(['Seitheben am Kabel', 'Alzate laterali ai cavi']);
    expect(row?.isCanonical).toBe(false);
  });

  it('returns null (skip) for a non-English-only entry', () => {
    expect(toRow({ id: 7, translations: [{ language: 13, name: 'Solo italiano' }] })).toBeNull();
  });
});

describe('CURATED_CANONICAL', () => {
  it('covers exactly the four beta-named movements', () => {
    expect(CURATED_CANONICAL.map((c) => c.name).sort()).toEqual([
      'Lat Pulldown',
      'Lateral Raise',
      'Shoulder Press',
      'Triceps Pushdown',
    ]);
  });

  it('carries the German "Seitheben" alias for Lateral Raise', () => {
    const lateral = CURATED_CANONICAL.find((c) => c.name === 'Lateral Raise');
    expect(lateral?.aliases).toContain('Seitheben');
  });
});

describe('applyCuration', () => {
  it('promotes an existing row to canonical and unions curated aliases + equipment', () => {
    const existing: SeedRow = {
      name: 'Triceps Pushdown',
      category: 'Arms',
      muscles: ['Triceps'],
      equipment: [],
      license: 'CC-BY-SA 4',
      licenseAuthor: 'x',
      aliases: ['Pressa per tricipiti'],
      isCanonical: false,
    };
    const byKey = new Map<string, SeedRow>([[nameKey(existing.name), existing]]);
    applyCuration(byKey);
    const row = byKey.get('triceps pushdown');
    expect(row?.isCanonical).toBe(true);
    expect(row?.equipment).toContain('Cable'); // curated equipment unioned in
    expect(row?.aliases).toContain('Pressa per tricipiti'); // raw alias kept
    expect(row?.aliases).toContain('Tricep Pushdown'); // curated alias added
  });

  it('inserts a missing movement as a new canonical base row', () => {
    const byKey = new Map<string, SeedRow>();
    applyCuration(byKey);
    const lateral = byKey.get('lateral raise');
    expect(lateral?.isCanonical).toBe(true);
    expect(lateral?.aliases).toContain('Seitheben');
    expect(lateral?.equipment).toEqual(['Dumbbell']);
  });
});

describe('buildRows', () => {
  const exercises: WgerExercise[] = [
    lateralRaiseWger,
    { id: 200, translations: [{ language: 13, name: 'Solo italiano' }] }, // skipped
    {
      id: 300,
      category: { name: 'Arms' },
      muscles: [{ name: 'Triceps' }],
      equipment: [],
      translations: [{ language: 2, name: 'Triceps Pushdown' }],
    },
  ];

  it('skips non-English entries, applies curation, and reports stats', () => {
    const { rows, stats } = buildRows(exercises);
    expect(stats.skipped).toBe(1);
    expect(stats.canonical).toBe(4); // Triceps Pushdown promoted + 3 inserted
    const names = rows.map((r) => r.name);
    expect(names).toContain('Lateral Raise');
    expect(names).toContain('Lat Pulldown');
    expect(names).toContain('Shoulder Press');
    expect(names).toContain('Triceps Pushdown');
    const triceps = rows.find((r) => r.name === 'Triceps Pushdown');
    expect(triceps?.isCanonical).toBe(true);
  });

  it('throws on an empty export', () => {
    expect(() => buildRows([])).toThrow(/non-empty array/);
  });
});

describe('buildSql', () => {
  const sql = buildSql(buildRows([lateralRaiseWger]).rows);

  it('inserts the new aliases + is_canonical columns idempotently by name', () => {
    expect(sql).toContain(
      'insert into public.exercises (name, category, muscles, equipment, license, license_author, aliases, is_canonical)',
    );
    expect(sql).toContain('where not exists (select 1 from public.exercises e where e.name = v.name);');
    expect(sql).not.toContain('delete from');
  });

  it('marks the four canonical movements is_canonical = true', () => {
    for (const name of ['Lateral Raise', 'Lat Pulldown', 'Triceps Pushdown', 'Shoulder Press']) {
      expect(sql).toMatch(new RegExp(`\\('${name}',[\\s\\S]*?, true\\)`));
    }
  });

  it('is deterministic — two builds of the same input are byte-identical', () => {
    const a = buildSql(buildRows([lateralRaiseWger]).rows);
    const b = buildSql(buildRows([lateralRaiseWger]).rows);
    expect(a).toBe(b);
  });
});
