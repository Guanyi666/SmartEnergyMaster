<template>
  <div v-if="device" class="device-float-card" @click.stop>
    <!-- 4 角折角线框装饰 -->
    <span class="corner corner--tl"></span>
    <span class="corner corner--tr"></span>
    <span class="corner corner--bl"></span>
    <span class="corner corner--br"></span>

    <!-- 头部 -->
    <header class="dfc-header">
      <div class="dfc-header__left">
        <span class="dfc-pulse" :style="{ background: color, boxShadow: `0 0 10px ${color}` }"></span>
        <div>
          <h3 class="dfc-title">
            <span>{{ device.deviceName }}</span>
            <em>{{ deviceTypeLabel }}</em>
          </h3>
          <p class="dfc-sub">{{ device.deviceCode }} · DEVICE_ID {{ device.id }}</p>
        </div>
      </div>
      <div class="dfc-header__right">
        <span class="dfc-pill" :style="pillStyle">{{ statusText }}</span>
        <button class="dfc-close" @click="onClose" aria-label="关闭">
          <span></span><span></span>
        </button>
      </div>
    </header>

    <!-- 实时功率折线图 -->
    <section class="dfc-section">
      <div class="dfc-section__title">
        <h4>实时功率 / REAL-TIME POWER</h4>
        <span class="dfc-section__meta">最近 30 分钟 · 1min sample</span>
      </div>
      <div class="dfc-trend">
        <TrendChart :records="trend" />
      </div>
    </section>

    <!-- 温度 + 压力 双仪表 -->
    <section class="dfc-section dfc-section--gauges">
      <div class="dfc-gauge dfc-gauge--temp">
        <MiniGauge
          :value="Number(device.temperature) || 0"
          :min="0" :max="1400"
          title="TEMPERATURE"
          unit="°C"
          color="#ff7e00"
        />
      </div>
      <div class="dfc-gauge dfc-gauge--press">
        <MiniGauge
          :value="Number(device.pressure) || 0"
          :min="0" :max="220"
          title="PRESSURE"
          unit="kPa"
          color="#5cdcff"
        />
      </div>
    </section>

    <!-- 即时数值 + 操作 -->
    <section class="dfc-stats">
      <div class="dfc-stat">
        <span>振动</span>
        <strong>{{ formatNum(device.vibration) }}<em>mm/s</em></strong>
      </div>
      <div class="dfc-stat">
        <span>功率</span>
        <strong style="color:#3bff9f">{{ formatNum(device.usageKwh) }}<em>kWh</em></strong>
      </div>
      <div class="dfc-stat">
        <span>负载等级</span>
        <strong>{{ device.loadType || '—' }}</strong>
      </div>
    </section>

    <footer class="dfc-footer">
      <span class="dfc-footer__time">UPDATED · {{ nowText }}</span>
      <div class="dfc-footer__actions">
        <button class="dfc-btn" @click="onClose">关闭</button>
        <button class="dfc-btn dfc-btn--primary" @click="onViewDetail">完整详情 →</button>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import TrendChart from '../TrendChart.vue'
import MiniGauge from './MiniGauge.vue'

const props = defineProps({
  // ★ 现在 device 本身就是触发条件,null 即隐藏(原 visible 已废弃)
  device: { type: Object, default: null }
})
const emit = defineEmits(['close', 'view-detail'])

const STATUS_COLOR = {
  RUNNING: '#3bff9f',
  HIGH_LOAD: '#ffb347',
  IDLE: '#5cdcff',
  STOPPED: '#94a3b8',
  OFFLINE: '#64748b',
  FAULT: '#ff5d5d',
  MAINTENANCE: '#ff7e00'
}
const STATUS_TEXT = {
  RUNNING: '运行中', HIGH_LOAD: '高负荷', IDLE: '空转', STOPPED: '停机',
  OFFLINE: '离线', FAULT: '故障', MAINTENANCE: '维修中'
}
const DEVICE_TYPE_LABELS = {
  ARC_FURNACE: '电弧炉', PUMP: '循环水泵', COMPRESSOR: '空压机',
  FAN: '风机', TRANSFORMER: '变压器', BOILER: '锅炉'
}

