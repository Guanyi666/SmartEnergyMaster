<template>
  <view class="login-page">
    <view class="login-container">
      <!-- Logo & Branding -->
      <view class="brand-section">
        <view class="logo-icon">
          <text class="logo-text">⚡</text>
        </view>
        <text class="app-name">智驭能效</text>
        <text class="app-subtitle">SmartEnergyMaster · 智能能源管理平台</text>
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
