<template>
  <aside class="hud-panel hud-panel--right">
    <span class="hp-corner hp-corner--tl"></span>
    <span class="hp-corner hp-corner--tr"></span>
    <span class="hp-corner hp-corner--bl"></span>
    <span class="hp-corner hp-corner--br"></span>

    <!-- 头部 -->
    <header class="hp-header">
      <div>
        <p class="hp-eyebrow">REAL-TIME MONITOR</p>
        <h3 class="hp-title">设备实时监测</h3>
      </div>
      <span class="hp-chip">{{ devices.length }} 台</span>
    </header>

    <!-- 设备列表 -->
    <ul class="hp-devices">
      <li
        v-for="dev in devices"
        :key="dev.id || dev.deviceCode"
        class="hp-device"
        :class="{ 'is-active': dev.deviceCode === focusCode }"
        :style="{ '--row-color': statusColor(dev.status) }"
        @click="$emit('select', dev)"
      >
        <span class="hp-device__bar"></span>
        <div class="hp-device__head">
          <div class="hp-device__name">
            <span class="hp-device__dot"></span>
            <strong>{{ dev.deviceName }}</strong>
          </div>
          <span class="hp-device__pill">{{ statusText(dev.status) }}</span>
        </div>
        <div class="hp-device__body">
          <div class="hp-device__metric">
            <span>P</span><strong>{{ formatNum(dev.usageKwh) }}</strong><em>kWh</em>
          </div>
          <div class="hp-device__metric">
            <span>T</span><strong>{{ formatNum(dev.temperature) }}</strong><em>°C</em>
          </div>
          <div class="hp-device__metric">
            <span>P°</span><strong>{{ formatNum(dev.pressure) }}</strong><em>kPa</em>
          </div>
        </div>
        <div class="hp-device__sparkline">
          <MiniSparkline
            :data="sparklineData(dev)"
            :color="statusColor(dev.status)"
            :height="28"
          />
        </div>
      </li>
      <li v-if="!devices.length" class="hp-empty">暂无设备数据</li>
    </ul>

    <!-- 今日概况柱状图 -->
    <section class="hp-overview">
      <header class="hp-overview__head">
        <h4>今日概况 / TODAY OVERVIEW</h4>
        <span class="hp-overview__date">{{ dateText }}</span>
      </header>
      <MiniBar :data="overviewData" :height="100" />
    </section>

    <!-- 未来能耗预测胶囊 -->
    <section v-if="forecast && forecast.length" class="hp-forecast">
      <header class="hp-overview__head">
        <h4>能耗预测 / FORECAST</h4>
        <span class="hp-overview__date">FUTURE 60min</span>
      </header>
      <div class="hp-forecast__list">
        <div
          v-for="p in forecast.slice(0, 4)"
          :key="p.minutesAhead"
          class="hp-forecast__chip"
        >
          <span>{{ p.minutesAhead }}min</span>
          <strong>{{ Number(p.mean || 0).toFixed(0) }}</strong>
          <em>kWh</em>
        </div>
      </div>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import MiniSparkline from './MiniSparkline.vue'
import MiniBar from './MiniBar.vue'

const props = defineProps({
  devices: { type: Array, default: () => [] },
  focusCode: { type: String, default: '' },
  summary: { type: Object, default: () => ({}) },
  forecast: { type: Array, default: () => [] }
})
defineEmits(['select'])

const STATUS_COLOR = {
  RUNNING: '#3bff9f', HIGH_LOAD: '#ffb347', IDLE: '#5cdcff',
  STOPPED: '#94a3b8', OFFLINE: '#64748b',
  FAULT: '#ff5d5d', MAINTENANCE: '#ff7e00'
}
const STATUS_TEXT = {
  RUNNING: '运行', HIGH_LOAD: '高负荷', IDLE: '空转',
  STOPPED: '停机', OFFLINE: '离线',
  FAULT: '故障', MAINTENANCE: '维修'
}
const statusColor = (s) => STATUS_COLOR[s] || '#5cdcff'
const statusText = (s) => STATUS_TEXT[s] || s || '未知'
const formatNum = (n) => {
  if (n == null || n === '') return '--'
  const v = Number(n); return Number.isNaN(v) ? '--' : v.toFixed(0)
}

