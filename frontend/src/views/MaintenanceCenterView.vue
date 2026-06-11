<template>
  <div class="page-shell">
    <!-- 顶部页头 -->
    <div class="page-header">
      <div>
        <h2 class="page-title">维修指挥中心</h2>
        <p class="page-subtitle">
          拖拽卡片仅改 status，指派人由抽屉管理；每 5s 自动轮询新工单；最后同步：{{ lastSyncLabel }}
        </p>
      </div>
      <div class="header-tools">
        <el-button :icon="Plus" @click="openCreateDialog">新建工单</el-button>
        <el-button type="primary" :icon="Plus" @click="goDispatch">智能调度</el-button>
      </div>
    </div>

    <!-- 顶部 5 个 StatBadge -->
    <div class="stat-row">
      <StatBadge label="总工单" :value="summary.totalOrders || 0" icon="document" tone="blue" />
      <StatBadge label="待处理" :value="summary.pendingOrders || 0" icon="clock" tone="orange" />
      <StatBadge label="处理中" :value="summary.inProgressOrders || 0" icon="loading" tone="purple" />
      <StatBadge label="在岗 / 总数" :value="`${summary.onDutyPersonnel || 0}/${summary.totalPersonnel || 0}`"
                 icon="user" tone="green" />
      <StatBadge label="平均负载" :value="summary.avgWorkloadRate || 0" suffix="%" icon="trend" tone="red" />
    </div>

    <!-- 3 列 Kanban -->
    <div class="kanban-row">
      <!-- PENDING -->
      <div class="kanban-col"
           :class="{
             'col-dragover': dragOver === 'PENDING',
             'col-locked':  draggingFromResolved
           }"
           @dragover.prevent="onDragOver($event, 'PENDING')"
           @dragleave="dragOver = null"
           @drop.prevent="onDrop('PENDING')">
        <div class="col-head head-pending">
          <div class="col-title">
            <span class="dot" />
            <strong>待处理</strong>
            <el-badge :value="pending.length" :max="99" class="count-badge" />
          </div>
          <span class="col-sub">等待分配或开始处理</span>
        </div>
        <div class="col-body">
          <WorkOrderCard
            v-for="o in pending" :key="o.id"
            :order="o"
            draggable="true"
            @dragstart="onDragStart($event, o)"
            @click="openDetail(o)"
          />
          <div v-if="!pending.length" class="empty-tip">暂无待处理工单</div>
        </div>
      </div>

      <!-- IN_PROGRESS -->
      <div class="kanban-col"
           :class="{
             'col-dragover': dragOver === 'IN_PROGRESS',
             'col-locked':  draggingFromResolved
           }"
           @dragover.prevent="onDragOver($event, 'IN_PROGRESS')"
           @dragleave="dragOver = null"
           @drop.prevent="onDrop('IN_PROGRESS')">
        <div class="col-head head-in-progress">
          <div class="col-title">
            <span class="dot" />
            <strong>处理中</strong>
            <el-badge :value="inProgress.length" :max="99" class="count-badge" />
          </div>
          <span class="col-sub">维修工程师已接单</span>
        </div>
        <div class="col-body">
          <WorkOrderCard
            v-for="o in inProgress" :key="o.id"
            :order="o"
            draggable="true"
            @dragstart="onDragStart($event, o)"
            @click="openDetail(o)"
          />
          <div v-if="!inProgress.length" class="empty-tip">暂无处理中工单</div>
        </div>
      </div>

      <!-- RESOLVED -->
      <div class="kanban-col"
           :class="{ 'col-dragover': dragOver === 'RESOLVED' }"
           @dragover.prevent="onDragOver($event, 'RESOLVED')"
           @dragleave="dragOver = null"
           @drop.prevent="onDrop('RESOLVED')">
        <div class="col-head head-resolved">
          <div class="col-title">
            <span class="dot" />
            <strong>已完成</strong>
            <el-badge :value="resolved.length" :max="99" class="count-badge" />
          </div>
          <span class="col-sub">最近 50 条闭环记录</span>
        </div>
        <div class="col-body">
          <WorkOrderCard
            v-for="o in resolved.slice(0, 50)" :key="o.id"
            :order="o"
            draggable="true"
            @dragstart="onDragStart($event, o)"
            @click="openDetail(o)"
          />
          <div v-if="!resolved.length" class="empty-tip">暂无已闭环工单</div>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <WorkOrderDetailDrawer
      v-model="drawerOpen"
      :order="currentOrder"
      @confirm="confirmHandle"
      @resolve="markResolved"
      @refresh="onRefresh"
    />

    <!-- 🆕 新建工单对话框 -->
    <el-dialog
      v-model="createDialogOpen"
      title="新建工单"
      width="640px"
      append-to-body
      @closed="resetCreateForm"
    >
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="设备" required>
          <el-select
            v-model="createForm.deviceId"
            filterable
            placeholder="选择设备"
            style="width: 100%"
            @change="onDeviceChange"
          >
            <el-option
              v-for="d in deviceOptions"
              :key="d.id"
              :label="`${d.deviceName} (${d.deviceCode})`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="故障类型" required>
          <el-select v-model="createForm.faultType" placeholder="选择故障类型" style="width: 100%">
            <el-option
              v-for="(meta, key) in faultTypeOptions"
              :key="key"
              :label="`${meta.emoji} ${meta.label}`"
              :value="key"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" required>
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option
              v-for="(meta, key) in priorityOptions"
              :key="key"
              :label="meta.label"
              :value="key"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" required>
          <el-input
            v-model="createForm.title"
            maxlength="128"
            show-word-limit
            placeholder="如：巡检发现轴承异响"
          />
        </el-form-item>

        <el-form-item label="故障描述" required>
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="描述故障现象、影响范围、初步判断"
          />
        </el-form-item>

        <!-- 只读上下文：选中设备后展示当前传感器快照，让操作员确认是这台设备 / 这个工况 -->
        <el-form-item label="当前快照">
          <div v-if="sensorSnapshot" class="snapshot-row">
            <span>🌡 {{ formatSnapshotVal(sensorSnapshot.temperature) }} ℃</span>
            <span>💧 {{ formatSnapshotVal(sensorSnapshot.pressure) }} kPa</span>
            <span>📳 {{ formatSnapshotVal(sensorSnapshot.vibration) }} mm/s</span>
            <span class="snapshot-time">{{ formatSnapshotTime(sensorSnapshot.time) }}</span>
          </div>
          <span v-else-if="snapshotLoading" class="snapshot-muted">加载中…</span>
          <span v-else class="snapshot-muted">暂无传感器数据（创建时将记录为 NULL）</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createSubmitting"
          :disabled="createSubmitting"
          @click="submitCreate"
        >
          创建工单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import StatBadge from '../components/StatBadge.vue'
