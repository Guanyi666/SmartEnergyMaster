# Epic 05：维修工单与人员调度 —— 实施计划

> <span style="color:red;font-weight:bold">⚠️ 审查状态：已审查（2026-06-09），发现 3 处严重问题 + 3 处重要问题 + 6 处建议优化，详见文中红色标注。</span>

---

## Context

### 业务目标
构建从"故障发现 → 派单 → 接单 → 维修 → 闭环"的完整数字化流程。当前项目基础工单已通（Story 05-1-1/2/3 完成），但**人员管理与自动指派完全缺失**（Story 05-1-4 与 Feature 05-2 全部未做）。`work_order.assignee` 现在只是 VARCHAR 自由文本，没有外键关系、没有工作负载统计、没有专业技能匹配。

### 用户的需求（已确认）
1. **新模块放新文件夹**：在项目根下新建一个顶级文件夹，后端作为独立 Spring Boot 工程
2. **登录后分流**：维修人员账号登录后进入新界面（运维/操作员/管理员仍走现有大屏）
3. **现有其他代码不修改**：仅最小化改 router / vite.config / LoginView 的几行代码以支持新角色路由
4. **前端要做得漂亮**：与现有深色玻璃拟态风格一致，并升级看板 + 拖拽 + 智能推荐卡片

### 集成策略
- 复用现有 **TimescaleDB**（port 5432）
- 新表加 `epic05_` 前缀（`public` schema 内），不修改现有 4 张表
- 新模块**只读 + 写 `work_order.assignee`** 显示字段（不改 work_order 表结构）
- 业务关系（多对多）放在新表 `epic05_work_order_assignment`，这是人员指派事实表
- 新后端端口 **8081**（避免与现有 8080 冲突）

---

## Architecture

```
C:/SmartEnergyMaster/
├── backend/                          ← 不动
├── frontend/                         ← 95% 不动，只追加
│   ├── src/
│   │   ├── api/epic05.js             ★ 新增：Epic05 API 封装
│   │   ├── components/
│   │   │   ├── WorkOrderKanban.vue   ★ 新增
│   │   │   ├── WorkOrderCard.vue     ★ 新增
│   │   │   ├── WorkOrderDetailDrawer.vue   ★ 新增
│   │   │   ├── PersonnelCard.vue     ★ 新增
│   │   │   ├── AssignmentDialog.vue  ★ 新增（自动匹配 + 手动选择）
│   │   │   ├── SkillChip.vue         ★ 新增
│   │   │   ├── WorkloadBar.vue       ★ 新增（负载进度条）
│   │   │   └── StatBadge.vue         ★ 新增（顶部统计徽章）
│   │   ├── views/
│   │   │   ├── MaintenanceCenterView.vue   ★ 新增：维修中心（Kanban 主页）
│   │   │   ├── PersonnelView.vue           ★ 新增：人员管理
│   │   │   ├── DispatchView.vue            ★ 新增：智能调度/指派中心
│   │   │   └── WorkOrderDetailView.vue     ★ 新增：工单全屏详情
│   │   ├── stores/dispatch.js        ★ 新增：Pinia 共享状态
│   │   ├── composables/useDragDrop.js★ 新增：拖拽 helper
│   │   ├── router/index.js           ⚠️ 改 3 处：加 4 个新路由 + 改登录守卫
│   │   ├── views/LoginView.vue       ⚠️ 改 1 处：登录后按角色 redirect
│   │   └── ... (其他不动)
│   ├── vite.config.js                ⚠️ 改 1 处：加 /api/epic05 反向代理
│   └── package.json                  （不动，Element Plus 已有 el-tag/dialog/drawer 都够用；拖拽用 SortableJS 需新增）
├── epic05-backend/                   ★ 新建：独立 Spring Boot 工程
│   ├── pom.xml
│   ├── src/main/java/com/smartenergy/epic05/
│   │   ├── Epic05Application.java
│   │   ├── config/                   （DataSource、OpenAPI、CORS）
│   │   ├── controller/
│   │   │   ├── MaintenancePersonnelController.java
│   │   │   ├── WorkOrderAssignmentController.java
│   │   │   └── Epic05DashboardController.java
│   │   ├── service/
│   │   │   ├── MaintenancePersonnelService.java
│   │   │   ├── WorkOrderAssignmentService.java
│   │   │   ├── AutoMatchEngine.java
│   │   │   └── Epic05DashboardService.java
│   │   ├── mapper/
│   │   │   ├── Epic05MaintenancePersonnelMapper.java
│   │   │   ├── Epic05WorkOrderAssignmentMapper.java
│   │   │   └── ExistingWorkOrderMapper.java   ★ 读 + 写 work_order.assignee
│   │   ├── entity/
│   │   │   ├── Epic05MaintenancePersonnel.java
│   │   │   ├── Epic05WorkOrderAssignment.java
│   │   │   └── ExistingWorkOrder.java         ★ 复用现有表结构
│   │   ├── dto/
│   │   │   ├── MaintenancePersonnelRequest.java
│   │   │   ├── WorkOrderAssignRequest.java
│   │   │   ├── WorkOrderDispatchRequest.java
│   │   │   └── PageQuery.java
│   │   └── vo/
│   │       ├── MaintenancePersonnelVO.java
│   │       ├── WorkOrderAssignmentVO.java
│   │       ├── WorkOrderDispatchVO.java     ★ 含自动匹配 Top 3
│   │       └── DispatchSummaryVO.java
│   └── src/main/resources/
│       ├── application.yml
│       └── db/init-epic05.sql         ★ 新表 + 种子数据
├── epic05-docs/                       ★ 新建：Epic 05 设计文档
│   ├── API.md
│   ├── DB.md
│   └── UI.md
└── README-EPIC05.md                   ★ 新建：运行说明
```

