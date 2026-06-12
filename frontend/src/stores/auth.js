import { defineStore } from 'pinia'
import { loginApi, logoutApi, registerApi } from '../api'

const TOKEN_KEY = 'smart-energy-token'
const USER_KEY = 'smart-energy-user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  actions: {
    async login(payload) {
      const result = await loginApi(payload)
      this.token = result.token
      this.user = result
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(USER_KEY, JSON.stringify(result))
      return result
    },
    async register(payload) {
      return registerApi(payload)
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
    }
  }
})
