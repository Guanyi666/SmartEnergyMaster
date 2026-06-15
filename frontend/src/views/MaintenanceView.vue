<script setup>
import { computed, onMounted, reactive, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, CircleCheck, Clock } from '@element-plus/icons-vue'
import {
  createTransferRequest,
  getWorkOrderList,
  listTransferRequests,
  patchWorkOrderSop,
  patchWorkOrderStatus
} from '../api/workorder'
import {
  getSensorHistory,
  getSop,
  listSpareParts,
  listSparePartUsages,
  listSops,
  recordSparePartUsages
} from '../api'
import FaultHistoryChart from '../components/FaultHistoryChart.vue'
import PartsRequestDialog from '../components/PartsRequestDialog.vue'
import SopDecisionPanel from '../components/SopDecisionPanel.vue'
import StatusPill from '../components/StatusPill.vue'
import { useAuthStore } from '../stores/auth'
import { getFaultTypeMeta, getPriorityMeta } from '../utils/status'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 三个分诊分组（后端仍是 PENDING / IN_PROGRESS / RESOLVED 三态）：
//   pending  → 待接单（公共池，未认领）
//   progress → 我的进行中
//   done     → 我的已完成
const tab = shallowRef(route.query.tab === 'history' ? 'done' : 'pending')
const tabDefs = [
  { key: 'pending', label: '待接单' },
  { key: 'progress', label: '我的进行中' },
  { key: 'done', label: '我的已完成' }
]
const searchText = shallowRef('')
const priorityFilter = shallowRef('')
const sortBy = shallowRef('priority')

const orders = shallowRef([])
const selectedId = shallowRef(null)
const recommendedSop = shallowRef(null)
const allSops = shallowRef([])
const parts = shallowRef([])
const sensorHistory = shallowRef([])
const partHistory = shallowRef([])
const note = shallowRef('')
const loading = shallowRef(false)
const requestDialogOpen = shallowRef(false)
const requestSubmitting = shallowRef(false)
const transferDialogOpen = shallowRef(false)
const transferReason = shallowRef('')
const transferSubmitting = shallowRef(false)
const transferRequests = shallowRef([])
const suggestionQuantities = reactive({})
let firstLoad = true

const priorityRank = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
const isMaintenanceEngineer = computed(() => auth.user?.role === 'MAINTENANCE_ENGINEER')
const isMine = (item) => item.activeAssignments?.some((assignment) =>
  assignment.employeeNo === auth.user?.username || assignment.name === auth.user?.nickname
) || item.assignee === auth.user?.nickname || item.assignee === auth.user?.username

const faultLabel = (ft) => getFaultTypeMeta(ft).label
const priorityLabel = (p) => getPriorityMeta(p).label

// ===== 分诊分组 =====
const pendingPool = computed(() => orders.value.filter((item) => item.status === 'PENDING'))
const myInProgress = computed(() => orders.value.filter((item) => item.status === 'IN_PROGRESS' && isMine(item)))
const myDone = computed(() => orders.value.filter((item) => item.status === 'RESOLVED' && isMine(item)))
const tabGroups = computed(() => ({ pending: pendingPool.value, progress: myInProgress.value, done: myDone.value }))
const tabCounts = computed(() => ({ pending: pendingPool.value.length, progress: myInProgress.value.length, done: myDone.value.length }))
const emptyText = computed(() => ({
  pending: '当前没有待接单工单',
  progress: '你当前没有进行中的工单',
  done: '暂无已完成的维修记录'
}[tab.value]))

// ===== 筛选 + 排序 =====
const filteredOrders = computed(() => {
  let list = tabGroups.value[tab.value] || []
  if (priorityFilter.value) list = list.filter((item) => item.priority === priorityFilter.value)
  const kw = searchText.value.trim().toLowerCase()
  if (kw) {
    list = list.filter((item) =>
      `${item.orderNo} ${item.deviceName} ${item.title} ${faultLabel(item.faultType)}`.toLowerCase().includes(kw))
  }
  return [...list].sort((a, b) => sortBy.value === 'time'
    ? new Date(b.createdAt || b.sourceTime || 0) - new Date(a.createdAt || a.sourceTime || 0)
    : (priorityRank[a.priority] ?? 9) - (priorityRank[b.priority] ?? 9))
})

