<template>
  <div ref="chartRef" class="multi-ring-gauge"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * 多环形仪表 —— 用于"设备状态总览"。
 * 每条环代表一种状态（RUNNING / HIGH_LOAD / IDLE / FAULT 等），
 * 环的填充百分比 = 该状态设备数 / 总设备数。
 * 中央数字 = 运行总数 / 总数。
 */
const props = defineProps({
  rings: {
    type: Array,
    default: () => []
    // [{ name, value, max, color }]
  },
  centerValue: { type: [Number, String], default: 0 },
  centerLabel: { type: String, default: '运行设备' },
  centerSuffix: { type: String, default: '台' }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const RADIUS_STEP = 12 // 每条环之间的间距 (px)
  const RING_WIDTH = 8
  const BASE_OUTER = 92 // 最外环半径（百分比）

  const series = props.rings.map((ring, idx) => {
    const outer = `${BASE_OUTER - idx * (RING_WIDTH + 6)}%`
    const inner = `${BASE_OUTER - idx * (RING_WIDTH + 6) - RING_WIDTH}%`
    const ratio = ring.max ? Math.min(1, ring.value / ring.max) : 0
    return {
      type: 'pie',
      radius: [inner, outer],
      center: ['50%', '50%'],
      silent: true,
      startAngle: 90,
      avoidLabelOverlap: false,
      itemStyle: { borderWidth: 0 },
      label: { show: false },
      labelLine: { show: false },
      data: [
        {
          value: ratio,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
              { offset: 0, color: ring.color },
              { offset: 1, color: ring.color2 || ring.color }
            ]),
            shadowBlur: 10,
            shadowColor: ring.color
          }
        },
        {
          value: 1 - ratio,
          itemStyle: { color: 'rgba(13, 37, 64, 0.55)' }
        }
      ]
    }
  })

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { show: false },
    title: {
      text: `${props.centerValue}`,
      subtext: `${props.centerLabel}`,
      left: 'center',
      top: '38%',
      textStyle: {
        color: '#5cdcff',
        fontSize: 26,
        fontWeight: 700,
        fontFamily: 'Bahnschrift',
        textShadowBlur: 14,
        textShadowColor: 'rgba(92,220,255,0.55)'
      },
      subtextStyle: {
        color: '#a8c4e0',
        fontSize: 10,
        letterSpacing: 2,
        fontWeight: 600
      }
    },
    series
  })
}

const handleResize = () => chart && chart.resize()

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})

watch(() => [props.rings, props.centerValue, props.centerLabel], render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.multi-ring-gauge {
  width: 100%;
  height: 100%;
  min-height: 200px;
}
</style>
