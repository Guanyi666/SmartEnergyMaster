import { defineStore } from 'pinia'
import { loginApi, logoutApi } from '../api'
import { normalizeRole } from '../utils/role'
import { clearStoredSession, readStoredSession, TOKEN_KEY, USER_KEY } from '../utils/session'

// 拿到后端返回的 user 时归一化 role：防御 "HR MANAGER" / "HR_MANAGER " 等空格脏数据导致路由死循环
const sanitizeUser = (u) => {
  if (!u) return u
  return { ...u, role: normalizeRole(u.role) }
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
      const sanitized = sanitizeUser(result)
      this.token = sanitized.token
      this.user = sanitized
      localStorage.setItem(TOKEN_KEY, sanitized.token)
      localStorage.setItem(USER_KEY, JSON.stringify(sanitized))
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
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    }
  }
})