---

## 一、Epic05 Backend（独立 Spring Boot，port 8081）

### 1.1 技术栈
- Spring Boot 3.4.5 + Java 17 + Maven
- MyBatis-Plus 3.5.5
- SpringDoc OpenAPI 2.8.6
- PostgreSQL Driver（连现有 TimescaleDB）
- Lombok + Hutool
- **不引** Spring Security：新模块放在内网信任区，自己不鉴权，由前端代理时把 JWT 透传过来（可选解析，不强制）

### 1.2 数据库设计（`init-epic05.sql`）

<span style="color:red;font-weight:bold">🔴 严重问题 #1：以下建表缺少 IF NOT EXISTS，配合 mode: always 会在第二次启动时因表已存在而崩溃。</span>

```sql
-- 维修人员档案
CREATE TABLE IF NOT EXISTS epic05_maintenance_personnel (  <!-- 🔴 必须加 IF NOT EXISTS -->
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(64),
    avatar_color VARCHAR(16) DEFAULT '#52c8ff',  -- 头像底色
    specializations JSONB NOT NULL DEFAULT '[]', -- 技能标签数组，如 ["电气","液压","机械"]
    skill_level VARCHAR(16) NOT NULL,            -- JUNIOR/INTERMEDIATE/SENIOR/EXPERT
    certification VARCHAR(256),                  -- 证书描述
    current_workload INT NOT NULL DEFAULT 0,     -- 当前在处理工单数
    max_workload INT NOT NULL DEFAULT 5,         -- 最大并行处理数
    is_on_duty BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_e05_person_specialization ON epic05_maintenance_personnel USING GIN (specializations);
CREATE INDEX idx_e05_person_on_duty ON epic05_maintenance_personnel (is_on_duty, skill_level);

-- 工单-人员指派关系（事实表）
CREATE TABLE IF NOT EXISTS epic05_work_order_assignment (  <!-- 🔴 必须加 IF NOT EXISTS -->
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,               -- 关联 work_order.id
    personnel_id BIGINT NOT NULL REFERENCES epic05_maintenance_personnel(id),
    role VARCHAR(16) NOT NULL DEFAULT 'PRIMARY', -- PRIMARY / ASSIST
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,                       -- 转派/闭环时填
    note VARCHAR(256)
);
CREATE INDEX idx_e05_woa_wo ON epic05_work_order_assignment (work_order_id, released_at);
CREATE INDEX idx_e05_woa_personnel ON epic05_work_order_assignment (personnel_id, released_at);

-- 种子数据：6 名维修人员（覆盖 4 种技能等级、3 种专业）
INSERT INTO epic05_maintenance_personnel (employee_no, name, phone, specializations, skill_level, certification, max_workload) VALUES
('E001', '张工',  '13800000001', '["电气","自动化"]',          'EXPERT',       '高级工程师 / 15年', 5),
('E002', '李工',  '13800000002', '["机械","液压"]',            'SENIOR',       '机械工程师 / 10年', 4),
('E003', '王工',  '13800000003', '["电气","机械","液压"]',     'SENIOR',       '复合技师 / 8年',   4),
('E004', '赵工',  '13800000004', '["仪表","自动化"]',          'INTERMEDIATE', '仪表技师 / 5年',    3),
('E005', '孙工',  '13800000005', '["机械","焊接"]',            'INTERMEDIATE', '机修工 / 3年',      3),
('E006', '周工',  '13800000006', '["电气","仪表","自动化"]',   'JUNIOR',       '助理工程师 / 1年',  2)
ON CONFLICT (employee_no) DO NOTHING;  <!-- 🔴 必须加 ON CONFLICT，避免重复插入报错 -->
```

