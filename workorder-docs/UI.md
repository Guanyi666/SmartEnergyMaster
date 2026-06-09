# 维修工单与人员调度 前端设计

> 实现: `C:\SmartEnergyMaster\frontend\src\views/MaintenanceCenterView.vue` 等

---

## 1. 设计原则

- **风格延续**：与现有 DashboardView 一致的深色玻璃拟态（`glass-panel`）
- **主色板**：蓝 `#52c8ff` / 绿 `#3bff9f` / 橙 `#ff9f43` / 红 `#ff5d5d` / 紫 `#a78bfa`
- **动效**：hover 上浮、拖拽高亮、数字滚动、staggered fade-in

---

## 2. 路由与角色

| 路径 | 角色 | 视图 |
|---|---|---|
| `/maintenance` | MAINTENANCE_ENGINEER / ADMIN | MaintenanceCenterView (Kanban) |
| `/maintenance/personnel` | + MANAGER | PersonnelView (花名册) |
| `/maintenance/dispatch` | + MANAGER | DispatchView (智能调度) |
| `/maintenance/orders/:id` | MAINTENANCE_ENGINEER / ADMIN | WorkOrderDetailView |

登录分流：
- `MAINTENANCE_ENGINEER` → `/maintenance`
- 其他 → `/dashboard`

---

## 3. 关键组件

| 组件 | 用途 |
|---|---|
| `StatBadge` | 顶部统计徽章（图标 + 数值 + 副标题） |
| `WorkOrderCard` | Kanban 卡片：工单号 + 设备 + 故障 + 指标 + 指派人 |
| `WorkOrderDetailDrawer` | 右侧抽屉：完整工单详情 + 操作按钮 |
| `WorkOrderKanban` | （合并到 MaintenanceCenterView，用原生 HTML5 拖拽） |

---

## 4. 三个核心页面

### 4.1 MaintenanceCenterView（维修指挥中心主页）
- 顶部 5 个 StatBadge
- 3 列 Kanban：待处理 / 处理中 / 已完成
- 原生 HTML5 drag-and-drop，状态切换直调 8080 现有 API
- 5s 轮询新工单（`usePollingTask`）
- 点击卡片打开右侧抽屉

### 4.2 PersonnelView（人员花名册）
- 顶部筛选：搜索 + 技能等级 + 在岗状态 + 技能
- 网格 3 列人员卡片：头像 + 姓名/工号 + 等级徽章 + 技能 chips + 负载进度条 + 在岗 toggle
- 新增/编辑 Dialog（带表单校验）
- 删除前确认（currentWorkload>0 时阻止）

### 4.3 DispatchView（智能调度中心）
- 三栏布局：
  - 左 280px：待派工单列表（点击切换）
  - 中 1fr：当前工单 + 自动匹配 Top N（带匹配分环形进度条）
  - 右 320px：按技能分组的负载矩阵
- 推荐卡 staggered fade-in（每张延迟 80ms）
- 技能 chip 高亮"matched"（命中所需技能）

### 4.4 WorkOrderDetailView（工单全屏详情）
- 状态色条 banner
- 4 卡片网格：触发时传感器 / 当前指派人 / 故障描述 / 指派历史
- 路由 `/maintenance/orders/:id`

---

## 5. 配色

| 用途 | 颜色 |
|---|---|
| 主色（玻璃面板边框） | rgba(82, 200, 255, 0.18) |
| 强调蓝 | #52c8ff |
| 成功绿 | #3bff9f |
| 警告橙 | #ff9f43 |
| 危险红 | #ff5d5d |
| 优先级 CRITICAL 紫 | #a78bfa |
| 文字主色 | #e0f2fe |
| 文字副色 | var(--text-secondary) |
| 玻璃背景 | rgba(15, 23, 42, 0.62) + backdrop-filter: blur(14px) |
| 技能色 | 电气 #ff9f43 / 机械 #52c8ff / 液压 #3bff9f / 仪表 #a78bfa / 自动化 #f472b6 |

---

## 6. 动效细节

- Kanban 卡片 hover：`transform: translateY(-4px)` + 蓝色边框 + 阴影（200ms）
- 拖拽中：目标列高亮淡蓝背景 `rgba(82, 200, 255, 0.08)`
- 自动匹配推荐卡：`animation: slideUp 0.4s ease forwards`，延迟 0/80/160ms
- WorkloadBar 危险状态：超 80% 触发 `pulse 2s infinite`
- StatBadge 边框：linear-gradient + mask 实现 1px 渐变描边

---

## 7. 关键约束

- 🟠 拖拽改 status 走 8080 现有 PATCH，不走 8081（脏写风险修正）
- 🟠 5s 轮询新工单（实时感知）
- 🟠 菜单项按 `auth.user?.role` v-if 显示
- 🟠 路由守卫按 `meta.roles` 检查
