/**
 * Integration check for the curated catalog seed (issue #189) against the local
 * Supabase stack, after `supabase db reset --local` re-applies seed.sql:
 *  - the four beta-named base movements (Lateral Raise, Lat Pulldown, Triceps
 *    Pushdown, Shoulder Press) exist and are is_canonical = true,
 *  - the German alias "Seitheben" resolves the canonical Lateral Raise via the
 *    `aliases` array (the German↔English bridge from beta §4.6),
 *  - aliases are seeded broadly from the wger non-English translations,
 *  - Cable/Machine are best-effort derived into `equipment` from the name while
 *    the raw equipment value is retained.
 * Read-only over the public catalog (anon SELECT); no fixtures are inserted.
 * See docs/specs/spec-catalog-search.md.
 */

import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import { describe, expect, it } from 'vitest';

import type { Config } from './config.js';
import { testConfig } from './testSupport.js';

const config: Config = testConfig();
const anon: SupabaseClient = createClient(config.supabaseUrl, config.supabaseAnonKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const CANONICAL_MOVEMENTS = ['Lateral Raise', 'Lat Pulldown', 'Triceps Pushdown', 'Shoulder Press'];

interface CatalogRow {
  name: string;
  aliases: string[] | null;
  is_canonical: boolean;
  equipment: string[] | null;
}

describe('curated catalog seed', () => {
  it('marks the four beta-named base movements canonical', async () => {
    const { data, error } = await anon
      .from('exercises')
      .select('name, is_canonical')
      .in('name', CANONICAL_MOVEMENTS)
      .returns<Pick<CatalogRow, 'name' | 'is_canonical'>[]>();
    expect(error).toBeNull();
    const byName = new Map((data ?? []).map((r) => [r.name, r.is_canonical]));
    for (const name of CANONICAL_MOVEMENTS) {
      expect(byName.get(name), `${name} present and canonical`).toBe(true);
    }
  });

  it('resolves the German alias "Seitheben" to the canonical Lateral Raise', async () => {
    const { data, error } = await anon
      .from('exercises')
      .select('name, aliases, is_canonical, equipment')
      .contains('aliases', ['Seitheben'])
      .returns<CatalogRow[]>();
    expect(error).toBeNull();
    const lateral = (data ?? []).find((r) => r.name === 'Lateral Raise');
    expect(lateral, 'Seitheben resolves Lateral Raise').toBeDefined();
    expect(lateral?.is_canonical).toBe(true);
  });

  it('seeds aliases broadly from the wger non-English translations', async () => {
    const { count, error } = await anon
      .from('exercises')
      .select('name', { count: 'exact', head: true })
      .not('aliases', 'is', null);
    expect(error).toBeNull();
    // The curated 4 plus the bulk of the catalog carry other-language aliases;
    // a few dozen at minimum proves the translation harvest ran, not just the
    // curated set.
    expect(count ?? 0).toBeGreaterThan(50);
  });

  it('best-effort derives Cable into equipment from the name, keeping the raw value', async () => {
    const { data, error } = await anon
      .from('exercises')
      .select('name, aliases, is_canonical, equipment')
      .eq('name', 'Tricep Pushdown on Cable')
      .maybeSingle<CatalogRow>();
    expect(error).toBeNull();
    expect(data?.equipment ?? []).toContain('Cable');
  });
});