import WorkOrderCard from '../components/WorkOrderCard.vue'
import WorkOrderDetailDrawer from '../components/WorkOrderDetailDrawer.vue'
import { getDispatchSummary, getWorkOrderList, patchWorkOrderStatus, createWorkOrder } from '../api/workorder'
import { getDevices, getLatestSensor } from '../api'
import { faultTypeMeta, priorityMeta } from '../utils/status'
import { usePollingTask } from '../composables/usePollingTask'

const router = useRouter()

// 全部工单
const allOrders = ref([])
const summary = ref({})

const pending    = computed(() => allOrders.value.filter(o => o.status === 'PENDING'))
const inProgress = computed(() => allOrders.value.filter(o => o.status === 'IN_PROGRESS'))
const resolved   = computed(() => allOrders.value.filter(o => o.status === 'RESOLVED'))

const lastSyncLabel = computed(() => {
  if (!lastSyncAt.value) return '—'
  const d = new Date(lastSyncAt.value)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
})
const lastSyncAt = ref(null)

// 🟠 5s 轮询新工单
const loadKanban = async () => {
  try {
    const [list, sum] = await Promise.all([
      getWorkOrderList({ page: 1, size: 200 }),
      getDispatchSummary()
    ])
    allOrders.value = list.records || []
    summary.value = sum || {}
    lastSyncAt.value = new Date()
  } catch (e) {
    // http.js 拦截器已 toast
  }
}
const { start: startPolling, run: refreshNow } = usePollingTask(loadKanban, 5000)

