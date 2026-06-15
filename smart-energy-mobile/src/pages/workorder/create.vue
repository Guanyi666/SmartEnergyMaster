<template>
  <view class="create-page">
    <!-- Nav -->
    <view class="nav-bar">
      <view class="nav-left" @click="goBack">
        <text class="nav-back">‹</text>
        <text class="nav-title">手工报修</text>
      </view>
    </view>

    <scroll-view scroll-y class="create-scroll" v-if="!loadingDevices">
      <!-- Device Picker -->
      <view class="card">
        <text class="card-title">设备选择</text>
        <picker :value="deviceIndex" :range="deviceLabels" @change="onDeviceChange">
          <view class="picker-display">
            <text :class="['picker-text', { placeholder: !form.deviceId }]">
              {{ form.deviceId ? selectedDeviceLabel : '请选择设备' }}
            </text>
            <text class="picker-arrow">▾</text>
          </view>
        </picker>
      </view>

      <!-- Fault Info -->
      <view class="card">
        <text class="card-title">故障信息</text>

        <view class="form-item">
          <text class="form-label">报修标题</text>
          <input class="form-input" v-model="form.title" placeholder="例如：巡检发现轴承异响" placeholder-style="color: #484f58" maxlength="128" />
        </view>

        <view class="form-item">
          <text class="form-label">故障类型</text>
          <picker :value="faultTypeIndex" :range="faultTypeLabels" @change="onFaultTypeChange">
            <view class="picker-display">
              <text :class="['picker-text', { placeholder: !form.faultType }]">
                {{ form.faultType ? faultTypeLabels[faultTypeIndex] : '请选择故障类型' }}
              </text>
              <text class="picker-arrow">▾</text>
            </view>
          </picker>
        </view>

        <view class="form-item">
          <text class="form-label">优先级</text>
          <view class="priority-options">
            <view
              v-for="p in priorityOptions"
              :key="p.value"
              :class="['priority-opt', { active: form.priority === p.value, ['pri-' + p.value.toLowerCase()]: true }]"
              @click="form.priority = p.value"
            >
              <text class="priority-opt-text">{{ p.label }}</text>
            </view>
          </view>
        </view>

        <view class="form-item">
          <text class="form-label">故障描述</text>
          <textarea class="form-textarea" v-model="form.description" placeholder="请详细描述故障现象..." placeholder-style="color: #484f58" :maxlength="500" />
          <text class="char-count">{{ form.description.length }}/500</text>
        </view>
      </view>

      <!-- Submit -->
      <button class="submit-btn" @click="handleSubmit" :disabled="submitting">
        {{ submitting ? '提交中...' : '提交报修' }}
      </button>

      <view class="safe-bottom"></view>
    </scroll-view>

    <view v-else class="loading-wrap">
      <text class="loading-text">加载设备列表...</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { get, post } from '@/utils/request'

// --- Form ---
const form = ref({
  deviceId: null,
  title: '',
  faultType: '',
  priority: 'MEDIUM',
  description: '',
})

const submitting = ref(false)
const devices = ref([])
const deviceIndex = ref(-1)
const loadingDevices = ref(true)

// --- Fault types ---
const faultTypes = [
  { value: 'MECHANICAL_JAM', label: '机械卡涩' },
  { value: 'COOLING_INTERRUPT', label: '冷却中断' },
  { value: 'ELECTRICAL_OVERLOAD', label: '电气过载' },
  { value: 'SENSOR_DRIFT', label: '传感器漂移' },
  { value: 'BEARING_WEAR', label: '轴承磨损' },
  { value: 'INTERMITTENT_JAM', label: '间歇性卡涩' },
  { value: 'OTHER', label: '其他' },
]
const faultTypeLabels = faultTypes.map(t => t.label)
const faultTypeIndex = computed(() => {
  const idx = faultTypes.findIndex(t => t.value === form.value.faultType)
  return idx >= 0 ? idx : -1
})

