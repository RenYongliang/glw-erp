import { AuthClient } from './authClient'
import { mockAuthTransport } from './mockAuthTransport'
import type { AuthMode } from '../domain/authSession'
export const authMode: AuthMode = import.meta.env.VITE_AUTH_MODE === 'mock' ? 'mock' : 'backend'
export const authClient = new AuthClient({
  mode: authMode,
  baseUrl: import.meta.env.VITE_API_BASE_URL || '/api',
  transport: authMode === 'mock' ? mockAuthTransport : window.fetch.bind(window),
  local: window.localStorage,
  tab: window.sessionStorage,
  lock: navigator.locks
    ? (action) => navigator.locks.request('glw-auth-refresh', action)
    : undefined,
})
export const apiRequest = <T>(path: string, init?: RequestInit, anonymous = false) =>
  authClient.request<T>(path, init, anonymous)