const color = computed(() => STATUS_COLOR[props.device?.status] || '#5cdcff')
const statusText = computed(() => STATUS_TEXT[props.device?.status] || '未知')
const deviceTypeLabel = computed(() => DEVICE_TYPE_LABELS[props.device?.deviceType] || '设备')
const pillStyle = computed(() => ({
  background: `${color.value}22`,
  borderColor: `${color.value}aa`,
  color: color.value,
  boxShadow: `0 0 10px ${color.value}55`
}))

const formatNum = (n) => {
  if (n == null || n === '') return '--'
  const v = Number(n)
  return Number.isNaN(v) ? '--' : v.toFixed(1)
}

const nowText = ref(formatTime(new Date()))
function formatTime (d) {
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
}
let timer = null

const trend = ref([])
function buildTrend () {
  const arr = []
  const now = Date.now()
  const baseP = Number(props.device?.usageKwh) || 60
  for (let i = 30; i >= 0; i--) {
    arr.push({
      time: new Date(now - i * 60_000).toISOString(),
      usageKwh: baseP + Math.sin(i / 3) * 14 + (Math.random() - 0.5) * 6,
      xianPriceTier: i > 18 ? 'VALLEY' : i > 8 ? 'FLAT' : 'PEAK'
    })
  }
  trend.value = arr
}

function onClose () { emit('close') }
function onViewDetail () { emit('view-detail', props.device) }

watch(() => props.device, (d) => {
  if (d) buildTrend()
}, { immediate: true })

