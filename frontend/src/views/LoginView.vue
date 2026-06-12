<template>
  <div class="login-shell">
    <div class="login-card glass-panel">
      <div class="login-head">
        <p class="login-mark">智能能源指挥中心</p>
        <h1>智驭能效平台</h1>
        <span>工业现场实时监控与工单闭环中心</span>
      </div>

      <el-form :model="loginForm" @submit.prevent="handleLogin">
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

      <div class="login-tip">
        <span>可使用管理员账号或维修工程师账号体验对应功能</span>
        <span>账号由人事管理员或系统管理员创建</span>
        <span>默认账号与密码请查阅部署使用说明</span>
      </div>
    </div>
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
    // ★ 登录后按角色 redirect：MAINTENANCE_ENGINEER 进维修中心
    router.push(defaultHomeForRole(authStore.user?.role))
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
  flex-direction: column;
  gap: 4px;
  text-align: center;
}
</style>
