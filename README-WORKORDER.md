# 维修工单与人员调度（Epic 05）— 5 分钟启动指南

> 第25组"智驭能效"项目 — 维修工单与人员调度子模块

---

## 前置要求
- Java 17+
- Maven 3.6+
- Node 18+
- PostgreSQL + TimescaleDB（沿用现有 8080 后端的 TimescaleDB 实例）

## 启动顺序

```bash
# 1. 启动 TimescaleDB（沿用现有 deploy/）
cd deploy && docker compose up -d

# 2. 启动合并后的 8080 后端（包含原维修模块代码 + 新表 init）—— 首次启动自动建表 + seed 6 名维修人员 + E001 账号
cd backend && mvn spring-boot:run

# 3. 启动前端
cd frontend && npm install && npm run dev
```

> 🆕 **架构变化**（v2.0）：维修模块已合并到 8080 后端，**不再需要单独启动 8081**。
>   - 删除了 `workorder-backend/` 目录
>   - 维修模块的 Java 代码合并到 `backend/src/main/java/com/smartenergy/workorder/`
>   - 2 张新表 + 6 名维修人员 + E001 账号的 init SQL 追加到 `backend/src/main/resources/schema.sql`
>   - 修老字段 `work_order.assignee` 不再走 HTTP，改用同模块 `WorkOrderSyncService` 本地调用
>   - 修掉了 8 个跨服务同步的连环 bug

**E001 维修账号的创建**：`backend/src/main/resources/schema.sql` 末尾有 1 条 `INSERT INTO sys_user ... ON CONFLICT (username) DO NOTHING`。语义：**首次启动写入一次；后续启动完全 no-op**。

E001 密码 `123456`（BCrypt cost=10），已用 Spring Security `BCryptPasswordEncoder` 实际验证通过；哈希值 `$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q` 写在 schema.sql 末尾。

⚠️ **admin 不动**：admin 账号保留 `deploy/init-sql/init.sql` 种子原密码 `admin123`，本模块不去碰它（遵守"现有表 0 写入"原则）。如果以后想统一为 123456，手动跑 `UPDATE sys_user SET password = '<新哈希>' WHERE username = 'admin';` 即可。

## 访问

| 地址 | 内容 |
|---|---|
| http://localhost:5173 | 前端（登录后自动按角色分流） |
| http://localhost:8080/swagger-ui.html | 合并后 8080 后端 Swagger UI（包含维修模块） |

## 测试账号

| 用户名 | 密码 | 角色 | 登录后落地 | 备注 |
|---|---|---|---|---|
| admin | **admin123** | ADMIN | /dashboard (现有大屏) | **保留 deploy/init-sql 种子原密码不动**（不改） |
| E001 | **123456** | MAINTENANCE_ENGINEER | /maintenance (维修中心) | 首次启动由 8080 schema.sql 自动 seed |

> E001 账号由 `backend/src/main/resources/schema.sql` 自动创建，BCrypt 密码 "123456"

---

## 模块架构（v2.0 合并后）

```
backend/                              ← 唯一后端（端口 8080）
├── pom.xml
└── src/main/
    ├── java/com/smartenergy/
    │   ├── backend/                  (原 8080 业务)
    │   │   ├── BackendApplication.java
    │   │   ├── config/               (Security/JWT, OpenAPI, DataInitializer)
    │   │   ├── controller/           (Auth, WorkOrder, Device, Sensor, Dashboard)
    │   │   ├── service/              (5 个)
    │   │   ├── entity/               (4 个: SysUser, WorkOrder, Device, SensorData)
    │   │   ├── mapper/               (4 个 BaseMapper)
    │   │   ├── filter/               (JwtAuthenticationFilter)
    │   │   ├── utils/                (JwtUtils, DeviceStatusHelper)
    │   │   ├── dto/  vo/             (5 + 5)
    │   │   └── service/WorkOrderSyncService.java  ← 🆕 合并新加：本地同步老字段
    │   └── workorder/                ← 🆕 从原 8081 合并
    │       ├── config/               (GlobalExceptionHandler, MybatisPlus)
    │       ├── controller/           (4 个: WorkOrderRead/Assignment, DispatchDashboard, MaintenancePersonnel)
    │       ├── service/              (含 AutoMatchEngine, 不再有 WorkOrderClient)
    │       ├── entity/               (MaintenancePersonnel, WorkOrderAssignment, ExistingWorkOrder, ExistingDevice)
    │       ├── mapper/               (5 个)
    │       ├── dto/  vo/             (5 + 9)
    └── resources/
        ├── application.yml           (合并 MyBatis-Plus, SQL init, SpringDoc)
        └── schema.sql                (原 4 张表 + 🆕 2 张新表 + 6 维修人员 + E001 账号)

workorder-docs/                       ← 设计文档
├── API.md  DB.md  UI.md
```

