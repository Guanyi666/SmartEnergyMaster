<template>
  <div class="device-scene" :class="{ 'is-compact': compact }">
    <svg
      ref="svgRef"
      viewBox="0 0 1280 720"
      preserveAspectRatio="xMidYMid meet"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <!-- ============ 通用 ============ -->
        <radialGradient id="floor-glow" cx="50%" cy="50%" r="60%">
          <stop offset="0%" stop-color="rgba(92,220,255,0.18)" />
          <stop offset="60%" stop-color="rgba(92,220,255,0.04)" />
          <stop offset="100%" stop-color="rgba(92,220,255,0)" />
        </radialGradient>

        <linearGradient id="platform" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="rgba(92,220,255,0.10)" />
          <stop offset="100%" stop-color="rgba(92,220,255,0.02)" />
        </linearGradient>

        <linearGradient id="rim-light" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="rgba(255,255,255,0.45)" />
          <stop offset="100%" stop-color="rgba(255,255,255,0)" />
        </linearGradient>

        <linearGradient id="side-shadow" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(0,0,0,0)" />
          <stop offset="50%" stop-color="rgba(0,0,0,0.25)" />
          <stop offset="100%" stop-color="rgba(0,0,0,0.5)" />
        </linearGradient>

        <!-- ============ 电弧炉 ============ -->
        <linearGradient id="furnace-body" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#dde7f4" />
          <stop offset="35%" stop-color="#9aafca" />
          <stop offset="80%" stop-color="#4a5a72" />
          <stop offset="100%" stop-color="#2a3850" />
        </linearGradient>
        <linearGradient id="furnace-top" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#b6c8de" />
          <stop offset="100%" stop-color="#3a4a62" />
        </linearGradient>
        <radialGradient id="furnace-mouth" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#fff8c0" />
          <stop offset="40%" stop-color="#ff7e00" />
          <stop offset="80%" stop-color="#a02d00" />
          <stop offset="100%" stop-color="#3a1000" />
        </radialGradient>
        <linearGradient id="electrode" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#ffd9a0" />
          <stop offset="50%" stop-color="#c46a18" />
          <stop offset="100%" stop-color="#5a2a00" />
        </linearGradient>

        <!-- ============ 水泵 ============ -->
        <linearGradient id="pump-body" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#9ec4e8" />
          <stop offset="50%" stop-color="#3a6a98" />
          <stop offset="100%" stop-color="#0a1a2e" />
        </linearGradient>
        <linearGradient id="pump-motor" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#5a8ab8" />
          <stop offset="100%" stop-color="#0a1a2e" />
        </linearGradient>
        <radialGradient id="pump-gauge" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#a8e4ff" />
          <stop offset="100%" stop-color="#0d2540" />
        </radialGradient>

        <!-- ============ 空压机（橙色罐） ============ -->
        <linearGradient id="tank-body" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#ffb060" />
          <stop offset="50%" stop-color="#d4621a" />
          <stop offset="100%" stop-color="#5a2000" />
        </linearGradient>
        <linearGradient id="tank-cap" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#ff9a40" />
          <stop offset="100%" stop-color="#a04010" />
        </linearGradient>

        <!-- ============ 风机（绿色涡轮） ============ -->
        <linearGradient id="fan-body" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#a0e8a0" />
          <stop offset="50%" stop-color="#3a9a4a" />
          <stop offset="100%" stop-color="#0a2a14" />
        </linearGradient>
        <linearGradient id="fan-housing" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#5ab86a" />
          <stop offset="100%" stop-color="#1a4a24" />
        </linearGradient>

        <!-- ============ 变压器（灰色散热） ============ -->
        <linearGradient id="xfmr-body" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#c0c8d4" />
          <stop offset="50%" stop-color="#6a7282" />
          <stop offset="100%" stop-color="#1a1e28" />
        </linearGradient>
        <linearGradient id="xfmr-fin" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#8a92a2" />
          <stop offset="100%" stop-color="#2a2e38" />
        </linearGradient>
        <linearGradient id="bushing" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#fff0c0" />
          <stop offset="50%" stop-color="#c8a060" />
          <stop offset="100%" stop-color="#5a3010" />
        </linearGradient>

        <!-- ============ 锅炉（红色压力容器） ============ -->
        <linearGradient id="boiler-body" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#e8a8a8" />
          <stop offset="40%" stop-color="#b04040" />
          <stop offset="100%" stop-color="#3a0a0a" />
        </linearGradient>
        <linearGradient id="boiler-cap" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#d06060" />
          <stop offset="100%" stop-color="#5a1010" />
        </linearGradient>

        <!-- ============ 管道流光 ============ -->
        <linearGradient id="pipe-cyan" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(92,220,255,0)" />
          <stop offset="50%" stop-color="rgba(92,220,255,1)" />
          <stop offset="100%" stop-color="rgba(92,220,255,0)" />
        </linearGradient>
        <linearGradient id="pipe-orange" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(255,126,0,0)" />
          <stop offset="50%" stop-color="rgba(255,126,0,1)" />
          <stop offset="100%" stop-color="rgba(255,126,0,0)" />
        </linearGradient>
        <linearGradient id="pipe-steam" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(200,200,220,0)" />
          <stop offset="50%" stop-color="rgba(220,220,240,0.95)" />
          <stop offset="100%" stop-color="rgba(200,200,220,0)" />
        </linearGradient>
        <linearGradient id="pipe-water" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="rgba(59,159,255,0)" />
          <stop offset="50%" stop-color="rgba(59,159,255,0.95)" />
          <stop offset="100%" stop-color="rgba(59,159,255,0)" />
        </linearGradient>

        <!-- 阴影 -->
        <radialGradient id="device-shadow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="rgba(0,0,0,0.55)" />
          <stop offset="100%" stop-color="rgba(0,0,0,0)" />
        </radialGradient>

        <!-- 悬停光晕 -->
        <radialGradient id="hover-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="rgba(92,220,255,0.4)" />
          <stop offset="100%" stop-color="rgba(92,220,255,0)" />
        </radialGradient>

        <filter id="soft-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>

        <filter id="strong-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>

        <pattern id="iso-grid" x="0" y="0" width="60" height="34" patternUnits="userSpaceOnUse">
          <path d="M 0 17 L 30 0 L 60 17 L 30 34 Z" fill="none" stroke="rgba(92,220,255,0.10)" stroke-width="0.6" />
        </pattern>
      </defs>

      <!-- ============ 背景 ============ -->
      <rect width="1280" height="720" fill="rgba(5,11,24,0.5)" />
      <ellipse cx="640" cy="380" rx="580" ry="240" fill="url(#floor-glow)" />

      <!-- ============ 等距地板 ============ -->
      <g class="floor-layer">
        <polygon points="80,360 640,80 1200,360 640,640" fill="url(#platform)" stroke="rgba(92,220,255,0.2)" stroke-width="1" />
        <polygon points="80,360 640,80 1200,360 640,640" fill="url(#iso-grid)" opacity="0.6" />
        <line x1="80" y1="360" x2="640" y2="80" stroke="rgba(92,220,255,0.45)" stroke-width="1" />
        <line x1="640" y1="80" x2="1200" y2="360" stroke="rgba(92,220,255,0.45)" stroke-width="1" />
        <line x1="640" y1="640" x2="1200" y2="360" stroke="rgba(92,220,255,0.45)" stroke-width="1" />
        <line x1="640" y1="640" x2="80" y2="360" stroke="rgba(92,220,255,0.45)" stroke-width="1" />

        <!-- 6 个设备底座分隔线 -->
        <line x1="360" y1="220" x2="360" y2="500" stroke="rgba(92,220,255,0.12)" stroke-dasharray="2 4" />
        <line x1="920" y1="220" x2="920" y2="500" stroke="rgba(92,220,255,0.12)" stroke-dasharray="2 4" />
        <line x1="200" y1="360" x2="1080" y2="360" stroke="rgba(255,126,0,0.12)" stroke-dasharray="2 4" />
      </g>

      <!-- ============ 主管道（连接上下两排设备）============ -->
      <g class="pipes" pointer-events="none">
        <!-- 上排主管 -->
        <path d="M 220 240 L 1060 240" stroke="#1a4a68" stroke-width="6" fill="none" stroke-linecap="round" />
        <path d="M 220 240 L 1060 240" stroke="url(#pipe-cyan)" stroke-width="2" stroke-dasharray="4 10" class="flow flow-cyan" fill="none" />

        <!-- 下排主管 -->
        <path d="M 220 480 L 1060 480" stroke="#1a4a68" stroke-width="6" fill="none" stroke-linecap="round" />
        <path d="M 220 480 L 1060 480" stroke="url(#pipe-orange)" stroke-width="2" stroke-dasharray="4 10" class="flow flow-orange" fill="none" />

        <!-- 立管 -->
        <line x1="220" y1="240" x2="220" y2="480" stroke="#1a4a68" stroke-width="5" />
        <line x1="640" y1="240" x2="640" y2="480" stroke="#1a4a68" stroke-width="5" />
        <line x1="1060" y1="240" x2="1060" y2="480" stroke="#1a4a68" stroke-width="5" />

        <!-- 阀门节点 -->
        <circle cx="220" cy="240" r="6" fill="#2a6a88" stroke="#5cdcff" />
        <circle cx="640" cy="240" r="6" fill="#2a6a88" stroke="#5cdcff" />
        <circle cx="1060" cy="240" r="6" fill="#2a6a88" stroke="#5cdcff" />
        <circle cx="220" cy="480" r="6" fill="#884a2a" stroke="#ff7e00" />
        <circle cx="640" cy="480" r="6" fill="#884a2a" stroke="#ff7e00" />
        <circle cx="1060" cy="480" r="6" fill="#884a2a" stroke="#ff7e00" />
      </g>

      <!-- ============ 设备 1：电弧炉（上排左） ============ -->
      <g
        class="device device--furnace"
        :class="deviceStateClass(getDeviceByType('ARC_FURNACE'))"
        :transform="`translate(${devicePositions.furnace.x}, ${devicePositions.furnace.y})`"
        @click="onDeviceClick(getDeviceByType('ARC_FURNACE'))"
        @mouseenter="hoveredId = getDeviceByType('ARC_FURNACE')?.id"
        @mouseleave="hoveredId = null"
      >
        <DeviceFurnace3D :device="getDeviceByType('ARC_FURNACE')" :hovered="hoveredId === getDeviceByType('ARC_FURNACE')?.id" />
      </g>

      <!-- ============ 设备 2：水泵（上排中） ============ -->
      <g
        class="device device--pump"
        :class="deviceStateClass(getDeviceByType('PUMP'))"
        :transform="`translate(${devicePositions.pump.x}, ${devicePositions.pump.y})`"
        @click="onDeviceClick(getDeviceByType('PUMP'))"
        @mouseenter="hoveredId = getDeviceByType('PUMP')?.id"
        @mouseleave="hoveredId = null"
      >
        <DevicePump3D :device="getDeviceByType('PUMP')" :hovered="hoveredId === getDeviceByType('PUMP')?.id" />
      </g>

      <!-- ============ 设备 3：空压机（上排右） ============ -->
      <g
        class="device device--compressor"
        :class="deviceStateClass(getDeviceByType('COMPRESSOR'))"
        :transform="`translate(${devicePositions.compressor.x}, ${devicePositions.compressor.y})`"
        @click="onDeviceClick(getDeviceByType('COMPRESSOR'))"
        @mouseenter="hoveredId = getDeviceByType('COMPRESSOR')?.id"
        @mouseleave="hoveredId = null"
      >
        <DeviceCompressor3D :device="getDeviceByType('COMPRESSOR')" :hovered="hoveredId === getDeviceByType('COMPRESSOR')?.id" />
      </g>

      <!-- ============ 设备 4：风机（下排左） ============ -->
      <g
        class="device device--fan"
        :class="deviceStateClass(getDeviceByType('FAN'))"
        :transform="`translate(${devicePositions.fan.x}, ${devicePositions.fan.y})`"
        @click="onDeviceClick(getDeviceByType('FAN'))"
        @mouseenter="hoveredId = getDeviceByType('FAN')?.id"
        @mouseleave="hoveredId = null"
      >
        <DeviceFan3D :device="getDeviceByType('FAN')" :hovered="hoveredId === getDeviceByType('FAN')?.id" />
      </g>

      <!-- ============ 设备 5：变压器（下排中） ============ -->
      <g
        class="device device--transformer"
        :class="deviceStateClass(getDeviceByType('TRANSFORMER'))"
        :transform="`translate(${devicePositions.transformer.x}, ${devicePositions.transformer.y})`"
        @click="onDeviceClick(getDeviceByType('TRANSFORMER'))"
        @mouseenter="hoveredId = getDeviceByType('TRANSFORMER')?.id"
        @mouseleave="hoveredId = null"
      >
        <DeviceTransformer3D :device="getDeviceByType('TRANSFORMER')" :hovered="hoveredId === getDeviceByType('TRANSFORMER')?.id" />
      </g>

      <!-- ============ 设备 6：锅炉（下排右） ============ -->
      <g
        class="device device--boiler"
        :class="deviceStateClass(getDeviceByType('BOILER'))"
        :transform="`translate(${devicePositions.boiler.x}, ${devicePositions.boiler.y})`"
        @click="onDeviceClick(getDeviceByType('BOILER'))"
        @mouseenter="hoveredId = getDeviceByType('BOILER')?.id"
        @mouseleave="hoveredId = null"
      >
        <DeviceBoiler3D :device="getDeviceByType('BOILER')" :hovered="hoveredId === getDeviceByType('BOILER')?.id" />
      </g>

      <!-- ============ 顶部状态条 ============ -->
      <g class="scene-topbar" pointer-events="none">
        <line x1="60" y1="50" x2="1220" y2="50" stroke="rgba(92,220,255,0.18)" stroke-dasharray="2 6" />
        <text x="60" y="40" fill="#5cdcff" font-size="11" letter-spacing="3" font-weight="700">SMART FACTORY · 设备数字孪生 / DIGITAL TWIN</text>
        <text x="1220" y="40" text-anchor="end" fill="#a8c4e0" font-size="10" letter-spacing="2">{{ deviceCount }} 设备在线 · {{ faultCount }} 告警</text>
      </g>

      <!-- ============ 底部状态条 ============ -->
      <g class="scene-bottombar" pointer-events="none">
        <line x1="60" y1="680" x2="1220" y2="680" stroke="rgba(255,126,0,0.18)" stroke-dasharray="2 6" />
        <text x="60" y="700" fill="#a8c4e0" font-size="10" letter-spacing="2">点击设备查看详情 · CLICK DEVICE TO INSPECT</text>
        <text x="640" y="700" text-anchor="middle" fill="#ff7e00" font-size="10" letter-spacing="3" font-weight="700">智驭能效 · SMART ENERGY MASTER</text>
        <text x="1220" y="700" text-anchor="end" fill="#5cdcff" font-size="10" letter-spacing="2">总功率 {{ totalUsageKwh }} kWh · 碳排 {{ totalCo2 }} t</text>
      </g>

      <!-- ============ 角落装饰 ============ -->
      <g class="corner-deco" pointer-events="none">
        <path d="M 30 30 L 60 30 M 30 30 L 30 60" stroke="#5cdcff" stroke-width="1.5" />
        <path d="M 1250 30 L 1220 30 M 1250 30 L 1250 60" stroke="#5cdcff" stroke-width="1.5" />
        <path d="M 30 690 L 60 690 M 30 690 L 30 660" stroke="#ff7e00" stroke-width="1.5" />
        <path d="M 1250 690 L 1220 690 M 1250 690 L 1250 660" stroke="#ff7e00" stroke-width="1.5" />
      </g>
    </svg>

    <div class="scene-caption">
      <span class="scene-mark">智驭能效 · 设备数字孪生 / DIGITAL TWIN</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import DeviceFurnace3D from './devices3d/DeviceFurnace3D.vue'
