# 维修工单与人员调度 数据库设计

> DDL 实际文件: `C:\SmartEnergyMaster\workorder-backend\src\main\resources\db\init-workorder.sql`

---

## 1. 表结构概览

| 表 | 用途 | 关系 |
|---|---|---|
| `workorder_maintenance_personnel` | 维修人员档案（含技能、负载） | 自包含 |
| `workorder_assignment` | 工单-人员指派事实表 | FK → workorder_maintenance_personnel |

不修改现有 `work_order` / `device` / `sys_user` 表（除了通过现有 8080 后端 PATCH 间接写入 `work_order.assignee`）。

---

## 2. workorder_maintenance_personnel

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGSERIAL | PK | 自增主键 |
| employee_no | VARCHAR(32) | UNIQUE NOT NULL | 工号（如 E001） |
| name | VARCHAR(64) | NOT NULL | 姓名 |
| phone | VARCHAR(20) | | 电话 |
| email | VARCHAR(64) | | 邮箱 |
| avatar_color | VARCHAR(16) | DEFAULT '#52c8ff' | 头像底色 |
| specializations | JSONB | NOT NULL DEFAULT '[]' | 技能标签数组，如 `["电气","自动化"]` |
| skill_level | VARCHAR(16) | NOT NULL | JUNIOR / INTERMEDIATE / SENIOR / EXPERT |
| certification | VARCHAR(256) | | 证书描述 |
| current_workload | INT | NOT NULL DEFAULT 0 | 当前在处理工单数 |
| max_workload | INT | NOT NULL DEFAULT 5 | 最大并行处理数 |
| is_on_duty | BOOLEAN | NOT NULL DEFAULT TRUE | 是否在岗 |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL DEFAULT NOW() | |

索引：
- `idx_workorder_person_specialization` GIN(specializations)
- `idx_workorder_person_on_duty` (is_on_duty, skill_level)
- `idx_workorder_person_workload` (current_workload)

---

## 3. workorder_assignment

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGSERIAL | PK | 自增主键 |
| work_order_id | BIGINT | NOT NULL | 工单 ID（不建 FK 以兼容现有数据） |
| personnel_id | BIGINT | NOT NULL, FK→workorder_maintenance_personnel(id) | |
| role | VARCHAR(16) | NOT NULL DEFAULT 'PRIMARY' | PRIMARY / ASSIST |
| assigned_at | TIMESTAMP | NOT NULL DEFAULT NOW() | |
| released_at | TIMESTAMP | | 转派/闭环时填 |
| note | VARCHAR(256) | | |

索引：
- `idx_workorder_assignment_wo` (work_order_id, released_at)
- `idx_workorder_assignment_personnel` (personnel_id, released_at)
- `idx_workorder_assignment_active` (personnel_id) WHERE released_at IS NULL（部分索引，加速"在岗"查询）

---

## 4. 种子数据

6 名维修人员（覆盖 4 种技能等级、3 种专业方向），见 `init-workorder.sql` §3。

附带 INSERT 1 条 `sys_user`（username=E001, role=MAINTENANCE_ENGINEER, BCrypt "123456"）用于登录。

---

## 5. 幂等性策略

- 所有 `CREATE TABLE` / `CREATE INDEX` 加 `IF NOT EXISTS`
- 种子 INSERT 加 `ON CONFLICT (employee_no) DO NOTHING`
- 配合 `spring.sql.init.mode=always`（dev profile），重复启动不会崩溃

---

## 6. 生产环境切换

dev: `mode: always`（自动跑 DDL）
prod: `application-prod.yml` 覆盖 `mode: never`，配合 Flyway 做版本化迁移（v1 暂未实施）

切换方式：环境变量 `SQL_INIT_MODE=never` 或 `SPRING_PROFILES_ACTIVE=prod`
