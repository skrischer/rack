/**
 * Transactional nested-create tools (Phase 16): `create_plan` writes a whole
 * plan tree (plan -> days -> each day's exercises) in one call and
 * `create_plan_day` appends one day plus its exercises to an existing plan. Both
 * Zod-validate the whole nested camelCase input at the MCP boundary, then pass it
 * as a single `payload` to a `SECURITY INVOKER` plpgsql RPC via the user-scoped
 * client, so the entire tree is inserted under RLS as the resolved user in one
 * transaction with every row `source='agent'`. Any failure (Zod, FK, CHECK, RLS)
 * leaves nothing written and surfaces a loud error. No tool accepts a `user_id`;
 * identity comes solely from the auth context's JWT. The exercise/day shapes are
 * the Phase 15 typed fields reused from {@link writeTools}. See
 * docs/specs/spec-mcp-authoring.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { errorResult, jsonResult } from './result.js';
import { planDayFields, planExerciseFields, repRangeOk, rirRangeOk } from './writeTools.js';

/** One nested exercise: the shared typed prescription fields + range invariants. */
const nestedExercise = z
  .object(planExerciseFields)
  .refine(repRangeOk, { message: 'repMin must be less than or equal to repMax', path: ['repMin'] })
  .refine(rirRangeOk, { message: 'rirLow must be less than or equal to rirHigh', path: ['rirLow'] });

/** One nested day: the shared day fields plus its ordered exercises. */
const nestedDay = z.object({ ...planDayFields, exercises: z.array(nestedExercise).default([]) });

/** Full `create_plan` input: a plan plus its days and each day's exercises. */
const createPlanInput = z.object({
  name: z.string().min(1, { message: 'name must not be empty' }),
  kind: z.string().min(1).optional(),
  days: z.array(nestedDay).default([]),
});

/** `create_plan_day` input: an existing planId plus one day and its exercises. */
const createPlanDayInput = z.object({
  planId: z.uuid({ message: 'planId must be a UUID' }),
  ...planDayFields,
  exercises: z.array(nestedExercise).default([]),
});

/** Calls a nested-create RPC with the validated camelCase payload as one jsonb arg. */
async function callRpc(
  auth: AuthContext,
  fn: 'create_plan' | 'create_plan_day',
  payload: Record<string, unknown>,
): Promise<CallToolResult> {
  const { data, error } = await auth.supabase.rpc(fn, { payload });
  if (error !== null) {
    return errorResult(`failed to ${fn}: ${error.message}`);
  }
  return jsonResult(data);
}

/** Registers the transactional nested-create tools (`create_plan`, `create_plan_day`). */
export function registerCreatePlanTools(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'create_plan',
    {
      title: 'Create plan',
      description:
        'Create a whole training plan in one atomic transaction: the plan, its days, ' +
        "and each day's exercises, all written with source='agent'. " +
        'Params: name (required), kind?, and days[] where each day is ' +
        '{ position, title?, focus?, tag?, exercises[] } and each exercise is ' +
        '{ exerciseId (UUID), position, sets?, repMin?, repMax?, rirLow?, rirHigh?, ' +
        'restSeconds?, cue?, supersetId?, groupType? } (Zod-validated; repMin<=repMax, ' +
        'rirLow<=rirHigh; id and user_id are server-assigned). Grouping: set a ' +
        'per-day-local supersetId plus groupType on grouped exercises. ' +
        'Any failure rolls the entire tree back and writes nothing. ' +
        'Returns: { planId, dayCount, exerciseCount }; read the tree back via get_plan.',
      inputSchema: createPlanInput,
    },
    async (input) => callRpc(auth, 'create_plan', input),
  );

  server.registerTool(
    'create_plan_day',
    {
      title: 'Create plan day',
      description:
        'Append one day plus its exercises to an existing plan in one atomic transaction ' +
        "(written with source='agent'), without rewriting the whole tree. " +
        'Params: planId (required UUID of a plan the caller owns), position, title?, focus?, ' +
        'tag?, and exercises[] (same exercise shape as create_plan). ' +
        'Any failure rolls the day and its exercises back and writes nothing. ' +
        'Returns: { planId, dayId, exerciseCount }; errors if the plan is not found or not owned.',
      inputSchema: createPlanDayInput,
    },
    async (input) => callRpc(auth, 'create_plan_day', input),
  );
}