const dateText = computed(() => {
  const d = new Date()
  return `${String(d.getMonth()+1).padStart(2,'0')}/${String(d.getDate()).padStart(2,'0')}`
})

// 为每台设备生成一段伪实时趋势线（基于当前 usageKwh 抖动）
const sparklineCache = new Map()
function sparklineData (dev) {
  const key = dev.deviceCode || String(dev.id || '')
  const arr = sparklineCache.get(key) || []
  const base = Number(dev.usageKwh) || 0
  arr.push(base + (Math.random() - 0.5) * (base * 0.12 + 2))
  if (arr.length > 22) arr.shift()
  sparklineCache.set(key, arr)
  return arr.slice()
}

// 今日概况柱状图数据
const overviewData = computed(() => {
  const s = props.summary || {}
  return [
    { name: '功率',    value: Number(s.totalUsageKwh || 0).toFixed(0), color: '#3bff9f' },
    { name: '碳排',    value: Number(s.totalCo2Emission || 0).toFixed(0), color: '#ff7e00' },
    { name: '运行',    value: props.devices.filter(d => !['OFFLINE','STOPPED'].includes(d.status)).length, color: '#5cdcff' },
    { name: '故障',    value: props.devices.filter(d => ['FAULT','MAINTENANCE'].includes(d.status)).length, color: '#ff5d5d' }
  ]
})
</script>

<style scoped>
.hud-panel {
  position: relative;
  width: 318px;
  padding: 18px 14px 14px;
  border-radius: 14px;
  pointer-events: auto;
  background: rgba(8, 16, 30, 0.68);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  border: 1px solid rgba(0, 255, 255, 0.22);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 0 24px rgba(0, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 200px);
  font-family: Bahnschrift, 'Microsoft YaHei', sans-serif;
  color: #ffffff;
}

.hp-corner {
  position: absolute;
  width: 16px; height: 16px;
  border-color: #00ffff;
  border-style: solid;
  filter: drop-shadow(0 0 4px rgba(0, 255, 255, 0.55));
  pointer-events: none;
}
.hp-corner--tl { top: -1px; left: -1px; border-width: 2px 0 0 2px; border-radius: 6px 0 0 0; }
.hp-corner--tr { top: -1px; right: -1px; border-width: 2px 2px 0 0; border-radius: 0 6px 0 0; }
.hp-corner--bl { bottom: -1px; left: -1px; border-width: 0 0 2px 2px; border-radius: 0 0 0 6px; }
.hp-corner--br { bottom: -1px; right: -1px; border-width: 0 2px 2px 0; border-radius: 0 0 6px 0; }
.hud-panel::before {
  content: '';
  position: absolute;
  top: 0; left: 12%;
  width: 76%; height: 1px;
  background: linear-gradient(90deg, transparent, #00ffff, transparent);
  opacity: 0.7;
}

.hp-header { display: flex; justify-content: space-between; align-items: center; }
.hp-eyebrow {
  margin: 0;
  font-size: 9px;
  letter-spacing: 3px;
  color: #5cdcff;
  font-weight: 600;
}
.hp-title {
  margin: 4px 0 0;
  font-size: 15px;
  letter-spacing: 3px;
  color: #ffffff;
  font-weight: 700;
}
.hp-chip {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(92, 220, 255, 0.12);
  border: 1px solid rgba(92, 220, 255, 0.4);
  color: #5cdcff;
  font-size: 10px;
  letter-spacing: 2px;
  font-weight: 700;
}

/* 设备列表 */
.hp-devices {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 250px;
  overflow-y: auto;
  padding-right: 2px;
}
.hp-devices::-webkit-scrollbar { width: 4px; }
.hp-devices::-webkit-scrollbar-thumb { background: rgba(92, 220, 255, 0.3); border-radius: 2px; }

.hp-device {
  --row-color: #5cdcff;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px 8px 14px;
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.65), rgba(8, 16, 30, 0.45));
  border: 1px solid rgba(92, 220, 255, 0.08);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.22s ease;
  overflow: hidden;
}
.hp-device__bar {
  position: absolute;
  left: 0; top: 6px; bottom: 6px;
  width: 3px;
  background: var(--row-color);
  box-shadow: 0 0 6px var(--row-color);
  border-radius: 0 2px 2px 0;
}
.hp-device:hover {
  border-color: var(--row-color);
  background: linear-gradient(180deg, rgba(20, 50, 80, 0.7), rgba(10, 25, 41, 0.55));
  transform: translateX(2px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--row-color) 30%, transparent);
}
.hp-device.is-active {
  border-color: var(--row-color);
  background: linear-gradient(180deg, rgba(0, 50, 80, 0.78), rgba(10, 25, 41, 0.6));
  box-shadow: 0 0 22px color-mix(in srgb, var(--row-color) 45%, transparent),
              inset 0 0 12px color-mix(in srgb, var(--row-color) 18%, transparent);
}