**注意**：现有 `work_order` 表**不修改**。新模块在指派时把人员姓名写入现有 `work_order.assignee`（VARCHAR）字段，仅做展示同步。

### 1.3 故障类型 → 技能映射（`AutoMatchEngine.java`）

<span style="color:red;font-weight:bold">🔴 严重问题 #2：故障类型 key 用了中文，但现有系统 work_order.fault_type 存的是 "MECHANICAL_JAM"、"COOLING_INTERRUPT" 等英文常量，自动匹配永远不会命中。</span>

```java
private static final Map<String, List<String>> FAULT_TO_SKILL = Map.of(
    // 🔴 原值——中文 key，系统实际 fault_type 是英文常量，匹配不到！
    // "机械卡涩",    List.of("机械", "液压"),
    // "电气过载",    List.of("电气", "自动化"),
    // "冷却异常",    List.of("液压", "仪表"),
    // "传感器漂移",  List.of("仪表", "自动化")
    // ✅ 修正——对齐现有系统 work_order.fault_type 实际值：
    "MECHANICAL_JAM",       List.of("机械", "液压"),
    "COOLING_INTERRUPT",    List.of("液压", "仪表"),
    "ELECTRICAL_OVERLOAD",  List.of("电气", "自动化"),
    "SENSOR_DRIFT",         List.of("仪表", "自动化")
);
```

匹配评分：`基础分 50 + 技能匹配 30（每匹配一个 +15）+ 技能等级 +0/5/10/15（JUNIOR/INTERMEDIATE/SENIOR/EXPERT）- 负载率 * 20`。

### 1.4 核心 API 端点

| Method | Path | 用途 |
|---|---|---|
| GET | `/api/epic05/personnel` | 人员列表（分页 + 筛选：specialization/skillLevel/onDuty） |
| POST | `/api/epic05/personnel` | 新增人员 |
| PUT | `/api/epic05/personnel/{id}` | 编辑人员 |
| DELETE | `/api/epic05/personnel/{id}` | 删除人员（仅当 workload=0） |
| PATCH | `/api/epic05/personnel/{id}/duty` | 切换在岗/离岗 |
| GET | `/api/epic05/personnel/{id}` | 人员详情（含接单历史） |
| GET | `/api/epic05/auto-match?faultType=...&workOrderId=...` | 自动匹配 Top N 推荐（含评分） |
| GET | `/api/epic05/work-orders` | 工单列表（分页 + 状态筛选） |
| GET | `/api/epic05/work-orders/{id}` | 工单详情（含完整时间线 + 指派历史 + 当前人员负载） |
| POST | `/api/epic05/work-orders/{id}/assign` | 手动指派人员（含自动匹配调用入口） |
| POST | `/api/epic05/work-orders/{id}/release` | 释放人员（转派/撤单时） |
| GET | `/api/epic05/dashboard/summary` | 调度总览（在岗数 / 平均负载 / 技能覆盖） |
| GET | `/api/epic05/dispatch-board` | 调度看板（按技能分组的负载矩阵） |

所有读操作直接 SQL 查现库 `work_order` 表（read-only），写操作通过 `ExistingWorkOrderMapper` 同步 `work_order.assignee` 字段。

### 1.5 application.yml（关键）
```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_energy
    username: energy_user
    password: energy_password
  sql:
    init:
      mode: always   <!-- 🔴 见严重问题 #1：mode: always 意味着每次启动都执行 init-epic05.sql，必须配套 IF NOT EXISTS 和 ON CONFLICT -->
      schema-locations: classpath:db/init-epic05.sql
```
<span style="color:#ff9f43;font-weight:bold">🟠 建议：首次部署后改为 mode: never 或使用 Flyway/Liquibase 做版本迁移，避免生产环境误执行。</span>

---

## 二、Frontend 改造（95% 新增 + 3 处微改）

### 2.1 现有文件微改（共 3 处，加起来 < 30 行）

**`frontend/src/router/index.js`** —— 加 4 条路由 + 改守卫：