---

## 同步策略（关键）

| 操作 | 谁 | 通过 |
|---|---|---|
| 读 work_order | 8080 业务 | 直查 DB（只读） |
| 改 work_order.status | 8080 业务 | `WorkOrderService.updateStatus`（前端 PATCH /api/work-orders/{id}/status） |
| 改 work_order.assignee | 8080 业务 | **同模块 `WorkOrderSyncService` → `WorkOrderService.updateStatus`**（无 HTTP） |
| 设备状态联动 | 8080 业务 | WorkOrderServiceImpl 自动 |
| 写 workorder_assignment | 8080 维修模块 | 直写新表 |
| 写 workorder_maintenance_personnel | 8080 维修模块 | 直写新表 |

**前端拖拽改 status 直调 8080 现有 API**。`frontend/src/api/workorder.js` 的 `patchWorkOrderStatus()` 函数封装此调用。

**指派/释放/替换 → 修老字段** 走 `WorkOrderSyncService` 同模块本地方法调用，**不再有跨进程 HTTP 同步**。从根本上消除 8 个连环 bug 的根因层。

---

## 数据库（关键）

`schema.sql` 用 `IF NOT EXISTS` + `ON CONFLICT DO NOTHING` 保证幂等，dev profile 的 `mode: ${SQL_INIT_MODE:always}` 重复启动不会崩溃。

生产环境：`SPRING_PROFILES_ACTIVE=prod` 或 `SQL_INIT_MODE=never` 手动跑 DDL。

---

## 端到端验证（验收清单）

按以下 7 步操作，**全部成功 = Epic 05 验收通过**：

1. **登录分流**：用 `E001/123456` 登录 → 自动跳 `/maintenance`；用 `admin/admin123` 登录 → 跳 `/dashboard` 且侧边栏**看不到**维修中心菜单
2. **角色守卫**：admin 登录后改 URL 访问 `/maintenance/dispatch` → 重定向回 `/dashboard`
3. **人员 CRUD**：在 PersonnelView 新增"测试人员" → 数据库 7 行 → 编辑 → 删除 → 6 行
4. **自动匹配**：DispatchView 选 `MECHANICAL_JAM` 工单 → 推荐 Top 3，李工/王工因有"机械/液压"技能靠前
5. **拖拽改 status**：Kanban 拖 PENDING → IN_PROGRESS，浏览器 Network 看到请求打 `http://localhost:8080/api/work-orders/{id}/status`，work_order.status 真的变 IN_PROGRESS
6. **超载保护**：把张工（max 5）指到第 6 单 → 返回 400
7. **实时刷新**：触发一次新数据上推 → 5s 内新工单自动出现在 PENDING 列

---

## 已知限制 / TODO

- v1 不上 WebSocket，Kanban 用 5s 轮询（性能可接受）
- v1 不上 Flyway，prod 配置 `mode: never` 但 DDL 仍需手动跑
- v1 不上 8080 端 SseController 推事件
- 仅 `MAINTENANCE_ENGINEER` 一种新角色，`MANAGER` 角色预留但未在 sys_user 中预置
- AutoMatchEngine 评分公式硬编码（基础50+技能30+等级15-负载*20）
