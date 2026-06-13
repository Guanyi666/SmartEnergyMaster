<template>
  <div class="industrial-scene" :class="{ 'is-compact': compact }">
    <svg viewBox="0 0 1280 720" preserveAspectRatio="xMidYMid meet" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <!-- 六边形地板图案 -->
        <pattern id="hex-floor" x="0" y="0" width="60" height="52" patternUnits="userSpaceOnUse">
          <polygon
            points="30,2 56,16 56,42 30,56 4,42 4,16"
            fill="none"
            stroke="rgba(92,220,255,0.10)"
            stroke-width="0.8"
          />
        </pattern>

        <!-- 青色流光渐变 -->
        <linearGradient id="flow-cyan" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(92,220,255,0)" />
          <stop offset="50%" stop-color="rgba(92,220,255,1)" />
          <stop offset="100%" stop-color="rgba(92,220,255,0)" />
        </linearGradient>

        <!-- 橙色流光渐变 -->
        <linearGradient id="flow-orange" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(255,126,0,0)" />
          <stop offset="50%" stop-color="rgba(255,126,0,1)" />
          <stop offset="100%" stop-color="rgba(255,126,0,0)" />
        </linearGradient>

        <!-- 反应罐径向渐变 -->
        <radialGradient id="reactor-core" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#5cdcff" stop-opacity="0.9" />
          <stop offset="60%" stop-color="#3da9ff" stop-opacity="0.4" />
          <stop offset="100%" stop-color="#0d2540" stop-opacity="0.2" />
        </radialGradient>

        <radialGradient id="reactor-inner" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#ffffff" stop-opacity="1" />
          <stop offset="40%" stop-color="#5cdcff" stop-opacity="0.8" />
          <stop offset="100%" stop-color="#3da9ff" stop-opacity="0.1" />
        </radialGradient>

        <!-- 罐体填充渐变 -->
        <linearGradient id="tank-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="rgba(92,220,255,0.18)" />
          <stop offset="100%" stop-color="rgba(13,37,64,0.6)" />
        </linearGradient>

        <!-- 中心辉光 -->
        <radialGradient id="center-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="rgba(92,220,255,0.4)" />
          <stop offset="100%" stop-color="rgba(92,220,255,0)" />
        </radialGradient>

        <!-- 地面辉光 -->
        <radialGradient id="floor-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="rgba(61,169,255,0.18)" />
          <stop offset="100%" stop-color="rgba(61,169,255,0)" />
        </radialGradient>

        <!-- 滤镜：辉光 -->
        <filter id="cyan-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>

        <filter id="orange-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <!-- 背景层 -->
      <rect width="1280" height="720" fill="rgba(5,11,24,0.4)" />
      <rect width="1280" height="720" fill="url(#hex-floor)" opacity="0.55" />

      <!-- 地面辉光 -->
      <ellipse cx="640" cy="420" rx="520" ry="180" fill="url(#floor-glow)" />
      <ellipse cx="640" cy="380" rx="200" ry="80" fill="url(#center-glow)" />

      <!-- 主体：传送带（顶部和底部） -->
      <g class="conveyor">
        <!-- 上传送带 -->
        <rect x="160" y="170" width="960" height="20" rx="10" fill="rgba(13,37,64,0.8)" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
        <line x1="170" y1="180" x2="1110" y2="180" stroke="url(#flow-cyan)" stroke-width="2" stroke-dasharray="6 12" class="flow-line flow-cyan" />

        <!-- 下传送带 -->
        <rect x="160" y="560" width="960" height="20" rx="10" fill="rgba(13,37,64,0.8)" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
        <line x1="170" y1="570" x2="1110" y2="570" stroke="url(#flow-orange)" stroke-width="2" stroke-dasharray="6 12" class="flow-line flow-orange" />
      </g>

      <!-- 储罐（左上和右上） -->
      <g class="tank tank-tl" transform="translate(220,260)">
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="url(#tank-fill)" stroke="rgba(92,220,255,0.55)" stroke-width="1.5" />
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="url(#reactor-core)" opacity="0.4" />
        <line x1="-40" y1="30" x2="40" y2="30" stroke="rgba(92,220,255,0.45)" />
        <line x1="-40" y1="60" x2="40" y2="60" stroke="rgba(92,220,255,0.45)" />
        <text x="0" y="118" text-anchor="middle" fill="#a8c4e0" font-size="11" letter-spacing="1">储罐 A</text>
        <text x="0" y="134" text-anchor="middle" fill="#5cdcff" font-size="13" font-weight="700">{{ tankA }}</text>
      </g>

      <g class="tank tank-tr" transform="translate(1060,260)">
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="url(#tank-fill)" stroke="rgba(92,220,255,0.55)" stroke-width="1.5" />
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="url(#reactor-core)" opacity="0.4" />
        <line x1="-40" y1="30" x2="40" y2="30" stroke="rgba(92,220,255,0.45)" />
        <line x1="-40" y1="60" x2="40" y2="60" stroke="rgba(92,220,255,0.45)" />
        <text x="0" y="118" text-anchor="middle" fill="#a8c4e0" font-size="11" letter-spacing="1">储罐 B</text>
        <text x="0" y="134" text-anchor="middle" fill="#5cdcff" font-size="13" font-weight="700">{{ tankB }}</text>
      </g>

      <!-- 储罐（左下和右下） -->
      <g class="tank tank-bl" transform="translate(220,440)">
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="url(#tank-fill)" stroke="rgba(255,126,0,0.45)" stroke-width="1.5" />
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="rgba(255,126,0,0.08)" />
        <line x1="-40" y1="30" x2="40" y2="30" stroke="rgba(255,126,0,0.45)" />
        <line x1="-40" y1="60" x2="40" y2="60" stroke="rgba(255,126,0,0.45)" />
        <text x="0" y="118" text-anchor="middle" fill="#a8c4e0" font-size="11" letter-spacing="1">储罐 C</text>
        <text x="0" y="134" text-anchor="middle" fill="#ff7e00" font-size="13" font-weight="700">{{ tankC }}</text>
      </g>

      <g class="tank tank-br" transform="translate(1060,440)">
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="url(#tank-fill)" stroke="rgba(255,126,0,0.45)" stroke-width="1.5" />
        <rect x="-40" y="0" width="80" height="100" rx="8" fill="rgba(255,126,0,0.08)" />
        <line x1="-40" y1="30" x2="40" y2="30" stroke="rgba(255,126,0,0.45)" />
        <line x1="-40" y1="60" x2="40" y2="60" stroke="rgba(255,126,0,0.45)" />
        <text x="0" y="118" text-anchor="middle" fill="#a8c4e0" font-size="11" letter-spacing="1">储罐 D</text>
        <text x="0" y="134" text-anchor="middle" fill="#ff7e00" font-size="13" font-weight="700">{{ tankD }}</text>
      </g>

      <!-- 中央反应器 -->
      <g class="reactor" transform="translate(640,360)">
        <!-- 外环 -->
        <circle r="120" fill="none" stroke="rgba(92,220,255,0.25)" stroke-width="1" stroke-dasharray="3 5" class="rotating-ring" />
        <circle r="100" fill="none" stroke="rgba(92,220,255,0.45)" stroke-width="1.5" />
        <!-- 核心 -->
        <circle r="80" fill="url(#reactor-core)" />
        <circle r="55" fill="url(#reactor-inner)" filter="url(#cyan-glow)" class="core-pulse" />
        <circle r="20" fill="#ffffff" opacity="0.85" />
        <!-- 中心文字 -->
        <text y="-6" text-anchor="middle" fill="#ffffff" font-size="14" font-weight="700" letter-spacing="3">能效中枢</text>
        <text y="14" text-anchor="middle" fill="#5cdcff" font-size="11" letter-spacing="2">EFFICIENCY HUB</text>
        <text y="36" text-anchor="middle" fill="#a8c4e0" font-size="10" letter-spacing="1">{{ efficiencyLabel }}</text>
        <!-- 旋转小环 -->
        <circle r="92" fill="none" stroke="rgba(92,220,255,0.6)" stroke-width="1.2" stroke-dasharray="8 24" class="rotating-ring-reverse" />
      </g>

      <!-- 控制塔（左侧） -->
      <g class="tower" transform="translate(120,360)">
        <polygon points="-30,40 30,40 20,-40 -20,-40" fill="rgba(13,37,64,0.85)" stroke="rgba(92,220,255,0.5)" stroke-width="1.5" />
        <rect x="-12" y="-50" width="24" height="10" fill="rgba(92,220,255,0.7)" />
        <line x1="0" y1="-50" x2="0" y2="-65" stroke="rgba(92,220,255,0.7)" stroke-width="1.5" />
        <circle cx="0" cy="-65" r="3" fill="#5cdcff" class="pulse-dot" />
        <circle cx="0" cy="-65" r="8" fill="none" stroke="rgba(92,220,255,0.5)" class="pulse-ring" />
        <text x="0" y="62" text-anchor="middle" fill="#a8c4e0" font-size="11" letter-spacing="1">控制塔</text>
      </g>

      <!-- 控制塔（右侧） -->
      <g class="tower" transform="translate(1160,360)">
        <polygon points="-30,40 30,40 20,-40 -20,-40" fill="rgba(13,37,64,0.85)" stroke="rgba(255,126,0,0.5)" stroke-width="1.5" />
        <rect x="-12" y="-50" width="24" height="10" fill="rgba(255,126,0,0.7)" />
        <line x1="0" y1="-50" x2="0" y2="-65" stroke="rgba(255,126,0,0.7)" stroke-width="1.5" />
        <circle cx="0" cy="-65" r="3" fill="#ff7e00" class="pulse-dot" />
        <circle cx="0" cy="-65" r="8" fill="none" stroke="rgba(255,126,0,0.5)" class="pulse-ring" />
        <text x="0" y="62" text-anchor="middle" fill="#a8c4e0" font-size="11" letter-spacing="1">数据塔</text>
      </g>

      <!-- 主管线：储罐 → 反应器 -->
      <g class="pipes" stroke-linecap="round" fill="none">
        <path d="M 260 310 Q 400 310 460 360" stroke="url(#flow-cyan)" stroke-width="3" stroke-dasharray="8 16" class="flow-line flow-cyan" filter="url(#cyan-glow)" />
        <path d="M 1020 310 Q 880 310 820 360" stroke="url(#flow-cyan)" stroke-width="3" stroke-dasharray="8 16" class="flow-line flow-cyan" filter="url(#cyan-glow)" />
        <path d="M 260 490 Q 400 490 460 380" stroke="url(#flow-orange)" stroke-width="3" stroke-dasharray="8 16" class="flow-line flow-orange" filter="url(#orange-glow)" />
        <path d="M 1020 490 Q 880 490 820 380" stroke="url(#flow-orange)" stroke-width="3" stroke-dasharray="8 16" class="flow-line flow-orange" filter="url(#orange-glow)" />
      </g>

      <!-- 角落数据标签 -->
      <g class="corner-tag tag-tl" transform="translate(40,40)">
        <rect x="0" y="0" width="170" height="48" rx="6" fill="rgba(13,37,64,0.85)" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
        <circle cx="14" cy="24" r="3" fill="#3bff9f" class="pulse-dot" />
        <text x="26" y="20" fill="#a8c4e0" font-size="10" letter-spacing="1">设备在线</text>
        <text x="26" y="38" fill="#ffffff" font-size="14" font-weight="700">{{ activeDevices }} 台</text>
      </g>

      <g class="corner-tag tag-tr" transform="translate(1070,40)">
        <rect x="0" y="0" width="170" height="48" rx="6" fill="rgba(13,37,64,0.85)" stroke="rgba(255,126,0,0.4)" stroke-width="1" />
        <circle cx="14" cy="24" r="3" fill="#ff5d5d" class="pulse-dot" />
        <text x="26" y="20" fill="#a8c4e0" font-size="10" letter-spacing="1">故障告警</text>
        <text x="26" y="38" fill="#ffffff" font-size="14" font-weight="700">{{ faultCount }} 条</text>
      </g>

      <g class="corner-tag tag-bl" transform="translate(40,632)">
        <rect x="0" y="0" width="200" height="48" rx="6" fill="rgba(13,37,64,0.85)" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
        <text x="14" y="20" fill="#a8c4e0" font-size="10" letter-spacing="1">总有功功率</text>
        <text x="14" y="38" fill="#5cdcff" font-size="14" font-weight="700">{{ totalUsageKwh }} <tspan font-size="10" fill="#a8c4e0">kWh</tspan></text>
      </g>

      <g class="corner-tag tag-br" transform="translate(1040,632)">
        <rect x="0" y="0" width="200" height="48" rx="6" fill="rgba(13,37,64,0.85)" stroke="rgba(59,255,159,0.4)" stroke-width="1" />
        <text x="14" y="20" fill="#a8c4e0" font-size="10" letter-spacing="1">累计碳排放</text>
        <text x="14" y="38" fill="#3bff9f" font-size="14" font-weight="700">{{ totalCo2 }} <tspan font-size="10" fill="#a8c4e0">吨</tspan></text>
      </g>

      <!-- 顶部弧线装饰 -->
      <path d="M 60 70 Q 640 30 1220 70" stroke="rgba(92,220,255,0.25)" stroke-width="1" fill="none" stroke-dasharray="2 6" />
      <!-- 底部弧线装饰 -->
      <path d="M 60 690 Q 640 720 1220 690" stroke="rgba(255,126,0,0.25)" stroke-width="1" fill="none" stroke-dasharray="2 6" />
    </svg>

    <div class="scene-caption">
      <span class="scene-mark">智驭能效 · SMART ENERGY CONTROL CENTER</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  totalUsageKwh: { type: [Number, String], default: 0 },
  totalCo2: { type: [Number, String], default: 0 },
  activeDevices: { type: [Number, String], default: 0 },
  faultCount: { type: [Number, String], default: 0 },
  efficiency: { type: [Number, String], default: 92 },
  compact: { type: Boolean, default: false }
})

