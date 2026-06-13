<template>
  <div class="layout-shell" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 全宽顶栏(全屏路由隐藏) -->
    <header v-if="!isFullscreen" class="layout-header">
      <div class="header-left">
        <div class="brand-mark">
          <div class="brand-icon">
            <span class="brand-icon-glow"></span>
          </div>
          <div class="brand-text">
            <p class="brand-eyebrow">SMART ENERGY</p>
            <h1 class="brand-name">智驭能效</h1>
          </div>
        </div>
        <div class="header-divider"></div>
        <div class="page-meta">
          <h2 class="page-meta-title">{{ pageTitle }}</h2>
          <p class="page-meta-subtitle">{{ pageSubtitle }}</p>
        </div>
      </div>

      <div class="header-center">
        <div class="system-status">
          <span class="status-dot status-dot--running"></span>
          <span class="status-label">SYSTEM ONLINE</span>
          <span class="status-divider">|</span>
          <span class="status-time">{{ clock }}</span>
          <span class="status-divider">|</span>
          <span class="status-date">{{ dateText }}</span>
        </div>
      </div>

      <div class="header-right">
        <span class="role-tag">{{ roleLabel(role) }}</span>
        <NotificationBell :alerts="alerts" @open-alert="openAlert" />
        <el-button text class="header-logout" @click="logout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出</span>
        </el-button>
      </div>
    </header>

    <!-- 主体：侧栏 + 主区 -->
    <div class="layout-body">
      <aside v-if="!isFullscreen" class="layout-sidebar industrial-panel">
        <div class="sidebar-section">
          <p class="sidebar-eyebrow">NAVIGATION</p>
          <el-menu
            :default-active="route.path"
            class="layout-menu"
            background-color="transparent"
            text-color="#cbd5f5"
            active-text-color="#5cdcff"
            @select="router.push"
          >
            <el-menu-item v-if="canViewControlRoom" index="/dashboard">
              <el-icon><Monitor /></el-icon>
              <span>监控大屏</span>
            </el-menu-item>
            <el-menu-item v-if="canViewControlRoom" index="/analysis">
              <el-icon><TrendCharts /></el-icon>
              <span>负荷分析</span>
            </el-menu-item>
            <el-menu-item v-if="canManageDevices" index="/devices">
              <el-icon><Setting /></el-icon>
              <span>设备管理</span>
            </el-menu-item>

            <el-menu-item v-if="canMaintain" index="/knowledge">
              <el-icon><Reading /></el-icon>
              <span>维修知识库</span>
            </el-menu-item>
            <el-menu-item v-if="canManageParts" index="/spare-parts">
              <el-icon><Box /></el-icon>
              <span>备件库存</span>
            </el-menu-item>
            <el-menu-item v-if="isMaintenanceEngineer" index="/maintenance/spare-parts">
              <el-icon><Box /></el-icon>
              <span>库存与申请记录</span>
            </el-menu-item>

            <el-menu-item v-if="isEngineer" index="/maintenance">
              <el-icon><Tools /></el-icon>
              <span>工单中心</span>
            </el-menu-item>

            <el-menu-item v-if="canReviewTransfers" index="/maintenance/transfer-requests">
              <el-icon><Switch /></el-icon>
              <span>转派审批</span>
            </el-menu-item>

            <el-menu-item v-if="canCreateOrders" index="/operations/orders">
              <el-icon><Tickets /></el-icon>
              <span>故障工单中心</span>
            </el-menu-item>

            <el-menu-item v-if="canSchedule" index="/scheduler">
              <el-icon><Calendar /></el-icon>
              <span>生产调度优化</span>
            </el-menu-item>

            <el-menu-item v-if="canViewAdmin" index="/admin">
              <el-icon><DataAnalysis /></el-icon>
              <span>经理决策仪表盘</span>
            </el-menu-item>

            <el-menu-item v-if="canManagePeople" index="/admin/people">
              <el-icon><UserFilled /></el-icon>
              <span>人员管理</span>
            </el-menu-item>

            <el-menu-item v-if="canDispatchMaintenance" index="/maintenance/dispatch">
              <el-icon><Tickets /></el-icon>
              <span>智能调度</span>
            </el-menu-item>

            <el-menu-item v-if="isAdmin" index="/admin/config">
              <el-icon><Operation /></el-icon>
              <span>系统配置</span>
            </el-menu-item>
            <el-menu-item v-if="isAdmin" index="/audit-log">
              <el-icon><DocumentChecked /></el-icon>
              <span>审计日志</span>
            </el-menu-item>
            <el-menu-item index="/account-settings">
              <el-icon><User /></el-icon>
              <span>账号设置</span>
            </el-menu-item>
          </el-menu>
        </div>

        <div class="sidebar-footer">
          <p class="sidebar-eyebrow">CURRENT USER</p>
          <strong class="footer-user">{{ auth.user?.username || '未登录' }}</strong>
          <p v-if="auth.user?.role" class="footer-role">{{ roleLabel(auth.user.role) }}</p>
        </div>
      </aside>

      <main class="layout-main">
        <router-view />
      </main>
    </div>

    <!-- 品牌水印(全屏路由隐藏) -->
    <div v-if="!isFullscreen" class="brand-watermark">智驭能效 · SMART ENERGY MASTER</div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, shallowRef, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Monitor, TrendCharts, Setting, Reading, Box, Tools, User, Tickets, Calendar,
  DataAnalysis, UserFilled, Operation, DocumentChecked, Switch, SwitchButton
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import NotificationBell from '../components/NotificationBell.vue'
import { getActiveAlerts } from '../api'
import { usePollingTask } from '../composables/usePollingTask'
import { defaultHomeForRole } from '../utils/role'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// ★ 全屏豁免:监控大屏类的路由完全隐藏外层 chrome,让组件独占 100vw/100vh
const FULLSCREEN_ROUTES = ['/dashboard']
const isFullscreen = computed(() => FULLSCREEN_ROUTES.includes(route.path))

