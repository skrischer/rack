/**
 * Unit tests for the pure enrichment transforms used by the idempotent
 * `/supabase` enrichment routine (issue #48). These cover the source-precedence
 * and image-guard rules WITHOUT touching the DB or network:
 *  - wger description HTML parses into ordered instruction steps,
 *  - free-exercise-db (public domain) text fills ONLY empty wger instructions
 *    and never overwrites wger text; muscles backfill only where wger is empty,
 *  - any free-exercise-db image path is rejected by the guard,
 *  - the generated SQL is additive/idempotent (coalesce, match by lower(name))
 *    and identical across two builds of the same input.
 * See docs/specs/spec-exercise-detail.md.
 */

import { describe, it, expect } from 'vitest';

import {
  parseWgerSteps,
  wgerRecord,
  freeExerciseDbRecord,
  mergeRecords,
  isFreeExerciseDbImage,
  buildPatches,
  buildEnrichSql,
  type WgerExercise,
  type FreeExerciseDbExercise,
} from '../../supabase/seed/enrich/transform.mjs';

describe('parseWgerSteps', () => {
  it('extracts ordered list items', () => {
    expect(parseWgerSteps('<ol><li>Stand up</li><li>Sit down</li></ol>')).toEqual([
      'Stand up',
      'Sit down',
    ]);
  });

  it('falls back to paragraphs when there is no list', () => {
    expect(parseWgerSteps('<p>First line</p><p>Second line</p>')).toEqual([
      'First line',
      'Second line',
    ]);
  });

  it('returns an empty list for empty or non-string input', () => {
    expect(parseWgerSteps('')).toEqual([]);
    expect(parseWgerSteps(null)).toEqual([]);
    expect(parseWgerSteps('<p>   </p>')).toEqual([]);
  });
});

describe('isFreeExerciseDbImage guard', () => {
  it('flags free-exercise-db relative image paths', () => {
    expect(isFreeExerciseDbImage('Bench_Press/0.jpg')).toBe(true);
    expect(isFreeExerciseDbImage('3_4_Sit-Up/1.jpg')).toBe(true);
  });

  it('does not flag wger media keys (numeric-id directory)', () => {
    expect(isFreeExerciseDbImage('1962/74041371-1019-4f89-9ebe.png')).toBe(false);
    expect(isFreeExerciseDbImage('192/Bench-press-1.png')).toBe(false);
  });

  it('does not flag absolute wger URLs', () => {
    expect(isFreeExerciseDbImage('https://wger.de/media/exercise-images/1962/x.png')).toBe(false);
  });
});

const wgerWithImage: WgerExercise = {
  translations: [
    {
      language: 2,
      name: 'Bench Press',
      description: '<ol><li>Lie down</li><li>Press up</li></ol>',
    },
  ],
  muscles: [{ name: 'Pectoralis major', name_en: 'Chest' }],
  muscles_secondary: [{ name: 'Triceps' }],
  images: [
    { image: 'https://wger.de/media/exercise-images/192/main.png', is_main: true },
    { image: 'https://wger.de/media/exercise-images/192/other.png', is_main: false },
  ],
};

describe('wgerRecord', () => {
  it('maps English translation, muscles, and the main image key', () => {
    const rec = wgerRecord(wgerWithImage);
    expect(rec).not.toBeNull();
    expect(rec?.key).toBe('bench press');
    expect(rec?.instructions).toEqual(['Lie down', 'Press up']);
    expect(rec?.primaryMuscles).toEqual(['Chest']);
    expect(rec?.secondaryMuscles).toEqual(['Triceps']);
    expect(rec?.imageKey).toBe('192/main.png');
  });

  it('returns null without an English translation', () => {
    expect(wgerRecord({ translations: [{ language: 1, name: 'Bankdrücken' }] })).toBeNull();
  });
});

describe('mergeRecords precedence (wger-first)', () => {
  const fed = freeExerciseDbRecord({
    name: 'Bench Press',
    instructions: ['fed step one', 'fed step two'],
    primaryMuscles: ['fed-primary'],
    secondaryMuscles: ['fed-secondary'],
  } satisfies FreeExerciseDbExercise);

  it('keeps wger instructions and never overwrites with free-exercise-db text', () => {
    const merged = mergeRecords(wgerRecord(wgerWithImage), fed);
    expect(merged.instructions).toEqual(['Lie down', 'Press up']);
    expect(merged.instructionsSource).toBe('wger');
    expect(merged.primaryMuscles).toEqual(['Chest']);
  });

  it('fills ONLY empty wger instructions from free-exercise-db', () => {
    const emptyWger = wgerRecord({
      translations: [{ language: 2, name: 'Bench Press', description: '' }],
      muscles: [],
      muscles_secondary: [],
    });
    const merged = mergeRecords(emptyWger, fed);
    expect(merged.instructions).toEqual(['fed step one', 'fed step two']);
    expect(merged.instructionsSource).toBe('free-exercise-db');
    expect(merged.primaryMuscles).toEqual(['fed-primary']);
  });

  it('flags "none" when neither source has instructions', () => {
    const merged = mergeRecords(
      wgerRecord({ translations: [{ language: 2, name: 'X', description: '' }] }),
      freeExerciseDbRecord({ name: 'X', instructions: [] }),
    );
    expect(merged.instructions).toEqual([]);
    expect(merged.instructionsSource).toBe('none');
  });
});

describe('buildPatches + buildEnrichSql', () => {
  const wgerExport: WgerExercise[] = [wgerWithImage];
  const fedExport: FreeExerciseDbExercise[] = [
    { name: 'Bench Press', instructions: ['ignored'], primaryMuscles: ['ignored'] },
    {
      name: 'Crunch',
      instructions: ['Lie on the floor', 'Curl up'],
      primaryMuscles: ['abdominals'],
    },
  ];

  it('produces additive, lower(name)-matched, coalescing SQL', () => {
    const sql = buildEnrichSql(buildPatches(wgerExport, fedExport));
    expect(sql).toContain('update public.exercises e set');
    expect(sql).toContain('coalesce(nullif(e.instructions, array[]::text[]), v.instructions)');
    expect(sql).toContain('where lower(e.name) = v.name_key;');
    expect(sql).not.toContain('insert into');
    expect(sql).not.toContain('delete from');
  });

  it('is deterministic — two builds of the same input are byte-identical (idempotent)', () => {
    const a = buildEnrichSql(buildPatches(wgerExport, fedExport));
    const b = buildEnrichSql(buildPatches(wgerExport, fedExport));
    expect(a).toBe(b);
  });

  it('refuses to emit SQL carrying a free-exercise-db image path', () => {
    const patches = buildPatches(wgerExport, fedExport);
    patches[0]!.imageKey = 'Bench_Press/0.jpg';
    expect(() => buildEnrichSql(patches)).toThrow(/free-exercise-db image path/);
  });
});
