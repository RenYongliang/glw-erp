import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  authenticate,
  decodeSession,
  SESSION_KEY,
  LOGOUT_KEY,
  type MockSession,
} from '../domain/mockAuth'

function readSession(): MockSession | null {
  for (const name of ['sessionStorage', 'localStorage'] as const) {
    try {
      const storage = window[name]
      const raw = storage.getItem(SESSION_KEY)
      const session = decodeSession(raw)
      if (session) return session
      if (raw) storage.removeItem(SESSION_KEY)
    } catch {
      /* Storage can be disabled by browser settings. */
    }
  }
  return null
}
function clearSession() {
  for (const name of ['sessionStorage', 'localStorage'] as const) {
    try {
      const storage = window[name]
      storage.removeItem(SESSION_KEY)
    } catch {
      /* Keep logout usable even if storage is disabled. */
    }
  }
}
export const useAuthStore = defineStore('auth', () => {
  const session = ref<MockSession | null>(readSession())
  const user = computed(() => session.value?.user ?? null)
  const isAuthenticated = computed(() =>
    Boolean(session.value && session.value.expiresAt > Date.now()),
  )
  function sync() {
    session.value = readSession()
  }
  function login(username: string, password: string, remember: boolean) {
    const user = authenticate(username, password)
    if (!user) throw new Error('账号或密码不正确，请核对下方测试账号。')
    const next: MockSession = {
      user,
      remember,
      expiresAt: Date.now() + (remember ? 7 * 24 : 8) * 60 * 60 * 1000,
    }
    clearSession()
    try {
      ;(remember ? localStorage : sessionStorage).setItem(SESSION_KEY, JSON.stringify(next))
    } catch {
      throw new Error('浏览器无法保存登录状态，请允许此站点使用本地存储后重试。')
    }
    session.value = next
  }
  function logout(broadcast = true) {
    clearSession()
    session.value = null
    if (broadcast) {
      try {
        localStorage.setItem(LOGOUT_KEY, `${Date.now()}-${Math.random()}`)
      } catch {
        /* Current tab has already signed out. */
      }
    }
  }
  return { session, user, isAuthenticated, login, logout, sync }
})
