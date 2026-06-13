<template>
  <div class="page-shell account-settings-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">账号设置</h2>
        <p class="page-subtitle">维护当前账号的联系方式与登录密码。</p>
      </div>
    </div>

    <section class="glass-panel settings-panel">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <div class="settings-section">
          <div>
            <h3>基本信息</h3>
            <p>账号不可在此修改，如需调整请联系管理员。</p>
          </div>
          <div class="form-grid">
            <el-form-item label="当前账号">
              <el-input v-model="form.username" disabled />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model.trim="form.phone" maxlength="11" placeholder="请输入手机号" clearable />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="form.email" maxlength="128" placeholder="请输入邮箱" clearable />
            </el-form-item>
          </div>
        </div>

        <el-divider />

        <div class="settings-section">
          <div>
            <h3>修改密码</h3>
            <p>不修改密码时请保持以下字段为空。修改成功后需要重新登录。</p>
          </div>
          <div class="form-grid">
            <el-form-item label="当前密码" prop="currentPassword">
              <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少 6 个字符" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
          </div>
        </div>

        <div class="settings-actions">
          <el-button type="primary" :loading="saving" @click="submit">保存设置</el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAccountSettings, updateAccountSettings } from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = shallowRef()
const saving = shallowRef(false)
const form = reactive({
  username: '',
  phone: '',
  email: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRequired = () => Boolean(form.currentPassword || form.newPassword || form.confirmPassword)
const rules = {
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }],
  currentPassword: [{
    validator: (_, value, callback) => passwordRequired() && !value ? callback(new Error('请输入当前密码')) : callback(),
    trigger: 'blur'
  }],
  newPassword: [{
    validator: (_, value, callback) => {
      if (passwordRequired() && !value) return callback(new Error('请输入新密码'))
      if (value && value.length < 6) return callback(new Error('新密码不能少于 6 个字符'))
      callback()
    },
    trigger: 'blur'
  }],
  confirmPassword: [{
    validator: (_, value, callback) => {
      if (passwordRequired() && !value) return callback(new Error('请再次输入新密码'))
      if (value !== form.newPassword) return callback(new Error('两次输入的新密码不一致'))
      callback()
    },
    trigger: 'blur'
  }]
}

const load = async () => {
  const result = await getAccountSettings()
  form.username = result.username
  form.phone = result.phone || ''
  form.email = result.email || ''
}

const submit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await updateAccountSettings({
      phone: form.phone,
      email: form.email,
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    if (result.passwordChanged) {
      auth.clearSession()
      ElMessage.success('密码修改成功，请重新登录')
      await router.replace('/login')
      return
    }
    auth.updateContactInfo(result)
    ElMessage.success('账号设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.account-settings-page {
  max-width: 1040px;
}

.settings-panel {
  padding: 28px;
}

.settings-section {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 28px;
}

.settings-section h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.settings-section p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .settings-section,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
