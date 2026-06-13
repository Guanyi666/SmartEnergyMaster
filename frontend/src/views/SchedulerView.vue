<script setup>
import { computed, onMounted, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import ForecastChart from '../components/ForecastChart.vue'
import InsightChart from '../components/InsightChart.vue'
import { useScheduler } from '../composables/useScheduler'

const { currentPrice, priceHours, schedules, suggestions, forecast, history, loading, load, decide } = useScheduler()
const calendarMode = shallowRef('day')
const calendarValue = shallowRef(new Date())
const priceSeries = computed(() => [{ name: '电价', type: 'bar', data: priceHours.value.map((item) => item.price) }])

const makeDecision = (item, status) => {
  decide(item.id, status)
  ElMessage.success(status === 'CONFIRMED' ? `已确认 ${item.deviceName} 的负荷转移建议` : '已驳回本次建议')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-shell scheduler-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">生产调度优化面板</h2>
        <p class="page-subtitle">依据未来电价、设备排产与短期负荷预测辅助排产决策。</p>
      </div>
      <div class="price-now glass-panel">
        <span>当前电价时段</span>
        <strong :style="{ color: currentPrice.color }">{{ currentPrice.tier }} · {{ currentPrice.price }} 元/千瓦时</strong>
      </div>
    </div>

    <section class="glass-panel panel">
      <h3 class="card-title">未来 12 小时电价走势</h3>
      <InsightChart :labels="priceHours.map(item => item.hour)" :series="priceSeries" :height="230" y-unit="元" />
    </section>

    <div class="scheduler-grid section-spacer">
      <section class="glass-panel panel">
        <h3 class="card-title">设备排产甘特图</h3>
        <div class="gantt-scale"><span v-for="hour in 12" :key="hour">{{ hour + 7 }}:00</span></div>
        <div class="gantt-list">
          <div v-for="item in schedules" :key="item.id" class="gantt-row">
            <strong>{{ item.deviceName }}</strong>
            <div class="gantt-track">
              <span class="gantt-bar" :style="{ left: `${((item.start - 8) / 12) * 100}%`, width: `${(item.duration / 12) * 100}%` }">
                {{ item.load }} 千瓦时
              </span>
            </div>
          </div>
        </div>
      </section>

      <section class="glass-panel panel advice-panel">
        <h3 class="card-title">负荷转移建议</h3>
        <div class="suggestion-list">
          <article v-for="item in suggestions" :key="item.id" class="suggestion">
            <div>
              <strong>{{ item.deviceName }}</strong>
              <p>{{ item.action }} · 推迟 {{ item.delay }}</p>
              <span>预计节省 ¥{{ item.saving }}</span>
            </div>
            <div v-if="!item.status" class="suggestion-actions">
              <el-button size="small" type="success" @click="makeDecision(item, 'CONFIRMED')">确认</el-button>
              <el-button size="small" @click="makeDecision(item, 'REJECTED')">驳回</el-button>
            </div>
            <el-tag v-else :type="item.status === 'CONFIRMED' ? 'success' : 'info'">
              {{ item.status === 'CONFIRMED' ? '已确认' : '已驳回' }}
            </el-tag>
          </article>
        </div>
      </section>

      <section class="glass-panel panel">
        <h3 class="card-title">未来 30 分钟预测曲线</h3>
        <ForecastChart :history="history" :forecast="forecast" :height="350" />
      </section>
    </div>

    <section class="glass-panel panel section-spacer calendar-panel">
      <div class="panel-head">
        <h3 class="card-title">排产日历</h3>
        <el-segmented v-model="calendarMode" :options="[{ label: '按天', value: 'day' }, { label: '按周', value: 'week' }]" />
      </div>
      <el-calendar v-model="calendarValue">
        <template #date-cell="{ data }">
          <div class="calendar-cell">
            <span>{{ data.day.split('-').slice(2).join('') }}</span>
            <small v-if="Number(data.day.slice(-2)) % 3 === 0">低谷排产 2 项</small>
            <small v-else>常规排产</small>
          </div>
        </template>
      </el-calendar>
    </section>
  </div>
</template>

<style scoped>
.scheduler-page {
  min-width: 980px;
}

.panel,
.price-now {
  padding: 20px;
}

.price-now {
  display: grid;
  gap: 8px;
  min-width: 240px;
}

.price-now span {
  color: var(--text-secondary);
  font-size: 12px;
}

.price-now strong {
  font-size: 20px;
}

.scheduler-grid {
  display: grid;
  grid-template-columns: 1fr 1.05fr .9fr;
  gap: 18px;
}

.gantt-scale {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  margin-left: 118px;
  color: var(--text-secondary);
  font-size: 9px;
}

.gantt-list,
.suggestion-list {
  display: grid;
  gap: 10px;
}

.gantt-row {
  display: grid;
  grid-template-columns: 110px 1fr;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.gantt-track {
  position: relative;
  height: 30px;
  overflow: hidden;
  border-radius: 8px;
  background: repeating-linear-gradient(90deg, rgba(148,163,184,.12) 0, rgba(148,163,184,.12) 1px, transparent 1px, transparent 8.33%);
}

.gantt-bar {
  position: absolute;
  top: 4px;
  bottom: 4px;
  overflow: hidden;
  padding: 4px 8px;
  border-radius: 6px;
  background: linear-gradient(90deg, #1677a8, #3bff9f);
  white-space: nowrap;
}

.suggestion {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px;
  border: 1px solid rgba(82,200,255,.14);
  border-radius: 14px;
  background: rgba(15,23,42,.56);
}

.suggestion p,
.suggestion span {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.suggestion-actions,
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.calendar-panel :deep(.el-calendar) {
  --el-calendar-border: rgba(148,163,184,.14);
  background: transparent;
}

.calendar-cell {
  display: grid;
  gap: 7px;
}

.calendar-cell small {
  color: var(--accent-green);
}

@media (max-width: 1400px) {
  .scheduler-grid {
    grid-template-columns: 1fr;
  }
}
</style>
