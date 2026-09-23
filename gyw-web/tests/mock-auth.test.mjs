import test from 'node:test'
import assert from 'node:assert/strict'
import { authenticate, decodeSession, safeRedirect } from '../src/domain/mockAuth.ts'

test('mock credentials validate exact passwords and omit passwords from profile', () => {
  assert.equal(authenticate(' admin ', 'Admin123!').name, '管理员')
  assert.equal(authenticate('demo', 'Demo123!').name, '体验用户')
  assert.equal(authenticate('admin', 'admin123!'), null)
  assert.equal(authenticate('unknown', 'Admin123!'), null)
  assert.equal('password' in authenticate('admin', 'Admin123!'), false)
})
test('sessions reject expired, malformed, and unknown users', () => {
  const session = {user: authenticate('admin', 'Admin123!'), expiresAt: 2000, remember: true}
  const raw = JSON.stringify(session)
  assert.equal(decodeSession(raw, 1000).user.username, 'admin')
  assert.equal(decodeSession(raw, 2000), null)
  for (const value of [null, '', '{broken', 'null', '{}', '[]']) assert.equal(decodeSession(value, 1000), null)
  assert.equal(decodeSession(JSON.stringify({...session, user: {id:'unknown'}}), 1000), null)
  assert.equal(decodeSession(JSON.stringify({...session, remember:'true'}), 1000), null)
  assert.equal(decodeSession(JSON.stringify({...session, user:{...session.user, name:'injected'}}),1000).user.name,'管理员')
})
test('return URLs preserve valid destinations and reject external or unknown routes', () => {
  assert.equal(safeRedirect('/boards/sales?sample=1#chart'), '/boards/sales?sample=1#chart')
  for(const value of ['https://example.com', '//example.com', '/\\example.com', '/login', '/unknown', null, ['/', '/orders']]) assert.equal(safeRedirect(value), '/')
})