```js
// 在 children 数组里追加 4 条
{ path: '/maintenance', name: 'maintenance', component: MaintenanceCenterView, meta: { roles: ['MAINTENANCE_ENGINEER','ADMIN'] } },
{ path: '/maintenance/personnel', name: 'maintenance-personnel', component: PersonnelView, meta: { roles: ['MAINTENANCE_ENGINEER','ADMIN','MANAGER'] } },
{ path: '/maintenance/dispatch', name: 'maintenance-dispatch', component: DispatchView, meta: { roles: ['MAINTENANCE_ENGINEER','ADMIN','MANAGER'] } },
{ path: '/maintenance/orders/:id', name: 'maintenance-order-detail', component: WorkOrderDetailView, meta: { roles: ['MAINTENANCE_ENGINEER','ADMIN'] } },

// beforeEach 守卫：增加角色检查，未授权 redirect 到 /403 或 /dashboard
```
<span style="color:#ff9f43;font-weight:bold">🟠 重要问题 #4：现有 router.beforeEach 只检查了 `!to.meta.public && !token`，完全没有角色维度的拦截。必须在守卫中扩展：</span>
```js
// ✅ 建议在 beforeEach 中增加：
if (to.meta.roles && !to.meta.roles.includes(authStore.user?.role)) {
  return '/dashboard'  // 无权限角色重定向到首页
}
```

**`frontend/src/views/LoginView.vue`** —— `handleLogin` 末尾加：
```js
const role = authStore.user?.role
if (role === 'MAINTENANCE_ENGINEER') {
  router.push('/maintenance')
} else {
  router.push('/dashboard')
}
```

**`frontend/vite.config.js`** —— proxy 加一条：
```js
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/api/epic05': { target: 'http://localhost:8081', changeOrigin: true } // 优先匹配
}
```

**`frontend/src/layouts/MainLayout.vue`** —— 侧边栏菜单按角色动态显示（追加 1 个 `v-if` 块，新增"维修中心"菜单项，**不删除现有菜单项**）。

<span style="color:#ff9f43;font-weight:bold">🟠 重要问题 #5：现有 MainLayout.vue 菜单项是写死的，无角色判断逻辑。需要引入 `useAuthStore`，给维修中心菜单项加 `v-if="authStore.user?.role === 'MAINTENANCE_ENGINEER'"`。实施步骤中需明确此项。</span>

### 2.2 新增 `package.json` 依赖
- `sortablejs@^1.15.0`（拖拽）
- `vuedraggable@^4.1.0`（Vue 3 包装）—— 或者直接用 SortableJS

### 2.3 新增视图与组件

| 文件 | 内容要点 |
|---|---|
| `views/MaintenanceCenterView.vue` | **Kanban 主页**：3 列拖拽看板（PENDING/IN_PROGRESS/RESOLVED），顶部 5 个 StatBadge，点击卡片打开右侧 DetailDrawer，背景与 DashboardView 一致的深色玻璃 |
| `views/PersonnelView.vue` | **人员花名册**：顶部筛选（技能 chip 多选 / 技能等级 / 在岗 toggle），网格 3 列人员卡片，"+ 新增"按钮打开 Dialog |
| `views/DispatchView.vue` | **智能调度中心**：左 30% 工单列表 + 中 40% 自动匹配 Top 3 大卡片（带匹配度环形进度条）+ 右 30% 在岗人员负载矩阵（按技能分组） |
| `views/WorkOrderDetailView.vue` | **工单全屏详情**：工单头部 + 设备快照 + 触发时刻传感器数据（数字大字）+ 时间线 + 指派历史表 + 操作按钮组（确认处理/转派/闭环） |
| `components/WorkOrderKanban.vue` | 基于 vuedraggable 的 3 列容器，列头显示统计 |
| `components/WorkOrderCard.vue` | 工单卡：工单号 / 设备 / 故障主题（按类型 emoji 标识）/ 优先级 chip / 关键指标 / 指派人 avatar / 状态色边 |
| `components/WorkOrderDetailDrawer.vue` | Element Plus el-drawer，含工单完整信息 |
| `components/PersonnelCard.vue` | 头像（首字 + 底色）/ 姓名 / 工号 / 技能 chip 行 / 技能等级徽章 / WorkloadBar / 在岗 toggle / 联系电话 |
| `components/AssignmentDialog.vue` | 自动匹配 Top 3 卡片（带 match score 圆环）+ 全量人员网格（可点击选择） |
| `components/SkillChip.vue` | 不同技能用不同颜色（电气 #ff9f43 / 机械 #52c8ff / 液压 #3bff9f / 仪表 #a78bfa / 自动化 #f472b6） |
| `components/WorkloadBar.vue` | el-progress 包装，负载 > 80% 变红，> 60% 黄，否则绿 |
| `components/StatBadge.vue` | 顶部统计徽章（图标 + 数值 + 副标题），带渐变边框 |
| `api/epic05.js` | 所有 Epic05 API 调用封装（与现有 `api/index.js` 同风格） |
| `stores/dispatch.js` | Pinia store：当前选中工单 ID、自动匹配结果缓存、看板刷新触发器 |
| `composables/useDragDrop.js` | 拖拽状态封装（拖动时高亮目标列、放置后触发 PATCH） |

