# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SmartEnergyMaster（智驭能效）是一个工业能源管理平台，用于监控钢铁厂核心设备（电弧炉、循环水泵、空压机）的实时运行状态。系统接收传感器遥测数据，自动检测设备故障，生成维修工单，并结合分时电价给出 AI 调度建议。

---

## 项目结构总览

```
SmartEnergyMaster/
├── backend/          # Spring Boot 后端 — REST API、认证、业务逻辑、数据库访问
├── frontend/         # Vue 3 前端 — 监控大屏、设备管理、数据分析页面
├── deploy/           # Docker 部署 — TimescaleDB 容器、后端 Dockerfile、初始化 SQL
├── data/             # 数据管道 — UCI 数据集下载、设备仿真数据泵
├── ai_models/        # AI 模型（预留，当前为空壳）
└── docs/             # 项目文档
```

### 各模块职责

| 模块 | 职责说明 |
|------|----------|
| `backend/` | Spring Boot 3.4.5 后端服务。提供 REST API 接口，包含用户认证（Spring Security + JWT）、设备管理 CRUD、传感器数据接收与查询、仪表盘聚合统计、维修工单生命周期管理。使用 MyBatis-Plus 访问 TimescaleDB 数据库。 |
| `frontend/` | Vue 3 + Vite 前端 SPA。包含四个页面：登录页、实时监控大屏（Dashboard）、历史分析页（Analysis）、设备管理页（Devices）。使用 Element Plus 组件库、ECharts 图表、Pinia 状态管理。大屏每 5 秒轮询后端，支持设备切换和工单快捷操作。 |
| `deploy/` | Docker 容器化部署。`docker-compose.yml` 启动 TimescaleDB（pg14），自动挂载 `init-sql/` 目录完成建表。`Dockerfile-backend` 将 Spring Boot JAR 打包为 OpenJDK 17 镜像。 |
| `data/` | Python 数据管道。`download_csv.py` 从 UCI 下载钢铁行业能耗数据集（id=851）。`data_pump.py` 是设备仿真器，模拟 3 台设备的耦合物理模型，先回填 24 小时历史数据，再以 3 秒间隔持续推送实时数据到后端 `/api/sensor/upload` 接口，并随机注入故障场景。 |
| `ai_models/` | 预留目录，计划用于 AI 能耗预测、异常检测等模型。当前仅有占位文件。 |

---

## 当前已实现的功能业务

### 1. 用户认证与权限
- 用户注册（`POST /api/auth/register`）：新用户默认角色为 `OPERATOR`
- 用户登录（`POST /api/auth/login`）：验证用户名密码，返回 JWT Token（24h 有效）
- 内置管理员账号：`2026010001 / admin123`（通过 `DataInitializer` 在启动时自动创建；Docker 部署时由 `init.sql` 预置同一密码）
- Spring Security 拦截除白名单外的所有请求，白名单包括：`/api/auth/**`、`/api/sensor/upload`、`/api/sensor/latest/**`、`/api/sensor/history/**`
- 前端路由守卫：未登录自动跳转 `/login`，已登录访问 `/login` 自动跳转 `/dashboard`

### 2. 设备管理
- 设备列表展示（`GET /api/devices`）：每台设备附带最新传感器数据、当前状态、活跃工单数
- 设备创建、编辑、删除（CRUD）
- 设备编码唯一性校验
- 设备状态自动推断：根据最新传感器 `operatingStatus` 映射为 `STOPPED / IDLE / RUNNING / HIGH_LOAD`，结合工单状态叠加 `FAULT / MAINTENANCE`，超过 5 分钟无数据标记 `OFFLINE`
- 前端 `/devices` 页提供设备管理表格

### 3. 传感器数据接收与查询
- 数据上报（`POST /api/sensor/upload`）：接收设备遥测数据（功耗、温度、振动、压力、CO₂排放、电价时段等）
- 最新数据查询（`GET /api/sensor/latest/{deviceCode}`）：返回指定设备最近一条传感器记录
- 历史数据查询（`GET /api/sensor/history/{deviceCode}?hours=24`）：返回指定时间窗口内的时序数据，供前端绘制趋势图

