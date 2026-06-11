<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="560px"
    :with-header="false"
    :destroy-on-close="false"
    custom-class="wo-detail-drawer"
  >
    <div v-if="order" class="drawer-content">
      <!-- 头部 -->
      <div class="drawer-head">
        <div>
          <p class="head-mark">工单详情</p>
          <h2 class="head-title">
            <span class="wo-no">{{ order.orderNo }}</span>
            <span class="prio-chip" :class="['tone-' + (order.priority || 'high').toLowerCase()]">
              {{ priorityLabel(order.priority) }}
            </span>
          </h2>
        </div>
        <el-button text circle @click="close">
          <el-icon :size="20"><Close /></el-icon>
        </el-button>
      </div>

      <!-- 状态色条 -->
      <div class="status-bar" :class="`status-${(order.status || '').toLowerCase()}`">
        <el-icon :size="18">
          <component :is="statusIcon(order.status)" />
        </el-icon>
        <span>{{ statusLabel(order.status) }}</span>
      </div>

      <!-- 🆕 多人指派列表 -->
      <section class="block">
        <h3 class="block-title">
          <el-icon><User /></el-icon>
          指派人（{{ activeList.length }}）
          <el-button v-if="order.status !== 'RESOLVED'"
                     text type="primary" size="small"
                     style="margin-left: auto; padding: 0 6px;"
                     @click="openBatchDialog">
            <el-icon><Plus /></el-icon> 批量指派
          </el-button>
        </h3>

        <div v-if="activeList.length" class="assignment-list">
          <div v-for="a in activeList" :key="a.personnelId" class="assignment-row">
            <div class="avatar" :style="{ background: avatarBg(a.avatarColor) }">
              {{ (a.name || '?').charAt(0) }}
            </div>
            <div class="info">
              <strong>{{ a.name }}</strong>
              <span class="emp-no">{{ a.employeeNo }}</span>
              <span class="role-tag" :class="`role-${(a.role || 'PRIMARY').toLowerCase()}`">
                {{ getRoleLabel(a.role) }}
              </span>
            </div>
            <div class="row-actions" v-if="order.status !== 'RESOLVED'">
              <el-button text type="primary" size="small" @click="openReplaceDialog(a)">
                <el-icon><Refresh /></el-icon> 替换
              </el-button>
              <el-button text type="danger" size="small" @click="confirmRelease(a)">
                <el-icon><Close /></el-icon> 释放
              </el-button>
            </div>
          </div>
        </div>
        <div v-else class="empty-tip">尚未指派任何维修人员</div>
      </section>

      <!-- 设备快照 -->
      <section class="block">
        <h3 class="block-title"><el-icon><Cpu /></el-icon>设备快照</h3>
        <div class="device-row">
          <span class="device-name">{{ order.deviceName || '—' }}</span>
          <span class="device-code">{{ order.deviceCode }}</span>
          <span v-if="order.deviceType" class="device-type">{{ getDeviceTypeLabel(order.deviceType) }}</span>
          <span v-if="order.deviceLocation" class="device-loc">
            <el-icon><Location /></el-icon> {{ order.deviceLocation }}
          </span>
        </div>
      </section>

      <!-- 触发时刻传感器数据 -->
      <section class="block">
        <h3 class="block-title"><el-icon><DataLine /></el-icon>触发时刻传感器</h3>
        <div class="metric-grid">
          <div class="metric-cell">
            <span class="metric-label">温度</span>
            <span class="metric-value">{{ formatNum(order.latestTemperature) }}<i>℃</i></span>
          </div>
          <div class="metric-cell">
            <span class="metric-label">压力</span>
            <span class="metric-value">{{ formatNum(order.latestPressure) }}<i>kPa</i></span>
          </div>
          <div class="metric-cell">
            <span class="metric-label">振动</span>
            <span class="metric-value">{{ formatNum(order.latestVibration) }}<i>mm/s</i></span>
          </div>
        </div>
      </section>

      <!-- 时间线 -->
      <section class="block">
        <h3 class="block-title"><el-icon><Clock /></el-icon>时间线</h3>
        <ul class="timeline">
          <li>
            <span class="dot" />
            <div>
              <strong>工单创建</strong>
              <span class="time">{{ formatTime(order.createdAt) }}</span>
            </div>
          </li>
          <li v-if="order.sourceTime">
            <span class="dot" />
            <div>
              <strong>故障源时间</strong>
              <span class="time">{{ formatTime(order.sourceTime) }}</span>
            </div>
          </li>
          <li v-if="order.acceptedAt">
            <span class="dot success" />
            <div>
              <strong>已接受处理</strong>
              <span class="time">{{ formatTime(order.acceptedAt) }}</span>
            </div>
          </li>
          <li v-if="order.resolvedAt">
            <span class="dot success" />
            <div>
              <strong>已闭环</strong>
              <span class="time">{{ formatTime(order.resolvedAt) }}</span>
            </div>
          </li>
        </ul>
      </section>

      <!-- 故障描述 -->
      <section v-if="order.description" class="block">
        <h3 class="block-title"><el-icon><InfoFilled /></el-icon>故障描述</h3>
        <p class="desc">{{ order.description }}</p>
      </section>

      <!-- 操作按钮 -->
      <div class="drawer-actions">
        <el-button
          v-if="order.status === 'PENDING'"
          type="primary"
          size="large"
          @click="$emit('confirm', order)"
        >
          <el-icon><Check /></el-icon> 确认处理
        </el-button>
        <el-button
          v-if="order.status !== 'RESOLVED'"
          type="success"
          size="large"
          @click="$emit('resolve', order)"
        >
          <el-icon><CircleCheck /></el-icon> 已修复
        </el-button>
      </div>
    </div>

    <!-- 🆕 批量指派 Dialog -->
    <el-dialog v-model="batchDialogOpen" title="批量指派" width="640px" append-to-body>
      <div class="batch-toolbar">
        <el-input v-model="batchSearch" placeholder="搜索姓名/工号" clearable size="small" style="width: 200px" />
        <span class="batch-count">已选 {{ selectedIds.size }} 人</span>
        <el-button text size="small" @click="toggleSelectAll" style="margin-left: auto">
          {{ isAllSelected ? '取消全选' : '全选当前' }}
        </el-button>
      </div>
      <div class="batch-grid">
        <div
          v-for="p in filteredPersonnel"
          :key="p.id"
          class="batch-cell"
          :class="{
            selected: selectedIds.has(p.id),
            disabled: isAlreadyAssigned(p.id) || !p.isOnDuty
          }"
          @click="togglePerson(p)"
        >
          <div class="avatar" :style="{ background: avatarBg(p.avatarColor) }">{{ (p.name || '?').charAt(0) }}</div>
          <div class="cell-info">
            <strong>{{ p.name }}</strong>
            <span class="emp-no">{{ p.employeeNo }} · {{ skillLevelLabel(p.skillLevel) }}</span>
            <div class="cell-skills">
              <span v-for="s in (p.specializations || [])" :key="s" class="skill-chip" :class="`skill-${s}`">{{ s }}</span>
            </div>
            <span v-if="!p.isOnDuty" class="muted-tag">离岗</span>
            <span v-else-if="isAlreadyAssigned(p.id)" class="muted-tag">已指派</span>
            <span v-else class="muted-tag">负载 {{ p.currentWorkload }}/{{ p.maxWorkload }}</span>
          </div>
          <el-checkbox :model-value="selectedIds.has(p.id)" disabled class="batch-check" />
        </div>
      </div>
      <template #footer>
        <el-button @click="batchDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" :disabled="selectedIds.size === 0" @click="submitBatch">
          指派 {{ selectedIds.size }} 人
        </el-button>
      </template>
    </el-dialog>

    <!-- 🆕 替换 Dialog -->
    <el-dialog v-model="replaceDialogOpen" :title="`替换 ${replacingTarget?.name || ''}`" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="当前人员">
          <el-tag>{{ replacingTarget?.name }} ({{ replacingTarget?.employeeNo }})</el-tag>
        </el-form-item>
        <el-form-item label="新人员">
          <el-select v-model="newPersonnelId" placeholder="选择替换上去的人" filterable style="width: 100%">
            <el-option
              v-for="p in replaceCandidates"
              :key="p.id"
              :label="`${p.name} (${p.employeeNo})`"
              :value="p.id"
              :disabled="!p.isOnDuty || p.currentWorkload >= p.maxWorkload"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="replaceNote" placeholder="（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replaceDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="replaceSubmitting" :disabled="!newPersonnelId" @click="submitReplace">
          确认替换
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Cpu, Location, DataLine, Clock, InfoFilled, Check, CircleCheck, Refresh, Warning, Loading, SuccessFilled, User, Plus } from '@element-plus/icons-vue'
import { listPersonnel, releaseOneAssignment, replaceAssignment, batchAssignWorkOrder } from '../api/workorder'
import { skillLevelLabel } from '../utils/skillLevel'
import { getPriorityMeta, getFaultTypeMeta, getRoleLabel, getDeviceTypeLabel } from '../utils/status'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'resolve', 'refresh'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const close = () => emit('update:modelValue', false)

