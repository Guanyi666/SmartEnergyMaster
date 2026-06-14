import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    // v6.2 修复：vite 5.x 默认 manualChunks 会产出多个 index-*.js，
    // index.html 引用的是 vendor chunk（不是主入口），浏览器跑不起来。
    // 强制 inlineDynamicImports + 自定义 entryFileNames 让主入口是 index.js
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {
        manualChunks: undefined,
        entryFileNames: 'assets/index.js',
        chunkFileNames: 'assets/chunk-[name].js'
      }
    }
  },
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