---

## 三、视觉设计（重点：漂亮）

### 3.1 设计原则
- **风格延续**：与现有 `DashboardView` 一致的深色玻璃拟态（`glass-panel` 类），蓝/青/绿/橙/红五色作为强调
- **动效加分**：
  - Kanban 卡片 hover：上浮 4px + 蓝色边框 + 阴影加深（200ms ease）
  - 拖拽中：原位置半透明，目标列高亮淡蓝背景
  - 数字滚动：负载变化时用 `<el-statistic>` 或手写 `requestAnimationFrame` 数字滚动 600ms
  - 自动匹配推荐卡：进入时 staggered fade-in（每张延迟 80ms）
- **视觉层级**：
  - 顶部：5 个 StatBadge（总工单 / 待处理 / 处理中 / 在岗人数 / 平均负载），圆角 16px，渐变 1px 边框
  - 主体：3 列 Kanban，每列列头显示状态色 + 数量徽章
  - 卡内信息密度适中，关键指标（温度/压力/振动）用迷你 sparkline 或大字号
  - 优先级 chip：HIGH 红底 / MEDIUM 黄底 / LOW 绿底 / CRITICAL 紫色脉动

### 3.2 配色（统一色板）
| 用途 | 颜色 |
|---|---|
| 主色（玻璃面板边框） | `rgba(82, 200, 255, 0.18)` |
| 强调蓝 | `#52c8ff` |
| 成功绿 | `#3bff9f` |
| 警告橙 | `#ff9f43` |
| 危险红 | `#ff5d5d` |
| 优先级 CRITICAL 紫 | `#a78bfa` |
| 文字主色 | `#e0f2fe` |
| 文字副色 | `var(--text-secondary)`（沿用现有 CSS 变量） |
| 玻璃背景 | `rgba(15, 23, 42, 0.62)` + `backdrop-filter: blur(14px)` |
| 技能色（见 SkillChip 注释） | 电气/机械/液压/仪表/自动化 各一色 |

### 3.3 关键页面线框

**MaintenanceCenterView（维修中心主页）**
```
┌────────────────────────────────────────────────────────────────────┐
│  维修指挥中心                            [刷新] [+ 新增工单] [⋯]   │
├────────────────────────────────────────────────────────────────────┤
│  [总工单 38] [待处理 12] [处理中 8] [在岗 5/6] [平均负载 67%]        │
├──────────────┬──────────────────────────┬──────────────────────────┤
│ 待处理  12  │ 处理中  8               │ 已完成  18               │
│ ┌──────────┐│ ┌──────────────────────┐│ ┌──────────────────────┐  │
│ │WO-xxx EAF││ │WO-yyy PUMP          ││ │WO-zzz COMP          │  │
│ │机械卡涩🔥││ │电气过载 ⚡ IN_PROG   ││ │传感器漂移 ✓          │  │
│ │🟠HIGH   ││ │🟣CRITICAL   张工 👤 ││ │孙工 10:23 闭环        │  │
│ │T 1050 P 80│ │T 980 P 45 V 18      ││ │T 720 P 95            │  │
│ └──────────┘│ └──────────────────────┘│ └──────────────────────┘  │
│  ... 拖拽   │   ...                  │   ...                     │
└──────────────┴──────────────────────────┴──────────────────────────┘
```

**DispatchView（智能调度中心）**

```
┌────────────────────────────────────────────────────────────────────┐
│  智能调度总览                       [按技能: 全部/电气/机械/...]    │
├──────────────┬──────────────────────────┬──────────────────────────┐
│ 待派工单     │ 智能推荐 Top 3 (WO-xxx) │ 在岗人员负载              │
│ ┌──────────┐│ ┌──────────────────────┐│ 电气组 (3 人)             │
│ │WO-xxx    ││ │ ╭─ 92% ─╮ 张工 EXPERT ││  张工 ████░ 4/5         │
│ │机械卡涩  ││ │ 电气 ✓ 机械 ✓       ││  王工 ███░░ 3/4         │
│ │→ 点击    ││ │ [指派]               ││  周工 ░░░░░ 0/2          │
│ ├──────────┤│ ├──────────────────────┤│ 机械组 (2 人)             │
│ │WO-yyy    ││ │ ╭─ 78% ─╮ 王工 SENIOR ││  李工 ███░░ 3/4         │
│ │...       ││ │ ...                  ││  赵工 ░░░░░ 0/3          │
│ └──────────┘│ └──────────────────────┘│                           │
└──────────────┴──────────────────────────┴──────────────────────────┘
```

