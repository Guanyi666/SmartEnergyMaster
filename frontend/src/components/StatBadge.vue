<template>
  <div class="stat-badge industrial-panel" :class="`tone-${tone}`">
    <div class="stat-bar"></div>
    <div class="stat-icon">
      <el-icon :size="22"><component :is="iconComp" /></el-icon>
    </div>
    <div class="stat-body">
      <div class="stat-value-row">
        <span class="stat-value">{{ displayValue }}</span>
        <span v-if="suffix" class="stat-suffix">{{ suffix }}</span>
      </div>
      <div class="stat-label">{{ label }}</div>
      <div v-if="sub" class="stat-sub">{{ sub }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Document, Clock, Loading, User, TrendCharts } from '@element-plus/icons-vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], required: true },
  suffix: { type: String, default: '' },
  sub: { type: String, default: '' },
  tone: { type: String, default: 'blue' }, // blue | green | orange | red | purple | cyan
  icon: { type: String, default: 'document' }
})

const ICON_MAP = { document: Document, clock: Clock, loading: Loading, user: User, trend: TrendCharts }
const iconComp = computed(() => ICON_MAP[props.icon] || Document)

const displayValue = computed(() => {
  if (typeof props.value === 'number') return props.value.toLocaleString()
  return props.value
})
</script>

<style scoped>
.stat-badge {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.stat-badge:hover {
  transform: translateY(-2px);
}

.stat-bar {
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 0 2px 2px 0;
}

.tone-blue   .stat-bar { background: #5cdcff; box-shadow: 0 0 8px #5cdcff; }
.tone-cyan   .stat-bar { background: #5cdcff; box-shadow: 0 0 8px #5cdcff; }
.tone-green  .stat-bar { background: #3bff9f; box-shadow: 0 0 8px #3bff9f; }
.tone-orange .stat-bar { background: #ff7e00; box-shadow: 0 0 8px #ff7e00; }
.tone-red    .stat-bar { background: #ff5d5d; box-shadow: 0 0 8px #ff5d5d; }
.tone-purple .stat-bar { background: #a78bfa; box-shadow: 0 0 8px #a78bfa; }

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.tone-blue   .stat-icon { background: rgba(92, 220, 255, 0.18); color: #5cdcff; }
.tone-cyan   .stat-icon { background: rgba(92, 220, 255, 0.18); color: #5cdcff; }
.tone-green  .stat-icon { background: rgba(59, 255, 159, 0.18); color: #3bff9f; }
.tone-orange .stat-icon { background: rgba(255, 126, 0, 0.18); color: #ff7e00; }
.tone-red    .stat-icon { background: rgba(255, 93, 93, 0.18); color: #ff5d5d; }
.tone-purple .stat-icon { background: rgba(167, 139, 250, 0.18); color: #a78bfa; }

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.stat-suffix {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 6px;
  letter-spacing: 1px;
}

.stat-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}
</style>