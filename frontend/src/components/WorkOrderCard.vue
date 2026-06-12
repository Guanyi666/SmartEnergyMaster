<template>
  <div class="wo-card"
       :class="[
         'prio-' + (order.priority || 'HIGH').toLowerCase(),
         { 'is-locked': order.status === 'RESOLVED' }
       ]"
       @click="$emit('click', order)">
    <!-- 顶部：工单号 + 优先级 chip + 来源 chip (AUTO/MANUAL) -->
    <div class="wo-head">
      <span class="wo-no">{{ order.orderNo }}</span>
      <span class="source-chip" :class="`source-${(order.source || 'AUTO').toLowerCase()}`">
        {{ order.source === 'MANUAL' ? '手动' : '告警' }}
      </span>
      <span class="prio-chip" :class="['tone-' + (order.priority || 'HIGH').toLowerCase()]">
        {{ priorityLabel(order.priority) }}
      </span>
    </div>

    <!-- 设备 -->
    <div class="wo-device">
      <el-icon><Cpu /></el-icon>
      <span class="device-name">{{ order.deviceName || '—' }}</span>
      <span class="device-code">{{ order.deviceCode || '' }}</span>
    </div>

    <!-- 标题 + 故障类型 emoji -->
    <div class="wo-title">
      <span class="fault-emoji">{{ faultEmoji(order.faultType) }}</span>
      <span class="title-text">{{ order.title }}</span>
    </div>

    <!-- 关键指标 -->
    <div class="wo-metrics">
      <div class="metric">
        <span class="metric-label">温度</span>
        <span class="metric-value">{{ formatNum(order.latestTemperature) }}<i>℃</i></span>
      </div>
      <div class="metric">
        <span class="metric-label">压力</span>
        <span class="metric-value">{{ formatNum(order.latestPressure) }}<i>kPa</i></span>
      </div>
      <div class="metric">
        <span class="metric-label">振动</span>
        <span class="metric-value">{{ formatNum(order.latestVibration) }}<i>mm/s</i></span>
      </div>
    </div>

    <!-- 底部：层叠头像 + 时间 -->
    <div class="wo-foot">
      <div class="assignee-stack" v-if="activeList.length">
        <div class="avatar-stack">
          <div
            v-for="(p, i) in displayAvatars"
            :key="p.personnelId || i"
            class="avatar"
            :style="{
              background: avatarBg(p.avatarColor),
              zIndex: 10 - i
            }"
            :title="p.name"
          >{{ initial(p.name) }}</div>
          <div v-if="overflowCount > 0" class="avatar more">+{{ overflowCount }}</div>
        </div>
        <span class="assignee-summary">
          <span v-if="activeList.length === 1">{{ activeList[0].name }}</span>
          <span v-else>{{ activeList[0].name }} 等 {{ activeList.length }} 人</span>
        </span>
      </div>
      <div class="assignee empty" v-else>
        <div class="avatar muted">?</div>
        <span>待指派</span>
      </div>
      <span class="time">{{ timeAgo(order.createdAt) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Cpu } from '@element-plus/icons-vue'

import { getFaultTypeMeta, getPriorityMeta } from '../utils/status'

const props = defineProps({
  order: { type: Object, required: true }
})
defineEmits(['click'])

const priorityLabel = (p) => getPriorityMeta(p).label
const faultEmoji = (f) => getFaultTypeMeta(f).emoji
const faultLabel = (f) => getFaultTypeMeta(f).label

const formatNum = (v) => (v == null ? '—' : Number(v).toFixed(1))

// 🆕 二次迭代：层叠头像
// 优先用后端返回的 activeAssignments（完整列表）；无则回退到 JOIN 的 assigneeName（单字段）
// ⚠️ 注意：不要再兜底到 work_order.assignee 老字段——它只是同步用的遗留字符串，
//   故障自动创建工单时会被 device.maintainer（设备主负责人）预填，与真实指派人无关。
//   此兜底会导致卡片显示"张工"但抽屉显示"尚未指派"的不一致。
//   行为与 WorkOrderDetailDrawer 对齐：没有活跃指派就老老实实显示"待指派"。
const activeList = computed(() => {
  if (Array.isArray(props.order.activeAssignments) && props.order.activeAssignments.length > 0) {
    return props.order.activeAssignments
  }
  if (props.order.assigneeName) {
    return [{
      personnelId: props.order.assigneeId,
      name: props.order.assigneeName,
      avatarColor: null
    }]
  }
  return []
})

const displayAvatars = computed(() => activeList.value.slice(0, 3))
const overflowCount = computed(() => Math.max(0, activeList.value.length - 3))

const avatarBg = (color) => {
  if (!color) return 'linear-gradient(135deg, #52c8ff, #a78bfa)'
  return `linear-gradient(135deg, ${color}, #a78bfa)`
}

const initial = (name) => (name || '?').charAt(0).toUpperCase()

const timeAgo = (iso) => {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  const diff = Math.floor((Date.now() - t) / 1000)
  if (diff < 60) return diff + '秒前'
  if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
  return Math.floor(diff / 86400) + '天前'
}
</script>

<style scoped>
.wo-card {
  background: rgba(15, 23, 42, 0.62);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  padding: 14px 14px 12px;
  margin-bottom: 12px;
  cursor: grab;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}

.wo-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
}

