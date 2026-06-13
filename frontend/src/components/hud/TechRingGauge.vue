<template>
  <div ref="chartRef" class="tech-ring-gauge"></div>
</template>

<script setup>
/**
 * TechRingGauge —— 极小青色环形 (用于设备监测右上角)
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  value: { type: Number, default: 0 },     // 0-100
  size: { type: Number, default: 50 }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const v = Math.max(0, Math.min(100, Number(props.value) || 0))

  chart.setOption({
    backgroundColor: 'transparent',
    animation: true,
    series: [
      {
        type: 'gauge',
        startAngle: 90,
        endAngle: -270,
        radius: '94%',
        min: 0, max: 100,
        progress: {
          show: true,
          width: 4,
          itemStyle: {
            color: '#00FFFF',
            shadowColor: '#00FFFF',
            shadowBlur: 8
          }
        },
        axisLine: {
          lineStyle: { width: 4, color: [[1, 'rgba(0, 255, 255, 0.15)']] }
        },
        pointer: { show: false },
        anchor: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          offsetCenter: [0, '0%'],
          color: '#00FFFF',
          fontSize: 14,
          fontWeight: 700,
          fontFamily: 'DIN, Arial'
        },
        data: [{ value: Math.round(v) }]
      }
    ]
  })
}

const handleResize = () => chart && chart.resize()
onMounted(() => { render(); window.addEventListener('resize', handleResize) })
watch(() => props.value, render)
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose() })
</script>

<style scoped>
.tech-ring-gauge {
  width: v-bind(size + 'px');
  height: v-bind(size + 'px');
}
</style>
