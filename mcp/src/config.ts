/**
 * Runtime configuration for the rack-MCP server.
 *
 * All values are read from the process environment (sourced from the gitignored
 * `.env`, see `.env.example`). Validation happens once at startup via Zod so a
 * missing or malformed value fails fast with a clear message rather than
 * surfacing as an obscure runtime error later. No secret is ever logged.
 */

import { z } from 'zod';

/** Default port the Streamable HTTP server listens on when `MCP_PORT` is unset. */
const DEFAULT_MCP_PORT = 8787;

const configSchema = z.object({
  supabaseUrl: z.string().url(),
  supabaseAnonKey: z.string().min(1),
  supabaseServiceRoleKey: z.string().min(1),
  supabaseJwtSecret: z.string().min(1),
  apiKeyPepper: z.string().min(1),
  mcpPort: z.coerce.number().int().positive().default(DEFAULT_MCP_PORT),
});

/** Validated, typed runtime configuration. */
export type Config = z.infer<typeof configSchema>;

/**
 * Reads and validates the rack-MCP configuration from the given environment
 * (defaults to `process.env`). Throws a Zod error if any required value is
 * missing or malformed.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  return configSchema.parse({
    supabaseUrl: env.SUPABASE_URL,
    supabaseAnonKey: env.SUPABASE_ANON_KEY,
    supabaseServiceRoleKey: env.SUPABASE_SERVICE_ROLE_KEY,
    supabaseJwtSecret: env.SUPABASE_JWT_SECRET,
    apiKeyPepper: env.API_KEY_PEPPER,
    mcpPort: env.MCP_PORT,
  });
}