const tankA = computed(() => formatNum((Number(props.totalUsageKwh) || 0) * 0.32))
const tankB = computed(() => formatNum((Number(props.totalUsageKwh) || 0) * 0.28))
const tankC = computed(() => formatNum((Number(props.totalUsageKwh) || 0) * 0.22))
const tankD = computed(() => formatNum((Number(props.totalUsageKwh) || 0) * 0.18))

const efficiencyLabel = computed(() => `${props.efficiency}%`)

function formatNum (n) {
  if (n === null || n === undefined || n === '') return '--'
  const v = Number(n)
  if (Number.isNaN(v)) return '--'
  if (v >= 1000) return `${(v / 1000).toFixed(2)}k`
  return v.toFixed(1)
}
</script>

<style scoped>
.industrial-scene {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  border-radius: 14px;
  overflow: hidden;
  background: radial-gradient(ellipse at center, rgba(13, 37, 64, 0.4) 0%, rgba(5, 11, 24, 0.6) 100%);
}

.industrial-scene svg {
  width: 100%;
  height: 100%;
  display: block;
}

.industrial-scene.is-compact {
  min-height: 320px;
}

.scene-caption {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  pointer-events: none;
  z-index: 2;
}

.scene-mark {
  font-size: 11px;
  letter-spacing: 6px;
  color: var(--accent-cyan);
  opacity: 0.6;
  text-shadow: 0 0 10px rgba(92, 220, 255, 0.6);
}

