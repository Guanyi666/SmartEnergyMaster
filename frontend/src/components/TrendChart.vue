<template>
  <div ref="chartRef" class="chart-box"></div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  records: { type: Array, default: () => [] }
})

const chartRef = ref()
let chart

const markAreas = computed(() => {
  if (!props.records.length) return []
  const areas = []
  let start = 0

  for (let i = 1; i <= props.records.length; i += 1) {
    const prev = props.records[i - 1]
    const current = props.records[i]
    if (!current || current.xianPriceTier !== prev.xianPriceTier) {
      const color =
        prev.xianPriceTier === 'CRITICAL_PEAK' || prev.xianPriceTier === 'PEAK'
          ? 'rgba(255, 93, 93, 0.14)'
          : prev.xianPriceTier === 'VALLEY' || prev.xianPriceTier === 'DEEP_VALLEY'
            ? 'rgba(59, 255, 159, 0.12)'
            : 'rgba(82, 200, 255, 0.08)'
      areas.push([
        { xAxis: start, itemStyle: { color } },
        { xAxis: i - 1 }
      ])
      start = i
    }
  }
  return areas
})

const renderChart = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 40, right: 20, top: 30, bottom: 36 },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisLabel: {
        color: '#94a3b8'
      },
      data: props.records.map((item) => new Date(item.time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#94a3b8'
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.15)'
        }
      }
    },
    series: [
      {
        name: '负荷',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: {
          width: 3,
          color: '#52c8ff'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(82, 200, 255, 0.35)' },
            { offset: 1, color: 'rgba(82, 200, 255, 0.02)' }
          ])
        },
        markArea: {
          silent: true,
          data: markAreas.value
        },
        data: props.records.map((item) => item.usageKwh)
      }
    ]
  })
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(() => props.records, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>

<style scoped>
.chart-box {
  width: 100%;
  height: 400px;
}
</style>
