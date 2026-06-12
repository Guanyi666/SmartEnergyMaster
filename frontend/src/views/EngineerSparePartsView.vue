<script setup>
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { listSpareParts, listSparePartUsages } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const parts = shallowRef([])
const usages = shallowRef([])
const loading = shallowRef(false)
const filter = reactive({ keyword: '', lowStockOnly: false })
const lowStockCount = computed(() => parts.value.filter((part) => part.lowStock).length)

const load = async () => {
  loading.value = true
  try {
    const [partList, usageList] = await Promise.all([
      listSpareParts({
        keyword: filter.keyword || undefined,
        lowStockOnly: filter.lowStockOnly || undefined
      }),
      listSparePartUsages({ userName: auth.user?.username, limit: 200 })
    ])
    parts.value = partList || []
    usages.value = usageList || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">库存与个人申请记录</h2>
        <p class="page-subtitle">维修工程师仅可查看配件库存与自己的申请记录，配件申请请在具体工单中提交。</p>
      </div>
      <el-button @click="load">刷新数据</el-button>
    </div>

    <div class="glass-panel filter-panel">
      <el-input v-model="filter.keyword" clearable placeholder="搜索配件编号、名称、规格或供应商" @keyup.enter="load" />
      <el-switch v-model="filter.lowStockOnly" active-text="仅看低库存" @change="load" />
      <el-tag :type="lowStockCount ? 'danger' : 'success'" effect="dark">低库存 {{ lowStockCount }} 项</el-tag>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <section class="glass-panel panel">
      <h3>当前库存</h3>
      <el-table :data="parts" stripe>
        <el-table-column prop="partCode" label="配件编号" min-width="130" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="spec" label="规格" min-width="160" />
        <el-table-column label="当前库存" min-width="120">
          <template #default="{ row }">
            <strong :class="{ 'low-stock': row.lowStock }">{{ row.quantity }} {{ row.unit }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" min-width="100" />
        <el-table-column prop="location" label="存放位置" min-width="130" />
        <el-table-column prop="supplier" label="供应商" min-width="140" />
      </el-table>
    </section>

    <section class="glass-panel panel">
      <h3>我的配件申请记录</h3>
      <el-table :data="usages" stripe>
        <el-table-column prop="usedAt" label="申请时间" min-width="170" />
        <el-table-column prop="partCode" label="配件编号" min-width="130" />
        <el-table-column prop="partName" label="配件名称" min-width="160" />
        <el-table-column prop="quantity" label="数量" min-width="80" />
        <el-table-column prop="workOrderNo" label="关联工单" min-width="160">
          <template #default="{ row }">{{ row.workOrderNo || '未关联工单' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="200" />
      </el-table>
      <el-empty v-if="!usages.length" description="暂无个人申请记录" />
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 16px;
}

.filter-panel {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto auto auto;
  align-items: center;
  gap: 14px;
  padding: 18px;
}

.panel {
  padding: 18px;
}

.panel h3 {
  margin: 0 0 14px;
}

.low-stock {
  color: #ff5d5d;
}
</style>
