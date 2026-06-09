import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 🆕 合并 workorder-backend: 所有 /api/* 统一打到 8080
      // 之前 workorder 模块单独打 8081 跨服务，现在 8081 已合并到 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
