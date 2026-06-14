import { defineStore } from 'pinia'
import { loginApi, logoutApi } from '../api'
import { normalizeRole } from '../utils/role'
import { clearStoredSession, readStoredSession, setSessionToken, setSessionUser } from '../utils/session'

const sanitizeUser = (u) => {
  if (!u) return u
  const { token, password, ...profile } = u
  return { ...profile, role: normalizeRole(profile.role) }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const session = readStoredSession()
    return {
      token: session.token,
      user: sanitizeUser(session.user)
    }
  },
  actions: {
    async login(payload) {
      const result = await loginApi(payload)
      const token = result.token || ''
      const sanitized = sanitizeUser(result)

      this.token = token
      this.user = sanitized
      setSessionToken(token)
      setSessionUser(sanitized)
      return sanitized
    },
    async logout() {
      try {
        if (this.token) await logoutApi()
      } finally {
        this.clearSession()
      }
    },
    clearSession() {
      this.token = ''
      this.user = null
      clearStoredSession()
    },
    updateContactInfo({ phone, email }) {
      if (!this.user) return
      this.user = { ...this.user, phone, email }
      setSessionUser(this.user)
    }
  }
})