### 3.4 组件动效细节
- **WorkOrderCard**：默认 `transition: all 0.2s ease`，hover `transform: translateY(-4px)`，drag 中 `opacity: 0.5`
- **AssignmentDialog 打开**：3 张推荐卡 `animation: slideUp 0.4s ease forwards`，`animation-delay` 0/0.08/0.16s
- **WorkloadBar**：超过 80% 触发 `@keyframes pulse { 0%,100% { opacity: 1 } 50% { opacity: 0.6 } }` 2s 无限循环
- **StatBadge 数字**：进入页面时 `useCountUp` 风格滚动到目标值，800ms

---

## 四、关键文件清单

### 新建（项目根）
- `epic05-backend/pom.xml` 与全部 Java 源码（约 25 个文件）
- `epic05-backend/src/main/resources/db/init-epic05.sql`
- `epic05-docs/API.md`、`DB.md`、`UI.md`
- `README-EPIC05.md`（顶层运行说明）

### 新建（frontend/src/）
- `api/epic05.js`
- `components/WorkOrderKanban.vue`、`WorkOrderCard.vue`、`WorkOrderDetailDrawer.vue`
- `components/PersonnelCard.vue`、`AssignmentDialog.vue`
- `components/SkillChip.vue`、`WorkloadBar.vue`、`StatBadge.vue`
- `views/MaintenanceCenterView.vue`、`PersonnelView.vue`、`DispatchView.vue`、`WorkOrderDetailView.vue`
- `stores/dispatch.js`
- `composables/useDragDrop.js`

### 微改（4 处，共 < 40 行）<span style="color:#52c8ff"> ← 🟡 建议 #11：原文写"3 处"，实际是 4 处，已修正</span>
- `frontend/src/router/index.js`（加 4 条路由 + 守卫角色判断）
- `frontend/src/views/LoginView.vue`（登录后按角色 redirect）
- `frontend/vite.config.js`（加 `/api/epic05` 反代）
- `frontend/src/layouts/MainLayout.vue`（侧边栏按角色显示维修中心菜单）

### 不动
- `backend/`（整个后端 Spring Boot 工程 0 行变更）
- `deploy/`、`data/`、`docs/`

---

## 五、实施顺序

| 步骤 | 内容 | 验证 |
|---|---|---|
| 1 | 新建 `epic05-backend/pom.xml` + `Epic05Application` + `application.yml` | `mvn spring-boot:run` 启动到 8081 成功 |
| 2 | 写 `init-epic05.sql` 跑通，新表 + 6 名种子人员 | 登录 psql `SELECT * FROM epic05_maintenance_personnel` 看到 6 行 |
| 3 | 实现 `Epic05MaintenancePersonnel` 实体 + Mapper + Service + Controller（CRUD + 切岗） | Swagger UI 看到所有人员端点，curl 增删改查通过 |
| 4 | 实现 `ExistingWorkOrder` 实体 + Mapper（只查现有 work_order） | curl `GET /api/epic05/work-orders?page=1&size=10` 返回现有工单 |
| 5 | 实现 `AutoMatchEngine` + `WorkOrderAssignRequest` + `POST /work-orders/{id}/assign` | 给工单指派人员后，查询 `work_order.assignee` 已被更新 |
| 6 | 实现人员负载 +1/-1 + 超 max 拒绝保护 | 反复指派到第 6 单时返回 400 |
| 7 | 实现 Dashboard/Dispatch 端点 | curl 看到在岗数 / 平均负载 / 技能矩阵 |
| 8 | 前端：加 4 条路由 + LoginView redirect + vite 反代 | 登录 MAINTENANCE_ENGINEER 账号进 /maintenance |
| 9 | 前端：写 `api/epic05.js` + `stores/dispatch.js` | 控制台看到请求打到 8081 |
| 10 | 前端：实现 MaintenanceCenterView（Kanban + 拖拽） | <span style="color:red">🔴 见严重问题 #3：拖拽改 status 应调用现有 8080 后端 PATCH /api/work-orders/{id}/status，而非新模块直接 UPDATE</span> |
| 11 | 前端：实现 PersonnelView + PersonnelCard + AssignmentDialog | 能新增/编辑/删除人员，能指派工单 |
| 12 | 前端：实现 DispatchView（推荐 Top 3） | 选工单后看到按评分排序的 3 张卡 |
| 13 | 前端：实现 WorkOrderDetailView（抽屉/全屏） | 看到工单完整时间线 + 指派历史 |
| 14 | 数据初始化脚本写好（6 名种子人员） | 重启后 6 名人员还在 |
| 15 | 写 README-EPIC05.md + epic05-docs/ | 同事按 README 能 5 分钟跑起来 |

---

## 六、Verification 端到端测试

