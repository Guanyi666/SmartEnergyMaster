<template>
  <view class="settings-page">
    <!-- Nav -->
    <view class="nav-bar">
      <view class="nav-left" @click="goBack">
        <text class="nav-back">‹</text>
        <text class="nav-title">账号设置</text>
      </view>
    </view>

    <scroll-view scroll-y class="settings-scroll" v-if="!loading">
      <!-- Account Info -->
      <view class="card">
        <text class="card-title">账号信息</text>
        <view class="form-item">
          <text class="form-label">账号 / 工号</text>
          <input class="form-input disabled" :value="form.username" disabled />
        </view>
        <view class="form-item">
          <text class="form-label">手机号</text>
          <input class="form-input" v-model="form.phone" placeholder="请输入手机号" placeholder-style="color: #484f58" type="number" maxlength="11" />
        </view>
        <view class="form-item">
          <text class="form-label">邮箱</text>
          <input class="form-input" v-model="form.email" placeholder="请输入邮箱" placeholder-style="color: #484f58" />
        </view>
        <button class="save-btn" @click="handleSaveInfo" :disabled="saveLoading">
          {{ saveLoading ? '保存中...' : '保存设置' }}
        </button>
      </view>

      <!-- Change Password -->
      <view class="card">
        <text class="card-title">修改密码</text>
        <view class="form-item">
          <text class="form-label">当前密码</text>
          <input class="form-input" v-model="pwdForm.currentPassword" type="password" placeholder="请输入当前密码" placeholder-style="color: #484f58" />
        </view>
        <view class="form-item">
          <text class="form-label">新密码</text>
          <input class="form-input" v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码" placeholder-style="color: #484f58" />
        </view>
        <view class="form-item">
          <text class="form-label">确认密码</text>
          <input class="form-input" v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" placeholder-style="color: #484f58" />
        </view>
        <button class="pwd-btn" @click="handleChangePassword" :disabled="pwdLoading">
          {{ pwdLoading ? '处理中...' : '修改密码' }}
        </button>
        <text v-if="pwdSuccess" class="pwd-success">密码修改成功，请重新登录</text>
      </view>

      <view class="safe-bottom"></view>
    </scroll-view>

    <view v-else class="loading-wrap">
      <text class="loading-text">加载中...</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { get, put } from '@/utils/request'

const loading = ref(true)
const saveLoading = ref(false)
const pwdLoading = ref(false)
const pwdSuccess = ref(false)

const form = ref({
  username: '',
  phone: '',
  email: '',
})

const pwdForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

// --- Fetch settings ---
const fetchSettings = () => {
  loading.value = true
  get('/account-settings')
    .then((data) => {
      form.value.username = data.username || ''
      form.value.phone = data.phone || ''
      form.value.email = data.email || ''
    })
    .catch(() => {
      uni.showToast({ title: '加载设置失败', icon: 'none' })
    })
    .finally(() => { loading.value = false })
}

fetchSettings()

// --- Save settings ---
const handleSaveInfo = () => {
  saveLoading.value = true
  const payload = {}
  if (form.value.phone) payload.phone = form.value.phone
  if (form.value.email) payload.email = form.value.email
  put('/account-settings', payload)
    .then(() => {
      uni.showToast({ title: '设置已保存', icon: 'success' })
    })
    .catch(() => {})
    .finally(() => { saveLoading.value = false })
}

// --- Change password ---
const handleChangePassword = () => {
  const cp = pwdForm.value.currentPassword
  const np = pwdForm.value.newPassword
  const rp = pwdForm.value.confirmPassword
  if (!cp) { uni.showToast({ title: '请输入当前密码', icon: 'none' }); return }
  if (!np) { uni.showToast({ title: '请输入新密码', icon: 'none' }); return }
  if (np !== rp) { uni.showToast({ title: '两次密码输入不一致', icon: 'none' }); return }

  pwdLoading.value = true
  const token = uni.getStorageSync('token')
  const payload = {
    currentPassword: cp,
    newPassword: np,
    phone: form.value.phone,
    email: form.value.email,
  }
  // Put with Authorization header (request.js already handles this)
  put('/account-settings', payload)
    .then((data) => {
      if (data?.passwordChanged) {
        pwdSuccess.value = true
        uni.showToast({ title: '密码已修改，请重新登录', icon: 'success' })
        // Clear session and redirect to login (matches backend behavior)
        setTimeout(() => {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.reLaunch({ url: '/pages/login/login' })
        }, 1500)
      }
    })
    .catch(() => {})
    .finally(() => { pwdLoading.value = false })
}

const goBack = () => { uni.navigateBack() }
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: #0d1117;
}

/* Nav */
.nav-bar {
  display: flex; align-items: center;
  padding: 0 24rpx; height: 88rpx;
  background: #161b22; border-bottom: 1rpx solid #21262d;
}
.nav-left { display: flex; align-items: center; }
.nav-back { font-size: 48rpx; color: #8b949e; margin-right: 8rpx; line-height: 1; }
.nav-title { font-size: 30rpx; font-weight: 600; color: #e6edf3; }

/* Scroll */
.settings-scroll { padding: 24rpx 32rpx; }

/* Card */
.card {
  background: #161b22; border: 1rpx solid #21262d;
  border-radius: 20rpx; padding: 28rpx; margin-bottom: 24rpx;
}
.card-title {
  font-size: 26rpx; font-weight: 700; color: #f0a500;
  display: block; margin-bottom: 20rpx;
}

/* Form */
.form-item { margin-bottom: 20rpx; }
.form-label {
  font-size: 24rpx; color: #8b949e;
  display: block; margin-bottom: 10rpx;
}
.form-input {
  width: 100%; height: 80rpx;
  background: #0d1117; border: 1rpx solid #21262d;
  border-radius: 12rpx; padding: 0 20rpx;
  font-size: 26rpx; color: #e6edf3; box-sizing: border-box;
}
.form-input.disabled {
  color: #6b7280; background: #0a0d12;
}

/* Buttons */
.save-btn {
  width: 100%; height: 80rpx; line-height: 80rpx;
  background: linear-gradient(135deg,#2ec4b6,#1a9e92);
  color: #fff; font-size: 28rpx; font-weight: 700;
  border-radius: 14rpx; border: none; margin-top: 8rpx;
}
.save-btn[disabled] { opacity: 0.6; }

.pwd-btn {
  width: 100%; height: 80rpx; line-height: 80rpx;
  background: linear-gradient(135deg,#f0a500,#d48500);
  color: #0d1117; font-size: 28rpx; font-weight: 700;
  border-radius: 14rpx; border: none; margin-top: 8rpx;
}
.pwd-btn[disabled] { opacity: 0.6; }

.pwd-success {
  display: block; text-align: center; margin-top: 16rpx;
  font-size: 24rpx; color: #2ec4b6;
}

/* Loading */
.loading-wrap { flex: 1; display: flex; align-items: center; justify-content: center; padding-top: 200rpx; }
.loading-text { font-size: 28rpx; color: #8b949e; }

.safe-bottom { height: 60rpx; }
</style>
