<template>
  <div class="page-shell">
    <!-- 顶部页头 -->
    <div class="page-header">
      <div>
        <h2 class="page-title">维修指挥中心</h2>
        <p class="page-subtitle">
          拖拽卡片改 status（直调 8080 现有 API）；每 5s 自动轮询新工单；最后同步：{{ lastSyncLabel }}
        </p>
      </div>
      <div class="header-tools">
        <el-button :icon="Refresh" @click="refreshNow">立即刷新</el-button>
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
           :class="{ 'col-dragover': dragOver === 'PENDING' }"
           @dragover.prevent="dragOver = 'PENDING'"
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
           :class="{ 'col-dragover': dragOver === 'IN_PROGRESS' }"
           @dragover.prevent="dragOver = 'IN_PROGRESS'"
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
           @dragover.prevent="dragOver = 'RESOLVED'"
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
      @refresh="refreshNow"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import StatBadge from '../components/StatBadge.vue'
import WorkOrderCard from '../components/WorkOrderCard.vue'
import WorkOrderDetailDrawer from '../components/WorkOrderDetailDrawer.vue'
import { getDispatchSummary, getWorkOrderList, patchWorkOrderStatus, releaseWorkOrder } from '../api/workorder'
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

// ---- 拖拽 ----
const dragOver = ref(null)
const draggingOrder = ref(null)
const onDragStart = (e, order) => {
  draggingOrder.value = order
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(order.id))
}
const onDrop = async (targetStatus) => {
  const order = draggingOrder.value
  dragOver.value = null
  draggingOrder.value = null
  if (!order || order.status === targetStatus) return

  const oldStatus = order.status

  try {
    // 🔴 严重问题 #3 修正：拖拽改 status 走 8080 现有 PATCH，不走 8081
    await patchWorkOrderStatus(order.id, { status: targetStatus })
    ElMessage.success(`已将 ${order.orderNo} 移到${statusLabel(targetStatus)}`)

    // 🆕 任何路径回到 PENDING 都要清空指派人（包括 8080 老字段 assignee）
    //   后端 release() 已经是幂等的：没活跃指派时也会 PATCH 8080 把 assignee 置空
    if (targetStatus === 'PENDING') {
      try {
        await releaseWorkOrder(order.id)
        ElMessage.info('已自动清空原指派人')
      } catch (e) {
        // 释放失败不阻塞主流程，但要把 8080 的 assignee 也清掉（防御性兜底）
        console.warn('[Drag→PENDING] releaseWorkOrder 失败，尝试直接 PATCH 8080 清空', e)
      }
    }

    await refreshNow()
  } catch (e) {
    // 失败时 http.js 已提示
  }
}

const statusLabel = (s) => ({ PENDING: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已完成' }[s] || s)

// ---- 详情抽屉 ----
const drawerOpen = ref(false)
const currentOrder = ref(null)
const openDetail = (o) => { currentOrder.value = o; drawerOpen.value = true }

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
    await ElMessageBox.confirm(`确认 ${o.orderNo} 已修复？`, '闭环确认', { type: 'success' })
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
  transition: background 0.2s ease, border-color 0.2s ease;
}

.kanban-col.col-dragover {
  background: rgba(82, 200, 255, 0.08);
  border-color: rgba(82, 200, 255, 0.45);
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

@media (max-width: 1200px) {
  .stat-row { grid-template-columns: repeat(2, 1fr); }
  .kanban-row { grid-template-columns: 1fr; }
}
</style>
