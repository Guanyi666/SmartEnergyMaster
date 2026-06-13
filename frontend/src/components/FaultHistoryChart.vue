<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] },
  sourceTime: { type: String, default: '' }
})

const chartElement = shallowRef(null)
let chart

const metricDefinitions = [
  { key: 'temperature', label: '温度', unit: '℃', color: '#ff7e00' },
  { key: 'pressure', label: '压力', unit: '千帕', color: '#5cdcff' },
  { key: 'vibration', label: '振动', unit: '毫米/秒', color: '#a78bfa' }
]

const median = (values) => {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.floor(sorted.length / 2)]
}

const chartData = computed(() => {
  const baselines = Object.fromEntries(metricDefinitions.map((metric) => {
    const values = props.points.map((point) => Number(point[metric.key])).filter(Number.isFinite)
    return [metric.key, median(values) || 1]
  }))
  return {
    baselines,
    times: props.points.map((point) => new Date(point.time).toLocaleTimeString('zh-CN', { hour12: false })),
    series: metricDefinitions.map((metric) => ({
      name: metric.label,
      type: 'line',
      smooth: true,
      showSymbol: true,
      symbolSize: (_value, params) => {
        const ratio = params.value / 100
        return ratio < 0.85 || ratio > 1.15 ? 8 : 3
      },
      lineStyle: { color: metric.color, width: 2 },
      itemStyle: {
        color: (params) => params.value < 85 || params.value > 115 ? '#ff5d5d' : metric.color
      },
      data: props.points.map((point) => Number((Number(point[metric.key] || 0) / baselines[metric.key] * 100).toFixed(1))),
      markArea: metric.key === 'temperature' ? {
        silent: true,
        itemStyle: { color: 'rgba(59, 255, 159, 0.10)' },
        data: [[{ yAxis: 85 }, { yAxis: 115 }]]
      } : undefined
    }))
  }
})

const render = async () => {
  await nextTick()
  if (!chartElement.value) return
  chart ||= echarts.init(chartElement.value)
  const data = chartData.value
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      formatter: (items) => {
        const index = items[0]?.dataIndex ?? 0
        const point = props.points[index] || {}
        const values = metricDefinitions.map((metric) =>
          `${metric.label}：${point[metric.key] ?? '无'} ${metric.unit}`)
        return [data.times[index], ...values].join('<br/>')
      }
    },
    legend: { data: metricDefinitions.map((metric) => metric.label), textStyle: { color: '#a8c4e0' } },
    grid: { left: 48, right: 20, top: 42, bottom: 36 },
    xAxis: { type: 'category', data: data.times, axisLabel: { color: '#a8c4e0', hideOverlap: true }, axisLine: { lineStyle: { color: 'rgba(92,220,255,.2)' } } },
    yAxis: {
      type: 'value',
      name: '相对正常值（%）',
      nameTextStyle: { color: '#94a3b8' },
      min: (value) => Math.floor(Math.min(value.min, 80) - 5),
      max: (value) => Math.ceil(Math.max(value.max, 120) + 5),
      axisLabel: { color: '#a8c4e0' },
      splitLine: { lineStyle: { color: 'rgba(92,220,255,.10)', type: 'dashed' } }
    },
    series: data.series
  }, true)
}

const resize = () => chart?.resize()
watch(() => props.points, render, { deep: true })
onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<template>
  <section class="history-panel">
    <div class="history-heading">
      <div>
        <h4>故障前十五分钟历史趋势</h4>
        <p>绿色区域表示合理波动范围，红色数据点表示异常偏离。</p>
      </div>
      <el-tag type="danger" effect="dark">故障发生时间：{{ sourceTime ? new Date(sourceTime).toLocaleString('zh-CN', { hour12: false }) : '无' }}</el-tag>
    </div>
    <div v-if="points.length" ref="chartElement" class="history-chart" />
    <el-empty v-else description="暂无故障前历史数据" />
  </section>
</template>

<style scoped>
.history-panel {
  padding: 16px;
  border: 1px solid rgba(82, 200, 255, .17);
  border-radius: 14px;
  background: rgba(15, 23, 42, .55);
}

.history-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.history-heading h4,
.history-heading p {
  margin: 0;
}

.history-heading p {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.history-chart {
  height: 320px;
  margin-top: 12px;
}
</style>
