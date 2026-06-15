<template>
  <div ref="chartRef" class="mini-bar"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * 迷你柱状图，用于将"今日概况"的纯文本数据可视化。
 * data: [{ name, value, color }]
 */
const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 110 }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const names = props.data.map(d => d.name)
  const values = props.data.map(d => Number(d.value) || 0)
  const colors = props.data.map(d => d.color || '#5cdcff')
  const maxVal = Math.max(...values, 1)

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(13, 37, 64, 0.92)',
      borderColor: 'rgba(92, 220, 255, 0.4)',
      borderWidth: 1,
      textStyle: { color: '#fff', fontSize: 11 },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(92, 220, 255, 0.06)' } }
    },
    grid: { left: 6, right: 6, top: 10, bottom: 22 },
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: 'rgba(92, 220, 255, 0.18)' } },
      axisTick: { show: false },
      axisLabel: { color: '#a8c4e0', fontSize: 9, letterSpacing: 1 }
    },
    yAxis: { type: 'value', show: false, max: maxVal * 1.15 },
    series: [
      {
        type: 'bar',
        data: values.map((v, i) => ({
          value: v,
          itemStyle: {
            borderRadius: [3, 3, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: colors[i] },
              { offset: 1, color: `${colors[i]}30` }
            ]),
            shadowColor: colors[i],
            shadowBlur: 8
          }
        })),
        barWidth: '46%',
        label: {
          show: true,
          position: 'top',
          color: '#ffffff',
          fontSize: 10,
          fontWeight: 700,
          textShadowBlur: 4,
          textShadowColor: 'rgba(0,0,0,0.6)'
        }
      }
    ]
  })
}

const handleResize = () => chart && chart.resize()
onMounted(() => { render(); window.addEventListener('resize', handleResize) })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose() })
</script>

<style scoped>
.mini-bar {
  width: 100%;
  height: v-bind(height + 'px');
}
</style>