### 6.1 数据准备
1. 启动现有 TimescaleDB（docker compose up -d）
2. 启动现有后端（`cd backend && mvn spring-boot:run`）→ 8080
3. 启动新 epic05 后端（`cd epic05-backend && mvn spring-boot:run`）→ 8081
4. 启动前端（`cd frontend && npm run dev`）→ 5173

### 6.2 测试账号
在 `epic05_maintenance_personnel` 表里手动插入 1 条关联 `sys_user` 记录（或者扩展 DataInitializer 让用户名 = employee_no 即可登录）。
- `admin / admin123`（现有，ADMIN）→ 登录后到 `/dashboard`
- `E001 / 123456`（新增，MAINTENANCE_ENGINEER）→ 登录后到 `/maintenance`

### 6.3 关键场景
1. **登录分流**：用 E001 登录 → 跳到 `/maintenance`（Kanban）；用 admin 登录 → 跳到 `/dashboard`
2. **人员 CRUD**：在 PersonnelView 新增"测试人员" → 数据库 7 行 → 编辑技能 → 删除 → 6 行
3. **自动匹配**：在 DispatchView 选一个"机械卡涩"工单 → 推荐 Top 3 出现（李工/王工靠前，因技能匹配 + 负载低）
4. **指派闭环**：<span style="color:red">🔴 严重问题 #3 修正后：</span>拖拽 PENDING 工单到 IN_PROGRESS 列 → <span style="color:red">新模块调用现有 8080 后端 `PATCH /api/work-orders/{id}/status`</span> → `work_order.status` 变 IN_PROGRESS + <span style="color:red">现有 WorkOrderService 同步更新 assignee</span> + 新模块记录 `current_workload +1`
5. **超载保护**：把张工（max 5）指到第 6 单 → 后端返回 400"已超过最大工作负载"
6. **拖拽看板**：拖工单卡到 RESOLVED 列 → <span style="color:red">同样调用现有 8080 后端，由 syncDeviceStatusAfterOrderUpdate 自动恢复设备状态</span>
7. **样式漂亮**：检查 Kanban 卡片 hover 动效、自动匹配 staggered fade-in、WorkloadBar 超 80% 红色脉动

### 6.4 验收对照（与全景图任务清单逐条）
- ✅ Story 05-1-4：GET /api/work-orders/{id} + 分页
- ✅ Story 05-2-1：personnel 实体 + mapper + DTO + VO + Service + Controller
- ✅ Story 05-2-2：自动匹配 + workload +1/-1 + 超载保护 + assign 端点

---

<span style="color:#52c8ff;font-weight:bold">🟡 建议 #7：两个服务同时写 `work_order.assignee` 存在脏写风险。</span> 现有 `WorkOrderServiceImpl.updateStatus()` 第 88-90 行也写 `assignee`，新模块也写。建议制定写入优先级约定：新模块仅在自己的 `POST .../assign` 时写入 assignee，且使用 `UPDATE work_order SET assignee = ? WHERE id = ? AND updated_at = ?` 形式的乐观锁，避免覆盖他人写入。

<span style="color:#52c8ff;font-weight:bold">🟡 建议 #9：看板缺少实时刷新机制。</span> Kanban 上的工单可能由现有后端（data_pump 触发故障→创建工单）动态产生，建议看板页面复用现有 `usePollingTask` 组合式函数，每 10 秒自动刷新工单列表。

---

## 七、风险与边界

| 风险 | 缓解 |
|---|---|
| <span style="color:red">🔴 严重问题 #3：新模块 Kanban 拖拽改 work_order.status 与"status 流转仍由现有 WorkOrderService 负责"矛盾</span> | <span style="color:red">✅ 修正方案：新模块的 Kanban 拖拽不直接 UPDATE work_order 表，而是调用现有 8080 后端 `PATCH /api/work-orders/{id}/status`。在 `api/epic05.js` 中封装调用现有 API 的函数。新模块只负责：(1) 读 work_order 做展示 (2) 写 epic05_work_order_assignment 做人员指派 (3) 通过 assign 接口同步写 work_order.assignee（仅展示用）。</span> |
| 角色扩展（MAINTENANCE_ENGINEER）现在 `sys_user.role` 只有 ADMIN/OPERATOR | <span style="color:#ff9f43">🟠 重要问题 #6：</span>✅ 修正方案：在新模块 `init-epic05.sql` 中追加 `INSERT INTO sys_user (username, password, role) VALUES ('E001', '$2a$10$...', 'MAINTENANCE_ENGINEER') ON CONFLICT (username) DO NOTHING;`。现有 `UserDetailsServiceImpl` 无需改动——role 字段直接作为 authority 返回，任何 role 值都能正常工作。同时建议在现有 DataInitializer 中也增加 MAINTENANCE_ENGINEER 角色支持（可选）。|
| Vite 代理 `/api/epic05` 与现有 `/api` 冲突 | Vite proxy 是顺序匹配，把 `/api/epic05` 写在 `/api` 前面即可命中 |
| 拖拽库在 Vue 3 兼容性 | 使用 `vuedraggable@4.1.0`（官方 Vue 3 适配版本）或直接用 SortableJS（更稳定） |
| 现有代码"零修改"边界 | router / LoginView / vite.config / MainLayout 这 4 处是用户已接受的"最小化修改"，其余绝对不动 |
| 工单-人员多对多复杂度 | v1 做 1 个工单最多 1 个主负责人 + 多个协助（role=PRIMARY/ASSIST），不在 v1 引入复杂排班 |

