import { authenticate } from '../domain/mockAuth'
import type { TokenResponse } from '../domain/authSession'
import { mockLoginKey, mockDecryptLogin } from './mockLoginEncryption.ts'
const KEY = 'glw.mock.auth.server.v1'
/** Offline demo only. These opaque random tokens are never accepted by the backend. */
export const mockAuthTransport: typeof fetch = async (input, init) => {
  const path = String(input).split('?')[0]!,
    method = init?.method || 'GET'
  let body = init?.body ? JSON.parse(String(init.body)) : {}
  const sessions: TokenResponse[] = JSON.parse(localStorage.getItem(KEY) || '[]').filter(
    (s: TokenResponse) => Date.parse(s.refreshExpiresAt) > Date.now(),
  )
  const response = (data: unknown, status = 200, message = '成功') =>
    new Response(
      JSON.stringify({
        code: status === 200 ? 0 : status * 100,
        message,
        data,
        timestamp: new Date().toISOString(),
      }),
      { status, headers: { 'Content-Type': 'application/json' } },
    )
  const unauthorized = () => response(null, 401, '未登录或登录已失效')
  const pair = (user: TokenResponse['user'], end: number): TokenResponse => ({
    user,
    accessToken: 'mock-access-' + crypto.randomUUID(),
    refreshToken: 'mock-refresh-' + crypto.randomUUID(),
    tokenType: 'Bearer',
    expiresAt: new Date(Math.min(Date.now() + 15 * 60000, end)).toISOString(),
    refreshExpiresAt: new Date(end).toISOString(),
  })
  const persist = () => localStorage.setItem(KEY, JSON.stringify(sessions))
  if (method === 'GET' && path.endsWith('/users/login-key')) return response(await mockLoginKey())
  if (method === 'POST' && (path.endsWith('/users/login') || path.endsWith('/users/token'))) {
    try {
      body = await mockDecryptLogin(body)
    } catch {
      return response(null, 400, '登录加密数据无效或已过期，请重新登录')
    }
    const user = authenticate(body.username || '', body.password || '')
    if (!user) return response(null, 401, '账号或密码不正确')
    const tokens = pair(user, Date.now() + (body.remember ? 7 * 24 : 8) * 3600000)
    sessions.push(tokens)
    persist()
    return response(tokens)
  }
  if (method === 'POST' && path.endsWith('/users/refresh-token')) {
    const index = sessions.findIndex((s) => s.refreshToken === body.refreshToken)
    if (index < 0) return unauthorized()
    const old = sessions[index]!,
      tokens = pair(old.user, Date.parse(old.refreshExpiresAt))
    sessions[index] = tokens
    persist()
    return response(tokens)
  }
  const bearer = new Headers(init?.headers).get('Authorization'),
    index = sessions.findIndex(
      (s) => bearer === `Bearer ${s.accessToken}` && Date.parse(s.expiresAt) > Date.now(),
    )
  if (index < 0) return unauthorized()
  if (method === 'GET' && path.endsWith('/users/me')) return response(sessions[index]!.user)
  if (method === 'POST' && path.endsWith('/users/logout')) {
    sessions.splice(index, 1)
    persist()
    return response(null)
  }
  return response(null, 404, '该演示接口尚未实现')
}
