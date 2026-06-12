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
  background: rgba(15, 23, 42, .88);
  border-color: rgba(82, 200, 255, .28);
  color: #dbeafe;
}

.notification-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
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
  border-radius: 12px;
  background: transparent;
  color: #eef4ff;
  text-align: left;
  cursor: pointer;
}

.notification-item:hover,
.notification-item.unread {
  border-color: rgba(82, 200, 255, .2);
  background: rgba(82, 200, 255, .08);
}

.notification-dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: #64748b;
}

.unread .notification-dot {
  background: #ff5d5d;
  box-shadow: 0 0 10px rgba(255, 93, 93, .6);
}

.notification-item span:last-child {
  display: grid;
  gap: 5px;
}

.notification-item small,
.empty-text {
  color: #94a3b8;
}
</style>