const selected = computed(() => filteredOrders.value.find((item) => item.id === selectedId.value)
  || filteredOrders.value[0])
const isReadonly = computed(() => selected.value?.status === 'RESOLVED')
const selectedSop = computed(() => allSops.value.find((item) => item.id === selected.value?.sopId) || recommendedSop.value)
const suggestedParts = computed(() => {
  const requiredCodes = Array.isArray(selectedSop.value?.requiredParts) ? selectedSop.value.requiredParts : []
  if (!requiredCodes.length) return parts.value.slice(0, 4)
  return parts.value.filter((part) => requiredCodes.includes(part.partCode))
})
const selectedTransferPending = computed(() => transferRequests.value.some((item) =>
  item.workOrderId === selected.value?.id && item.status === 'PENDING'
))

// ===== 阶段进度（前端派生，不改后端三态）=====
const stageSteps = ['接单', '诊断', '维修执行', '完成']
const stageIndex = computed(() => {
  const order = selected.value
  if (!order) return 0
  if (order.status === 'PENDING') return 0
  if (order.status === 'RESOLVED') return 4
  return order.sopId ? 2 : 1
})

// ===== 完成清单（闭环 / 防错）=====
const checklist = computed(() => {
  const order = selected.value
  return [
    { label: '已接单', done: !!order && order.status !== 'PENDING', required: true },
    { label: '已选择维修流程', done: !!order?.sopId, required: false },
    { label: '已申请所需配件', done: partHistory.value.length > 0, required: false },
    { label: '已填写维修记录', done: note.value.trim().length > 0, required: true }
  ]
})
const canComplete = computed(() => selected.value?.status === 'IN_PROGRESS' && note.value.trim().length > 0)
const completeHint = computed(() =>
  selected.value?.status === 'IN_PROGRESS' && !note.value.trim() ? '请先填写维修记录后再完成工单' : '')

