<template>
  <view class="workbench">
    <!-- Custom Navigation Bar -->
    <view class="nav-bar">
      <view class="nav-left">
        <text class="nav-icon">⚡</text>
        <text class="nav-title">智驭能效 · 维修工作台</text>
      </view>
      <view class="nav-right">
        <view class="notify-bell" @click="handleBellTap">
          <text class="bell-icon">🔔</text>
          <view v-if="unreadCount > 0" class="bell-badge">
            <text class="bell-badge-text">{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
          </view>
        </view>
        <view class="nav-icon-btn" @click="goSettings">
          <text class="settings-icon">⚙</text>
        </view>
        <view class="nav-icon-btn" @click="handleLogout">
          <text class="logout-icon">⏻</text>
        </view>
      </view>
    </view>

    <!-- Header: Worker Info -->
    <view class="header-section">
      <view class="worker-info">
        <view class="avatar">
          <text class="avatar-text">{{ userInfo.name?.charAt(0) || '工' }}</text>
        </view>
        <view class="worker-detail">
          <text class="greeting">欢迎，{{ userInfo.nickname || userInfo.username || '张工' }}</text>
          <text class="role-tag">{{ roleDisplayName }}</text>
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

    <!-- Tab Switcher -->
    <view class="tab-bar">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-item', { active: activeTab === tab.key }]"
        @click="switchTab(tab.key)"
      >
        <text class="tab-text">{{ tab.label }}</text>
        <view v-if="activeTab === tab.key" class="tab-underline"></view>
      </view>
    </view>

    <!-- Summary Card -->
    <view class="summary-card">
      <view class="summary-left">
        <text class="summary-number">{{ displayOrders.length }}</text>
        <text class="summary-label">{{ currentTabLabel }}</text>
      </view>
      <view class="summary-divider"></view>
      <view class="summary-right">
        <view class="priority-row">
          <view class="priority-dot critical"></view>
          <text class="priority-text">紧急 {{ tabCriticalCount }}</text>
        </view>
        <view class="priority-row">
          <view class="priority-dot high"></view>
          <text class="priority-text">高 {{ tabHighCount }}</text>
        </view>
        <view class="priority-row">
          <view class="priority-dot medium"></view>
          <text class="priority-text">中 {{ tabMediumCount }}</text>
        </view>
      </view>
    </view>

    <!-- Orders List -->
    <view v-if="ordersLoading" class="loading-row">
      <text class="loading-text">加载中...</text>
    </view>

    <view v-else class="order-list">
      <view
        v-for="order in displayOrders"
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
    <view v-if="!ordersLoading && displayOrders.length === 0" class="empty-state">
      <text class="empty-icon">{{ emptyIcon }}</text>
      <text class="empty-text">{{ emptyText }}</text>
    </view>

    <!-- Safe Bottom -->
    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onPullDownRefresh, onShow, onHide } from '@dcloudio/uni-app'
import { get, post } from '@/utils/request'

// --- User Info ---
const userInfo = ref({ nickname: '张工', username: '', role: 'MAINTENANCE_ENGINEER' })

const roleDisplayName = computed(() => {
  const r = userInfo.value.role || ''
  if (/ADMIN/i.test(r)) return '系统管理员'
  if (/MANAGER/i.test(r)) return '设备经理'
  if (/OPERATOR/i.test(r)) return '操作员'
  if (/MAINTENANCE/i.test(r)) return '运维工程师'
  return '工作人员'
})

onMounted(() => {
  try {
    const stored = uni.getStorageSync('userInfo')
    if (stored) {
      const parsed = typeof stored === 'string' ? JSON.parse(stored) : stored
      if (parsed && typeof parsed === 'object') {
        userInfo.value = parsed
      }
    }
  } catch (_) { /* use default */ }

  fetchOrders()
  startNotificationPolling()
})

// --- Auto-refresh on return (Task 1) ---
onShow(() => {
  fetchOrders()
})

// --- Cleanup on leave ---
let notifyTimer = null
onHide(() => {
  if (notifyTimer) {
    clearInterval(notifyTimer)
    notifyTimer = null
  }
})

