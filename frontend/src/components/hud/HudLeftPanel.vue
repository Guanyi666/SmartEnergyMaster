<template>
  <aside class="hud-left">
    <!-- ─── 段一:全厂能源总览 ─── -->
    <div class="hud-block hlp-block">
      <header class="hud-block-title">
        <span class="hud-block-title__cn">全厂能源总览</span>
        <span class="hud-block-title__en">FACTORY OVERVIEW</span>
      </header>
      <div class="hlp-block__body">
        <!-- 2x2 数据网格 -->
        <ul class="hlp-grid">
          <li v-for="m in metrics" :key="m.key" class="hlp-grid__cell">
            <span class="hlp-grid__label">{{ m.label }}</span>
            <strong class="hlp-grid__val hud-num">
              {{ m.value }}<em>{{ m.unit }}</em>
            </strong>
          </li>
        </ul>
        <!-- 双发光进度条 -->
        <div class="hlp-bars">
          <div v-for="b in bars" :key="b.label" class="hlp-bar">
            <div class="hlp-bar__head">
              <span>{{ b.label }}</span>
              <strong class="hud-num">{{ b.value }}%</strong>
            </div>
            <div class="hlp-bar__track">
              <span class="hlp-bar__fill" :style="{ width: b.value + '%' }"></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ─── 段二:今日分时用能 ─── -->
    <div class="hud-block hlp-block">
      <header class="hud-block-title">
        <span class="hud-block-title__cn">今日分时用能</span>
        <span class="hud-block-title__en">HOURLY USAGE</span>
      </header>
      <div class="hlp-block__body hlp-block__body--chart">
        <TechBarChart :data="hourlyData" unit="kWh" :height="160" />
      </div>
    </div>

    <!-- ─── 段三:实时告警监控 ─── -->
    <div class="hud-block hlp-block hlp-block--alerts">
      <header class="hud-block-title">
        <span class="hud-block-title__cn">实时告警监控</span>
        <span class="hud-block-title__en">ALERT MONITOR</span>
      </header>
      <div class="hlp-block__body hlp-block__body--alerts">
        <ul v-if="alerts.length" class="hlp-alerts">
          <li v-for="a in alerts.slice(0, 4)" :key="a.id" class="hlp-alert"
              :class="`p-${(a.priority || 'medium').toLowerCase()}`">
            <span class="hlp-alert__bar"></span>
            <strong>{{ a.title || a.faultType || '设备异常' }}</strong>
            <span class="hlp-alert__meta">
              {{ formatTime(a.createdAt) }} · {{ a.deviceName || '—' }}
            </span>
          </li>
        </ul>
        <div v-else class="hlp-empty">
          <span class="hlp-empty__icon">●</span>
          <p>当前设备运行稳定,暂无实时告警</p>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import TechBarChart from './TechBarChart.vue'

const props = defineProps({
  summary: { type: Object, default: () => ({}) },
  devices: { type: Array, default: () => [] },
  alerts:  { type: Array, default: () => [] }
})

// ============ 2x2 总览数据 ============
const runningCount = computed(() =>
  props.devices.filter(d => !['OFFLINE','STOPPED'].includes(d.status)).length
)
const runRate = computed(() =>
  props.devices.length ? Math.round((runningCount.value / props.devices.length) * 1000) / 10 : 0
)
const metrics = computed(() => {
  const s = props.summary || {}
  return [
    { key: 'power',  label: '总耗电',  value: Number(s.totalUsageKwh || 0).toFixed(0), unit: 'kWh' },
    { key: 'water',  label: '总耗水',  value: Math.round(Number(s.totalUsageKwh || 0) * 6.5), unit: 'm³' },
    { key: 'total',  label: '总设备',  value: props.devices.length, unit: '台' },
    { key: 'run',    label: '运行率',  value: runRate.value, unit: '%' }
  ]
})

// ============ 双进度条 ============
const bars = computed(() => {
  const s = props.summary || {}
  return [
    { label: '电力负载 POWER',  value: Math.min(100, Math.round(Number(s.totalUsageKwh || 0) / 30)) },
    { label: '水资源 WATER',    value: Math.min(100, Math.round(Number(s.totalUsageKwh || 0) / 50)) }
  ]
})

