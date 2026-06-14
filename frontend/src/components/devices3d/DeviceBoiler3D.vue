<template>
  <g class="boiler-3d">
    <ellipse cx="0" cy="158" rx="105" ry="16" fill="url(#device-shadow)" />

    <!-- 底座 -->
    <polygon points="-95,135 0,95 95,135 0,175" fill="#2a3850" stroke="rgba(92,220,255,0.4)" stroke-width="1" />
    <polygon points="-95,135 0,175 95,135 0,95" fill="rgba(13,37,64,0.6)" />

    <!-- 锅炉主罐（横卧红色大罐）-->
    <ellipse cx="-50" cy="80" rx="22" ry="50" fill="#3a0a0a" />
    <rect x="-72" y="30" width="44" height="100" fill="url(#boiler-body)" />
    <rect x="-72" y="30" width="44" height="100" fill="url(#side-shadow)" opacity="0.5" />
    <rect x="-72" y="30" width="44" height="100" fill="url(#rim-light)" opacity="0.2" />
    <ellipse cx="50" cy="80" rx="22" ry="50" fill="#3a0a0a" />
    <rect x="28" y="30" width="44" height="100" fill="url(#boiler-body)" />
    <rect x="28" y="30" width="44" height="100" fill="url(#side-shadow)" opacity="0.5" />
    <rect x="28" y="30" width="44" height="100" fill="url(#rim-light)" opacity="0.2" />

    <!-- 中间圆顶封头 -->
    <ellipse cx="0" cy="80" rx="28" ry="50" fill="url(#boiler-cap)" />
    <ellipse cx="0" cy="80" rx="28" ry="50" fill="url(#rim-light)" opacity="0.3" />
    <ellipse cx="0" cy="80" rx="28" ry="50" fill="none" stroke="rgba(255,255,255,0.2)" stroke-width="1" />

    <!-- 加强环 -->
    <line x1="-72" y1="50" x2="72" y2="50" stroke="rgba(0,0,0,0.5)" stroke-width="2" />
    <line x1="-72" y1="110" x2="72" y2="110" stroke="rgba(0,0,0,0.5)" stroke-width="2" />

    <!-- 顶部安全阀（红色高压警示） -->
    <rect x="-6" y="10" width="12" height="20" fill="#3a1010" />
    <rect x="-6" y="10" width="12" height="20" fill="url(#rim-light)" opacity="0.3" />
    <circle cx="0" cy="8" r="3" fill="#ff5d5d" class="status-led" filter="url(#soft-glow)" />

    <!-- 顶部蒸汽管 -->
    <rect x="-3" y="-20" width="6" height="32" fill="#3a1010" stroke="rgba(255,126,0,0.5)" />
    <rect x="-3" y="-20" width="6" height="32" fill="url(#rim-light)" opacity="0.3" />

    <!-- 顶部蒸汽管路（白色蒸汽）-->
    <path d="M 0 -20 Q 0 -38 30 -38 Q 60 -38 80 -20" stroke="#3a3a4a" stroke-width="4" fill="none" stroke-linecap="round" />
    <path d="M 0 -20 Q 0 -38 30 -38 Q 60 -38 80 -20" stroke="#aaaaaa" stroke-width="2.5" fill="none" stroke-linecap="round" />
    <path d="M 0 -20 Q 0 -38 30 -38 Q 60 -38 80 -20" stroke="url(#pipe-steam)" stroke-width="1.2" fill="none" stroke-dasharray="3 6" class="flow flow-steam" />

    <!-- 燃烧器接口（左侧底部，蓝色火焰）-->
    <rect x="-90" y="100" width="20" height="14" fill="#0d2540" stroke="rgba(92,220,255,0.5)" />
    <ellipse cx="-80" cy="93" rx="6" ry="6" fill="url(#furnace-mouth)" />
    <ellipse cx="-80" cy="93" rx="3" ry="3" fill="#fff8c0" class="status-led" />

    <!-- 底部进水口（青色） -->
    <rect x="-3" y="125" width="6" height="20" fill="#1a4a68" />
    <path d="M 0 145 Q 0 165 -30 165 Q -60 165 -60 145" stroke="#1a4a68" stroke-width="4" fill="none" stroke-linecap="round" />
    <path d="M 0 145 Q 0 165 -30 165 Q -60 165 -60 145" stroke="#5cdcff" stroke-width="2.5" fill="none" stroke-linecap="round" />
    <path d="M 0 145 Q 0 165 -30 165 Q -60 165 -60 145" stroke="url(#pipe-water)" stroke-width="1.2" fill="none" stroke-dasharray="3 6" class="flow flow-water" />

    <!-- 压力表 -->
    <g transform="translate(-58, 60)">
      <circle r="9" fill="#0d2540" stroke="rgba(255,93,93,0.7)" stroke-width="1.5" />
      <circle r="6" fill="#fff" />
      <line x1="0" y1="0" x2="4" y2="-3" stroke="#ff5d5d" stroke-width="1.2" />
      <circle r="1.2" fill="#0d2540" />
    </g>
    <g transform="translate(58, 100)">
      <circle r="9" fill="#0d2540" stroke="rgba(92,220,255,0.7)" stroke-width="1.5" />
      <circle r="6" fill="#fff" />
      <line x1="0" y1="0" x2="-3" y2="-4" stroke="#5cdcff" stroke-width="1.2" />
      <circle r="1.2" fill="#0d2540" />
    </g>

    <!-- 状态灯 -->
    <circle cx="-30" cy="20" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />
    <circle cx="30" cy="20" r="3" :fill="statusColor(device)" class="status-light" filter="url(#strong-glow)" />

    <!-- 标签 -->
    <g class="device-label" pointer-events="none">
      <rect x="-60" y="-58" width="120" height="30" rx="4" fill="rgba(13,37,64,0.92)" :stroke="statusColor(device)" stroke-width="1" />
      <text x="0" y="-45" text-anchor="middle" fill="#ffffff" font-size="11" font-weight="700" letter-spacing="1">{{ device?.deviceName || '锅炉' }}</text>
      <text x="0" y="-33" text-anchor="middle" :fill="statusColor(device)" font-size="9" letter-spacing="2">{{ formatStatus(device?.status) }}</text>
    </g>

    <!-- 数据 -->
    <g class="data-readout" pointer-events="none">
      <text x="-65" y="150" fill="#ff5d5d" font-size="9" font-weight="700">T {{ formatNum(device?.temperature) }}°C</text>
      <text x="20" y="150" fill="#ff7e00" font-size="9" font-weight="700">P {{ formatNum(device?.pressure) }}kPa</text>
    </g>

    <ellipse v-if="hovered" cx="0" cy="80" rx="115" ry="105" fill="url(#hover-glow)" class="hover-halo" pointer-events="none" />
  </g>
</template>

<script setup>
import { statusColor, formatNum, formatStatus } from './_helpers.js'

defineProps({
  device: { type: Object, default: null },
  hovered: { type: Boolean, default: false }
})
</script>