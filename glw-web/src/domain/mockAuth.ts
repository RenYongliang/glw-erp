export const SESSION_KEY = 'glw.auth.session.v1'
export const LOGOUT_KEY = 'glw.auth.logout.v1'
export const mockAccounts = [
  {
    username: 'admin',
    password: 'Admin123!',
    id: 'user-admin',
    name: '管理员',
    role: '系统管理员',
    department: '运营管理部',
  },
  {
    username: 'demo',
    password: 'Demo123!',
    id: 'user-demo',
    name: '体验用户',
    role: '演示用户',
    department: '业务体验组',
  },
] as const
export interface MockUser {
  id: string
  username: string
  name: string
  role: string
  department: string
}
export interface MockSession {
  user: MockUser
  expiresAt: number
  remember: boolean
}
export function authenticate(username: string, password: string): MockUser | null {
  const account = mockAccounts.find(
    (account) => account.username === username.trim() && account.password === password,
  )
  if (!account) return null
  return {
    id: account.id,
    username: account.username,
    name: account.name,
    role: account.role,
    department: account.department,
  }
}
export function decodeSession(raw: string | null, now = Date.now()): MockSession | null {
  try {
    if (!raw) return null
    const value = JSON.parse(raw)
    if (
      !value ||
      typeof value.expiresAt !== 'number' ||
      !Number.isFinite(value.expiresAt) ||
      value.expiresAt <= now ||
      typeof value.remember !== 'boolean'
    )
      return null
    const account = mockAccounts.find(
      (account) => account.id === value.user?.id && account.username === value.user?.username,
    )
    if (!account) return null
    return {
      user: {
        id: account.id,
        username: account.username,
        name: account.name,
        role: account.role,
        department: account.department,
      },
      expiresAt: value.expiresAt,
      remember: value.remember,
    }
  } catch {
    return null
  }
}
export function safeRedirect(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  )
    return '/'
  try {
    const url = new URL(value, 'https://glw.local')
    if (
      url.origin !== 'https://glw.local' ||
      !['/', '/orders', '/boards/sales', '/boards/purchase'].includes(url.pathname)
    )
      return '/'
    return url.pathname + url.search + url.hash
  } catch {
    return '/'
  }
}
