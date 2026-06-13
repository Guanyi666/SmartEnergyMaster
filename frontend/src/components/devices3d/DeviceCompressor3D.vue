<template>
  <g class="compressor-3d">
    <ellipse cx="0" cy="155" rx="105" ry="18" fill="url(#device-shadow)" />

    <polygon points="-95,130 0,92 95,130 0,168" fill="#2a3850" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
    <polygon points="-95,130 0,168 95,130 0,92" fill="rgba(13,37,64,0.6)" />

    <!-- 储气罐（横卧橙色大罐） -->
    <!-- 罐体三段 -->
    <ellipse cx="-40" cy="75" rx="20" ry="55" fill="#5a2000" />
    <rect x="-60" y="20" width="40" height="110" fill="url(#tank-body)" />
    <rect x="-60" y="20" width="40" height="110" fill="url(#side-shadow)" opacity="0.55" />
    <rect x="-60" y="20" width="40" height="110" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="20" cy="75" rx="20" ry="55" fill="#5a2000" />
    <rect x="20" y="20" width="40" height="110" fill="url(#tank-body)" />
    <rect x="20" y="20" width="40" height="110" fill="url(#side-shadow)" opacity="0.55" />
    <rect x="20" y="20" width="40" height="110" fill="url(#rim-light)" opacity="0.2" />
    <!-- 中间圆顶 -->
    <ellipse cx="0" cy="75" rx="20" ry="55" fill="url(#tank-cap)" />
    <ellipse cx="0" cy="75" rx="20" ry="55" fill="url(#rim-light)" opacity="0.35" />
    <ellipse cx="0" cy="75" rx="20" ry="55" fill="none" stroke="rgba(255,255,255,0.25)" stroke-width="1" />

    <!-- 加强环 -->
    <line x1="-60" y1="40" x2="60" y2="40" stroke="rgba(0,0,0,0.45)" stroke-width="2" />
    <line x1="-60" y1="110" x2="60" y2="110" stroke="rgba(0,0,0,0.45)" stroke-width="2" />

    <!-- 顶部安全阀 -->
    <rect x="-6" y="0" width="12" height="20" fill="#5a3a20" />
    <rect x="-6" y="0" width="12" height="20" fill="url(#rim-light)" opacity="0.3" />
    <circle cx="0" cy="-2" r="3" fill="#ff7e00" class="status-led" />

    <!-- 压力表 1 -->
    <g transform="translate(-72, 50)">
      <circle r="9" fill="#0d2540" stroke="rgba(255,179,71,0.6)" stroke-width="1.5" />
      <circle r="6" fill="#fff" />
      <line x1="0" y1="0" x2="3" y2="-4" stroke="#ff5d5d" stroke-width="1.2" />
      <circle r="1.2" fill="#0d2540" />
    </g>
    <!-- 压力表 2 -->
    <g transform="translate(-72, 90)">
      <circle r="9" fill="#0d2540" stroke="rgba(255,179,71,0.6)" stroke-width="1.5" />
      <circle r="6" fill="#fff" />
      <line x1="0" y1="0" x2="-3" y2="-3" stroke="#5cdcff" stroke-width="1.2" />
      <circle r="1.2" fill="#0d2540" />
    </g>

    <!-- 出口管 -->
    <path d="M 60 55 Q 88 55 88 75 Q 88 105 68 105" stroke="#1a4a68" stroke-width="5" fill="none" stroke-linecap="round" />
    <path d="M 60 55 Q 88 55 88 75 Q 88 105 68 105" stroke="#5cdcff" stroke-width="3.5" fill="none" stroke-linecap="round" />
    <path d="M 60 55 Q 88 55 88 75 Q 88 105 68 105" stroke="url(#pipe-cyan)" stroke-width="1.5" fill="none" stroke-dasharray="3 6" class="flow flow-cyan" />

    <!-- 状态灯 -->
    <circle cx="40" cy="20" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />

    <!-- 标签 -->
    <g class="device-label" pointer-events="none">
      <rect x="-68" y="-35" width="136" height="30" rx="4" fill="rgba(13,37,64,0.92)" :stroke="statusColor(device)" stroke-width="1" />
      <text x="0" y="-22" text-anchor="middle" fill="#ffffff" font-size="11" font-weight="700" letter-spacing="1">{{ device?.deviceName || '空压机' }}</text>
      <text x="0" y="-10" text-anchor="middle" :fill="statusColor(device)" font-size="9" letter-spacing="2">{{ formatStatus(device?.status) }}</text>
    </g>

    <!-- 数据 -->
    <g class="data-readout" pointer-events="none">
      <text x="-68" y="148" fill="#ff7e00" font-size="9" font-weight="700">P {{ formatNum(device?.pressure) }}kPa</text>
      <text x="18" y="148" fill="#5cdcff" font-size="9" font-weight="700">VIB {{ formatNum(device?.vibration) }}</text>
    </g>

    <ellipse v-if="hovered" cx="0" cy="70" rx="115" ry="105" fill="url(#hover-glow)" class="hover-halo" pointer-events="none" />
  </g>
</template>

<script setup>
import { statusColor, formatNum, formatStatus } from './_helpers.js'

defineProps({
  device: { type: Object, default: null },
  hovered: { type: Boolean, default: false }
})
</script>