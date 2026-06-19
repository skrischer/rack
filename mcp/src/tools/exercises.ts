/**
 * Tools over the exercise catalog: a relevance-ranked read (`search_exercises`)
 * and a user-scoped write (`create_exercise`) for custom / user-authored
 * exercises. The catalog (`exercises`) is the one shared, non-user-owned table,
 * but Phase 17 lets it also hold per-user custom rows: a custom exercise is an
 * `exercises` row whose `user_id` is set, owner-scoped by RLS, while shared
 * catalog rows keep `user_id IS NULL`. Both tools run through the user-scoped
 * client, so RLS returns the public catalog plus the caller's own customs and
 * never another user's rows. See docs/specs/spec-custom-exercises.md.
 *
 * Search is relevance-ranked, multi-word and alias-aware: each whitespace token
 * of `q` is matched against name + aliases via pg_trgm word-similarity
 * (token-AND — every token must match), rows rank by the summed per-token score
 * with `is_canonical` as the tiebreak, and the heavy lifting lives in the
 * `public.search_exercises` SQL function (see the search RPC migration). With `q`
 * omitted the tool is a filter-only browse. See docs/specs/spec-catalog-search.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { errorResult, jsonResult } from './result.js';

/**
 * Columns returned for an exercise row, including the `aliases` / `is_canonical`
 * curation fields, the CC-BY-SA attribution, and `user_id` (null for a shared
 * catalog row, the owner for a custom exercise — so a client can mark customs).
 * This MUST stay in sync with the `search_exercises` RPC's RETURNS TABLE: every
 * name listed here must be a column the function projects (see the search
 * projection migration). The ranking `score`/`tiebreak` the RPC also returns are
 * used only for ordering and are projected out of the output.
 */
const EXERCISE_COLUMNS =
  'id, name, category, muscles, equipment, license, license_author, aliases, is_canonical, user_id';

/** Default maximum rows returned when no `limit` is given. */
const DEFAULT_LIMIT = 25;

/**
 * Zod input shape: an optional search term plus optional equipment / muscle /
 * category filters and a row limit. With `q` omitted the tool browses by filters
 * alone (ordered canonical-first, then by name).
 */
const inputSchema = {
  q: z
    .string({ message: 'q must be a string' })
    .min(1, { message: 'q must not be empty' })
    .optional(),
  equipment: z.string().min(1).optional(),
  muscle: z.string().min(1).optional(),
  category: z.string().min(1).optional(),
  limit: z.number().int().positive().max(100).optional(),
} as const;

/** Parsed and validated input for `search_exercises`. */
type ExerciseQuery = {
  q?: string;
  equipment?: string;
  muscle?: string;
  category?: string;
  limit?: number;
};

/**
 * Zod input shape for `create_exercise`: `name` is required; `category`,
 * `equipment`, `primaryMuscles`, and `aliases` are optional. The id, `user_id`
 * (injected from the auth context) and `is_canonical` (always false for a custom
 * exercise) are server-assigned and never accepted from the caller.
 */
const createInputSchema = {
  name: z.string().min(1, { message: 'name must not be empty' }),
  category: z.string().min(1).optional(),
  equipment: z.array(z.string().min(1)).optional(),
  primaryMuscles: z.array(z.string().min(1)).optional(),
  aliases: z.array(z.string().min(1)).optional(),
} as const;

/** Parsed and validated input for `create_exercise`. */
type CreateExerciseInput = {
  name: string;
  category?: string;
  equipment?: string[];
  primaryMuscles?: string[];
  aliases?: string[];
};

/**
 * Runs the catalog search via the `search_exercises` RPC, which does the
 * token-AND word-similarity matching, filtering, ranking (summed similarity,
 * canonical-first, then a name-similarity tiebreak) and top-N limiting in one
 * SQL statement. The `.select()` here only projects the catalog columns (dropping
 * the RPC's internal `score`/`tiebreak`); PostgREST preserves the function's
 * own row order (a column-only projection does not re-order), so the rows arrive
 * best-match first — an invariant the search integration test guards.
 */
async function searchCatalog(auth: AuthContext, input: ExerciseQuery): Promise<unknown> {
  const { data, error } = await auth.supabase
    .rpc('search_exercises', {
      search_query: input.q,
      filter_equipment: input.equipment,
      filter_muscle: input.muscle,
      filter_category: input.category,
      result_limit: input.limit ?? DEFAULT_LIMIT,
    })
    .select(EXERCISE_COLUMNS);
  if (error !== null) {
    throw new Error(`failed to search exercises: ${error.message}`);
  }
  return data;
}

/**
 * Inserts a custom exercise owned by the resolved user: `user_id` is injected
 * from the auth context (never from input) and `is_canonical` stays false so a
 * user row can never masquerade as a curated catalog base movement. The RLS
 * INSERT policy (`user_id = auth.uid()`) backs the injection, so a spoofed or
 * catalog (`user_id NULL`) write would be rejected at the DB boundary too.
 */
async function createExercise(
  auth: AuthContext,
  input: CreateExerciseInput,
): Promise<CallToolResult> {
  const { primaryMuscles, ...rest } = input;
  const { data, error } = await auth.supabase
    .from('exercises')
    .insert({
      ...rest,
      ...(primaryMuscles !== undefined ? { primary_muscles: primaryMuscles } : {}),
      user_id: auth.userId,
      is_canonical: false,
    })
    .select(EXERCISE_COLUMNS)
    .single<Record<string, unknown>>();
  if (error !== null) {
    return errorResult(`failed to create exercise: ${error.message}`);
  }
  return jsonResult(data);
}

/** Registers `search_exercises` and `create_exercise` on the server. */
export function registerExerciseTools(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'search_exercises',
    {
      title: 'Search exercises',
      description:
        'Search the exercise catalog: relevance-ranked, multi-word, alias-aware. ' +
        'Returns the shared public catalog plus your own custom exercises. ' +
        'Each whitespace token of q is matched against the exercise name ' +
        'and its aliases via trigram word-similarity (token-AND: every token must match), ' +
        'and results rank by summed similarity with canonical base movements first. ' +
        'Params: q (optional term — omit it to browse by filters alone), ' +
        'equipment (optional, matches the equipment list), muscle (optional, matches the ' +
        'primary/legacy muscle list), category (optional exact filter), ' +
        'limit (optional, 1-100, default 25). ' +
        'Returns: an array of exercise rows (id, name, category, muscles, equipment, ' +
        'aliases, is_canonical, license, license_author, user_id — user_id is null for a ' +
        'catalog row and set for your own custom exercise), best match first.',
      inputSchema,
      annotations: { readOnlyHint: true },
    },
    async (input) => jsonResult(await searchCatalog(auth, input)),
  );

  server.registerTool(
    'create_exercise',
    {
      title: 'Create custom exercise',
      description:
        'Create a custom exercise you own, usable in a plan exactly like a catalog row. ' +
        'Params: name (required) plus optional category, equipment (string array), ' +
        'primaryMuscles (string array), aliases (string array). ' +
        'The id and user_id are server-assigned (user_id from your API key, never accepted ' +
        'from input) and is_canonical is always false. ' +
        'Returns: the created exercise row, including its id to reference from a plan.',
      inputSchema: createInputSchema,
    },
    async (input) => createExercise(auth, input),
  );
}
