<template>
  <el-dialog v-model="visible" title="新建工单" width="560px" destroy-on-close>
    <el-form :model="form" label-width="100px" label-position="right">
      <el-form-item label="设备" required>
        <el-select v-model="form.deviceId" filterable placeholder="选择设备" style="width: 100%">
          <el-option
            v-for="d in devices"
            :key="d.id"
            :label="`${d.deviceCode} - ${d.deviceName}`"
            :value="d.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="故障类型" required>
        <el-select v-model="form.faultType" style="width: 100%">
          <el-option label="机械卡涩 MECHANICAL_JAM" value="MECHANICAL_JAM" />
          <el-option label="冷却中断 COOLING_INTERRUPT" value="COOLING_INTERRUPT" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" required>
        <el-select v-model="form.priority" style="width: 100%">
          <el-option label="高 HIGH" value="HIGH" />
          <el-option label="严重 CRITICAL" value="CRITICAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="故障现象、初步判断等" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// v6.2 改造：抽公共"新建工单"对话框组件，给 DevicesView.vue（OPERATOR）和 MaintenanceCenterView.vue（DEVICE_MANAGER 之前用）共用
// 当前：DevicesView.vue 调用（OPERATOR 角色）
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createWorkOrder } from '../api/workorder'
import { getDevices } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  presetDeviceCode: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'created'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const form = ref({
  deviceId: null,
  faultType: 'MECHANICAL_JAM',
  priority: 'HIGH',
  description: ''
})
const devices = ref([])
const submitting = ref(false)

const loadDevices = async () => {
  try {
    const result = await getDevices({ size: 500 })
    devices.value = result.records || []
    // 如果传了 presetDeviceCode，预填
    if (props.presetDeviceCode) {
      const match = devices.value.find((d) => d.deviceCode === props.presetDeviceCode)
      if (match) form.value.deviceId = match.id
    }
  } catch (e) {
    ElMessage.error('设备列表加载失败')
  }
}
onMounted(loadDevices)

const onSubmit = async () => {
  if (!form.value.deviceId) return ElMessage.warning('请选择设备')
  if (!form.value.faultType) return ElMessage.warning('请选故障类型')
  submitting.value = true
  try {
    const wo = await createWorkOrder(form.value)
    ElMessage.success('工单创建成功')
    visible.value = false
    emit('created', wo)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>