/* 流光动画 */
.flow-line {
  stroke-dasharray: 8 16;
}

@keyframes flow-move {
  to { stroke-dashoffset: -120; }
}

.flow-cyan {
  animation: flow-move 2.5s linear infinite;
}

.flow-orange {
  animation: flow-move 3s linear infinite reverse;
}

/* 旋转环 */
.rotating-ring {
  transform-origin: center;
  animation: rotate 30s linear infinite;
}

.rotating-ring-reverse {
  transform-origin: center;
  animation: rotate 20s linear infinite reverse;
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

/* 核心脉动 */
.core-pulse {
  animation: core-pulse 3s ease-in-out infinite;
  transform-origin: center;
}

@keyframes core-pulse {
  0%, 100% { opacity: 0.85; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.08); }
}

/* 脉冲点 */
.pulse-dot {
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.pulse-ring {
  animation: pulse-ring 2s ease-out infinite;
  transform-origin: center;
}

@keyframes pulse-ring {
  0% { opacity: 0.7; r: 6; }
  100% { opacity: 0; r: 16; }
}

/* 响应式 */
@media (prefers-reduced-motion: reduce) {
  .flow-cyan,
  .flow-orange,
  .rotating-ring,
  .rotating-ring-reverse,
  .core-pulse,
  .pulse-dot,
  .pulse-ring {
    animation: none;
  }
}
</style>