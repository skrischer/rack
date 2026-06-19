/**
 * Integration check for the Phase 14 catalog-search migration (issue #188)
 * against the local Supabase stack. It asserts the migration's schema contract
 * is present after `supabase db reset --local`:
 *  - the pg_trgm and unaccent extensions are installed,
 *  - `aliases` (text[]) and `is_canonical` (boolean NOT NULL DEFAULT false) exist
 *    on public.exercises, with no Phase 1/7 column dropped,
 *  - the GIN trigram index over name + aliases exists,
 *  - the public SELECT policy (anon + authenticated) is in place,
 *  - and, end-to-end, an alias participates in trigram matching via the
 *    `exercises_search_text(name, aliases)` expression the index backs.
 * The functional fixture uses CATALOG_FIXTURE_PREFIX + a random suffix so
 * parallel/repeat runs never collide, and is removed in teardown. Requires
 * `supabase start` (docker container supabase_db_rack), the same local stack the
 * other integration tests use. See docs/specs/spec-catalog-search.md.
 */

import { spawnSync } from 'node:child_process';

import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { CATALOG_FIXTURE_PREFIX } from './testSupport.js';

const PSQL = (
  process.env.RACK_PSQL ?? 'docker exec -i supabase_db_rack psql -U postgres -d postgres'
)
  .split(' ')
  .filter(Boolean);

function runSql(sql: string): string {
  const [cmd, ...args] = PSQL;
  const res = spawnSync(cmd!, [...args, '-v', 'ON_ERROR_STOP=1', '-At', '-q'], {
    input: sql,
    encoding: 'utf8',
  });
  if (res.status !== 0) {
    throw new Error(`psql exited ${res.status}: ${res.stderr || res.stdout}`);
  }
  return res.stdout.trim();
}

function sqlString(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}

const suffix = Math.random().toString(16).slice(2, 10);
const exerciseName = `${CATALOG_FIXTURE_PREFIX} Lateral Raise ${suffix}`;
const alias = `Seitheben${suffix}`;

beforeAll(() => {
  // A canonical fixture row carrying an alias, inserted as the DB owner (the
  // seed/curation privilege), to prove the new columns and trigram path work.
  runSql(
    `insert into public.exercises (name, category, muscles, equipment, aliases, is_canonical)
     values (${sqlString(exerciseName)}, 'Shoulders', array['Shoulders']::text[],
             array['Dumbbell']::text[], array[${sqlString(alias)}]::text[], true);`,
  );
});

afterAll(() => {
  runSql(`delete from public.exercises where name = ${sqlString(exerciseName)};`);
});

describe('catalog-search migration schema contract (local DB)', () => {
  it('installs the pg_trgm and unaccent extensions', () => {
    const present = runSql(
      `select string_agg(extname, ',' order by extname) from pg_extension
       where extname in ('pg_trgm', 'unaccent');`,
    );
    expect(present).toBe('pg_trgm,unaccent');
  });

  it('adds aliases text[] additively (no Phase 1/7 column dropped)', () => {
    const aliasesType = runSql(
      `select data_type || '/' || udt_name from information_schema.columns
       where table_schema = 'public' and table_name = 'exercises' and column_name = 'aliases';`,
    );
    expect(aliasesType).toBe('ARRAY/_text');

    // Phase 1 / Phase 7 columns must all survive (additive migration).
    const retained = runSql(
      `select count(*) from information_schema.columns
       where table_schema = 'public' and table_name = 'exercises'
         and column_name in ('name','category','muscles','equipment','license',
                             'license_author','instructions','primary_muscles',
                             'secondary_muscles','image_path');`,
    );
    expect(Number(retained)).toBe(10);
  });

  it('adds is_canonical boolean NOT NULL DEFAULT false', () => {
    const meta = runSql(
      `select data_type || '/' || is_nullable || '/' || coalesce(column_default, 'null')
       from information_schema.columns
       where table_schema = 'public' and table_name = 'exercises'
         and column_name = 'is_canonical';`,
    );
    expect(meta).toBe('boolean/NO/false');
  });

  it('creates the GIN trigram index over name + aliases', () => {
    const indexdef = runSql(
      `select indexdef from pg_indexes
       where schemaname = 'public' and tablename = 'exercises'
         and indexname = 'exercises_search_trgm';`,
    );
    expect(indexdef).toContain('USING gin');
    expect(indexdef).toContain('gin_trgm_ops');
    expect(indexdef).toContain('exercises_search_text');
  });

  it('keeps the public SELECT policy for anon + authenticated', () => {
    const policy = runSql(
      `select cmd || '/' || array_to_string(array(select unnest(roles) order by 1), ',')
       from pg_policies
       where schemaname = 'public' and tablename = 'exercises'
         and policyname = 'exercises_public_read';`,
    );
    expect(policy).toBe('SELECT/anon,authenticated');
  });

  it('matches an alias through the trigram search expression', () => {
    // Word-similarity (`<%`) finds the token within the name+aliases text, which
    // is how the rebuilt search (#190) matches a query token against an alias;
    // `gin_trgm_ops` backs this operator. The token equals the (uniquely
    // suffixed) alias word, so word_similarity is 1.0 and the match is
    // deterministic and unique to this fixture.
    const matched = runSql(
      `select name from public.exercises
       where ${sqlString(alias)} <% exercises_search_text(name, aliases)
       order by word_similarity(${sqlString(alias)}, exercises_search_text(name, aliases)) desc
       limit 1;`,
    );
    expect(matched).toBe(exerciseName);
  });
});
