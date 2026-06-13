<template>
  <div class="ticker-shell industrial-panel">
    <div class="ticker-bar"></div>
    <div class="ticker-label">
      <span class="ticker-dot"></span>
      异常告警 / ALERT
    </div>
    <div class="ticker-track">
      <div v-if="duplicatedAlerts.length" class="ticker-content">
        <span v-for="(item, index) in duplicatedAlerts" :key="`${item.id}-${index}`" class="ticker-item">
          <strong>[{{ item.deviceName }}]</strong>
          {{ item.title }} / {{ item.assignee }}
        </span>
      </div>
      <div v-else class="ticker-empty">当前无活动告警，系统运行平稳 / NO ACTIVE ALERTS</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  alerts: { type: Array, default: () => [] }
})

const duplicatedAlerts = computed(() => [...props.alerts, ...props.alerts])
</script>

<style scoped>
.ticker-shell {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 18px 12px 16px;
  overflow: hidden;
  position: relative;
}

.ticker-bar {
  width: 4px;
  height: 28px;
  background: linear-gradient(180deg, var(--accent-amber), var(--accent-orange));
  border-radius: 2px;
  box-shadow: 0 0 10px var(--accent-orange);
  flex-shrink: 0;
}

.ticker-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--accent-amber);
  font-size: 12px;
  letter-spacing: 2px;
  font-weight: 700;
  flex-shrink: 0;
  white-space: nowrap;
}

.ticker-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-amber);
  box-shadow: 0 0 8px var(--accent-amber);
  animation: ticker-blink 1.2s ease-in-out infinite;
}

@keyframes ticker-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.ticker-track {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
}

.ticker-content {
  display: inline-flex;
  gap: 36px;
  min-width: max-content;
  animation: ticker-scroll 22s linear infinite;
}

.ticker-item {
  color: #ffd7a8;
  font-size: 13px;
  letter-spacing: 1px;
}

.ticker-item strong {
  color: var(--accent-amber);
  font-weight: 700;
}

.ticker-empty {
  color: var(--text-secondary);
  font-size: 13px;
  letter-spacing: 1px;
}

@keyframes ticker-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
</style>