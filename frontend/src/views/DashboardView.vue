<template>
  <div class="command-center">
    <div class="grid-overlay"></div>

    <header class="command-header">
      <button class="brand" @click="router.push('/analysis')">
        <span class="brand-icon">⚡</span>
        <span><b>SMART ENERGY</b><small>智驭能效 · 数字工厂</small></span>
      </button>
      <div class="title-frame">
        <span></span>
        <div>
          <h1>智慧能源生产指挥中心</h1>
          <p>SMART ENERGY PRODUCTION COMMAND CENTER</p>
        </div>
        <span></span>
      </div>
      <div class="header-meta">
        <div><strong>{{ currentTime }}</strong><span>{{ currentDate }}</span></div>
        <div class="weather"><i>☼</i><strong>23°C</strong><span>系统运行正常</span></div>
      </div>
    </header>

    <nav class="quick-nav">
      <button @click="router.push('/analysis')">负荷分析</button>
      <button @click="router.push('/devices')">设备管理</button>
      <button @click="router.push('/scheduler')">调度优化</button>
      <button @click="router.push('/account-settings')">账号设置</button>
    </nav>

    <main class="screen-grid">
      <section class="column left-column">
        <article class="data-panel status-panel">
          <PanelTitle title="全厂能源总览" subtitle="ENERGY OVERVIEW" />
          <div class="metric-grid">
            <div class="metric" v-for="item in overviewMetrics" :key="item.label">
              <span>{{ item.label }}</span>
              <strong :style="{ color: item.color }">{{ item.value }}</strong>
              <small>{{ item.unit }}</small>
            </div>
          </div>
          <div class="energy-bars">
            <div v-for="item in energyMix" :key="item.name">
              <span>{{ item.name }}</span>
              <i><b :style="{ width: `${item.value}%`, background: item.color }"></b></i>
              <strong>{{ item.value }}%</strong>
            </div>
          </div>
        </article>

        <article class="data-panel production-panel">
          <PanelTitle title="今日分时用能" subtitle="HOURLY ENERGY LOAD" />
          <div class="bar-chart">
            <div v-for="(value, index) in hourlyLoad" :key="index" class="bar-column">
              <i :style="{ height: `${value}%` }"></i>
              <span>{{ index % 2 === 0 ? `${String(index * 2).padStart(2, '0')}:00` : '' }}</span>
            </div>
          </div>
          <div class="chart-legend"><span>实时负荷</span><strong>峰值 {{ maxHourly }} kWh</strong></div>
        </article>

        <article class="data-panel alert-panel">
          <PanelTitle title="实时告警监控" subtitle="REAL-TIME ALERTS" />
          <div class="alert-list">
            <button v-for="order in alerts.slice(0, 4)" :key="order.id" @click="router.push('/devices')">
              <i :class="order.status === 'PENDING' ? 'danger' : 'warn'"></i>
              <span><strong>{{ order.title }}</strong><small>{{ order.deviceName }} · {{ order.assignee || '待分配' }}</small></span>
              <em>{{ order.status === 'PENDING' ? '待处理' : '处理中' }}</em>
            </button>
            <div v-if="!alerts.length" class="empty-state">当前生产运行稳定，暂无待处理告警</div>
          </div>
        </article>
      </section>

      <section class="digital-twin">
        <div class="twin-glow"></div>
        <img src="../assets/energy-digital-twin.png" alt="智慧能源数字孪生厂区" />
        <button class="hotspot hotspot-generator" @click="selectFirstDevice">
          <i></i><span>核心发电机组<small>{{ focusDevice?.status || 'RUNNING' }}</small></span>
        </button>
        <button class="hotspot hotspot-solar"><i></i><span>光伏阵列<small>效率 92.6%</small></span></button>
        <button class="hotspot hotspot-control"><i></i><span>智能配电中心<small>负荷均衡</small></span></button>
        <button class="hotspot hotspot-cooling"><i></i><span>循环冷却系统<small>温度正常</small></span></button>

        <div class="center-summary">
          <div><span>实时总负荷</span><strong>{{ formatCompact(summary.totalUsageKwh) }}</strong><small>kWh</small></div>
          <div><span>接入设备</span><strong>{{ devices.length || 0 }}</strong><small>台</small></div>
          <div><span>运行效率</span><strong>96.8</strong><small>%</small></div>
        </div>

        <div class="device-carousel">
          <button v-for="device in devices.slice(0, 5)" :key="device.id" :class="{ active: device.deviceCode === focusDeviceCode }" @click="selectDevice(device.deviceCode)">
            <i :class="statusClass(device.status)"></i>
            <span>{{ device.deviceName }}</span>
            <small>{{ device.location || '生产区域' }}</small>
          </button>
        </div>
      </section>

      <section class="column right-column">
        <article class="data-panel device-panel">
          <PanelTitle title="重点设备运行监测" subtitle="DEVICE MONITORING" />
          <div class="focus-device">
            <div class="device-name"><span>当前监测</span><strong>{{ focusDevice?.deviceName || '核心能源设备' }}</strong></div>
            <div class="health-ring"><span>健康度</span><strong>96</strong><small>%</small></div>
          </div>
          <div class="sensor-grid">
            <div><span>实时温度</span><strong>{{ formatNumber(latestData.temperature) }}</strong><small>°C</small></div>
            <div><span>系统压力</span><strong>{{ formatNumber(latestData.pressure) }}</strong><small>kPa</small></div>
            <div><span>设备功率</span><strong>{{ formatNumber(focusDevice?.usageKwh) }}</strong><small>kWh</small></div>
            <div><span>运行状态</span><strong class="running">{{ statusLabel(focusDevice?.status) }}</strong></div>
          </div>
          <div class="spark-line"><i v-for="(value, index) in sparkData" :key="index" :style="{ height: `${value}%` }"></i></div>
        </article>

        <article class="data-panel forecast-panel">
          <PanelTitle title="AI 能耗预测" subtitle="AI ENERGY FORECAST" />
          <div class="forecast-list">
            <div v-for="point in forecastPoints" :key="point.label">
              <span>{{ point.label }}</span>
              <i><b :style="{ width: point.width }"></b></i>
              <strong>{{ point.value }}</strong>
            </div>
          </div>
          <div class="ai-advice">
            <span>AI 调度建议</span>
            <strong>{{ summary.dispatchAdvice?.title || '当前负荷平稳，建议维持运行策略' }}</strong>
            <p>{{ summary.dispatchAdvice?.content || '预测未来 30 分钟供能充足，设备运行效率处于最优区间。' }}</p>
          </div>
        </article>

        <article class="data-panel orders-panel">
          <PanelTitle title="设备状态矩阵" subtitle="DEVICE STATUS MATRIX" />
          <div class="status-matrix">
            <button v-for="device in devices.slice(0, 8)" :key="device.id" @click="openDeviceDetail(device)">
              <i :class="statusClass(device.status)"></i>
              <span>{{ device.deviceName }}</span>
              <small>{{ statusLabel(device.status) }}</small>
            </button>
          </div>
        </article>
      </section>
    </main>

    <footer class="screen-footer">
      <span>数据更新时间 {{ currentTime }}</span>
      <i></i>
      <strong>SMART ENERGY DIGITAL TWIN PLATFORM</strong>
      <i></i>
      <span>每 5 秒自动同步实时数据</span>
    </footer>

    <DeviceDetailDialog v-model="detailVisible" :device="detailDevice" />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DeviceDetailDialog from '../components/DeviceDetailDialog.vue'
