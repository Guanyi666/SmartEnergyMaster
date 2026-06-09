<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">实时监控指挥大屏</h2>
        <p class="page-subtitle">页面每 5 秒自动刷新一次，切回浏览器标签页时也会立即同步最新数据。</p>
      </div>
      <el-select v-model="focusDeviceCode" style="width: 220px" @change="refreshNow">
        <el-option v-for="device in devices" :key="device.id" :label="device.deviceName" :value="device.deviceCode" />
      </el-select>
    </div>

    <div class="grid-three">
      <MetricCard label="全厂总有功功率" :value="formatNumber(summary.totalUsageKwh)" unit="kWh" note="当前接入设备聚合值" color="#52c8ff" />
      <MetricCard label="当前累计碳排放" :value="formatNumber(summary.totalCo2Emission)" unit="tCO2" note="按实时采集数据估算" color="#3bff9f" />
      <MetricCard label="当前电价区间" :value="priceMeta.label" note="根据焦点设备当前时段自动切换" :color="priceMeta.color" />
    </div>

    <div class="section-spacer">
      <AlertTicker :alerts="alerts" />
    </div>

    <div class="grid-two section-spacer">
      <div class="glass-panel chart-panel">
        <div class="panel-header">
          <div>
            <h3 class="card-title">{{ focusDevice?.deviceName || '1号电弧炉' }} 实时仪表</h3>
            <p class="muted">温度与压力会随着后端实时数据自动跳动。</p>
          </div>
          <StatusPill :status="focusDevice?.status" />
        </div>
        <div class="gauge-grid">
          <GaugeChart :value="Number(latestData.temperature || 0)" title="温度" unit="°C" :max="1400" color="#ff9f43" />
          <GaugeChart :value="Number(latestData.pressure || 0)" title="压力" unit="kPa" :max="220" color="#52c8ff" />
        </div>
      </div>

      <div class="glass-panel side-panel">
        <h3 class="card-title">AI 智能调度建议</h3>
        <div class="advice-card" :class="`advice-${(summary.dispatchAdvice?.level || 'INFO').toLowerCase()}`">
          <strong>{{ summary.dispatchAdvice?.title || '暂无建议' }}</strong>
          <p>{{ summary.dispatchAdvice?.content || '等待实时数据接入。' }}</p>
        </div>

        <h3 class="card-title section-title">待处理维修工单</h3>
        <div class="order-list">
          <div v-for="order in alerts.slice(0, 4)" :key="order.id" class="order-item">
            <div>
              <strong>{{ order.title }}</strong>
              <p>{{ order.deviceName }} / {{ order.assignee }}</p>
            </div>
            <div class="order-actions">
              <el-button v-if="order.status === 'PENDING'" size="small" @click="changeOrderStatus(order, 'IN_PROGRESS')">确认处理</el-button>
              <el-button v-if="order.status !== 'RESOLVED'" type="success" size="small" @click="changeOrderStatus(order, 'RESOLVED')">已修复</el-button>
            </div>
          </div>
          <div v-if="!alerts.length" class="muted">当前没有待处理工单。</div>
        </div>
      </div>
    </div>

    <div class="glass-panel section-spacer device-strip">
      <div class="device-strip-head">
        <h3 class="card-title">设备状态总览</h3>
        <span class="muted">支持运行中、停机、离线、故障待处理、维修中等状态</span>
      </div>
      <div class="device-strip-grid">
        <div v-for="device in devices" :key="device.id" class="device-tile" @click="selectDevice(device.deviceCode)">
          <div class="tile-top">
            <strong>{{ device.deviceName }}</strong>
            <StatusPill :status="device.status" />
          </div>
          <p>{{ device.location || '未配置位置' }} / {{ device.maintainer || '待分配' }}</p>
          <div class="tile-metrics">
            <span>功率 {{ formatNumber(device.usageKwh) }}</span>
            <span>温度 {{ formatNumber(device.temperature) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MetricCard from '../components/MetricCard.vue'
import StatusPill from '../components/StatusPill.vue'
import GaugeChart from '../components/GaugeChart.vue'
import AlertTicker from '../components/AlertTicker.vue'
import { getActiveAlerts, getDashboardSummary, getDevices, getLatestSensor, updateWorkOrderStatus } from '../api'
import { usePollingTask } from '../composables/usePollingTask'
import { getPriceTierMeta } from '../utils/status'

const summary = ref({})
const devices = ref([])
const latestData = ref({})
const alerts = ref([])
const focusDeviceCode = ref('EAF-01')

const focusDevice = computed(() => devices.value.find((device) => device.deviceCode === focusDeviceCode.value))
const priceMeta = computed(() => getPriceTierMeta(summary.value.currentPriceTier))

const formatNumber = (value) => (value ?? value === 0 ? Number(value).toFixed(2) : '--')

const loadAll = async () => {
  const [summaryResult, devicesResult, latestResult, alertsResult] = await Promise.all([
    getDashboardSummary(focusDeviceCode.value),
    getDevices({ size: 999 }),
    getLatestSensor(focusDeviceCode.value),
    getActiveAlerts(8)
  ])

  summary.value = summaryResult
  devices.value = devicesResult.records || devicesResult
  latestData.value = typeof latestResult === 'string' ? {} : latestResult
  alerts.value = alertsResult

  if (!focusDevice.value && devices.value.length) {
    focusDeviceCode.value = devices.value[0].deviceCode
  }
}

const { start: startPolling, run: refreshNow } = usePollingTask(loadAll, 5000)

const selectDevice = async (deviceCode) => {
  focusDeviceCode.value = deviceCode
  await refreshNow()
}

const changeOrderStatus = async (order, status) => {
  await updateWorkOrderStatus(order.id, {
    status,
    assignee: order.assignee
  })
  ElMessage.success(status === 'IN_PROGRESS' ? '工单已确认处理' : '工单已完成闭环')
  await refreshNow()
}

onMounted(async () => {
  await startPolling()
})
</script>

<style scoped>
.chart-panel,
.side-panel,
.device-strip {
  padding: 20px;
}

.panel-header,
.device-strip-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.gauge-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.advice-card {
  padding: 18px;
  border-radius: 18px;
  margin-bottom: 20px;
  border: 1px solid rgba(82, 200, 255, 0.2);
  background: rgba(15, 23, 42, 0.7);
}

.advice-card p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.advice-warn {
  border-color: rgba(255, 159, 67, 0.45);
}

.advice-critical {
  border-color: rgba(255, 93, 93, 0.5);
}

.advice-good {
  border-color: rgba(59, 255, 159, 0.45);
}

.section-title {
  margin-top: 12px;
}

.order-list {
  display: grid;
  gap: 12px;
}

.order-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.62);
}

.order-item p,
.device-tile p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.device-strip-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.device-tile {
  padding: 16px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.6);
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease;
  border: 1px solid rgba(82, 200, 255, 0.12);
}

.device-tile:hover {
  transform: translateY(-2px);
  border-color: rgba(82, 200, 255, 0.32);
}

.tile-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.tile-metrics {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  color: #dbeafe;
  font-size: 13px;
}

@media (max-width: 1024px) {
  .gauge-grid,
  .device-strip-grid {
    grid-template-columns: 1fr;
  }

  .order-item,
  .tile-top,
  .tile-metrics {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
