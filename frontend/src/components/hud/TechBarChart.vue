<template>
  <div ref="chartRef" class="tech-bar-chart"></div>
</template>

<script setup>
/**
 * TechBarChart —— 暗青→亮青垂直渐变 + 强制去除所有背景网格
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },   // [{ label, value }]
  unit: { type: String, default: 'kWh' },
  height: { type: Number, default: 150 }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const labels = props.data.map(d => d.label)
  const values = props.data.map(d => Number(d.value) || 0)
  const maxVal = Math.max(...values, 1)

  chart.setOption({
    backgroundColor: 'transparent',
    animation: true,
    animationDuration: 600,
    grid: { left: 32, right: 8, top: 12, bottom: 22, containLabel: false },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(4, 15, 30, 0.92)',
      borderColor: '#00FFFF',
      borderWidth: 1,
      padding: [6, 10],
      textStyle: { color: '#d9e8f5', fontSize: 11, fontFamily: 'DIN, Arial' },
      formatter: (params) => {
        const p = params[0]
        return `<span style="color:rgba(217,232,245,0.6)">${p.axisValue}时</span><br/><strong style="color:#00FFFF">${p.value} ${props.unit}</strong>`
      },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(0,255,255,0.06)' } }
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: 'rgba(0, 255, 255, 0.25)', width: 1 } },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(217, 232, 245, 0.55)',
        fontSize: 9,
        fontFamily: 'DIN, Arial',
        interval: Math.max(0, Math.floor(labels.length / 8) - 1)
      },
      // ★★★ 强制去除背景网格线 ★★★
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      max: maxVal * 1.15,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(217, 232, 245, 0.4)',
        fontSize: 9,
        fontFamily: 'DIN, Arial',
        formatter: (v) => v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v
      },
      // ★★★ 强制去除背景网格线 ★★★
      splitLine: { show: false }
    },
    series: [
      {
        type: 'bar',
        barWidth: '50%',
        // 暗青(底) → 亮青(顶) 垂直渐变
        itemStyle: {
          borderRadius: [2, 2, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 1, 0, 0, [
            { offset: 0,    color: 'rgba(0, 60, 100, 0.15)' }, // 底
            { offset: 0.5,  color: 'rgba(0, 180, 220, 0.55)' },
            { offset: 1,    color: '#00FFFF' }                 // 顶
          ]),
          shadowColor: 'rgba(0, 255, 255, 0.6)',
          shadowBlur: 6
        },
        data: values
      }
    ]
  })
}

const handleResize = () => chart && chart.resize()
onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.tech-bar-chart {
  width: 100%;
  height: v-bind(height + 'px');
}
</style>
