# 维修工单与人员调度（Epic 05）— 5 分钟启动指南

> 第25组"智驭能效"项目 — 维修工单与人员调度独立子模块

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

# 2. 启动现有 8080 后端（必须！本模块会调它的 PATCH /api/work-orders/{id}/status）
cd backend && mvn spring-boot:run

# 3. 启动新 8081 后端（维修模块）—— 首次启动会自动创建 E001 账号
cd workorder-backend && mvn spring-boot:run

# 4. 启动前端
cd frontend && npm install && npm run dev
```

**E001 维修账号的创建**：`workorder-backend/src/main/resources/db/init-workorder.sql` 末尾有 1 条 `INSERT INTO sys_user ... ON CONFLICT (username) DO NOTHING`（只有 3 个实际存在的列：`username, password, role`）。语义：**首次启动写入一次；后续启动完全 no-op**（依赖 `ON CONFLICT DO NOTHING` 实现幂等）。

E001 密码 `123456`（BCrypt cost=10），已用 Spring Security `BCryptPasswordEncoder` 实际验证通过；哈希值 `$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q` 写在 init-workorder.sql 末尾。

⚠️ **admin 不动**：admin 账号保留 `deploy/init-sql/init.sql` 种子原密码 `admin123`，本模块不去碰它（遵守"现有表 0 写入"原则）。如果以后想统一为 123456，手动跑 `UPDATE sys_user SET password = '<新哈希>' WHERE username = 'admin';` 即可。

## 访问

| 地址 | 内容 |
|---|---|
| http://localhost:5173 | 前端（登录后自动按角色分流） |
| http://localhost:8081/swagger-ui.html | 新模块 Swagger UI |
| http://localhost:8080/swagger-ui.html | 现有 8080 后端 Swagger UI |

## 测试账号

| 用户名 | 密码 | 角色 | 登录后落地 | 备注 |
|---|---|---|---|---|
| admin | **admin123** | ADMIN | /dashboard (现有大屏) | **保留 deploy/init-sql 种子原密码不动**（不改） |
| E001 | **123456** | MAINTENANCE_ENGINEER | /maintenance (维修中心) | 首次启动由 8081 init-workorder.sql 自动 seed |

> E001 账号由 `workorder-backend/src/main/resources/db/init-workorder.sql` 自动创建，BCrypt 密码 "123456"

---

## 模块架构

```
workorder-backend/                  ← 新模块（端口 8081）
├── pom.xml
├── src/main/java/com/smartenergy/workorder/
│   ├── WorkorderApplication.java
│   ├── config/                     (OpenAPI, GlobalExceptionHandler, MybatisPlus, HttpClient)
│   ├── entity/                     (MaintenancePersonnel, WorkOrderAssignment, ExistingWorkOrder, ExistingDevice)
│   ├── mapper/                     (BaseMapper × 3 + WorkOrderQueryMapper 自定义 JOIN)
│   ├── service/
│   │   ├── MaintenancePersonnelService       (CRUD + 切岗)
│   │   ├── WorkOrderReadService             (JOIN 设备 + 指派人)
│   │   ├── WorkOrderAssignmentService       (assign/release/autoMatch)
│   │   ├── DispatchDashboardService         (summary/board)
│   │   ├── AutoMatchEngine                  (英文 faultType 匹配)
│   │   └── WorkOrderClient                  (调 8080 PATCH /status)
│   ├── controller/                 (4 个 Controller)
│   ├── dto/                        (MaintenancePersonnelRequest, WorkOrderAssignRequest, PageQuery)
│   └── vo/                         (5 个 VO)
└── src/main/resources/
    ├── application.yml             (dev = mode:always)
    ├── application-prod.yml        (prod = mode:never + Flyway 占位)
    └── db/init-workorder.sql       (2 张表 + 6 名种子 + E001 账号)

workorder-docs/                     ← 设计文档
├── API.md
├── DB.md
└── UI.md
```

---

## 跨服务调用（关键）

| 操作 | 谁 | 通过 |
|---|---|---|
| 读 work_order | 新模块 | 直查 DB（只读） |
| 改 work_order.status | **现有 8080** | PATCH /api/work-orders/{id}/status |
| 改 work_order.assignee | **现有 8080** | PATCH /api/work-orders/{id}/status (body 携 assignee) |
| 设备状态联动 | 现有 8080 | WorkOrderServiceImpl 自动 |
| 写 workorder_assignment | 新模块 | 直写新表 |
| 写 workorder_maintenance_personnel | 新模块 | 直写新表 |

**前端拖拽改 status 直调 8080 现有 API**，不经过 8081 中转。`frontend/src/api/workorder.js` 的 `patchWorkOrderStatus()` 函数封装此调用。

---

## 数据库（关键）

`init-workorder.sql` 用 `IF NOT EXISTS` + `ON CONFLICT DO NOTHING` 保证幂等，dev profile 的 `mode: always` 重复启动不会崩溃。

生产环境：`SPRING_PROFILES_ACTIVE=prod` 或 `SQL_INIT_MODE=never` 切换到 `application-prod.yml`（v1 暂未引入 Flyway，留 TODO）。

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
