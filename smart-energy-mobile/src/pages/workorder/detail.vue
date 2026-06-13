<template>
  <view class="detail-page">
    <!-- Custom Navigation Bar -->
    <view class="nav-bar">
      <view class="nav-left" @click="goBack">
        <text class="nav-back">‹</text>
        <text class="nav-title">工单详情</text>
      </view>
      <view class="nav-right">
        <text class="nav-order-no">{{ order.orderNo || '' }}</text>
      </view>
    </view>

    <view v-if="loading" class="loading-wrap">
      <text class="loading-text">加载中...</text>
    </view>

    <template v-else-if="order.id">
      <scroll-view scroll-y class="detail-scroll">
        <!-- Status Banner -->
        <view :class="['status-banner', statusBannerClass]">
          <text class="status-icon">{{ statusIcon }}</text>
          <view class="status-info">
            <text class="status-title">{{ order.title || '工单' }}</text>
            <view class="status-row">
              <view :class="['status-tag', statusClass]">
                <text class="status-tag-text">{{ statusLabel }}</text>
              </view>
              <view :class="['priority-tag', priorityClass]">
                <view :class="['priority-dot-sm', priorityClass]"></view>
                <text class="priority-tag-text">{{ priorityLabel }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- Device Info Card -->
        <view class="info-card">
          <text class="card-title">设备信息</text>
          <view class="info-row">
            <text class="info-label">设备名称</text>
            <text class="info-value">{{ order.deviceName || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">设备编码</text>
            <text class="info-value mono">{{ order.deviceCode || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">设备类型</text>
            <text class="info-value">{{ order.deviceType || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">所在区域</text>
            <text class="info-value">{{ order.deviceLocation || '-' }}</text>
          </view>
        </view>

        <!-- Fault Info Card -->
        <view class="info-card">
          <text class="card-title">故障信息</text>
          <view class="info-row">
            <text class="info-label">故障类型</text>
            <text class="info-value">{{ faultTypeLabel }}</text>
          </view>
          <view class="info-row block">
            <text class="info-label">故障描述</text>
            <text class="info-value desc">{{ order.description || order.faultType || '暂无描述' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">工单来源</text>
            <text class="info-value">{{ order.source === 'AUTO' ? '自动检测' : '手工报修' }}</text>
          </view>
          <view v-if="order.assigneeName" class="info-row">
            <text class="info-label">当前指派人</text>
            <text class="info-value highlight">{{ order.assigneeName }}</text>
          </view>
        </view>

        <!-- Sensor Snapshot -->
        <view v-if="hasSensorData" class="info-card">
          <text class="card-title">触发时传感器快照</text>
          <view class="sensor-grid">
            <view class="sensor-item">
              <text class="sensor-label">温度</text>
              <text class="sensor-value temp">{{ order.latestTemperature }}°C</text>
            </view>
            <view class="sensor-item">
              <text class="sensor-label">振动</text>
              <text class="sensor-value vib">{{ order.latestVibration }} mm/s</text>
            </view>
            <view class="sensor-item">
              <text class="sensor-label">压力</text>
              <text class="sensor-value pres">{{ order.latestPressure }} kPa</text>
            </view>
          </view>
        </view>

        <!-- Timeline -->
        <view class="info-card">
          <text class="card-title">时间线</text>
          <view class="timeline-item">
            <view class="timeline-dot"></view>
            <view class="timeline-content">
              <text class="timeline-label">创建时间</text>
              <text class="timeline-time">{{ formatTime(order.createdAt) }}</text>
            </view>
          </view>
          <view v-if="order.sourceTime" class="timeline-item">
            <view class="timeline-dot fault"></view>
            <view class="timeline-content">
              <text class="timeline-label">故障源时间</text>
              <text class="timeline-time">{{ formatTime(order.sourceTime) }}</text>
            </view>
          </view>
          <view v-if="order.acceptedAt" class="timeline-item">
            <view class="timeline-dot active"></view>
            <view class="timeline-content">
              <text class="timeline-label">接单时间</text>
              <text class="timeline-time">{{ formatTime(order.acceptedAt) }}</text>
            </view>
          </view>
          <view v-if="order.resolvedAt" class="timeline-item">
            <view class="timeline-dot done"></view>
            <view class="timeline-content">
              <text class="timeline-label">完成时间</text>
              <text class="timeline-time">{{ formatTime(order.resolvedAt) }}</text>
            </view>
          </view>
        </view>

        <!-- Active Assignees -->
        <view v-if="order.activeAssignments && order.activeAssignments.length" class="info-card">
          <text class="card-title">维修人员 ({{ order.activeAssignments.length }})</text>
          <view v-for="a in order.activeAssignments" :key="a.id" class="assignee-row">
            <view class="assignee-avatar" :style="{ background: a.avatarColor || '#f0a500' }">
              <text class="assignee-avatar-text">{{ (a.name || '工').charAt(0) }}</text>
            </view>
            <view class="assignee-info">
              <text class="assignee-name">{{ a.name }}</text>
              <text class="assignee-role">{{ a.role === 'PRIMARY' ? '主修' : '协助' }}</text>
            </view>
          </view>
        </view>

        <view class="safe-bottom"></view>
      </scroll-view>

      <!-- Bottom Action Bar -->
      <view class="action-bar" v-if="order.status === 'PENDING' || order.status === 'IN_PROGRESS'">
        <button
          v-if="order.status === 'PENDING'"
          class="action-btn start-btn"
          @click="handleStartRepair"
          :disabled="actionLoading"
        >
          {{ actionLoading ? '处理中...' : '接单 / 开始维修' }}
        </button>
        <button
          v-if="order.status === 'IN_PROGRESS'"
          class="action-btn finish-btn"
          @click="showFinishModal = true"
        >
          完成维修
        </button>
      </view>
    </template>

    <!-- Error State -->
    <view v-else class="empty-wrap">
      <text class="empty-icon">⚠️</text>
      <text class="empty-text">无法加载工单详情</text>
      <view class="retry-btn" @click="fetchDetail">
        <text class="retry-text">重新加载</text>
      </view>
    </view>

    <!-- Finish Repair Modal -->
    <view v-if="showFinishModal" class="modal-mask" @click="showFinishModal = false">
      <view class="modal-card" @click.stop>
        <text class="modal-title">完成维修</text>

        <view class="modal-body">
          <text class="modal-label">维修备注</text>
          <textarea
            class="modal-textarea"
            v-model="finishNote"
            placeholder="请描述维修过程和结果..."
            placeholder-style="color: #6b7280"
            :maxlength="500"
          />

          <text class="modal-label">现场照片（可选）</text>
          <view class="photo-section">
            <view
              v-for="(img, idx) in photos"
              :key="idx"
              class="photo-thumb"
              @click="previewPhoto(idx)"
            >
              <image :src="img" mode="aspectFill" class="photo-img" />
              <view class="photo-remove" @click.stop="removePhoto(idx)">✕</view>
            </view>
            <view v-if="photos.length < 3" class="photo-add" @click="choosePhoto">
              <text class="photo-add-icon">+</text>
              <text class="photo-add-text">添加照片</text>
            </view>
          </view>
        </view>

        <view class="modal-actions">
          <button class="modal-btn cancel-btn" @click="showFinishModal = false">取消</button>
          <button
            class="modal-btn confirm-btn"
            @click="handleFinishRepair"
            :disabled="finishLoading"
          >
            {{ finishLoading ? '提交中...' : '确认完成' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { get, put } from '@/utils/request'

const orderId = ref(null)
const order = ref({})
const loading = ref(true)
const actionLoading = ref(false)
const showFinishModal = ref(false)
const finishNote = ref('')
const photos = ref([])
const finishLoading = ref(false)

// --- Receive route param via onLoad ---
const onLoad = (options) => {
  orderId.value = options?.id || options?.orderId
  if (orderId.value) {
    fetchDetail()
  } else {
    loading.value = false
  }
}

// --- Fetch detail ---
const fetchDetail = () => {
  loading.value = true
  get(`/workorder/orders/${orderId.value}`)
    .then((data) => {
      order.value = data || {}
    })
    .catch(() => {
      uni.showToast({ title: '加载详情失败', icon: 'none' })
    })
    .finally(() => {
      loading.value = false
    })
}

// --- Status helpers ---
const statusBannerClass = computed(() => {
  if (order.value.status === 'PENDING') return 'banner-pending'
  if (order.value.status === 'IN_PROGRESS') return 'banner-progress'
  return 'banner-resolved'
})

const statusIcon = computed(() => {
  if (order.value.status === 'PENDING') return '⏳'
  if (order.value.status === 'IN_PROGRESS') return '🔧'
  return '✅'
})

const statusClass = computed(() => {
  if (order.value.status === 'PENDING') return 'status-pending'
  if (order.value.status === 'IN_PROGRESS') return 'status-progress'
  return 'status-resolved'
})

const statusLabel = computed(() => {
  if (order.value.status === 'PENDING') return '待处理'
  if (order.value.status === 'IN_PROGRESS') return '维修中'
  return '已解决'
})

const priorityClass = computed(() => {
  const p = order.value.priority
  if (p === 'CRITICAL') return 'priority-critical'
  if (p === 'HIGH') return 'priority-high'
  if (p === 'MEDIUM') return 'priority-medium'
  return 'priority-low'
})

const priorityLabel = computed(() => {
  const p = order.value.priority
  if (p === 'CRITICAL') return '紧急'
  if (p === 'HIGH') return '高'
  if (p === 'MEDIUM') return '中'
  if (p === 'LOW') return '低'
  return p || '中'
})

const faultTypeLabel = computed(() => {
  const m = {
    MECHANICAL_JAM: '机械卡涩',
    COOLING_INTERRUPT: '冷却中断',
    ELECTRICAL_OVERLOAD: '电气过载',
    SENSOR_DRIFT: '传感器漂移',
    BEARING_WEAR: '轴承磨损',
    INTERMITTENT_JAM: '间歇性卡涩',
  }
  return m[order.value.faultType] || order.value.faultType || '-'
})

const hasSensorData = computed(() => {
  return order.value.latestTemperature != null ||
    order.value.latestVibration != null ||
    order.value.latestPressure != null
})

// --- Actions ---
const goBack = () => {
  uni.navigateBack()
}

const handleStartRepair = () => {
  actionLoading.value = true
  put(`/work-orders/${orderId.value}/status`, { status: 'IN_PROGRESS' })
    .then(() => {
      uni.showToast({ title: '已接单，开始维修', icon: 'success' })
      fetchDetail()
    })
    .catch(() => { /* toast already shown */ })
    .finally(() => {
      actionLoading.value = false
    })
}

const handleFinishRepair = () => {
  if (!finishNote.value.trim()) {
    uni.showToast({ title: '请填写维修备注', icon: 'none' })
    return
  }
  finishLoading.value = true
  put(`/work-orders/${orderId.value}/status`, {
    status: 'RESOLVED',
    note: finishNote.value,
    photos: photos.value,
  })
    .then(() => {
      uni.showToast({ title: '维修完成！', icon: 'success' })
      showFinishModal.value = false
      fetchDetail()
    })
    .catch(() => { /* toast already shown */ })
    .finally(() => {
      finishLoading.value = false
    })
}

const choosePhoto = () => {
  uni.chooseImage({
    count: 3 - photos.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (res) => {
      photos.value = [...photos.value, ...res.tempFilePaths]
    },
  })
}

const removePhoto = (idx) => {
  photos.value.splice(idx, 1)
}

const previewPhoto = (idx) => {
  uni.previewImage({
    current: idx,
    urls: photos.value,
  })
}

const formatTime = (t) => {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${mo}-${day} ${h}:${mi}`
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: #0d1117;
  display: flex;
  flex-direction: column;
}

/* ======== Nav ======== */
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  height: 88rpx;
  background: #161b22;
  border-bottom: 1rpx solid #21262d;
}

.nav-left {
  display: flex;
  align-items: center;
}

.nav-back {
  font-size: 48rpx;
  color: #8b949e;
  margin-right: 8rpx;
  line-height: 1;
}

.nav-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #e6edf3;
}

.nav-order-no {
  font-size: 22rpx;
  color: #484f58;
  font-family: monospace;
}

/* ======== Loading / Empty ======== */
.loading-wrap,
.empty-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.loading-text {
  font-size: 28rpx;
  color: #8b949e;
}

.empty-icon { font-size: 64rpx; margin-bottom: 16rpx; }
.empty-text { font-size: 28rpx; color: #8b949e; margin-bottom: 24rpx; }

.retry-btn {
  background: rgba(240, 165, 0, 0.15);
  border: 1rpx solid rgba(240, 165, 0, 0.3);
  padding: 14rpx 36rpx;
  border-radius: 12rpx;
}
.retry-text { font-size: 26rpx; color: #f0a500; }

/* ======== Scroll ======== */
.detail-scroll {
  flex: 1;
  padding-bottom: 140rpx;
}

/* ======== Status Banner ======== */
.status-banner {
  display: flex;
  align-items: center;
  margin: 24rpx 32rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  border: 1rpx solid #21262d;
}

.banner-pending { background: linear-gradient(135deg, #1a1a1f, #161b22); border-color: rgba(240, 165, 0, 0.3); }
.banner-progress { background: linear-gradient(135deg, #1a1f2b, #161b22); border-color: rgba(46, 196, 182, 0.3); }
.banner-resolved { background: linear-gradient(135deg, #1a1f1a, #161b22); border-color: rgba(46, 196, 182, 0.2); }

.status-icon {
  font-size: 48rpx;
  margin-right: 20rpx;
}

.status-info {
  flex: 1;
}

.status-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #e6edf3;
  display: block;
  margin-bottom: 12rpx;
}

.status-row {
  display: flex;
  gap: 12rpx;
}

.status-tag {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
}
.status-pending { background: rgba(240, 165, 0, 0.15); border: 1rpx solid rgba(240, 165, 0, 0.3); }
.status-progress { background: rgba(46, 196, 182, 0.15); border: 1rpx solid rgba(46, 196, 182, 0.3); }
.status-resolved { background: rgba(46, 196, 182, 0.08); border: 1rpx solid rgba(46, 196, 182, 0.15); }

.status-tag-text {
  font-size: 22rpx;
  font-weight: 600;
  color: #e6edf3;
}

.priority-tag {
  display: flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
}
.priority-critical { background: rgba(230, 57, 70, 0.15); border: 1rpx solid rgba(230, 57, 70, 0.3); }
.priority-high { background: rgba(240, 165, 0, 0.15); border: 1rpx solid rgba(240, 165, 0, 0.3); }
.priority-medium { background: rgba(255, 159, 67, 0.1); border: 1rpx solid rgba(255, 159, 67, 0.25); }
.priority-low { background: rgba(139, 148, 158, 0.1); border: 1rpx solid rgba(139, 148, 158, 0.2); }

.priority-dot-sm {
  width: 10rpx; height: 10rpx; border-radius: 50%; margin-right: 8rpx;
}
.priority-dot-sm.priority-critical { background: #e63946; }
.priority-dot-sm.priority-high { background: #f0a500; }
.priority-dot-sm.priority-medium { background: #ff9f43; }
.priority-dot-sm.priority-low { background: #8b949e; }

.priority-tag-text {
  font-size: 22rpx;
  font-weight: 600;
  color: #e6edf3;
}

/* ======== Info Cards ======== */
.info-card {
  background: #161b22;
  border: 1rpx solid #21262d;
  border-radius: 20rpx;
  margin: 0 32rpx 20rpx;
  padding: 28rpx;
}

.card-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #f0a500;
  margin-bottom: 20rpx;
  display: block;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #21262d;
}
.info-row:last-child { border-bottom: none; }
.info-row.block { flex-direction: column; }

.info-label {
  font-size: 24rpx;
  color: #8b949e;
  flex-shrink: 0;
  margin-right: 24rpx;
}

.info-value {
  font-size: 26rpx;
  color: #c9d1d9;
  text-align: right;
  word-break: break-all;
}
.info-value.mono { font-family: monospace; }
.info-value.desc { text-align: left; margin-top: 8rpx; line-height: 40rpx; color: #9ba3af; }
.info-value.highlight { color: #2ec4b6; }

/* ======== Sensor Grid ======== */
.sensor-grid {
  display: flex;
  gap: 16rpx;
}

.sensor-item {
  flex: 1;
  background: #0d1117;
  border-radius: 14rpx;
  padding: 20rpx 12rpx;
  text-align: center;
}

.sensor-label {
  display: block;
  font-size: 22rpx;
  color: #6b7280;
  margin-bottom: 8rpx;
}

.sensor-value {
  font-size: 26rpx;
  font-weight: 700;
}
.sensor-value.temp { color: #e63946; }
.sensor-value.vib { color: #f0a500; }
.sensor-value.pres { color: #2ec4b6; }

/* ======== Timeline ======== */
.timeline-item {
  display: flex;
  align-items: flex-start;
  padding: 12rpx 0;
}
.timeline-item:last-child { padding-bottom: 0; }

.timeline-dot {
  width: 16rpx; height: 16rpx;
  border-radius: 50%;
  background: #484f58;
  margin-right: 16rpx;
  margin-top: 6rpx;
  flex-shrink: 0;
}
.timeline-dot.fault { background: #e63946; }
.timeline-dot.active { background: #2ec4b6; }
.timeline-dot.done { background: #f0a500; }

.timeline-content {
  flex: 1;
}
.timeline-label {
  display: block;
  font-size: 24rpx;
  color: #8b949e;
}
.timeline-time {
  font-size: 22rpx;
  color: #484f58;
  font-family: monospace;
}

/* ======== Assignees ======== */
.assignee-row {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #21262d;
}
.assignee-row:last-child { border-bottom: none; }

.assignee-avatar {
  width: 56rpx; height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16rpx;
}
.assignee-avatar-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
}

.assignee-info {
  flex: 1;
}
.assignee-name {
  display: block;
  font-size: 26rpx;
  color: #c9d1d9;
}
.assignee-role {
  font-size: 22rpx;
  color: #8b949e;
}

/* ======== Bottom Action Bar ======== */
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 32rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #161b22;
  border-top: 1rpx solid #21262d;
}

.action-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 16rpx;
  border: none;
  font-size: 32rpx;
  font-weight: 700;
  color: #fff;
}

.start-btn {
  background: linear-gradient(135deg, #2ec4b6, #1a9e92);
}
.finish-btn {
  background: linear-gradient(135deg, #f0a500, #d48500);
  color: #0d1117;
}

.action-btn[disabled] {
  opacity: 0.6;
}

/* ======== Modal ======== */
.modal-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  z-index: 999;
}

.modal-card {
  width: 100%;
  max-width: 600rpx;
  background: #1c2128;
  border: 1rpx solid #21262d;
  border-radius: 24rpx;
  padding: 36rpx;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #e6edf3;
  margin-bottom: 28rpx;
  display: block;
}

.modal-body {
  margin-bottom: 28rpx;
}

.modal-label {
  font-size: 26rpx;
  color: #8b949e;
  margin-bottom: 12rpx;
  display: block;
}

.modal-textarea {
  width: 100%;
  height: 180rpx;
  background: #0d1117;
  border: 1rpx solid #21262d;
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 26rpx;
  color: #c9d1d9;
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

/* ======== Photo Upload ======== */
.photo-section {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.photo-thumb {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  overflow: hidden;
}

.photo-img {
  width: 100%;
  height: 100%;
}

.photo-remove {
  position: absolute;
  top: 4rpx; right: 4rpx;
  width: 36rpx; height: 36rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.7);
  color: #e63946;
  font-size: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.photo-add {
  width: 160rpx; height: 160rpx;
  border: 2rpx dashed #30363d;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.photo-add-icon {
  font-size: 48rpx;
  color: #6b7280;
  line-height: 1;
}
.photo-add-text {
  font-size: 20rpx;
  color: #6b7280;
  margin-top: 4rpx;
}

/* ======== Modal Actions ======== */
.modal-actions {
  display: flex;
  gap: 20rpx;
}

.modal-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 14rpx;
  border: none;
  font-size: 28rpx;
  font-weight: 700;
}

.cancel-btn {
  background: #21262d;
  color: #8b949e;
}
.confirm-btn {
  background: linear-gradient(135deg, #2ec4b6, #1a9e92);
  color: #fff;
}
.confirm-btn[disabled] {
  opacity: 0.6;
}

/* ======== Safe Bottom ======== */
.safe-bottom {
  height: 40rpx;
}
</style>
