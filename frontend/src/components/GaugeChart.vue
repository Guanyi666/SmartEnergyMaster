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
  color: { type: String, default: '#5cdcff' }
})

const chartRef = ref()
let chart

const renderChart = () => {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  // 根据 color 推算三段渐变（青→琥珀→红）
  const colorStops = [
    [0.6, props.color],
    [0.85, '#ffb347'],
    [1, '#ff5d5d']
  ]

  chart.setOption({
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        min: props.min,
        max: props.max,
        radius: '92%',
        axisLine: {
          lineStyle: {
            width: 16,
            color: colorStops
          }
        },
        pointer: {
          length: '70%',
          width: 4,
          itemStyle: {
            color: props.color,
            shadowBlur: 8,
            shadowColor: props.color
          }
        },
        axisTick: {
          distance: -22,
          length: 6,
          lineStyle: {
            color: 'rgba(92,220,255,0.35)',
            width: 1
          }
        },
        splitLine: {
          distance: -25,
          length: 12,
          lineStyle: {
            color: 'rgba(168,196,224,0.5)',
            width: 2
          }
        },
        axisLabel: {
          distance: -36,
          color: '#94a3b8',
          fontSize: 10
        },
        progress: {
          show: true,
          width: 16,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: props.color },
              { offset: 1, color: '#ffb347' }
            ])
          }
        },
        anchor: {
          show: true,
          size: 14,
          itemStyle: {
            color: props.color,
            borderColor: '#fff',
            borderWidth: 2
          }
        },
        detail: {
          valueAnimation: true,
          formatter: `{value|${'{value}'} ${props.unit}}`,
          rich: {
            value: {
              fontSize: 22,
              fontWeight: 700,
              color: '#ffffff',
              fontFamily: 'Bahnschrift'
            }
          },
          offsetCenter: [0, '60%']
        },
        title: {
          offsetCenter: [0, '85%'],
          color: '#a8c4e0',
          fontSize: 12,
          fontWeight: 600,
          letterSpacing: 2
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
  height: 240px;
}
</style>