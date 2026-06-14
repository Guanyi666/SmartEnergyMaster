<template>
  <div ref="chartRef" class="chart-box" :style="{ height: height + 'px' }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  history: { type: Array, default: () => [] },
  forecast: { type: Array, default: () => [] },
  historyShown: { type: Number, default: 24 },
  height: { type: Number, default: 400 },
  compact: { type: Boolean, default: false }
})

const chartRef = ref()
let chart
let resizeObserver

const fmtTime = (t) => new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const renderChart = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const recent = props.history.slice(-props.historyShown)
  const n = recent.length
  const fc = props.forecast || []
  const lastActual = n ? Number(recent[n - 1].usageKwh) : null

  const labels = [...recent.map((r) => fmtTime(r.time)), ...fc.map((f) => `${f.minutesAhead}分钟后`)]

  const actual = [...recent.map((r) => Number(r.usageKwh)), ...fc.map(() => null)]

  const forecastLine = new Array(n + fc.length).fill(null)
  if (n) forecastLine[n - 1] = lastActual
  fc.forEach((f, i) => { forecastLine[n + i] = f.mean })

  const ciBase = new Array(n + fc.length).fill(null)
  const ciDelta = new Array(n + fc.length).fill(null)
  if (n) { ciBase[n - 1] = lastActual; ciDelta[n - 1] = 0 }
  fc.forEach((f, i) => { ciBase[n + i] = f.lower; ciDelta[n + i] = f.upper - f.lower })

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 40, right: 16, top: props.compact ? 12 : 36, bottom: 28 },
    legend: {
      show: !props.compact,
      data: ['实际负荷', '预测负荷', '95% 置信区间'],
      textStyle: { color: '#a8c4e0' }, top: 4
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(13, 37, 64, 0.92)',
      borderColor: 'rgba(92, 220, 255, 0.4)',
      borderWidth: 1,
      textStyle: { color: '#fff' },
      axisPointer: {
        lineStyle: { color: 'rgba(92, 220, 255, 0.4)' }
      }
    },
    xAxis: {
      type: 'category', boundaryGap: false,
      axisLabel: { color: '#a8c4e0' },
      axisLine: { lineStyle: { color: 'rgba(92, 220, 255, 0.2)' } },
      axisTick: { show: false },
      data: labels
    },
    yAxis: {
      type: 'value', axisLabel: { color: '#a8c4e0' },
      splitLine: { lineStyle: { color: 'rgba(92, 220, 255, 0.10)', type: 'dashed' } }
    },
    series: [
      { name: '95% 置信区间', type: 'line', stack: 'ci', symbol: 'none',
        lineStyle: { opacity: 0 }, areaStyle: { opacity: 0 }, data: ciBase },
      { name: '95% 置信区间', type: 'line', stack: 'ci', symbol: 'none',
        lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(255, 126, 0, 0.22)' }, data: ciDelta },
      { name: '实际负荷', type: 'line', smooth: true, symbol: 'none',
        lineStyle: {
          width: 3,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#5cdcff' },
            { offset: 1, color: '#3da9ff' }
          ]),
          shadowColor: 'rgba(92, 220, 255, 0.5)',
          shadowBlur: 8
        }, data: actual },
      { name: '预测负荷', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        lineStyle: { width: 2.5, color: '#ff7e00', type: 'dashed' },
        itemStyle: { color: '#ff7e00' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255, 126, 0, 0.18)' },
            { offset: 1, color: 'rgba(255, 126, 0, 0.02)' }
          ])
        },
        data: forecastLine }
    ]
  })
}

const resize = () => chart?.resize()

onMounted(() => {
  renderChart()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
  window.addEventListener('resize', resize)
})
watch(() => [props.history, props.forecast], renderChart, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.chart-box {
  width: 100%;
}
</style>