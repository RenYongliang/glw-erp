import test from 'node:test'
import assert from 'node:assert/strict'
import { AuthClient, ApiError } from '../src/services/authClient.ts'
import { SESSION_KEY, fromTokens, decodeSession } from '../src/domain/authSession.ts'
import { mockLoginKey, mockDecryptLogin } from '../src/services/mockLoginEncryption.ts'
class MemoryStorage {
  values = new Map()
  getItem(key) {
    return this.values.get(key) ?? null
  }
  setItem(key, value) {
    this.values.set(key, value)
  }
  removeItem(key) {
    this.values.delete(key)
  }
}
const tokens = (n = 1) => ({
  accessToken: `access-${n}`,
  refreshToken: `refresh-${n}`,
  tokenType: 'Bearer',
  expiresAt: new Date(Date.now() + 900000).toISOString(),
  refreshExpiresAt: new Date(Date.now() + 28800000).toISOString(),
  user: { id: '123', username: 'actual-user', name: '用户', role: 'USER', department: '销售部' },
})
const reply = (data, status = 200) =>
  new Response(
    JSON.stringify({ code: status === 200 ? 0 : status * 100, message: 'response', data }),
    { status },
  )
function setup(handler) {
  const local = new MemoryStorage(),
    tab = new MemoryStorage(),
    calls = []
  const client = new AuthClient({
    mode: 'backend',
    baseUrl: '/api',
    local,
    tab,
    transport: async (url, init) => {
      if (url.endsWith('/login-key')) {
        assert.equal(init.headers.has('Authorization'), false)
        return reply(await mockLoginKey())
      }
      if (url.endsWith('/login')) {
        const body = JSON.parse(init.body)
        assert.equal(body.password, undefined)
        assert.equal(body.username, undefined)
        assert.equal((await mockDecryptLogin(body)).password, 'pw')
      }
      calls.push({ url, init })
      return handler(url, init)
    },
  })
  tab.setItem(SESSION_KEY, JSON.stringify(fromTokens(tokens(), false, 'backend')))
  return { client, local, tab, calls }
}
test('accepts real server users and expired access with usable refresh, rejects malformed/expired sessions', () => {
  const s = fromTokens(tokens(), false, 'backend')
  s.expiresAt = Date.now() - 1
  assert.equal(decodeSession(JSON.stringify(s), 'backend').user.id, '123')
  assert.equal(decodeSession(JSON.stringify(s), 'mock'), null)
  s.refreshExpiresAt = Date.now() - 1
  assert.equal(decodeSession(JSON.stringify(s), 'backend'), null)
  assert.equal(decodeSession('invalid', 'backend'), null)
})
test('adds access bearer only on protected API and not login or refresh', async () => {
  const { client, calls } = setup(() => reply(tokens(2)))
  await client.request('/users/me')
  assert.equal(calls[0].init.headers.get('Authorization'), 'Bearer access-1')
  await client.login('admin', 'pw', true)
  assert.equal(calls[1].init.headers.has('Authorization'), false)
  await client.refresh()
  assert.equal(calls[2].init.headers.has('Authorization'), false)
  assert.equal(JSON.parse(calls[2].init.body).refreshToken, 'refresh-2')
})
test('concurrent expired requests share one refresh and retry with new access token', async () => {
  let refreshes = 0
  const { client, tab } = setup(async (url, init) => {
    if (url.endsWith('refresh-token')) {
      refreshes++
      await new Promise((r) => setTimeout(r, 15))
      return reply(tokens(2))
    }
    return init.headers.get('Authorization') === 'Bearer access-2' ? reply('ok') : reply(null, 401)
  })
  const s = JSON.parse(tab.getItem(SESSION_KEY))
  s.expiresAt = Date.now() - 1
  tab.setItem(SESSION_KEY, JSON.stringify(s))
  assert.deepEqual(
    await Promise.all(Array.from({ length: 12 }, () => client.request('/protected'))),
    Array(12).fill('ok'),
  )
  assert.equal(refreshes, 1)
})
test('401 rotates once and retry uses new token; a second 401 clears the session', async () => {
  const { client, calls } = setup((url) =>
    url.endsWith('refresh-token') ? reply(tokens(2)) : reply(null, 401),
  )
  await assert.rejects(() => client.request('/protected'), ApiError)
  assert.equal(calls.length, 3)
  assert.equal(client.read(), null)
})
test('403 never refreshes; 503 refresh failures keep the session', async () => {
  const { client, calls } = setup(() => reply(null, 403))
  await assert.rejects(() => client.request('/protected'), ApiError)
  assert.equal(calls.length, 1)
  assert.ok(client.read())
  const other = setup(() => reply(null, 503))
  await assert.rejects(() => other.client.refresh(), ApiError)
  assert.ok(other.client.read())
})
test('invalid refresh clears both stores', async () => {
  const { client, local, tab } = setup(() => reply(null, 401))
  await assert.rejects(() => client.refresh(), ApiError)
  assert.equal(tab.getItem(SESSION_KEY), null)
  assert.equal(local.getItem(SESSION_KEY), null)
})
test('logout revokes the server session then clears local credentials even on network failure', async () => {
  const { client, calls } = setup(() => reply(null))
  await client.logout()
  assert.equal(calls[0].url, '/api/users/logout')
  assert.equal(client.read(), null)
  const failed = setup(() => {
    throw new Error('offline')
  })
  await assert.rejects(() => failed.client.logout())
  assert.equal(failed.client.read(), null)
})
test('a refresh response arriving after logout cannot restore authentication', async () => {
  let complete
  const { client } = setup(
    () =>
      new Promise((resolve) => {
        complete = () => resolve(reply(tokens(2)))
      }),
  )
  const pending = client.refresh()
  client.clear()
  complete()
  await assert.rejects(() => pending, ApiError)
  assert.equal(client.read(), null)
})
test('remember login uses localStorage; normal login uses sessionStorage', async () => {
  const { client, local, tab } = setup(() => reply(tokens(2)))
  await client.login('admin', 'pw', true)
  assert.ok(local.getItem(SESSION_KEY))
  assert.equal(tab.getItem(SESSION_KEY), null)
  await client.login('admin', 'pw', false)
  assert.ok(tab.getItem(SESSION_KEY))
  assert.equal(local.getItem(SESSION_KEY), null)
})
