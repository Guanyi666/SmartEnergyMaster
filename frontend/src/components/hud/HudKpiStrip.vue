<template>
  <div class="hud-kpi-strip">
    <div class="hk-title">
      <span class="hk-title__mark">DIGITAL TWIN</span>
      <h2>工厂数字孪生 · 实时驾驶舱</h2>
    </div>

    <div class="hk-kpis">
      <div
        v-for="kpi in kpis"
        :key="kpi.key"
        class="hk-kpi"
        :class="`hk-kpi--${kpi.tone}`"
        @mouseenter="$emit('hover-kpi', kpi.deviceType)"
        @mouseleave="$emit('hover-kpi', null)"
      >
        <span class="hk-kpi__bar"></span>
        <div class="hk-kpi__icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <component :is="iconPath(kpi.icon)" />
          </svg>
        </div>
        <div class="hk-kpi__body">
          <span class="hk-kpi__label">{{ kpi.label }}</span>
          <strong class="hk-kpi__value">{{ kpi.value }}<em>{{ kpi.unit }}</em></strong>
        </div>
        <div class="hk-kpi__trend">
          <svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor">
            <path d="M5 14l5-5 4 4 5-5v8H5z"/>
          </svg>
          <span>{{ kpi.trend }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { h, computed } from 'vue'

const props = defineProps({
  summary: { type: Object, default: () => ({}) },
  devices: { type: Array, default: () => [] },
  alertCount: { type: Number, default: 0 }
})
defineEmits(['hover-kpi'])

const runningCount = computed(() => props.devices.filter(d => !['OFFLINE','STOPPED'].includes(d.status)).length)
const faultCount = computed(() => props.devices.filter(d => ['FAULT','MAINTENANCE'].includes(d.status)).length)

const kpis = computed(() => [
  {
    key: 'total', tone: 'cyan', icon: 'cpu', deviceType: null,
    label: '设备总数 / TOTAL',
    value: props.devices.length,
    unit: '台',
    trend: `在线 ${runningCount.value}`
  },
  {
    key: 'power', tone: 'green', icon: 'bolt', deviceType: 'TRANSFORMER',
    label: '总有功功率 / POWER',
    value: Number(props.summary.totalUsageKwh || 0).toFixed(1),
    unit: 'kWh',
    trend: '实时'
  },
  {
    key: 'co2', tone: 'orange', icon: 'sun', deviceType: 'BOILER',
    label: '碳排放 / CO₂',
    value: Number(props.summary.totalCo2Emission || 0).toFixed(1),
    unit: '吨',
    trend: '累计'
  },
  {
    key: 'fault', tone: 'red', icon: 'warning', deviceType: 'FAN',
    label: '故障设备 / FAULT',
    value: faultCount.value,
    unit: '台',
    trend: `告警 ${props.alertCount}`
  }
])

const iconPath = (name) => {
  switch (name) {
    case 'cpu': return () => h('g', [
      h('rect', { x: 4, y: 4, width: 16, height: 16, rx: 2 }),
      h('rect', { x: 9, y: 9, width: 6, height: 6 }),
      h('line', { x1: 9, y1: 1, x2: 9, y2: 4 }),
      h('line', { x1: 15, y1: 1, x2: 15, y2: 4 }),
      h('line', { x1: 9, y1: 20, x2: 9, y2: 23 }),
      h('line', { x1: 15, y1: 20, x2: 15, y2: 23 }),
      h('line', { x1: 20, y1: 9, x2: 23, y2: 9 }),
      h('line', { x1: 20, y1: 15, x2: 23, y2: 15 }),
      h('line', { x1: 1, y1: 9, x2: 4, y2: 9 }),
      h('line', { x1: 1, y1: 15, x2: 4, y2: 15 })
    ])
    case 'bolt': return () => h('polygon', { points: '13 2 3 14 12 14 11 22 21 10 12 10 13 2' })
    case 'sun': return () => h('g', [
      h('circle', { cx: 12, cy: 12, r: 5 }),
      h('line', { x1: 12, y1: 1, x2: 12, y2: 3 }),
      h('line', { x1: 12, y1: 21, x2: 12, y2: 23 }),
      h('line', { x1: 4.22, y1: 4.22, x2: 5.64, y2: 5.64 }),
      h('line', { x1: 18.36, y1: 18.36, x2: 19.78, y2: 19.78 }),
      h('line', { x1: 1, y1: 12, x2: 3, y2: 12 }),
      h('line', { x1: 21, y1: 12, x2: 23, y2: 12 }),
      h('line', { x1: 4.22, y1: 19.78, x2: 5.64, y2: 18.36 }),
      h('line', { x1: 18.36, y1: 5.64, x2: 19.78, y2: 4.22 })
    ])
    case 'warning': return () => h('g', [
      h('polygon', { points: '12 2 22 20 2 20 12 2' }),
      h('line', { x1: 12, y1: 9, x2: 12, y2: 14 }),
      h('circle', { cx: 12, cy: 17, r: 0.5, fill: 'currentColor' })
    ])
  }
}
</script>

<style scoped>
.hud-kpi-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  pointer-events: auto;
  background: rgba(8, 16, 30, 0.62);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  border: 1px solid rgba(0, 255, 255, 0.22);
  border-radius: 12px;
  padding: 10px 16px;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.5),
    0 0 22px rgba(0, 255, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  font-family: Bahnschrift, 'Microsoft YaHei', sans-serif;
  color: #ffffff;
}