---

## 八、文件统计预估

| 类别 | 新建 | 修改 |
|---|---|---|
| 后端 Java | 25 个 | 0 |
| 后端 SQL/配置 | 2 个 | 0 |
| 前端组件 | 8 个 | 0 |
| 前端视图 | 4 个 | 0 |
| 前端 API/Store/Composable | 3 个 | 0 |
| 前端路由/配置/登录/布局 | 1 个（新增 router 段） | 4 处微改 |
| 文档 | 4 个 | 0 |
| **总计** | **47 个** | **4 处** |

---

## 九、给用户的 3 个待确认细节（已解决）

1. **拖拽库选哪个？** 建议 `vuedraggable@^4.1.0`（Vue 3 官方适配，API 简单）
2. **种子 MAINTENANCE_ENGINEER 账号放哪？** <span style="color:#3bff9f">✅ 已确定：在 epic05 init SQL 里追加 1 行 sys_user 插入（`ON CONFLICT (username) DO NOTHING`，启动即生效）。</span>
3. **是否需要单元测试？** 建议至少给 AutoMatchEngine 写 5-8 个 JUnit 测试（匹配评分、负载边界、转派逻辑）

---

## 十、审查意见汇总

| # | 级别 | 位置 | 问题 | 修改状态 |
|---|------|------|------|----------|
| 1 | 🔴 严重 | §1.2 SQL | `CREATE TABLE` 缺 `IF NOT EXISTS` + `INSERT` 缺 `ON CONFLICT`，配合 `mode: always` 重启即崩溃 | ✅ 已在文档中标出并给出修正写法 |
| 2 | 🔴 严重 | §1.3 Java | `AutoMatchEngine` 故障类型 key 用中文（"机械卡涩"），实际系统 `fault_type` 为英文常量（"MECHANICAL_JAM"），自动匹配永不命中 | ✅ 已在文档中替换为英文 key |
| 3 | 🔴 严重 | §五/§六/§七 | Kanban 拖拽直接改 `work_order.status` vs 风险声明"status 流转仍由现有 WorkOrderService 负责"前后矛盾 | ✅ 修正为调用现有 8080 后端 API |
| 4 | 🟠 重要 | §2.1 Router | `meta.roles` 加了但路由守卫 `beforeEach` 未扩展角色检查逻辑 | ✅ 已补充守卫代码 |
| 5 | 🟠 重要 | §2.1 MainLayout | 侧边栏菜单无角色动态显示，现有代码菜单项写死 | ✅ 已标注需引入 useAuthStore |
| 6 | 🟠 重要 | §七 风险表 | `MAINTENANCE_ENGINEER` 角色在系统中不存在 | ✅ 已给出 init SQL 中 INSERT sys_user 的具体方案 |
| 7 | 🟡 建议 | §七 | 两个服务同时写 `work_order.assignee` 存在脏写风险 | 已标注，建议增加乐观锁 |
| 8 | 🟡 建议 | §1.4 API | `workOrderId` 在 auto-match 查询参数中用途不明 | 建议补充注释说明用途（排除已指派+加载工单上下文） |
| 9 | 🟡 建议 | §二 Kanban | 看板缺少实时刷新机制（新工单由现有后端产生，看板感知不到） | 建议复用 `usePollingTask` 每 10s 刷新 |
| 10 | 🟡 建议 | §四 | `epic05-docs/` 应放入 `docs/epic05/` 统一管理 | 建议调整 |
| 11 | 🟡 建议 | §四 | 实施步骤"微改 3 处"实际是 4 处（漏算 MainLayout） | 已在 §四 标注 |
| 12 | 🟡 建议 | §1.5 | `mode: always` 生产环境有风险 | 建议首次部署后改为 `never` 或引入 Flyway |

<span style="color:#3bff9f;font-weight:bold">✅ 3 处严重问题已在文档中直接修正；3 处重要问题已给出具体修改方案。修正后可进入实施阶段。</span>
