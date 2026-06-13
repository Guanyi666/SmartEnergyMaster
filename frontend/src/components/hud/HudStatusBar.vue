<template>
  <div class="hud-status-bar">
    <div class="hsb-left">
      <span class="hsb-tag hsb-tag--green">
        <span class="hsb-tag__dot"></span>SYSTEM ONLINE
      </span>
      <span class="hsb-divider"></span>
      <span class="hsb-meta">DATA SYNC · 5s AUTO</span>
      <span class="hsb-divider"></span>
      <span class="hsb-meta">{{ deviceCount }} DEVICES · {{ runningCount }} RUNNING</span>
    </div>

    <div class="hsb-center">
      <span class="hsb-meta">CURRENT PRICE</span>
      <span class="hsb-price" :style="{ color: priceMeta.color, borderColor: priceMeta.color }">
        {{ priceMeta.label }}
      </span>
    </div>

    <div class="hsb-right">
      <span class="hsb-tag hsb-tag--cyan">
        <span class="hsb-tag__dot"></span>WebGL · CSS2D · BLOOM
      </span>
      <span class="hsb-divider"></span>
      <span class="hsb-meta">DEVICE_FOCUS · {{ focusCode || '--' }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  summary: { type: Object, default: () => ({}) },
  devices: { type: Array, default: () => [] },
  focusCode: { type: String, default: '' }
})

const deviceCount = computed(() => props.devices.length)
const runningCount = computed(() => props.devices.filter(d => !['OFFLINE','STOPPED'].includes(d.status)).length)

// 简化的电价时段着色
const PRICE_META = {
  CRITICAL_PEAK: { label: '尖峰电价', color: '#ff5d5d' },
  PEAK:          { label: '峰段电价', color: '#ff7e00' },
  FLAT:          { label: '平段电价', color: '#5cdcff' },
  VALLEY:        { label: '谷段电价', color: '#3bff9f' },
  DEEP_VALLEY:   { label: '深谷电价', color: '#10b981' }
}
const priceMeta = computed(() => PRICE_META[props.summary?.currentPriceTier] || PRICE_META.FLAT)
</script>

<style scoped>
.hud-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 8px 18px;
  pointer-events: auto;
  background: rgba(8, 16, 30, 0.7);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  border: 1px solid rgba(0, 255, 255, 0.22);
  border-radius: 999px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5), 0 0 18px rgba(0, 255, 255, 0.08);
  font-family: Bahnschrift, 'Microsoft YaHei', sans-serif;
  color: #ffffff;
  font-size: 11px;
  letter-spacing: 1.5px;
}

.hsb-left, .hsb-right { display: flex; align-items: center; gap: 10px; }
.hsb-center { display: flex; align-items: center; gap: 10px; }

.hsb-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 10px;
  letter-spacing: 2px;
  font-weight: 700;
}
.hsb-tag__dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
  animation: status-pulse 1.4s ease-in-out infinite;
}
.hsb-tag--green {
  color: #3bff9f;
  background: rgba(59, 255, 159, 0.12);
  border: 1px solid rgba(59, 255, 159, 0.36);
}
.hsb-tag--cyan {
  color: #5cdcff;
  background: rgba(92, 220, 255, 0.12);
  border: 1px solid rgba(92, 220, 255, 0.36);
}
@keyframes status-pulse { 0%,100% { opacity: 1 } 50% { opacity: 0.5 } }

.hsb-divider {
  width: 1px; height: 14px;
  background: rgba(92, 220, 255, 0.25);
}
.hsb-meta {
  font-size: 10px;
  letter-spacing: 1.5px;
  color: #a8c4e0;
  font-family: 'SF Mono', Consolas, monospace;
}

.hsb-price {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(13, 37, 64, 0.6);
  border: 1px solid;
  font-size: 11px;
  letter-spacing: 2px;
  font-weight: 700;
  text-shadow: 0 0 6px currentColor;
}

@media (max-width: 1024px) {
  .hud-status-bar { flex-wrap: wrap; justify-content: center; border-radius: 14px; }
  .hsb-center { order: 3; width: 100%; justify-content: center; }
}
</style>
