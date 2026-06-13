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
        <!-- v6.2 改造：OPERATOR 在 3 页面里能新建工单（公共组件） -->
        <el-button type="success" @click="createOrderOpen = true">+ 新建工单</el-button>
      </div>
    </div>

    <div class="search-bar glass-panel">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="设备名称 / 编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="searchForm.type" placeholder="全部" clearable style="width: 160px" :disabled="isDepartmentManager">
            <el-option label="电弧炉" value="ARC_FURNACE" />
            <el-option label="变压器" value="TRANSFORMER" />
            <el-option label="空压机" value="COMPRESSOR" />
            <el-option label="风机" value="FAN" />
            <el-option label="水泵" value="PUMP" />
            <el-option label="锅炉" value="BOILER" />
            <el-option label="发电机组" value="GENERATOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="停机" value="STOPPED" />
            <el-option label="离线" value="OFFLINE" />
            <el-option label="故障待处理" value="FAULT" />
            <el-option label="维修中" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="glass-panel table-panel">
      <div class="table-head">
        <h3 class="card-title">设备列表</h3>
      </div>
      <el-table :data="devices">
        <el-table-column prop="deviceName" label="设备名称" min-width="160" />
        <el-table-column prop="deviceCode" label="设备编码" min-width="120" />
        <el-table-column label="设备类型" min-width="120">
          <template #default="{ row }">
            {{ deviceTypeLabel(row.deviceType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            <StatusPill :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="区域" min-width="120">
          <template #default="{ row }">
            {{ locationLabel(row.location) }}
          </template>
        </el-table-column>
        <el-table-column prop="maintainer" label="维修工人" min-width="120" />
        <el-table-column label="实时指标" min-width="220">
          <template #default="{ row }">
            功率 {{ formatNumber(row.usageKwh) }} / 温度 {{ formatNumber(row.temperature) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
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
            <!-- v6.2 改造：OPERATOR 失去"维修工单模块的编辑权限"，不能点"确认处理/已修复" -->
            <el-button v-if="canEditWorkOrder && row.status === 'PENDING'" size="small" @click="updateOrder(row, 'IN_PROGRESS')">确认处理</el-button>
            <el-button v-if="canEditWorkOrder && row.status !== 'RESOLVED'" size="small" type="success" @click="updateOrder(row, 'RESOLVED')">已修复</el-button>
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

    <!-- v6.2 改造：新建工单公共对话框（OPERATOR/DEVICE MANAGER 都能用） -->
    <WorkOrderCreateDialog v-model="createOrderOpen" @created="onWorkOrderCreated" />

    <el-dialog v-model="detailVisible" title="设备详情" width="720px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="deviceDetail">
          <div class="detail-section">
            <h4 class="detail-section-title">基本信息</h4>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">设备名称</span>
                <span class="detail-value">{{ deviceDetail.deviceName }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">设备编码</span>
                <span class="detail-value">{{ deviceDetail.deviceCode }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">设备类型</span>
                <span class="detail-value">{{ deviceTypeLabel(deviceDetail.deviceType) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">当前状态</span>
                <span class="detail-value">
                  <StatusPill :status="deviceDetail.status" />
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">所在区域</span>
                <span class="detail-value">{{ locationLabel(deviceDetail.location) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">维修工人</span>
                <span class="detail-value">{{ deviceDetail.maintainer }}</span>
              </div>
              <div class="detail-item detail-full">
                <span class="detail-label">备注</span>
                <span class="detail-value">{{ deviceDetail.description || '--' }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="detail-section-title">实时指标</h4>
            <div class="detail-metrics">
              <div class="metric-chip">
                <span>功率</span>
                <strong>{{ formatNumber(deviceDetail.usageKwh) }} 千瓦时</strong>
              </div>
              <div class="metric-chip">
                <span>碳排放</span>
                <strong>{{ formatNumber(deviceDetail.co2Emission) }} tCO₂</strong>
              </div>
              <div class="metric-chip">
                <span>温度</span>
                <strong>{{ formatNumber(deviceDetail.temperature) }} °C</strong>
              </div>
              <div class="metric-chip">
                <span>振动</span>
                <strong>{{ formatNumber(deviceDetail.vibration) }} 毫米/秒</strong>
              </div>
              <div class="metric-chip">
                <span>压力</span>
                <strong>{{ formatNumber(deviceDetail.pressure) }} 千帕</strong>
              </div>
              <div class="metric-chip">
                <span>电价区间</span>
                <strong>{{ priceTierLabel(deviceDetail.xianPriceTier) }}</strong>
              </div>
            </div>
          </div>

          <div v-if="healthScore" class="detail-section">
            <h4 class="detail-section-title">设备健康度</h4>
            <div class="health-score-area">
              <div class="health-overall">
                <el-progress
                  type="dashboard"
                  :percentage="healthScore.overallScore"
                  :color="healthProgressColor(healthScore.overallScore)"
                  :stroke-width="18"
                  :width="160"
                >
                  <template #default="{ percentage }">
                    <span class="health-percent">{{ percentage }}分</span>
                  </template>
                </el-progress>
                <p class="health-evaluated">评估时间：{{ healthScore.evaluatedAt || '--' }}</p>
              </div>
              <div class="health-subs">
                <div class="sub-score">
                  <span class="sub-label">运行得分</span>
                  <el-progress :percentage="healthScore.runtimeScore" :color="healthProgressColor(healthScore.runtimeScore)" :stroke-width="10" />
                </div>
                <div class="sub-score">
                  <span class="sub-label">故障次数得分</span>
                  <el-progress :percentage="healthScore.faultCountScore" :color="healthProgressColor(healthScore.faultCountScore)" :stroke-width="10" />
                </div>
                <div class="sub-score">
                  <span class="sub-label">振动得分</span>
                  <el-progress :percentage="healthScore.vibrationScore" :color="healthProgressColor(healthScore.vibrationScore)" :stroke-width="10" />
                </div>
                <div class="sub-score">
                  <span class="sub-label">温度得分</span>
                  <el-progress :percentage="healthScore.temperatureScore" :color="healthProgressColor(healthScore.temperatureScore)" :stroke-width="10" />
                </div>
                <div class="sub-score">
                  <span class="sub-label">维保得分</span>
                  <el-progress :percentage="healthScore.maintenanceScore" :color="healthProgressColor(healthScore.maintenanceScore)" :stroke-width="10" />
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="detail-section-title">故障历史</h4>
            <el-timeline v-if="faultHistory.length">
              <el-timeline-item
                v-for="order in faultHistory"
                :key="order.id"
                :timestamp="order.createdAt || order.sourceTime"
                placement="top"
                :color="orderTimelineColor(order.status)"
              >
                <div class="timeline-card">
                  <strong>{{ order.title }}</strong>
                  <p v-if="order.description">{{ order.description }}</p>
                  <div class="timeline-meta">
                    <span>工单号：{{ order.orderNo }}</span>
                    <span>优先级：{{ priorityLabel(order.priority) }}</span>
                    <span>负责人：{{ order.assignee }}</span>
                    <StatusPill :status="order.status" />
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无故障记录" />
          </div>
        </template>
        <el-empty v-else-if="!detailLoading" description="无法加载设备详情" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '../components/StatusPill.vue'
import WorkOrderCreateDialog from '../components/WorkOrderCreateDialog.vue'
import {
  createDevice, deleteDevice, getDevices, getDeviceDetail,
  getDeviceFaultHistory, getDeviceHealthScore,
  getWorkOrders, updateDevice, updateWorkOrderStatus
} from '../api'
import { usePollingTask } from '../composables/usePollingTask'
import { getPriorityMeta } from '../utils/status'
import { useAuthStore } from '../stores/auth'

const priorityLabel = (p) => getPriorityMeta(p).label
const auth = useAuthStore()

// v6.2 改造：OPERATOR 失去"维修工单模块的编辑权限"，不能点"确认处理/已修复"按钮
const canEditWorkOrder = computed(() => !['OPERATOR'].includes(auth.user?.role))
// v6.2 改造：新建工单对话框状态（OPERATOR 也能用）
const createOrderOpen = ref(false)
const onWorkOrderCreated = () => { loadWorkOrders() }
const departmentTypeMap = {
  '炼钢设备科': 'ARC_FURNACE',
  '公辅设备科': 'PUMP',
  '动力设备科': 'COMPRESSOR',
  '连铸设备科': 'CONTINUOUS_CASTER',
  '环保设备科': 'DUST_COLLECTOR',
  '精炼设备科': 'LADLE_FURNACE'
}
const managedDeviceType = departmentTypeMap[auth.user?.department] || ''
const isDepartmentManager = auth.user?.role === 'DEVICE_MANAGER' && Boolean(managedDeviceType)

const devices = ref([])
const workOrders = ref([])
const orderFilter = ref('')
const dialogVisible = ref(false)
const editingDevice = ref({})

const searchForm = reactive({ keyword: '', type: '', status: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDeviceId = ref(null)
const deviceDetail = ref(null)
const faultHistory = ref([])
const healthScore = ref(null)

const deviceTypeMap = {
  'ARC_FURNACE': '电弧炉',
  'PUMP': '水泵',
  'COMPRESSOR': '空压机',
  'FAN': '风机',
  'TRANSFORMER': '变压器',
  'BOILER': '锅炉',
  'GENERATOR': '发电机组',
  'LADLE_FURNACE': '钢包精炼炉',
  'CONTINUOUS_CASTER': '连铸机',
  'DUST_COLLECTOR': '除尘系统'
}

const deviceTypeLabel = (type) => deviceTypeMap[type] || type || '--'

const locationMap = {
  'Steel Workshop A': '炼钢一车间',
  'Utility Station': '公用动力站',
  'Power Station': '动力中心'
}

const locationLabel = (loc) => locationMap[loc] || loc || '--'

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
  const params = {
    page: pagination.page,
    size: pagination.size
  }
  if (searchForm.keyword) params.keyword = searchForm.keyword
  if (searchForm.type) params.type = searchForm.type
  if (searchForm.status) params.status = searchForm.status

  const result = await getDevices(params)
  devices.value = result.records || []
  pagination.total = result.total || 0
  pagination.page = result.page || pagination.page
  pagination.size = result.size || pagination.size
}

const handleSearch = () => {
  pagination.page = 1
  refreshNow()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.type = managedDeviceType
  searchForm.status = ''
  pagination.page = 1
  refreshNow()
}

const handlePageChange = (page) => {
  pagination.page = page
  refreshNow()
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  refreshNow()
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

const openDetail = async (row) => {
  currentDeviceId.value = row.id
  detailVisible.value = true
  detailLoading.value = true
  deviceDetail.value = row
  faultHistory.value = []
  healthScore.value = null

  try {
    const [detail, history, health] = await Promise.all([
      getDeviceDetail(row.id),
      getDeviceFaultHistory(row.id),
      getDeviceHealthScore(row.id)
    ])
    deviceDetail.value = detail
    faultHistory.value = history || []
    healthScore.value = health
  } catch {
    // Error toast already shown by axios interceptor
  } finally {
    detailLoading.value = false
  }
}

const healthProgressColor = (score) => {
  if (score >= 80) return '#67c23a'
  if (score >= 50) return '#e6a23c'
  return '#f56c6c'
}

const priceTierLabel = (tier) => {
  const map = {
    CRITICAL_PEAK: '尖峰', PEAK: '峰', FLAT: '平',
    VALLEY: '谷', DEEP_VALLEY: '深谷'
  }
  return map[tier] || tier || '--'
}

const orderTimelineColor = (status) => {
  const map = { PENDING: '#e6a23c', IN_PROGRESS: '#409eff', RESOLVED: '#67c23a' }
  return map[status] || '#909399'
}

onMounted(async () => {
  if (isDepartmentManager) searchForm.type = managedDeviceType
  await startPolling()
})
</script>

<style scoped>
.analysis-tools {
  display: flex;
  gap: 12px;
}

.table-panel {
  padding: 18px 20px;
  margin-bottom: 14px;
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

.search-bar {
  padding: 16px 20px;
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-body {
  min-height: 200px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 700;
  color: var(--accent-cyan);
  letter-spacing: 2px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item.detail-full {
  grid-column: 1 / -1;
}

.detail-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.detail-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metric-chip {
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.metric-chip span {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.metric-chip strong {
  font-size: 16px;
  color: var(--accent-blue);
}

.health-score-area {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.health-overall {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.health-percent {
  font-size: 28px;
  font-weight: 700;
}

.health-evaluated {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.health-subs {
  flex: 1;
  display: grid;
  gap: 14px;
  min-width: 0;
}

.sub-score {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sub-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.timeline-card {
  padding: 8px 0;
}

.timeline-card p {
  margin: 6px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

@media (max-width: 640px) {
  .health-score-area {
    flex-direction: column;
    align-items: center;
  }

  .detail-grid,
  .detail-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