const parseArray = (value) => {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const normalizeSop = (sop) => {
  if (!sop) return null
  return {
    ...sop,
    steps: parseArray(sop.steps),
    requiredParts: parseArray(sop.requiredParts)
  }
}

const loadDetailAssets = async () => {
  if (!selected.value) return
  const [sopOptions, spareParts, history, usages, transfers] = await Promise.all([
    listSops({}),
    listSpareParts({}),
    getSensorHistory(selected.value.deviceCode, 1),
    listSparePartUsages({ workOrderId: selected.value.id, limit: 100 }),
    isMaintenanceEngineer.value ? listTransferRequests({ mine: true }) : Promise.resolve([])
  ])
  allSops.value = (sopOptions || []).map(normalizeSop)
  parts.value = spareParts || []
  partHistory.value = usages || []
  transferRequests.value = transfers || []

  const sourceAt = selected.value.sourceTime ? new Date(selected.value.sourceTime).getTime() : Date.now()
  const startAt = sourceAt - 15 * 60 * 1000
  const beforeFault = (history || []).filter((point) => {
    const time = new Date(point.time).getTime()
    return time >= startAt && time <= sourceAt
  })
  sensorHistory.value = beforeFault.length ? beforeFault : (history || []).slice(-300)

  const recommendedList = await listSops({
    deviceType: selected.value.deviceType,
    faultType: selected.value.faultType
  })
  recommendedSop.value = normalizeSop(recommendedList?.[0] || null)
  if (selected.value.sopId && !allSops.value.some((item) => item.id === selected.value.sopId)) {
    const detail = await getSop(selected.value.sopId)
    allSops.value = [normalizeSop(detail), ...allSops.value]
  }
  Object.keys(suggestionQuantities).forEach((key) => delete suggestionQuantities[key])
  suggestedParts.value.forEach((part) => { suggestionQuantities[part.id] = part.quantity > 0 ? 1 : 0 })
}

const load = async () => {
  loading.value = true
  try {
    const result = await getWorkOrderList({ page: 1, size: 200 })
    orders.value = result.records || []
    // 首次进入：待接单为空但有进行中/已完成时，自动落到对应页签，避免落在空列表
    if (firstLoad && tab.value === 'pending') {
      firstLoad = false
      if (!pendingPool.value.length && myInProgress.value.length) tab.value = 'progress'
      else if (!pendingPool.value.length && !myInProgress.value.length && myDone.value.length) tab.value = 'done'
    }
    if (!selectedId.value || !filteredOrders.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = filteredOrders.value[0]?.id || null
    }
    await loadDetailAssets()
  } finally {
    loading.value = false
  }
}

const selectOrder = async (item) => {
  selectedId.value = item.id
  await loadDetailAssets()
}

const changeStatus = async (status) => {
  if (!selected.value) return
  const orderId = selected.value.id
  const payload = { status, note: note.value }
  if (status === 'IN_PROGRESS') {
    payload.assignee = auth.user?.nickname || auth.user?.username
  }
  await patchWorkOrderStatus(orderId, payload)
  ElMessage.success(status === 'RESOLVED' ? '维修已完成并写入个人维修记录' : '已接单，开始维修')
  note.value = ''
  // 先切换页签再刷新数据：load() 内依赖 tab.value 计算 filteredOrders，
  // 若仍在"待接单"页签调用 load()，已变为 IN_PROGRESS 的工单会从过滤列表消失，
  // 导致 selectedId 被清空，后续页签切换无法恢复选中。
  if (status === 'IN_PROGRESS') tab.value = 'progress'
  if (status === 'RESOLVED') tab.value = 'done'
  // 乐观更新本地工单状态与指派人，确保 isMine() 命中，避免 load() 返回
  // 数据与当前用户不匹配时工单从 Tab 列表消失（维修工程师接单场景）
  const idx = orders.value.findIndex(o => o.id === orderId)
  if (idx !== -1) {
    const updated = { ...orders.value[idx], status }
    if (status === 'IN_PROGRESS') {
      updated.assignee = auth.user?.nickname || auth.user?.username
    }
    orders.value = [...orders.value.slice(0, idx), updated, ...orders.value.slice(idx + 1)]
  }
  await load()
  // 兜底：确保刚才操作的工单仍被选中（即使 load() 内 filteredOrders 匹配失败）
  selectedId.value = orderId
}

const startRepair = async () => {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(
      `确认接单并开始维修「${selected.value.title}」？接单后该工单将指派给你。`,
      '接单确认',
      { type: 'info', confirmButtonText: '接单开始', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await changeStatus('IN_PROGRESS')
}

const completeRepair = async () => {
  if (!canComplete.value) return
  try {
    await ElMessageBox.confirm(
      '确认该工单维修完成？完成后将写入你的个人维修记录，且工单不可重新打开。',
      '维修完成确认',
      { type: 'warning', confirmButtonText: '确认完成', cancelButtonText: '再检查一下' }
    )
  } catch {
    return
  }
  await changeStatus('RESOLVED')
}

const chooseSop = async (sopId) => {
  await patchWorkOrderSop(selected.value.id, sopId)
  ElMessage.success(sopId === recommendedSop.value?.id ? '已接受智能推荐流程' : '已选择新的维修流程')
  await load()
}

const changeSuggestionQuantity = (part, delta) => {
  const current = Number(suggestionQuantities[part.id] || 0)
  suggestionQuantities[part.id] = Math.min(part.quantity, Math.max(0, current + delta))
}

const submitUsageItems = async (selectedItems) => {
  requestSubmitting.value = true
  try {
    const items = selectedItems.map(({ part, quantity }) => ({
      partId: part.id,
      quantity,
      workOrderId: selected.value.id,
      userName: auth.user?.username,
      note: `工单 ${selected.value.orderNo} 配件申请`
    }))
    await recordSparePartUsages(items)
    ElMessage.success('配件申请已提交')
    requestDialogOpen.value = false
    await loadDetailAssets()
  } finally {
    requestSubmitting.value = false
  }
}

const submitSuggestedParts = async () => {
  const selectedItems = suggestedParts.value
    .filter((part) => Number(suggestionQuantities[part.id] || 0) > 0)
    .map((part) => ({ part, quantity: Number(suggestionQuantities[part.id]) }))
  if (!selectedItems.length) {
    ElMessage.warning('请至少选择一种建议配件')
    return
  }
  await submitUsageItems(selectedItems)
}

const submitTransfer = async () => {
  if (!transferReason.value.trim()) {
    ElMessage.warning('请填写转工单缘由')
    return
  }
  transferSubmitting.value = true
  try {
    await createTransferRequest(selected.value.id, { reason: transferReason.value })
    ElMessage.success('转工单申请已提交，等待设备管理人员审批')
    transferDialogOpen.value = false
    transferReason.value = ''
    await loadDetailAssets()
  } finally {
    transferSubmitting.value = false
  }
}

// 切换页签：选中项若不在新列表则落到第一条
watch(tab, () => {
  router.replace({ query: tab.value === 'done' ? { tab: 'history' } : {} })
  if (!filteredOrders.value.some((item) => item.id === selectedId.value)) {
    selectedId.value = filteredOrders.value[0]?.id || null
  }
  loadDetailAssets()
})
// 切换工单时清空维修记录草稿
watch(() => selected.value?.id, () => { note.value = '' })

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-shell wo-center">
    <div class="page-header">
      <div>
        <h2 class="page-title">工单中心</h2>
        <p class="page-subtitle">接单 → 诊断 → 维修 → 完成：按流程处理工单，系统会校验关键步骤，避免漏项。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="wo-body">
      <!-- 左：分诊列表 -->
      <aside class="glass-panel wo-list">
        <div class="wo-tabs">
          <button
            v-for="t in tabDefs"
            :key="t.key"
            type="button"
            class="wo-tab"
            :class="{ active: tab === t.key }"
            @click="tab = t.key"
          >
            {{ t.label }}<span class="wo-tab__count">{{ tabCounts[t.key] }}</span>
          </button>
        </div>

        <div class="wo-filters">
          <el-input v-model="searchText" placeholder="搜索 工单号 / 设备 / 故障" clearable size="small" :prefix-icon="Search" />
          <div class="wo-filter-row">
            <el-select v-model="priorityFilter" placeholder="全部优先级" clearable size="small">
              <el-option label="紧急" value="CRITICAL" />
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
            <el-select v-model="sortBy" size="small">
              <el-option label="按优先级" value="priority" />
              <el-option label="按时间" value="time" />
            </el-select>
          </div>
        </div>

        <div class="wo-cards">
          <button
            v-for="item in filteredOrders"
            :key="item.id"
            type="button"
            class="wo-card"
            :class="{ selected: selected?.id === item.id }"
            @click="selectOrder(item)"
          >
            <span class="wo-card__bar" :style="{ background: getPriorityMeta(item.priority).color }" />
            <div class="wo-card__body">
              <div class="wo-card__top">
                <strong>{{ item.deviceName }}</strong>
                <span class="wo-prio" :style="{ color: getPriorityMeta(item.priority).color }">{{ priorityLabel(item.priority) }}</span>
              </div>
              <p class="wo-card__title">{{ item.title }}</p>
              <div class="wo-card__meta">
                <span>{{ item.orderNo }}</span>
                <StatusPill :status="item.status" />
              </div>
            </div>
          </button>
          <el-empty v-if="!filteredOrders.length" :description="emptyText" />
        </div>
      </aside>

      <!-- 右：工作台 -->
      <main class="wo-work">
        <template v-if="selected">
          <!-- 头部 + 阶段进度 -->
          <section class="glass-panel wo-headcard">
            <div class="wo-headrow">
              <div class="wo-headinfo">
                <span class="wo-orderno">{{ selected.orderNo }}</span>
                <h3>{{ selected.title }}</h3>
                <p>{{ selected.deviceName }} · {{ faultLabel(selected.faultType) }} · 优先级 {{ priorityLabel(selected.priority) }}</p>
              </div>
              <StatusPill :status="selected.status" />
            </div>
            <el-steps :active="stageIndex" align-center finish-status="success" class="wo-steps">
              <el-step v-for="step in stageSteps" :key="step" :title="step" />
            </el-steps>
          </section>

          <div class="wo-grid">
            <!-- 中：诊断 / 趋势 / SOP / 配件 -->
            <div class="wo-detail">
              <section class="glass-panel panel diagnosis-card">
                <div><span>智能诊断置信度</span><strong>92%</strong></div>
                <p>根据温度、压力与振动历史趋势，初步判断为 {{ faultLabel(selected.faultType) }}，请结合现场情况执行维修流程。</p>
              </section>

              <section class="glass-panel panel">
                <h4 class="panel-h">故障前后趋势</h4>
                <FaultHistoryChart :points="sensorHistory" :source-time="selected.sourceTime" />
              </section>

              <SopDecisionPanel
                :recommended="recommendedSop"
                :selected-id="selected.sopId"
                :options="allSops"
                :disabled="isReadonly"
                @accept="chooseSop"
                @choose="chooseSop"
              />

              <section class="glass-panel panel">
                <div class="section-heading">
                  <div>
                    <h4 class="panel-h">配件建议</h4>
                    <p class="muted small">可调整建议申请数量，但不能超过当前库存。</p>
                  </div>
                  <el-button type="primary" :disabled="isReadonly" @click="submitSuggestedParts">申请建议配件</el-button>
                </div>
                <div class="part-grid">
                  <div v-for="part in suggestedParts" :key="part.id" class="part-item">
                    <div>
                      <strong>{{ part.name }}</strong>
                      <small>{{ part.spec || '无规格说明' }} · 库存 {{ part.quantity }} {{ part.unit }}</small>
                    </div>
                    <div class="part-quantity">
                      <el-button circle :disabled="isReadonly || !suggestionQuantities[part.id]" @click="changeSuggestionQuantity(part, -1)">－</el-button>
                      <strong>{{ suggestionQuantities[part.id] || 0 }}</strong>
                      <el-button circle :disabled="isReadonly || Number(suggestionQuantities[part.id] || 0) >= part.quantity" @click="changeSuggestionQuantity(part, 1)">＋</el-button>
                    </div>
                  </div>
                </div>
              </section>

              <section class="glass-panel panel">
                <h4 class="panel-h">配件申请记录</h4>
                <el-table :data="partHistory" stripe>
                  <el-table-column prop="usedAt" label="申请时间" min-width="160" />
                  <el-table-column prop="partName" label="配件" min-width="140" />
                  <el-table-column prop="quantity" label="数量" width="70" />
                  <el-table-column prop="userName" label="申请人" min-width="100" />
                  <el-table-column prop="note" label="备注" min-width="180" />
                </el-table>
                <el-empty v-if="!partHistory.length" description="当前工单暂无配件申请记录" />
              </section>
            </div>

            <!-- 右：按阶段引导的操作区（闭环 / 防错） -->
            <aside class="wo-action">
              <section class="glass-panel panel action-card">
                <h4 class="panel-h">当前操作</h4>

                <!-- 待接单 -->
                <template v-if="selected.status === 'PENDING'">
                  <p class="action-tip">该工单尚未接单。接单后将指派给你并进入维修流程。</p>
                  <el-button type="warning" class="action-primary" @click="startRepair">接单并开始维修</el-button>
                </template>

                <!-- 进行中 -->
                <template v-else-if="selected.status === 'IN_PROGRESS'">
                  <ul class="checklist">
                    <li v-for="c in checklist" :key="c.label" class="check-item" :class="{ done: c.done }">
                      <el-icon><component :is="c.done ? CircleCheck : Clock" /></el-icon>
                      <span>{{ c.label }}<i v-if="c.required" class="req">*</i></span>
                    </li>
                  </ul>
                  <el-input v-model="note" type="textarea" :rows="5" placeholder="填写检查过程、维修措施与结果（必填）" />
                  <el-tooltip :disabled="!completeHint" :content="completeHint" placement="top">
                    <span class="action-primary-wrap">
                      <el-button type="success" class="action-primary" :disabled="!canComplete" @click="completeRepair">维修完成</el-button>
                    </span>
                  </el-tooltip>
                  <div class="secondary-actions">
                    <el-button @click="requestDialogOpen = true">申请配件</el-button>
                    <el-button type="danger" plain :disabled="!isMine(selected) || selectedTransferPending" @click="transferDialogOpen = true">申请转工单</el-button>
                  </div>
                  <p v-if="selectedTransferPending" class="muted small">已提交转工单申请，等待设备管理人员审批。</p>
                </template>

                <!-- 已完成 -->
                <template v-else>
                  <el-result icon="success" title="工单已完成" sub-title="已写入你的个人维修记录" />
                </template>
              </section>

              <section class="glass-panel panel snapshot">
                <h4 class="panel-h">故障时刻数据</h4>
                <div class="snap-grid">
                  <div><span>温度</span><strong>{{ selected.latestTemperature ?? '无' }} ℃</strong></div>
                  <div><span>压力</span><strong>{{ selected.latestPressure ?? '无' }} 千帕</strong></div>
                  <div><span>振动</span><strong>{{ selected.latestVibration ?? '无' }} 毫米/秒</strong></div>
                </div>
              </section>
            </aside>
          </div>
        </template>
        <el-empty v-else :description="emptyText" />
      </main>
    </div>

    <PartsRequestDialog v-model="requestDialogOpen" :parts="parts" :submitting="requestSubmitting" @submit="submitUsageItems" />

    <el-dialog v-model="transferDialogOpen" title="申请转工单" width="560px">
      <p class="dialog-note">设备管理人员会审核申请；接受申请后将重新指派其他维修人员。</p>
      <el-input v-model="transferReason" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请详细说明无法继续处理该工单的缘由" />
      <template #footer>
        <el-button @click="transferDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="transferSubmitting" @click="submitTransfer">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wo-center {
  min-width: 1080px;
}

.wo-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
}

/* ===== 左侧分诊列表 ===== */
.wo-list {
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: 12px;
  padding: 14px;
  align-content: start;
  /* 左侧分诊列表跟随滚动固定，避免工作台很长时左边出现大片空白 */
  position: sticky;
  top: 16px;
  align-self: start;
  max-height: calc(100vh - 32px);
}

.wo-tabs {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: 12px;
  background: rgba(2, 6, 23, .4);
}

.wo-tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 6px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all .2s ease;
}