.wo-card.prio-critical::before { background: #a78bfa; }
.wo-card.prio-high::before     { background: #ff9f43; }
.wo-card.prio-medium::before   { background: #52c8ff; }
.wo-card.prio-low::before      { background: #3bff9f; }

.wo-card:hover {
  transform: translateY(-4px);
  border-color: rgba(82, 200, 255, 0.55);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

/* 🔒 已闭环工单：不可拖动、视觉降级、抑制 hover 浮起 */
.wo-card.is-locked {
  cursor: not-allowed;
  opacity: 0.82;
}
.wo-card.is-locked::after {
  content: '🔒 已闭环 · 不可拖动';
  position: absolute;
  right: 10px;
  top: 10px;
  font-size: 10px;
  color: var(--text-secondary);
  background: rgba(15, 23, 42, 0.7);
  padding: 2px 6px;
  border-radius: 6px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  letter-spacing: 0.5px;
}
.wo-card.is-locked:hover {
  transform: none;
  border-color: rgba(148, 163, 184, 0.18);   /* 抑制 hover 蓝色边框高亮 */
  box-shadow: none;
}

.wo-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.wo-no {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 0.5px;
}

.prio-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  letter-spacing: 1px;
}

.prio-chip.tone-critical { background: rgba(167, 139, 250, 0.2);  color: #a78bfa; }
.prio-chip.tone-high     { background: rgba(255, 159, 67, 0.2);  color: #ff9f43; }
.prio-chip.tone-medium   { background: rgba(82, 200, 255, 0.2);  color: #52c8ff; }
.prio-chip.tone-low      { background: rgba(59, 255, 159, 0.2);   color: #3bff9f; }

/* 🆕 来源 chip：AUTO=告警生成（默认蓝）、MANUAL=手动创建（强调橙） */
.source-chip {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 6px;
  letter-spacing: 0.5px;
  margin-right: 4px;
}
.source-chip.source-auto   { background: rgba(82, 200, 255, 0.12); color: #52c8ff; border: 1px solid rgba(82, 200, 255, 0.3); }
.source-chip.source-manual { background: rgba(255, 159, 67, 0.15); color: #ff9f43; border: 1px solid rgba(255, 159, 67, 0.4); font-weight: 600; }

.wo-device {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.wo-device .device-name {
  color: #e0f2fe;
  font-size: 13px;
  font-weight: 500;
}

.wo-device .device-code {
  font-family: 'SF Mono', Consolas, monospace;
  color: rgba(82, 200, 255, 0.8);
}

.wo-title {
  font-size: 14px;
  color: #e0f2fe;
  margin-bottom: 10px;
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 6px;
}

.fault-emoji {
  font-size: 16px;
}

.title-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.wo-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  padding: 8px 0;
  border-top: 1px dashed rgba(148, 163, 184, 0.18);
  border-bottom: 1px dashed rgba(148, 163, 184, 0.18);
  margin-bottom: 8px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-label {
  font-size: 10px;
  color: var(--text-secondary);
}

.metric-value {
  font-size: 13px;
  font-weight: 600;
  color: #e0f2fe;
  font-family: 'SF Mono', Consolas, monospace;
}

.metric-value i {
  font-style: normal;
  font-size: 10px;
  color: var(--text-secondary);
  margin-left: 2px;
  font-weight: 400;
}

.wo-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

/* 🆕 层叠头像容器 */
.assignee-stack {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.avatar-stack {
  display: inline-flex;
  align-items: center;
}

.avatar-stack .avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  color: #0f172a;
  border: 2px solid rgba(15, 23, 42, 0.95);
  /* 每个头像比前一个左移 8px 实现层叠 */
  margin-left: -8px;
}

.avatar-stack .avatar:first-child {
  margin-left: 0;
}

.avatar.more {
  background: rgba(148, 163, 184, 0.25) !important;
  color: #e0f2fe;
  font-size: 10px;
  z-index: 0;
}

.assignee-summary {
  font-size: 12px;
  color: #e0f2fe;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assignee.empty {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.assignee.empty .avatar {
  background: rgba(148, 163, 184, 0.2);
  color: var(--text-secondary);
}

.time {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: 'SF Mono', Consolas, monospace;
  flex-shrink: 0;
}
</style>