const priorityLabel = (p) => getPriorityMeta(p).label

const STATUS_LABELS = { PENDING: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已完成' }
const statusLabel = (s) => STATUS_LABELS[s] || s || '—'

const STATUS_ICONS = { PENDING: Warning, IN_PROGRESS: Loading, RESOLVED: SuccessFilled }
const statusIcon = (s) => STATUS_ICONS[s] || Warning

const formatNum = (v) => (v == null ? '—' : Number(v).toFixed(1))

const formatTime = (iso) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}

const avatarBg = (color) => {
  if (!color) return 'linear-gradient(135deg, #52c8ff, #a78bfa)'
  return `linear-gradient(135deg, ${color}, #a78bfa)`
}

// 指派人列表（兼容老字段 assigneeName）
const activeList = computed(() => {
  if (Array.isArray(props.order?.activeAssignments) && props.order.activeAssignments.length > 0) {
    return props.order.activeAssignments
  }
  if (props.order?.assigneeName) {
    return [{
      personnelId: props.order.assigneeId,
      name: props.order.assigneeName,
      employeeNo: '',
      avatarColor: null,
      role: 'PRIMARY'
    }]
  }
  return []
})

// ===== 释放单条 =====
const confirmRelease = async (a) => {
  try {
    const isLast = activeList.value.length === 1
    await ElMessageBox.confirm(
      isLast
        ? `${a.name} 是该工单最后一名指派人，释放后工单将回到待处理状态。确认撤下？`
        : `确定将 ${a.name} 从工单 ${props.order.orderNo} 上撤下？`,
      '释放指派',
      {
        type: isLast ? 'error' : 'warning',
        confirmButtonText: '确认释放',
        cancelButtonText: '取消'
      }
    )
    await releaseOneAssignment(props.order.id, a.personnelId)
    ElMessage.success(`${a.name} 已撤下`)
    emit('refresh')
  } catch (e) {
    if (e === 'cancel') return
  }
}