const role = computed(() => auth.user?.role)
const isAdmin = computed(() => role.value === 'ADMIN')
const isEngineer = computed(() => ['MAINTENANCE_ENGINEER', 'ADMIN'].includes(role.value))
const isMaintenanceEngineer = computed(() => role.value === 'MAINTENANCE_ENGINEER')
const canMaintain = computed(() => ['MAINTENANCE_ENGINEER', 'ADMIN'].includes(role.value))
const canManageParts = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value))
const canReviewTransfers = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value))
const canViewControlRoom = computed(() => ['OPERATOR', 'MANAGER', 'ADMIN'].includes(role.value))
const canManageDevices = computed(() => ['OPERATOR', 'ADMIN'].includes(role.value))
const canCreateOrders = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value))
const canSchedule = computed(() => ['MANAGER', 'ADMIN'].includes(role.value))
const canViewAdmin = computed(() => ['MANAGER', 'ADMIN'].includes(role.value))
const canManagePeople = computed(() => ['HR_MANAGER', 'DEVICE_MANAGER', 'ADMIN'].includes(role.value))
const canDispatchMaintenance = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value))

const ROLE_LABELS = {
  ADMIN: '管理员',
  OPERATOR: '操作员',
  MAINTENANCE_ENGINEER: '维修工程师',
  DEVICE_MANAGER: '工单管理人员',
  MANAGER: '生产经理',
  HR_MANAGER: '人事管理员'
}
const roleLabel = (r) => ROLE_LABELS[r] || r

// 页面标题（来自路由 meta.title）
const pageTitle = computed(() => route.meta?.title || '工业能效指挥中心')
const pageSubtitle = computed(() => route.meta?.subtitle || 'INDUSTRIAL CONTROL')

