<template>
  <div class="login-shell">
    <div class="login-card glass-panel">
      <div class="login-head">
        <p class="login-mark">SMART ENERGY COMMAND</p>
        <h1>智驭能效平台</h1>
        <span>工业现场实时监控与工单闭环中心</span>
      </div>

      <el-segmented v-model="mode" class="mode-switch" :options="modeOptions" block />

      <el-form v-if="mode === 'login'" :model="loginForm" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="loginForm.username" size="large" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" size="large" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
          登录指挥中心
        </el-button>
      </el-form>

      <el-form v-else :model="registerForm" @submit.prevent="handleRegister">
        <el-form-item>
          <el-input v-model="registerForm.username" size="large" placeholder="请输入新账号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="registerForm.password" size="large" type="password" show-password placeholder="请输入密码（至少 6 位）" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="registerForm.email" size="large" placeholder="请输入邮箱（可选）" />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleRegister">
          创建操作员账号
        </el-button>
      </el-form>

      <div class="login-tip">
        <span>演示账号：admin</span>
        <span>密码：admin123</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const mode = ref('login')

const modeOptions = [
  { label: '登录', value: 'login' },
  { label: '注册', value: 'register' }
]

const loginForm = reactive({
  username: 'admin',
  password: 'admin123'
})

const registerForm = reactive({
  username: '',
  password: '',
  email: ''
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
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.username || !registerForm.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码长度至少为 6 位')
    return
  }

  loading.value = true
  try {
    await authStore.register(registerForm)
    ElMessage.success('注册成功，请登录')
    loginForm.username = registerForm.username
    loginForm.password = registerForm.password
    registerForm.username = ''
    registerForm.password = ''
    registerForm.email = ''
    mode.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.login-card {
  width: min(460px, 100%);
  padding: 36px;
}

.login-head {
  margin-bottom: 24px;
}

.login-mark,
.login-head span,
.login-tip {
  color: var(--text-secondary);
}

.login-mark {
  margin: 0 0 12px;
  letter-spacing: 4px;
  font-size: 12px;
}

.login-head h1 {
  margin: 0 0 10px;
  font-size: 34px;
}

.mode-switch {
  margin-bottom: 18px;
  width: 100%;
}

.login-btn {
  width: 100%;
  margin-top: 10px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(90deg, #1d4ed8, #06b6d4);
  border: none;
}

.login-tip {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
  font-size: 13px;
}
</style>
