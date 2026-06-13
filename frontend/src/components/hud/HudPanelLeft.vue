<template>
  <aside class="hud-panel hud-panel--left">
    <!-- 4 角折角线框 -->
    <span class="hp-corner hp-corner--tl"></span>
    <span class="hp-corner hp-corner--tr"></span>
    <span class="hp-corner hp-corner--bl"></span>
    <span class="hp-corner hp-corner--br"></span>

    <!-- 头部 -->
    <header class="hp-header">
      <div>
        <p class="hp-eyebrow">FACTORY STATUS</p>
        <h3 class="hp-title">设备状态总览</h3>
      </div>
      <span class="hp-live">
        <span class="hp-live__dot"></span>LIVE
      </span>
    </header>

    <!-- 多环仪表 -->
    <div class="hp-rings">
      <MultiRingGauge
        :rings="rings"
        :center-value="`${runningCount} / ${totalCount}`"
        center-label="RUNNING / TOTAL"
      />
    </div>

    <!-- 状态分布列表 -->
    <ul class="hp-legend">
      <li v-for="r in rings" :key="r.name">
        <span class="hp-legend__dot" :style="{ background: r.color, boxShadow: `0 0 8px ${r.color}` }"></span>
        <span class="hp-legend__name">{{ r.name }}</span>
        <span class="hp-legend__val" :style="{ color: r.color }">{{ r.value }} <em>/ {{ r.max }}</em></span>
      </li>
    </ul>

    <!-- 智能调度建议 (呼吸灯告警) -->
    <section class="hp-advice" :class="`hp-advice--${(advice?.level || 'INFO').toLowerCase()}`">
      <div class="hp-advice__halo"></div>
      <div class="hp-advice__head">
        <div class="hp-advice__icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M12 2 L13 9 L20 9 L14 14 L16 22 L12 18 L8 22 L10 14 L4 9 L11 9 Z"
                  fill="currentColor"/>
          </svg>
        </div>
        <div class="hp-advice__title">
          <strong>AI 调度建议</strong>
          <span class="hp-advice__level">{{ adviceLevelText }}</span>
        </div>
      </div>
      <p class="hp-advice__body">{{ advice?.title || '系统正在分析最优策略...' }}</p>
      <p class="hp-advice__content">{{ advice?.content || '电力时段平稳，运行状态良好。' }}</p>
      <div v-if="advice && advice.level !== 'INFO'" class="hp-advice__actions">
        <button class="hp-btn hp-btn--ok" @click="$emit('decide', 'CONFIRM')">采纳</button>
        <button class="hp-btn" @click="$emit('decide', 'REJECT')">忽略</button>
      </div>
    </section>

    <!-- 底部时钟 -->
    <footer class="hp-footer">
      <span class="hp-clock">{{ clock }}</span>
      <span class="hp-day">{{ dayText }} · {{ dateText }}</span>
    </footer>
  </aside>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import MultiRingGauge from './MultiRingGauge.vue'

const props = defineProps({
  devices: { type: Array, default: () => [] },
  advice: { type: Object, default: null }
})
defineEmits(['decide'])

const totalCount = computed(() => props.devices.length || 1)
const runningCount = computed(() =>
  props.devices.filter(d => !['OFFLINE', 'STOPPED'].includes(d.status)).length
)

const rings = computed(() => {
  const total = totalCount.value
  const cnt = (s) => props.devices.filter(d => d.status === s).length
  return [
    { name: 'RUNNING',    value: cnt('RUNNING'),    max: total, color: '#3bff9f', color2: '#10b981' },
    { name: 'HIGH_LOAD',  value: cnt('HIGH_LOAD'),  max: total, color: '#ffb347', color2: '#f59e0b' },
    { name: 'IDLE',       value: cnt('IDLE'),       max: total, color: '#5cdcff', color2: '#3da9ff' },
    { name: 'FAULT',      value: cnt('FAULT') + cnt('MAINTENANCE'), max: total, color: '#ff5d5d', color2: '#dc2626' },
    { name: 'OFFLINE',    value: cnt('OFFLINE') + cnt('STOPPED'),  max: total, color: '#94a3b8', color2: '#475569' }
  ]
})

