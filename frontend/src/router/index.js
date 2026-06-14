import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '../stores/auth'
import { defaultHomeForRole, normalizeRole } from '../utils/role'
import { readStoredSession } from '../utils/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true, title: '系统登录' }
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          redirect: () => defaultHomeForRole(readStoredSession().user?.role)
        },
        {
          path: '/dashboard',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
          meta: { roles: ['OPERATOR', 'MANAGER', 'ADMIN'], title: '监控指挥中心', subtitle: 'REAL-TIME MONITORING' }
        },
        {
          path: '/scheduler',
          name: 'scheduler',
          component: () => import('../views/SchedulerView.vue'),
          meta: { roles: ['MANAGER', 'ADMIN'], title: '生产调度优化', subtitle: 'PRODUCTION SCHEDULING' }
        },
        {
          path: '/analysis',
          name: 'analysis',
          component: () => import('../views/AnalysisView.vue'),
          meta: { roles: ['OPERATOR', 'DEVICE_MANAGER', 'MANAGER', 'HR_MANAGER', 'ADMIN'], title: '负荷分析', subtitle: 'LOAD ANALYSIS' }
        },
        {
          path: '/devices',
          name: 'devices',
          component: () => import('../views/DevicesView.vue'),
          meta: { roles: ['OPERATOR', 'ADMIN'], title: '设备管理', subtitle: 'DEVICE MANAGEMENT' }
        },
        // Epic 07：维修知识库 & 备件管理
        {
          path: '/knowledge',
          name: 'knowledge',
          component: () => import('../views/KnowledgeView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'], title: '维修知识库', subtitle: 'MAINTENANCE KNOWLEDGE' }
        },
        {
          path: '/spare-parts',
          name: 'spare-parts',
          component: () => import('../views/SparePartsView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'], title: '备件库存', subtitle: 'SPARE PARTS' }
        },
        {
          path: '/maintenance/spare-parts',
          name: 'maintenance-spare-parts',
          component: () => import('../views/EngineerSparePartsView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'], title: '库存与申请记录', subtitle: 'ENGINEER INVENTORY' }
        },
        {
          path: '/maintenance/transfer-requests',
          name: 'transfer-requests',
          component: () => import('../views/TransferRequestsView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'], title: '转派审批', subtitle: 'TRANSFER REVIEW' }
        },
        // Epic 05 维修模块（按需加载，含 meta.roles 角色拦截）
        {
          path: '/maintenance',
          name: 'maintenance',
          component: () => import('../views/MaintenanceView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'], title: '工单中心', subtitle: 'WORK ORDER CENTER' }
        },
        {
          path: '/operations/orders',
          name: 'operations-orders',
          component: () => import('../views/MaintenanceCenterView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'], title: '故障工单中心', subtitle: 'FAULT ORDER CENTER' }
        },
        {
          path: '/admin/people',
          name: 'people',
          component: () => import('../views/PeopleManagementView.vue'),
          meta: { roles: ['HR_MANAGER', 'DEVICE_MANAGER', 'ADMIN'], title: '人员管理', subtitle: 'PEOPLE MANAGEMENT' }
        },
        {
          path: '/maintenance/dispatch',
          name: 'maintenance-dispatch',
          component: () => import('../views/DispatchView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'], title: '智能调度', subtitle: 'INTELLIGENT DISPATCH' }
        },
        {
          path: '/maintenance/orders/:id',
          name: 'maintenance-order-detail',
          component: () => import('../views/WorkOrderDetailView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'], hideInMenu: true, title: '工单详情', subtitle: 'ORDER DETAIL' }
        },
        {
          path: '/admin',
          name: 'admin',
          component: () => import('../views/AdminView.vue'),
          meta: { roles: ['MANAGER', 'ADMIN'], title: '经理决策仪表盘', subtitle: 'MANAGER COCKPIT' }
        },
        {
          path: '/admin/users',
          redirect: '/admin/people'
        },
        {
          path: '/maintenance/personnel',
          redirect: '/admin/people'
        },
        {
          path: '/admin/config',
          name: 'config',
          component: () => import('../views/ConfigView.vue'),
          meta: { roles: ['ADMIN'], title: '系统配置', subtitle: 'SYSTEM CONFIG' }
        },
        {
          path: '/audit-log',
          name: 'audit-log',
          component: () => import('../views/AuditLogView.vue'),
          meta: { roles: ['ADMIN'], title: '审计日志', subtitle: 'AUDIT LOG' }
        },
        {
          path: '/account-settings',
          name: 'account-settings',
          component: () => import('../views/AccountSettingsView.vue'),
          meta: { title: '账号设置', subtitle: 'ACCOUNT SETTINGS' }
        }
      ]
    }
  ]
})

// ★ 关键修复：现有守卫只检查 !to.meta.public && !token，没有角色拦截
// 🟠 重要问题 #4 修复：完整重写守卫加 meta.roles 检查
router.beforeEach((to) => {
  const authStore = useAuthStore()
  const storedSession = readStoredSession()
  const token = authStore.token || storedSession.token
  const rawUser = authStore.user || storedSession.user
  const user = rawUser ? { ...rawUser, role: normalizeRole(rawUser.role) } : rawUser

  // 1. 未登录
  if (!to.meta.public && !token) return '/login'

  // 2. 已登录访问 /login：根据角色选默认落地页
  if (to.path === '/login' && token) {
    return defaultHomeForRole(user?.role)
  }

  // 3. 角色拦截
  const required = to.meta.roles
  if (Array.isArray(required) && required.length > 0) {
    if (!user) return '/login'
    if (!required.includes(user.role)) {
      // 未授权：回该用户主落地页
      return defaultHomeForRole(user.role)
    }
  }
})

export default router
