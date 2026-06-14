<template>
  <g class="transformer-3d">
    <ellipse cx="0" cy="155" rx="105" ry="16" fill="url(#device-shadow)" />

    <!-- 底座 -->
    <polygon points="-95,130 0,90 95,130 0,170" fill="#2a3850" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
    <polygon points="-95,130 0,170 95,130 0,90" fill="rgba(13,37,64,0.6)" />

    <!-- 变压器主体（箱体 + 散热片）-->
    <!-- 主箱 -->
    <ellipse cx="0" cy="35" rx="55" ry="14" fill="#3a4250" />
    <rect x="-55" y="35" width="110" height="80" fill="url(#xfmr-body)" />
    <rect x="-55" y="35" width="110" height="80" fill="url(#side-shadow)" opacity="0.45" />
    <rect x="-55" y="35" width="110" height="80" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="0" cy="115" rx="55" ry="14" fill="#1a1e28" />

    <!-- 顶部 -->
    <ellipse cx="0" cy="35" rx="55" ry="14" fill="url(#xfmr-body)" />
    <ellipse cx="0" cy="35" rx="55" ry="14" fill="none" stroke="rgba(255,255,255,0.25)" stroke-width="1" />

    <!-- 散热片（左右两侧）-->
    <g class="fins-left">
      <rect v-for="i in 6" :key="`fl-${i}`" :x="-72 - (i - 1) * 4" y="50" width="3" height="60" fill="url(#xfmr-fin)" :opacity="0.4 + i * 0.08" />
    </g>
    <g class="fins-right">
      <rect v-for="i in 6" :key="`fr-${i}`" :x="55 + (i - 1) * 4" y="50" width="3" height="60" fill="url(#xfmr-fin)" :opacity="0.4 + i * 0.08" />
    </g>

    <!-- 铭牌 -->
    <rect x="-20" y="60" width="40" height="14" fill="#0d2540" stroke="rgba(92,220,255,0.5)" />
    <text x="0" y="70" text-anchor="middle" fill="#5cdcff" font-size="6" letter-spacing="1">TRANSFORMER</text>
    <circle cx="-12" cy="67" r="1.4" fill="#3bff9f" class="status-led" />

    <!-- 高压套管（顶部 3 个）-->
    <g class="bushings">
      <g transform="translate(-30, 35)">
        <rect x="-3" y="-40" width="6" height="40" fill="url(#bushing)" />
        <rect x="-3" y="-40" width="6" height="40" fill="url(#rim-light)" opacity="0.3" />
        <ellipse cx="0" cy="-40" rx="6" ry="3" fill="#c8a060" />
        <circle cx="0" cy="-42" r="3" fill="#fff8c0" filter="url(#soft-glow)" />
      </g>
      <g transform="translate(0, 35)">
        <rect x="-3" y="-48" width="6" height="48" fill="url(#bushing)" />
        <rect x="-3" y="-48" width="6" height="48" fill="url(#rim-light)" opacity="0.3" />
        <ellipse cx="0" cy="-48" rx="6" ry="3" fill="#c8a060" />
        <circle cx="0" cy="-50" r="3" fill="#fff8c0" filter="url(#soft-glow)" />
      </g>
      <g transform="translate(30, 35)">
        <rect x="-3" y="-40" width="6" height="40" fill="url(#bushing)" />
        <rect x="-3" y="-40" width="6" height="40" fill="url(#rim-light)" opacity="0.3" />
        <ellipse cx="0" cy="-40" rx="6" ry="3" fill="#c8a060" />
        <circle cx="0" cy="-42" r="3" fill="#fff8c0" filter="url(#soft-glow)" />
      </g>
    </g>

    <!-- 油枕（侧面圆柱形储油柜）-->
    <ellipse cx="-58" cy="70" rx="6" ry="22" fill="#1a1e28" />
    <rect x="-64" y="48" width="12" height="44" fill="url(#xfmr-body)" />
    <rect x="-64" y="48" width="12" height="44" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="-58" cy="48" rx="6" ry="22" fill="#3a4250" />

    <ellipse cx="58" cy="70" rx="6" ry="22" fill="#1a1e28" />
    <rect x="52" y="48" width="12" height="44" fill="url(#xfmr-body)" />
    <rect x="52" y="48" width="12" height="44" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="58" cy="48" rx="6" ry="22" fill="#3a4250" />

    <!-- 高压进线（左） -->
    <path d="M -30 -42 Q -50 -60 -75 -50" stroke="#1a4a68" stroke-width="3" fill="none" stroke-linecap="round" />
    <path d="M -30 -42 Q -50 -60 -75 -50" stroke="#5cdcff" stroke-width="1.5" fill="none" stroke-linecap="round" />

    <!-- 高压进线（右） -->
    <path d="M 30 -42 Q 50 -60 75 -50" stroke="#1a4a68" stroke-width="3" fill="none" stroke-linecap="round" />
    <path d="M 30 -42 Q 50 -60 75 -50" stroke="#5cdcff" stroke-width="1.5" fill="none" stroke-linecap="round" />

    <!-- 状态灯 -->
    <circle cx="-30" cy="-50" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />
    <circle cx="30" cy="-50" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />

    <!-- 标签 -->
    <g class="device-label" pointer-events="none">
      <rect x="-65" y="-78" width="130" height="30" rx="4" fill="rgba(13,37,64,0.92)" :stroke="statusColor(device)" stroke-width="1" />
      <text x="0" y="-65" text-anchor="middle" fill="#ffffff" font-size="11" font-weight="700" letter-spacing="1">{{ device?.deviceName || '变压器' }}</text>
      <text x="0" y="-53" text-anchor="middle" :fill="statusColor(device)" font-size="9" letter-spacing="2">{{ formatStatus(device?.status) }}</text>
    </g>

    <!-- 数据 -->
    <g class="data-readout" pointer-events="none">
      <text x="-65" y="150" fill="#5cdcff" font-size="9" font-weight="700">LOAD {{ formatNum(device?.usageKwh) }}</text>
      <text x="20" y="150" fill="#ffb347" font-size="9" font-weight="700">T {{ formatNum(device?.temperature) }}°C</text>
    </g>

    <ellipse v-if="hovered" cx="0" cy="80" rx="115" ry="100" fill="url(#hover-glow)" class="hover-halo" pointer-events="none" />
  </g>
</template>

<script setup>
import { statusColor, formatNum, formatStatus } from './_helpers.js'

defineProps({
  device: { type: Object, default: null },
  hovered: { type: Boolean, default: false }
})
</script>