import { getActiveAlerts, getDashboardSummary, getDevices, getLatestSensor } from '../api'
import { usePollingTask } from '../composables/usePollingTask'

const PanelTitle = defineComponent({
  props: { title: String, subtitle: String },
  setup: (props) => () => h('div', { class: 'panel-title' }, [
    h('div', [h('h3', props.title), h('small', props.subtitle)]),
    h('span', [h('i'), h('i'), h('i')])
  ])
})

const router = useRouter()
const summary = ref({})
const devices = ref([])
const latestData = ref({})
const alerts = ref([])
const focusDeviceCode = ref('EAF-01')
const detailVisible = ref(false)
const detailDevice = ref(null)
const currentTime = ref('')
const currentDate = ref('')
let clockTimer

const hourlyLoad = [38, 52, 44, 48, 55, 63, 71, 59, 67, 78, 88, 56]
const sparkData = [32, 48, 43, 64, 55, 72, 66, 81, 62, 75, 58, 86, 72, 91, 76, 84, 68, 78]
const maxHourly = 8840
const energyMix = [
  { name: '市电', value: 72, color: 'linear-gradient(90deg, #05d8ff, #2176ff)' },
  { name: '光伏', value: 18, color: 'linear-gradient(90deg, #36f3a2, #05d8ff)' },
  { name: '储能', value: 10, color: 'linear-gradient(90deg, #ffb547, #ff7b42)' }
]

