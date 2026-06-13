<template>
  <g class="pump-3d">
    <ellipse cx="0" cy="150" rx="100" ry="16" fill="url(#device-shadow)" />

    <!-- 底座 -->
    <polygon points="-90,130 0,92 90,130 0,168" fill="#2a3850" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
    <polygon points="-90,130 0,168 90,130 0,92" fill="rgba(13,37,64,0.6)" />

    <!-- 电机（左侧圆柱） -->
    <ellipse cx="-50" cy="35" rx="30" ry="9" fill="#5a8ab8" />
    <rect x="-80" y="35" width="60" height="80" fill="url(#pump-motor)" />
    <rect x="-80" y="35" width="60" height="80" fill="url(#side-shadow)" opacity="0.55" />
    <rect x="-80" y="35" width="60" height="80" fill="url(#rim-light)" opacity="0.15" />
    <ellipse cx="-50" cy="115" rx="30" ry="9" fill="#0a1a2e" />
    <!-- 散热片 -->
    <g>
      <line x1="-74" y1="50" x2="-74" y2="100" stroke="rgba(0,0,0,0.5)" stroke-width="1" />
      <line x1="-64" y1="50" x2="-64" y2="100" stroke="rgba(0,0,0,0.5)" stroke-width="1" />
      <line x1="-54" y1="50" x2="-54" y2="100" stroke="rgba(0,0,0,0.5)" stroke-width="1" />
      <line x1="-44" y1="50" x2="-44" y2="100" stroke="rgba(0,0,0,0.5)" stroke-width="1" />
      <line x1="-34" y1="50" x2="-34" y2="100" stroke="rgba(0,0,0,0.5)" stroke-width="1" />
      <line x1="-26" y1="50" x2="-26" y2="100" stroke="rgba(0,0,0,0.5)" stroke-width="1" />
    </g>
    <!-- 电机铭牌 -->
    <rect x="-58" y="60" width="16" height="9" fill="#0d2540" stroke="rgba(92,220,255,0.5)" />
    <circle cx="-50" cy="65" r="1.6" fill="#3bff9f" class="status-led" />

    <!-- 泵体（中间蜗壳） -->
    <ellipse cx="22" cy="55" rx="38" ry="34" fill="url(#pump-body)" />
    <ellipse cx="22" cy="55" rx="38" ry="34" fill="url(#side-shadow)" opacity="0.4" />
    <ellipse cx="22" cy="55" rx="38" ry="34" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="1" />
    <!-- 泵入口圆盘 -->
    <ellipse cx="22" cy="55" rx="22" ry="22" fill="url(#pump-gauge)" />
    <ellipse cx="22" cy="55" rx="22" ry="22" fill="none" stroke="rgba(92,220,255,0.6)" stroke-width="1.5" />
    <ellipse cx="22" cy="55" rx="14" ry="14" fill="rgba(13,37,64,0.7)" />

    <!-- 旋转叶片 -->
    <g class="fan-blades" :style="{ transformOrigin: '22px 55px' }">
      <ellipse cx="22" cy="42" rx="3" ry="11" fill="rgba(92,220,255,0.55)" />
      <ellipse cx="34" cy="55" rx="11" ry="3" fill="rgba(92,220,255,0.55)" />
      <ellipse cx="22" cy="68" rx="3" ry="11" fill="rgba(92,220,255,0.55)" />
      <ellipse cx="10" cy="55" rx="11" ry="3" fill="rgba(92,220,255,0.55)" />
      <circle cx="22" cy="55" r="3" fill="#fff" />
    </g>

    <!-- 出口管 -->
    <rect x="16" y="85" width="12" height="35" fill="#1a3050" stroke="rgba(92,220,255,0.4)" />
    <rect x="16" y="85" width="12" height="35" fill="url(#pump-motor)" />
    <rect x="16" y="85" width="12" height="35" fill="url(#rim-light)" opacity="0.15" />
    <ellipse cx="22" cy="120" rx="12" ry="3.5" fill="#0a1a2e" />

    <!-- 入口管（左侧弯管） -->
    <path d="M -50 75 Q -85 75 -85 100 Q -85 120 -65 120" stroke="#1a3050" stroke-width="5" fill="none" stroke-linecap="round" />
    <path d="M -50 75 Q -85 75 -85 100 Q -85 120 -65 120" stroke="#3a6a98" stroke-width="3.5" fill="none" stroke-linecap="round" />
    <path d="M -50 75 Q -85 75 -85 100 Q -85 120 -65 120" stroke="url(#pipe-water)" stroke-width="1.5" fill="none" stroke-dasharray="3 6" class="flow flow-water" />

    <!-- 压力表 -->
    <g transform="translate(-78, 25)">
      <circle r="8" fill="#0d2540" stroke="rgba(92,220,255,0.5)" stroke-width="1" />
      <circle r="5" fill="url(#pump-gauge)" />
      <line x1="0" y1="0" x2="3" y2="-3" stroke="#5cdcff" stroke-width="1" />
      <circle r="1" fill="#fff" />
    </g>

    <!-- 顶部状态灯 -->
    <circle cx="-50" cy="20" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />

    <!-- 设备标签 -->
    <g class="device-label" pointer-events="none">
      <rect x="-62" y="-25" width="124" height="30" rx="4" fill="rgba(13,37,64,0.92)" :stroke="statusColor(device)" stroke-width="1" />
      <text x="0" y="-12" text-anchor="middle" fill="#ffffff" font-size="11" font-weight="700" letter-spacing="1">{{ device?.deviceName || '水泵' }}</text>
      <text x="0" y="0" text-anchor="middle" :fill="statusColor(device)" font-size="9" letter-spacing="2">{{ formatStatus(device?.status) }}</text>
    </g>

    <!-- 数据 -->
    <g class="data-readout" pointer-events="none">
      <text x="-58" y="142" fill="#5cdcff" font-size="9" font-weight="700">FLOW {{ formatNum(device?.usageKwh) }}</text>
      <text x="14" y="142" fill="#3b9fff" font-size="9" font-weight="700">P {{ formatNum(device?.pressure) }}</text>
    </g>

    <ellipse v-if="hovered" cx="0" cy="70" rx="110" ry="100" fill="url(#hover-glow)" class="hover-halo" pointer-events="none" />
  </g>
</template>

<script setup>
import { statusColor, formatNum, formatStatus } from './_helpers.js'

defineProps({
  device: { type: Object, default: null },
  hovered: { type: Boolean, default: false }
})
</script>