// ===== 批量指派 =====
const batchDialogOpen = ref(false)
const batchSearch = ref('')
const selectedIds = ref(new Set())
const batchSubmitting = ref(false)
const allPersonnel = ref([])

const openBatchDialog = async () => {
  batchDialogOpen.value = true
  batchSearch.value = ''
  selectedIds.value = new Set()
  if (allPersonnel.value.length === 0) {
    const res = await listPersonnel({ pageNum: 1, pageSize: 100, onDuty: true })
    allPersonnel.value = res.records || []
  }
}

const isAlreadyAssigned = (pid) => activeList.value.some(a => a.personnelId === pid)

const filteredPersonnel = computed(() => {
  const q = batchSearch.value.toLowerCase().trim()
  if (!q) return allPersonnel.value
  return allPersonnel.value.filter(p =>
    (p.name || '').toLowerCase().includes(q) ||
    (p.employeeNo || '').toLowerCase().includes(q)
  )
})

const isAllSelected = computed(() => {
  const list = filteredPersonnel.value.filter(p => !isAlreadyAssigned(p.id) && p.isOnDuty)
  return list.length > 0 && list.every(p => selectedIds.value.has(p.id))
})

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedIds.value = new Set()
  } else {
    const next = new Set(selectedIds.value)
    filteredPersonnel.value
      .filter(p => !isAlreadyAssigned(p.id) && p.isOnDuty)
      .forEach(p => next.add(p.id))
    selectedIds.value = next
  }
}

