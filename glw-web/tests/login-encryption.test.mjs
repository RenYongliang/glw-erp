import test from 'node:test'
import assert from 'node:assert/strict'
import { encryptLogin } from '../src/services/loginEncryption.ts'
import { mockLoginKey, mockDecryptLogin } from '../src/services/mockLoginEncryption.ts'

test('encrypted credentials support Unicode and long input, never expose plaintext, reject replay', async () => {
  const credentials = {
    username: '光联微测试用户'.repeat(8),
    password: '密'.repeat(24),
    remember: true,
  }
  const body = await encryptLogin(await mockLoginKey(), credentials)
  assert.equal(body.username, undefined)
  assert.equal(body.password, undefined)
  assert.equal(JSON.stringify(body).includes(credentials.password), false)
  assert.deepEqual(await mockDecryptLogin(body), credentials)
  await assert.rejects(() => mockDecryptLogin(body))
})
test('rejects tampered ciphertext, challenge substitution, and expired keys', async () => {
  const credentials = { username: 'admin', password: 'Admin123!' }
  const body = await encryptLogin(await mockLoginKey(), credentials)
  body.ciphertext = (body.ciphertext.startsWith('A') ? 'B' : 'A') + body.ciphertext.slice(1)
  await assert.rejects(() => mockDecryptLogin(body))
  const other = await encryptLogin(await mockLoginKey(), credentials)
  other.challenge = (await mockLoginKey()).challenge
  await assert.rejects(() => mockDecryptLogin(other))
  const expired = { ...(await mockLoginKey()), expiresAt: new Date(Date.now() - 1).toISOString() }
  await assert.rejects(() => encryptLogin(expired, credentials))
})
