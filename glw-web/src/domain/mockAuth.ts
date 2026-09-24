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
      ![
        '/',
        '/orders',
        '/boards/sales',
        '/boards/purchase',
        '/boards/inventory',
        '/customers',
        '/followups',
      ].includes(url.pathname)
    )
      return '/'
    return url.pathname + url.search + url.hash
  } catch {
    return '/'
  }
}
