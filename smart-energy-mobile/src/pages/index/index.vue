<template>
  <view class="workbench">
    <!-- Custom Navigation Bar -->
    <view class="nav-bar">
      <view class="nav-left">
        <text class="nav-icon">⚡</text>
        <text class="nav-title">智驭能效 · 维修工作台</text>
      </view>
      <view class="nav-right" @click="handleLogout">
        <text class="logout-icon">⏻</text>
      </view>
    </view>

    <!-- Header: Worker Info -->
    <view class="header-section">
      <view class="worker-info">
        <view class="avatar">
          <text class="avatar-text">{{ userInfo.name?.charAt(0) || '工' }}</text>
        </view>
        <view class="worker-detail">
          <text class="greeting">欢迎，{{ userInfo.name || '张工' }}</text>
          <text class="role-tag">{{ userInfo.roleName || '运维工程师' }}</text>
        </view>
      </view>
      <view class="status-badge">
        <view class="status-dot"></view>
        <text class="status-text">值班中</text>
      </view>
    </view>

    <!-- Date / Time -->
    <view class="date-row">
      <text class="date-text">{{ currentDate }}</text>
    </view>

    <!-- Action Grid -->
    <view class="action-grid">
      <view class="action-card scan-card" @click="handleScanCode">
        <view class="action-icon-wrap">
          <text class="action-icon">📷</text>
        </view>
        <text class="action-label">扫码巡检</text>
        <text class="action-desc">扫描设备二维码</text>
      </view>
      <view class="action-card report-card" @click="handleCreateOrder">
        <view class="action-icon-wrap">
          <text class="action-icon">🔧</text>
        </view>
        <text class="action-label">手工报修</text>
        <text class="action-desc">提交故障维修工单</text>
      </view>
    </view>

    <!-- Pending Orders Summary -->
    <view class="summary-card">
      <view class="summary-left">
        <text class="summary-number">{{ pendingOrders.length }}</text>
        <text class="summary-label">待处理工单</text>
      </view>
      <view class="summary-divider"></view>
      <view class="summary-right">
        <view class="priority-row">
          <view class="priority-dot critical"></view>
          <text class="priority-text">紧急 {{ criticalCount }}</text>
        </view>
        <view class="priority-row">
          <view class="priority-dot high"></view>
          <text class="priority-text">高 {{ highCount }}</text>
        </view>
        <view class="priority-row">
          <view class="priority-dot medium"></view>
          <text class="priority-text">中 {{ mediumCount }}</text>
        </view>
      </view>
    </view>

    <!-- Pending Orders List -->
    <view class="section-title">待处理工单列表</view>

    <view v-if="ordersLoading" class="loading-row">
      <text class="loading-text">加载中...</text>
    </view>

    <view v-else class="order-list">
      <view
        v-for="order in pendingOrders"
        :key="order.id"
        class="order-card"
        @click="handleViewOrder(order)"
      >
        <view class="order-header">
          <text class="order-no">{{ order.orderNo }}</text>
          <view :class="['priority-badge', priorityClass(order.priority)]">
            <view :class="['badge-dot', priorityClass(order.priority)]"></view>
            <text class="badge-text">{{ priorityLabel(order.priority) }}</text>
          </view>
        </view>
        <view class="order-body">
          <text class="device-name">{{ order.deviceName }}</text>
          <text class="fault-type">{{ order.faultType }}</text>
        </view>
        <view class="order-footer">
          <text class="order-time">{{ order.createdAt }}</text>
          <view class="view-detail">
            <text class="detail-text">查看详情</text>
            <text class="detail-arrow">›</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Empty State -->
    <view v-if="pendingOrders.length === 0" class="empty-state">
      <text class="empty-icon">✅</text>
      <text class="empty-text">暂无待处理工单</text>
    </view>

    <!-- Safe Bottom -->
    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { get } from '@/utils/request'

// --- User Info ---
const userInfo = ref({ name: '张工', roleName: '运维工程师' })

onMounted(() => {
  try {
    const stored = uni.getStorageSync('userInfo')
    if (stored) {
      userInfo.value = typeof stored === 'string' ? JSON.parse(stored) : stored
    }
  } catch (_) { /* use default */ }

  fetchOrders()
})

// --- Pull to Refresh ---
const onPullDownRefresh = () => {
  fetchOrders().finally(() => {
    uni.stopPullDownRefresh()
  })
}

// --- Date ---
const currentDate = computed(() => {
  const d = new Date()
  const weekMap = ['日', '一', '二', '三', '四', '五', '六']
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const w = weekMap[d.getDay()]
  return `${y}年${m}月${day}日 星期${w}`
})

// --- Work Orders ---
const pendingOrders = ref([])
const ordersLoading = ref(false)

