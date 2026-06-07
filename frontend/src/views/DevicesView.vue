<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">设备管理与维修工单</h2>
        <p class="page-subtitle">页面每 8 秒自动刷新一次，设备状态和工单列表会自动同步。</p>
      </div>
      <div class="analysis-tools">
        <el-button @click="refreshNow">立即刷新</el-button>
        <el-button type="primary" @click="openDialog()">新增设备</el-button>
      </div>
    </div>

    <div class="glass-panel table-panel">
      <div class="table-head">
        <h3 class="card-title">设备列表</h3>
      </div>
      <el-table :data="devices">
        <el-table-column prop="deviceName" label="设备名称" min-width="160" />
        <el-table-column prop="deviceCode" label="设备编码" min-width="120" />
        <el-table-column prop="deviceType" label="设备类型" min-width="120" />
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            <StatusPill :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="location" label="区域" min-width="120" />
        <el-table-column prop="maintainer" label="维修工人" min-width="120" />
        <el-table-column label="实时指标" min-width="220">
          <template #default="{ row }">
            功率 {{ formatNumber(row.usageKwh) }} / 温度 {{ formatNumber(row.temperature) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="glass-panel table-panel section-spacer">
      <div class="table-head">
        <h3 class="card-title">维修工单</h3>
        <el-select v-model="orderFilter" style="width: 160px" @change="refreshNow">
          <el-option label="全部" value="" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="IN_PROGRESS" />
          <el-option label="已修复" value="RESOLVED" />
        </el-select>
      </div>
      <el-table :data="workOrders">
        <el-table-column prop="orderNo" label="工单号" min-width="180" />
        <el-table-column prop="deviceName" label="设备" min-width="140" />
        <el-table-column prop="title" label="故障主题" min-width="180" />
        <el-table-column prop="assignee" label="维修工人" min-width="120" />
        <el-table-column prop="status" label="状态" min-width="130" />
        <el-table-column label="关键指标" min-width="220">
          <template #default="{ row }">
            温度 {{ formatNumber(row.latestTemperature) }} / 压力 {{ formatNumber(row.latestPressure) }} / 振动 {{ formatNumber(row.latestVibration) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" @click="updateOrder(row, 'IN_PROGRESS')">确认处理</el-button>
            <el-button v-if="row.status !== 'RESOLVED'" size="small" type="success" @click="updateOrder(row, 'RESOLVED')">已修复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingDevice.id ? '编辑设备' : '新增设备'" width="520px">
      <el-form label-width="96px">
        <el-form-item label="设备编码">
          <el-input v-model="form.deviceCode" :disabled="Boolean(editingDevice.id)" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="form.deviceName" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-input v-model="form.deviceType" />
        </el-form-item>
        <el-form-item label="设备状态">
          <el-select v-model="form.status">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="停机" value="STOPPED" />
            <el-option label="离线" value="OFFLINE" />
            <el-option label="故障待处理" value="FAULT" />
            <el-option label="维修中" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在区域">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="维修工人">
          <el-input v-model="form.maintainer" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDevice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '../components/StatusPill.vue'
import { createDevice, deleteDevice, getDevices, getWorkOrders, updateDevice, updateWorkOrderStatus } from '../api'
import { usePollingTask } from '../composables/usePollingTask'

const devices = ref([])
const workOrders = ref([])
const orderFilter = ref('')
const dialogVisible = ref(false)
const editingDevice = ref({})

const emptyForm = () => ({
  deviceCode: '',
  deviceName: '',
  deviceType: '',
  status: 'STOPPED',
  location: '',
  maintainer: '',
  description: ''
})

const form = reactive(emptyForm())

const formatNumber = (value) => (value ?? value === 0 ? Number(value).toFixed(2) : '--')

const resetForm = () => {
  Object.assign(form, emptyForm())
}

const loadDevices = async () => {
  devices.value = await getDevices()
}

const loadWorkOrders = async () => {
  workOrders.value = await getWorkOrders(orderFilter.value)
}

const loadPageData = async () => {
  await Promise.all([loadDevices(), loadWorkOrders()])
}

const { start: startPolling, run: refreshNow } = usePollingTask(loadPageData, 8000)

const openDialog = (row) => {
  dialogVisible.value = true
  editingDevice.value = row || {}
  Object.assign(form, row ? { ...row } : emptyForm())
}

const submitDevice = async () => {
  if (editingDevice.value.id) {
    await updateDevice(editingDevice.value.id, form)
    ElMessage.success('设备已更新')
  } else {
    await createDevice(form)
    ElMessage.success('设备已新增')
  }
  dialogVisible.value = false
  resetForm()
  await refreshNow()
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除设备 ${row.deviceName} 吗？`, '删除确认', {
    type: 'warning'
  })
  await deleteDevice(row.id)
  ElMessage.success('设备已删除')
  await refreshNow()
}

const updateOrder = async (row, status) => {
  await updateWorkOrderStatus(row.id, {
    status,
    assignee: row.assignee
  })
  ElMessage.success(status === 'IN_PROGRESS' ? '工单已确认处理' : '工单已修复')
  await refreshNow()
}

onMounted(async () => {
  await startPolling()
})
</script>

<style scoped>
.analysis-tools {
  display: flex;
  gap: 12px;
}

.table-panel {
  padding: 20px;
}

.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

@media (max-width: 768px) {
  .analysis-tools,
  .table-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
