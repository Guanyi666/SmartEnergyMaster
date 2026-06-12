<script setup>
import { onMounted, reactive, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, deleteUser, listUsers, updateUser, updateUserStatus } from '../api'
import { accountFormatHint, validateAccount } from '../utils/account'

const users = shallowRef([])
const loading = shallowRef(false)
const dialogVisible = shallowRef(false)
const editingId = shallowRef(null)
const filters = reactive({ keyword: '', role: '', department: '', status: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const form = reactive({ username: '', password: '', nickname: '', role: 'OPERATOR', department: '', phone: '', email: '' })
const roles = [
  ['ADMIN', '系统管理员'], ['HR_MANAGER', '人事管理员'], ['MANAGER', '生产经理'],
  ['OPERATOR', '操作员'], ['DEVICE_MANAGER', '设备管理人员'], ['MAINTENANCE_ENGINEER', '维修工程师']
]
const departments = ['总控室', '生产管理部', '人事部', '炼钢设备科', '公辅设备科', '动力设备科', '连铸设备科', '环保设备科', '维修部']

const load = async () => {
  loading.value = true
  try {
    const result = await listUsers({ ...filters, page: pagination.page, size: pagination.size })
    users.value = result.records || []
    pagination.total = result.total || 0
  } finally {
    loading.value = false
  }
}
const resetForm = () => Object.assign(form, { username: '', password: '', nickname: '', role: 'OPERATOR', department: '', phone: '', email: '' })
const openCreate = () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}
const openEdit = (row) => {
  editingId.value = row.id
  Object.assign(form, { ...row, password: '' })
  dialogVisible.value = true
}
const submit = async () => {
  if (!form.username || !form.role || (!editingId.value && !form.password)) {
    ElMessage.warning('请填写用户名、角色和初始密码')
    return
  }
  const accountError = validateAccount(form.username, form.role)
  if (accountError) return ElMessage.warning(accountError)
  editingId.value ? await updateUser(editingId.value, form) : await createUser(form)
  ElMessage.success(editingId.value ? '用户资料已更新' : '新人账号已创建并分配身份')
  dialogVisible.value = false
  await load()
}
const toggleStatus = async (row, active) => {
  await updateUserStatus(row.id, active ? 'ACTIVE' : 'DISABLED')
  ElMessage.success(active ? '账号已启用' : '账号已禁用')
  await load()
}
const remove = async (row) => {
  await ElMessageBox.confirm(`确认将 ${row.nickname || row.username} 从系统中移除？`, '离职人员移除', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('人员已移除')
  await load()
}
const roleLabel = (value) => roles.find(([key]) => key === value)?.[1] || value
const search = () => { pagination.page = 1; load() }
onMounted(load)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">人员与身份管理</h2>
        <p class="page-subtitle">查看部门和身份，为新人分配岗位，并处理离职账号。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建人员</el-button>
    </div>

    <section class="glass-panel filter-panel">
      <el-input v-model="filters.keyword" placeholder="搜索姓名 / 账号 / 手机 / 邮箱" clearable @keyup.enter="search" />
      <el-select v-model="filters.role" placeholder="按角色筛选" clearable>
        <el-option v-for="[value, label] in roles" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="filters.department" placeholder="按部门筛选" clearable>
        <el-option v-for="item in departments" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="filters.status" placeholder="账号状态" clearable>
        <el-option label="已启用" value="ACTIVE" />
        <el-option label="已禁用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </section>

    <section class="glass-panel table-panel">
      <el-table v-loading="loading" :data="users">
        <el-table-column prop="nickname" label="姓名" min-width="100" />
        <el-table-column prop="username" label="账号" min-width="110" />
        <el-table-column label="身份" min-width="130"><template #default="{ row }">{{ roleLabel(row.role) }}</template></el-table-column>
        <el-table-column prop="department" label="部门" min-width="130" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="170" />
        <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch :model-value="row.status !== 'DISABLED'" @change="toggleStatus(row, $event)" /></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size" :total="pagination.total" layout="total, sizes, prev, pager, next" @change="load" />
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑人员资料' : '新人入职建档'" width="600px">
      <el-form label-width="88px">
        <el-form-item label="账号"><el-input v-model="form.username" maxlength="10" :placeholder="accountFormatHint" /></el-form-item>
        <el-form-item :label="editingId ? '重置密码' : '初始密码'"><el-input v-model="form.password" type="password" show-password :placeholder="editingId ? '留空则不修改' : '请输入初始密码'" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="身份"><el-select v-model="form.role" style="width:100%"><el-option v-for="[value, label] in roles" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="部门"><el-select v-model="form.department" filterable allow-create style="width:100%"><el-option v-for="item in departments" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-panel {
  display: grid;
  grid-template-columns: 1.5fr repeat(3, 1fr) auto;
  gap: 12px;
  padding: 16px;
  margin-bottom: 18px;
}
.table-panel { padding: 20px; display: grid; gap: 16px; }
.table-panel :deep(.el-pagination) { justify-content: flex-end; }
</style>
