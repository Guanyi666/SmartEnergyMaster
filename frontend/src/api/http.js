import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 12000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('smart-energy-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.response?.data || error.message || '请求失败'
    if (error.response?.status === 401) {
      localStorage.removeItem('smart-energy-token')
      localStorage.removeItem('smart-energy-user')
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    ElMessage.error(String(message))
    return Promise.reject(error)
  }
)

export default request
