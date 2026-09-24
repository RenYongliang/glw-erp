import test from 'node:test'
import assert from 'node:assert/strict'
import { authenticate, safeRedirect } from '../src/domain/mockAuth.ts'
test('offline mock login checks passwords and never returns passwords', () => {
  assert.equal(authenticate('admin', 'Admin123!').username, 'admin')
  assert.equal(authenticate('admin', 'wrong'), null)
  assert.equal(authenticate('unknown', 'Admin123!'), null)
  assert.equal(authenticate('admin', 'Admin123!').password, undefined)
})
test('login return permits existing pages and prevents external redirects', () => {
  for (const path of [
    '/',
    '/orders',
    '/boards/inventory',
    '/boards/sales',
    '/boards/purchase',
    '/customers',
    '/followups',
  ])
    assert.equal(safeRedirect(path), path)
  for (const path of ['//example.com', 'https://example.com', '/unknown', '/\\example.com'])
    assert.equal(safeRedirect(path), '/')
})
