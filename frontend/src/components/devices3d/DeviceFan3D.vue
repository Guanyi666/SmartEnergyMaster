<template>
  <g class="fan-3d">
    <ellipse cx="0" cy="160" rx="100" ry="16" fill="url(#device-shadow)" />

    <!-- 底座 -->
    <polygon points="-90,138 0,96 90,138 0,180" fill="#2a3850" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
    <polygon points="-90,138 0,180 90,138 0,96" fill="rgba(13,37,64,0.6)" />

    <!-- 进风筒（左侧圆锥） -->
    <ellipse cx="-50" cy="80" rx="22" ry="40" fill="#0a2a14" />
    <rect x="-72" y="40" width="44" height="80" fill="url(#fan-body)" />
    <rect x="-72" y="40" width="44" height="80" fill="url(#side-shadow)" opacity="0.55" />
    <rect x="-72" y="40" width="44" height="80" fill="url(#rim-light)" opacity="0.18" />
    <ellipse cx="-50" cy="40" rx="22" ry="40" fill="url(#fan-body)" />
    <ellipse cx="-50" cy="40" rx="22" ry="40" fill="url(#rim-light)" opacity="0.35" />
    <ellipse cx="-50" cy="40" rx="22" ry="40" fill="none" stroke="rgba(255,255,255,0.25)" stroke-width="1" />
    <!-- 进气口栅格 -->
    <line x1="-72" y1="60" x2="-28" y2="60" stroke="rgba(0,0,0,0.4)" stroke-width="0.8" />
    <line x1="-72" y1="80" x2="-28" y2="80" stroke="rgba(0,0,0,0.4)" stroke-width="0.8" />
    <line x1="-72" y1="100" x2="-28" y2="100" stroke="rgba(0,0,0,0.4)" stroke-width="0.8" />

    <!-- 电机外壳（中间圆柱） -->
    <ellipse cx="0" cy="80" rx="18" ry="42" fill="#0a2a14" />
    <rect x="-18" y="38" width="36" height="84" fill="url(#fan-housing)" />
    <rect x="-18" y="38" width="36" height="84" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="0" cy="38" rx="18" ry="42" fill="url(#fan-housing)" />
    <ellipse cx="0" cy="38" rx="18" ry="42" fill="none" stroke="rgba(255,255,255,0.2)" stroke-width="1" />

    <!-- 出风筒（右侧） -->
    <ellipse cx="50" cy="80" rx="22" ry="40" fill="#0a2a14" />
    <rect x="28" y="40" width="44" height="80" fill="url(#fan-body)" />
    <rect x="28" y="40" width="44" height="80" fill="url(#side-shadow)" opacity="0.55" />
    <rect x="28" y="40" width="44" height="80" fill="url(#rim-light)" opacity="0.18" />
    <ellipse cx="50" cy="40" rx="22" ry="40" fill="url(#fan-body)" />
    <ellipse cx="50" cy="40" rx="22" ry="40" fill="url(#rim-light)" opacity="0.35" />
    <ellipse cx="50" cy="40" rx="22" ry="40" fill="none" stroke="rgba(255,255,255,0.25)" stroke-width="1" />

    <!-- 中心叶轮（透过出风筒）-->
    <g class="fan-blades" :style="{ transformOrigin: '50px 80px' }">
      <ellipse cx="50" cy="60" rx="3" ry="16" fill="rgba(168,255,168,0.5)" />
      <ellipse cx="50" cy="100" rx="3" ry="16" fill="rgba(168,255,168,0.5)" />
      <ellipse cx="32" cy="80" rx="16" ry="3" fill="rgba(168,255,168,0.5)" />
      <ellipse cx="68" cy="80" rx="16" ry="3" fill="rgba(168,255,168,0.5)" />
      <ellipse cx="38" cy="68" rx="3" ry="11" fill="rgba(168,255,168,0.5)" transform="rotate(45 38 68)" />
      <ellipse cx="62" cy="92" rx="3" ry="11" fill="rgba(168,255,168,0.5)" transform="rotate(45 62 92)" />
      <ellipse cx="62" cy="68" rx="3" ry="11" fill="rgba(168,255,168,0.5)" transform="rotate(-45 62 68)" />
      <ellipse cx="38" cy="92" rx="3" ry="11" fill="rgba(168,255,168,0.5)" transform="rotate(-45 38 92)" />
      <circle cx="50" cy="80" r="4" fill="#fff" />
    </g>

    <!-- 顶部排气管 -->
    <rect x="-5" y="-10" width="10" height="48" fill="#0a2a14" stroke="rgba(92,220,255,0.4)" />
    <rect x="-5" y="-10" width="10" height="48" fill="url(#fan-housing)" />
    <rect x="-5" y="-10" width="10" height="48" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="0" cy="-10" rx="10" ry="3" fill="#0a2a14" />

    <!-- 排气管路（青色） -->
    <path d="M 0 -10 Q 0 -25 30 -25 Q 60 -25 80 -10" stroke="#1a4a68" stroke-width="4" fill="none" stroke-linecap="round" />
    <path d="M 0 -10 Q 0 -25 30 -25 Q 60 -25 80 -10" stroke="#5cdcff" stroke-width="2.5" fill="none" stroke-linecap="round" />
    <path d="M 0 -10 Q 0 -25 30 -25 Q 60 -25 80 -10" stroke="url(#pipe-steam)" stroke-width="1.2" fill="none" stroke-dasharray="3 6" class="flow flow-steam" />

    <!-- 状态灯 -->
    <circle cx="0" cy="20" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />

    <!-- 控制面板 -->
    <rect x="-10" y="110" width="20" height="12" fill="#0d2540" stroke="rgba(92,220,255,0.5)" />
    <circle cx="0" cy="116" r="1.4" fill="#3bff9f" class="status-led" />

    <!-- 标签 -->
    <g class="device-label" pointer-events="none">
      <rect x="-58" y="-50" width="116" height="30" rx="4" fill="rgba(13,37,64,0.92)" :stroke="statusColor(device)" stroke-width="1" />
      <text x="0" y="-37" text-anchor="middle" fill="#ffffff" font-size="11" font-weight="700" letter-spacing="1">{{ device?.deviceName || '风机' }}</text>
      <text x="0" y="-25" text-anchor="middle" :fill="statusColor(device)" font-size="9" letter-spacing="2">{{ formatStatus(device?.status) }}</text>
    </g>

    <!-- 数据 -->
    <g class="data-readout" pointer-events="none">
      <text x="-60" y="150" fill="#5cdcff" font-size="9" font-weight="700">RPM --</text>
      <text x="20" y="150" fill="#3bff9f" font-size="9" font-weight="700">FLOW {{ formatNum(device?.usageKwh) }}</text>
    </g>

    <ellipse v-if="hovered" cx="0" cy="80" rx="110" ry="100" fill="url(#hover-glow)" class="hover-halo" pointer-events="none" />
  </g>
</template>

<script setup>
import { statusColor, formatNum, formatStatus } from './_helpers.js'

defineProps({
  device: { type: Object, default: null },
  hovered: { type: Boolean, default: false }
})
</script>