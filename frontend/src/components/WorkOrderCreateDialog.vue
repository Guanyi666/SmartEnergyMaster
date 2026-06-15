<template>
  <el-dialog v-model="visible" title="新建工单" width="640px" destroy-on-close append-to-body @closed="resetForm">
    <el-form :model="form" label-width="90px">
      <el-form-item label="设备" required>
        <el-select v-model="form.deviceId" filterable placeholder="选择设备" style="width: 100%" @change="onDeviceChange">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceName} (${d.deviceCode})`" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="故障类型" required>
        <el-select v-model="form.faultType" placeholder="选择故障类型" style="width: 100%">
          <el-option v-for="(meta, key) in faultTypeOptions" :key="key" :label="`${meta.emoji} ${meta.label}`" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" required>
        <el-select v-model="form.priority" style="width: 100%">
          <el-option v-for="(meta, key) in priorityOptions" :key="key" :label="meta.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="如：巡检发现轴承异响" />
      </el-form-item>
      <el-form-item label="故障描述" required>
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit
                  placeholder="描述故障现象、影响范围、初步判断" />
      </el-form-item>
      <el-form-item label="当前快照">
        <div v-if="sensorSnapshot" class="snapshot-row">
          <span>🌡 {{ formatVal(sensorSnapshot.temperature) }} ℃</span>
          <span>压力 {{ formatVal(sensorSnapshot.pressure) }} 千帕</span>
          <span>振动 {{ formatVal(sensorSnapshot.vibration) }} 毫米/秒</span>
          <span class="snapshot-time">{{ formatTime(sensorSnapshot.time) }}</span>
        </div>
        <span v-else class="snapshot-muted">选择设备后加载实时传感器快照</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="submitting" @click="onSubmit">创建工单</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createWorkOrder } from '../api/workorder'
import { getDevices, getLatestSensor } from '../api'
import { faultTypeMeta, priorityMeta } from '../utils/status'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  presetDeviceCode: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'created'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const faultTypeOptions = faultTypeMeta
const priorityOptions = priorityMeta

const form = ref({ deviceId: null, faultType: '', priority: 'MEDIUM', title: '', description: '' })
const devices = ref([])
const sensorSnapshot = ref(null)
const submitting = ref(false)

const loadDevices = async () => {
  try {
    const result = await getDevices({ page: 1, size: 500 })
    devices.value = result.records || []
    if (props.presetDeviceCode) {
      const match = devices.value.find(d => d.deviceCode === props.presetDeviceCode)
      if (match) { form.value.deviceId = match.id; onDeviceChange(match.id) }
    }
  } catch { /* http.js 已 toast */ }
}
onMounted(loadDevices)

const onDeviceChange = async (deviceId) => {
  sensorSnapshot.value = null
  if (!deviceId) return
  const device = devices.value.find(d => d.id === deviceId)
  if (!device) return
  try {
    const data = await getLatestSensor(device.deviceCode)
    sensorSnapshot.value = data
  } catch { sensorSnapshot.value = null }
}

const formatVal = (v) => (v == null ? '—' : Number(v).toFixed(1))
const formatTime = (iso) => iso ? new Date(iso).toLocaleString('zh-CN', { hour12: false }) : ''

const resetForm = () => {
  form.value = { deviceId: null, faultType: '', priority: 'MEDIUM', title: '', description: '' }
  sensorSnapshot.value = null
}

const onSubmit = async () => {
  if (submitting.value) return
  const f = form.value
  if (!f.deviceId || !f.faultType || !f.priority || !f.title.trim() || !f.description.trim()) {
    ElMessage.warning('请填写所有必填项')
    return
  }
  submitting.value = true
  try {
    const wo = await createWorkOrder({ deviceId: f.deviceId, title: f.title.trim(), faultType: f.faultType, priority: f.priority, description: f.description.trim() })
    ElMessage.success(`工单 ${wo.orderNo} 已创建`)
    visible.value = false
    emit('created', wo)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.snapshot-row { display: flex; gap: 16px; flex-wrap: wrap; font-size: 13px; color: var(--text-primary); }
.snapshot-row span { white-space: nowrap; }
.snapshot-time { color: var(--text-secondary); margin-left: auto; }
.snapshot-muted { color: var(--text-secondary); font-size: 13px; }
</style>