.wo-tab.active {
  background: rgba(82, 200, 255, .14);
  color: var(--accent-cyan);
  box-shadow: inset 0 0 0 1px rgba(92, 220, 255, .35);
}

.wo-tab__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(148, 163, 184, .22);
  color: var(--text-primary);
  font-size: 11px;
  line-height: 18px;
}

.wo-tab.active .wo-tab__count {
  background: var(--accent-cyan);
  color: #03121f;
}

.wo-filters {
  display: grid;
  gap: 8px;
}

.wo-filter-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.wo-cards {
  display: grid;
  gap: 8px;
  align-content: start;
  overflow-y: auto;
  padding-right: 2px;
}

.wo-card {
  display: flex;
  gap: 0;
  padding: 0;
  border: 1px solid rgba(148, 163, 184, .16);
  border-radius: 12px;
  background: rgba(15, 23, 42, .62);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: all .2s ease;
}

.wo-card:hover {
  border-color: rgba(82, 200, 255, .4);
}

.wo-card.selected {
  border-color: rgba(82, 200, 255, .55);
  background: rgba(82, 200, 255, .12);
}

.wo-card__bar {
  width: 4px;
  flex-shrink: 0;
}

.wo-card__body {
  display: grid;
  gap: 6px;
  padding: 11px 12px;
  min-width: 0;
  width: 100%;
}

