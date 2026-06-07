<template>
  <div class="metric-card glass-panel">
    <div class="metric-head">
      <span class="metric-label">{{ label }}</span>
      <span class="metric-dot" :style="{ background: color }"></span>
    </div>
    <div class="metric-value">
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
  color: { type: String, default: '#52c8ff' }
})
</script>

<style scoped>
.metric-card {
  padding: 18px 20px;
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.metric-label,
.metric-note {
  color: var(--text-secondary);
}

.metric-value {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 1px;
}

.metric-value small {
  margin-left: 8px;
  font-size: 15px;
  color: var(--text-secondary);
}

.metric-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  box-shadow: 0 0 12px currentColor;
}

.metric-note {
  margin: 10px 0 0;
  font-size: 13px;
}
</style>
