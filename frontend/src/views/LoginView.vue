<template>
  <div class="login-shell">
    <div class="login-decoration login-decoration--tl"></div>
    <div class="login-decoration login-decoration--br"></div>

    <div class="login-card industrial-panel">
      <div class="login-head">
        <div class="brand-block">
          <div class="brand-icon">
            <span class="brand-icon-glow"></span>
          </div>
          <div class="brand-text">
            <p class="login-mark">SMART ENERGY · CONTROL CENTER</p>
            <h1>智驭能效</h1>
          </div>
        </div>
        <span class="login-tag">工业能效指挥中心 / INDUSTRIAL CONTROL</span>
      </div>

      <div class="login-form">
        <p class="login-section-title">身份验证 / IDENTITY VERIFICATION</p>
        <el-form :model="loginForm" @submit.prevent="handleLogin">
          <el-form-item>
            <label class="login-label">账号 / USERNAME</label>
            <el-input v-model="loginForm.username" size="large" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item>
            <label class="login-label">密码 / PASSWORD</label>
            <el-input v-model="loginForm.password" size="large" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
            <span v-if="!loading">登录指挥中心 / ENTER CONTROL CENTER</span>
            <span v-else>登录中...</span>
          </el-button>
        </el-form>
      </div>

      <div class="login-tip">
        <p class="login-tip-row">
          <span class="login-tip-dot login-tip-dot--green"></span>
          可使用管理员账号或维修工程师账号体验对应功能
        </p>
        <p class="login-tip-row">
          <span class="login-tip-dot login-tip-dot--cyan"></span>
          账号由人事管理员或系统管理员创建
        </p>
        <p class="login-tip-row">
          <span class="login-tip-dot login-tip-dot--orange"></span>
          默认账号与密码请查阅部署使用说明
        </p>
      </div>
    </div>

    <div class="brand-watermark">智驭能效 · SMART ENERGY MASTER</div>
  </div>
</template>

<script setup>
import { reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { defaultHomeForRole } from '../utils/role'

const router = useRouter()
const authStore = useAuthStore()
const loading = shallowRef(false)

const loginForm = reactive({
  username: '2026010001',
  password: 'admin123'
})

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入完整账号密码')
    return
  }

  loading.value = true
  try {
    await authStore.login(loginForm)
    ElMessage.success('登录成功')
    router.push(defaultHomeForRole(authStore.user?.role))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  overflow: hidden;
}

.login-decoration {
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
  opacity: 0.5;
}

.login-decoration--tl {
  top: -120px;
  left: -120px;
  background: radial-gradient(circle, rgba(61, 169, 255, 0.5), transparent 70%);
}

.login-decoration--br {
  bottom: -160px;
  right: -160px;
  background: radial-gradient(circle, rgba(255, 126, 0, 0.4), transparent 70%);
}

.login-card {
  position: relative;
  width: min(480px, 100%);
  padding: 40px 36px;
  animation: panel-fade-in 0.5s ease-out;
}

.login-head {
  margin-bottom: 28px;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}

.brand-icon {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
  box-shadow: 0 0 24px rgba(92, 220, 255, 0.5);
  display: grid;
  place-items: center;
}

.brand-icon::after {
  content: '⚡';
  font-size: 30px;
  color: #fff;
  text-shadow: 0 0 10px rgba(255, 255, 255, 0.6);
}

.brand-icon-glow {
  position: absolute;
  inset: -10px;
  border-radius: 22px;
  background: radial-gradient(circle, rgba(92, 220, 255, 0.5), transparent 70%);
  z-index: -1;
  animation: brand-pulse 3s ease-in-out infinite;
}

@keyframes brand-pulse {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

.brand-text h1 {
  margin: 4px 0 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 6px;
  background: linear-gradient(90deg, #ffffff, var(--accent-cyan));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.login-mark {
  margin: 0;
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--accent-cyan);
  font-weight: 600;
}

.login-tag {
  display: inline-block;
  padding: 4px 12px;
  background: rgba(92, 220, 255, 0.1);
  border: 1px solid rgba(92, 220, 255, 0.3);
  border-radius: 999px;
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--text-secondary);
}

.login-section-title {
  margin: 0 0 16px;
  font-size: 12px;
  letter-spacing: 3px;
  color: var(--accent-cyan);
  font-weight: 600;
  padding-left: 10px;
  border-left: 3px solid var(--accent-cyan);
}

.login-label {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--text-secondary);
  font-weight: 600;
}

.login-form {
  margin-bottom: 24px;
}

.login-btn {
  width: 100%;
  margin-top: 12px;
  height: 50px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 3px;
  background: linear-gradient(90deg, var(--accent-cyan), var(--accent-blue)) !important;
  border: none !important;
  box-shadow: 0 6px 20px rgba(92, 220, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 28px rgba(92, 220, 255, 0.5);
}

.login-tip {
  margin-top: 20px;
  padding: 14px 16px;
  background: rgba(13, 37, 64, 0.4);
  border: 1px solid rgba(92, 220, 255, 0.12);
  border-radius: 10px;
}

.login-tip-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0;
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 1px;
}

.login-tip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;
}

.login-tip-dot--green {
  background: var(--accent-green);
  color: var(--accent-green);
}

.login-tip-dot--cyan {
  background: var(--accent-cyan);
  color: var(--accent-cyan);
}

.login-tip-dot--orange {
  background: var(--accent-orange);
  color: var(--accent-orange);
}
</style>