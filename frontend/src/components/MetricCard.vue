<template>
  <div class="metric-card industrial-panel">
    <div class="metric-bar" :style="{ background: color, boxShadow: `0 0 10px ${color}` }"></div>
    <div class="metric-head">
      <span class="metric-label">{{ label }}</span>
      <span class="metric-dot" :style="{ background: color }"></span>
    </div>
    <div class="metric-value" :style="{ color }">
      {{ value }}
      <small>{{ unit }}</small>
    </div>
    <p class="metric-note">{{ note }}</p>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '--' },
  unit: { type: String, default: '' },
  note: { type: String, default: '' },
  color: { type: String, default: '#5cdcff' }
})
</script>

<style scoped>
.metric-card {
  position: relative;
  padding: 18px 20px;
}

.metric-bar {
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 3px;
  border-radius: 0 2px 2px 0;
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.metric-label,
.metric-note {
  color: var(--text-secondary);
}

.metric-label {
  font-size: 12px;
  letter-spacing: 2px;
  font-weight: 600;
}

.metric-value {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 1px;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  text-shadow: 0 0 16px currentColor;
}

.metric-value small {
  margin-left: 8px;
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.metric-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  box-shadow: 0 0 10px currentColor;
  animation: dot-pulse 2s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.metric-note {
  margin: 12px 0 0;
  font-size: 12px;
  letter-spacing: 1px;
}
</style>