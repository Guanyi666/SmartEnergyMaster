<script setup>
import { computed, shallowRef, watch } from 'vue'
import { Bell } from '@element-plus/icons-vue'

const props = defineProps({
  alerts: { type: Array, default: () => [] }
})
const emit = defineEmits(['open-alert'])

const readIds = shallowRef(new Set())
const unread = computed(() => props.alerts.filter((item) => !readIds.value.has(item.id)).length)

const markAllRead = () => {
  readIds.value = new Set(props.alerts.map((item) => item.id))
}

const openAlert = (item) => {
  readIds.value = new Set([...readIds.value, item.id])
  emit('open-alert', item)
}

watch(() => props.alerts.map((item) => item.id).join(','), () => {
  readIds.value = new Set([...readIds.value].filter((id) => props.alerts.some((item) => item.id === id)))
})
</script>

<template>
  <el-popover placement="bottom-end" :width="360" trigger="click" popper-class="notification-popover">
    <template #reference>
      <el-badge :value="unread" :hidden="!unread" :max="99">
        <el-button circle class="bell-button" :icon="Bell" aria-label="通知" />
      </el-badge>
    </template>
    <div class="notification-head">
      <strong>告警通知</strong>
      <el-button link type="primary" @click="markAllRead">全部已读</el-button>
    </div>
    <div class="notification-list">
      <button
        v-for="item in alerts"
        :key="item.id"
        class="notification-item"
        :class="{ unread: !readIds.has(item.id) }"
        type="button"
        @click="openAlert(item)"
      >
        <span class="notification-dot" />
        <span>
          <strong>{{ item.deviceName }} · {{ item.title }}</strong>
          <small>{{ item.assignee || '待指派' }} / {{ item.priority }}</small>
        </span>
      </button>
      <p v-if="!alerts.length" class="empty-text">当前没有未处理告警</p>
    </div>
  </el-popover>
</template>

<style scoped>
.bell-button {
  background: rgba(13, 37, 64, .88);
  border-color: rgba(92, 220, 255, .32);
  color: var(--accent-cyan);
}

.bell-button:hover {
  background: rgba(13, 37, 64, 1);
  border-color: var(--accent-cyan);
  box-shadow: 0 0 12px rgba(92, 220, 255, 0.4);
}

.notification-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.notification-head strong {
  color: var(--accent-cyan);
  letter-spacing: 2px;
  font-size: 14px;
}

.notification-list {
  display: grid;
  gap: 6px;
  max-height: 360px;
  overflow: auto;
}

.notification-item {
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 11px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.notification-item:hover,
.notification-item.unread {
  border-color: rgba(92, 220, 255, 0.32);
  background: rgba(92, 220, 255, 0.08);
}

.notification-dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: #64748b;
}

.unread .notification-dot {
  background: var(--accent-red);
  box-shadow: 0 0 10px rgba(255, 93, 93, 0.6);
}

.notification-item span:last-child {
  display: grid;
  gap: 5px;
}

.notification-item small,
.empty-text {
  color: var(--text-secondary);
}
</style>