// 时钟
const clock = ref(formatTime(new Date()))
const dateText = ref(formatDate(new Date()))
let clockTimer = null
function formatTime (d) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}
function formatDate (d) {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ${weekdays[d.getDay()]}`
}

// 告警轮询
const alerts = shallowRef([])
const loadAlerts = async () => { alerts.value = await getActiveAlerts(8) }
const { start: startAlertPolling } = usePollingTask(loadAlerts, 10000)
const openAlert = () => router.push(isEngineer.value ? '/maintenance' : canCreateOrders.value ? '/operations/orders' : defaultHomeForRole(role.value))

const logout = async () => {
  await auth.logout()
  router.push('/login')
}

const handleBeforeUnload = () => {
  if (auth.token && auth.user?.username) {
    try {
      const payload = JSON.stringify({ token: auth.token, username: auth.user.username })
      const blob = new Blob([payload], { type: 'application/json' })
      navigator.sendBeacon('/api/auth/logout', blob)
    } catch (e) {
      // 静默失败
    }
  }
}

onMounted(() => {
  startAlertPolling()
  clockTimer = setInterval(() => {
    const d = new Date()
    clock.value = formatTime(d)
    dateText.value = formatDate(d)
  }, 1000)
  window.addEventListener('beforeunload', handleBeforeUnload)
})
onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped>
.layout-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* ★★★ 全屏豁免模式 —— 让大屏类组件突破布局占满整个视口 ★★★ */
.layout-shell.is-fullscreen {
  display: block;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  padding: 0;
  margin: 0;
  overflow: hidden;
  background: #02060d;
}
.layout-shell.is-fullscreen .layout-body {
  display: block;
  grid-template-columns: none;
  padding: 0;
  gap: 0;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
}
.layout-shell.is-fullscreen .layout-main {
  width: 100vw;
  height: 100vh;
  padding: 0;
  margin: 0;
  overflow: hidden;
}

/* ===== 顶栏 ===== */
.layout-header {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1.4fr 1fr;
  align-items: center;
  height: 76px;
  padding: 0 22px;
  background:
    linear-gradient(180deg, rgba(13, 37, 64, 0.92) 0%, rgba(10, 25, 41, 0.85) 100%);
  border-bottom: 1px solid var(--panel-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45), inset 0 -1px 0 rgba(92, 220, 255, 0.08);
  z-index: 5;
}

.layout-header::before {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 8%;
  right: 8%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent-cyan), transparent);
  opacity: 0.7;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
  box-shadow: 0 0 16px rgba(92, 220, 255, 0.5);
  display: grid;
  place-items: center;
}

.brand-icon::after {
  content: '⚡';
  font-size: 20px;
  color: #fff;
  text-shadow: 0 0 8px rgba(255, 255, 255, 0.6);
}

.brand-icon-glow {
  position: absolute;
  inset: -6px;
  border-radius: 14px;
  background: radial-gradient(circle, rgba(92, 220, 255, 0.4), transparent 70%);
  z-index: -1;
  animation: brand-pulse 3s ease-in-out infinite;
}

@keyframes brand-pulse {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1;
}

.brand-eyebrow {
  margin: 0 0 4px;
  font-size: 9px;
  letter-spacing: 3px;
  color: var(--accent-cyan);
  font-weight: 600;
}

.brand-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 4px;
  background: linear-gradient(90deg, #ffffff, var(--accent-cyan));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-divider {
  width: 1px;
  height: 28px;
  background: linear-gradient(180deg, transparent, var(--panel-border-strong), transparent);
}

.page-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.page-meta-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--text-primary);
}

.page-meta-subtitle {
  margin: 4px 0 0;
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--text-muted);
}

.header-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.system-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 18px;
  border-radius: 999px;
  background: rgba(13, 37, 64, 0.6);
  border: 1px solid rgba(92, 220, 255, 0.18);
  font-size: 12px;
  letter-spacing: 2px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-green);
  box-shadow: 0 0 8px var(--accent-green);
  animation: status-blink 2s ease-in-out infinite;
}

@keyframes status-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-dot--running {
  background: var(--accent-green);
  box-shadow: 0 0 8px var(--accent-green);
}

.status-label {
  color: var(--accent-green);
  font-weight: 600;
}

.status-divider {
  color: var(--text-muted);
}

.status-time {
  font-variant-numeric: tabular-nums;
  color: var(--accent-cyan);
  font-weight: 600;
  min-width: 80px;
  text-align: center;
}

.status-date {
  color: var(--text-secondary);
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.role-tag {
  padding: 4px 12px;
  border: 1px solid rgba(92, 220, 255, 0.4);
  background: rgba(92, 220, 255, 0.12);
  color: var(--accent-cyan);
  border-radius: 999px;
  font-size: 11px;
  letter-spacing: 2px;
  font-weight: 600;
}

.header-logout {
  color: var(--text-secondary) !important;
  letter-spacing: 1px;
}

.header-logout:hover {
  color: var(--accent-red) !important;
}

/* ===== 主体 ===== */
.layout-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  padding: 16px 22px 24px;
  flex: 1;
  min-height: 0;
}

.layout-sidebar {
  display: flex;
  flex-direction: column;
  padding: 18px 12px 12px;
  height: fit-content;
  position: sticky;
  top: 16px;
}

.sidebar-section {
  flex: 1;
}

.sidebar-eyebrow {
  margin: 0 0 12px;
  padding: 0 8px;
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--text-muted);
  font-weight: 600;
}

.layout-menu {
  border-right: none !important;
}

.layout-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 4px 0;
  border-radius: 8px;
  font-size: 13px;
  letter-spacing: 1px;
  transition: all 0.2s ease;
}

.layout-menu :deep(.el-menu-item:hover) {
  background: rgba(92, 220, 255, 0.08) !important;
  color: var(--accent-cyan) !important;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(92, 220, 255, 0.18), rgba(92, 220, 255, 0.05)) !important;
  color: var(--accent-cyan) !important;
  font-weight: 600;
  border-left: 2px solid var(--accent-cyan);
}

.layout-menu :deep(.el-menu-item.is-active::after) {
  display: none;
}

.layout-main {
  min-width: 0;
  min-height: 0;
}

.sidebar-footer {
  margin-top: 16px;
  padding: 12px 8px 4px;
  border-top: 1px solid var(--panel-border-soft);
}

.footer-user {
  display: block;
  font-size: 13px;
  color: var(--text-primary);
  letter-spacing: 1px;
  margin-bottom: 4px;
}

.footer-role {
  margin: 0;
  font-size: 11px;
  color: var(--accent-cyan);
  letter-spacing: 2px;
}

/* ===== 响应式 ===== */
@media (max-width: 1280px) {
  .header-center {
    display: none;
  }
  .layout-header {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 1024px) {
  .layout-body {
    grid-template-columns: 1fr;
    padding: 16px;
  }
  .layout-sidebar {
    position: static;
    padding: 12px;
  }
  .layout-menu {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
  .layout-menu :deep(.el-menu-item) {
    flex: 0 0 auto;
  }
  .sidebar-footer {
    display: none;
  }
}

@media (max-width: 768px) {
  .layout-header {
    grid-template-columns: 1fr;
    height: auto;
    padding: 12px 16px;
    gap: 10px;
  }
  .header-left,
  .header-right {
    justify-content: space-between;
  }
  .page-meta-title {
    font-size: 14px;
    letter-spacing: 2px;
  }
}
</style>