const fetchOrders = () => {
  ordersLoading.value = true
  return get('/work-orders/active-alerts', { limit: 20 })
    .then((data) => {
      pendingOrders.value = (data || []).map((o) => ({
        id: o.id,
        orderNo: o.orderNo,
        deviceName: o.deviceName || o.deviceCode || '未知设备',
        faultType: o.title || o.faultType || '未指定故障',
        priority: o.priority || 'MEDIUM',
        createdAt: formatTime(o.createdAt),
      }))
    })
    .catch(() => {
      // use mock data as fallback when backend unreachable
      pendingOrders.value = [
        {
          id: 1,
          orderNo: 'WO-20240612-001',
          deviceName: '电弧炉 EAF-01',
          faultType: '机械卡涩 — 振动异常超标',
          priority: 'CRITICAL',
          createdAt: '2024-06-12 08:35',
        },
        {
          id: 2,
          orderNo: 'WO-20240612-002',
          deviceName: '循环水泵 CWP-02',
          faultType: '冷却中断 — 温度过高 / 压力过低',
          priority: 'CRITICAL',
          createdAt: '2024-06-12 09:12',
        },
        {
          id: 3,
          orderNo: 'WO-20240611-015',
          deviceName: '空压机 ACP-01',
          faultType: '轴承磨损 — 振动持续偏高',
          priority: 'HIGH',
          createdAt: '2024-06-11 23:45',
        },
        {
          id: 4,
          orderNo: 'WO-20240611-012',
          deviceName: '电弧炉 EAF-01',
          faultType: '炉膛温度传感器读数间歇波动',
          priority: 'MEDIUM',
          createdAt: '2024-06-11 18:20',
        },
      ]
    })
    .finally(() => {
      ordersLoading.value = false
    })
}

const formatTime = (t) => {
  if (!t) return ''
  // handles both ISO string and array format from backend
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

// --- Priority Computed ---
const criticalCount = computed(() => pendingOrders.value.filter(o => o.priority === 'CRITICAL').length)
const highCount = computed(() => pendingOrders.value.filter(o => o.priority === 'HIGH').length)
const mediumCount = computed(() => pendingOrders.value.filter(o => o.priority === 'MEDIUM' || o.priority === 'LOW').length)

const priorityClass = (p) => {
  if (p === 'CRITICAL') return 'critical'
  if (p === 'HIGH') return 'high'
  if (p === 'MEDIUM') return 'medium'
  return 'medium'
}

const priorityLabel = (p) => {
  if (p === 'CRITICAL') return '紧急'
  if (p === 'HIGH') return '高'
  if (p === 'MEDIUM') return '中'
  if (p === 'LOW') return '低'
  return '中'
}

// --- Actions ---
const handleScanCode = () => {
  // #ifdef APP-PLUS
  uni.scanCode({
    scanType: ['barCode', 'qrCode'],
    success: (res) => {
      uni.showToast({ title: `设备: ${res.result}`, icon: 'success' })
    },
    fail: () => {
      uni.showToast({ title: '扫码取消', icon: 'none' })
    },
  })
  // #endif
  // #ifdef H5
  uni.showToast({ title: '设备: EAF-01 (H5模拟)', icon: 'success' })
  // #endif
}

const handleCreateOrder = () => {
  uni.showToast({ title: '跳转到报修页面...', icon: 'none' })
}

const handleViewOrder = (order) => {
  uni.navigateTo({ url: `/pages/workorder/detail?id=${order.id}` })
}

const handleLogout = () => {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        uni.reLaunch({ url: '/pages/login/login' })
      }
    },
  })
}
</script>

<style scoped>
/* ======== Theme Variables ======== */
.workbench {
  min-height: 100vh;
  background: #0d1117;
  padding-bottom: env(safe-area-inset-bottom);
}

/* ======== Custom Nav ======== */
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  height: 88rpx;
  background: #161b22;
  border-bottom: 1rpx solid #21262d;
}

.nav-left {
  display: flex;
  align-items: center;
}

.nav-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
}

.nav-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #e6edf3;
}

.nav-right {
  padding: 8rpx;
}

.logout-icon {
  font-size: 36rpx;
  color: #8b949e;
}

/* ======== Header ======== */
.header-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 32rpx 0;
}

.worker-info {
  display: flex;
  align-items: center;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #f0a500, #e63946);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.avatar-text {
  font-size: 36rpx;
  font-weight: 700;
  color: #fff;
}

.greeting {
  font-size: 34rpx;
  font-weight: 600;
  color: #e6edf3;
  display: block;
  margin-bottom: 8rpx;
}

.role-tag {
  font-size: 24rpx;
  color: #8b949e;
  background: #21262d;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
}

.status-badge {
  display: flex;
  align-items: center;
  background: rgba(46, 196, 182, 0.12);
  border: 1rpx solid rgba(46, 196, 182, 0.3);
  padding: 12rpx 24rpx;
  border-radius: 32rpx;
}

