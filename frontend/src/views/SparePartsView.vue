<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">备件库存管理</h2>
        <p class="page-subtitle">登记备件出入库、跟踪领用记录；库存低于安全阈值时自动红色预警，避免维修缺件延误。</p>
      </div>
      <div class="header-actions">
        <el-button @click="loadParts()">立即刷新</el-button>
        <el-button type="warning" @click="loadParts(true)">仅看低库存</el-button>
        <el-button @click="openAllHistory()">全部领用记录</el-button>
        <el-button type="primary" @click="openPartDialog()">新增备件</el-button>
      </div>
    </div>

    <div class="glass-panel filter-panel">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" clearable placeholder="编号/名称/规格/供应商" style="width: 280px" @keyup.enter="loadParts" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadParts()">查询</el-button>
          <el-button @click="resetFilter()">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-tag :type="stats.lowStock > 0 ? 'danger' : 'success'" effect="dark" size="large">
            低库存 {{ stats.lowStock }} 项
          </el-tag>
          <el-tag type="info" effect="dark" size="large" style="margin-left: 8px">
            备件合计 {{ stats.total }} 项
          </el-tag>
        </el-form-item>
      </el-form>
    </div>

    <div class="glass-panel table-panel section-spacer">
      <el-table :data="parts" stripe>
        <el-table-column prop="partCode" label="备件编号" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="spec" label="规格" min-width="160" />
        <el-table-column label="当前库存" min-width="120">
          <template #default="{ row }">
            <span :class="row.lowStock ? 'qty-low' : 'qty-ok'">
              {{ row.quantity }} {{ row.unit }}
            </span>
            <el-tag v-if="row.lowStock" type="danger" size="small" effect="dark" style="margin-left: 6px">低</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" min-width="100">
          <template #default="{ row }">{{ row.safetyStock }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" min-width="100">
          <template #default="{ row }">{{ row.unitPrice ? Number(row.unitPrice).toFixed(2) : '--' }}</template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" min-width="140" show-overflow-tooltip class-name="hidden-md" />
        <el-table-column prop="location" label="位置" min-width="100" show-overflow-tooltip class-name="hidden-md" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <!-- 宽屏：按钮组 -->
            <span class="btn-group-wide">
              <el-button link type="primary" size="small" @click.stop="openPartDialog(row)">编辑</el-button>
              <el-button link type="warning" size="small" @click.stop="openUsageDialog(row)">领用</el-button>
              <el-button link type="info" size="small" @click.stop="openHistory(row)">记录</el-button>
              <el-button link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button>
            </span>
            <!-- 窄屏：下拉菜单 -->
            <span class="btn-group-narrow">
              <el-dropdown trigger="click" @command="(cmd) => handleRowCmd(cmd, row)">
                <el-button link type="primary" size="small">操作 ▾</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="usage">领用</el-dropdown-item>
                    <el-dropdown-item command="history">领用记录</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!parts.length" class="empty-tip">暂无备件数据，点击"新增备件"开始登记。</div>
    </div>

    <!-- 备件新增/编辑弹窗 -->
    <el-dialog v-model="partDialog" :title="partForm.id ? '编辑备件' : '新增备件'" width="640px">
      <el-form :model="partForm" label-width="100px">
        <el-form-item label="备件编号">
          <el-input v-model="partForm.partCode" :disabled="Boolean(partForm.id)" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="partForm.name" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="partForm.spec" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="partForm.unit" placeholder="件 / 桶 / 袋 / 套 ..." />
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input-number v-model="partForm.quantity" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="partForm.safetyStock" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="单价 (元)">
          <el-input-number v-model="partForm.unitPrice" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="partForm.supplier" />
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="partForm.location" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partDialog = false">取消</el-button>
        <el-button type="primary" @click="submitPart()">保存</el-button>
      </template>
    </el-dialog>

    <!-- 领用登记弹窗 -->
    <el-dialog v-model="usageDialog" title="备件领用登记" width="560px">
      <el-form :model="usageForm" label-width="100px">
        <el-form-item label="备件">
          <el-tag size="large" effect="dark">{{ usageForm.partCode }} - {{ usageForm.partName }}</el-tag>
          <span class="muted" style="margin-left: 12px">当前库存：{{ usageForm.maxQuantity }} {{ usageForm.unit }}</span>
        </el-form-item>
        <el-form-item label="领用数量">
          <el-input-number v-model="usageForm.quantity" :min="1" :max="usageForm.maxQuantity" :step="1" />
        </el-form-item>
        <el-form-item label="关联工单">
          <el-input v-model="usageForm.workOrderId" placeholder="可选，工单序号" />
        </el-form-item>
        <el-form-item label="领用人">
          <el-input v-model="usageForm.userName" placeholder="如 张工" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="usageForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="usageDialog = false">取消</el-button>
        <el-button type="primary" @click="submitUsage()">确认领用</el-button>
      </template>
    </el-dialog>

    <!-- 领用记录弹窗（单个备件 或 全部） -->
    <el-dialog v-model="historyDialog" :title="historyPart ? '领用记录：' + historyPart.name : '全部备件领用记录'" width="780px">
      <el-table :data="usages" stripe>
        <el-table-column prop="partCode" label="编号" min-width="120" />
        <el-table-column prop="partName" label="名称" min-width="160" />
        <el-table-column prop="workOrderNo" label="关联工单" min-width="160">
          <template #default="{ row }">{{ row.workOrderNo || '--' }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" min-width="80" />
        <el-table-column prop="userName" label="领用人" min-width="100" />
        <el-table-column prop="note" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="usedAt" label="领用时间" min-width="170" />
      </el-table>
      <div v-if="!usages.length" class="empty-tip">暂无领用记录</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createSparePart, deleteSparePart, listSparePartUsages, listSpareParts, recordSparePartUsage, updateSparePart } from '../api'