const focusDevice = computed(() => devices.value.find((device) => device.deviceCode === focusDeviceCode.value))
const overviewMetrics = computed(() => [
  { label: '实时总负荷', value: formatCompact(summary.value.totalUsageKwh), unit: 'kWh', color: '#64e8ff' },
  { label: '碳排放量', value: formatCompact(summary.value.totalCo2Emission), unit: 'tCO₂', color: '#47f5a2' },
  { label: '在线设备', value: devices.value.filter((item) => !['OFFLINE', 'STOPPED'].includes(item.status)).length, unit: ` / ${devices.value.length || 0}`, color: '#ffbf5b' },
  { label: '综合能效', value: '96.8', unit: '%', color: '#8f9cff' }
])
const forecastPoints = computed(() => {
  const list = summary.value.forecast || []
  return [0, 1, 2].map((index) => ({
    label: list[index] ? `${list[index].minutesAhead} 分钟后` : `${(index + 1) * 10} 分钟后`,
    value: list[index]?.mean ? `${Number(list[index].mean).toFixed(0)} kWh` : `${[8620, 8180, 7950][index]} kWh`,
    width: `${[88, 76, 68][index]}%`
  }))
})

const formatNumber = (value) => value === 0 || value ? Number(value).toFixed(1) : '--'
const formatCompact = (value) => {
  const number = Number(value || 0)
  return number >= 10000 ? `${(number / 10000).toFixed(2)}万` : number.toFixed(0)
}
const statusLabel = (status) => ({ RUNNING: '运行中', ONLINE: '在线', MAINTENANCE: '维修中', FAULT: '故障', OFFLINE: '离线', STOPPED: '停机' }[status] || '运行中')
const statusClass = (status) => ['FAULT', 'OFFLINE'].includes(status) ? 'bad' : ['MAINTENANCE', 'STOPPED'].includes(status) ? 'warn' : 'good'

const updateClock = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
  currentDate.value = now.toLocaleDateString('zh-CN', { weekday: 'short', year: 'numeric', month: '2-digit', day: '2-digit' })
}

const loadAll = async () => {
  const [summaryResult, devicesResult, latestResult, alertsResult] = await Promise.all([
    getDashboardSummary(focusDeviceCode.value),
    getDevices({ size: 999 }),
    getLatestSensor(focusDeviceCode.value),
    getActiveAlerts(8)
  ])
  summary.value = summaryResult || {}
  devices.value = devicesResult?.records || devicesResult || []
  latestData.value = typeof latestResult === 'string' ? {} : latestResult || {}
  alerts.value = alertsResult || []
  if (!focusDevice.value && devices.value.length) focusDeviceCode.value = devices.value[0].deviceCode
}

const { start: startPolling, run: refreshNow } = usePollingTask(loadAll, 5000)
const selectDevice = async (code) => {
  focusDeviceCode.value = code
  await refreshNow()
}
const selectFirstDevice = () => devices.value.length && selectDevice(devices.value[0].deviceCode)
const openDeviceDetail = async (device) => {
  await selectDevice(device.deviceCode)
  detailDevice.value = device
  detailVisible.value = true
}

