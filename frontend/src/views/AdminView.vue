<script setup>
import { computed, onMounted, shallowRef } from 'vue'
import MetricCard from '../components/MetricCard.vue'
import InsightChart from '../components/InsightChart.vue'
import { getDashboardSummary, getDevices, getSensorHistory } from '../api'
import { usePollingTask } from '../composables/usePollingTask'
import { getStatusMeta } from '../utils/status'

const statusLabel = (s) => getStatusMeta(s).label

const summary = shallowRef({})
const devices = shallowRef([])
const history = shallowRef([])
const period = shallowRef('day')

const kpis = computed(() => {
  const usage = Number(summary.value.totalUsageKwh || 0)
  const carbon = Number(summary.value.totalCo2Emission || 0)
  return [
    { label: '今日总电费', value: `¥${(usage * .68).toFixed(0)}`, note: '按实时累计用电估算', color: '#52c8ff' },
    { label: '本月累计碳排', value: `${(carbon / 1000).toFixed(2)} t`, note: '配额使用 63%', color: '#a78bfa' },
    { label: '环比节能率', value: '8.6%', note: '较上月提升 2.1%', color: '#3bff9f' },
    { label: 'OEE', value: `${Number(summary.value.equipmentUtilization || 0).toFixed(1)}%`, note: '目标 85%', color: '#ffb347' }
  ]
})
const trendLabels = computed(() => history.value.slice(-14).map((item) => new Date(item.time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })))
const energySeries = computed(() => [
  { name: '本期能耗', data: history.value.slice(-14).map((item) => Number(item.usageKwh || 0)), area: true },
  { name: '同期能耗', data: history.value.slice(-14).map((item) => Number(item.usageKwh || 0) * 1.08) }
])
const carbonSeries = computed(() => [{ name: '碳排放', type: 'bar', data: history.value.slice(-14).map((item) => Number(item.co2Emission || 0)) }])
const ranking = computed(() => [...devices.value].sort((a, b) => Number(b.usageKwh || 0) - Number(a.usageKwh || 0)))

const load = async () => {
  const deviceResult = await getDevices({ size: 999 })
  devices.value = deviceResult.records || []
  // 趋势焦点取实时能耗最高的设备：仿真器只向部分设备推送遥测，
  // 若盲取第一台（可能停机/离线/无历史）会导致能耗、碳排趋势曲线为空
  const focusCode = [...devices.value]
    .sort((a, b) => Number(b.usageKwh || 0) - Number(a.usageKwh || 0))[0]?.deviceCode
    || devices.value[0]?.deviceCode || 'EAF-01'
  const [summaryResult, historyResult] = await Promise.all([getDashboardSummary(focusCode), getSensorHistory(focusCode, period.value === 'month' ? 720 : period.value === 'week' ? 168 : 24)])
  summary.value = summaryResult
  history.value = historyResult || []
}
const { start, run } = usePollingTask(load, 30000)
onMounted(start)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">生产经理决策仪表盘</h2>
        <p class="page-subtitle">聚合成本、碳排、节能率与设备综合效率，支持经营决策。</p>
      </div>
      <el-segmented v-model="period" :options="[{ label: '日', value: 'day' }, { label: '周', value: 'week' }, { label: '月', value: 'month' }]" @change="run" />
    </div>

    <div class="kpi-grid">
      <MetricCard v-for="item in kpis" :key="item.label" v-bind="item" />
    </div>

    <div class="admin-grid section-spacer">
      <section class="glass-panel panel wide">
        <h3 class="card-title">能耗趋势 · 同比环比</h3>
        <InsightChart :labels="trendLabels" :series="energySeries" :height="310" />
      </section>
      <section class="glass-panel panel">
        <h3 class="card-title">碳排放趋势</h3>
        <InsightChart :labels="trendLabels" :series="carbonSeries" :height="250" />
        <div class="quota">
          <div><span>本月配额已用</span><strong>63%</strong></div>
          <el-progress :percentage="63" color="#a78bfa" :stroke-width="12" :show-text="false" />
          <small>剩余配额 184.6 吨二氧化碳</small>
        </div>
      </section>
      <section class="glass-panel panel">
        <h3 class="card-title">设备能耗排名</h3>
        <el-table :data="ranking" max-height="330">
          <el-table-column type="index" label="#" width="45" />
          <el-table-column prop="deviceName" label="设备" min-width="140" />
          <el-table-column label="实时能耗" width="110">
            <template #default="{ row }">{{ Number(row.usageKwh || 0).toFixed(1) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
        </el-table>
      </section>
      <section class="glass-panel panel execution">
        <h3 class="card-title">调度建议执行率</h3>
        <el-progress type="dashboard" :percentage="78" color="#3bff9f" :width="180" />
        <p>本月已执行 36 条，节省电费约 ¥28,640</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.admin-grid {
  display: grid;
  grid-template-columns: 1.4fr .8fr;
  gap: 18px;
}

.panel {
  padding: 20px;
}

.wide {
  grid-column: 1 / -1;
}

.quota,
.execution {
  display: grid;
  gap: 12px;
}

.quota div {
  display: flex;
  justify-content: space-between;
}

.quota span,
.quota small,
.execution p {
  color: var(--text-secondary);
}

.execution {
  place-items: center;
  text-align: center;
}

@media (max-width: 1100px) {
  .kpi-grid,
  .admin-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
