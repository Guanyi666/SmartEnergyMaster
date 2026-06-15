<template>
  <div ref="chartRef" class="mini-sparkline"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * 极简迷你折线/区域图，用于右侧 HUD 设备行的实时趋势缩略。
 */
const props = defineProps({
  data: { type: Array, default: () => [] },
  color: { type: String, default: '#5cdcff' },
  height: { type: Number, default: 32 }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 0, right: 0, top: 2, bottom: 2 },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: props.data.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        showSymbol: false,
        lineStyle: { width: 1.4, color: props.color, shadowColor: props.color, shadowBlur: 6 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: `${props.color}66` },
            { offset: 1, color: `${props.color}05` }
          ])
        },
        data: props.data
      }
    ]
  })
}

const handleResize = () => chart && chart.resize()
onMounted(() => { render(); window.addEventListener('resize', handleResize) })
watch(() => [props.data, props.color], render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose() })
</script>

<style scoped>
.mini-sparkline {
  width: 100%;
  height: v-bind(height + 'px');
}
</style>
