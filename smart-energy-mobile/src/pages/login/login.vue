<template>
  <view class="login-page">
    <view class="login-container">
      <!-- Logo & Branding (tap 5x for dev menu) -->
      <view class="brand-section">
        <view class="logo-icon" @click="handleLogoTap">
          <text class="logo-text">⚡</text>
        </view>
        <text class="app-name">智驭能效</text>
        <text class="app-subtitle">SmartEnergyMaster · 智能能源管理平台</text>
        <text v-if="tapCount > 0 && tapCount < 5" class="tap-hint">再点击 {{ 5 - tapCount }} 次进入开发者菜单</text>
      </view>

      <!-- Login Form -->
      <view class="form-section">
        <view class="input-group">
          <view class="input-icon">👤</view>
          <input
            class="form-input"
            v-model="username"
            placeholder="请输入工号 / 用户名"
            placeholder-style="color: #6b7280"
            :focus="true"
          />
        </view>

        <view class="input-group">
          <view class="input-icon">🔒</view>
          <input
            class="form-input"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            placeholder-style="color: #6b7280"
          />
        </view>

        <button class="login-btn" @click="handleLogin" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </view>

      <!-- Footer -->
      <view class="footer-section">
        <text class="footer-text">SmartEnergyMaster v1.0.0</text>
        <text class="footer-text">运维工程师 · 移动工作台</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { post } from '@/utils/request'

const username = ref('')
const password = ref('')
const loading = ref(false)

// --- Dev menu easter egg ---
const tapCount = ref(0)
let tapTimer = null

const handleLogoTap = () => {
  tapCount.value++
  if (tapTimer) clearTimeout(tapTimer)
  if (tapCount.value >= 5) {
    tapCount.value = 0
    showDevMenu()
  } else {
    tapTimer = setTimeout(() => { tapCount.value = 0 }, 3000)
  }
}

const showDevMenu = () => {
  const currentUrl = uni.getStorageSync('dev_api_url') || 'http://10.31.16.190:8080/api'
  uni.showModal({
    title: '开发者菜单',
    content: `当前 API 地址:\n${currentUrl}\n\n点击"确定"输入新的 API 地址`,
    cancelText: '取消',
    confirmText: '更换地址',
    success: (res) => {
      if (res.confirm) {
        uni.showModal({
          title: '修改 API 地址',
          content: currentUrl,
          editable: true,
          placeholderText: 'http://192.168.x.x:8080/api',
          cancelText: '重置默认',
          confirmText: '确定',
          success: (res2) => {
            if (res2.confirm && res2.content) {
              const trimmed = res2.content.trim()
              uni.setStorageSync('dev_api_url', trimmed)
              uni.showToast({ title: '已更新: ' + trimmed, icon: 'success', duration: 3000 })
            } else if (res2.cancel) {
              uni.removeStorageSync('dev_api_url')
              uni.showToast({ title: '已重置为默认地址', icon: 'success' })
            }
          },
        })
      }
    },
  })
}

const handleLogin = () => {
  if (!username.value.trim()) {
    uni.showToast({ title: '请输入工号或用户名', icon: 'none' })
    return
  }
  if (!password.value.trim()) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }

  loading.value = true

  post('/auth/login', {
    username: username.value.trim(),
    password: password.value,
  })
    .then((data) => {
      // Store the full LoginVO (matches Web: JSON.stringify(sanitized))
      uni.setStorageSync('token', data.token)
      uni.setStorageSync('userInfo', JSON.stringify(data))
      uni.switchTab({ url: '/pages/index/index' })
    })
    .catch(() => {
      // Toast already shown by request interceptor
    })
    .finally(() => {
      loading.value = false
    })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0d1117 0%, #161b22 50%, #0d1117 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
}

.login-container {
  width: 100%;
  max-width: 600rpx;
}

/* Branding */
.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 80rpx;
}

.logo-icon {
  width: 140rpx;
  height: 140rpx;
  background: linear-gradient(135deg, #f0a500, #e63946);
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 30rpx;
  box-shadow: 0 8rpx 32rpx rgba(240, 165, 0, 0.25);
}

.logo-text {
  font-size: 64rpx;
}

.app-name {
  font-size: 48rpx;
  font-weight: 700;
  color: #f0f0f0;
  letter-spacing: 8rpx;
  margin-bottom: 12rpx;
}

.app-subtitle {
  font-size: 24rpx;
  color: #8b949e;
  letter-spacing: 2rpx;
}

.tap-hint {
  font-size: 20rpx;
  color: #f0a500;
  margin-top: 16rpx;
  opacity: 0.7;
}

/* Form */
.form-section {
  margin-bottom: 60rpx;
}

.input-group {
  display: flex;
  align-items: center;
  background: #161b22;
  border: 2rpx solid #21262d;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
  padding: 0 24rpx;
  height: 96rpx;
  transition: border-color 0.3s;
}

.input-group:focus-within {
  border-color: #f0a500;
}

.input-icon {
  font-size: 36rpx;
  margin-right: 16rpx;
  width: 48rpx;
  text-align: center;
}

.form-input {
  flex: 1;
  height: 100%;
  font-size: 30rpx;
  color: #e6edf3;
  background: transparent;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: linear-gradient(135deg, #f0a500, #d48500);
  color: #0d1117;
  font-size: 34rpx;
  font-weight: 700;
  border-radius: 16rpx;
  border: none;
  letter-spacing: 16rpx;
  margin-top: 40rpx;
  transition: opacity 0.3s;
}

.login-btn:active {
  opacity: 0.85;
}

.login-btn[disabled] {
  opacity: 0.6;
}

/* Footer */
.footer-section {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.footer-text {
  font-size: 22rpx;
  color: #484f58;
  line-height: 36rpx;
}
</style>
