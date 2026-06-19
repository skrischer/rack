/**
 * Regression test for the §4.5 `get_plan`-after-writes hang.
 *
 * The hang's root cause is an unbounded request: the per-request supabase-js
 * client used Node's global `fetch` with no timeout, so a read reusing a
 * silently-dropped keep-alive socket (only happens over the hosted network, not
 * loopback) stalled for minutes. {@link timeoutFetch} caps every request. This
 * test reproduces the dead-socket condition deterministically with a black-hole
 * server that accepts connections but never responds, and asserts the wrapped
 * fetch aborts promptly instead of hanging — and that it leaves a normal,
 * responsive request untouched.
 */

import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import type { Socket } from 'node:net';

import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { timeoutFetch } from './supabase.js';

/** A server that accepts connections but never answers, plus one that does. */
let blackHole: Server;
let blackHoleUrl: string;
let responsive: Server;
let responsiveUrl: string;
const openSockets = new Set<Socket>();

beforeAll(async () => {
  blackHole = createServer(() => {
    /* deliberately never respond: simulates a dead/half-open upstream socket */
  });
  blackHole.on('connection', (socket) => {
    openSockets.add(socket);
    socket.on('close', () => openSockets.delete(socket));
  });
  responsive = createServer((_req, res) => {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{"ok":true}');
  });
  await Promise.all([
    new Promise<void>((resolve) => blackHole.listen(0, '127.0.0.1', resolve)),
    new Promise<void>((resolve) => responsive.listen(0, '127.0.0.1', resolve)),
  ]);
  blackHoleUrl = `http://127.0.0.1:${(blackHole.address() as AddressInfo).port}/`;
  responsiveUrl = `http://127.0.0.1:${(responsive.address() as AddressInfo).port}/`;
});

afterAll(async () => {
  for (const socket of openSockets) {
    socket.destroy();
  }
  await Promise.all([
    new Promise<void>((resolve) => blackHole.close(() => resolve())),
    new Promise<void>((resolve) => responsive.close(() => resolve())),
  ]);
});

describe('timeoutFetch', () => {
  it('aborts a never-responding request within the timeout instead of hanging', async () => {
    const fetchWithTimeout = timeoutFetch(300);
    const start = process.hrtime.bigint();
    await expect(fetchWithTimeout(blackHoleUrl)).rejects.toMatchObject({
      name: 'TimeoutError',
    });
    const elapsedMs = Number(process.hrtime.bigint() - start) / 1e6;
    expect(elapsedMs).toBeLessThan(3_000);
  });

  it('passes a normal, responsive request through unchanged', async () => {
    const fetchWithTimeout = timeoutFetch(5_000);
    const response = await fetchWithTimeout(responsiveUrl);
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ ok: true });
  });

  it('still aborts on the per-request timeout when a caller signal is supplied', async () => {
    const fetchWithTimeout = timeoutFetch(300);
    const caller = new AbortController();
    const start = process.hrtime.bigint();
    await expect(
      fetchWithTimeout(blackHoleUrl, { signal: caller.signal }),
    ).rejects.toMatchObject({ name: 'TimeoutError' });
    const elapsedMs = Number(process.hrtime.bigint() - start) / 1e6;
    expect(elapsedMs).toBeLessThan(3_000);
  });

  it('rejects immediately when the caller signal is already aborted', async () => {
    const fetchWithTimeout = timeoutFetch(5_000);
    await expect(
      fetchWithTimeout(blackHoleUrl, { signal: AbortSignal.abort() }),
    ).rejects.toThrow();
  });
});
