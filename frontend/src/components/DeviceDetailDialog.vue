<script setup>
import { computed, nextTick, shallowRef, watch } from 'vue'
import { getDeviceDetail, getDeviceFaultHistory, getDeviceHealthScore, getSensorHistory } from '../api'
import StatusPill from './StatusPill.vue'
import TrendChart from './TrendChart.vue'

const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({
  device: { type: Object, default: null }
})

const loading = shallowRef(false)
const detail = shallowRef(null)
const history = shallowRef([])
const faults = shallowRef([])
const health = shallowRef(null)
const trendChartRef = shallowRef(null)
const title = computed(() => `${detail.value?.deviceName || props.device?.deviceName || '设备'} · 实时详情`)

// 负荷趋势可选时间范围
const rangeHours = shallowRef(3)
const rangeOptions = [
  { label: '1 小时', value: 1 },
  { label: '3 小时', value: 3 },
  { label: '12 小时', value: 12 }
]
const historyLoading = shallowRef(false)

const format = (value, unit = '') => value ?? value === 0 ? `${Number(value).toFixed(2)}${unit}` : '--'
const healthColor = computed(() => {
  const score = health.value?.overallScore || 0
  return score >= 80 ? '#3bff9f' : score >= 60 ? '#ffb347' : '#ff5d5d'
})

const load = async () => {
  if (!visible.value || !props.device?.id) return
  loading.value = true
  try {
    const [detailResult, historyResult, faultResult, healthResult] = await Promise.all([
      getDeviceDetail(props.device.id),
      getSensorHistory(props.device.deviceCode, rangeHours.value),
      getDeviceFaultHistory(props.device.id),
      getDeviceHealthScore(props.device.id)
    ])
    detail.value = detailResult
    history.value = historyResult || []
    faults.value = faultResult || []
    health.value = healthResult
  } finally {
    loading.value = false
  }
}

// 仅切换时间范围时，单独重新拉取负荷历史，不重载整个弹窗
const loadHistory = async () => {
  if (!visible.value || !props.device?.deviceCode) return
  historyLoading.value = true
  try {
    history.value = await getSensorHistory(props.device.deviceCode, rangeHours.value) || []
    await resizeTrendChart()
  } finally {
    historyLoading.value = false
  }
}

watch(() => [visible.value, props.device?.id], load)
watch(rangeHours, loadHistory)

const resizeTrendChart = async () => {
  await nextTick()
  trendChartRef.value?.resize()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="min(920px, calc(100vw - 32px))"
    destroy-on-close
    @opened="resizeTrendChart"
  >
    <div v-loading="loading" class="device-detail">
      <div class="detail-head">
        <div>
          <p>{{ detail?.deviceCode }} · {{ detail?.location }}</p>
          <StatusPill :status="detail?.status" />
        </div>
        <div class="health-ring" :style="{ borderColor: healthColor, color: healthColor }">
          <strong>{{ health?.overallScore ?? '--' }}</strong>
          <span>健康度</span>
        </div>
      </div>

      <div class="metric-grid">
        <div><span>实时功率</span><strong>{{ format(detail?.usageKwh, ' 千瓦时') }}</strong></div>
        <div><span>温度</span><strong>{{ format(detail?.temperature, ' ℃') }}</strong></div>
        <div><span>压力</span><strong>{{ format(detail?.pressure, ' 千帕') }}</strong></div>
        <div><span>振动</span><strong>{{ format(detail?.vibration, ' 毫米/秒') }}</strong></div>
      </div>

      <div class="detail-panel">
        <div class="panel-row">
          <h4>过去 {{ rangeHours }} 小时负荷趋势</h4>
          <el-segmented v-model="rangeHours" :options="rangeOptions" size="small" />
        </div>
        <TrendChart ref="trendChartRef" v-loading="historyLoading" :records="history" />
      </div>

      <div class="detail-panel">
        <h4>故障历史</h4>
        <el-table :data="faults" max-height="230">
          <el-table-column prop="createdAt" label="时间" min-width="160" />
          <el-table-column prop="title" label="故障" min-width="200" />
          <el-table-column prop="priority" label="优先级" width="90" />
          <el-table-column prop="assignee" label="负责人" width="100" />
          <el-table-column prop="status" label="状态" width="120" />
        </el-table>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.device-detail,
.detail-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-head p {
  margin: 0 0 8px;
  color: var(--text-secondary);
}

.health-ring {
  display: grid;
  place-items: center;
  width: 92px;
  height: 92px;
  border: 8px solid;
  border-radius: 50%;
}

.health-ring strong {
  font-size: 25px;
}

.health-ring span {
  font-size: 11px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.metric-grid div,
.detail-panel {
  padding: 16px;
  border: 1px solid rgba(82, 200, 255, .15);
  border-radius: 16px;
  background: rgba(15, 23, 42, .55);
}

.metric-grid span {
  display: block;
  margin-bottom: 7px;
  color: var(--text-secondary);
  font-size: 12px;
}

.metric-grid strong {
  color: var(--accent-blue);
}

.detail-panel h4 {
  margin: 0;
}

.panel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-panel :deep(.chart-box) {
  min-width: 0;
  height: clamp(220px, 32vh, 360px);
}

@media (max-width: 720px) {
  .detail-head {
    align-items: flex-start;
  }

  .health-ring {
    width: 72px;
    height: 72px;
    border-width: 6px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