// 指派调整后刷新详情：重新拉取列表后同步更新 currentOrder 引用
const onRefresh = async () => {
  await refreshNow()
  if (currentOrder.value) {
    const updated = allOrders.value.find(o => o.id === currentOrder.value.id)
    if (updated) currentOrder.value = updated
  }
}

// ---- 拖拽 ----
const dragOver = ref(null)
const draggingOrder = ref(null)
// 🔒 只存 ID（primitive），不复用整个对象引用，避免 5s 轮询替换 allOrders 后的竞态
const draggingOrderId = ref(null)
// 🔒 记录当前拖拽的卡片是否来自已闭环列；用于在 dragover 阶段禁用非法目标
const draggingFromResolved = ref(false)

const onDragStart = (e, order) => {
  draggingOrder.value = order
  draggingOrderId.value = order.id
  draggingFromResolved.value = order.status === 'RESOLVED'
  // 不暂停轮询：上次迭代的 ID 重取方案已足够防护拖拽-轮询竞态；
  // 暂停反而会让 onDrop 后 startPolling() 立刻补一次 API + 重排 timer，造成视觉抖动
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(order.id))
}

// 拖入列时的统一处理：已闭环的卡不允许拖到 PENDING/IN_PROGRESS 列
const onDragOver = (e, targetStatus) => {
  if (draggingFromResolved.value && targetStatus !== 'RESOLVED') {
    e.dataTransfer.dropEffect = 'none'   // 浏览器显示 not-allowed 光标
    return
  }
  e.dataTransfer.dropEffect = 'move'
  dragOver.value = targetStatus
}

const onDrop = async (targetStatus) => {
  // 🔧 用 id 从最新 allOrders 重取对象，杜绝 5s 轮询中途替换数组导致的陈旧引用
  const orderId = draggingOrderId.value
  dragOver.value = null
  draggingOrder.value = null
  draggingOrderId.value = null
  draggingFromResolved.value = false
  if (!orderId) return
  const order = allOrders.value.find(o => o.id === orderId)
  if (!order) {
    ElMessage.warning('该工单已不存在（可能刚被删除），操作已取消')
    return
  }
  if (order.status === targetStatus) return

  // 🔒 前端兜底：即使浏览器允许 drop，也要在请求前再校验一次
  if (order.status === 'RESOLVED' && targetStatus !== 'RESOLVED') {
    ElMessage.warning('已闭环工单不可重新打开，如需反工请创建新工单')
    return
  }

  const oldStatus = order.status

  try {
    // 🔴 严重问题 #3 修正：拖拽改 status 走 8080 现有 PATCH，不走 8081
    await patchWorkOrderStatus(order.id, { status: targetStatus })

    // 🟢 Mode A：status 与 assignee 解耦。拖拽仅改 status，assignee 由抽屉显式管理。
    //   拖回 PENDING 时若仍有指派人，toast 显式提示"已保留"，避免操作员误以为被释放。
    let msg = `已将 ${order.orderNo} 移到${statusLabel(targetStatus)}`
    const retained = order.activeAssignments?.length || 0
    if (targetStatus === 'PENDING' && retained > 0) {
      msg += `（${retained} 名指派人保留，由抽屉"释放"按钮管理）`
    }
    ElMessage.success(msg)

    await refreshNow()
  } catch (e) {
    // 失败时 http.js 已提示
  }
}

