import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import AnalysisView from '../views/AnalysisView.vue'
import DevicesView from '../views/DevicesView.vue'
// ★ 新增 Epic 05 维修模块 4 个视图
import MaintenanceCenterView from '../views/MaintenanceCenterView.vue'
import PersonnelView from '../views/PersonnelView.vue'
import DispatchView from '../views/DispatchView.vue'
import WorkOrderDetailView from '../views/WorkOrderDetailView.vue'
import { useAuthStore } from '../stores/auth'   // ★ 守卫需要拿角色

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
          redirect: '/dashboard'
        },
        {
          path: '/dashboard',
          name: 'dashboard',
          component: DashboardView
        },
        {
          path: '/analysis',
          name: 'analysis',
          component: AnalysisView
        },
        {
          path: '/devices',
          name: 'devices',
          component: DevicesView
        },
        // ★ 新增 4 条路由（带 meta.roles 用于角色拦截）
        {
          path: '/maintenance',
          name: 'maintenance',
          component: MaintenanceCenterView,
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'] }
        },
        {
          path: '/maintenance/personnel',
          name: 'maintenance-personnel',
          component: PersonnelView,
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN', 'MANAGER'] }
        },
        {
          path: '/maintenance/dispatch',
          name: 'maintenance-dispatch',
          component: DispatchView,
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN', 'MANAGER'] }
        },
        {
          path: '/maintenance/orders/:id',
          name: 'maintenance-order-detail',
          component: WorkOrderDetailView,
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'], hideInMenu: true }
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
  const user = authStore.user || JSON.parse(localStorage.getItem('smart-energy-user') || 'null')

  // 1. 未登录
  if (!to.meta.public && !token) return '/login'

  // 2. 已登录访问 /login：根据角色选默认落地页
  if (to.path === '/login' && token) {
    return user?.role === 'MAINTENANCE_ENGINEER' ? '/maintenance' : '/dashboard'
  }

  // 3. 角色拦截
  const required = to.meta.roles
  if (Array.isArray(required) && required.length > 0) {
    if (!user) return '/login'
    if (!required.includes(user.role)) {
      // 未授权：回该用户主落地页
      return user.role === 'MAINTENANCE_ENGINEER' ? '/maintenance' : '/dashboard'
    }
  }
})

export default router
