<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">历史负荷与 AI 调度页</h2>
        <p class="page-subtitle">页面每 10 秒自动刷新一次，负荷曲线会持续吸收最新的实时数据。</p>
      </div>
      <div class="analysis-tools">
        <el-select v-model="deviceCode" style="width: 220px" @change="refreshNow">
          <el-option v-for="device in devices" :key="device.id" :label="device.deviceName" :value="device.deviceCode" />
        </el-select>
        <el-button @click="refreshNow">立即刷新</el-button>
      </div>
    </div>

    <div class="grid-two">
      <div class="glass-panel analysis-panel">
        <h3 class="card-title">过去 24 小时负荷曲线</h3>
        <TrendChart :records="history" />
      </div>

      <div class="analysis-side">
        <div class="glass-panel advice-panel">
          <h3 class="card-title">AI 智能调度建议</h3>
          <div class="advice-banner" :class="`banner-${(advice.level || 'INFO').toLowerCase()}`">
            <strong>{{ advice.title || '暂无建议' }}</strong>
            <p>{{ advice.content || '等待数据加载。' }}</p>
          </div>
          <div class="analysis-facts">
            <div>
              <span>当前价格区间</span>
              <strong>{{ priceMeta.label }}</strong>
            </div>
            <div>
              <span>当前负荷</span>
              <strong>{{ latestUsage }}</strong>
            </div>
          </div>
        </div>

        <div class="glass-panel stat-panel">
          <h3 class="card-title">负荷解读</h3>
          <ul class="fact-list">
            <li>红色背景代表峰段或尖峰时段，适合演示避峰就谷策略。</li>
            <li>绿色背景代表低谷时段，适合安排可转移的高耗能工序。</li>
            <li>页面自动轮询后，历史曲线和调度建议会随实时上报数据联动更新。</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import TrendChart from '../components/TrendChart.vue'
import { getDevices, getDispatchAdvice, getLatestSensor, getSensorHistory } from '../api'
import { usePollingTask } from '../composables/usePollingTask'
import { getPriceTierMeta } from '../utils/status'

const devices = ref([])
const deviceCode = ref('EAF-01')
const history = ref([])
const advice = ref({})
const latest = ref({})

const latestUsage = computed(() => (latest.value?.usageKwh ?? latest.value?.usageKwh === 0 ? `${Number(latest.value.usageKwh).toFixed(2)} kWh` : '--'))
const priceMeta = computed(() => getPriceTierMeta(latest.value?.xianPriceTier))

const loadAnalysis = async () => {
  const [historyResult, adviceResult, latestResult] = await Promise.all([
    getSensorHistory(deviceCode.value, 24),
    getDispatchAdvice(deviceCode.value),
    getLatestSensor(deviceCode.value)
  ])
  history.value = historyResult
  advice.value = adviceResult
  latest.value = typeof latestResult === 'string' ? {} : latestResult
}

const { start: startPolling, run: refreshNow } = usePollingTask(loadAnalysis, 10000)

onMounted(async () => {
  devices.value = (await getDevices({ size: 999 })).records
  if (devices.value.length) {
    deviceCode.value = devices.value[0].deviceCode
  }
  await startPolling()
})
</script>

<style scoped>
.analysis-tools {
  display: flex;
  gap: 12px;
}

.analysis-panel,
.advice-panel,
.stat-panel {
  padding: 20px;
}

.analysis-side {
  display: grid;
  gap: 18px;
}

.advice-banner {
  padding: 18px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.68);
  border: 1px solid rgba(82, 200, 255, 0.2);
}

.advice-banner p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.banner-warn {
  border-color: rgba(255, 179, 71, 0.48);
}

.banner-critical {
  border-color: rgba(255, 93, 93, 0.48);
}

.banner-good {
  border-color: rgba(59, 255, 159, 0.48);
}

.analysis-facts {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.analysis-facts div,
.fact-list li {
  color: var(--text-secondary);
}

.analysis-facts strong {
  display: block;
  margin-top: 6px;
  color: var(--text-primary);
  font-size: 18px;
}

.fact-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}

@media (max-width: 1024px) {
  .analysis-tools {
    width: 100%;
    flex-direction: column;
  }
}
</style>
