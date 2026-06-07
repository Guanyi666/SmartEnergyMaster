<template>
  <div ref="chartRef" class="chart-box"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  value: { type: Number, default: 0 },
  title: { type: String, default: '' },
  unit: { type: String, default: '' },
  min: { type: Number, default: 0 },
  max: { type: Number, default: 100 },
  color: { type: String, default: '#52c8ff' }
})

const chartRef = ref()
let chart

const renderChart = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  chart.setOption({
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        min: props.min,
        max: props.max,
        axisLine: {
          lineStyle: {
            width: 18,
            color: [
              [0.6, '#164e63'],
              [0.85, '#0f766e'],
              [1, '#7f1d1d']
            ]
          }
        },
        pointer: {
          itemStyle: {
            color: props.color
          }
        },
        progress: {
          show: true,
          width: 18,
          itemStyle: {
            color: props.color
          }
        },
        detail: {
          valueAnimation: true,
          formatter: `{value} ${props.unit}`,
          fontSize: 22,
          color: '#eef4ff',
          offsetCenter: [0, '58%']
        },
        title: {
          offsetCenter: [0, '84%'],
          color: '#94a3b8',
          fontSize: 14
        },
        data: [
          {
            value: props.value,
            name: props.title
          }
        ]
      }
    ]
  })
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(() => [props.value, props.title, props.min, props.max, props.color], renderChart)

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>

<style scoped>
.chart-box {
  width: 100%;
  height: 280px;
}
</style>
