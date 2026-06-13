<template>
  <div ref="chartRef" class="chart-box"></div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  records: { type: Array, default: () => [] }
})

const chartRef = ref()
let chart
let resizeObserver
let resizeFrame

const normalizedRecords = computed(() => props.records
  .map((item) => ({
    ...item,
    timestamp: new Date(item.time).getTime(),
    usage: Number(item.usageKwh)
  }))
  .filter((item) => Number.isFinite(item.timestamp) && Number.isFinite(item.usage))
  .sort((a, b) => a.timestamp - b.timestamp))

const timeSpan = computed(() => {
  const records = normalizedRecords.value
  return records.length > 1 ? records[records.length - 1].timestamp - records[0].timestamp : 0
})

const markAreas = computed(() => {
  if (normalizedRecords.value.length < 2) return []
  const areas = []
  let start = 0

  for (let i = 1; i <= normalizedRecords.value.length; i += 1) {
    const prev = normalizedRecords.value[i - 1]
    const current = normalizedRecords.value[i]
    if (!current || current.xianPriceTier !== prev.xianPriceTier) {
      const color =
        prev.xianPriceTier === 'CRITICAL_PEAK' || prev.xianPriceTier === 'PEAK'
          ? 'rgba(255, 93, 93, 0.14)'
          : prev.xianPriceTier === 'VALLEY' || prev.xianPriceTier === 'DEEP_VALLEY'
            ? 'rgba(59, 255, 159, 0.12)'
            : 'rgba(82, 200, 255, 0.08)'
      areas.push([
        { xAxis: normalizedRecords.value[start].timestamp, itemStyle: { color } },
        { xAxis: prev.timestamp }
      ])
      start = i
    }
  }
  return areas
})

const formatAxisTime = (value) => {
  const date = new Date(value)
  if (timeSpan.value <= 10 * 60 * 1000) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
  }
  if (timeSpan.value <= 48 * 60 * 60 * 1000) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const renderChart = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 24, right: 24, top: 30, bottom: 28, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        const point = params[0]
        if (!point) return ''
        const time = new Date(point.value[0]).toLocaleString('zh-CN', { hour12: false })
        return `${time}<br/>负荷：${Number(point.value[1]).toFixed(2)} kWh`
      }
    },
    xAxis: {
      type: 'time',
      boundaryGap: false,
      splitNumber: Math.max(3, Math.min(10, Math.floor((chartRef.value.clientWidth || 600) / 130))),
      axisLabel: {
        color: '#94a3b8',
        hideOverlap: true,
        formatter: formatAxisTime
      },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.32)' } },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.15)' } }
    },
    series: [
      {
        name: '负荷',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 3, color: '#52c8ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(82, 200, 255, 0.35)' },
            { offset: 1, color: 'rgba(82, 200, 255, 0.02)' }
          ])
        },
        markArea: { silent: true, data: markAreas.value },
        data: normalizedRecords.value.map((item) => [item.timestamp, item.usage])
      }
    ]
  }, true)
}

const resize = () => {
  cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    chart?.resize()
    renderChart()
  })
}

onMounted(async () => {
  await nextTick()
  renderChart()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(chartRef.value)
  window.addEventListener('resize', resize)
})

watch(() => props.records, renderChart, { deep: true })

onBeforeUnmount(() => {
  cancelAnimationFrame(resizeFrame)
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<style scoped>
.chart-box {
  width: 100%;
  height: 400px;
}
</style>
