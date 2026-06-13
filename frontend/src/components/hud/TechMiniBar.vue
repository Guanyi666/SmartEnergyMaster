<template>
  <div ref="chartRef" class="tech-mini-bar"></div>
</template>

<script setup>
/**
 * TechMiniBar —— 嵌入式迷你柱状趋势图
 * 用于设备监测块底部的"过去 24 点功率趋势"
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 56 },
  color: { type: String, default: '#00ffff' }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const data = props.data
  const maxIdx = data.length ? data.indexOf(Math.max(...data)) : -1

  chart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    grid: { left: 0, right: 0, top: 4, bottom: 6 },
    xAxis: {
      type: 'category',
      show: false,
      data: data.map((_, i) => i)
    },
    yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
    series: [
      {
        type: 'bar',
        barWidth: '70%',
        data: data.map((v, i) => ({
          value: v,
          itemStyle: {
            borderRadius: [1, 1, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: i === maxIdx ? '#00ffff' : props.color },
              { offset: 1, color: 'rgba(0, 80, 120, 0.05)' }
            ])
          }
        }))
      }
    ]
  })
}

const handleResize = () => chart && chart.resize()
onMounted(() => { render(); window.addEventListener('resize', handleResize) })
watch(() => [props.data, props.color], render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose() })
</script>

<style scoped>
.tech-mini-bar {
  width: 100%;
  height: v-bind(height + 'px');
}
</style>