const togglePerson = (p) => {
  if (isAlreadyAssigned(p.id) || !p.isOnDuty) return
  const next = new Set(selectedIds.value)
  if (next.has(p.id)) next.delete(p.id)
  else next.add(p.id)
  selectedIds.value = next
}

const submitBatch = async () => {
  if (selectedIds.value.size === 0) return
  batchSubmitting.value = true
  try {
    await batchAssignWorkOrder(props.order.id, {
      personnelIds: Array.from(selectedIds.value),
      role: 'PRIMARY',
      note: ''
    })
    ElMessage.success(`已批量指派 ${selectedIds.value.size} 人`)
    batchDialogOpen.value = false
    emit('refresh')
  } catch (e) {
    // http.js 已 toast
  } finally {
    batchSubmitting.value = false
  }
}

// ===== 替换 =====
const replaceDialogOpen = ref(false)
const replacingTarget = ref(null)
const newPersonnelId = ref(null)
const replaceNote = ref('')
const replaceSubmitting = ref(false)
const replaceCandidates = ref([])

const openReplaceDialog = async (target) => {
  replacingTarget.value = target
  newPersonnelId.value = null
  replaceNote.value = ''
  replaceDialogOpen.value = true
  // 每次打开重新拉取，确保数据和负载最新
  const res = await listPersonnel({ pageNum: 1, pageSize: 100, onDuty: true })
  replaceCandidates.value = (res.records || []).filter(p =>
    p.isOnDuty && p.currentWorkload < p.maxWorkload && p.id !== target.personnelId
  )
}

const submitReplace = async () => {
  if (!newPersonnelId.value || !replacingTarget.value) return
  replaceSubmitting.value = true
  try {
    await replaceAssignment(props.order.id, replacingTarget.value.personnelId, {
      newPersonnelId: newPersonnelId.value,
      note: replaceNote.value
    })
    ElMessage.success('已替换')
    replaceDialogOpen.value = false
    emit('refresh')
  } catch (e) {
  } finally {
    replaceSubmitting.value = false
  }
}

watch(() => props.modelValue, (v) => {
  if (!v) {
    // 关闭 drawer 时清状态
    selectedIds.value = new Set()
    batchSearch.value = ''
    newPersonnelId.value = null
    replaceNote.value = ''
  }
})

// 🆕 监听 order.id 变化（轮询刷新 / 切换不同工单）时重置子对话框状态
//   避免开着批量指派/替换对话框时，背后工单数据已被替换导致子状态错位
watch(() => props.order?.id, () => {
  selectedIds.value = new Set()
  batchSearch.value = ''
  newPersonnelId.value = null
  replaceNote.value = ''
  // 候选人缓存保留（人员列表跨工单复用），但清掉替换目标
  replacingTarget.value = null
})
</script>

<style>
.wo-detail-drawer {
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.92)) !important;
  backdrop-filter: blur(20px);
  color: #e0f2fe;
}

.wo-detail-drawer .el-drawer__body {
  padding: 0;
}
</style>

<style scoped>
.drawer-content {
  padding: 24px 28px 32px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 18px;
}

.head-mark {
  margin: 0 0 4px;
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--text-secondary);
}

.head-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.wo-no {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 16px;
}

.prio-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  letter-spacing: 1px;
}

.prio-chip.tone-critical { background: rgba(167, 139, 250, 0.2);  color: #a78bfa; }
.prio-chip.tone-high     { background: rgba(255, 159, 67, 0.2);  color: #ff9f43; }
.prio-chip.tone-medium   { background: rgba(82, 200, 255, 0.2);  color: #52c8ff; }
.prio-chip.tone-low      { background: rgba(59, 255, 159, 0.2);   color: #3bff9f; }

.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-weight: 500;
  margin-bottom: 22px;
}

.status-bar.status-pending {
  background: rgba(255, 159, 67, 0.16);
  color: #ff9f43;
  border: 1px solid rgba(255, 159, 67, 0.32);
}
.status-bar.status-in_progress {
  background: rgba(82, 200, 255, 0.16);
  color: #52c8ff;
  border: 1px solid rgba(82, 200, 255, 0.32);
}
.status-bar.status-resolved {
  background: rgba(59, 255, 159, 0.16);
  color: #3bff9f;
  border: 1px solid rgba(59, 255, 159, 0.32);
}

.block {
  margin-bottom: 22px;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 1px;
}

/* 🆕 指派人列表 */
.assignment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assignment-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
}

.assignment-row .avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  flex-shrink: 0;
}

.assignment-row .info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.assignment-row .info strong {
  font-size: 14px;
  color: #e0f2fe;
}

.emp-no {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--text-secondary);
}