// --- Notification Polling (Task 2) ---
const unreadCount = ref(0)
const previousPendingIds = ref(new Set())

const startNotificationPolling = () => {
  if (notifyTimer) clearInterval(notifyTimer)
  // Initial fetch
  fetchPendingAlerts()
  // Poll every 10 seconds (matches Web MainLayout notification polling)
  notifyTimer = setInterval(fetchPendingAlerts, 10000)
}

const fetchPendingAlerts = () => {
  get('/work-orders/active-alerts', { limit: 20 })
    .then((data) => {
      const pendingOnly = (data || []).filter(o => o.status === 'PENDING')
      const newIds = new Set(pendingOnly.map(o => o.id))
      unreadCount.value = pendingOnly.length

      // Detect new orders: trigger vibration
      if (previousPendingIds.value.size > 0) {
        const hasNew = pendingOnly.some(o => !previousPendingIds.value.has(o.id))
        if (hasNew) {
          console.log('[notify] New PENDING order detected, vibrating')
          try { uni.vibrateLong() } catch (_) { /* vibration may not be supported */ }
        }
      }
      previousPendingIds.value = newIds
    })
    .catch(() => { /* silent background failure */ })
}

const handleBellTap = () => {
  // Switch to PENDING tab to show the new orders
  activeTab.value = 'pending'
}

// --- Pull to Refresh ---
onPullDownRefresh(() => {
  fetchOrders().finally(() => {
    uni.stopPullDownRefresh()
  })
})

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

// --- Tabs ---
const activeTab = ref('pending')
const tabs = [
  { key: 'pending', label: '待处理' },
  { key: 'in_progress', label: '执行中' },
  { key: 'history', label: '历史' },
]

const currentTabLabel = computed(() => {
  const t = tabs.find(t => t.key === activeTab.value)
  return t ? t.label + '工单' : '工单'
})

const emptyIcon = computed(() => {
  if (activeTab.value === 'pending') return '📋'
  if (activeTab.value === 'in_progress') return '🔧'
  return '📁'
})

const emptyText = computed(() => {
  if (activeTab.value === 'pending') return '暂无待处理工单'
  if (activeTab.value === 'in_progress') return '暂无执行中工单'
  return '暂无历史工单'
})

const switchTab = (key) => {
  activeTab.value = key
}

// --- Get worker identity ---
const getStoredUserInfo = () => {
  try {
    const stored = uni.getStorageSync('userInfo')
    return typeof stored === 'string' ? JSON.parse(stored) : (stored || {})
  } catch (_) { return {} }
}

const getStoredUsername = () => getStoredUserInfo().username || ''
const getStoredNickname = () => getStoredUserInfo().nickname || getStoredUserInfo().name || getStoredUserInfo().username || ''

// --- Work Orders ---
const allOrders = ref([])   // raw data from API
const ordersLoading = ref(false)

const fetchOrders = () => {
  ordersLoading.value = true
  return Promise.all([
    get('/workorder/orders', { status: 'PENDING', pageNum: 1, pageSize: 50 }),
    get('/workorder/orders', { status: 'IN_PROGRESS', pageNum: 1, pageSize: 50 }),
    get('/workorder/orders', { status: 'RESOLVED', pageNum: 1, pageSize: 50 }),
  ])
    .then(([pending, inProgress, resolved]) => {
      const mapOrder = (o) => ({
        id: o.id,
        orderNo: o.orderNo,
        deviceName: o.deviceName || o.deviceCode || '未知设备',
        deviceType: o.deviceType || '',
        faultType: o.title || o.faultType || '未指定故障',
        priority: o.priority || 'MEDIUM',
        status: o.status || 'PENDING',
        assigneeName: o.assigneeName || o.assignee || '',
        assigneeId: o.assigneeId || null,
        activeAssignments: o.activeAssignments || [],
        createdAt: formatTime(o.createdAt),
      })
      const pList = (pending?.records || pending || []).map(mapOrder)
      const iList = (inProgress?.records || inProgress || []).map(mapOrder)
      const rList = (resolved?.records || resolved || []).map(mapOrder)
      allOrders.value = [...pList, ...iList, ...rList]
    })
    .catch(() => {
      allOrders.value = []
    })
    .finally(() => {
      ordersLoading.value = false
    })
}

