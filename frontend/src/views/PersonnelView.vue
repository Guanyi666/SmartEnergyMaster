<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">维修人员花名册</h2>
        <p class="page-subtitle">管理维修人员档案、技能、负载与在岗状态</p>
      </div>
      <div class="header-tools">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增人员</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar glass-panel">
      <el-input v-model="filter.name" placeholder="搜索姓名 / 工号" clearable style="width: 220px" @change="loadList" />
      <el-select v-model="filter.skillLevel" placeholder="技能等级" clearable style="width: 140px" @change="loadList">
        <el-option v-for="o in SKILL_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="filter.onDuty" placeholder="在岗状态" clearable style="width: 130px" @change="loadList">
        <el-option label="在岗" :value="true" />
        <el-option label="离岗" :value="false" />
      </el-select>
      <el-input v-model="filter.specialization" placeholder="技能 (如: 电气)" clearable style="width: 180px" @change="loadList" />
    </div>

    <!-- 人员网格 -->
    <div v-loading="loading" class="personnel-grid">
      <div v-for="p in personnel" :key="p.id" class="person-card glass-panel" :class="{ 'off-duty': !p.isOnDuty }">
        <div class="avatar" :style="{ background: avatarBg(p.avatarColor) }">
          {{ (p.name || '?').charAt(0) }}
        </div>
        <div class="info">
          <div class="name-row">
            <strong class="name">{{ p.name }}</strong>
            <span class="emp-no">{{ p.employeeNo }}</span>
          </div>
          <div class="level-row">
            <span class="level-badge" :class="`lvl-${skillLevelTone(p.skillLevel)}`">
              {{ skillLevelLabel(p.skillLevel) }}
            </span>
            <el-switch
              :model-value="p.isOnDuty"
              active-text="在岗"
              inactive-text="离岗"
              inline-prompt
              size="small"
              @change="(v) => onDutyChange(p, v)"
            />
          </div>
          <div class="skill-row">
            <span v-for="s in (p.specializations || [])" :key="s" class="skill-chip" :class="`skill-${s}`">{{ s }}</span>
          </div>
          <div class="cert" v-if="p.certification">{{ p.certification }}</div>
          <!-- 负载进度条 -->
          <div class="workload">
            <div class="workload-row">
              <span class="wl-label">负载</span>
              <span class="wl-value">{{ p.currentWorkload }} / {{ p.maxWorkload }} ({{ p.workloadRate }}%)</span>
            </div>
            <div class="wl-bar">
              <div class="wl-fill" :class="workloadClass(p.workloadRate)" :style="{ width: (p.workloadRate || 0) + '%' }" />
            </div>
          </div>
          <div class="contact" v-if="p.phone || p.email">
            <span v-if="p.phone"><el-icon><Phone /></el-icon> {{ p.phone }}</span>
            <span v-if="p.email"><el-icon><Message /></el-icon> {{ p.email }}</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button text type="primary" @click="openEdit(p)">编辑</el-button>
          <el-button text type="danger" @click="onDelete(p)">删除</el-button>
        </div>
      </div>

      <div v-if="!loading && !personnel.length" class="empty">
        暂无人员数据，点击右上角"新增人员"开始
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogOpen" :title="form.id ? '编辑人员' : '新增人员'" width="520px">
      <el-form :model="form" label-width="96px" :rules="formRules" ref="formRef">
        <el-form-item label="工号" prop="employeeNo">
          <el-input v-model="form.employeeNo" :disabled="Boolean(form.id)" placeholder="请输入工号" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="技能" prop="specializations">
          <el-select v-model="form.specializations" multiple placeholder="选择技能" style="width: 100%">
            <el-option v-for="s in KNOWN_SKILLS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="技能等级" prop="skillLevel">
          <el-select v-model="form.skillLevel" style="width: 100%">
            <el-option v-for="o in SKILL_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大负载" prop="maxWorkload">
          <el-input-number v-model="form.maxWorkload" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="证书描述">
          <el-input v-model="form.certification" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="头像颜色">
          <el-color-picker v-model="form.avatarColor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Phone, Message } from '@element-plus/icons-vue'
import { listPersonnel, createPersonnel, updatePersonnel, deletePersonnel, toggleDuty } from '../api/workorder'
import { SKILL_LEVEL_OPTIONS, skillLevelLabel, skillLevelTone } from '../utils/skillLevel'

const KNOWN_SKILLS = ['电气', '机械', '液压', '仪表', '自动化']

const loading = ref(false)
const saving = ref(false)
const personnel = ref([])

const filter = reactive({
  name: '',
  skillLevel: '',
  onDuty: '',
  specialization: ''
})

const dialogOpen = ref(false)
const formRef = ref(null)
const form = reactive(emptyForm())
function emptyForm() {
  return {
    id: null,
    employeeNo: '',
    name: '',
    specializations: [],
    skillLevel: 'JUNIOR',
    certification: '',
    maxWorkload: 5,
    phone: '',
    email: '',
    avatarColor: '#52c8ff'
  }
}

const formRules = {
  employeeNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  specializations: [{ required: true, type: 'array', min: 1, message: '至少选一个技能', trigger: 'change' }],
  skillLevel: [{ required: true, message: '请选择技能等级', trigger: 'change' }],
  maxWorkload: [{ required: true, message: '请输入最大负载', trigger: 'blur' }]
}