// ============ 24h 分时用能 ============
const HOUR_PROFILE = [
  0.62, 0.58, 0.54, 0.52, 0.50, 0.55, 0.70, 0.85,
  0.95, 1.00, 1.08, 1.12, 1.08, 1.05, 1.02, 0.98,
  0.94, 0.92, 0.88, 0.84, 0.78, 0.72, 0.68, 0.65
]
const hourlyData = computed(() => {
  const base = Math.max(60, Number(props.summary.totalUsageKwh || 100)) * 1.3
  return HOUR_PROFILE.map((w, i) => ({
    label: String(i).padStart(2, '0'),
    value: Math.round(base * w + Math.sin(i * 0.7) * (base * 0.04))
  }))
})

function formatTime (iso) {
  if (!iso) return '--:--'
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<style scoped>
.hud-left {
  width: 400px;
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

/* 三段平分纵向 + 截断溢出 */
.hlp-block {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.hlp-block--alerts { flex: 1 1 0; }
.hlp-block__body {
  flex: 1 1 0;
  min-height: 0;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
}
.hlp-block__body--chart { padding: 6px 4px 8px; }
.hlp-block__body--alerts { padding: 10px 12px; }

/* ─── 2x2 数据网格 ─── */
.hlp-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  flex-shrink: 0;
}
.hlp-grid__cell {
  position: relative;
  padding: 8px 10px;
  background: linear-gradient(180deg, rgba(0, 50, 90, 0.25), rgba(0, 15, 30, 0.05));
  border: 1px solid rgba(0, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hlp-grid__label {
  font-size: 11px;
  color: rgba(217, 232, 245, 0.55);
  letter-spacing: 1px;
}
.hlp-grid__val {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
}
.hlp-grid__val em {
  font-style: normal;
  font-size: 10px;
  margin-left: 4px;
  color: rgba(217, 232, 245, 0.5);
  font-weight: 500;
}

/* ─── 双发光进度条 ─── */
.hlp-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}
.hlp-bar { display: flex; flex-direction: column; gap: 3px; }
.hlp-bar__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 10px;
  letter-spacing: 1px;
  color: rgba(217, 232, 245, 0.6);
}
.hlp-bar__head strong { font-size: 12px; }
.hlp-bar__track {
  position: relative;
  height: 5px;
  background: rgba(0, 30, 60, 0.6);
  border: 1px solid rgba(0, 255, 255, 0.12);
  overflow: hidden;
}
.hlp-bar__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg,
    rgba(0, 100, 160, 0.4),
    #00FFFF 95%);
  box-shadow: 0 0 6px #00FFFF;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ─── 告警列表 + 空态 ─── */
.hlp-alerts {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.hlp-alerts::-webkit-scrollbar { display: none; width: 0; height: 0; }
.hlp-alert {
  position: relative;
  padding: 6px 10px 6px 14px;
  background: rgba(0, 30, 60, 0.4);
  border: 1px solid rgba(0, 255, 255, 0.1);
}
.hlp-alert__bar {
  position: absolute;
  left: 0; top: 4px; bottom: 4px;
  width: 3px;
  background: var(--c, #ffaa00);
  box-shadow: 0 0 6px var(--c, #ffaa00);
}
.hlp-alert.p-critical { --c: #ff4855; }
.hlp-alert.p-high { --c: #ff7e6b; }
.hlp-alert.p-medium { --c: #ffaa00; }
.hlp-alert.p-low { --c: #00FFFF; }
.hlp-alert strong {
  display: block;
  font-size: 11px;
  color: #ffffff;
  letter-spacing: 0.5px;
  font-weight: 600;
}
.hlp-alert__meta {
  display: block;
  font-size: 9.5px;
  color: rgba(217, 232, 245, 0.45);
  margin-top: 2px;
  letter-spacing: 0.5px;
}

.hlp-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed rgba(0, 255, 255, 0.12);
  background: rgba(0, 20, 40, 0.25);
}
.hlp-empty__icon {
  font-size: 14px;
  color: #00ff88;
  text-shadow: 0 0 10px #00ff88;
  animation: hlp-blink 1.6s ease-in-out infinite;
}
@keyframes hlp-blink { 0%,100% { opacity:1 } 50% { opacity:.4 } }
.hlp-empty p {
  margin: 0;
  font-size: 12px;
  color: rgba(217, 232, 245, 0.55);
  letter-spacing: 1px;
}
</style>