### 4. 自动故障检测与告警
- **机械卡涩（MECHANICAL_JAM）**：设备空转状态（operatingStatus=1）且振动 > 15.0 → 自动创建 HIGH 优先级维修工单
- **冷却中断（COOLING_INTERRUPT）**：温度 > 1000°C 且压力 < 50 kPa → 自动创建 CRITICAL 优先级维修工单
- 同一设备 + 同一故障类型已有未闭环工单时，不会重复创建（幂等保护）

### 5. 维修工单管理
- 工单列表查询（`GET /api/work-orders`）：支持按状态筛选
- 活跃告警查询（`GET /api/work-orders/active-alerts?limit=5`）：返回 PENDING/IN_PROGRESS 状态的工单
- 工单状态流转（`PATCH /api/work-orders/{id}/status`）：`PENDING → IN_PROGRESS → RESOLVED`
- 工单状态变更自动联动设备状态：确认处理 → 设备变更为 `MAINTENANCE`，闭环 → 恢复为传感器推导状态
- 工单闭环时记录 `acceptedAt` 和 `resolvedAt` 时间戳
- 前端大屏右侧面板展示待处理工单列表，支持一键"确认处理"和"已修复"操作

### 6. 仪表盘与调度建议
- 聚合总览（`GET /api/dashboard/summary`）：全厂总功耗、累计碳排放、当前电价区间、运行/离线设备数、活跃告警数、焦点设备详情
- 调度建议（`GET /api/dashboard/dispatch-advice`）：基于焦点设备的实时数据 + 电价时段，给出 4 级调度建议：
  - `CRITICAL`：设备存在未闭环工单，建议优先完成检修
  - `WARN`：当前处于峰/尖峰电价且负荷偏高，建议推迟排产避峰
  - `GOOD`：当前处于谷/深谷电价窗口，建议加大排产
  - `INFO`：负荷与电价可控，维持当前策略
- 前端 `/dashboard` 大屏：顶部 3 张指标卡（总有功功率、累计碳排放、电价区间），中部双仪表盘（温度/压力）+ 调度建议 + 工单列表，底部设备状态总览条

### 7. 数据仿真器
- 基于 UCI 钢铁能耗数据集（id=851）的真实数据驱动
- 电弧炉仿真器：含炉膛热量、轴承健康度、冷却健康度等物理状态变量，模拟升温/降温惯性
- 水泵仿真器：根据电弧炉状态联动，冷却负荷随炉温变化
- 空压机仿真器：根据电弧炉状态联动，气压需求追踪负载
- 支持概率性故障注入（空转卡涩、高负荷冷却中断）
- 先回填 24 小时历史（每 15 分钟一个点，共 96 点），再切换为实时推送模式

---

## 项目计划对照：已完成 vs 未完成

以下对照《第25组-智驭能效-项目计划报告》（docs/）中规划的功能模块，逐项标注当前实现状态。

### Sprint 1（基础架构与核心监控）— 基本完成

| 计划项 | 状态 | 说明 |
|--------|------|------|
| Spring Boot 后端框架搭建 | ✅ 完成 | 含 Spring Security、JWT、MyBatis-Plus |
| Vue 前端项目初始化 | ✅ 完成 | 含 Vite、Element Plus、ECharts、Pinia |
| 数据库表结构设计与 ER 建模 | ✅ 完成 | TimescaleDB 超表，4 张业务表 |
| 数据采集与处理模块 | ✅ 完成 | `data_pump.py` 仿真 + `POST /api/sensor/upload` |
| 全局能耗监控大屏 | ✅ 完成 | DashboardView，5 秒轮询 |
| 实时异常预警（静态阈值） | ⚠️ 部分 | 仅 2 条规则，缺少阈值配置页面和通知渠道 |
| Docker 部署 | ✅ 完成 | `docker-compose.yml` + `Dockerfile-backend` |
| 单元测试与接口测试 | ❌ 缺失 | 仅有默认 `contextLoads()`，无业务测试 |

### Sprint 2（预测与优化模块）— 基本未实现

