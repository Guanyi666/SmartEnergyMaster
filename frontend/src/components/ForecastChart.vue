<template>
  <div ref="chartRef" class="chart-box" :style="{ height: height + 'px' }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  history: { type: Array, default: () => [] },   // 近期实际读数 [{time, usageKwh}]
  forecast: { type: Array, default: () => [] },   // [{minutesAhead, mean, lower, upper}]
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

  // 实际（实线）：历史段有值，预测段为 null
  const actual = [...recent.map((r) => Number(r.usageKwh)), ...fc.map(() => null)]

  // 预测（虚线）：从最后一个实际点连出去，再接 +15/+30min
  const forecastLine = new Array(n + fc.length).fill(null)
  if (n) forecastLine[n - 1] = lastActual
  fc.forEach((f, i) => { forecastLine[n + i] = f.mean })

  // 置信带：用两条堆叠线（透明下界 + 着色的“上界-下界”）填充 lower..upper
  const ciBase = new Array(n + fc.length).fill(null)
  const ciDelta = new Array(n + fc.length).fill(null)
  if (n) { ciBase[n - 1] = lastActual; ciDelta[n - 1] = 0 }   // 从最后实际点零宽起笔
  fc.forEach((f, i) => { ciBase[n + i] = f.lower; ciDelta[n + i] = f.upper - f.lower })

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 40, right: 16, top: props.compact ? 12 : 36, bottom: 28 },
    legend: {
      show: !props.compact,
      data: ['实际负荷', '预测负荷', '95% 置信区间'],
      textStyle: { color: '#94a3b8' }, top: 4
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderColor: 'rgba(148, 163, 184, 0.25)',
      textStyle: { color: '#e2e8f0' }
    },
    xAxis: {
      type: 'category', boundaryGap: false,
      axisLabel: { color: '#94a3b8' }, data: labels
    },
    yAxis: {
      type: 'value', axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.15)' } }
    },
    series: [
      // 置信带（堆叠技巧）
      { name: '95% 置信区间', type: 'line', stack: 'ci', symbol: 'none',
        lineStyle: { opacity: 0 }, areaStyle: { opacity: 0 }, data: ciBase },
      { name: '95% 置信区间', type: 'line', stack: 'ci', symbol: 'none',
        lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(245, 158, 11, 0.18)' }, data: ciDelta },
      // 实际（实线）
      { name: '实际负荷', type: 'line', smooth: true, symbol: 'none',
        lineStyle: { width: 3, color: '#52c8ff' }, data: actual },
      // 预测（虚线）
      { name: '预测负荷', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        lineStyle: { width: 2.5, color: '#f59e0b', type: 'dashed' },
        itemStyle: { color: '#f59e0b' }, data: forecastLine }
    ]
  })
}

const resize = () => chart?.resize()

onMounted(() => {
  renderChart()
  // 容器尺寸变化时重测画布——修复在隐藏 tab 内以 0 宽度初始化、切换后不重排的问题
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
