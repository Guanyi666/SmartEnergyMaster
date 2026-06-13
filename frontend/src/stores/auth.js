import { defineStore } from 'pinia'
import { loginApi, logoutApi } from '../api'
import { normalizeRole } from '../utils/role'

const TOKEN_KEY = 'smart-energy-token'
const USER_KEY = 'smart-energy-user'

// 拿到后端返回的 user 时归一化 role：防御 "HR MANAGER" / "HR_MANAGER " 等空格脏数据导致路由死循环
const sanitizeUser = (u) => {
  if (!u) return u
  return { ...u, role: normalizeRole(u.role) }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: sanitizeUser(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))
  }),
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
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
    updateContactInfo({ phone, email }) {
      if (!this.user) return
      this.user = { ...this.user, phone, email }
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    }
  }
})