| 计划项 | 状态 | 说明 |
|--------|------|------|
| 历史时序数据集与特征工程 | ❌ 缺失 | `data_pump.py` 仅转发原始数据，未做特征构建 |
| LSTM/Transformer 能耗预测模型 | ❌ 缺失 | `ai_models/` 为空壳 |
| 预测模型封装为 REST API | ❌ 缺失 | 无 Python 模型服务 |
| 未来 30 分钟能耗趋势曲线可视化 | ❌ 缺失 | AnalysisView 仅有基础历史曲线 |
| 自适应优化控制算法（RL/模糊控制） | ❌ 缺失 | 无此模块 |
| 控制建议生成接口 | ❌ 缺失 | 无此 API |
| 人机协同确认机制 | ❌ 缺失 | 无此交互 |
| 生产调度员专用界面 | ❌ 缺失 | 前端仅有 3 个页面，无调度视角 |
| "能耗-产能"多维度对比图表 | ❌ 缺失 | 无产能数据维度 |
| NLG 智能诊断报告生成 | ❌ 缺失 | 无自然语言生成模块 |
| 一键导出 PDF 诊断报告 | ❌ 缺失 | 无报告导出功能 |

### Sprint 3（集成测试与收尾）— 尚未开始

| 计划项 | 状态 |
|--------|------|
| 全模块端到端联调 | ❌ |
| 系统集成测试 / 压力测试 | ❌ |
| 用户操作手册与部署指南 | ❌ |
| API 接口文档（Swagger） | ❌ |

### 核心功能模块对照（来自 docs/功能说明.txt）

| 四大模块 | 完成度 | 差距分析 |
|----------|--------|----------|
| ① 数字孪生底座 | ⬛⬛⬛⬜ 75% | 数据泵、TimescaleDB、分时电价已就绪。缺少：数据融合增强、交叉约束变量派生（温度/振动/压力目前是仿真器算的，不是真实物理方程）、数据质量校验 |
| ② 电力峰值预测与智能调度 | ⬜⬜⬜⬜ 0% | 完全缺失。需要：LSTM/Transformer 时序预测模型、负荷预测 API、启发式优化/RL 算法、柔性负荷转移指令、错峰排产建议 |
| ③ 多源传感器交叉验证与根因诊断 | ⬛⬜⬜⬜ 15% | 仅有 2 条双判据规则（振动+空转=卡涩，温度+压力=冷却中断）。缺少：多维度交叉验证矩阵、根因诊断链路、NLG 自然语言解释、动态阈值（基于历史基线）、误报/漏报率统计 |
| ④ 全栈闭环 | ⬛⬛⬛⬜ 70% | 前后端数据链路畅通。缺少：多角色视图（管理层/操作员/调度员/运维）、Swagger API 文档、自动化测试、Grafana 可选集成 |

---

## 建议补充的功能

### 一、基础功能补全（优先级：高）

这些是项目计划中明确规划但尚未实现的功能，属于"补齐欠账"：

**1. LSTM 能耗预测模型（ai_models/）**
- 从 `sensor_data` 表中导出历史时序数据，构建训练/验证集
- 搭建 LSTM 或 Transformer 模型，输入：`usage_kwh`、`temperature`、`vibration`、`pressure`、`nsm`、`week_status`、`load_type`、`xian_price_tier`
- 输出：未来 30 分钟至 1 小时的 `usage_kwh` 预测曲线及置信区间
- 封装为 Flask/FastAPI 服务，供后端 Java 调用
- 前端展示预测曲线（叠加在实际曲线上，用虚线 + 置信区间着色）

**2. 智能告警升级（多维度交叉验证 + NLG）**
- 当前仅有 2 条硬阈值规则，需扩充为：
  - 基于历史统计的动态基线（滑动窗口均值 ± 3σ）
  - 预测残差异常检测（预测值 vs 实际值偏差超阈值）
  - 多维交叉矩阵：`温度↑ + 振动↑ + 压力↓ + 空转 = 轴承损坏`、`功耗↑ + 温度↑ + 压力↓ + 高负荷 = 冷却断流`
