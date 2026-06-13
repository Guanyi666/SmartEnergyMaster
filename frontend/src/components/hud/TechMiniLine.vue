<template>
  <div ref="chartRef" class="tech-mini-line"></div>
</template>

<script setup>
/**
 * TechMiniLine —— 极简迷你折线 (设备监测块底部)
 * 无坐标 / 无网格 / 青色渐变填充 / 平滑曲线
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  color: { type: String, default: '#00FFFF' },
  height: { type: Number, default: 60 }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  chart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    grid: { left: 0, right: 0, top: 6, bottom: 4, containLabel: false },
    xAxis: {
      type: 'category',
      show: false,
      boundaryGap: false,
      splitLine: { show: false },          // ★ 去网格
      data: props.data.map((_, i) => i)
    },
    yAxis: {
      type: 'value',
      show: false,
      min: 'dataMin',
      max: 'dataMax',
      splitLine: { show: false }           // ★ 去网格
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        showSymbol: false,
        lineStyle: {
          width: 1.5,
          color: props.color,
          shadowColor: props.color,
          shadowBlur: 6
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: `${props.color}80` },
            { offset: 1, color: `${props.color}05` }
          ])
        },
        data: props.data
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
.tech-mini-line {
  width: 100%;
  height: v-bind(height + 'px');
}
</style>
