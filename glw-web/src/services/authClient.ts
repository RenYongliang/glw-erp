import {
  SESSION_KEY,
  LOGOUT_KEY,
  decodeSession,
  fromTokens,
  type AuthSession,
  type AuthMode,
  type TokenResponse,
} from '../domain/authSession.ts'
import { encryptLogin, type LoginKey } from './loginEncryption.ts'
export interface DownloadFile {
  blob: Blob
  fileName: string
}
function downloadFileName(response: Response): string {
  const disposition = response.headers.get('Content-Disposition') || ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  if (encoded) {
    try {
      return decodeURIComponent(encoded)
    } catch {
      /* Fall back to the plain filename. */
    }
  }
  return /filename="?([^";]+)"?/i.exec(disposition)?.[1] || ''
}
/** 兼容不支持 AbortSignal.timeout/any 的旧版移动浏览器。 */
function requestSignal(signal: AbortSignal | null | undefined, timeout: number): AbortSignal {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  const abort = () => {
    clearTimeout(timer)
    controller.abort()
  }
  if (signal) {
    if (signal.aborted) abort()
    else signal.addEventListener('abort', abort, { once: true })
  }
  controller.signal.addEventListener(
    'abort',
    () => {
      clearTimeout(timer)
      signal?.removeEventListener('abort', abort)
    },
    { once: true },
  )
  return controller.signal
}
export class ApiError extends Error {
  status: number
  code: number
  constructor(message: string, status: number, code = status) {
    super(message)
    this.status = status
    this.code = code
  }
}
interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}
interface Options {
  mode: AuthMode
  baseUrl: string
  transport: typeof fetch
  local: StorageLike
  tab: StorageLike
  lock?: <T>(action: () => Promise<T>) => Promise<T>
}
/** Central request client. The refresh token is only sent to the refresh endpoint. */
export class AuthClient {
  private options: Options
  private refreshTask: Promise<AuthSession> | null = null
  private generation = 0
  private listeners = new Set<() => void>()
  constructor(options: Options) {
    this.options = options
  }
  subscribe(callback: () => void) {
    this.listeners.add(callback)
    return () => {
      this.listeners.delete(callback)
    }
  }
  private notify() {
    this.listeners.forEach((fn) => fn())
  }
  read(): AuthSession | null {
    for (const storage of [this.options.tab, this.options.local]) {
      try {
        const raw = storage.getItem(SESSION_KEY)
        const value = decodeSession(raw, this.options.mode)
        if (value) return value
        if (raw) storage.removeItem(SESSION_KEY)
      } catch {
        /* Storage may be disabled. */
      }
    }
    return null
  }
  private save(session: AuthSession) {
    try {
      const target = session.remember ? this.options.local : this.options.tab
      target.setItem(SESSION_KEY, JSON.stringify(session))
      ;(session.remember ? this.options.tab : this.options.local).removeItem(SESSION_KEY)
    } catch {
      throw new Error('无法保存登录状态，请检查浏览器存储设置')
    }
    this.notify()
  }
  clear(broadcast = true) {
    this.generation++
    for (const storage of [this.options.tab, this.options.local]) {
      try {
        storage.removeItem(SESSION_KEY)
        storage.removeItem('glw.auth.session.v1')
      } catch {
        /* best effort */
      }
    }
    if (broadcast) {
      try {
        this.options.local.setItem(LOGOUT_KEY, `${Date.now()}-${Math.random()}`)
      } catch {
        /* best effort */
      }
    }
    this.notify()
  }
  private async send<T>(
    path: string,
    init: RequestInit = {},
    token?: string,
    binary: boolean | 'download' = false,
  ): Promise<T> {
    if (
      !path.startsWith('/') ||
      path.startsWith('//') ||
      path.includes('\\') ||
      path.includes('://')
    )
      throw new Error('接口地址必须为相对路径')
    const headers = new Headers(init.headers)
    headers.delete('Authorization')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type'))
      headers.set('Content-Type', 'application/json')
    const response = await this.options.transport(this.options.baseUrl.replace(/\/$/, '') + path, {
      ...init,
      headers,
      credentials: 'omit',
      cache: 'no-store',
      signal: requestSignal(init.signal, binary ? 120000 : 15000),
    })
    if (binary && response.ok) {
      const blob = await response.blob()
      if (binary === 'download') return { blob, fileName: downloadFileName(response) } as T
      return blob as T
    }
    let envelope: { code: number; message: string; data: T }
    try {
      envelope = await response.json()
    } catch {
      throw new ApiError('接口返回格式不正确', response.status || 502)
    }
    if (!response.ok || envelope.code !== 0)
      throw new ApiError(envelope.message || '请求失败', response.status, envelope.code)
    return envelope.data
  }
  async login(username: string, password: string, remember: boolean) {
    const generation = ++this.generation
    const key = await this.send<LoginKey>('/users/login-key')
    const encrypted = await encryptLogin(key, { username, password, remember })
    if (generation !== this.generation) throw new Error('登录操作已取消')
    const tokens = await this.send<TokenResponse>('/users/login', {
      method: 'POST',
      body: JSON.stringify(encrypted),
    })
    if (generation !== this.generation) throw new Error('登录操作已取消')
    this.save(fromTokens(tokens, remember, this.options.mode))
  }
  async refresh(failedToken?: string): Promise<AuthSession> {
    if (this.refreshTask) return this.refreshTask
    const start = this.read(),
      generation = this.generation
    if (!start) {
      this.clear()
      throw new ApiError('登录已失效，请重新登录', 401)
    }
    const action = async () => {
      const current = this.read()
      if (!current || generation !== this.generation) throw new ApiError('登录已退出', 401)
      // Another tab/request may already have rotated the pair while we waited for the lock.
      if (
        (failedToken && current.accessToken !== failedToken) ||
        current.refreshToken !== start.refreshToken
      )
        return current
      try {
        const tokens = await this.send<TokenResponse>('/users/refresh-token', {
          method: 'POST',
          body: JSON.stringify({ refreshToken: current.refreshToken }),
        })
        if (generation !== this.generation || this.read()?.refreshToken !== current.refreshToken)
          throw new ApiError('登录状态已改变', 401)
        const next = fromTokens(tokens, current.remember, this.options.mode)
        this.save(next)
        return next
      } catch (e) {
        // Network/503 failures do not destroy a still-valid refresh session.
        if (
          e instanceof ApiError &&
          e.status === 401 &&
          generation === this.generation &&
          this.read()?.refreshToken === current.refreshToken
        )
          this.clear()
        throw e
      }
    }
    const task = this.options.lock ? this.options.lock(action) : action()
    this.refreshTask = task
    try {
      return await task
    } finally {
      if (this.refreshTask === task) this.refreshTask = null
    }
  }
  async ensureSession() {
    const session = this.read()
    if (!session) return null
    return session.expiresAt <= Date.now() + 30000 ? this.refresh() : session
  }
  async request<T>(
    path: string,
    init: RequestInit = {},
    anonymous = false,
    binary: boolean | 'download' = false,
  ): Promise<T> {
    if (anonymous) return this.send<T>(path, init, undefined, binary)
    const session = await this.ensureSession()
    if (!session) {
      this.clear()
      throw new ApiError('请先登录', 401)
    }
    const generation = this.generation
    try {
      return await this.send<T>(path, init, session.accessToken, binary)
    } catch (e) {
      if (!(e instanceof ApiError) || e.status !== 401 || generation !== this.generation) throw e
      const next = await this.refresh(session.accessToken)
      try {
        return await this.send<T>(path, init, next.accessToken, binary)
      } catch (retryError) {
        if (
          retryError instanceof ApiError &&
          retryError.status === 401 &&
          generation === this.generation
        )
          this.clear()
        throw retryError
      }
    }
  }
  async logout() {
    try {
      if (this.read()) await this.request('/users/logout', { method: 'POST' })
    } finally {
      this.clear()
    }
  }
}