- 告警信息采用三段式结构：**异常现象 → 根因分析 → 处置建议**
- 集成 NLG 模板引擎，自动生成可读的告警文本

**3. 前端分析页（AnalysisView）完善**
- 增加时间范围选择器（1h / 6h / 24h / 7d）
- 多设备曲线叠加对比
- 预测曲线与实际曲线的对比展示
- "能耗-产能"多维度对比图表
- 电价时段背景着色（峰/谷/平/尖不同颜色带）

**4. API 文档与 Swagger 集成**
- 后端引入 SpringDoc OpenAPI
- 所有 Controller 添加 `@Operation` 注解
- 开发时可访问 `/swagger-ui.html` 调试接口

**5. 单元测试与集成测试**
- Service 层业务逻辑单元测试（JUnit 5 + Mockito）
- Controller 层接口测试（MockMvc / `@WebMvcTest`）
- 传感器数据上传 → 故障检测 → 工单创建 的集成测试

**6. 运维管理后台**
- 系统配置页面（告警阈值配置、电价时段配置）
- 数据质量看板（采集成功率、延迟统计、异常值统计）
- 用户管理界面（角色分配、启停用）

### 二、拓展功能（优先级：中高）

这些是项目计划中提及但尚未深入设计的智能化能力：

**7. 柔性负荷调度优化**
- 基于预测结果 + 电价时段的排产优化算法
- 输出可执行建议："建议将 EAF-01 的下一炉次推迟 30 分钟，避开尖峰电价窗口"
- 支持操作员确认/驳回，形成人机协同闭环
- 模拟节能收益计算（推迟排产可节省的电费）

**8. NLG 诊断报告与 PDF 导出**
- 基于告警事件 + 历史数据自动生成诊断报告
- 报告结构：摘要 → 异常时间线 → 传感器曲线截图 → 根因分析 → 处置建议 → 历史同类事件对比
- 前端一键导出为 PDF（使用 jsPDF 或后端渲染）

**9. 多角色视图**
- 管理层视角（/admin）：全局能耗总览、碳排趋势、成本统计、节能 KPI
- 操作员视角（/operator）：设备实时状态、告警列表、工单处理、调度建议
- 调度员视角（/scheduler）：预测曲线、排产计划、电价窗口、负荷转移建议

**10. Grafana 可选集成**
- 利用 TimescaleDB 的 Grafana 数据源插件
- 搭建独立的 Grafana 可视化面板作为高级监控方案
- 预置仪表盘 JSON 模板，一键导入

**11. 能耗异常模式挖掘（离线分析）**
- 对历史传感器数据进行聚类分析（DBSCAN / K-Means）
- 自动识别典型工况模式（正常生产 / 保温 / 检修 / 异常）
- 建立各模式的正常参数包络线，用于在线异常检测

**12. 强化学习自适应控制（进阶）**
- 定义 RL 环境：状态 = 传感器读数 + 电价时段，动作 = 设备功率调整幅度，奖励 = 节能收益 - 安全惩罚
- 使用 DQN / PPO 训练策略网络
- 输出控制建议 → 前端展示 → 操作员确认后执行

---

## 常用命令

### 数据库
```bash
cd deploy && docker-compose up -d          # 启动 TimescaleDB（端口 5432）
cd deploy && docker-compose down           # 停止数据库（数据保留在 Docker Volume 中）
```
首次启动时 `deploy/init-sql/init.sql` 自动执行，创建所有表并将 `sensor_data` 设为 TimescaleDB 超表。

### 后端（Spring Boot + Maven）
```bash
cd backend && mvn spring-boot:run          # 开发模式启动，端口 :8080（需先启动数据库）
cd backend && mvn clean package -DskipTests # 构建 JAR 到 backend/target/
cd backend && mvn test                      # 运行测试（默认仅含 contextLoads）
```
数据库连接配置在 `backend/src/main/resources/application.yml`，需与 `docker-compose.yml` 中的凭据一致。