const parts = ref([])
const usages = ref([])
const filter = reactive({ keyword: '' })
const onlyLowStock = ref(false)
const stats = reactive({ lowStock: 0, total: 0 })

const partDialog = ref(false)
const partForm = reactive({ id: null, partCode: '', name: '', spec: '', unit: '件', quantity: 0, safetyStock: 0, unitPrice: 0, supplier: '', location: '' })

const usageDialog = ref(false)
const usageForm = reactive({ partId: null, partCode: '', partName: '', unit: '', maxQuantity: 0, quantity: 1, workOrderId: '', userName: '', note: '' })

const historyDialog = ref(false)
const historyPart = ref(null)

const loadParts = async (lowOnly = false) => {
  onlyLowStock.value = lowOnly
  parts.value = await listSpareParts({
    keyword: filter.keyword || undefined,
    lowStockOnly: lowOnly || undefined
  })
  recomputeStats()
}

const recomputeStats = () => {
  stats.total = parts.value.length
  stats.lowStock = parts.value.filter((p) => p.lowStock).length
}

const resetFilter = () => {
  filter.keyword = ''
  loadParts(onlyLowStock.value)
}

const openPartDialog = (row) => {
  if (row && row.id) {
    Object.assign(partForm, {
      id: row.id,
      partCode: row.partCode,
      name: row.name,
      spec: row.spec,
      unit: row.unit,
      quantity: row.quantity,
      safetyStock: row.safetyStock,
      unitPrice: row.unitPrice,
      supplier: row.supplier,
      location: row.location
    })
  } else {
    Object.assign(partForm, {
      id: null, partCode: '', name: '', spec: '', unit: '件', quantity: 0, safetyStock: 0, unitPrice: 0, supplier: '', location: ''
    })
  }
  partDialog.value = true
}

