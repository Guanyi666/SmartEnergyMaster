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
let resizeObserver
let resizeFrame

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
          ? 'rgba(255, 93, 93, 0.16)'
          : prev.xianPriceTier === 'VALLEY' || prev.xianPriceTier === 'DEEP_VALLEY'
            ? 'rgba(59, 255, 159, 0.14)'
            : 'rgba(92, 220, 255, 0.10)'
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
  if (!chartRef.value?.clientWidth || !chartRef.value?.clientHeight) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 48, right: 22, top: 30, bottom: 36 },
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
      type: 'category',
      boundaryGap: false,
      axisLabel: {
        color: '#a8c4e0',
        fontSize: 11
      },
      axisLine: { lineStyle: { color: 'rgba(92, 220, 255, 0.2)' } },
      axisTick: { show: false },
      data: props.records.map((item) => new Date(item.time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#a8c4e0',
        fontSize: 11
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(92, 220, 255, 0.10)',
          type: 'dashed'
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
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#5cdcff' },
            { offset: 1, color: '#3da9ff' }
          ]),
          shadowColor: 'rgba(92, 220, 255, 0.4)',
          shadowBlur: 8
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(92, 220, 255, 0.45)' },
            { offset: 1, color: 'rgba(92, 220, 255, 0.02)' }
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

const resize = () => {
  cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    if (chart) {
      chart.resize()
    } else {
      renderChart()
    }
  })
}

defineExpose({ resize })

onMounted(() => {
  renderChart()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(chartRef.value)
  window.addEventListener('resize', resize)
})

watch(() => props.records, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  cancelAnimationFrame(resizeFrame)
  chart?.dispose()
})
</script>

<style scoped>
.chart-box {
  width: 100%;
  height: 400px;
}
</style>