.hud-kpi-strip::before {
  content: '';
  position: absolute;
  top: 0; left: 5%;
  width: 90%; height: 1px;
  background: linear-gradient(90deg, transparent, #00ffff, transparent);
  opacity: 0.55;
}

.hk-title {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  padding-right: 14px;
  border-right: 1px solid rgba(92, 220, 255, 0.18);
  flex-shrink: 0;
}
.hk-title__mark {
  font-size: 9px;
  letter-spacing: 4px;
  color: #5cdcff;
  font-weight: 600;
  margin-bottom: 4px;
}
.hk-title h2 {
  margin: 0;
  font-size: 14px;
  letter-spacing: 3px;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 0 10px rgba(92, 220, 255, 0.4);
}

.hk-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  flex: 1;
}

.hk-kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 8px 16px;
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.55), rgba(8, 16, 30, 0.4));
  border: 1px solid rgba(92, 220, 255, 0.1);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.22s ease;
}
.hk-kpi:hover {
  transform: translateY(-1px);
  background: linear-gradient(180deg, rgba(20, 50, 80, 0.65), rgba(10, 25, 41, 0.5));
}

.hk-kpi__bar {
  position: absolute;
  left: 0; top: 8px; bottom: 8px;
  width: 3px;
  border-radius: 0 2px 2px 0;
}
.hk-kpi--cyan   .hk-kpi__bar { background: #5cdcff; box-shadow: 0 0 6px #5cdcff; }
.hk-kpi--green  .hk-kpi__bar { background: #3bff9f; box-shadow: 0 0 6px #3bff9f; }
.hk-kpi--orange .hk-kpi__bar { background: #ff7e00; box-shadow: 0 0 6px #ff7e00; }
.hk-kpi--red    .hk-kpi__bar { background: #ff5d5d; box-shadow: 0 0 6px #ff5d5d; }

.hk-kpi:hover.hk-kpi--cyan   { border-color: #5cdcff; box-shadow: 0 0 18px rgba(92, 220, 255, 0.3); }
.hk-kpi:hover.hk-kpi--green  { border-color: #3bff9f; box-shadow: 0 0 18px rgba(59, 255, 159, 0.3); }
.hk-kpi:hover.hk-kpi--orange { border-color: #ff7e00; box-shadow: 0 0 18px rgba(255, 126, 0, 0.3); }
.hk-kpi:hover.hk-kpi--red    { border-color: #ff5d5d; box-shadow: 0 0 18px rgba(255, 93, 93, 0.3); }

.hk-kpi__icon {
  width: 30px; height: 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  flex-shrink: 0;
}
.hk-kpi--cyan   .hk-kpi__icon { background: rgba(92, 220, 255, 0.16); color: #5cdcff; }
.hk-kpi--green  .hk-kpi__icon { background: rgba(59, 255, 159, 0.16); color: #3bff9f; }
.hk-kpi--orange .hk-kpi__icon { background: rgba(255, 126, 0, 0.16); color: #ff7e00; }
.hk-kpi--red    .hk-kpi__icon { background: rgba(255, 93, 93, 0.16); color: #ff5d5d; }

.hk-kpi__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.hk-kpi__label {
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #94a3b8;
  font-weight: 600;
}
.hk-kpi__value {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  margin-top: 2px;
}
.hk-kpi__value em {
  font-style: normal;
  font-size: 10px;
  margin-left: 4px;
  color: #94a3b8;
  font-weight: 500;
}
.hk-kpi__trend {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  letter-spacing: 1px;
  color: #94a3b8;
  align-self: flex-end;
}
.hk-kpi--green .hk-kpi__trend { color: #3bff9f; }
.hk-kpi--red .hk-kpi__trend { color: #ff5d5d; }
</style>
