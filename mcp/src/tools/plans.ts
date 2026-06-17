/**
 * Read tools over the caller's plans: list every plan and fetch a single plan's
 * days + exercises tree. Both run through the per-request user-scoped client so
 * only the resolved user's rows return (RLS, `user_id = auth.uid()`); neither
 * tool exposes a `user_id` or account selector. See docs/specs/spec-rack-mcp.md.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

import type { AuthContext } from '../auth.js';
import { jsonResult } from './result.js';

/** Columns returned for a plan row (the caller's own, by RLS). */
const PLAN_COLUMNS = 'id, name, kind, source, created_at, updated_at';

/** Columns returned for a plan day, ordered by `position`. */
const DAY_COLUMNS = 'id, plan_id, position, title, focus, tag, source, updated_at';

/** Columns returned for a plan exercise, joined to its catalog name. */
const EXERCISE_COLUMNS =
  'id, day_id, exercise_id, position, target, rir, cue, superset_label, source, ' +
  'exercise:exercises(name, category)';

/** Registers `list_plans`: returns every plan owned by the resolved user. */
function registerListPlans(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'list_plans',
    {
      title: 'List plans',
      description: "List the caller's training plans, newest first.",
      inputSchema: {},
    },
    async () => {
      const { data, error } = await auth.supabase
        .from('plans')
        .select(PLAN_COLUMNS)
        .order('created_at', { ascending: false });
      if (error !== null) {
        throw new Error(`failed to list plans: ${error.message}`);
      }
      return jsonResult(data);
    },
  );
}

/** Registers `get_plan`: returns one plan with its days and their exercises. */
function registerGetPlan(server: McpServer, auth: AuthContext): void {
  server.registerTool(
    'get_plan',
    {
      title: 'Get plan tree',
      description: "Get one of the caller's plans with its days and each day's exercises.",
      inputSchema: { planId: z.uuid({ message: 'planId must be a UUID' }) },
    },
    async ({ planId }) => jsonResult(await loadPlanTree(auth, planId)),
  );
}

/** Resolved plan with its ordered days and each day's ordered exercises. */
interface PlanTree {
  plan: Record<string, unknown> | null;
  days: Record<string, unknown>[];
  exercises: Record<string, unknown>[];
}

/** Loads a plan and, if it exists for the caller, its days and exercises. */
async function loadPlanTree(auth: AuthContext, planId: string): Promise<PlanTree> {
  const plan = await selectOne(auth, 'plans', PLAN_COLUMNS, 'id', planId);
  if (plan === null) {
    return { plan: null, days: [], exercises: [] };
  }
  const days = await selectMany(auth, 'plan_days', DAY_COLUMNS, 'plan_id', planId);
  const dayIds = days.map((day) => String(day.id));
  const exercises =
    dayIds.length === 0 ? [] : await selectExercises(auth, dayIds);
  return { plan, days, exercises };
}

/** Selects exactly one row by an equality filter, or null when none match. */
async function selectOne(
  auth: AuthContext,
  table: string,
  columns: string,
  column: string,
  value: string,
): Promise<Record<string, unknown> | null> {
  const { data, error } = await auth.supabase
    .from(table)
    .select(columns)
    .eq(column, value)
    .maybeSingle<Record<string, unknown>>();
  if (error !== null) {
    throw new Error(`failed to read ${table}: ${error.message}`);
  }
  return data;
}

/** Selects rows by an equality filter, ordered by `position`. */
async function selectMany(
  auth: AuthContext,
  table: string,
  columns: string,
  column: string,
  value: string,
): Promise<Record<string, unknown>[]> {
  const { data, error } = await auth.supabase
    .from(table)
    .select(columns)
    .eq(column, value)
    .order('position', { ascending: true })
    .overrideTypes<Record<string, unknown>[], { merge: false }>();
  if (error !== null) {
    throw new Error(`failed to read ${table}: ${error.message}`);
  }
  return data ?? [];
}

/** Selects the exercises across the given day ids, ordered by day then position. */
async function selectExercises(
  auth: AuthContext,
  dayIds: string[],
): Promise<Record<string, unknown>[]> {
  const { data, error } = await auth.supabase
    .from('plan_exercises')
    .select(EXERCISE_COLUMNS)
    .in('day_id', dayIds)
    .order('day_id', { ascending: true })
    .order('position', { ascending: true })
    .overrideTypes<Record<string, unknown>[], { merge: false }>();
  if (error !== null) {
    throw new Error(`failed to read plan_exercises: ${error.message}`);
  }
  return data ?? [];
}

/** Registers the plan read tools (`list_plans`, `get_plan`) on the server. */
export function registerPlanTools(server: McpServer, auth: AuthContext): void {
  registerListPlans(server, auth);
  registerGetPlan(server, auth);
}
