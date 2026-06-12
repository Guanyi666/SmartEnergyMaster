<script setup>
import { computed, onMounted, reactive, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
const tab = shallowRef(route.query.tab === 'history' ? 'history' : 'active')
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

const priorityRank = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
const isMaintenanceEngineer = computed(() => auth.user?.role === 'MAINTENANCE_ENGINEER')
const isMine = (item) => item.activeAssignments?.some((assignment) =>
  assignment.employeeNo === auth.user?.username || assignment.name === auth.user?.nickname
) || item.assignee === auth.user?.nickname
const activeOrders = computed(() => orders.value
  .filter((item) => item.status !== 'RESOLVED' && (item.status === 'PENDING' || isMine(item)))
  .sort((a, b) => (priorityRank[a.priority] ?? 9) - (priorityRank[b.priority] ?? 9)))
const historyOrders = computed(() => orders.value.filter((item) => item.status === 'RESOLVED' && isMine(item)))
const visibleOrders = computed(() => tab.value === 'history' ? historyOrders.value : activeOrders.value)
const selected = computed(() => visibleOrders.value.find((item) => item.id === selectedId.value)
  || visibleOrders.value[0])
const selectedSop = computed(() => allSops.value.find((item) => item.id === selected.value?.sopId) || recommendedSop.value)
const suggestedParts = computed(() => {
  const requiredCodes = Array.isArray(selectedSop.value?.requiredParts) ? selectedSop.value.requiredParts : []
  if (!requiredCodes.length) return parts.value.slice(0, 4)
  return parts.value.filter((part) => requiredCodes.includes(part.partCode))
})
const selectedTransferPending = computed(() => transferRequests.value.some((item) =>
  item.workOrderId === selected.value?.id && item.status === 'PENDING'
))

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
    if (!selectedId.value) selectedId.value = activeOrders.value[0]?.id || historyOrders.value[0]?.id
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
  await patchWorkOrderStatus(selected.value.id, { status, note: note.value })
  ElMessage.success(status === 'RESOLVED' ? '维修已完成并写入个人维修记录' : '已开始维修')
  note.value = ''
  await load()
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

watch(tab, (value) => {
  selectedId.value = (value === 'history' ? historyOrders.value : activeOrders.value)[0]?.id || null
  router.replace({ query: value === 'history' ? { tab: 'history' } : {} })
  loadDetailAssets()
})
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-shell maintenance-center">
    <div class="page-header">
      <div>
        <h2 class="page-title">工单中心</h2>
        <p class="page-subtitle">处理工单、选择维修流程、查看故障历史并提交配件申请。</p>
      </div>
      <el-segmented v-model="tab" :options="[{ label: '待处理工单', value: 'active' }, { label: '我的维修记录', value: 'history' }]" />
    </div>

    <div class="center-grid">
      <aside class="glass-panel panel order-column">
        <h3 class="card-title">{{ tab === 'active' ? '待处理工单' : '我的维修记录' }}</h3>
        <button
          v-for="item in (tab === 'active' ? activeOrders : historyOrders)"
          :key="item.id"
          type="button"
          class="order-card"
          :class="{ selected: selected?.id === item.id, critical: item.priority === 'CRITICAL', high: item.priority === 'HIGH' }"
          @click="selectOrder(item)"
        >
          <span>{{ item.orderNo }}</span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.deviceName }} · {{ getPriorityMeta(item.priority).label }}</small>
        </button>
        <el-empty v-if="!(tab === 'active' ? activeOrders : historyOrders).length" description="暂无工单" />
      </aside>

      <main class="detail-column">
        <template v-if="selected">
          <section class="glass-panel panel detail-title">
            <div>
              <span>{{ selected.orderNo }}</span>
              <h3>{{ selected.title }}</h3>
              <p>{{ selected.deviceName }} · {{ getFaultTypeMeta(selected.faultType).label }}</p>
            </div>
            <StatusPill :status="selected.status" />
          </section>

          <section class="glass-panel panel diagnosis-card">
            <div><span>智能诊断置信度</span><strong>92%</strong></div>
            <p>根据温度、压力与振动历史趋势，初步判断为 {{ getFaultTypeMeta(selected.faultType).label }}，请结合现场情况执行维修流程。</p>
          </section>

          <FaultHistoryChart :points="sensorHistory" :source-time="selected.sourceTime" />

          <SopDecisionPanel
            :recommended="recommendedSop"
            :selected-id="selected.sopId"
            :options="allSops"
            :disabled="selected.status === 'RESOLVED'"
            @accept="chooseSop"
            @choose="chooseSop"
          />

          <section class="glass-panel panel detail-section">
            <div class="section-heading">
              <div>
                <h4>配件建议</h4>
                <p>可以调整建议申请数量，但不能超过当前库存。</p>
              </div>
              <el-button type="primary" :disabled="selected.status === 'RESOLVED'" @click="submitSuggestedParts">申请建议配件</el-button>
            </div>
            <div class="part-grid">
              <div v-for="part in suggestedParts" :key="part.id" class="part-item">
                <div>
                  <strong>{{ part.name }}</strong>
                  <small>{{ part.spec || '无规格说明' }} · 库存 {{ part.quantity }} {{ part.unit }}</small>
                </div>
                <div class="part-quantity">
                  <el-button circle :disabled="!suggestionQuantities[part.id]" @click="changeSuggestionQuantity(part, -1)">－</el-button>
                  <strong>{{ suggestionQuantities[part.id] || 0 }}</strong>
                  <el-button circle :disabled="Number(suggestionQuantities[part.id] || 0) >= part.quantity" @click="changeSuggestionQuantity(part, 1)">＋</el-button>
                </div>
              </div>
            </div>
          </section>

          <section class="glass-panel panel detail-section">
            <h4>配件历史记录</h4>
            <el-table :data="partHistory" stripe>
              <el-table-column prop="usedAt" label="申请时间" min-width="170" />
              <el-table-column prop="partName" label="配件" min-width="150" />
              <el-table-column prop="quantity" label="数量" width="80" />
              <el-table-column prop="userName" label="申请人" min-width="110" />
              <el-table-column prop="note" label="备注" min-width="220" />
            </el-table>
            <el-empty v-if="!partHistory.length" description="当前工单暂无配件申请记录" />
          </section>
        </template>
        <el-empty v-else description="请选择工单" />
      </main>

      <aside class="glass-panel panel action-column">
        <h3 class="card-title">操作区</h3>
        <el-button type="warning" :disabled="!selected || selected.status !== 'PENDING'" @click="changeStatus('IN_PROGRESS')">开始维修</el-button>
        <el-button :disabled="!selected || selected.status === 'RESOLVED'" @click="requestDialogOpen = true">申请配件</el-button>
        <el-button type="danger" plain :disabled="!selected || selected.status === 'RESOLVED' || !isMine(selected) || selectedTransferPending" @click="transferDialogOpen = true">
          申请转工单
        </el-button>
        <el-input v-model="note" type="textarea" :rows="6" placeholder="填写检查过程、维修措施和结果备注" />
        <el-button type="success" :disabled="!selected || selected.status !== 'IN_PROGRESS'" @click="changeStatus('RESOLVED')">维修完成</el-button>
        <div v-if="selected" class="snapshot">
          <span>故障时刻数据</span>
          <strong>温度 {{ selected.latestTemperature ?? '无' }} ℃</strong>
          <strong>压力 {{ selected.latestPressure ?? '无' }} 千帕</strong>
          <strong>振动 {{ selected.latestVibration ?? '无' }} 毫米/秒</strong>
        </div>
      </aside>
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
.maintenance-center {
  min-width: 1100px;
}

