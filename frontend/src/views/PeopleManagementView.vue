<template>
  <div class="page-shell">
    <header class="page-header">
      <div>
        <h2 class="page-title">人员管理</h2>
        <p class="page-subtitle">统一查看账号身份、维修人员档案与排班负载</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增人员</el-button>
    </header>

    <el-card class="filter-bar" shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="账号 / 姓名 / 手机 / 邮箱" clearable
                    style="width: 220px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.role" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="[v, l] in roles" :key="v" :label="l" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="filters.department" placeholder="如：维修部" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item label="维修身份">
          <el-select v-model="filters.isMaintenance" placeholder="全部" clearable style="width: 120px">
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" class="people-table" :data="rows" stripe border>
        <!-- 账号信息 -->
        <el-table-column label="账号" prop="username" min-width="110" />
        <el-table-column label="姓名" prop="nickname" min-width="100" />
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" prop="department" min-width="100" />
        <el-table-column label="账号状态" min-width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>

        <!-- 是否维修人员标识 -->
        <el-table-column label="维修身份" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isMaintenance" type="success" size="small">✓ 维修人员</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">普通</el-tag>
          </template>
        </el-table-column>

        <!-- 档案信息（仅维修人员有） -->
        <el-table-column label="工号" prop="employeeNo" min-width="80" />
        <el-table-column label="姓名（档案）" prop="archiveName" min-width="100" />
        <el-table-column label="手机" prop="archivePhone" min-width="120" />
        <el-table-column label="技能等级" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="row.skillLevel" size="small" effect="plain">{{ row.skillLevel }}</el-tag>
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column label="专长" min-width="180">
          <template #default="{ row }">
            <span v-if="!row.specializations" class="empty-cell">—</span>
            <el-tag v-for="s in parseSkills(row.specializations)" v-else :key="s" size="small"
                     effect="plain" style="margin-right: 4px">{{ s }}</el-tag>
          </template>
        </el-table-column>

        <!-- 排班信息 -->
        <el-table-column label="在岗" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isOnDuty === true" type="success" size="small">在岗</el-tag>
            <el-tag v-else-if="row.isOnDuty === false" type="warning" size="small">离岗</el-tag>
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column label="当前/最大" min-width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.currentWorkload != null" class="value-text">{{ row.currentWorkload }} / {{ row.maxWorkload }}</span>
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column label="负载率" min-width="120">
          <template #default="{ row }">
            <el-progress v-if="row.workloadRate != null" :percentage="row.workloadRate"
                         :color="rateColor(row.workloadRate)" :stroke-width="14" />
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>

        <!-- v6.1：操作列（编辑 + 更多下拉） -->
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="onEdit(row)">编辑</el-button>
            <el-dropdown trigger="click" size="small">
              <el-button size="small" link type="primary">
                更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.isMaintenance" @click="onToggleDuty(row)">
                    切换在岗
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isBuiltInAdmin(row)" divided @click="onDelete(row)">
                    <span style="color: #f56c6c">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <!-- v6.1：编辑对话框（账号 + 维修档案/排班 合并） -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑人员' : '新增人员'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="100px" label-position="right">
        <!-- 账号信息 -->
        <fieldset class="form-section">
          <legend>账号信息</legend>
          <el-form-item label="登录账号" required>
            <el-input v-model="form.username" :disabled="!canEditUsername"
                      placeholder="如：2026030001" maxlength="10" />
            <div class="account-format-tip">{{ accountFormatHint }}</div>
          </el-form-item>
          <el-form-item label="登录密码" :required="!editingId">
            <el-input v-model="form.password" type="password" show-password
                      :placeholder="editingId ? '留空表示不修改' : '必填'" />
          </el-form-item>
          <el-form-item label="姓名" required>
            <el-input v-model="form.nickname" />
          </el-form-item>
          <el-form-item label="角色" required>
            <el-select v-model="form.role" :disabled="isEditingBuiltInAdmin" style="width: 100%">
              <el-option v-for="[v, l] in roles" :key="v" :label="l" :value="v" />
            </el-select>
            <div v-if="isEditingBuiltInAdmin" class="admin-protection-tip">
              内置管理员 {{ BUILT_IN_ADMIN_ACCOUNT }} 永远只能是系统管理员，且不能被删除。
            </div>
          </el-form-item>
          <el-form-item label="部门">
            <el-input v-model="form.department" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" />
          </el-form-item>
        </fieldset>

        <!-- 维修人员信息（仅维修角色显示） -->
        <fieldset v-if="form.role === 'MAINTENANCE_ENGINEER'" class="form-section">
          <legend>维修人员档案 / 排班</legend>
          <el-form-item label="姓名（档案）" required>
            <el-input v-model="form.archiveName" placeholder="维修人员显示名（可与昵称不同）" />
          </el-form-item>
          <el-form-item label="技能等级" required>
            <el-select v-model="form.skillLevel" clearable style="width: 100%">
              <el-option label="初级 JUNIOR" value="JUNIOR" />
              <el-option label="中级 INTERMEDIATE" value="INTERMEDIATE" />
              <el-option label="高级 SENIOR" value="SENIOR" />
              <el-option label="专家 EXPERT" value="EXPERT" />
            </el-select>
          </el-form-item>
          <el-form-item label="专长">
            <el-select v-model="form.specializationsList" multiple filterable allow-create
                       placeholder="可多选/输入新专长" style="width: 100%">
              <el-option v-for="s in skillOptions" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="证书">
            <el-input v-model="form.certification" placeholder="如：高级工程师 / 15年" />
          </el-form-item>
          <el-form-item label="手机（维修）">
            <el-input v-model="form.archivePhone" />
          </el-form-item>
          <el-form-item label="邮箱（维修）">
            <el-input v-model="form.archiveEmail" />
          </el-form-item>
          <el-form-item label="最大工作负载">
            <el-input-number v-model="form.maxWorkload" :min="1" :max="20" />
          </el-form-item>
        </fieldset>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, deleteUser, listUsersWithPersonnel, updateUser } from '../api'