onMounted(() => {
  timer = setInterval(() => { nowText.value = formatTime(new Date()) }, 1000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
/*
 * 设备详情面板 —— 现在 inline 渲染,完全填满父容器(.hud-panel.is-right,400px 宽 × 全高)
 * 不再 position:absolute 浮在大屏上,避免与右侧概览三模块重叠
 */
.device-float-card {
  position: relative;             /* 替代原 absolute */
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 2px;             /* 与 .hud-panel 风格对齐 */
  padding: 0 0 12px;
  color: #ffffff;
  pointer-events: auto;
  font-family: Bahnschrift, 'Microsoft YaHei', sans-serif;
  /* 内部轻量玻璃面:与外层 .hud-panel 4 角框共存,不重复背景 */
  background: linear-gradient(180deg, rgba(0, 30, 60, 0.18), rgba(0, 10, 20, 0.05));
  overflow: hidden;
}

/* 4 角折线框 */
.corner {
  position: absolute;
  width: 14px; height: 14px;
  border-color: #00ffff;
  border-style: solid;
  filter: drop-shadow(0 0 4px rgba(0, 255, 255, 0.6));
}
.corner--tl { top: -1px; left: -1px; border-width: 2px 0 0 2px; border-radius: 4px 0 0 0; }
.corner--tr { top: -1px; right: -1px; border-width: 2px 2px 0 0; border-radius: 0 4px 0 0; }
.corner--bl { bottom: -1px; left: -1px; border-width: 0 0 2px 2px; border-radius: 0 0 0 4px; }
.corner--br { bottom: -1px; right: -1px; border-width: 0 2px 2px 0; border-radius: 0 0 4px 0; }

/* 头部 */
.dfc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid rgba(92, 220, 255, 0.18);
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.45), transparent);
}
.dfc-header__left { display: flex; align-items: center; gap: 10px; }
.dfc-pulse {
  width: 10px; height: 10px; border-radius: 50%;
  animation: dfc-blink 1.4s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes dfc-blink { 0%,100% {opacity:1} 50% {opacity:.45} }
.dfc-title {
  margin: 0;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 14px;
  letter-spacing: 2px;
  font-weight: 700;
  color: #ffffff;
}
.dfc-title em {
  font-style: normal;
  font-size: 10px;
  color: #5cdcff;
  letter-spacing: 1.5px;
  font-family: Bahnschrift, monospace;
}
.dfc-sub {
  margin: 2px 0 0;
  font-size: 10px;
  color: #94a3b8;
  letter-spacing: 1.5px;
  font-family: 'SF Mono', Consolas, monospace;
}
.dfc-header__right { display: flex; align-items: center; gap: 8px; }
.dfc-pill {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  border: 1px solid;
}
.dfc-close {
  width: 24px; height: 24px;
  position: relative;
  border: 1px solid rgba(92, 220, 255, 0.25);
  border-radius: 4px;
  background: rgba(13, 37, 64, 0.6);
  cursor: pointer;
  transition: all 0.2s ease;
}
.dfc-close span {
  position: absolute;
  top: 50%; left: 50%;
  width: 12px; height: 1.5px;
  background: #5cdcff;
  transform-origin: center;
}
.dfc-close span:nth-child(1) { transform: translate(-50%, -50%) rotate(45deg); }
.dfc-close span:nth-child(2) { transform: translate(-50%, -50%) rotate(-45deg); }
.dfc-close:hover {
  border-color: #ff5d5d;
  background: rgba(255, 93, 93, 0.12);
}
.dfc-close:hover span { background: #ff5d5d; }

/* 段落 */
.dfc-section {
  padding: 12px 16px 4px;
}
.dfc-section__title {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.dfc-section__title h4 {
  margin: 0;
  font-size: 11px;
  letter-spacing: 2.5px;
  color: #5cdcff;
  font-weight: 700;
}
.dfc-section__meta {
  font-size: 9px;
  color: #94a3b8;
  letter-spacing: 1px;
  font-family: 'SF Mono', monospace;
}
.dfc-trend {
  height: 120px;
}
.dfc-trend :deep(.chart-box) {
  height: 120px !important;
}

.dfc-section--gauges {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.dfc-gauge {
  background: rgba(13, 37, 64, 0.5);
  border: 1px solid rgba(92, 220, 255, 0.16);
  border-radius: 10px;
  padding: 6px 4px 4px;
  height: 140px;
  position: relative;
  overflow: hidden;
}
.dfc-gauge::before {
  content: '';
  position: absolute;
  top: 0; left: 8%;
  width: 84%; height: 1px;
  background: linear-gradient(90deg, transparent, currentColor, transparent);
  opacity: 0.4;
}
.dfc-gauge--temp { color: #ff7e00; }
.dfc-gauge--press { color: #5cdcff; }

/* 即时数值 */
.dfc-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  padding: 4px 16px;
}
.dfc-stat {
  background: rgba(13, 37, 64, 0.5);
  border: 1px solid rgba(92, 220, 255, 0.1);
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dfc-stat span {
  font-size: 9px;
  color: #94a3b8;
  letter-spacing: 1.5px;
}
.dfc-stat strong {
  font-size: 14px;
  color: #ffffff;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.dfc-stat em {
  font-style: normal;
  font-size: 9px;
  margin-left: 4px;
  color: #94a3b8;
  font-weight: 500;
}

/* 底部 */
.dfc-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding: 10px 16px 0;
  border-top: 1px solid rgba(92, 220, 255, 0.15);
}
.dfc-footer__time {
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #94a3b8;
  font-family: 'SF Mono', monospace;
}
.dfc-footer__actions { display: flex; gap: 6px; }
.dfc-btn {
  padding: 5px 14px;
  background: rgba(13, 37, 64, 0.6);
  border: 1px solid rgba(92, 220, 255, 0.25);
  border-radius: 4px;
  color: #a8c4e0;
  font-size: 11px;
  letter-spacing: 1.5px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}
.dfc-btn:hover { border-color: #5cdcff; color: #5cdcff; }
.dfc-btn--primary {
  background: linear-gradient(180deg, rgba(61, 169, 255, 0.45), rgba(37, 99, 235, 0.4));
  border-color: rgba(61, 169, 255, 0.6);
  color: #ffffff;
  font-weight: 700;
}
.dfc-btn--primary:hover {
  background: linear-gradient(180deg, rgba(61, 169, 255, 0.6), rgba(37, 99, 235, 0.55));
}

/* 入场动画 —— 已移至 DashboardView.vue 由父级 <transition name="right-swap"> 接管 */

@media (max-width: 1024px) {
  .device-float-card {
    width: calc(100% - 32px);
    right: 16px;
    left: 16px;
  }
}
</style>
