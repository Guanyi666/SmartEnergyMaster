import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearStoredSession, getSessionToken } from '../utils/session'

const request = axios.create({
  baseURL: '/api',
  timeout: 12000
})

request.interceptors.request.use((config) => {
  const token = getSessionToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.response?.data || error.message || 'Request failed'
    if (error.response?.status === 401) {
      clearStoredSession()
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    ElMessage.error(String(message))
    return Promise.reject(error)
  }
)

export default request
