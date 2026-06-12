<script setup>
import { onMounted, reactive, shallowRef } from 'vue'
import { listAuditLogs } from '../api'

const rows = shallowRef([])
const loading = shallowRef(false)
const range = shallowRef([])
const filters = reactive({ action: '', module: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const actions = [
  { value: 'CREATE', label: '创建' },
  { value: 'UPDATE', label: '更新' },
  { value: 'DELETE', label: '删除' },
  { value: 'STATUS_CHANGE', label: '状态变更' }
]
const actionLabels = Object.fromEntries(actions.map((item) => [item.value, item.label]))
const moduleLabels = { USER: '用户管理' }
const targetLabels = { SYS_USER: '系统用户', USER: '用户' }

const load = async () => {
  loading.value = true
  try {
    const params = { ...filters, page: pagination.page, size: pagination.size }
    if (range.value?.length === 2) {
      params.startAt = range.value[0].toISOString()
      params.endAt = range.value[1].toISOString()
    }
    const result = await listAuditLogs(params)
    rows.value = result.records || []
    pagination.total = result.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div><h2 class="page-title">审计日志</h2><p class="page-subtitle">追踪用户、配置和关键业务操作。</p></div>
    </div>
    <section class="glass-panel filter-panel">
      <el-select v-model="filters.action" placeholder="操作类型" clearable><el-option v-for="item in actions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="filters.module" placeholder="模块" clearable><el-option label="用户管理" value="USER" /></el-select>
      <el-date-picker v-model="range" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" />
      <el-button type="primary" @click="load">筛选</el-button>
    </section>
    <section class="glass-panel table-panel">
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="createdAt" label="时间" min-width="180" />
        <el-table-column prop="actorUsername" label="操作人" min-width="110" />
        <el-table-column label="模块" min-width="100"><template #default="{ row }">{{ moduleLabels[row.module] || row.module }}</template></el-table-column>
        <el-table-column label="操作" min-width="120"><template #default="{ row }">{{ actionLabels[row.action] || row.action }}</template></el-table-column>
        <el-table-column label="对象类型" min-width="120"><template #default="{ row }">{{ targetLabels[row.targetType] || row.targetType }}</template></el-table-column>
        <el-table-column prop="targetId" label="对象序号" min-width="90" />
        <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip />
      </el-table>
      <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size" :total="pagination.total" layout="total, sizes, prev, pager, next" @change="load" />
    </section>
  </div>
</template>

<style scoped>
.filter-panel { display: flex; gap: 12px; padding: 16px; margin-bottom: 18px; }
.table-panel { display: grid; gap: 16px; padding: 20px; }
.table-panel :deep(.el-pagination) { justify-content: flex-end; }
</style>
