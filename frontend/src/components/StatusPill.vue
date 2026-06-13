<template>
  <span class="status-tag" :style="{ '--status-color': meta.color }">
    <span class="status-bar"></span>
    <span class="status-dot"></span>
    <span class="status-label">{{ meta.label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { getStatusMeta } from '../utils/status'

const props = defineProps({
  status: { type: String, default: '' }
})

const meta = computed(() => getStatusMeta(props.status))
</script>

<style scoped>
.status-tag {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 8px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--status-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--status-color) 30%, transparent);
  color: var(--status-color);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
}

.status-bar {
  position: absolute;
  left: -1px;
  top: -1px;
  bottom: -1px;
  width: 3px;
  background: var(--status-color);
  box-shadow: 0 0 6px var(--status-color);
  border-radius: 4px 0 0 4px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-color);
  box-shadow: 0 0 8px var(--status-color);
  animation: status-blink 2s ease-in-out infinite;
}

@keyframes status-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
</style>