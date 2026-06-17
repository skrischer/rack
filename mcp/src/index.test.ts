import { strict as assert } from 'node:assert';
import { test } from 'node:test';

import { PACKAGE_NAME, greet } from './index.js';

test('greet builds a greeting from the given name', () => {
  assert.equal(greet('Rack'), 'Hello, Rack!');
});

test('package name is exposed', () => {
  assert.equal(PACKAGE_NAME, 'rack-mcp');
});
