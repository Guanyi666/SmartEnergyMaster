<template>
  <aside class="hud-right">
    <!-- ─── 段一:重点设备运行监测 ─── -->
    <div class="hud-block hrp-block">
      <header class="hud-block-title">
        <span class="hud-block-title__cn">重点设备运行监测</span>
        <span class="hud-block-title__en">DEVICE MONITOR</span>
      </header>
      <div class="hrp-block__body hrp-monitor">
        <!-- 顶部:设备名 + 右上角小环形 -->
        <div class="hrp-monitor__head">
          <div class="hrp-monitor__name">
            <strong>{{ focus?.deviceName || '1号电弧炉' }}</strong>
            <span>{{ focus?.deviceCode || 'EAF-01' }} · {{ deviceTypeLabel }}</span>
          </div>
          <TechRingGauge :value="loadPct" :size="56" />
        </div>
        <!-- 中部:温度 + 功率 数据 -->
        <div class="hrp-monitor__data">
          <div class="hrp-stat">
            <span class="hrp-stat__label">温度 TEMP</span>
            <strong class="hud-num" style="color:#ff7e00; text-shadow: 0 0 6px rgba(255,126,0,0.5)">
              {{ focus ? formatNum(focus.temperature) : '--' }}<em>°C</em>
            </strong>
          </div>
          <div class="hrp-stat">
            <span class="hrp-stat__label">湿度 HUM</span>
            <strong class="hud-num">
              {{ humidity }}<em>%</em>
            </strong>
          </div>
          <div class="hrp-stat">
            <span class="hrp-stat__label">功率 POWER</span>
            <strong class="hud-num">
              {{ focus ? formatNum(focus.usageKwh) : '--' }}<em>kWh</em>
            </strong>
          </div>
        </div>
        <!-- 底部:迷你折线趋势 -->
        <div class="hrp-monitor__trend">
          <span class="hrp-monitor__trend-label">REAL-TIME · 24 PTS</span>
          <TechMiniLine :data="trendData" color="#00FFFF" :height="46" />
        </div>
      </div>
    </div>

    <!-- ─── 段二:AI 能耗预测 ─── -->
    <div class="hud-block hrp-block">
      <header class="hud-block-title">
        <span class="hud-block-title__cn">AI 能耗预测</span>
        <span class="hud-block-title__en">FORECAST · LSTM</span>
      </header>
      <div class="hrp-block__body hrp-forecast">
        <div v-for="f in forecastBars" :key="f.label" class="hrp-fcst">
          <span class="hrp-fcst__label">{{ f.label }}</span>
          <div class="hrp-fcst__track">
            <span class="hrp-fcst__fill" :style="{ width: f.pct + '%' }"></span>
          </div>
          <strong class="hrp-fcst__value hud-num">
            {{ f.value }}<em>{{ f.unit }}</em>
          </strong>
        </div>

        <!-- AI 调度建议框 (硬朗 1px 青色边) -->
        <div class="hrp-advice" :class="`l-${(advice?.level || 'INFO').toLowerCase()}`">
          <div class="hrp-advice__head">
            <span class="hrp-advice__tag">⚠ 调度建议</span>
            <span class="hrp-advice__level">{{ adviceLevel }}</span>
          </div>
          <p class="hrp-advice__body">{{ advice?.title || '系统正在分析最优策略...' }}</p>
          <p class="hrp-advice__content">{{ advice?.content || '电力时段平稳,运行状态良好。' }}</p>
          <div v-if="advice && advice.level !== 'INFO'" class="hrp-advice__actions">
            <template v-if="!adviceDecided">
              <button class="hrp-btn hrp-btn--primary" @click="$emit('decide', 'CONFIRM')">采纳</button>
              <button class="hrp-btn" @click="$emit('decide', 'REJECT')">忽略</button>
            </template>
            <span v-else class="hrp-advice__decided">
              {{ adviceDecided === 'CONFIRM' ? '✓ 已采纳，请按建议执行' : '已忽略本次建议' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- ─── 段三:设备状态矩阵 ─── -->
    <div class="hud-block hrp-block hrp-block--matrix">
      <header class="hud-block-title">
        <span class="hud-block-title__cn">设备状态矩阵</span>
        <span class="hud-block-title__en">STATUS MATRIX</span>
      </header>
      <div class="hrp-block__body hrp-matrix">
        <div
          v-for="d in devices"
          :key="d.id || d.deviceCode"
          class="hrp-cell"
          :class="[
            { 'is-active': d.deviceCode === focusCode },
            `s-${(d.status || 'offline').toLowerCase()}`
          ]"
          @click="$emit('select', d)"
        >
          <span class="hrp-cell__dot"></span>
          <span class="hrp-cell__name">{{ d.deviceName }}</span>
          <span class="hrp-cell__state">{{ statusText(d.status) }}</span>
        </div>
        <div v-if="!devices.length" class="hrp-empty">— NO DEVICE —</div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import TechRingGauge from './TechRingGauge.vue'
import TechMiniLine from './TechMiniLine.vue'

const props = defineProps({
  devices: { type: Array, default: () => [] },
  focusCode: { type: String, default: '' },
  summary: { type: Object, default: () => ({}) },
  forecast: { type: Array, default: () => [] },
  advice: { type: Object, default: null },
  // 'CONFIRM' | 'REJECT' | null：当前建议是否已被采纳/忽略
  adviceDecided: { type: String, default: null }
})
defineEmits(['select', 'decide'])

const DEVICE_TYPE_LABELS = {
  ARC_FURNACE: '电弧炉', PUMP: '循环水泵', COMPRESSOR: '空压机',
  FAN: '风机', TRANSFORMER: '变压器', BOILER: '锅炉'
}
const STATUS_TEXT = {
  RUNNING: '运行', HIGH_LOAD: '高负荷', IDLE: '空转',
  STOPPED: '停机', OFFLINE: '离线',
  FAULT: '故障', MAINTENANCE: '维修'
}
const statusText = (s) => STATUS_TEXT[s] || '未知'

const focus = computed(() => props.devices.find(d => d.deviceCode === props.focusCode) || null)
const deviceTypeLabel = computed(() => DEVICE_TYPE_LABELS[focus.value?.deviceType] || '设备')
const loadPct = computed(() => {
  // 使用后端返回的 eafLoadRate，若无则按 45MW 额定计算
  const s = props.summary || {}
  if (s.eafLoadRate != null) return Number(s.eafLoadRate)
  const u = Number(focus.value?.usageKwh) || 0
  return Math.max(0, Math.min(100, Math.round(u / 450)))
})
const humidity = computed(() => {
  // 基于温度反向推导一个伪湿度(实际项目可接真实传感器)
  const t = Number(focus.value?.temperature) || 25
  return Math.max(20, Math.min(95, Math.round(85 - t / 22)))
})

const adviceLevel = computed(() => ({
  CRITICAL: '紧急', WARN: '预警', GOOD: '机会', INFO: '正常'
}[props.advice?.level] || '正常'))

const formatNum = (n) => {
  if (n == null || n === '') return '--'
  const v = Number(n)
  return Number.isNaN(v) ? '--' : v.toFixed(1)
}

// ============ 24 点实时趋势 (随聚焦设备 usageKwh 抖动) ============
const trendCache = ref([])
watch([() => focus.value?.usageKwh, () => props.focusCode], () => {
  const base = Number(focus.value?.usageKwh) || 50
  trendCache.value.push(base + (Math.random() - 0.5) * (base * 0.2 + 4))
  if (trendCache.value.length > 24) trendCache.value.shift()
}, { immediate: true })
const trendData = computed(() => trendCache.value.length ? trendCache.value.slice() : Array(12).fill(50))

// ============ 3 条预测进度条 ============
const forecastBars = computed(() => {
  const f = props.forecast || []
  const currentLoad = Number(focus.value?.usageKwh || 0)
  const at = (min) => {
    if (!f.length) return currentLoad
    let best = f[0]
    for (const p of f) {
      if (Math.abs(p.minutesAhead - min) < Math.abs(best.minutesAhead - min)) best = p
    }
    return Number(best.mean || currentLoad)
  }
  const now = currentLoad
  const h15 = at(15)
  const h30 = at(30)
  const max = Math.max(now, h15, h30, 1)
  return [
    { label: '当前', value: (now / 1000).toFixed(1), unit: 'MW', pct: Math.round((now / max) * 100) },
    { label: '15min', value: (h15 / 1000).toFixed(1), unit: 'MW', pct: Math.round((h15 / max) * 100) },
    { label: '30min', value: (h30 / 1000).toFixed(1), unit: 'MW', pct: Math.round((h30 / max) * 100) }
  ]
})
</script>

<style scoped>
.hud-right {
  width: 400px;
  /* ★ 严格高度 = 视口 - Header(88) - BottomNav(100) - 各种 padding */
  height: 100%;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  gap: 15px;
  pointer-events: auto;
  background: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  overflow: hidden;
}

/* ★ 三段平分纵向 + 严格 overflow:hidden 防止溢出 */
.hrp-block {
  flex: 1 1 0;
  min-height: 0;       /* ★ 关键:允许 flex 子项收缩到 0 以下 */
  display: flex;
  flex-direction: column;
  overflow: hidden;    /* ★ 容器层切断溢出 */
}
.hrp-block__body {
  flex: 1 1 0;
  min-height: 0;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;    /* ★ body 层也截断 */
}

/* ─── 设备监测 ─── */
.hrp-monitor__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  background: rgba(0, 40, 70, 0.3);
  border: 1px solid rgba(0, 255, 255, 0.1);
  flex-shrink: 0;
}
.hrp-monitor__name { display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
.hrp-monitor__name strong {
  font-size: 15px;
  color: #ffffff;
  letter-spacing: 2px;
  font-weight: 600;
}
.hrp-monitor__name span {
  font-size: 10px;
  color: rgba(0, 255, 255, 0.55);
  letter-spacing: 1px;
  margin-top: 2px;
  font-family: 'DIN', Arial, monospace;
}

.hrp-monitor__data {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
  flex-shrink: 0;
}
.hrp-stat {
  position: relative;
  padding: 6px 8px;
  background: rgba(0, 30, 60, 0.35);
  border: 1px solid rgba(0, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hrp-stat::before {
  content: '';
  position: absolute;
  left: 0; top: 4px; bottom: 4px;
  width: 2px;
  background: #00FFFF;
  box-shadow: 0 0 4px #00FFFF;
}
.hrp-stat__label {
  font-size: 9px;
  letter-spacing: 1px;
  color: rgba(217, 232, 245, 0.5);
}
.hrp-stat strong {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.1;
}
.hrp-stat strong em {
  font-style: normal;
  font-size: 9px;
  margin-left: 3px;
  color: rgba(217, 232, 245, 0.5);
  font-weight: 500;
}

.hrp-monitor__trend {
  padding: 4px 6px 2px;
  background: rgba(0, 20, 40, 0.4);
  border: 1px solid rgba(0, 255, 255, 0.08);
  flex: 1 1 0;
  min-height: 60px;
  display: flex;
  flex-direction: column;
}
.hrp-monitor__trend-label {
  display: block;
  font-size: 9px;
  letter-spacing: 1.5px;
  color: rgba(0, 255, 255, 0.5);
  margin-bottom: 2px;
}

/* ─── AI 预测条 ─── */
.hrp-forecast {
  gap: 6px;
}
.hrp-fcst {
  display: grid;
  grid-template-columns: 34px 1fr 80px;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.hrp-fcst__label {
  font-size: 11px;
  color: #00FFFF;
  letter-spacing: 1.5px;
  font-weight: 700;
  text-align: right;
  font-family: 'DIN', Arial, monospace;
}
.hrp-fcst__track {
  position: relative;
  height: 12px;
  background: rgba(0, 30, 60, 0.6);
  border: 1px solid rgba(0, 255, 255, 0.12);
  overflow: hidden;
}
.hrp-fcst__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg,
    rgba(0, 80, 130, 0.5),
    #00FFFF 95%);
  box-shadow: 0 0 8px #00FFFF, inset 0 0 6px rgba(0, 200, 255, 0.3);
  transition: width 0.6s ease;
  position: relative;
}
.hrp-fcst__fill::after {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(135deg,
    transparent 0,
    transparent 6px,
    rgba(255,255,255,0.1) 6px,
    rgba(255,255,255,0.1) 8px);
  animation: fcst-flow 1.6s linear infinite;
}
@keyframes fcst-flow { to { background-position: 16px 0; } }
.hrp-fcst__value {
  font-size: 13px;
  font-weight: 700;
  text-align: right;
}
.hrp-fcst__value em {
  font-style: normal;
  font-size: 9px;
  margin-left: 3px;
  color: rgba(217, 232, 245, 0.55);
  font-weight: 500;
}

/* AI 调度建议 (硬朗 1px 青色边) */
.hrp-advice {
  margin-top: 4px;
  padding: 8px 10px;
  background: rgba(0, 30, 60, 0.4);
  border: 1px solid #00FFFF;
  border-radius: 2px;
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  /* ★ 隐藏滚动条 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.hrp-advice::-webkit-scrollbar { display: none; }
.hrp-advice.l-warn { border-color: #ffaa00; }
.hrp-advice.l-critical {
  border-color: #ff4855;
  animation: adv-blink 1.4s ease-in-out infinite;
}
@keyframes adv-blink {
  0%, 100% { box-shadow: 0 0 0 transparent; }
  50% { box-shadow: 0 0 12px rgba(255, 72, 85, 0.4); }
}
.hrp-advice__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.hrp-advice__tag {
  font-size: 11px;
  color: #00FFFF;
  letter-spacing: 1.5px;
  font-weight: 700;
}
.hrp-advice.l-warn .hrp-advice__tag { color: #ffaa00; }
.hrp-advice.l-critical .hrp-advice__tag { color: #ff4855; }
.hrp-advice__level {
  font-size: 9px;
  letter-spacing: 1.5px;
  border: 1px solid currentColor;
  padding: 0 6px;
  color: #00FFFF;
}
.hrp-advice__body {
  margin: 0;
  font-size: 11.5px;
  color: #ffffff;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.hrp-advice__content {
  margin: 3px 0 0;
  font-size: 10px;
  color: rgba(217, 232, 245, 0.5);
  line-height: 1.5;
}
.hrp-advice__actions { display: flex; gap: 6px; margin-top: 6px; align-items: center; }
.hrp-advice__decided {
  font-size: 12px;
  color: #3bff9f;
  letter-spacing: 0.5px;
}
.hrp-btn {
  padding: 3px 12px;
  background: transparent;
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: rgba(217, 232, 245, 0.7);
  font-size: 10px;
  letter-spacing: 1.5px;
  font-family: inherit;
  cursor: pointer;
  border-radius: 2px;
}
.hrp-btn:hover { color: #00FFFF; border-color: #00FFFF; }
.hrp-btn--primary {
  background: rgba(0, 200, 255, 0.2);
  border-color: #00FFFF;
  color: #ffffff;
}

/* ─── 设备状态矩阵 ─── */
.hrp-matrix {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 5px;
  align-content: start;
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  /* ★ 严格隐藏滚动条 (跨浏览器) */
  scrollbar-width: none;       /* Firefox */
  -ms-overflow-style: none;    /* IE 10+ */
}
.hrp-matrix::-webkit-scrollbar {
  display: none;               /* Chrome / Safari / Webkit */
  width: 0; height: 0;
}
.hrp-cell {
  --c: rgba(217, 232, 245, 0.4);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: rgba(0, 30, 60, 0.35);
  border: 1px solid rgba(0, 255, 255, 0.08);
  cursor: pointer;
  transition: all 0.2s ease;
}
.hrp-cell:hover {
  background: rgba(0, 60, 100, 0.5);
  border-color: rgba(0, 255, 255, 0.4);
}
.hrp-cell.is-active {
  background: rgba(0, 200, 255, 0.18);
  border-color: #00FFFF;
  box-shadow: inset 0 0 8px rgba(0, 200, 255, 0.18);
}
.hrp-cell__dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: var(--c);
  flex-shrink: 0;
}
.hrp-cell.s-running, .hrp-cell.s-high_load { --c: #00ff88; }
.hrp-cell.s-idle { --c: #00FFFF; }
.hrp-cell.s-fault, .hrp-cell.s-maintenance { --c: #ff4855; }
.hrp-cell.s-running .hrp-cell__dot,
.hrp-cell.s-high_load .hrp-cell__dot,
.hrp-cell.s-fault .hrp-cell__dot {
  box-shadow: 0 0 6px var(--c);
  animation: hrp-blink 1.4s ease-in-out infinite;
}
@keyframes hrp-blink { 0%,100% { opacity:1 } 50% { opacity:.5 } }
.hrp-cell__name {
  flex: 1;
  font-size: 10.5px;
  color: #ffffff;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hrp-cell__state {
  font-size: 9px;
  letter-spacing: 1px;
  color: var(--c);
  font-weight: 700;
  font-family: 'DIN', Arial, monospace;
}
.hrp-empty {
  grid-column: span 2;
  padding: 20px;
  text-align: center;
  font-size: 10px;
  letter-spacing: 3px;
  color: rgba(217, 232, 245, 0.3);
}
</style>