const statusLabel = (s) => ({ PENDING: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已完成' }[s] || s)

// ---- 详情抽屉 ----
const drawerOpen = ref(false)
const currentOrder = ref(null)
const openDetail = (o) => {
  // 🆕 防御性校验：工单应仍在 allOrders（虽然通常从 kanban 点击一定在）
  if (!allOrders.value.some(x => x.id === o.id)) {
    ElMessage.warning('该工单不存在或已被删除')
    return
  }
  currentOrder.value = o
  drawerOpen.value = true
}

// 🔧 同步 currentOrder：抽屉里执行"指派/替换/释放"后，后端已更新但 currentOrder
//    还指向打开抽屉时的旧对象，导致 activeList 不刷新；轮询也只刷 allOrders 不刷 currentOrder。
//    监听 allOrders 变化时按 id 重新指向最新对象，drawer 立即反映最新数据。
watch(allOrders, (newList) => {
  if (!currentOrder.value || !Array.isArray(newList)) return
  const id = currentOrder.value.id
  const updated = newList.find(o => o.id === id)
  // 🆕 兜底：抽屉打开期间，如果 currentOrder 对应的工单已被另一标签页删除
  //   新列表里查不到 → 关闭抽屉并提示，避免显示陈旧数据
  if (!updated) {
    if (drawerOpen.value) {
      ElMessage.warning(`工单 #${id} 已被删除（可能在另一标签页），抽屉已关闭`)
      drawerOpen.value = false
    }
    currentOrder.value = null
    return
  }
  if (updated !== currentOrder.value) {
    currentOrder.value = updated
  }
})

// ===== 🆕 新建工单对话框 =====
const createDialogOpen = ref(false)
const createSubmitting = ref(false)
const snapshotLoading = ref(false)
const deviceOptions = ref([])
const sensorSnapshot = ref(null)

const faultTypeOptions = faultTypeMeta   // 复用 utils/status.js
const priorityOptions = priorityMeta

const createForm = ref({
  deviceId: null,
  faultType: '',
  priority: 'MEDIUM',
  title: '',
  description: ''
})

const openCreateDialog = async () => {
  createDialogOpen.value = true
  if (deviceOptions.value.length === 0) {
    try {
      const res = await getDevices({ page: 1, size: 200 })
      deviceOptions.value = res.records || []
    } catch (e) {
      // http.js 已 toast
    }
  }
}

const onDeviceChange = async (deviceId) => {
  sensorSnapshot.value = null
  if (!deviceId) return
  const device = deviceOptions.value.find(d => d.id === deviceId)
  if (!device) return
  snapshotLoading.value = true
  try {
    const data = await getLatestSensor(device.deviceCode)
    sensorSnapshot.value = data
  } catch (e) {
    sensorSnapshot.value = null   // http.js 已 toast；让用户看到"暂无"
  } finally {
    snapshotLoading.value = false
  }
}

const formatSnapshotVal = (v) => (v == null ? '—' : Number(v).toFixed(1))
const formatSnapshotTime = (iso) => {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}

const resetCreateForm = () => {
  createForm.value = {
    deviceId: null, faultType: '', priority: 'MEDIUM', title: '', description: ''
  }
  sensorSnapshot.value = null
}

const submitCreate = async () => {
  // 🔒 防重复提交：函数入口 + 按钮 disabled 双保险
  if (createSubmitting.value) return
  const f = createForm.value
  if (!f.deviceId || !f.faultType || !f.priority || !f.title.trim() || !f.description.trim()) {
    ElMessage.warning('请填写所有必填项')
    return
  }
  createSubmitting.value = true
  try {
    const created = await createWorkOrder({
      deviceId: f.deviceId,
      title: f.title.trim(),
      faultType: f.faultType,
      priority: f.priority,
      description: f.description.trim()
    })
    ElMessage.success(`工单 ${created.orderNo} 已创建`)
    createDialogOpen.value = false
    await refreshNow()        // 看板立即刷出新卡片
  } catch (e) {
    // http.js 拦截器已 toast
  } finally {
    createSubmitting.value = false
  }
}

const confirmHandle = async (o) => {
  try {
    await patchWorkOrderStatus(o.id, { status: 'IN_PROGRESS' })
    ElMessage.success('已确认处理')
    drawerOpen.value = false
    await refreshNow()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

const markResolved = async (o) => {
  try {
    await ElMessageBox.confirm(`确认 ${o.orderNo} 已修复？`, '闭环确认', {
      type: 'success',
      confirmButtonText: '确认闭环',
      cancelButtonText: '取消'
    })
    await patchWorkOrderStatus(o.id, { status: 'RESOLVED' })
    ElMessage.success('工单已闭环')
    drawerOpen.value = false
    await refreshNow()
  } catch (e) {
    if (e === 'cancel') return
  }
}

const goDispatch = () => router.push('/maintenance/dispatch')

onMounted(() => {
  startPolling()
})
</script>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #e0f2fe;
  letter-spacing: 1px;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 0.5px;
}

.header-tools {
  display: flex;
  gap: 10px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}

.kanban-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.kanban-col {
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;  /* 给 col-locked::before 水印提供定位锚点 */
  transition: background 0.2s ease, border-color 0.2s ease, border-style 0.2s ease;
}

.kanban-col.col-dragover {
  background: rgba(82, 200, 255, 0.08);
  border-color: rgba(82, 200, 255, 0.45);
}

/* 🔒 当拖动的是已闭环卡片时，目标列（RESOLVED 以外）显示禁止态 */
.kanban-col.col-locked {
  background: rgba(244, 114, 182, 0.06);
  border-color: rgba(244, 114, 182, 0.35);
  border-style: dashed;
}
.kanban-col.col-locked::before {
  content: '已闭环工单不可拖入';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  color: rgba(244, 114, 182, 0.85);
  pointer-events: none;
  letter-spacing: 1.5px;
  background: rgba(15, 23, 42, 0.55);
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid rgba(244, 114, 182, 0.3);
  z-index: 2;
}

.col-head {
  padding: 14px 16px 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.col-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #e0f2fe;
}

.col-title .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.head-pending .dot     { background: #ff9f43; box-shadow: 0 0 10px rgba(255, 159, 67, 0.6); }
.head-in-progress .dot { background: #52c8ff; box-shadow: 0 0 10px rgba(82, 200, 255, 0.6); }
.head-resolved .dot    { background: #3bff9f; box-shadow: 0 0 10px rgba(59, 255, 159, 0.6); }

.count-badge :deep(.el-badge__content) {
  background: rgba(15, 23, 42, 0.7);
  color: #e0f2fe;
  border: 1px solid rgba(148, 163, 184, 0.3);
  font-weight: 600;
}

.col-sub {
  font-size: 11px;
  color: var(--text-secondary);
  letter-spacing: 0.5px;
}

.col-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 12px 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(82, 200, 255, 0.3) transparent;
}

.col-body::-webkit-scrollbar {
  width: 6px;
}
.col-body::-webkit-scrollbar-thumb {
  background: rgba(82, 200, 255, 0.3);
  border-radius: 3px;
}

.empty-tip {
  padding: 40px 0;
  text-align: center;
  color: rgba(148, 163, 184, 0.5);
  font-size: 12px;
}

/* 🆕 新建工单对话框：当前传感器快照只读展示 */
.snapshot-row {
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  font-size: 13px;
  color: #e0f2fe;
  font-family: 'SF Mono', Consolas, monospace;
}
.snapshot-row .snapshot-time {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-secondary);
}
.snapshot-muted {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: italic;
}

@media (max-width: 1200px) {
  .stat-row { grid-template-columns: repeat(2, 1fr); }
  .kanban-row { grid-template-columns: 1fr; }
}
</style>
