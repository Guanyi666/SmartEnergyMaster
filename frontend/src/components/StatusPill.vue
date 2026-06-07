<template>
  <span class="status-pill" :style="{ '--status-color': meta.color }">
    <i></i>
    {{ meta.label }}
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
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--status-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--status-color) 35%, transparent);
  color: var(--status-color);
  font-size: 12px;
  font-weight: 700;
}

.status-pill i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--status-color);
  box-shadow: 0 0 10px var(--status-color);
}
</style>