const avatarBg = (color) => `linear-gradient(135deg, ${color || '#52c8ff'}, #a78bfa)`

const workloadClass = (rate) => {
  if (rate == null) return ''
  if (rate >= 80) return 'danger'
  if (rate >= 60) return 'warn'
  return 'ok'
}

const loadList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: 1,
      pageSize: 100,
      ...(filter.skillLevel ? { skillLevel: filter.skillLevel } : {}),
      ...(filter.onDuty !== '' ? { onDuty: filter.onDuty } : {}),
      ...(filter.specialization ? { specialization: filter.specialization } : {})
    }
    const res = await listPersonnel(params)
    let records = res.records || []
    if (filter.name) {
      const q = filter.name.toLowerCase()
      records = records.filter(p =>
        (p.name || '').toLowerCase().includes(q) ||
        (p.employeeNo || '').toLowerCase().includes(q)
      )
    }
    personnel.value = records
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.assign(form, emptyForm())
  dialogOpen.value = true
}
const openEdit = (p) => {
  Object.assign(form, {
    id: p.id,
    employeeNo: p.employeeNo,
    name: p.name,
    specializations: [...(p.specializations || [])],
    skillLevel: p.skillLevel,
    certification: p.certification || '',
    maxWorkload: p.maxWorkload,
    phone: p.phone || '',
    email: p.email || '',
    avatarColor: p.avatarColor || '#52c8ff'
  })
  dialogOpen.value = true
}

const submit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updatePersonnel(form.id, form)
      ElMessage.success('人员已更新')
    } else {
      await createPersonnel(form)
      ElMessage.success('人员已新增')
    }
    dialogOpen.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onDelete = async (p) => {
  try {
    await ElMessageBox.confirm(
      `确定删除 ${p.name} (${p.employeeNo})？${p.currentWorkload > 0 ? '\n注意：当前仍有 ' + p.currentWorkload + ' 单在处理，删除会失败。' : ''}`,
      '删除确认',
      { type: 'warning' }
    )
    await deletePersonnel(p.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e) {
    if (e === 'cancel') return
  }
}

const onDutyChange = async (p, onDuty) => {
  try {
    await toggleDuty(p.id, onDuty)
    p.isOnDuty = onDuty
    ElMessage.success(`${p.name} 已${onDuty ? '回到在岗' : '切换为离岗'}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '切换在岗状态失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #e0f2fe;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.filter-bar {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  align-items: center;
}

.personnel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.person-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  position: relative;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.person-card:hover {
  transform: translateY(-3px);
  border-color: rgba(82, 200, 255, 0.4);
}

.person-card.off-duty {
  opacity: 0.7;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.name-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.name {
  font-size: 16px;
  color: #e0f2fe;
}

.emp-no {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 11px;
  color: rgba(82, 200, 255, 0.85);
}

.level-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.level-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  letter-spacing: 1px;
  font-weight: 600;
}
.level-badge.lvl-junior        { background: rgba(148, 163, 184, 0.2); color: #cbd5f5; }
.level-badge.lvl-intermediate  { background: rgba(82, 200, 255, 0.2); color: #52c8ff; }
.level-badge.lvl-senior        { background: rgba(255, 159, 67, 0.2); color: #ff9f43; }
.level-badge.lvl-expert        { background: rgba(167, 139, 250, 0.2); color: #a78bfa; }

.skill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.skill-chip {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 8px;
  border: 1px solid;
  font-weight: 500;
}

.skill-chip.skill-电气   { background: rgba(255, 159, 67, 0.15); border-color: rgba(255, 159, 67, 0.4); color: #ff9f43; }
.skill-chip.skill-机械   { background: rgba(82, 200, 255, 0.15); border-color: rgba(82, 200, 255, 0.4); color: #52c8ff; }
.skill-chip.skill-液压   { background: rgba(59, 255, 159, 0.15); border-color: rgba(59, 255, 159, 0.4); color: #3bff9f; }
.skill-chip.skill-仪表   { background: rgba(167, 139, 250, 0.15); border-color: rgba(167, 139, 250, 0.4); color: #a78bfa; }
.skill-chip.skill-自动化 { background: rgba(244, 114, 182, 0.15); border-color: rgba(244, 114, 182, 0.4); color: #f472b6; }

.cert {
  font-size: 11px;
  color: var(--text-secondary);
  font-style: italic;
}

.workload {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 2px;
}

.workload-row {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-secondary);
}

.wl-value {
  font-family: 'SF Mono', Consolas, monospace;
  color: #e0f2fe;
}

.wl-bar {
  height: 5px;
  background: rgba(148, 163, 184, 0.15);
  border-radius: 3px;
  overflow: hidden;
}

.wl-fill {
  height: 100%;
  transition: width 0.4s ease;
  border-radius: 3px;
}

.wl-fill.ok     { background: linear-gradient(90deg, #3bff9f, #52c8ff); }
.wl-fill.warn   { background: linear-gradient(90deg, #ff9f43, #ff5d5d); }
.wl-fill.danger { background: linear-gradient(90deg, #ff5d5d, #a78bfa); animation: pulse 2s infinite; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.65; }
}

.contact {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.contact span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.card-actions {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 4px;
}

.empty {
  grid-column: 1 / -1;
  padding: 80px 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
