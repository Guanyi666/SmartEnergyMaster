<template>
  <div class="metric-tile" :class="`metric-tile--${tone}`">
    <div class="tile-top">
      <div class="tile-icon" v-if="icon || $slots.icon">
        <slot name="icon">
          <el-icon v-if="icon"><component :is="icon" /></el-icon>
        </slot>
      </div>
      <span class="tile-label">{{ label }}</span>
    </div>
    <div class="tile-value-row">
      <strong class="tile-value" :style="{ color: valueColor }">{{ value }}</strong>
      <span v-if="unit" class="tile-unit">{{ unit }}</span>
    </div>
    <div class="tile-bottom">
      <span v-if="trend" class="tile-trend" :class="trendClass">
        {{ trend }}
      </span>
      <span v-if="note" class="tile-note">{{ note }}</span>
      <slot name="footer" />
    </div>
    <span class="tile-bar" :style="{ background: valueColor }"></span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  unit: { type: String, default: '' },
  icon: { type: [String, Object], default: null },
  color: { type: String, default: '' },
  tone: { type: String, default: 'cyan' }, // cyan | orange | green | red | amber | blue
  trend: { type: String, default: '' },
  note: { type: String, default: '' }
})

const toneColor = {
  cyan: '#5cdcff',
  blue: '#3da9ff',
  orange: '#ff7e00',
  amber: '#ffb347',
  green: '#3bff9f',
  red: '#ff5d5d',
  violet: '#a78bfa'
}

const valueColor = computed(() => props.color || toneColor[props.tone] || '#5cdcff')
const trendClass = computed(() => {
  if (!props.trend) return ''
  if (props.trend.startsWith('+')) return 'is-up'
  if (props.trend.startsWith('-')) return 'is-down'
  return ''
})
</script>

<style scoped>
.metric-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.72), rgba(10, 25, 41, 0.6));
  border: 1px solid rgba(92, 220, 255, 0.16);
  border-radius: 12px;
  overflow: hidden;
  transition: border-color 0.25s ease, transform 0.25s ease;
}

.metric-tile:hover {
  border-color: rgba(92, 220, 255, 0.45);
  transform: translateY(-1px);
}

.tile-bar {
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  box-shadow: 0 0 10px currentColor;
}

.tile-top {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 1px;
}

.tile-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: rgba(92, 220, 255, 0.12);
  color: var(--accent-cyan);
  font-size: 14px;
}

.tile-label {
  text-transform: uppercase;
  letter-spacing: 1px;
}

.tile-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 2px;
}

.tile-value {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
  line-height: 1.1;
  text-shadow: 0 0 18px currentColor;
}

.tile-unit {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 1px;
}

.tile-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--text-muted);
}

.tile-trend.is-up {
  color: var(--accent-green);
}

.tile-trend.is-down {
  color: var(--accent-red);
}

.tile-note {
  color: var(--text-muted);
  text-align: right;
  flex: 1;
}
</style>