<template>
  <div class="layout-shell">
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
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <span>监控大屏</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><TrendCharts /></el-icon>
          <span>负荷分析</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Setting /></el-icon>
          <span>设备管理</span>
        </el-menu-item>

        <el-menu-item index="/knowledge">
          <el-icon><Reading /></el-icon>
          <span>维修知识库</span>
        </el-menu-item>
        <el-menu-item index="/spare-parts">
          <el-icon><Box /></el-icon>
          <span>备件库存</span>
        </el-menu-item>

        <!-- ★ 新增：仅 MAINTENANCE_ENGINEER / ADMIN / MANAGER 看到 -->
        <el-menu-item
          v-if="isMaintenanceRole"
          index="/maintenance"
        >
          <el-icon><Tools /></el-icon>
          <span>维修指挥中心</span>
        </el-menu-item>

        <el-sub-menu
          v-if="isMaintenanceRole"
          index="maintenance-sub"
        >
          <template #title>
            <el-icon><User /></el-icon>
            <span>人员调度</span>
          </template>
          <el-menu-item index="/maintenance/personnel">维修人员</el-menu-item>
          <el-menu-item index="/maintenance/dispatch">智能调度</el-menu-item>
        </el-sub-menu>
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
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, TrendCharts, Setting, Reading, Box, Tools, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 🟠 重要问题 #5 修复：菜单项按角色动态显示
const isMaintenanceRole = computed(() => {
  const r = auth.user?.role
  return r === 'MAINTENANCE_ENGINEER' || r === 'ADMIN' || r === 'MANAGER'
})

const ROLE_LABELS = {
  ADMIN: '管理员',
  OPERATOR: '操作员',
  MAINTENANCE_ENGINEER: '维修工程师',
  MANAGER: '调度主管'
}
const roleLabel = (role) => ROLE_LABELS[role] || role

const logout = () => {
  auth.logout()
  router.push('/login')
}
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