.role-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

.role-tag.role-primary { background: rgba(82, 200, 255, 0.18); color: #52c8ff; }
.role-tag.role-assist  { background: rgba(148, 163, 184, 0.18); color: #cbd5f5; }

.row-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.empty-tip {
  padding: 18px 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
  background: rgba(15, 23, 42, 0.4);
  border-radius: 10px;
  border: 1px dashed rgba(148, 163, 184, 0.2);
}

.device-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.device-name {
  font-size: 15px;
  font-weight: 600;
  color: #e0f2fe;
}

.device-code {
  font-family: 'SF Mono', Consolas, monospace;
  padding: 2px 8px;
  background: rgba(82, 200, 255, 0.12);
  border: 1px solid rgba(82, 200, 255, 0.32);
  border-radius: 8px;
  font-size: 12px;
  color: #52c8ff;
}

.device-type,
.device-loc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.metric-cell {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.metric-value {
  font-size: 18px;
  font-weight: 600;
  font-family: 'SF Mono', Consolas, monospace;
  color: #e0f2fe;
}

.metric-value i {
  font-style: normal;
  font-size: 11px;
  color: var(--text-secondary);
  margin-left: 4px;
  font-weight: 400;
}

.timeline {
  list-style: none;
  padding: 0;
  margin: 0;
  position: relative;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 6px;
  bottom: 6px;
  width: 1px;
  background: rgba(148, 163, 184, 0.25);
}

.timeline li {
  position: relative;
  padding: 4px 0 10px 22px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dot {
  position: absolute;
  left: 0;
  top: 8px;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #52c8ff;
  box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.96);
}

.dot.success { background: #3bff9f; }

.timeline strong {
  font-size: 13px;
  color: #e0f2fe;
}

.timeline .time {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: 'SF Mono', Consolas, monospace;
}

.desc {
  margin: 0;
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.6);
  border-left: 3px solid #52c8ff;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #e0f2fe;
}

.drawer-actions {
  margin-top: auto;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 16px;
  border-top: 1px dashed rgba(148, 163, 184, 0.18);
}

.drawer-actions .el-button {
  flex: 1;
  min-width: 120px;
}

/* 🆕 批量指派 dialog */
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 8px;
}

.batch-count {
  font-size: 13px;
  color: #52c8ff;
  font-weight: 500;
}

.batch-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  max-height: 50vh;
  overflow-y: auto;
  padding: 4px;
}

.batch-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.batch-cell:hover:not(.disabled) {
  border-color: rgba(82, 200, 255, 0.5);
}

.batch-cell.selected {
  border-color: #52c8ff;
  background: rgba(82, 200, 255, 0.1);
}

.batch-cell.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-cell .avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  flex-shrink: 0;
}

.cell-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-info strong {
  font-size: 13px;
  color: #e0f2fe;
}

.cell-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.skill-chip {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 5px;
  border: 1px solid;
}

.skill-chip.skill-电气   { background: rgba(255, 159, 67, 0.15); border-color: rgba(255, 159, 67, 0.4); color: #ff9f43; }
.skill-chip.skill-机械   { background: rgba(82, 200, 255, 0.15); border-color: rgba(82, 200, 255, 0.4); color: #52c8ff; }
.skill-chip.skill-液压   { background: rgba(59, 255, 159, 0.15); border-color: rgba(59, 255, 159, 0.4); color: #3bff9f; }
.skill-chip.skill-仪表   { background: rgba(167, 139, 250, 0.15); border-color: rgba(167, 139, 250, 0.4); color: #a78bfa; }
.skill-chip.skill-自动化 { background: rgba(244, 114, 182, 0.15); border-color: rgba(244, 114, 182, 0.4); color: #f472b6; }

.muted-tag {
  font-size: 10px;
  color: var(--text-secondary);
  font-style: italic;
}

.batch-check {
  pointer-events: none;
}
</style>
