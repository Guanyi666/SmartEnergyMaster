<template>
  <header class="hud-header">
    <!-- ─── 左:≡ 更多功能下拉 + LOGO ─── -->
    <div class="hh-left">
      <div class="hh-more-wrap" ref="moreWrapRef">
        <button
          class="hh-more"
          :class="{ 'is-open': dropdownOpen }"
          @click.stop="toggleDropdown"
        >
          <span class="hh-more__bars">
            <span></span><span></span><span></span>
          </span>
          <span class="hh-more__text">更多功能</span>
        </button>

        <!-- 毛玻璃下拉菜单 -->
        <Transition name="hh-dd">
          <div v-if="dropdownOpen" class="hh-dropdown">
            <span class="hh-dd-corner hh-dd-corner--tl"></span>
            <span class="hh-dd-corner hh-dd-corner--tr"></span>
            <span class="hh-dd-corner hh-dd-corner--bl"></span>
            <span class="hh-dd-corner hh-dd-corner--br"></span>

            <header class="hh-dd-head">
              <span class="hh-dd-head__title">系统功能 / SYSTEM FUNCTIONS</span>
              <button class="hh-dd-close" @click="closeDropdown">×</button>
            </header>

            <div class="hh-dd-grid">
              <section v-for="g in accessibleGroups" :key="g.title" class="hh-dd-group">
                <h4 class="hh-dd-group__title">
                  <span class="hh-dd-group__mark"></span>
                  {{ g.title }}
                </h4>
                <ul class="hh-dd-group__list">
                  <li
                    v-for="i in g.items"
                    :key="i.path"
                    class="hh-dd-item"
                    :class="{ 'is-active': route.path === i.path }"
                    @click="navAndClose(i)"
                  >
                    <span class="hh-dd-item__icon">{{ i.icon }}</span>
                    <div class="hh-dd-item__text">
                      <strong>{{ i.label }}</strong>
                      <em>{{ i.en }}</em>
                    </div>
                  </li>
                </ul>
              </section>
            </div>

            <footer class="hh-dd-foot">
              <div class="hh-dd-user">
                <span class="hh-dd-avatar">{{ userInitial }}</span>
                <div class="hh-dd-user__text">
                  <strong>{{ username }}</strong>
                  <em>{{ roleLabel(role) }}</em>
                </div>
              </div>
              <div class="hh-dd-foot__actions">
                <button class="hh-dd-btn" @click="goAccount">账号设置</button>
                <button class="hh-dd-btn hh-dd-btn--danger" @click="onLogout">退出登录</button>
              </div>
            </footer>
          </div>
        </Transition>
      </div>

      <span class="hh-logo">◇</span>
      <span class="hh-system">SMART ENERGY</span>
    </div>

    <!-- ─── 中央:大标题 + 快捷导航 ─── -->
    <div class="hh-center">
      <h1 class="hh-title">智慧能源生产指挥中心</h1>
      <nav class="hh-nav">
        <a
          v-for="n in mainNavs"
          :key="n.label"
          class="hh-nav__item"
          :class="{ 'is-active': n.active }"
          @click="navTo(n)"
        >{{ n.label }}</a>
      </nav>
    </div>

    <!-- ─── 右:时间 + 天气 + 角色徽章 ─── -->
    <div class="hh-right">
      <span class="hh-weather">☀ 28°</span>
      <span class="hh-divider"></span>
      <span class="hh-time">{{ clock }}</span>
      <span class="hh-date">{{ dateText }} {{ dayText }}</span>
      <span class="hh-divider"></span>
      <span class="hh-role-badge">{{ roleLabel(role) }}</span>
    </div>
  </header>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

defineProps({
  summary: { type: Object, default: () => ({}) },
  devices: { type: Array, default: () => [] },
  alertCount: { type: Number, default: 0 }
})

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const role = computed(() => auth.user?.role)
const username = computed(() => auth.user?.username || '未登录')
const userInitial = computed(() => (username.value?.[0] || '?').toUpperCase())