### 前端（Vue 3 + Vite）
```bash
cd frontend && npm install                  # 安装依赖（首次）
cd frontend && npm run dev                  # 开发服务器，端口 :5173，/api 代理到 :8080
cd frontend && npm run build                # 生产构建 → frontend/dist/
```
Vite 开发服务器将 `/api` 代理到 `http://localhost:8080`，开发时无跨域问题。

### 数据仿真器（Python）
```bash
cd data && pip install -r requirements.txt  # 依赖：pandas, requests, ucimlrepo
cd data && python data_pump.py              # 先回填 24h 历史，再持续推送实时数据
cd data && python download_csv.py           # 仅下载 UCI 数据集到 CSV
```
`data_pump.py` 模拟 3 台设备，通过 `POST /api/sensor/upload` 每 3 秒上报一次数据，需后端已启动。

### Docker 全栈部署
```bash
cd backend && mvn clean package -DskipTests
docker build -t energy-backend -f deploy/Dockerfile-backend .
docker run -d -p 8080:8080 --name energy-api --network deploy_energy_network energy-backend
```

---

## 架构要点

### 后端分层（包路径 `com.smartenergy.backend`）

| 层 | 包 | 职责 |
|----|-----|------|
| Controller | `controller/` | REST 接口，返回 `ResponseEntity` 或 VO 对象 |
| Service | `service/` + `service/impl/` | 业务逻辑，写操作标注 `@Transactional` |
| Mapper | `mapper/` | MyBatis-Plus `BaseMapper` 接口，无需 XML |
| Entity | `entity/` | 数据库表映射 POJO，Lombok `@Data` |
| DTO | `dto/` | 请求体对象，使用 Jakarta `@Valid` 校验 |
| VO | `vo/` | 响应体对象，由 Service 层组装返回 |

### 认证流程（注意：存在两套 JWT 实现）

1. **登录**（`UserServiceImpl`）：使用 **Hutool** `JWT.create().sign()`，密钥 `"SmartEnergyMasterSecretKey"`，PayLoad 包含 `username`、`role`、`expire_time`
2. **过滤器**（`JwtAuthenticationFilter`）：使用 **Hutool** `JWTUtil.verify()` 校验 `Authorization: Bearer <token>` 请求头，校验通过后加载 `UserDetails` 并设置 `SecurityContext`
3. **工具类**（`JwtUtils`）：使用 **jjwt** 库，密钥不同，**当前未被过滤器链使用**

### 设备状态状态机

- **传感器推导**：`operatingStatus` → `0=STOPPED, 1=IDLE, 2=RUNNING, 3=HIGH_LOAD`
- **叠加状态**：有活跃工单 → `FAULT`，若工单状态为 `IN_PROGRESS` → `MAINTENANCE`
- **离线判定**：最近一条数据的时间超过 5 分钟 → `OFFLINE`
- 最终状态由 `DeviceStatusHelper.resolveStatus()` 统一计算

### 前端组件树

```
App.vue
└── Router (createWebHistory)
    ├── LoginView（公开，/login）
    └── MainLayout（需认证的布局壳）
        ├── DashboardView（/ → /dashboard）— 5 秒轮询，仪表盘 + 告警 + 调度建议
        ├── AnalysisView（/analysis）— 历史趋势图表
        └── DevicesView（/devices）— 设备管理列表
```

- **状态管理**：Pinia `useAuthStore`，token 和用户信息持久化到 `localStorage`
- **HTTP 请求**：Axios 实例（`api/http.js`），请求拦截器自动附加 Bearer Token，响应拦截器自动解包 `.data` 并在出错时弹出 `ElMessage.error`
- **轮询机制**：`usePollingTask` 组合式函数，基于 `setTimeout` 循环，页面切到后台自动暂停、切回前台立即刷新

### 数据库（TimescaleDB / PostgreSQL）

关键设计：
- `sensor_data` 是 TimescaleDB **超表**（按 `time` 列），支持按时间分片的时序查询
- 复合索引 `ix_sensor_data_device_time` 覆盖 `(device_id, time DESC)`，优化按设备查询最新数据的性能
- `work_order` 表建有 `(status, created_at DESC)` 索引
- `sys_user` 表通过 `DataInitializer` 在启动时自动创建 `2026010001/admin123`（BCrypt 加密），仅当用户不存在时插入
- `sensor_data.device_id` 级联删除，工单不受设备删除影响

