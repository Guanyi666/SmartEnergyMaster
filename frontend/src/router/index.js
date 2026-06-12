import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '../stores/auth'
import { defaultHomeForRole, normalizeRole } from '../utils/role'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true }
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          redirect: () => defaultHomeForRole(JSON.parse(localStorage.getItem('smart-energy-user') || 'null')?.role)
        },
        {
          path: '/dashboard',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
          meta: { roles: ['OPERATOR', 'MANAGER', 'ADMIN'] } // v6.2 移除 DEVICE_MANAGER
        },
        {
          path: '/scheduler',
          name: 'scheduler',
          component: () => import('../views/SchedulerView.vue'),
          meta: { roles: ['MANAGER', 'ADMIN'] } // v6.2
        },
        {
          path: '/analysis',
          name: 'analysis',
          component: () => import('../views/AnalysisView.vue'),
          meta: { roles: ['OPERATOR', 'DEVICE_MANAGER', 'MANAGER', 'HR_MANAGER', 'ADMIN'] } // v6.2 加 HR_MANAGER
        },
        {
          path: '/devices',
          name: 'devices',
          component: () => import('../views/DevicesView.vue'),
          meta: { roles: ['OPERATOR', 'ADMIN'] } // v6.2 移除 DEVICE_MANAGER
        },
        // Epic 07：维修知识库 & 备件管理
        {
          path: '/knowledge',
          name: 'knowledge',
          component: () => import('../views/KnowledgeView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'] } // v6.2 移除 DEVICE_MANAGER
        },
        {
          path: '/spare-parts',
          name: 'spare-parts',
          component: () => import('../views/SparePartsView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'] }
        },
        {
          path: '/maintenance/spare-parts',
          name: 'maintenance-spare-parts',
          component: () => import('../views/EngineerSparePartsView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'] }
        },
        {
          path: '/maintenance/transfer-requests',
          name: 'transfer-requests',
          component: () => import('../views/TransferRequestsView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'] }
        },
        // Epic 05 维修模块（按需加载，含 meta.roles 角色拦截）
        {
          path: '/maintenance',
          name: 'maintenance',
          component: () => import('../views/MaintenanceView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'] }
        },
        {
          path: '/operations/orders',
          name: 'operations-orders',
          component: () => import('../views/MaintenanceCenterView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'] } // v6.2 移除 OPERATOR（OPERATOR 在 /devices 页面新建工单）
        },
        {
          // v6 改造：人员管理合并到统一路由（兼容旧 /admin/users 和 /maintenance/personnel）
          path: '/admin/people',
          name: 'people',
          component: () => import('../views/PeopleManagementView.vue'),
          meta: { roles: ['HR_MANAGER', 'DEVICE_MANAGER', 'ADMIN'] } // v6.2 移除 MANAGER
        },
        {
          path: '/maintenance/dispatch',
          name: 'maintenance-dispatch',
          component: () => import('../views/DispatchView.vue'),
          meta: { roles: ['DEVICE_MANAGER', 'ADMIN'] } // v6.2 移除 MANAGER
        },
        {
          path: '/maintenance/orders/:id',
          name: 'maintenance-order-detail',
          component: () => import('../views/WorkOrderDetailView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'], hideInMenu: true }
        },
        {
          path: '/admin',
          name: 'admin',
          component: () => import('../views/AdminView.vue'),
          meta: { roles: ['MANAGER', 'ADMIN'] }
        },
        {
          // v6 改造：保留旧 URL 兼容，都跳到新页面
          path: '/admin/users',
          redirect: '/admin/people'
        },
        {
          // v6 改造：保留旧 URL 兼容
          path: '/maintenance/personnel',
          redirect: '/admin/people'
        },
        {
          path: '/admin/config',
          name: 'config',
          component: () => import('../views/ConfigView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: '/audit-log',
          name: 'audit-log',
          component: () => import('../views/AuditLogView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: '/account-settings',
          name: 'account-settings',
          component: () => import('../views/AccountSettingsView.vue')
        }
      ]
    }
  ]
})

// ★ 关键修复：现有守卫只检查 !to.meta.public && !token，没有角色拦截
// 🟠 重要问题 #4 修复：完整重写守卫加 meta.roles 检查
router.beforeEach((to) => {
  const authStore = useAuthStore()
  const token = authStore.token || localStorage.getItem('smart-energy-token')
  const rawUser = authStore.user || JSON.parse(localStorage.getItem('smart-energy-user') || 'null')
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
