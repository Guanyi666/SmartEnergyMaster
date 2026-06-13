<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    class="tech-icon"
    :class="[`tech-icon--${tone}`]"
  >
    <!-- 通用电路风格图标：六边形外壳 + 内部电路 -->
    <template v-if="name === 'cpu'">
      <path d="M12 2L21 7V17L12 22L3 17V7L12 2Z" stroke="currentColor" stroke-width="1.5" />
      <rect x="8" y="8" width="8" height="8" rx="1" stroke="currentColor" stroke-width="1.5" />
      <line x1="2" y1="9" x2="8" y2="9" stroke="currentColor" stroke-width="1.5" />
      <line x1="2" y1="15" x2="8" y2="15" stroke="currentColor" stroke-width="1.5" />
      <line x1="16" y1="9" x2="22" y2="9" stroke="currentColor" stroke-width="1.5" />
      <line x1="16" y1="15" x2="22" y2="15" stroke="currentColor" stroke-width="1.5" />
      <line x1="9" y1="2" x2="9" y2="8" stroke="currentColor" stroke-width="1.5" />
      <line x1="15" y1="2" x2="15" y2="8" stroke="currentColor" stroke-width="1.5" />
      <line x1="9" y1="16" x2="9" y2="22" stroke="currentColor" stroke-width="1.5" />
      <line x1="15" y1="16" x2="15" y2="22" stroke="currentColor" stroke-width="1.5" />
      <circle cx="12" cy="12" r="2" fill="currentColor" opacity="0.8" />
    </template>

    <!-- 闪电图标：电源 -->
    <template v-else-if="name === 'bolt'">
      <path d="M12 2L21 7V17L12 22L3 17V7L12 2Z" stroke="currentColor" stroke-width="1.5" />
      <path d="M13 7L9 13H12L11 17L15 11H12L13 7Z" fill="currentColor" />
    </template>

    <!-- 太阳图标：能源 -->
    <template v-else-if="name === 'sun'">
      <circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="1.5" />
      <line x1="12" y1="2" x2="12" y2="5" stroke="currentColor" stroke-width="1.5" />
      <line x1="12" y1="19" x2="12" y2="22" stroke="currentColor" stroke-width="1.5" />
      <line x1="2" y1="12" x2="5" y2="12" stroke="currentColor" stroke-width="1.5" />
      <line x1="19" y1="12" x2="22" y2="12" stroke="currentColor" stroke-width="1.5" />
      <line x1="4.9" y1="4.9" x2="7" y2="7" stroke="currentColor" stroke-width="1.5" />
      <line x1="17" y1="17" x2="19.1" y2="19.1" stroke="currentColor" stroke-width="1.5" />
      <line x1="4.9" y1="19.1" x2="7" y2="17" stroke="currentColor" stroke-width="1.5" />
      <line x1="17" y1="7" x2="19.1" y2="4.9" stroke="currentColor" stroke-width="1.5" />
      <circle cx="12" cy="12" r="2" fill="currentColor" />
    </template>

    <!-- 警告图标 -->
    <template v-else-if="name === 'warning'">
      <path d="M12 2L22 20H2L12 2Z" stroke="currentColor" stroke-width="1.5" fill="none" />
      <line x1="12" y1="9" x2="12" y2="14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      <circle cx="12" cy="17" r="0.8" fill="currentColor" />
    </template>

    <!-- 工厂图标 -->
    <template v-else-if="name === 'factory'">
      <path d="M3 21V11L9 14V11L15 14V11L21 14V21H3Z" stroke="currentColor" stroke-width="1.5" />
      <line x1="6" y1="18" x2="6" y2="21" stroke="currentColor" stroke-width="1.5" />
      <line x1="10" y1="18" x2="10" y2="21" stroke="currentColor" stroke-width="1.5" />
      <line x1="14" y1="18" x2="14" y2="21" stroke="currentColor" stroke-width="1.5" />
      <line x1="18" y1="18" x2="18" y2="21" stroke="currentColor" stroke-width="1.5" />
      <line x1="3" y1="6" x2="3" y2="9" stroke="currentColor" stroke-width="1.5" />
      <path d="M3 9L5 7L7 9" stroke="currentColor" stroke-width="1.5" fill="none" />
    </template>

    <!-- 温度计图标 -->
    <template v-else-if="name === 'temp'">
      <path d="M14 4C14 2.9 13.1 2 12 2S10 2.9 10 4V14.5C8.8 15.4 8 16.8 8 18.5C8 21 9.9 23 12.5 23S17 21 17 18.5C17 16.8 16.2 15.4 15 14.5V12" stroke="currentColor" stroke-width="1.5" fill="none" />
      <circle cx="12.5" cy="18.5" r="2.5" fill="currentColor" />
      <line x1="12.5" y1="5" x2="12.5" y2="16" stroke="currentColor" stroke-width="1.5" />
    </template>

    <!-- 压力表图标 -->
    <template v-else-if="name === 'gauge'">
      <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5" />
      <path d="M12 12L17 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" />
      <line x1="12" y1="6" x2="12" y2="7" stroke="currentColor" stroke-width="1.5" />
      <line x1="18" y1="12" x2="17" y2="12" stroke="currentColor" stroke-width="1.5" />
      <line x1="6" y1="12" x2="7" y2="12" stroke="currentColor" stroke-width="1.5" />
      <line x1="16.5" y1="7.5" x2="15.7" y2="8.3" stroke="currentColor" stroke-width="1.5" />
      <line x1="7.5" y1="16.5" x2="8.3" y2="15.7" stroke="currentColor" stroke-width="1.5" />
    </template>

    <!-- 振动波形图标 -->
    <template v-else-if="name === 'wave'">
      <line x1="2" y1="12" x2="5" y2="12" stroke="currentColor" stroke-width="1.5" />
      <path d="M5 12L7 7L9 17L11 8L13 16L15 9L17 14L19 12H22" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linejoin="round" />
    </template>

    <!-- 监控图标（眼睛 + 十字） -->
    <template v-else-if="name === 'monitor'">
      <path d="M2 12C2 12 5 5 12 5S22 12 22 12C22 12 19 19 12 19S2 12 2 12Z" stroke="currentColor" stroke-width="1.5" />
      <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5" />
      <circle cx="12" cy="12" r="1" fill="currentColor" />
    </template>

    <!-- 时钟图标 -->
    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5" />
      <polyline points="12,7 12,12 16,14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" fill="none" />
      <line x1="12" y1="3" x2="12" y2="4" stroke="currentColor" stroke-width="1.5" />
      <line x1="12" y1="20" x2="12" y2="21" stroke="currentColor" stroke-width="1.5" />
      <line x1="3" y1="12" x2="4" y2="12" stroke="currentColor" stroke-width="1.5" />
      <line x1="20" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="1.5" />
    </template>

    <!-- 默认占位 -->
    <template v-else>
      <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5" />
      <circle cx="12" cy="12" r="3" fill="currentColor" />
    </template>
  </svg>
</template>

<script setup>
defineProps({
  name: { type: String, required: true },
  size: { type: [String, Number], default: 22 },
  tone: { type: String, default: 'cyan' } // cyan | orange | green | red | violet | amber
})
</script>

<style scoped>
.tech-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
  filter: drop-shadow(0 0 4px currentColor);
}

.tech-icon--cyan { color: var(--accent-cyan); }
.tech-icon--orange { color: var(--accent-orange); }
.tech-icon--green { color: var(--accent-green); }
.tech-icon--red { color: var(--accent-red); }
.tech-icon--violet { color: var(--accent-violet); }
.tech-icon--amber { color: var(--accent-amber); }
.tech-icon--blue { color: var(--accent-blue); }
</style>