/**
 * Integration tests for the rebuilt `search_exercises` tool (issue #190) against
 * the local Supabase stack. Each test drives a real `McpServer` over an in-memory
 * transport, so input is Zod-validated at the boundary and the query runs through
 * the user-scoped client and the `public.search_exercises` RPC exactly as it would
 * over Streamable HTTP. Read-only over the seeded public catalog (no fixtures).
 *
 * Covers the spec's acceptance: token-AND multi-word matching, relevance ranking
 * with canonical-first tiebreak, the German alias bridge, filter-only browse, and
 * the equipment/muscle filters. The muscle filter is asserted against the
 * `muscles` column (its `coalesce(nullif(primary_muscles,'{}'), muscles)` fallback
 * target), because a plain `db reset --local` leaves `primary_muscles` empty.
 * See docs/specs/spec-catalog-search.md.
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { SupabaseClient } from '@supabase/supabase-js';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { resolveAuthContext } from '../auth.js';
import type { Config } from '../config.js';
import { buildServer } from '../server.js';
import {
  adminClient,
  createConfirmedUser,
  deleteUser,
  newPlaintextKey,
  seedApiKey,
  testConfig,
} from '../testSupport.js';

const config: Config = testConfig();
const admin: SupabaseClient = adminClient(config);

/** A catalog row as the rebuilt tool projects it. */
interface CatalogRow {
  id: string;
  name: string;
  category: string | null;
  muscles: string[] | null;
  equipment: string[] | null;
  aliases: string[] | null;
  is_canonical: boolean;
  score?: unknown;
  tiebreak?: unknown;
}

let userId: string;
let key: string;
let client: Client;

beforeAll(async () => {
  userId = await createConfirmedUser(admin);
  key = newPlaintextKey();
  await seedApiKey(admin, config, userId, key);
  const auth = await resolveAuthContext(`Bearer ${key}`, config, admin);
  if (auth === null) {
    throw new Error('expected a resolved auth context for the test key');
  }
  const server = buildServer(auth);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  client = new Client({ name: 'test-client', version: '0.0.0' });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
});

afterAll(async () => {
  await deleteUser(admin, userId);
});

/** Calls `search_exercises` and returns its parsed rows. */
async function search(args: Record<string, unknown>): Promise<CatalogRow[]> {
  const result = (await client.callTool({
    name: 'search_exercises',
    arguments: args,
  })) as CallToolResult;
  const block = result.content[0];
  if (block === undefined || block.type !== 'text') {
    throw new Error('expected a text content block');
  }
  return JSON.parse(block.text) as CatalogRow[];
}

describe('search_exercises (rebuilt: token-AND trigram, filters, ranking, browse)', () => {
  it('matches a multi-word query by token-AND (shoulder press dumbbell)', async () => {
    const rows = await search({ q: 'shoulder press dumbbell' });
    expect(rows.length).toBeGreaterThan(0);
    expect(
      rows.some(
        (r) => /dumbbell/i.test(r.name) && /shoulder/i.test(r.name) && /press/i.test(r.name),
      ),
    ).toBe(true);
  });

  it('relevance-ranks press to surface both Leg Press and Shoulder Press', async () => {
    const rows = await search({ q: 'press' });
    const names = rows.map((r) => r.name);
    expect(names).toContain('Shoulder Press');
    expect(names).toContain('Leg Press');
  });

  it('ranks the canonical Lateral Raise first for "lateral raise"', async () => {
    const rows = await search({ q: 'lateral raise' });
    expect(rows[0]?.name).toBe('Lateral Raise');
    expect(rows[0]?.is_canonical).toBe(true);
  });

  it('resolves the German alias "Seitheben" to the canonical Lateral Raise', async () => {
    const rows = await search({ q: 'Seitheben' });
    expect(rows[0]?.name).toBe('Lateral Raise');
    expect(rows.some((r) => r.name === 'Lateral Raise')).toBe(true);
  });

  it('browses by filters alone when q is omitted (canonical-first)', async () => {
    const rows = await search({ equipment: 'Dumbbell', muscle: 'Shoulders' });
    expect(rows.length).toBeGreaterThan(0);
    // Filter-only browse orders is_canonical desc then name: a canonical row leads.
    expect(rows[0]?.is_canonical).toBe(true);
    expect(rows.map((r) => r.name)).toContain('Lateral Raise');
  });

  it('narrows correctly with equipment + muscle filters', async () => {
    const rows = await search({ equipment: 'Dumbbell', muscle: 'Shoulders' });
    expect(rows.length).toBeGreaterThan(0);
    for (const row of rows) {
      expect(row.equipment ?? []).toContain('Dumbbell');
      expect(row.muscles ?? []).toContain('Shoulders');
    }
  });

  it('projects aliases and is_canonical, and hides the ranking columns', async () => {
    const rows = await search({ q: 'lateral raise', limit: 1 });
    const top = rows[0];
    expect(top).toBeDefined();
    expect(top).toHaveProperty('aliases');
    expect(top).toHaveProperty('is_canonical');
    expect(top).not.toHaveProperty('score');
    expect(top).not.toHaveProperty('tiebreak');
    expect(top?.aliases ?? []).toContain('Seitheben');
  });

  it('honors the limit', async () => {
    const rows = await search({ q: 'press', limit: 3 });
    expect(rows.length).toBeLessThanOrEqual(3);
  });
});