// ============ 角色标签 ============
const ROLE_LABELS = {
  ADMIN: '管理员',
  OPERATOR: '操作员',
  MAINTENANCE_ENGINEER: '维修工程师',
  DEVICE_MANAGER: '工单管理人员',
  MANAGER: '生产经理',
  HR_MANAGER: '人事管理员'
}
const roleLabel = (r) => ROLE_LABELS[r] || r || '访客'

// ============ 中央 4 项主导航 ============
const mainNavs = computed(() => [
  { label: '负荷分析', path: '/analysis',    active: route.path.startsWith('/analysis') },
  { label: '设备管理', path: '/devices',     active: route.path.startsWith('/devices') },
  { label: '能耗优化', path: '/scheduler',   active: route.path.startsWith('/scheduler') },
  { label: '告警监控', path: '/maintenance', active: route.path === '/maintenance' }
])
const navTo = (n) => { if (!n.active) router.push(n.path) }

// ============ 下拉菜单 —— 按角色过滤 ============
// 每项: { label, en, path, icon, roles }
const ALL_GROUPS = [
  {
    title: '生产监控 / PRODUCTION',
    items: [
      { label: '监控大屏',  en: 'COCKPIT',   path: '/dashboard',   icon: '◆', roles: ['OPERATOR','MANAGER','ADMIN'] },
      { label: '负荷分析',  en: 'ANALYSIS',  path: '/analysis',    icon: '◢', roles: ['OPERATOR','MANAGER','ADMIN'] },
      { label: '生产调度',  en: 'SCHEDULER', path: '/scheduler',   icon: '◐', roles: ['MANAGER','ADMIN'] }
    ]
  },
  {
    title: '设备运维 / DEVICES',
    items: [
      { label: '设备管理',     en: 'DEVICES',   path: '/devices',                  icon: '◧', roles: ['OPERATOR','ADMIN'] },
      { label: '维修知识库',  en: 'KNOWLEDGE', path: '/knowledge',                icon: '◇', roles: ['MAINTENANCE_ENGINEER','ADMIN'] },
      { label: '工单中心',     en: 'ORDERS',    path: '/maintenance',              icon: '◨', roles: ['MAINTENANCE_ENGINEER','ADMIN'] },
      { label: '故障工单中心', en: 'OPS ORDERS',path: '/operations/orders',        icon: '◳', roles: ['DEVICE_MANAGER','ADMIN'] },
      { label: '智能调度',     en: 'DISPATCH',  path: '/maintenance/dispatch',     icon: '◰', roles: ['DEVICE_MANAGER','ADMIN'] },
      { label: '转派审批',     en: 'TRANSFER',  path: '/maintenance/transfer-requests', icon: '◱', roles: ['DEVICE_MANAGER','ADMIN'] }
    ]
  },
  {
    title: '库存物料 / INVENTORY',
    items: [
      { label: '备件库存',      en: 'SPARE PARTS',  path: '/spare-parts',                icon: '▢', roles: ['DEVICE_MANAGER','ADMIN'] },
      { label: '库存与申请记录', en: 'PART REQUESTS',path: '/maintenance/spare-parts',   icon: '▣', roles: ['MAINTENANCE_ENGINEER'] }
    ]
  },
  {
    title: '管理与系统 / ADMIN',
    items: [
      { label: '经理决策仪表盘', en: 'EXECUTIVE',  path: '/admin',           icon: '◣', roles: ['MANAGER','ADMIN'] },
      { label: '人员管理',       en: 'PEOPLE',     path: '/admin/people',    icon: '◬', roles: ['HR_MANAGER','DEVICE_MANAGER','ADMIN'] },
      { label: '系统配置',       en: 'CONFIG',     path: '/admin/config',    icon: '◵', roles: ['ADMIN'] },
      { label: '审计日志',       en: 'AUDIT LOG',  path: '/audit-log',       icon: '◶', roles: ['ADMIN'] },
      { label: '账号设置',       en: 'ACCOUNT',    path: '/account-settings',icon: '◔', roles: ['*'] }
    ]
  }
]

