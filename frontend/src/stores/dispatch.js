// frontend/src/stores/dispatch.js
// 维修工单调度 Pinia store
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useDispatchStore = defineStore('dispatch', () => {
  // 当前选中的工单 ID（详情抽屉 / 指派对话框使用）
  const selectedWorkOrderId = ref(null)

  // 自动匹配结果缓存 { workOrderId: DispatchMatchVO }
  const matchCache = ref({})

  // 看板刷新触发器（每次 +1 触发 Kanban 重新拉取）
  const kanbanRefreshTrigger = ref(0)

  // 最后一次同步时间
  const lastSyncAt = ref(null)

  function selectWorkOrder(id) {
    selectedWorkOrderId.value = id
  }

  function setMatchCache(workOrderId, match) {
    matchCache.value[workOrderId] = match
  }

  function getMatchCache(workOrderId) {
    return matchCache.value[workOrderId]
  }

  function triggerKanbanRefresh() {
    kanbanRefreshTrigger.value += 1
  }

  function setLastSync(at) {
    lastSyncAt.value = at
  }

  function clearAll() {
    selectedWorkOrderId.value = null
    matchCache.value = {}
    kanbanRefreshTrigger.value = 0
    lastSyncAt.value = null
  }

  return {
    selectedWorkOrderId,
    matchCache,
    kanbanRefreshTrigger,
    lastSyncAt,
    selectWorkOrder,
    setMatchCache,
    getMatchCache,
    triggerKanbanRefresh,
    setLastSync,
    clearAll
  }
})
