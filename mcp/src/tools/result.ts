/**
 * Shared result helpers for rack-MCP tool handlers.
 *
 * Read tools return their data as a single JSON text block so any MCP client can
 * render it without an out-of-band schema. The data is the resolved user's rows
 * (already RLS-scoped) or public catalog rows; handlers never shape another
 * user's data because the user-scoped client cannot read it. See
 * docs/specs/spec-rack-mcp.md.
 */

import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';

/** Wraps a query payload as a pretty-printed JSON text tool result. */
export function jsonResult(payload: unknown): CallToolResult {
  return { content: [{ type: 'text', text: JSON.stringify(payload, null, 2) }] };
}
