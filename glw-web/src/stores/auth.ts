import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authClient } from '../services/api'
export const useAuthStore = defineStore('auth', () => {
  const session = ref(authClient.read())
  const user = computed(() => session.value?.user ?? null)
  const isAuthenticated = computed(() =>
    Boolean(session.value && session.value.refreshExpiresAt > Date.now()),
  )
  function sync() {
    session.value = authClient.read()
  }
  authClient.subscribe(sync)
  async function login(username: string, password: string, remember: boolean) {
    await authClient.login(username, password, remember)
  }
  async function ensureSession() {
    try {
      await authClient.ensureSession()
    } finally {
      sync()
    }
  }
  async function logout(broadcast = true) {
    if (!broadcast) authClient.clear(false)
    else await authClient.logout()
  }
  return { session, user, isAuthenticated, sync, login, logout, ensureSession }
})
