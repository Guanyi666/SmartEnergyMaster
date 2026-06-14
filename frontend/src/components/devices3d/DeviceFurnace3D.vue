<template>
  <g class="furnace-3d">
    <!-- 设备阴影 -->
    <ellipse cx="0" cy="170" rx="105" ry="18" fill="url(#device-shadow)" />

    <!-- 底座平台 -->
    <polygon points="-90,140 0,98 90,140 0,182" fill="#2a3850" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
    <polygon points="-90,140 0,182 90,140 0,98" fill="rgba(13,37,64,0.6)" />
    <polygon points="-90,140 90,140 90,148 -90,148" fill="rgba(92,220,255,0.2)" />

    <!-- 炉体主罐 -->
    <ellipse cx="0" cy="130" rx="62" ry="20" fill="#1a2a40" />
    <rect x="-62" y="20" width="124" height="110" fill="url(#furnace-body)" />
    <rect x="-62" y="20" width="124" height="110" fill="url(#side-shadow)" opacity="0.55" />
    <rect x="-62" y="20" width="124" height="110" fill="url(#rim-light)" opacity="0.18" />
    <ellipse cx="0" cy="20" rx="62" ry="20" fill="url(#furnace-top)" />
    <ellipse cx="0" cy="20" rx="62" ry="20" fill="none" stroke="rgba(255,255,255,0.3)" stroke-width="1" />

    <!-- 顶盖橙色 -->
    <ellipse cx="0" cy="20" rx="48" ry="14" fill="url(#furnace-mouth)" />
    <ellipse cx="0" cy="18" rx="42" ry="12" fill="rgba(255,180,80,0.3)" />
    <ellipse cx="0" cy="18" rx="42" ry="12" fill="none" stroke="rgba(255,200,100,0.4)" />

    <!-- 三根电极 -->
    <g class="electrodes">
      <g transform="translate(-26, 18)">
        <rect x="-2.5" y="-50" width="5" height="50" fill="url(#electrode)" />
        <ellipse cx="0" cy="-50" rx="3" ry="3" fill="#fff8c0" filter="url(#soft-glow)" />
      </g>
      <g transform="translate(0, 18)">
        <rect x="-2.5" y="-58" width="5" height="58" fill="url(#electrode)" />
        <ellipse cx="0" cy="-58" rx="3" ry="3" fill="#fff8c0" filter="url(#soft-glow)" />
      </g>
      <g transform="translate(26, 18)">
        <rect x="-2.5" y="-50" width="5" height="50" fill="url(#electrode)" />
        <ellipse cx="0" cy="-50" rx="3" ry="3" fill="#fff8c0" filter="url(#soft-glow)" />
      </g>
    </g>

    <!-- 加强环 -->
    <line x1="-62" y1="55" x2="62" y2="55" stroke="rgba(0,0,0,0.4)" stroke-width="2" />
    <line x1="-62" y1="95" x2="62" y2="95" stroke="rgba(0,0,0,0.4)" stroke-width="2" />
    <line x1="-62" y1="20" x2="62" y2="20" stroke="rgba(255,255,255,0.18)" stroke-width="1" />

    <!-- 控制面板 -->
    <rect x="-32" y="72" width="22" height="14" fill="#0d2540" stroke="rgba(92,220,255,0.5)" />
    <circle cx="-21" cy="79" r="1.6" fill="#3bff9f" class="status-led" />

    <!-- 进料管（橙色） -->
    <path d="M -82 70 Q -100 70 -100 95 Q -100 115 -82 115" stroke="#a85a00" stroke-width="6" fill="none" stroke-linecap="round" />
    <path d="M -82 70 Q -100 70 -100 95 Q -100 115 -82 115" stroke="#ff7e00" stroke-width="4" fill="none" stroke-linecap="round" />
    <path d="M -82 70 Q -100 70 -100 95 Q -100 115 -82 115" stroke="url(#pipe-orange)" stroke-width="1.5" fill="none" stroke-dasharray="3 6" class="flow flow-orange" />

    <!-- 出料管（青色） -->
    <path d="M 82 95 Q 110 95 110 120 Q 110 145 82 145" stroke="#1a4a68" stroke-width="6" fill="none" stroke-linecap="round" />
    <path d="M 82 95 Q 110 95 110 120 Q 110 145 82 145" stroke="#5cdcff" stroke-width="4" fill="none" stroke-linecap="round" />
    <path d="M 82 95 Q 110 95 110 120 Q 110 145 82 145" stroke="url(#pipe-cyan)" stroke-width="1.5" fill="none" stroke-dasharray="3 6" class="flow flow-cyan" />

    <!-- 顶部状态灯 -->
    <circle cx="0" cy="-45" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />

    <!-- 设备标签 -->
    <g class="device-label" pointer-events="none">
      <rect x="-68" y="-95" width="136" height="32" rx="4" fill="rgba(13,37,64,0.92)" :stroke="statusColor(device)" stroke-width="1" />
      <text x="0" y="-82" text-anchor="middle" fill="#ffffff" font-size="11" font-weight="700" letter-spacing="1">{{ device?.deviceName || '电弧炉' }}</text>
      <text x="0" y="-69" text-anchor="middle" :fill="statusColor(device)" font-size="9" letter-spacing="2">{{ formatStatus(device?.status) }}</text>
    </g>

    <!-- 实时数据 -->
    <g class="data-readout" pointer-events="none">
      <text x="-60" y="160" fill="#5cdcff" font-size="9" font-weight="700">T {{ formatNum(device?.temperature) }}°C</text>
      <text x="20" y="160" fill="#ff7e00" font-size="9" font-weight="700">P {{ formatNum(device?.pressure) }}kPa</text>
    </g>

    <!-- 悬停光晕 -->
    <ellipse v-if="hovered" cx="0" cy="70" rx="120" ry="110" fill="url(#hover-glow)" class="hover-halo" pointer-events="none" />
  </g>
</template>

<script setup>
import { statusColor, formatNum, formatStatus } from './_helpers.js'

defineProps({
  device: { type: Object, default: null },
  hovered: { type: Boolean, default: false }
})
</script>