const adviceLevelText = computed(() => ({
  CRITICAL: '紧急',
  WARN: '预警',
  GOOD: '机会',
  INFO: '正常'
}[props.advice?.level] || '正常'))

// 时钟
const clock = ref('')
const dateText = ref('')
const dayText = ref('')
function updateClock () {
  const d = new Date()
  clock.value = `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
  dateText.value = `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`
  dayText.value = ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()]
}
let timer = null
onMounted(() => { updateClock(); timer = setInterval(updateClock, 1000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.hud-panel {
  position: relative;
  width: 304px;
  padding: 18px 16px 14px;
  border-radius: 14px;
  pointer-events: auto;
  color: #ffffff;
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
  gap: 14px;
  max-height: calc(100vh - 200px);
  font-family: Bahnschrift, 'Microsoft YaHei', sans-serif;
}

/* 4 角折线框 */
.hp-corner {
  position: absolute;
  width: 16px; height: 16px;
  border-color: #00ffff;
  border-style: solid;
  pointer-events: none;
  filter: drop-shadow(0 0 4px rgba(0, 255, 255, 0.55));
}
.hp-corner--tl { top: -1px; left: -1px; border-width: 2px 0 0 2px; border-radius: 6px 0 0 0; }
.hp-corner--tr { top: -1px; right: -1px; border-width: 2px 2px 0 0; border-radius: 0 6px 0 0; }
.hp-corner--bl { bottom: -1px; left: -1px; border-width: 0 0 2px 2px; border-radius: 0 0 0 6px; }
.hp-corner--br { bottom: -1px; right: -1px; border-width: 0 2px 2px 0; border-radius: 0 0 6px 0; }

/* 顶部装饰条 */
.hud-panel::before {
  content: '';
  position: absolute;
  top: 0; left: 12%;
  width: 76%; height: 1px;
  background: linear-gradient(90deg, transparent, #00ffff, transparent);
  opacity: 0.7;
}

/* 头部 */
.hp-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
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
.hp-live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  background: rgba(255, 126, 0, 0.16);
  border: 1px solid rgba(255, 126, 0, 0.4);
  border-radius: 4px;
  font-size: 9px;
  color: #ff7e00;
  letter-spacing: 2px;
  font-weight: 700;
  animation: live-pulse 1.4s ease-in-out infinite;
}
.hp-live__dot {
  width: 5px; height: 5px;
  background: #ff7e00;
  border-radius: 50%;
  box-shadow: 0 0 6px #ff7e00;
}
@keyframes live-pulse { 0%,100% { opacity: 1 } 50% { opacity: 0.5 } }

/* 多环仪表容器 */
.hp-rings {
  height: 200px;
  background:
    radial-gradient(ellipse at center, rgba(92, 220, 255, 0.08), transparent 70%);
  border: 1px solid rgba(92, 220, 255, 0.1);
  border-radius: 10px;
  padding: 4px;
  position: relative;
}
.hp-rings::after {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 50%;
  border: 1px dashed rgba(92, 220, 255, 0.15);
  pointer-events: none;
}

/* 图例列表 */
.hp-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.hp-legend li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  background: rgba(13, 37, 64, 0.5);
  border: 1px solid rgba(92, 220, 255, 0.08);
  border-radius: 6px;
  font-size: 11px;
}
.hp-legend__dot {
  width: 7px; height: 7px;
  border-radius: 50%;
}
.hp-legend__name {
  flex: 1;
  color: #a8c4e0;
  letter-spacing: 1.5px;
  font-weight: 600;
  font-family: Bahnschrift, monospace;
  font-size: 10px;
}
.hp-legend__val {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 12px;
}
.hp-legend__val em {
  font-style: normal;
  font-size: 9px;
  color: #64748b;
}

/* 调度建议告警 */
.hp-advice {
  position: relative;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(13, 37, 64, 0.55);
  border: 1px solid rgba(92, 220, 255, 0.22);
  overflow: hidden;
}
.hp-advice__halo {
  position: absolute;
  inset: -50%;
  background: radial-gradient(ellipse at center, currentColor, transparent 60%);
  opacity: 0.12;
  pointer-events: none;
  animation: halo-breath 3s ease-in-out infinite;
}
@keyframes halo-breath {
  0%, 100% { opacity: 0.08; transform: scale(0.9); }
  50% { opacity: 0.22; transform: scale(1.05); }
}
.hp-advice--info { color: #5cdcff; }
.hp-advice--good { color: #3bff9f; border-color: rgba(59, 255, 159, 0.35); }
.hp-advice--warn { color: #ffb347; border-color: rgba(255, 179, 71, 0.4); }
.hp-advice--critical {
  color: #ff5d5d;
  border-color: rgba(255, 93, 93, 0.5);
  animation: critical-blink 1.4s ease-in-out infinite;
}
@keyframes critical-blink {
  0%, 100% { box-shadow: 0 0 0 rgba(255, 93, 93, 0); }
  50% { box-shadow: 0 0 22px rgba(255, 93, 93, 0.4); }
}

.hp-advice__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  position: relative;
}
.hp-advice__icon {
  width: 24px; height: 24px;
  display: grid; place-items: center;
  background: currentColor;
  border-radius: 6px;
  color: rgba(8, 16, 30, 0.85);
  box-shadow: 0 0 12px currentColor;
}
.hp-advice__icon svg { color: rgba(8, 16, 30, 0.95); }
.hp-advice__title {
  display: flex;
  flex-direction: column;
  flex: 1;
  line-height: 1.1;
}
.hp-advice__title strong {
  font-size: 12px;
  color: #ffffff;
  letter-spacing: 2px;
  font-weight: 700;
}
.hp-advice__level {
  font-size: 9px;
  margin-top: 2px;
  letter-spacing: 2px;
  color: currentColor;
  font-weight: 700;
}
.hp-advice__body {
  position: relative;
  margin: 0;
  font-size: 12px;
  color: #ffffff;
  font-weight: 700;
  letter-spacing: 1px;
  line-height: 1.5;
}
.hp-advice__content {
  position: relative;
  margin: 4px 0 0;
  font-size: 10.5px;
  color: #a8c4e0;
  line-height: 1.6;
}
.hp-advice__actions {
  position: relative;
  display: flex;
  gap: 6px;
  margin-top: 8px;
}
.hp-btn {
  padding: 4px 12px;
  background: rgba(13, 37, 64, 0.6);
  border: 1px solid currentColor;
  border-radius: 4px;
  color: #a8c4e0;
  font-size: 10px;
  letter-spacing: 1.5px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}
.hp-btn:hover {
  background: currentColor;
  color: rgba(8, 16, 30, 0.95);
}
.hp-btn--ok {
  background: linear-gradient(180deg, rgba(59, 255, 159, 0.32), rgba(16, 185, 129, 0.28));
  color: #ffffff;
  border-color: #3bff9f;
}
.hp-btn--ok:hover {
  background: linear-gradient(180deg, #3bff9f, #10b981);
  color: rgba(8, 16, 30, 0.95);
}

/* 底部时钟 */
.hp-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 6px;
  border-top: 1px solid rgba(92, 220, 255, 0.12);
}
.hp-clock {
  font-size: 18px;
  font-weight: 700;
  color: #5cdcff;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
  text-shadow: 0 0 10px rgba(92, 220, 255, 0.5);
  font-family: Bahnschrift, monospace;
}
.hp-day {
  font-size: 10px;
  color: #94a3b8;
  letter-spacing: 1.5px;
}
</style>
