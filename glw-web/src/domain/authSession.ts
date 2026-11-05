export const SESSION_KEY = 'glw.auth.session.v2'
export const LOGOUT_KEY = 'glw.auth.logout.v1'
export type AuthMode = 'backend' | 'mock'
export interface AuthUser {
  id: string
  username: string
  name: string
  role: string
  /** 所属部门ID；旧会话可能没有该字段。 */
  departmentId?: number
  department: string
}
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresAt: string
  refreshExpiresAt: string
  user: AuthUser
}
export interface AuthSession {
  accessToken: string
  refreshToken: string
  expiresAt: number
  refreshExpiresAt: number
  user: AuthUser
  remember: boolean
  mode: AuthMode
}
export function decodeSession(
  raw: string | null,
  mode: AuthMode,
  now = Date.now(),
): AuthSession | null {
  try {
    if (!raw) return null
    const s = JSON.parse(raw)
    if (
      s.mode !== mode ||
      typeof s.remember !== 'boolean' ||
      !Number.isFinite(s.expiresAt) ||
      !Number.isFinite(s.refreshExpiresAt) ||
      s.refreshExpiresAt <= now ||
      s.expiresAt > s.refreshExpiresAt
    )
      return null
    if (
      ![
        s.accessToken,
        s.refreshToken,
        s.user?.id,
        s.user?.username,
        s.user?.name,
        s.user?.role,
        s.user?.department,
      ].every((v) => typeof v === 'string' && v.length > 0)
    )
      return null
    return s as AuthSession
  } catch {
    return null
  }
}
export function fromTokens(tokens: TokenResponse, remember: boolean, mode: AuthMode): AuthSession {
  const session = decodeSession(
    JSON.stringify({
      ...tokens,
      expiresAt: Date.parse(tokens.expiresAt),
      refreshExpiresAt: Date.parse(tokens.refreshExpiresAt),
      remember,
      mode,
    }),
    mode,
  )
  if (!session || tokens.tokenType !== 'Bearer' || session.expiresAt <= Date.now())
    throw new Error('登录接口返回了无效的凭据')
  return session
}
