<script setup>
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  labels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 280 },
  yUnit: { type: String, default: '' },
  horizontal: { type: Boolean, default: false }
})

const chartRef = useTemplateRef('chart')
let chart
let resizeObserver

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const categoryAxis = {
    type: 'category',
    data: props.labels,
    axisLabel: { color: '#94a3b8' },
    axisLine: { lineStyle: { color: 'rgba(148,163,184,.2)' } }
  }
  const valueAxis = {
    type: 'value',
    axisLabel: { color: '#94a3b8', formatter: `{value}${props.yUnit}` },
    splitLine: { lineStyle: { color: 'rgba(148,163,184,.12)' } }
  }
  chart.setOption({
    backgroundColor: 'transparent',
    color: ['#5cdcff', '#3bff9f', '#ffb347', '#a78bfa', '#ff5d5d'],
    grid: { left: props.horizontal ? 82 : 48, right: 22, top: 40, bottom: 36 },
    legend: { top: 4, textStyle: { color: '#a8c4e0' } },
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(13,37,64,.92)', borderColor: 'rgba(92,220,255,.4)', textStyle: { color: '#ffffff' } },
    xAxis: props.horizontal ? valueAxis : categoryAxis,
    yAxis: props.horizontal ? categoryAxis : valueAxis,
    series: props.series.map((item) => ({
      type: item.type || 'line',
      name: item.name,
      data: item.data,
      smooth: item.type !== 'bar',
      symbol: item.type === 'bar' ? undefined : 'none',
      barMaxWidth: 26,
      areaStyle: item.area ? { opacity: 0.12 } : undefined,
      lineStyle: { width: 2.5 }
    }))
  }, true)
}

onMounted(() => {
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
})
watch(() => [props.labels, props.series], render, { deep: true })
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="chart" class="insight-chart" :style="{ height: `${height}px` }" />
</template>

<style scoped>
.insight-chart {
  width: 100%;
}
</style>