onMounted(async () => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
  await startPolling()
})
onBeforeUnmount(() => window.clearInterval(clockTimer))
</script>

<style scoped>
.command-center {
  --cyan: #19d7ff;
  --cyan-soft: rgba(25, 215, 255, .18);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #eafaff;
  background: radial-gradient(circle at 50% 45%, #08284a 0, #031326 35%, #020914 78%);
  font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
}
.grid-overlay { position: absolute; inset: 0; opacity: .14; pointer-events: none; background-image: linear-gradient(rgba(35, 184, 255, .25) 1px, transparent 1px), linear-gradient(90deg, rgba(35, 184, 255, .25) 1px, transparent 1px); background-size: 48px 48px; mask-image: linear-gradient(to bottom, transparent, #000 20%, #000 80%, transparent); }
.command-header { position: relative; z-index: 5; height: 94px; display: grid; grid-template-columns: 280px 1fr 330px; align-items: center; padding: 0 24px; background: linear-gradient(180deg, rgba(2, 16, 34, .98), rgba(2, 16, 34, .6) 75%, transparent); }
.brand { display: flex; align-items: center; gap: 11px; border: 0; color: white; background: transparent; text-align: left; cursor: pointer; }
.brand-icon { display: grid; place-items: center; width: 44px; height: 44px; border: 1px solid var(--cyan); clip-path: polygon(20% 0, 100% 0, 100% 80%, 80% 100%, 0 100%, 0 20%); color: #061421; background: var(--cyan); box-shadow: 0 0 24px var(--cyan); }
.brand b { display: block; font-size: 15px; letter-spacing: 2px; }.brand small { display: block; margin-top: 4px; color: #7fb6cf; letter-spacing: 2px; }
.title-frame { height: 82px; display: flex; justify-content: center; align-items: center; gap: 22px; text-align: center; background: linear-gradient(90deg, transparent, rgba(13, 107, 172, .32), transparent); clip-path: polygon(8% 0, 92% 0, 100% 72%, 80% 72%, 76% 100%, 24% 100%, 20% 72%, 0 72%); }
.title-frame h1 { margin: 0; font-size: clamp(24px, 2vw, 38px); letter-spacing: 7px; text-shadow: 0 0 18px rgba(55, 208, 255, .7); }.title-frame p { margin: 5px 0 0; color: #55b8dc; font-size: 9px; letter-spacing: 4px; }.title-frame > span { width: 72px; height: 1px; background: linear-gradient(90deg, transparent, var(--cyan)); }.title-frame > span:last-child { transform: scaleX(-1); }
.header-meta { display: flex; justify-content: flex-end; gap: 22px; }.header-meta div { display: flex; flex-direction: column; align-items: flex-end; }.header-meta strong { font-size: 21px; letter-spacing: 2px; }.header-meta span { margin-top: 4px; color: #7faec2; font-size: 11px; }.header-meta .weather { position: relative; padding-left: 46px; border-left: 1px solid rgba(71, 211, 255, .35); }.weather i { position: absolute; left: 15px; top: 0; color: #ffd15c; font-size: 28px; font-style: normal; }
.quick-nav { position: absolute; z-index: 10; top: 91px; left: 50%; display: flex; gap: 4px; transform: translateX(-50%); }.quick-nav button { padding: 6px 18px; border: 1px solid rgba(35, 192, 255, .25); color: #75bbd7; background: rgba(3, 25, 48, .8); font-size: 11px; cursor: pointer; }.quick-nav button:hover { color: white; border-color: var(--cyan); background: rgba(0, 155, 224, .28); }
.screen-grid { position: relative; z-index: 2; display: grid; grid-template-columns: minmax(270px, 23%) 1fr minmax(290px, 25%); gap: 14px; min-height: calc(100vh - 124px); padding: 22px 18px 46px; }
.column { display: grid; gap: 13px; min-width: 0; }.left-column { grid-template-rows: .95fr 1fr 1.05fr; }.right-column { grid-template-rows: 1.1fr 1fr .9fr; }
.data-panel { position: relative; min-height: 0; padding: 13px 15px; border: 1px solid rgba(28, 177, 244, .28); background: linear-gradient(135deg, rgba(4, 24, 46, .91), rgba(3, 16, 33, .68)); box-shadow: inset 0 0 35px rgba(0, 113, 192, .08), 0 0 25px rgba(0, 0, 0, .28); clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px)); }
.data-panel::before, .data-panel::after { content: ""; position: absolute; width: 38px; height: 2px; background: var(--cyan); box-shadow: 0 0 10px var(--cyan); }.data-panel::before { top: 0; left: 0; }.data-panel::after { right: 0; bottom: 0; }
:deep(.panel-title) { display: flex; align-items: center; justify-content: space-between; padding-bottom: 9px; margin-bottom: 11px; border-bottom: 1px solid rgba(60, 182, 236, .24); }:deep(.panel-title h3) { display: inline; margin: 0; font-size: 15px; letter-spacing: 1px; }:deep(.panel-title small) { margin-left: 8px; color: #427d9a; font-size: 7px; letter-spacing: 1px; }:deep(.panel-title span) { display: flex; gap: 4px; }:deep(.panel-title i) { width: 10px; height: 3px; background: #1175a2; }:deep(.panel-title i:first-child) { background: var(--cyan); }
.metric-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }.metric { padding: 7px 8px; border-left: 2px solid #087eb0; background: linear-gradient(90deg, rgba(19, 125, 172, .17), transparent); }.metric span { display: block; color: #78a9bf; font-size: 10px; }.metric strong { margin-right: 4px; font-size: clamp(17px, 1.4vw, 25px); font-family: Bahnschrift, sans-serif; text-shadow: 0 0 12px currentColor; }.metric small { color: #648aa0; font-size: 8px; }
.energy-bars { display: grid; gap: 6px; margin-top: 10px; }.energy-bars div, .forecast-list div { display: grid; grid-template-columns: 36px 1fr 36px; align-items: center; gap: 7px; color: #8ab4c8; font-size: 9px; }.energy-bars i, .forecast-list i { height: 4px; background: rgba(25, 128, 180, .18); }.energy-bars b, .forecast-list b { display: block; height: 100%; box-shadow: 0 0 8px currentColor; }.energy-bars strong { color: white; font-size: 9px; }
.bar-chart { height: calc(100% - 60px); min-height: 76px; display: flex; align-items: flex-end; gap: 5px; padding: 8px 4px 0; border-bottom: 1px solid rgba(54, 165, 218, .25); background-image: linear-gradient(rgba(45, 144, 191, .1) 1px, transparent 1px); background-size: 100% 25%; }.bar-column { flex: 1; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; }.bar-column i { width: 62%; min-width: 5px; background: linear-gradient(to top, rgba(0, 92, 255, .25), #16d9ff); box-shadow: 0 0 8px rgba(20, 212, 255, .5); }.bar-column span { height: 14px; margin-top: 3px; color: #5d8ca3; font-size: 7px; white-space: nowrap; }.chart-legend { display: flex; justify-content: space-between; margin-top: 6px; color: #73a4b9; font-size: 9px; }.chart-legend span::before { content: ""; display: inline-block; width: 6px; height: 6px; margin-right: 5px; background: var(--cyan); box-shadow: 0 0 6px var(--cyan); }.chart-legend strong { color: #67dfff; font-weight: 500; }
.alert-list { display: grid; gap: 6px; }.alert-list button { display: grid; grid-template-columns: 8px 1fr auto; align-items: center; gap: 9px; width: 100%; padding: 7px 8px; border: 0; border-bottom: 1px solid rgba(59, 139, 176, .15); color: white; background: rgba(12, 55, 82, .16); text-align: left; cursor: pointer; }.alert-list i, .status-matrix i, .device-carousel i { width: 6px; height: 6px; border-radius: 50%; }.good { background: #34f5a4; box-shadow: 0 0 8px #34f5a4; }.warn { background: #ffb64d; box-shadow: 0 0 8px #ffb64d; }.bad, .danger { background: #ff5656; box-shadow: 0 0 8px #ff5656; }.alert-list span { min-width: 0; }.alert-list strong, .alert-list small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.alert-list strong { font-size: 10px; }.alert-list small { margin-top: 3px; color: #658fa4; font-size: 8px; }.alert-list em { color: #ffb14b; font-size: 8px; font-style: normal; }.empty-state { padding: 28px 0; color: #5f93aa; text-align: center; font-size: 10px; }
.digital-twin { position: relative; min-width: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; }.digital-twin::after { content: ""; position: absolute; inset: 10% 2%; border: 1px solid rgba(26, 187, 255, .1); border-radius: 50%; transform: perspective(500px) rotateX(64deg); box-shadow: 0 0 80px rgba(21, 171, 255, .16); }.digital-twin img { position: relative; z-index: 1; width: min(100%, 1000px); filter: saturate(1.05) contrast(1.06); mask-image: radial-gradient(ellipse, #000 52%, rgba(0,0,0,.88) 68%, transparent 83%); animation: twin-in 1.2s ease both; }.twin-glow { position: absolute; width: 62%; height: 34%; border-radius: 50%; background: rgba(0, 148, 255, .18); filter: blur(70px); }
.hotspot { position: absolute; z-index: 4; display: flex; align-items: center; gap: 7px; padding: 5px 9px; border: 1px solid rgba(42, 211, 255, .48); color: white; background: rgba(2, 23, 45, .78); clip-path: polygon(7px 0, 100% 0, 100% calc(100% - 7px), calc(100% - 7px) 100%, 0 100%, 0 7px); cursor: pointer; }.hotspot i { width: 7px; height: 7px; border-radius: 50%; background: var(--cyan); box-shadow: 0 0 0 4px rgba(22, 213, 255, .14), 0 0 12px var(--cyan); }.hotspot span { font-size: 9px; }.hotspot small { display: block; margin-top: 2px; color: #62b4d2; font-size: 7px; }.hotspot-generator { top: 37%; left: 48%; }.hotspot-solar { top: 54%; left: 14%; }.hotspot-control { top: 29%; right: 11%; }.hotspot-cooling { bottom: 28%; right: 8%; }
.center-summary { position: absolute; z-index: 4; top: 12px; left: 50%; display: flex; gap: 28px; transform: translateX(-50%); white-space: nowrap; }.center-summary div { text-align: center; }.center-summary span { display: block; color: #6598ae; font-size: 8px; }.center-summary strong { margin-right: 3px; color: #d7f9ff; font-size: 18px; text-shadow: 0 0 10px var(--cyan); }.center-summary small { color: #5fa2bd; font-size: 7px; }
.device-carousel { position: absolute; z-index: 5; bottom: 4px; left: 50%; display: flex; gap: 6px; width: 90%; transform: translateX(-50%); }.device-carousel button { flex: 1; min-width: 0; padding: 7px 7px 6px; border: 1px solid rgba(29, 155, 211, .2); color: white; background: rgba(3, 23, 43, .82); cursor: pointer; }.device-carousel button.active { border-color: var(--cyan); box-shadow: inset 0 -2px var(--cyan), 0 0 14px rgba(19, 196, 255, .15); }.device-carousel i { display: inline-block; margin-right: 5px; }.device-carousel span, .device-carousel small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.device-carousel span { font-size: 9px; }.device-carousel small { margin-top: 3px; color: #557f94; font-size: 7px; }
.focus-device { display: grid; grid-template-columns: 1fr 74px; gap: 10px; align-items: center; }.device-name { padding: 10px; border-left: 2px solid var(--cyan); background: linear-gradient(90deg, rgba(0, 157, 226, .18), transparent); }.device-name span, .device-name strong { display: block; }.device-name span { color: #6797ad; font-size: 9px; }.device-name strong { margin-top: 5px; font-size: 14px; }.health-ring { width: 68px; height: 68px; display: grid; place-content: center; border: 5px solid rgba(16, 179, 235, .18); border-top-color: var(--cyan); border-right-color: #39e6ba; border-radius: 50%; text-align: center; box-shadow: inset 0 0 18px rgba(18, 184, 245, .15), 0 0 15px rgba(18, 184, 245, .12); }.health-ring span { color: #6090a6; font-size: 7px; }.health-ring strong { font-size: 18px; }.health-ring small { color: #59a9c5; font-size: 7px; }
.sensor-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; margin-top: 8px; }.sensor-grid div { padding: 6px 8px; background: rgba(13, 71, 102, .18); }.sensor-grid span { display: block; color: #6793a8; font-size: 8px; }.sensor-grid strong { margin-right: 3px; color: #69e7ff; font-size: 15px; }.sensor-grid small { color: #608da2; font-size: 7px; }.sensor-grid .running { color: #39eba2; font-size: 11px; }.spark-line { height: 28px; display: flex; align-items: flex-end; gap: 3px; margin-top: 7px; border-bottom: 1px solid rgba(50, 175, 226, .2); }.spark-line i { flex: 1; background: linear-gradient(to top, rgba(19, 118, 210, .15), var(--cyan)); }
.forecast-list { display: grid; gap: 10px; }.forecast-list div { grid-template-columns: 54px 1fr 58px; }.forecast-list b { background: linear-gradient(90deg, #0d87ff, #29e2ff); }.forecast-list strong { color: #8beaff; font-size: 9px; text-align: right; }.ai-advice { margin-top: 13px; padding: 9px 10px; border: 1px solid rgba(61, 246, 175, .2); background: linear-gradient(90deg, rgba(31, 176, 133, .13), transparent); }.ai-advice span { color: #43e8ac; font-size: 8px; }.ai-advice strong { display: block; margin-top: 4px; color: #bcffea; font-size: 10px; }.ai-advice p { margin: 4px 0 0; color: #639787; font-size: 8px; line-height: 1.5; }
.status-matrix { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; }.status-matrix button { display: grid; grid-template-columns: 7px 1fr auto; align-items: center; gap: 6px; min-width: 0; padding: 7px; border: 1px solid rgba(40, 143, 187, .13); color: white; background: rgba(10, 57, 82, .17); text-align: left; cursor: pointer; }.status-matrix span { overflow: hidden; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }.status-matrix small { color: #5590a9; font-size: 7px; }
.screen-footer { position: absolute; z-index: 8; right: 0; bottom: 0; left: 0; height: 29px; display: flex; align-items: center; justify-content: center; gap: 18px; color: #477b92; background: linear-gradient(90deg, transparent, rgba(7, 55, 84, .72), transparent); font-size: 8px; letter-spacing: 1px; }.screen-footer i { width: 90px; height: 1px; background: linear-gradient(90deg, transparent, var(--cyan), transparent); }.screen-footer strong { color: #66b5d2; font-size: 8px; }
@keyframes twin-in { from { opacity: 0; transform: scale(.94); } to { opacity: 1; transform: scale(1); } }
@media (max-width: 1180px) {
  .command-header { grid-template-columns: 210px 1fr 230px; }.screen-grid { grid-template-columns: 260px 1fr 270px; gap: 8px; padding-inline: 8px; }.hotspot { display: none; }.title-frame h1 { letter-spacing: 3px; }.header-meta .weather { display: none; }
}
@media (max-width: 900px) {
  .command-center { overflow: auto; }.command-header { grid-template-columns: 1fr; height: 76px; }.brand, .header-meta, .quick-nav { display: none; }.screen-grid { grid-template-columns: 1fr; padding-top: 10px; }.digital-twin { min-height: 430px; grid-row: 1; }.column { grid-template-rows: auto; }.data-panel { min-height: 210px; }.screen-footer { position: relative; }.title-frame { height: 70px; }.device-carousel { bottom: 10px; }
}
</style>