import DevicePump3D from './devices3d/DevicePump3D.vue'
import DeviceCompressor3D from './devices3d/DeviceCompressor3D.vue'
import DeviceFan3D from './devices3d/DeviceFan3D.vue'
import DeviceTransformer3D from './devices3d/DeviceTransformer3D.vue'
import DeviceBoiler3D from './devices3d/DeviceBoiler3D.vue'

const props = defineProps({
  devices: { type: Array, default: () => [] },
  totalUsageKwh: { type: [Number, String], default: 0 },
  totalCo2: { type: [Number, String], default: 0 },
  faultCount: { type: [Number, String], default: 0 },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['device-click'])

const hoveredId = ref(null)

// 6 个设备的等距网格位置（2 行 × 3 列）
const devicePositions = {
  furnace:     { x: 220,  y: 240 },
  pump:        { x: 640,  y: 240 },
  compressor:  { x: 1060, y: 240 },
  fan:         { x: 220,  y: 480 },
  transformer: { x: 640,  y: 480 },
  boiler:      { x: 1060, y: 480 }
}

// 6 个类型
const SUPPORTED_TYPES = ['ARC_FURNACE', 'PUMP', 'COMPRESSOR', 'FAN', 'TRANSFORMER', 'BOILER']

function getDeviceByType (type) {
  return props.devices.find((d) => d.deviceType === type) || null
}

function deviceStateClass (device) {
  if (!device) return 'is-empty'
  const s = (device.status || '').toLowerCase()
  return `is-${s}`
}

function statusColor (device) {
  if (!device) return '#64748b'
  const s = (device.status || '').toUpperCase()
  return {
    RUNNING:    '#3bff9f',
    HIGH_LOAD:  '#ffb347',
    IDLE:       '#5cdcff',
    STOPPED:    '#94a3b8',
    OFFLINE:    '#64748b',
    FAULT:      '#ff5d5d',
    MAINTENANCE:'#ff7e00'
  }[s] || '#5cdcff'
}

function onDeviceClick (device) {
  if (!device) return
  emit('device-click', device)
}

const deviceCount = computed(() => props.devices.filter((d) => d.status !== 'OFFLINE').length)

// 暴露给子组件使用的工具
function formatNum (n) {
  if (n == null || n === '') return '--'
  const v = Number(n)
  if (Number.isNaN(v)) return '--'
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(1) + 'k'
  return v.toFixed(1)
}

function formatStatus (s) {
  return {
    RUNNING: '运行中',
    HIGH_LOAD: '高负荷',
    IDLE: '空转',
    STOPPED: '停机',
    OFFLINE: '离线',
    FAULT: '故障',
    MAINTENANCE: '维修中'
  }[s] || s || '未知'
}

defineExpose({ statusColor, formatNum, formatStatus, SUPPORTED_TYPES })
</script>

<style scoped>
.device-scene {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 520px;
  border-radius: 14px;
  overflow: hidden;
  background: radial-gradient(ellipse at center, rgba(13, 37, 64, 0.5) 0%, rgba(5, 11, 24, 0.85) 100%);
}

.device-scene svg {
  width: 100%;
  height: 100%;
  display: block;
}

.device-scene.is-compact {
  min-height: 360px;
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

/* 设备可交互 */
.device {
  cursor: pointer;
  transition: filter 0.3s ease;
}

.device:hover {
  filter: brightness(1.18) drop-shadow(0 0 12px rgba(92, 220, 255, 0.5));
}

.device.is-fault {
  animation: fault-shake 0.6s ease-in-out infinite;
}

.device.is-offline,
.device.is-empty {
  opacity: 0.45;
  filter: grayscale(0.6);
}

@keyframes fault-shake {
  0%, 100% { transform: translate(0, 0); }
  25% { transform: translate(-1px, 0); }
  75% { transform: translate(1px, 0); }
}

/* 管道流光 */
.flow {
  stroke-dasharray: 6 14;
}

@keyframes flow-move {
  to { stroke-dashoffset: -120; }
}

.flow-cyan {
  animation: flow-move 2.5s linear infinite;
}

.flow-orange {
  animation: flow-move 3s linear infinite;
}

.flow-water {
  animation: flow-move 2s linear infinite reverse;
}

.flow-steam {
  animation: flow-move 4s linear infinite;
}

/* 响应式 */
@media (max-width: 1280px) {
  .device-scene { min-height: 420px; }
}

@media (max-width: 1024px) {
  .device-scene { min-height: 360px; }
}

@media (prefers-reduced-motion: reduce) {
  .flow-cyan, .flow-orange, .flow-water, .flow-steam {
    animation: none;
  }
  .device.is-fault { animation: none; }
}
</style>