// --- Priority ---
const priorityOptions = [
  { value: 'CRITICAL', label: '紧急' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
]

// --- Device ---
const deviceLabels = computed(() => devices.value.map(d => d.deviceCode + ' ' + d.deviceName))
const selectedDeviceLabel = computed(() => {
  const d = devices.value[deviceIndex.value]
  return d ? d.deviceCode + ' ' + d.deviceName : ''
})

const fetchDevices = () => {
  loadingDevices.value = true
  get('/devices', { size: 100 })
    .then((data) => {
      devices.value = data?.records || data || []
    })
    .catch(() => {
      uni.showToast({ title: '加载设备列表失败', icon: 'none' })
    })
    .finally(() => { loadingDevices.value = false })
}

const onDeviceChange = (e) => {
  deviceIndex.value = e.detail.value
  const d = devices.value[e.detail.value]
  if (d) form.value.deviceId = d.id
}

const onFaultTypeChange = (e) => {
  const ft = faultTypes[e.detail.value]
  if (ft) form.value.faultType = ft.value
}

// --- Submit ---
const handleSubmit = () => {
  if (!form.value.deviceId) { uni.showToast({ title: '请选择设备', icon: 'none' }); return }
  if (!form.value.title.trim()) { uni.showToast({ title: '请输入报修标题', icon: 'none' }); return }
  if (!form.value.faultType) { uni.showToast({ title: '请选择故障类型', icon: 'none' }); return }
  if (!form.value.description.trim()) { uni.showToast({ title: '请输入故障描述', icon: 'none' }); return }

  submitting.value = true
  post('/work-orders', {
    deviceId: form.value.deviceId,
    title: form.value.title.trim(),
    faultType: form.value.faultType,
    priority: form.value.priority,
    description: form.value.description.trim(),
  })
    .then(() => {
      uni.showToast({ title: '报修成功', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 800)
    })
    .catch(() => {})
    .finally(() => { submitting.value = false })
}

const goBack = () => { uni.navigateBack() }

fetchDevices()
</script>

<style scoped>
.create-page { min-height: 100vh; background: #0d1117; }

/* Nav */
.nav-bar { display: flex; align-items: center; padding: 0 24rpx; height: 88rpx; background: #161b22; border-bottom: 1rpx solid #21262d; }
.nav-left { display: flex; align-items: center; }
.nav-back { font-size: 48rpx; color: #8b949e; margin-right: 8rpx; line-height: 1; }
.nav-title { font-size: 30rpx; font-weight: 600; color: #e6edf3; }

/* Scroll */
.create-scroll { padding: 24rpx 32rpx; }

/* Card */
.card { background: #161b22; border: 1rpx solid #21262d; border-radius: 20rpx; padding: 28rpx; margin-bottom: 24rpx; }
.card-title { font-size: 26rpx; font-weight: 700; color: #f0a500; display: block; margin-bottom: 20rpx; }

/* Form */
.form-item { margin-bottom: 20rpx; }
.form-item:last-child { margin-bottom: 0; }
.form-label { font-size: 24rpx; color: #8b949e; display: block; margin-bottom: 10rpx; }
.form-input { width: 100%; height: 80rpx; background: #0d1117; border: 1rpx solid #21262d; border-radius: 12rpx; padding: 0 20rpx; font-size: 26rpx; color: #e6edf3; box-sizing: border-box; }
.form-textarea { width: 100%; height: 200rpx; background: #0d1117; border: 1rpx solid #21262d; border-radius: 12rpx; padding: 20rpx; font-size: 26rpx; color: #e6edf3; box-sizing: border-box; }
.char-count { font-size: 22rpx; color: #484f58; text-align: right; display: block; margin-top: 8rpx; }

/* Picker */
.picker-display { display: flex; align-items: center; justify-content: space-between; background: #0d1117; border: 1rpx solid #21262d; border-radius: 12rpx; padding: 0 20rpx; height: 80rpx; }
.picker-text { font-size: 26rpx; color: #e6edf3; }
.picker-text.placeholder { color: #484f58; }
.picker-arrow { font-size: 28rpx; color: #6b7280; }

/* Priority */
.priority-options { display: flex; gap: 16rpx; }
.priority-opt { flex: 1; text-align: center; padding: 16rpx 8rpx; border-radius: 12rpx; background: #0d1117; border: 2rpx solid #21262d; transition: border-color 0.2s; }
.priority-opt.active.pri-critical { border-color: #e63946; background: rgba(230,57,70,0.1); }
.priority-opt.active.pri-high { border-color: #f0a500; background: rgba(240,165,0,0.1); }
.priority-opt.active.pri-medium { border-color: #ff9f43; background: rgba(255,159,67,0.1); }
.priority-opt.active.pri-low { border-color: #8b949e; background: rgba(139,148,158,0.1); }
.priority-opt-text { font-size: 24rpx; font-weight: 600; color: #8b949e; }
.priority-opt.active .priority-opt-text { color: #e6edf3; }

/* Submit */
.submit-btn { width: 100%; height: 88rpx; line-height: 88rpx; background: linear-gradient(135deg,#f0a500,#d48500); color: #0d1117; font-size: 32rpx; font-weight: 700; border-radius: 16rpx; border: none; }
.submit-btn[disabled] { opacity: 0.6; }

/* Loading */
.loading-wrap { flex: 1; display: flex; align-items: center; justify-content: center; padding-top: 200rpx; }
.loading-text { font-size: 28rpx; color: #8b949e; }

.safe-bottom { height: 60rpx; }
</style>
