<template>
  <nav class="hud-bottom-bar">
    <button
      v-for="dev in devices"
      :key="dev.id || dev.deviceCode"
      class="hbn-btn"
      :class="[
        { 'is-active': dev.deviceCode === focusCode },
        `s-${(dev.status || 'offline').toLowerCase()}`
      ]"
      @click="$emit('select', dev)"
    >
      <span class="hbn-btn__dot"></span>
      <span class="hbn-btn__text">{{ dev.deviceName }}</span>
    </button>
    <div v-if="!devices.length" class="hbn-empty">— NO DEVICE —</div>
  </nav>
</template>

<script setup>
defineProps({
  devices: { type: Array, default: () => [] },
  focusCode: { type: String, default: '' }
})
defineEmits(['select'])
</script>

<style scoped>
.hud-bottom-bar {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  pointer-events: auto;
  display: flex;
  gap: 10px;
  padding: 8px 14px;
  background: rgba(4, 15, 30, 0.7);
  border: 1px solid rgba(0, 255, 255, 0.15);
  border-radius: 2px;
  /* 4 角小折角 */
  background-image:
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF),
    linear-gradient(#00FFFF, #00FFFF);
  background-position:
    top left, top left,
    top right, top right,
    bottom left, bottom left,
    bottom right, bottom right;
  background-size:
    8px 2px, 2px 8px,
    8px 2px, 2px 8px,
    8px 2px, 2px 8px,
    8px 2px, 2px 8px;
  background-repeat: no-repeat;
  /* 后置实色背景 */
  background-color: rgba(4, 15, 30, 0.7);
  font-family: 'DIN', Arial, 'Microsoft YaHei', sans-serif;
}

.hbn-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  background: transparent;
  border: 1px solid rgba(0, 255, 255, 0.18);
  color: rgba(217, 232, 245, 0.7);
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 2px;
  border-radius: 2px;
  transition: all 0.2s ease;
}

.hbn-btn__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(217, 232, 245, 0.4);
}
.hbn-btn.s-running .hbn-btn__dot,
.hbn-btn.s-high_load .hbn-btn__dot {
  background: #00ff88;
  box-shadow: 0 0 6px #00ff88;
  animation: bn-blink 1.4s ease-in-out infinite;
}
.hbn-btn.s-idle .hbn-btn__dot {
  background: #00FFFF;
  box-shadow: 0 0 6px #00FFFF;
}
.hbn-btn.s-fault .hbn-btn__dot,
.hbn-btn.s-maintenance .hbn-btn__dot {
  background: #ff4855;
  box-shadow: 0 0 8px #ff4855;
  animation: bn-blink 0.8s ease-in-out infinite;
}
@keyframes bn-blink { 0%,100% { opacity:1 } 50% { opacity:.4 } }

.hbn-btn:hover {
  color: #00FFFF;
  border-color: rgba(0, 255, 255, 0.55);
  background: rgba(0, 255, 255, 0.05);
}

/* 选中态:背景青色 + 底部亮蓝下划线光效 */
.hbn-btn.is-active {
  color: #ffffff;
  background: linear-gradient(180deg, rgba(0, 200, 255, 0.25), rgba(0, 50, 90, 0.1));
  border-color: #00FFFF;
  text-shadow: 0 0 6px rgba(0, 255, 255, 0.55);
  box-shadow: inset 0 0 12px rgba(0, 200, 255, 0.15);
}
.hbn-btn.is-active::after {
  content: '';
  position: absolute;
  left: 8%; right: 8%;
  bottom: -1px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00FFFF 30%, #00FFFF 70%, transparent);
  box-shadow: 0 0 10px #00FFFF;
}

.hbn-empty {
  padding: 12px 24px;
  font-size: 11px;
  letter-spacing: 3px;
  color: rgba(217, 232, 245, 0.4);
}
</style>