.wo-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.wo-card__top strong {
  color: var(--text-primary);
  font-size: 13px;
}

.wo-prio {
  font-size: 11px;
  font-weight: 700;
}

.wo-card__title {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wo-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-faint);
  font-size: 11px;
}

/* ===== 右侧工作台 ===== */
.wo-work {
  display: grid;
  gap: 16px;
  align-content: start;
  min-width: 0;
}

.wo-headcard {
  display: grid;
  gap: 18px;
  padding: 18px 20px;
}

.wo-headrow {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.wo-orderno {
  color: var(--text-secondary);
  font-size: 12px;
}

.wo-headinfo h3 {
  margin: 6px 0;
}

.wo-headinfo p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.wo-steps {
  margin-top: 4px;
}

.wo-steps :deep(.el-step__title) {
  font-size: 13px;
  color: var(--text-secondary);
}

.wo-steps :deep(.el-step__title.is-process) {
  color: var(--accent-cyan);
  font-weight: 700;
}

.wo-steps :deep(.el-step__title.is-success) {
  color: var(--accent-green);
}

.wo-steps :deep(.el-step__head.is-process) {
  color: var(--accent-cyan);
  border-color: var(--accent-cyan);
}

.wo-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

.wo-detail,
.wo-action {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-content: start;
  min-width: 0;
}

/* 操作区做成吸顶侧栏：详情列很长时跟随滚动，避免右侧出现大片空白 */
.wo-action {
  position: sticky;
  top: 16px;
  align-self: start;
}

/* 让内部面板（尤其含 ECharts 画布的卡片）可收缩，避免撑破外层栅格列 */
.wo-detail > *,
.wo-action > * {
  min-width: 0;
}

.panel {
  padding: 16px 18px;
}

.panel-h {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 700;
  color: var(--accent-cyan);
}

.diagnosis-card {
  border: 1px solid rgba(82, 200, 255, .17);
}

.diagnosis-card div {
  display: flex;
  justify-content: space-between;
}

.diagnosis-card strong {
  color: var(--accent-green);
  font-size: 22px;
}

.diagnosis-card p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-heading h4 {
  margin: 0;
}

.part-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}