.hp-device__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hp-device__name {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.hp-device__dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--row-color);
  box-shadow: 0 0 6px var(--row-color);
  flex-shrink: 0;
}
.hp-device__name strong {
  font-size: 12px;
  letter-spacing: 1.5px;
  color: #ffffff;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hp-device__pill {
  font-size: 9px;
  letter-spacing: 1.5px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--row-color) 16%, transparent);
  border: 1px solid color-mix(in srgb, var(--row-color) 40%, transparent);
  color: var(--row-color);
  text-shadow: 0 0 6px color-mix(in srgb, var(--row-color) 50%, transparent);
}
.hp-device__body {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.hp-device__metric {
  display: flex;
  align-items: baseline;
  gap: 3px;
}
.hp-device__metric span {
  font-size: 9px;
  color: #94a3b8;
  letter-spacing: 1px;
}
.hp-device__metric strong {
  font-size: 13px;
  color: var(--row-color);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 6px color-mix(in srgb, var(--row-color) 40%, transparent);
}
.hp-device__metric em {
  font-style: normal;
  font-size: 8px;
  color: #94a3b8;
  font-weight: 500;
}
.hp-device__sparkline {
  margin-top: 2px;
  height: 28px;
}

.hp-empty {
  padding: 16px;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
}

/* 今日概况 */
.hp-overview, .hp-forecast {
  padding: 10px 12px;
  background: rgba(13, 37, 64, 0.5);
  border: 1px solid rgba(92, 220, 255, 0.1);
  border-radius: 10px;
}
.hp-overview__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.hp-overview__head h4 {
  margin: 0;
  font-size: 11px;
  letter-spacing: 2.5px;
  color: #5cdcff;
  font-weight: 700;
}
.hp-overview__date {
  font-size: 9px;
  color: #94a3b8;
  letter-spacing: 1px;
  font-family: 'SF Mono', monospace;
}
.hp-forecast__list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.hp-forecast__chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 6px 4px;
  background: linear-gradient(180deg, rgba(255, 126, 0, 0.16), rgba(255, 126, 0, 0.04));
  border: 1px solid rgba(255, 126, 0, 0.3);
  border-radius: 6px;
}
.hp-forecast__chip span {
  font-size: 9px;
  color: #ffb347;
  letter-spacing: 1px;
  font-weight: 600;
}
.hp-forecast__chip strong {
  font-size: 13px;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.hp-forecast__chip em {
  font-style: normal;
  font-size: 8px;
  color: #94a3b8;
}
</style>
