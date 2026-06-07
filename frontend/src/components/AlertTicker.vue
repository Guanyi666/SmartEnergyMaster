<template>
  <div class="ticker-shell glass-panel">
    <div class="ticker-label">异常告警</div>
    <div class="ticker-track">
      <div v-if="duplicatedAlerts.length" class="ticker-content">
        <span v-for="(item, index) in duplicatedAlerts" :key="`${item.id}-${index}`" class="ticker-item">
          [{{ item.deviceName }}] {{ item.title }} / {{ item.assignee }}
        </span>
      </div>
      <div v-else class="ticker-empty">当前无活动告警，系统运行平稳。</div>
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
  padding: 16px 18px;
  overflow: hidden;
}

.ticker-label {
  margin-bottom: 10px;
  color: #ff9f43;
  font-size: 13px;
  letter-spacing: 2px;
}

.ticker-track {
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
  font-size: 14px;
}

.ticker-empty {
  color: var(--text-secondary);
}

@keyframes ticker-scroll {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}
</style>