const accessibleGroups = computed(() => {
  const r = role.value
  return ALL_GROUPS
    .map(g => ({
      ...g,
      items: g.items.filter(i => i.roles.includes('*') || (r && i.roles.includes(r)))
    }))
    .filter(g => g.items.length)
})

// ============ 下拉控制 ============
const dropdownOpen = ref(false)
const moreWrapRef = ref(null)
const toggleDropdown = () => { dropdownOpen.value = !dropdownOpen.value }
const closeDropdown = () => { dropdownOpen.value = false }
const navAndClose = (item) => {
  closeDropdown()
  if (route.path !== item.path) router.push(item.path)
}
const goAccount = () => {
  closeDropdown()
  router.push('/account-settings')
}
const onLogout = async () => {
  closeDropdown()
  await auth.logout()
  router.push('/login')
}

// 点击外部关闭
const onDocClick = (e) => {
  if (!dropdownOpen.value) return
  if (moreWrapRef.value && !moreWrapRef.value.contains(e.target)) closeDropdown()
}

// ============ 时间 ============
const clock = ref('')
const dateText = ref('')
const dayText = ref('')
const update = () => {
  const d = new Date()
  clock.value = `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
  dateText.value = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  dayText.value = ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()]
}
let timer = null
onMounted(() => {
  update()
  timer = setInterval(update, 1000)
  document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('click', onDocClick)
})
</script>

<style scoped>
.hud-header {
  position: absolute;
  top: 0; left: 0;
  width: 100%;
  height: 80px;
  z-index: 30;
  pointer-events: auto;
  display: grid;
  grid-template-columns: 320px 1fr 340px;
  align-items: center;
  padding: 0 24px;
  background: linear-gradient(180deg, rgba(4, 15, 30, 0.9) 0%, rgba(4, 15, 30, 0.6) 70%, transparent 100%);
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  color: #d9e8f5;
  font-family: 'DIN', 'DIN Alternate', Arial, 'Microsoft YaHei', sans-serif;
}
.hud-header::before,
.hud-header::after {
  content: '';
  position: absolute;
  bottom: -1px;
  width: 22%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00FFFF, transparent);
}
.hud-header::before { left: 12%; }
.hud-header::after  { right: 12%; }

/* ─── 左:≡ 更多功能 + LOGO ─── */
.hh-left {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
}
.hh-more-wrap { position: relative; }
.hh-more {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  background: rgba(0, 30, 60, 0.5);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: #d9e8f5;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 2px;
  border-radius: 2px;
  transition: all 0.22s ease;
}
.hh-more__bars {
  display: inline-flex;
  flex-direction: column;
  gap: 3px;
  width: 14px;
}
.hh-more__bars span {
  display: block;
  height: 1.5px;
  background: #00FFFF;
  box-shadow: 0 0 4px #00FFFF;
  transition: all 0.22s ease;
}
.hh-more__bars span:nth-child(1) { width: 100%; }
.hh-more__bars span:nth-child(2) { width: 70%; }
.hh-more__bars span:nth-child(3) { width: 90%; }
.hh-more:hover,
.hh-more.is-open {
  background: rgba(0, 200, 255, 0.18);
  border-color: #00FFFF;
  color: #ffffff;
  box-shadow: 0 0 12px rgba(0, 255, 255, 0.25);
}
.hh-more.is-open .hh-more__bars span:nth-child(2) { width: 100%; }
.hh-more__text {
  font-weight: 600;
  text-shadow: 0 0 4px rgba(0, 255, 255, 0.4);
}

.hh-logo {
  width: 28px; height: 28px;
  display: grid; place-items: center;
  font-size: 18px;
  color: #00FFFF;
  border: 1px solid rgba(0, 255, 255, 0.4);
  text-shadow: 0 0 8px #00FFFF;
}
.hh-system {
  font-size: 13px;
  letter-spacing: 3px;
  color: #00FFFF;
  font-weight: 600;
}

/* ─── 下拉菜单 (毛玻璃 + 角标) ─── */
.hh-dropdown {
  position: absolute;
  top: calc(100% + 12px);
  left: 0;
  width: 640px;
  background: rgba(4, 15, 30, 0.85);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  border: 1px solid rgba(0, 255, 255, 0.3);
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.7),
    0 0 24px rgba(0, 255, 255, 0.15);
  z-index: 100;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 120px);
}
.hh-dd-corner {
  position: absolute;
  width: 10px; height: 10px;
  border-color: #00FFFF;
  border-style: solid;
  filter: drop-shadow(0 0 4px #00FFFF);
}
.hh-dd-corner--tl { top: -1px; left: -1px; border-width: 2px 0 0 2px; }
.hh-dd-corner--tr { top: -1px; right: -1px; border-width: 2px 2px 0 0; }
.hh-dd-corner--bl { bottom: -1px; left: -1px; border-width: 0 0 2px 2px; }
.hh-dd-corner--br { bottom: -1px; right: -1px; border-width: 0 2px 2px 0; }

.hh-dd-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.18);
  background: linear-gradient(90deg, rgba(0, 200, 255, 0.15), transparent 70%);
}
.hh-dd-head__title {
  font-size: 11px;
  letter-spacing: 3px;
  color: #00FFFF;
  font-weight: 700;
}
.hh-dd-close {
  width: 22px; height: 22px;
  background: transparent;
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: #00FFFF;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
}
.hh-dd-close:hover { background: rgba(255, 72, 85, 0.18); border-color: #ff4855; color: #ff4855; }

.hh-dd-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  padding: 14px;
  overflow-y: auto;
  background: rgba(0, 255, 255, 0.05);
}
.hh-dd-grid::-webkit-scrollbar { display: none; }
.hh-dd-grid { scrollbar-width: none; -ms-overflow-style: none; }

.hh-dd-group {
  background: rgba(4, 15, 30, 0.6);
  padding: 10px 12px;
}
.hh-dd-group__title {
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  letter-spacing: 2px;
  color: #00FFFF;
  font-weight: 700;
  padding-bottom: 6px;
  border-bottom: 1px dashed rgba(0, 255, 255, 0.15);
}
.hh-dd-group__mark {
  display: inline-block;
  width: 3px; height: 10px;
  background: #00FFFF;
  box-shadow: 0 0 4px #00FFFF;
}
.hh-dd-group__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hh-dd-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}
.hh-dd-item:hover {
  background: rgba(0, 200, 255, 0.12);
  border-color: rgba(0, 255, 255, 0.35);
}
.hh-dd-item.is-active {
  background: linear-gradient(90deg, rgba(0, 200, 255, 0.25), rgba(0, 100, 160, 0.05));
  border-color: #00FFFF;
  box-shadow: inset 0 0 8px rgba(0, 200, 255, 0.15);
}
.hh-dd-item__icon {
  width: 22px; height: 22px;
  display: grid; place-items: center;
  background: rgba(0, 100, 160, 0.18);
  border: 1px solid rgba(0, 255, 255, 0.2);
  color: #00FFFF;
  font-size: 12px;
  flex-shrink: 0;
}
.hh-dd-item.is-active .hh-dd-item__icon {
  background: rgba(0, 200, 255, 0.3);
  border-color: #00FFFF;
}
.hh-dd-item__text { display: flex; flex-direction: column; line-height: 1.1; min-width: 0; }
.hh-dd-item__text strong {
  font-size: 12px;
  letter-spacing: 1px;
  color: #ffffff;
  font-weight: 600;
}
.hh-dd-item__text em {
  font-style: normal;
  font-size: 8.5px;
  letter-spacing: 1.5px;
  color: rgba(0, 255, 255, 0.5);
  margin-top: 2px;
  font-family: Arial, monospace;
}

.hh-dd-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-top: 1px solid rgba(0, 255, 255, 0.18);
  background: rgba(0, 30, 60, 0.4);
}
.hh-dd-user { display: flex; align-items: center; gap: 10px; }
.hh-dd-avatar {
  width: 32px; height: 32px;
  display: grid; place-items: center;
  background: linear-gradient(135deg, rgba(0, 200, 255, 0.4), rgba(0, 100, 160, 0.2));
  border: 1px solid #00FFFF;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  text-shadow: 0 0 6px rgba(0, 255, 255, 0.6);
}
.hh-dd-user__text { display: flex; flex-direction: column; line-height: 1.2; }
.hh-dd-user__text strong { font-size: 13px; color: #ffffff; letter-spacing: 1px; }
.hh-dd-user__text em {
  font-style: normal;
  font-size: 10px;
  color: #00FFFF;
  letter-spacing: 1.5px;
  margin-top: 2px;
}
.hh-dd-foot__actions { display: flex; gap: 6px; }
.hh-dd-btn {
  padding: 5px 14px;
  background: transparent;
  border: 1px solid rgba(0, 255, 255, 0.35);
  color: rgba(217, 232, 245, 0.8);
  font-size: 11px;
  letter-spacing: 1.5px;
  font-family: inherit;
  cursor: pointer;
  border-radius: 2px;
}
.hh-dd-btn:hover { color: #00FFFF; border-color: #00FFFF; }
.hh-dd-btn--danger { border-color: rgba(255, 72, 85, 0.4); }
.hh-dd-btn--danger:hover { color: #ff4855; border-color: #ff4855; background: rgba(255, 72, 85, 0.1); }

/* 下拉过渡 */
.hh-dd-enter-active, .hh-dd-leave-active {
  transition: opacity 0.22s ease, transform 0.28s cubic-bezier(0.34, 1.4, 0.5, 1);
  transform-origin: top left;
}
.hh-dd-enter-from, .hh-dd-leave-to {
  opacity: 0;
  transform: scale(0.95) translateY(-8px);
}

/* ─── 中央标题 + 菜单 ─── */
.hh-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.hh-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #b3e9ff 50%, #00FFFF 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  text-shadow: 0 0 18px rgba(0, 255, 255, 0.7);
  filter: drop-shadow(0 0 12px rgba(0, 200, 255, 0.4));
  line-height: 1.1;
}
.hh-nav { display: flex; gap: 24px; }
.hh-nav__item {
  position: relative;
  font-size: 12px;
  letter-spacing: 4px;
  color: rgba(217, 232, 245, 0.65);
  cursor: pointer;
  padding: 2px 0;
  transition: color 0.2s ease;
}
.hh-nav__item::after {
  content: '';
  position: absolute;
  left: 50%; bottom: -2px;
  width: 0; height: 1px;
  background: #00FFFF;
  box-shadow: 0 0 6px #00FFFF;
  transform: translateX(-50%);
  transition: width 0.25s ease;
}
.hh-nav__item:hover { color: #00FFFF; }
.hh-nav__item:hover::after { width: 100%; }
.hh-nav__item.is-active {
  color: #00FFFF;
  text-shadow: 0 0 6px rgba(0, 255, 255, 0.6);
}
.hh-nav__item.is-active::after { width: 100%; }

/* ─── 右侧时间天气 + 角色徽章 ─── */
.hh-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  font-size: 12px;
  color: rgba(217, 232, 245, 0.85);
}
.hh-weather {
  color: #ffaa00;
  font-size: 13px;
  font-weight: 600;
  text-shadow: 0 0 6px rgba(255, 170, 0, 0.5);
}
.hh-divider {
  width: 1px;
  height: 16px;
  background: rgba(0, 255, 255, 0.3);
}
.hh-time {
  color: #00FFFF;
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
  text-shadow: 0 0 8px rgba(0, 255, 255, 0.6);
}
.hh-date {
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(217, 232, 245, 0.6);
}
.hh-role-badge {
  padding: 3px 10px;
  border: 1px solid rgba(0, 255, 255, 0.35);
  background: rgba(0, 200, 255, 0.12);
  color: #00FFFF;
  font-size: 10px;
  letter-spacing: 2px;
  font-weight: 700;
  border-radius: 2px;
}
</style>
