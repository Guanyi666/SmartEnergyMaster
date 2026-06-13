<template>
  <div class="layout-shell" :class="{ 'dashboard-mode': route.path === '/dashboard' }">
    <aside class="layout-sidebar glass-panel">
      <div class="brand-block">
        <p class="brand-mark">智能能源</p>
        <h1>智驭能效</h1>
        <span>工业能效中控平台</span>
      </div>

      <el-menu
        :default-active="route.path"
        class="layout-menu"
        background-color="transparent"
        text-color="#cbd5f5"
        active-text-color="#52c8ff"
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

        <!-- v6 改造：人员管理合并（账号 + 维修人员），旧 /admin/users 和 /maintenance/personnel 都跳此路由 -->
        <el-menu-item v-if="canManagePeople" index="/admin/people">
          <el-icon><UserFilled /></el-icon>
          <span>人员管理</span>
        </el-menu-item>

        <!-- v6 改造：人员调度只剩"智能调度"（维修人员名单已合并到 /admin/people） -->
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

      <div class="sidebar-footer">
        <div>
          <p class="muted footer-label">当前账号</p>
          <strong>{{ auth.user?.username || '未登录' }}</strong>
          <p v-if="auth.user?.role" class="role-tag">{{ roleLabel(auth.user.role) }}</p>
        </div>
        <el-button text type="danger" @click="logout">退出</el-button>
      </div>
    </aside>

    <main class="layout-main">
      <div class="layout-topbar">
        <span>{{ currentRoleIntro }}</span>
        <NotificationBell :alerts="alerts" @open-alert="openAlert" />
      </div>
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, TrendCharts, Setting, Reading, Box, Tools, User, Tickets, Calendar, DataAnalysis, UserFilled, Operation, DocumentChecked, Switch } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import NotificationBell from '../components/NotificationBell.vue'
import { getActiveAlerts } from '../api'
import { usePollingTask } from '../composables/usePollingTask'
import { defaultHomeForRole } from '../utils/role'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 🟠 重要问题 #5 修复：菜单项按角色动态显示
const role = computed(() => auth.user?.role)
const isAdmin = computed(() => role.value === 'ADMIN')
const isEngineer = computed(() => ['MAINTENANCE_ENGINEER', 'ADMIN'].includes(role.value))
const isMaintenanceEngineer = computed(() => role.value === 'MAINTENANCE_ENGINEER')
const canMaintain = computed(() => ['MAINTENANCE_ENGINEER', 'ADMIN'].includes(role.value)) // v6.2 移除 DEVICE_MANAGER
const canManageParts = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value))
const canReviewTransfers = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value))
const canViewControlRoom = computed(() => ['OPERATOR', 'MANAGER', 'ADMIN'].includes(role.value)) // v6.2 移除 DEVICE_MANAGER
const canManageDevices = computed(() => ['OPERATOR', 'ADMIN'].includes(role.value)) // v6.2 移除 DEVICE_MANAGER
const canCreateOrders = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value)) // v6.2 移除 OPERATOR
const canSchedule = computed(() => ['MANAGER', 'ADMIN'].includes(role.value)) // v6.2 移除 OPERATOR
const canViewAdmin = computed(() => ['MANAGER', 'ADMIN'].includes(role.value))
const canManageUsers = computed(() => ['HR_MANAGER', 'ADMIN'].includes(role.value))   // 旧：人员身份管理菜单
const canManagePeople = computed(() => ['HR_MANAGER', 'DEVICE_MANAGER', 'ADMIN'].includes(role.value))   // v6.2: 移除 MANAGER
const canDispatchMaintenance = computed(() => ['DEVICE_MANAGER', 'ADMIN'].includes(role.value)) // v6.2 移除 MANAGER
const alerts = shallowRef([])
const loadAlerts = async () => { alerts.value = await getActiveAlerts(8) }
const { start: startAlertPolling } = usePollingTask(loadAlerts, 10000)
const openAlert = () => router.push(isEngineer.value ? '/maintenance' : canCreateOrders.value ? '/operations/orders' : defaultHomeForRole(role.value))

const ROLE_LABELS = {
  ADMIN: '管理员',
  OPERATOR: '操作员',
  MAINTENANCE_ENGINEER: '维修工程师',
  DEVICE_MANAGER: '工单管理人员',
  MANAGER: '生产经理',
  HR_MANAGER: '人事管理员'
}
const roleLabel = (role) => ROLE_LABELS[role] || role
const currentRoleIntro = computed(() => `${roleLabel(role.value)}专属视图`)

const logout = async () => {
  await auth.logout()
  router.push('/login')
}

// v5 改造：监听 beforeunload，关闭页面/刷新/关闭 tab 时自动 logout
// 用 sendBeacon 而不是 fetch：sendBeacon 不会阻塞页面关闭，请求能可靠发出
// 注意：sendBeacon 不能设 Authorization header，所以后端 logout 同时支持从 body 取 token+username
const handleBeforeUnload = () => {
  if (auth.token && auth.user?.username) {
    try {
      const payload = JSON.stringify({ token: auth.token, username: auth.user.username })
      const blob = new Blob([payload], { type: 'application/json' })
      navigator.sendBeacon('/api/auth/logout', blob)
    } catch (e) {
      // 静默失败：关闭页面时不打扰用户
    }
  }
}

onMounted(() => {
  startAlertPolling()
  window.addEventListener('beforeunload', handleBeforeUnload)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped>
.layout-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: 100vh;
  gap: 20px;
  padding: 18px;
}

.layout-sidebar {
  padding: 24px 18px;
  display: flex;
  flex-direction: column;
}

.brand-block {
  padding: 10px 12px 20px;
}

.brand-block h1 {
  margin: 8px 0 4px;
  font-size: 30px;
  letter-spacing: 1px;
}

.brand-block span,
.brand-mark,
.footer-label {
  color: var(--text-secondary);
}

.brand-mark {
  margin: 0;
  font-size: 12px;
  letter-spacing: 3px;
}

.layout-menu {
  flex: 1;
  background: transparent;
}

.layout-main {
  min-width: 0;
}

.dashboard-mode {
  display: block;
  padding: 0;
}

.dashboard-mode .layout-sidebar,
.dashboard-mode .layout-topbar {
  display: none;
}

.layout-topbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  min-height: 40px;
  padding: 0 24px;
  color: var(--text-secondary);
  font-size: 12px;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 12px 8px;
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}

.role-tag {
  display: inline-block;
  margin: 4px 0 0;
  padding: 2px 8px;
  background: rgba(82, 200, 255, 0.16);
  border: 1px solid rgba(82, 200, 255, 0.32);
  border-radius: 10px;
  color: #52c8ff;
  font-size: 11px;
  letter-spacing: 1px;
}

@media (max-width: 1024px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-sidebar {
    gap: 12px;
  }

  .layout-menu {
    display: flex;
    flex-wrap: wrap;
  }
}
</style>
