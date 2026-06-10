<template>
  <div ref="chartRef" class="knowledge-graph"></div>
</template>

<script setup>
import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

echarts.use([GraphChart, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const props = defineProps({
  graphData: { type: Object, default: () => ({ nodes: [], links: [] }) },
  height: { type: String, default: '520px' }
})

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

const categoryColors = {
  device_type: '#52c8ff',
  fault_type: '#ff9f43',
  sop: '#3bff9f',
  case: '#c084fc',
  cause: '#f472b6'
}

const categoryLabels = {
  device_type: '设备类型',
  fault_type: '故障类型',
  sop: 'SOP',
  case: '案例',
  cause: '根因'
}

const buildOption = (data) => {
  const categoryKeys = Object.keys(categoryColors)
  return {
    tooltip: {
      formatter: (params) => {
        if (params.dataType === 'node') {
          const cat = categoryLabels[params.data.category] || params.data.category
          const desc = params.data.description ? '<br/>' + params.data.description : ''
          return '<strong>' + params.data.name + '</strong><br/>类别: ' + cat + desc
        }
        return params.data.source + ' -> ' + params.data.target + '<br/>关系: ' + (params.data.label || '')
      }
    },
    legend: [{
      data: categoryKeys.map((key) => ({
        name: key,
        icon: 'circle',
        textStyle: { color: '#cbd5f5' }
      })),
      formatter: (name) => categoryLabels[name] || name,
      textStyle: { color: '#cbd5f5' },
      top: 10
    }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      center: ['50%', '55%'],
      // 显式给出初始 boundingRect，避开"容器 0x0 时就计算"的问题
      boundingRect: undefined,
      data: (data.nodes || []).map((n) => ({
        id: n.id,
        name: n.name,
        category: n.category,
        symbolSize: n.symbolSize || 50,
        description: n.description,
        itemStyle: { color: categoryColors[n.category] || '#94a3b8' },
        label: { show: true, color: '#e2e8f0', fontSize: 12, position: 'right' }
      })),
      links: (data.links || []).map((l) => ({
        source: l.source,
        target: l.target,
        label: { show: false },
        lineStyle: { color: 'rgba(148, 163, 184, 0.4)', curveness: 0.1 }
      })),
      categories: categoryKeys.map((key) => ({
        name: key,
        itemStyle: { color: categoryColors[key] }
      })),
      force: {
        repulsion: 380,
        edgeLength: 130,
        gravity: 0.08,
        friction: 0.35,
        layoutAnimation: true,
        preventOverlap: true,
        minRadius: 30,
        maxRadius: 70
      },
      emphasis: { focus: 'adjacency', lineStyle: { width: 3 } }
    }]
  }
}

const renderChart = () => {
  if (!chartRef.value) {
    return
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value, null, { renderer: 'canvas' })
    // 容器尺寸变化时自动 resize + 重新 fit
    resizeObserver = new ResizeObserver(() => {
      if (chartInstance) {
        chartInstance.resize()
      }
    })
    resizeObserver.observe(chartRef.value)
  }
  chartInstance.setOption(buildOption(props.graphData), true)
}

onMounted(async () => {
  // 等一帧让容器拿到真实尺寸
  await nextTick()
  renderChart()
})

watch(
  () => props.graphData,
  () => {
    renderChart()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.knowledge-graph {
  width: 100%;
  height: 560px;
  min-height: 480px;
}
</style>