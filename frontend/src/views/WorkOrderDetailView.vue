<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">工单详情</h2>
        <p class="page-subtitle">工单序号：{{ workOrderId }}</p>
      </div>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <div v-if="loading" v-loading="true" class="loading-area"></div>

    <template v-else-if="order">
      <div class="glass-panel status-banner" :class="`status-${(order.status || '').toLowerCase()}`">
        <div>
          <p class="banner-mark">{{ order.orderNo }}</p>
          <h2>{{ order.title }}</h2>
          <div class="meta">
            <span><el-icon><Cpu /></el-icon> {{ order.deviceName }} · {{ order.deviceCode }}</span>
            <span><el-icon><Flag /></el-icon> {{ priorityLabel(order.priority) }}</span>
            <span><el-icon><Clock /></el-icon> 创建于 {{ formatTime(order.createdAt) }}</span>
          </div>
        </div>
        <div class="status-text">{{ statusLabel(order.status) }}</div>
      </div>

      <div class="info-grid">
        <!-- 触发时传感器 -->
        <div class="glass-panel card">
          <h3><el-icon><DataLine /></el-icon> 触发时刻传感器</h3>
          <div class="metric-grid">
            <div class="metric">
              <span class="m-label">温度</span>
              <span class="m-value">{{ formatNum(order.latestTemperature) }}<i>℃</i></span>
            </div>
            <div class="metric">
              <span class="m-label">压力</span>
              <span class="m-value">{{ formatNum(order.latestPressure) }}<i>千帕</i></span>
            </div>
            <div class="metric">
              <span class="m-label">振动</span>
              <span class="m-value">{{ formatNum(order.latestVibration) }}<i>毫米/秒</i></span>
            </div>
          </div>
        </div>

        <!-- 指派人 -->
        <div class="glass-panel card">
          <h3><el-icon><User /></el-icon> 当前指派人</h3>
          <div v-if="order.assigneeName" class="assignee-block">
            <div class="big-avatar">{{ (order.assigneeName || '?').charAt(0) }}</div>
            <div>
              <strong>{{ order.assigneeName }}</strong>
              <p class="time">指派于 {{ formatTime(order.assignedAt) }}</p>
            </div>
          </div>
          <div v-else class="empty">尚未指派</div>
        </div>

        <!-- 故障描述 -->
        <div class="glass-panel card">
          <h3><el-icon><InfoFilled /></el-icon> 故障描述</h3>
          <p class="desc">{{ order.description || '—' }}</p>
        </div>

        <!-- 指派历史 -->
        <div class="glass-panel card">
          <h3><el-icon><Document /></el-icon> 指派历史</h3>
          <el-timeline v-if="assignments.length">
            <el-timeline-item
              v-for="a in assignments" :key="a.id"
              :timestamp="formatTime(a.assignedAt)"
              :type="a.releasedAt ? 'warning' : 'primary'"
            >
              <strong>{{ a.personnelName }}</strong>
              <span class="role-tag">{{ getRoleLabel(a.role) }}</span>
              <p v-if="a.releasedAt" class="released">已释放于 {{ formatTime(a.releasedAt) }}</p>
              <p v-if="a.note" class="note">{{ a.note }}</p>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="empty">无指派记录</div>
        </div>
      </div>
    </template>

    <div v-else class="empty-tip">工单不存在</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Cpu, Flag, Clock, DataLine, User, InfoFilled, Document } from '@element-plus/icons-vue'
import { getWorkOrderDetail, getWorkOrderAssignments } from '../api/workorder'
import { getRoleLabel } from '../utils/status'

const route = useRoute()
const workOrderId = computed(() => Number(route.params.id))
const order = ref(null)
const assignments = ref([])
const loading = ref(false)

const PRIORITY_LABELS = { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityLabel = (p) => PRIORITY_LABELS[p] || p || '高'

const STATUS_LABELS = { PENDING: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已完成' }
const statusLabel = (s) => STATUS_LABELS[s] || s || '—'

const formatNum = (v) => (v == null ? '—' : Number(v).toFixed(1))
const formatTime = (iso) => {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}

const load = async () => {
  if (!workOrderId.value) return
  loading.value = true
  try {
    const [o, a] = await Promise.all([
      getWorkOrderDetail(workOrderId.value),
      getWorkOrderAssignments(workOrderId.value)
    ])
    order.value = o
    assignments.value = a || []
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, load, { immediate: true })
onMounted(load)
</script>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 16px; }

.page-header { display: flex; justify-content: space-between; align-items: flex-start; }

.page-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 3px;
  background: linear-gradient(90deg, #5cdcff, #3da9ff);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.page-subtitle { margin: 4px 0 0; font-size: 12px; color: var(--text-secondary); }

.loading-area { min-height: 200px; }

.status-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px;
  border-left-width: 4px;
  border-left-style: solid;
}

.status-banner.status-pending     { border-left-color: #ff9f43; }
.status-banner.status-in_progress { border-left-color: #52c8ff; }
.status-banner.status-resolved    { border-left-color: #3bff9f; }

.banner-mark {
  margin: 0 0 4px;
  font-size: 11px;
  font-family: 'SF Mono', Consolas, monospace;
  color: var(--text-secondary);
  letter-spacing: 1px;
}

.status-banner h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #ffffff;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: var(--text-secondary);
}

.meta span { display: inline-flex; align-items: center; gap: 4px; }

.status-text {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
}

.status-banner.status-pending .status-text     { color: #ff9f43; }
.status-banner.status-in_progress .status-text { color: #52c8ff; }
.status-banner.status-resolved .status-text    { color: #3bff9f; }

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.card { padding: 18px 20px; }

.card h3 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 12px;
  font-size: 14px;
  color: #ffffff;
  font-weight: 500;
  letter-spacing: 1px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.metric {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.m-label { font-size: 11px; color: var(--text-secondary); }
.m-value {
  font-size: 20px;
  font-weight: 600;
  color: #ffffff;
  font-family: 'SF Mono', Consolas, monospace;
}
.m-value i { font-style: normal; font-size: 11px; color: var(--text-secondary); margin-left: 4px; font-weight: 400; }

.assignee-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.big-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #52c8ff, #a78bfa);
  display: grid;
  place-items: center;
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
}

.assignee-block strong { font-size: 16px; color: #ffffff; }
.assignee-block .time { margin: 2px 0 0; font-size: 11px; color: var(--text-secondary); }

.empty { color: var(--text-secondary); font-size: 13px; }

.desc {
  margin: 0;
  padding: 12px 14px;
  background: rgba(15, 23, 42, 0.5);
  border-left: 3px solid #52c8ff;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #ffffff;
}

.role-tag {
  font-size: 10px;
  padding: 1px 6px;
  margin-left: 6px;
  background: rgba(82, 200, 255, 0.18);
  color: #52c8ff;
  border-radius: 6px;
}

.released { font-size: 12px; color: #ff9f43; margin: 4px 0 0; }
.note { font-size: 12px; color: var(--text-secondary); margin: 4px 0 0; font-style: italic; }

.empty-tip {
  padding: 60px 0;
  text-align: center;
  color: var(--text-secondary);
}

@media (max-width: 900px) {
  .info-grid { grid-template-columns: 1fr; }
}
</style>