const submitPart = async () => {
  if (!partForm.partCode || !partForm.name) {
    ElMessage.warning('备件编号和名称必填')
    return
  }
  const payload = { ...partForm }
  delete payload.id
  if (partForm.id) {
    await updateSparePart(partForm.id, payload)
    ElMessage.success('备件已更新')
  } else {
    await createSparePart(payload)
    ElMessage.success('备件已新增')
  }
  partDialog.value = false
  await loadParts(onlyLowStock.value)
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除备件 ${row.name}？领用记录会一并删除。`, '删除确认', { type: 'warning' })
  await deleteSparePart(row.id)
  ElMessage.success('备件已删除')
  await loadParts(onlyLowStock.value)
}

const openUsageDialog = (row) => {
  Object.assign(usageForm, {
    partId: row.id,
    partCode: row.partCode,
    partName: row.name,
    unit: row.unit,
    maxQuantity: row.quantity,
    quantity: 1,
    workOrderId: '',
    userName: '',
    note: ''
  })
  usageDialog.value = true
}

const submitUsage = async () => {
  if (!usageForm.userName) {
    ElMessage.warning('请填写领用人')
    return
  }
  const payload = {
    partId: usageForm.partId,
    quantity: usageForm.quantity,
    workOrderId: usageForm.workOrderId ? Number(usageForm.workOrderId) : null,
    userName: usageForm.userName,
    note: usageForm.note
  }
  await recordSparePartUsage(payload)
  ElMessage.success('领用成功，库存已扣减')
  usageDialog.value = false
  await loadParts(onlyLowStock.value)
}

const openHistory = async (row) => {
  historyPart.value = row
  historyDialog.value = true
  usages.value = await listSparePartUsages({ partId: row.id, limit: 50 })
}

const openAllHistory = async () => {
  historyPart.value = null
  historyDialog.value = true
  usages.value = await listSparePartUsages({ limit: 100 })
}

const handleRowCmd = (cmd, row) => {
  if (cmd === 'edit')    openPartDialog(row)
  if (cmd === 'usage')   openUsageDialog(row)
  if (cmd === 'history') openHistory(row)
  if (cmd === 'delete')  handleDelete(row)
}

defineExpose({ openHistory })

onMounted(() => loadParts())
</script>

<style scoped>
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.filter-panel {
  padding: 18px 20px;
  overflow-x: auto;
}

.filter-panel :deep(.el-form) {
  flex-wrap: wrap;
}

.table-panel {
  padding: 18px 20px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
/* v6.2 改造：5 页面（备件库存）加横向滑动适配屏幕 */
.table-panel :deep(.el-table) {
  min-width: 1100px;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-actions {
    width: 100%;
  }
}

.table-panel :deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

:deep(.el-dialog) {
  max-width: 90vw;
}

.empty-tip {
  padding: 32px 0;
  text-align: center;
  color: var(--text-secondary);
}

.qty-low {
  color: #ff5d5d;
  font-weight: 700;
}

.qty-ok {
  color: #3bff9f;
  font-weight: 600;
}

.muted {
  color: var(--text-secondary);
  font-size: 13px;
}

:deep(.el-table) {
  background-color: transparent !important;
  color: #e2e8f0;
  --el-table-border-color: rgba(148, 163, 184, 0.2);
  --el-table-header-bg-color: rgba(15, 23, 42, 0.85);
  --el-table-row-hover-bg-color: rgba(82, 200, 255, 0.1);
  --el-table-tr-bg-color: transparent;
}

:deep(.el-table tr),
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell) {
  background-color: transparent !important;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22) !important;
  color: #e2e8f0;
}

:deep(.el-table th.el-table__cell) {
  background-color: rgba(15, 23, 42, 0.85) !important;
  color: #cbd5f5 !important;
  font-weight: 600;
  font-size: 13px;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(82, 200, 255, 0.35) !important;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: rgba(15, 23, 42, 0.35) !important;
}

:deep(.el-table__row:hover > td) {
  background-color: rgba(82, 200, 255, 0.08) !important;
}

:deep(.el-table__empty-block) {
  background-color: transparent !important;
}

/* 宽屏显示按钮组，窄屏显示下拉菜单 */
.btn-group-narrow { display: none; }

@media (max-width: 900px) {
  .btn-group-wide   { display: none; }
  .btn-group-narrow { display: inline-block; }
  :deep(.hidden-md) { display: none; }
}
</style>