.status-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #2ec4b6;
  box-shadow: 0 0 12rpx rgba(46, 196, 182, 0.6);
  margin-right: 12rpx;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-text {
  font-size: 26rpx;
  color: #2ec4b6;
  font-weight: 600;
}

/* ======== Date Row ======== */
.date-row {
  padding: 16rpx 32rpx 24rpx;
}

.date-text {
  font-size: 24rpx;
  color: #484f58;
}

/* ======== Action Grid ======== */
.action-grid {
  display: flex;
  gap: 24rpx;
  padding: 0 32rpx 24rpx;
}

.action-card {
  flex: 1;
  background: #161b22;
  border: 1rpx solid #21262d;
  border-radius: 20rpx;
  padding: 36rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: border-color 0.3s, transform 0.15s;
}

.action-card:active {
  transform: scale(0.97);
}

.scan-card:active {
  border-color: #f0a500;
}

.report-card:active {
  border-color: #2ec4b6;
}

.action-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  background: rgba(240, 165, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20rpx;
}

.action-icon {
  font-size: 40rpx;
}

.action-label {
  font-size: 28rpx;
  font-weight: 700;
  color: #e6edf3;
  margin-bottom: 8rpx;
}

.action-desc {
  font-size: 22rpx;
  color: #6b7280;
}

/* ======== Summary Card ======== */
.summary-card {
  display: flex;
  align-items: center;
  margin: 0 32rpx 24rpx;
  padding: 36rpx;
  background: linear-gradient(135deg, #161b22, #1a1f2b);
  border: 1rpx solid #21262d;
  border-radius: 20rpx;
}

.summary-left {
  flex: 1;
}

.summary-number {
  font-size: 72rpx;
  font-weight: 800;
  color: #f0a500;
  text-shadow: 0 0 32rpx rgba(240, 165, 0, 0.4);
  line-height: 80rpx;
  display: block;
}

.summary-label {
  font-size: 26rpx;
  color: #8b949e;
}

.summary-divider {
  width: 2rpx;
  height: 80rpx;
  background: #21262d;
  margin: 0 32rpx;
}

.summary-right {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.priority-row {
  display: flex;
  align-items: center;
}

.priority-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  margin-right: 12rpx;
}

.priority-dot.critical { background: #e63946; box-shadow: 0 0 8rpx rgba(230, 57, 70, 0.5); }
.priority-dot.high { background: #f0a500; box-shadow: 0 0 8rpx rgba(240, 165, 0, 0.5); }
.priority-dot.medium { background: #ff9f43; }

.priority-text {
  font-size: 24rpx;
  color: #c9d1d9;
}

/* ======== Section Title ======== */
.section-title {
  padding: 0 32rpx 16rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #e6edf3;
}

/* ======== Loading ======== */
.loading-row {
  display: flex;
  justify-content: center;
  padding: 40rpx 0;
}

.loading-text {
  font-size: 26rpx;
  color: #8b949e;
}

/* ======== Order List ======== */
.order-list {
  padding: 0 32rpx;
}

.order-card {
  background: #161b22;
  border: 1rpx solid #21262d;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  transition: border-color 0.2s;
}

.order-card:active {
  border-color: #f0a500;
}

.order-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.order-no {
  font-size: 26rpx;
  font-weight: 600;
  color: #c9d1d9;
  font-family: monospace;
}

.priority-badge {
  display: flex;
  align-items: center;
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  background: rgba(230, 57, 70, 0.1);
  border: 1rpx solid transparent;
}

.priority-badge.critical {
  background: rgba(230, 57, 70, 0.12);
  border-color: rgba(230, 57, 70, 0.3);
}

.priority-badge.high {
  background: rgba(240, 165, 0, 0.12);
  border-color: rgba(240, 165, 0, 0.3);
}

.priority-badge.medium {
  background: rgba(255, 159, 67, 0.1);
  border-color: rgba(255, 159, 67, 0.25);
}

.badge-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  margin-right: 8rpx;
}

.badge-dot.critical { background: #e63946; }
.badge-dot.high { background: #f0a500; }
.badge-dot.medium { background: #ff9f43; }

.badge-text {
  font-size: 22rpx;
  font-weight: 600;
  color: #e6edf3;
}

.order-body {
  margin-bottom: 16rpx;
}

.device-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #e6edf3;
  display: block;
  margin-bottom: 8rpx;
}

.fault-type {
  font-size: 24rpx;
  color: #8b949e;
}

.order-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-time {
  font-size: 22rpx;
  color: #484f58;
}

.view-detail {
  display: flex;
  align-items: center;
}

.detail-text {
  font-size: 24rpx;
  color: #f0a500;
  margin-right: 4rpx;
}

.detail-arrow {
  font-size: 28rpx;
  color: #f0a500;
  font-weight: 700;
}

/* ======== Empty State ======== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0;
}

.empty-icon {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #484f58;
}

/* ======== Safe Bottom ======== */
.safe-bottom {
  height: 40rpx;
}
</style>