.part-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px;
  border-radius: 10px;
  background: rgba(2, 6, 23, .4);
}

.part-item > div:first-child {
  display: grid;
  gap: 6px;
}

.part-item small {
  color: var(--text-secondary);
  font-size: 12px;
}

.part-quantity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.part-quantity strong {
  min-width: 22px;
  text-align: center;
}

/* ===== 操作区 ===== */
.action-card {
  display: grid;
  gap: 12px;
}

.action-tip {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.action-primary-wrap {
  display: block;
}

.action-primary {
  width: 100%;
}

.checklist {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 12px;
  list-style: none;
  border-radius: 10px;
  background: rgba(2, 6, 23, .4);
}

.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.check-item .el-icon {
  color: var(--text-faint);
}

.check-item.done {
  color: var(--text-primary);
}

.check-item.done .el-icon {
  color: var(--accent-green);
}

.check-item .req {
  margin-left: 2px;
  color: var(--accent-red);
  font-style: normal;
}

.secondary-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.snapshot {
  display: grid;
  gap: 12px;
}

.snap-grid {
  display: grid;
  gap: 8px;
}

.snap-grid div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 11px;
  border-radius: 9px;
  background: rgba(15, 23, 42, .55);
}

.snap-grid span {
  color: var(--text-secondary);
  font-size: 12px;
}

.muted {
  color: var(--text-secondary);
}

.small {
  font-size: 12px;
  margin: 0;
}

.dialog-note {
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .wo-grid {
    grid-template-columns: 1fr;
  }
}
</style>
