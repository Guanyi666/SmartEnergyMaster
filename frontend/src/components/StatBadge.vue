<template>
  <div class="stat-badge glass-panel" :class="`tone-${tone}`">
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
  tone: { type: String, default: 'blue' }, // blue | green | orange | red | purple
  icon: { type: String, default: 'document' } // document | clock | loading | user | trend
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
  border-radius: 14px;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s ease;
}

.stat-badge::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 14px;
  padding: 1px;
  background: linear-gradient(135deg, rgba(82, 200, 255, 0.4), rgba(82, 200, 255, 0.05) 60%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}

.stat-badge:hover {
  transform: translateY(-2px);
}

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.tone-blue   .stat-icon { background: rgba(82, 200, 255, 0.18); color: #52c8ff; }
.tone-green  .stat-icon { background: rgba(59, 255, 159, 0.18); color: #3bff9f; }
.tone-orange .stat-icon { background: rgba(255, 159, 67, 0.18); color: #ff9f43; }
.tone-red    .stat-icon { background: rgba(255, 93, 93, 0.18);  color: #ff5d5d; }
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
  color: #e0f2fe;
  line-height: 1;
}

.stat-suffix {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 6px;
  letter-spacing: 0.5px;
}

.stat-sub {
  font-size: 11px;
  color: rgba(224, 242, 254, 0.55);
  margin-top: 2px;
}
</style>