// --- Display orders filtered by tab + worker ---
const displayOrders = computed(() => {
  const username = getStoredUsername()
  const nickname = getStoredNickname()
  return allOrders.value.filter((o) => {
    if (activeTab.value === 'pending') return o.status === 'PENDING'
    if (activeTab.value === 'in_progress') return o.status === 'IN_PROGRESS' && matchesWorker(o, username, nickname)
    if (activeTab.value === 'history') return o.status === 'RESOLVED' && matchesWorker(o, username, nickname)
    return false
  })
})

const matchesWorker = (order, storedUsername, storedNickname) => {
  if (!storedUsername && !storedNickname) return false
  // Primary: match by employeeNo in activeAssignments (reliable, from same table as login username)
  if (storedUsername && order.activeAssignments?.length) {
    const hasMatch = order.activeAssignments.some(a => a.employeeNo === storedUsername)
    if (hasMatch) return true
  }
  // Fallback: match by assigneeName against stored nickname (may differ across tables)
  if (storedNickname && order.assigneeName && order.assigneeName === storedNickname) return true
  // Final fallback: match by assignee name against stored username
  if (storedUsername && order.assigneeName && order.assigneeName === storedUsername) return true
  return false
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

// --- Tab-aware priority counts ---
const tabCriticalCount = computed(() => displayOrders.value.filter(o => o.priority === 'CRITICAL').length)
const tabHighCount = computed(() => displayOrders.value.filter(o => o.priority === 'HIGH').length)
const tabMediumCount = computed(() => displayOrders.value.filter(o => o.priority === 'MEDIUM' || o.priority === 'LOW').length)

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
  try {
    uni.scanCode({
      scanType: ['barCode', 'qrCode'],
      success: (res) => {
        uni.showToast({ title: `设备: ${res.result}`, icon: 'success' })
      },
      fail: () => {
        uni.showToast({ title: '扫码取消', icon: 'none' })
      },
    })
  } catch (_) {
    // Bug 2 fix: graceful fallback when scanCode is unavailable (H5, desktop, etc.)
    uni.showToast({ title: '当前环境不支持摄像头扫码', icon: 'none' })
  }
}

const handleCreateOrder = () => {
  uni.showToast({ title: '报修页面开发中', icon: 'none' })
}

const handleViewOrder = (order) => {
  const navUrl = '/pages/workorder/detail?id=' + order.id
  console.log('[index] navigating to:', navUrl, 'order.id:', order.id, 'order.orderNo:', order.orderNo)
  uni.navigateTo({ url: navUrl })
}

const goSettings = () => {
  uni.navigateTo({ url: '/pages/profile/settings' })
}

const handleLogout = () => {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) {
        // Bug 1 fix: call backend logout before clearing local storage
        post('/auth/logout', {}).catch(() => { /* best-effort */ })
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
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx;
}

.nav-icon-btn {
  padding: 8rpx;
}

.settings-icon {
  font-size: 32rpx;
  color: #8b949e;
}

.logout-icon {
  font-size: 36rpx;
  color: #8b949e;
}

/* Notification Bell */
.notify-bell {
  position: relative;
  padding: 8rpx;
}
.bell-icon {
  font-size: 34rpx;
}
.bell-badge {
  position: absolute;
  top: 0;
  right: -2rpx;
  min-width: 32rpx;
  height: 32rpx;
  border-radius: 16rpx;
  background: #e63946;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 6rpx;
}
.bell-badge-text {
  font-size: 18rpx;
  color: #fff;
  font-weight: 700;
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

/* ======== Tab Bar ======== */
.tab-bar {
  display: flex;
  padding: 0 32rpx;
  background: #161b22;
  border-bottom: 1rpx solid #21262d;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 20rpx 0 14rpx;
  position: relative;
}

.tab-text {
  font-size: 28rpx;
  color: #8b949e;
  font-weight: 600;
}

.tab-item.active .tab-text {
  color: #f0a500;
}

.tab-underline {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: #f0a500;
  border-radius: 2rpx;
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
