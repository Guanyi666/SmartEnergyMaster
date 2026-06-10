import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '../stores/auth'

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
          component: () => import('../views/DashboardView.vue')
        },
        {
          path: '/analysis',
          name: 'analysis',
          component: () => import('../views/AnalysisView.vue')
        },
        {
          path: '/devices',
          name: 'devices',
          component: () => import('../views/DevicesView.vue')
        },
        // Epic 07：维修知识库 & 备件管理
        {
          path: '/knowledge',
          name: 'knowledge',
          component: () => import('../views/KnowledgeView.vue')
        },
        {
          path: '/spare-parts',
          name: 'spare-parts',
          component: () => import('../views/SparePartsView.vue')
        },
        // Epic 05 维修模块（按需加载，含 meta.roles 角色拦截）
        {
          path: '/maintenance',
          name: 'maintenance',
          component: () => import('../views/MaintenanceCenterView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN'] }
        },
        {
          path: '/maintenance/personnel',
          name: 'maintenance-personnel',
          component: () => import('../views/PersonnelView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN', 'MANAGER'] }
        },
        {
          path: '/maintenance/dispatch',
          name: 'maintenance-dispatch',
          component: () => import('../views/DispatchView.vue'),
          meta: { roles: ['MAINTENANCE_ENGINEER', 'ADMIN', 'MANAGER'] }
        },
        {
          path: '/maintenance/orders/:id',
          name: 'maintenance-order-detail',
          component: () => import('../views/WorkOrderDetailView.vue'),
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