### 数据仿真模型

`data_pump.py` 以 UCI 钢铁行业能耗数据集（id=851）为原始输入，在时序推进中维护物理状态：

- **FurnaceSimulator（电弧炉）**：主设备——根据数据行推断工况阶段（STOPPED/IDLE/RUNNING/HIGH_LOAD），炉膛热量和轴承/冷却健康度逐步衰减，概率性注入机械卡涩和冷却中断故障
- **PumpSimulator（水泵）**：从电弧炉状态派生——冷却负荷跟踪炉温变化，压力与炉压联动
- **CompressorSimulator（空压机）**：从电弧炉状态派生——气压需求跟踪负载变化

---

## Git 协作规范（来自 README）

- `main` — 生产稳定分支，禁止直接提交
- `develop` — 开发主分支
- `feature/xxx` / `fix/xxx` / `hotfix/xxx` — 功能/修复分支
- Commit 格式：`type(scope): message`（如 `feat(backend): add energy API`）
- 合并进 main 必须通过 PR

---

## 当前进度（2026-06-08）

> **项目阶段**：第 1 天（敏捷项目启动与规划），10 天项目周期

### 已完成

| 模块 | 内容 |
|------|------|
| 环境搭建 | Docker、Node.js、Java、Maven、Python 全部就绪；TimescaleDB 容器化运行 |
| Sprint 1 后端 | 认证（JWT + Spring Security）、设备 CRUD、传感器上传/查询、故障检测（2 条规则）、工单管理（PENDING→IN_PROGRESS→RESOLVED）、仪表盘聚合 + 4 级调度建议 |
| Sprint 1 前端 | 登录页、Dashboard 大屏（5 秒轮询 + 指标卡 + 双仪表盘 + 工单面板 + 设备状态条）、设备管理页、AnalysisView 基础版 |
| Swagger | SpringDoc OpenAPI 集成，5 个 Controller + 5 个 DTO + 5 个 VO + 4 个 Entity 全部添加注解 |
| 数据仿真器 | data_pump.py（3 台设备耦合模型 + 24h 历史回填 + 3 秒实时推送 + 概率性故障注入） |
| 第一阶段文档 | 团队名单、分工表、团队章程、用户故事、产品待办列表（静态+动态）、需求规格说明书、需求评审记录、技术选型说明、风险识别、开发计划表 |

### 下一步（第 1-2 天）

1. 补全第一阶段文档：用户故事地图、思维导图/甘特图、项目章程、项目管理计划书、环境配置截图、分支管理规范
2. 提交 **第 1 次实验报告**（第 2 天截止）
3. 准备进入迭代 1 Sprint（第 3 天启动）

### 关键参考文件

| 文件 | 用途 |
|------|------|
| `docs/项目任务全景图_四层结构.md` | 全部任务清单（Epic→Feature→Story→Task 四级），含状态标记 |
| `docs/用户故事_全功能点.md` | 137 条用户故事（标准三段式），覆盖全部功能点 |
| `docs/项目任务清单.md` | 原始任务清单（文档 + 代码，含附录） |
| `docs/实验一：.../09.智驭能效_开发计划表.md` | 10 天详细开发计划，含迭代分工 |

### 未开始的大模块

- **Phase 1B~1F**：Redis 缓存、人员管理、审计日志、统一异常处理、分页
- **Phase 2A~2C**：LLM 故障诊断、多 Agent 系统、AI 能耗预测（LSTM/Transformer）
- **Phase 3A~3G**：备件管理、SOP 知识库、设备健康度、成本分析、碳排追踪、通知系统、数据导出
- **Phase 4A~4D**：5 种角色专属页面（维修工作台/调度面板/管理总览/用户管理/系统配置）
- **Phase 5A~5C**：Docker 全栈编排、环境变量管理、联调验证
- **Epic 13**：单元测试、集成测试、E2E 测试
- **Epic 14**：迭代 1~3 敏捷过程文档 + 答辩材料