import { useAuthStore } from '../stores/auth'
import { accountFormatHint, BUILT_IN_ADMIN_ACCOUNT, validateAccount } from '../utils/account'

const auth = useAuthStore()
const rows = shallowRef([])
const loading = shallowRef(false)
const filters = reactive({ keyword: '', role: '', department: '', status: '', isMaintenance: undefined })
const pagination = reactive({ page: 1, size: 10, total: 0 })

// v6.1: 编辑对话框
const dialogVisible = shallowRef(false)
const editingId = shallowRef(null)
const originalRole = shallowRef('')
const submitting = shallowRef(false)
const form = reactive({
  username: '', password: '', nickname: '', role: 'OPERATOR',
  department: '', phone: '', email: '',
  archiveName: '', skillLevel: '', specializationsList: [],
  certification: '', archivePhone: '', archiveEmail: '',
  maxWorkload: 5
})
const skillOptions = ['电气', '机械', '液压', '仪表', '自动化']
const isBuiltInAdmin = (user) => user?.username === BUILT_IN_ADMIN_ACCOUNT
const isEditingBuiltInAdmin = computed(() => form.username === BUILT_IN_ADMIN_ACCOUNT && editingId.value != null)
const canEditUsername = computed(() => !editingId.value || (auth.user?.role === 'ADMIN' && !isEditingBuiltInAdmin.value))

const resetForm = () => {
  Object.assign(form, {
    username: '', password: '', nickname: '', role: 'OPERATOR',
    department: '', phone: '', email: '',
    archiveName: '', skillLevel: '', specializationsList: [],
    certification: '', archivePhone: '', archiveEmail: '',
    maxWorkload: 5
  })
}