.center-grid {
  display: grid;
  grid-template-columns: 290px minmax(520px, 1fr) 250px;
  gap: 18px;
}

.panel,
.detail-column,
.order-column,
.action-column,
.part-grid {
  display: grid;
  align-content: start;
  gap: 12px;
}

.panel {
  padding: 18px;
}

.order-card {
  display: grid;
  gap: 7px;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, .16);
  border-left: 4px solid #52c8ff;
  border-radius: 12px;
  background: rgba(15, 23, 42, .62);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.order-card.critical { border-left-color: #ff5d5d; }
.order-card.high { border-left-color: #ffb347; }
.order-card.selected { background: rgba(82, 200, 255, .12); border-color: rgba(82, 200, 255, .4); }

.order-card span,
.order-card small,
.detail-title span,
.detail-title p,
.part-item small,
.section-heading p,
.dialog-note {
  color: var(--text-secondary);
  font-size: 12px;
}

.detail-title,
.part-item,
.section-heading,
.part-quantity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title h3,
.detail-title p,
.section-heading h4,
.section-heading p,
.detail-section h4 {
  margin: 0;
}

.detail-title h3 {
  margin: 6px 0;
}

.diagnosis-card {
  border: 1px solid rgba(82, 200, 255, .17);
  border-radius: 14px;
  background: rgba(15, 23, 42, .55);
}

.diagnosis-card div {
  display: flex;
  justify-content: space-between;
}

.diagnosis-card strong {
  color: #3bff9f;
  font-size: 22px;
}

.diagnosis-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.part-item {
  padding: 11px;
  border-radius: 10px;
  background: rgba(2, 6, 23, .4);
}

.part-item > div:first-child,
.snapshot {
  display: grid;
  gap: 6px;
}

.part-quantity strong {
  min-width: 24px;
  text-align: center;
}

.snapshot {
  padding: 14px;
  border-radius: 12px;
  background: rgba(15, 23, 42, .55);
}

.snapshot span {
  color: var(--text-secondary);
}
</style>
