/**
 * rack-MCP package entry point (scaffold).
 *
 * Phase 1 only establishes the package, its strict TypeScript toolchain, and the
 * verify/build gates. The MCP server, transport, and API-key resolution land in
 * Phase 2 (see docs/specs/spec-rack-mcp.md).
 */

/** Human-readable name of this package, used by the future MCP server banner. */
export const PACKAGE_NAME = 'rack-mcp';

/**
 * Returns a greeting for the given name. A trivial typed function so the
 * toolchain (lint, typecheck, build, test) has real source to operate on.
 */
export function greet(name: string): string {
  return `Hello, ${name}!`;
}