const openCreate = () => {
  resetForm()
  editingId.value = null
  originalRole.value = ''
  dialogVisible.value = true
}
const onEdit = (row) => {
  Object.assign(form, {
    username: row.username, password: '',
    nickname: row.nickname || row.archiveName || '',
    role: row.role, department: row.department || '',
    phone: row.phone || row.archivePhone || '',
    email: row.email || row.archiveEmail || '',
    archiveName: row.archiveName || '',
    skillLevel: row.skillLevel || '',
    specializationsList: parseSkills(row.specializations),
    certification: row.certification || '',
    archivePhone: row.archivePhone || '',
    archiveEmail: row.archiveEmail || '',
    maxWorkload: row.maxWorkload || 5
  })
  editingId.value = row.id
  originalRole.value = row.role
  dialogVisible.value = true
}
const onSubmit = async () => {
  if (!form.username) return ElMessage.warning('请输入登录账号')
  if (!editingId.value && !form.password) return ElMessage.warning('请输入密码')
  if (!form.nickname) return ElMessage.warning('请输入姓名')
  if (!form.role) return ElMessage.warning('请选择角色')
  const accountError = validateAccount(form.username, form.role)
  if (accountError) return ElMessage.warning(accountError)
  if (form.role === 'MAINTENANCE_ENGINEER' && !form.archiveName) {
    return ElMessage.warning('请输入维修人员档案姓名')
  }
  if (form.role === 'MAINTENANCE_ENGINEER' && !form.skillLevel) {
    return ElMessage.warning('请选择维修人员技能等级')
  }
  if (isEditingBuiltInAdmin.value) form.role = 'ADMIN'
  if (originalRole.value === 'MAINTENANCE_ENGINEER' && form.role !== 'MAINTENANCE_ENGINEER') {
    try {
      await ElMessageBox.confirm(
        '修改为非维修工程师后，将删除该成员的维修人员档案、排班和转派申请。确认继续？',
        '移除维修身份',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const payload = {
      username: form.username,
      password: form.password || undefined,
      nickname: form.nickname,
      role: form.role,
      department: form.department,
      phone: form.phone,
      email: form.email,
      maintenanceProfile: form.role === 'MAINTENANCE_ENGINEER' ? {
        name: form.archiveName,
        skillLevel: form.skillLevel,
        specializations: form.specializationsList,
        certification: form.certification,
        phone: form.archivePhone,
        email: form.archiveEmail,
        maxWorkload: form.maxWorkload
      } : undefined
    }
    if (editingId.value) {
      await updateUser(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createUser(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || (editingId.value ? '更新失败' : '创建失败'))
  } finally {
    submitting.value = false
  }
}
const onDelete = async (row) => {
  if (isBuiltInAdmin(row)) return ElMessage.warning(`内置管理员 ${BUILT_IN_ADMIN_ACCOUNT} 不能被删除`)
  try {
    await ElMessageBox.confirm(`确认删除 ${row.username}？此操作不可恢复`, '删除确认', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}
const onToggleDuty = (row) => {
  ElMessage.info('离岗/在岗切换请用维修人员子表（后续在 PersonnelView 接入）')
  // 暂未直接接 /api/workorder/personnel/{id}/duty，可在 PersonDetailView 中扩展
}

const roles = [
  ['ADMIN', '系统管理员'],
  ['HR_MANAGER', '人事管理员'],
  ['MANAGER', '生产经理'],
  ['OPERATOR', '操作员'],
  ['DEVICE_MANAGER', '设备管理人员'],
  ['MAINTENANCE_ENGINEER', '维修工程师']
]
const roleLabel = (r) => roles.find(([v]) => v === r)?.[1] || r
const roleTagType = (r) => {
  if (r === 'ADMIN') return 'danger'
  if (r === 'HR_MANAGER' || r === 'MANAGER') return 'warning'
  if (r === 'MAINTENANCE_ENGINEER') return 'success'
  return 'info'
}
const statusLabel = (s) => ({ ACTIVE: '启用', DISABLED: '禁用', LOCKED: '锁定' }[s] || s)
const statusTagType = (s) => ({ ACTIVE: 'success', DISABLED: 'info', LOCKED: 'danger' }[s] || 'info')

const parseSkills = (json) => {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}
const rateColor = (rate) => {
  if (rate >= 90) return '#f56c6c'
  if (rate >= 70) return '#e6a23c'
  return '#67c23a'
}

const load = async () => {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size }
    Object.keys(filters).forEach((k) => {
      if (filters[k] !== '' && filters[k] !== undefined && filters[k] !== null) params[k] = filters[k]
    })
    const result = await listUsersWithPersonnel(params)
    rows.value = result.records || []
    pagination.total = result.total || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { pagination.page = 1; load() }
const onReset = () => {
  Object.assign(filters, { keyword: '', role: '', department: '', status: '', isMaintenance: undefined })
  pagination.page = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 16px; }
.page-header { padding: 4px 4px 0; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.page-title { margin: 0; font-size: 22px; font-weight: 600; color: var(--text-primary); }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary); font-size: 13px; }
.filter-bar { background: rgba(15, 23, 42, 0.88); backdrop-filter: blur(8px); }

.people-table {
  --el-table-bg-color: rgba(15, 23, 42, 0.88);
  --el-table-tr-bg-color: rgba(15, 23, 42, 0.88);
  --el-table-header-bg-color: rgba(15, 23, 42, 0.96);
  --el-table-row-hover-bg-color: rgba(30, 41, 59, 0.96);
  --el-fill-color-lighter: rgba(30, 41, 59, 0.82);
  --el-table-border-color: rgba(148, 163, 184, 0.24);
  color: var(--text-primary);
}

:deep(.people-table .el-table__inner-wrapper),
:deep(.people-table .el-table__body-wrapper),
:deep(.people-table tr),
:deep(.people-table td.el-table__cell) {
  background-color: rgba(15, 23, 42, 0.88) !important;
  color: var(--text-primary);
}

:deep(.people-table th.el-table__cell) {
  background-color: rgba(15, 23, 42, 0.96) !important;
  color: #cbd5e1;
}

:deep(.people-table.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background-color: rgba(30, 41, 59, 0.82) !important;
}

:deep(.people-table .el-table__body tr:hover > td.el-table__cell) {
  background-color: rgba(51, 65, 85, 0.92) !important;
}

.empty-cell { color: var(--text-secondary); font-style: italic; font-weight: 400; }
.value-text { color: var(--text-primary); font-weight: 600; }
.muted { color: var(--text-secondary); }
.admin-protection-tip { margin-top: 6px; color: var(--accent-orange); font-size: 12px; }
.account-format-tip { margin-top: 6px; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
.form-section {
  border: 1px solid var(--panel-border);
  border-radius: 6px;
  padding: 16px 20px 4px;
  margin-bottom: 16px;
}
.form-section legend {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  padding: 0 8px;
}
</style>
