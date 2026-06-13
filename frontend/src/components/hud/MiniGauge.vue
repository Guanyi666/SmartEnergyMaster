<template>
  <div ref="chartRef" class="mini-gauge"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * 精简版仪表盘，用于设备详情浮窗内的温度/压力快速展示。
 */
const props = defineProps({
  value: { type: Number, default: 0 },
  min: { type: Number, default: 0 },
  max: { type: Number, default: 100 },
  title: { type: String, default: '' },
  unit: { type: String, default: '' },
  color: { type: String, default: '#5cdcff' }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  chart.setOption({
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        startAngle: 220,
        endAngle: -40,
        min: props.min,
        max: props.max,
        radius: '92%',
        axisLine: {
          lineStyle: {
            width: 8,
            color: [
              [0.7, props.color + '99'],
              [0.9, '#ffb347'],
              [1, '#ff5d5d']
            ]
          }
        },
        progress: {
          show: true,
          width: 8,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: props.color },
              { offset: 1, color: '#ffb347' }
            ]),
            shadowColor: props.color,
            shadowBlur: 10
          }
        },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: {
          length: 6,
          distance: -12,
          lineStyle: { color: 'rgba(168, 196, 224, 0.4)', width: 1 }
        },
        axisLabel: { show: false },
        anchor: { show: false },
        detail: {
          valueAnimation: true,
          formatter: `{v|{value}}\n{u|${props.unit}}`,
          rich: {
            v: { fontSize: 18, fontWeight: 700, color: '#ffffff', fontFamily: 'Bahnschrift' },
            u: { fontSize: 9, color: '#94a3b8', letterSpacing: 1 }
          },
          offsetCenter: [0, '5%']
        },
        title: {
          offsetCenter: [0, '70%'],
          color: '#5cdcff',
          fontSize: 10,
          fontWeight: 700,
          letterSpacing: 2
        },
        data: [{ value: Math.round(props.value), name: props.title }]
      }
    ]
  })
}

const handleResize = () => chart && chart.resize()
onMounted(() => { render(); window.addEventListener('resize', handleResize) })
watch(() => [props.value, props.title, props.color, props.max], render)
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose() })
</script>

<style scoped>
.mini-gauge {
  width: 100%;
  height: 100%;
  min-height: 120px;
}
</style>
