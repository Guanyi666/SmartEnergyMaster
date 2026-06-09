-- ============================================================================
-- 维修工单与人员调度 —— 数据库初始化（init-workorder.sql）
-- ============================================================================
-- 配合 application.yml 中 spring.sql.init.mode=always
-- 🟢 严重问题 #1 修复：所有 CREATE TABLE 加 IF NOT EXISTS，种子 INSERT 加 ON CONFLICT DO NOTHING
--    让 mode: always 下重复启动幂等不崩溃
--
-- 🟠 与现有表的关系（最重要）：
--   本 SQL 只创建 2 张新表（workorder_maintenance_personnel、workorder_assignment）
--   对现有 4 张表（sys_user / work_order / device / sensor_data）0 DDL 修改
--   关于 sys_user 唯一一次 INSERT：见底部 §4（用 ON CONFLICT DO NOTHING 实现
--   "首次启动写入，后续启动 no-op"，对 sys_user 0 副作用）
--
-- 🟠 字段类型说明：specializations 设计为 TEXT（存 JSON 字符串），不用 JSONB。
--   原因：MyBatis-Plus 自动生成的 UPDATE/INSERT SQL 用 setString() 传 varchar 参数，
--   PG 严格类型检查会报 "column X is of type jsonb but expression is of type
--   character varying"。改 TEXT 后 PG 接受隐式 varchar，应用层用 Jackson 序列化/解析。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 维修人员档案（新表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workorder_maintenance_personnel (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(64),
    avatar_color VARCHAR(16) DEFAULT '#52c8ff',  -- 头像底色
    specializations TEXT NOT NULL DEFAULT '[]', -- 技能标签 JSON 字符串（不用 JSONB，理由见顶部说明）
    skill_level VARCHAR(16) NOT NULL,            -- JUNIOR/INTERMEDIATE/SENIOR/EXPERT
    certification VARCHAR(256),                  -- 证书描述
    current_workload INT NOT NULL DEFAULT 0,     -- 当前在处理工单数
    max_workload INT NOT NULL DEFAULT 5,         -- 最大并行处理数
    is_on_duty BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 一次性迁移：如果表已存在且 specializations 是 JSONB 类型，改为 TEXT（保留数据）
-- ⚠️ Spring 的 ScriptUtils 默认按 ; 切分 SQL，遇到 $$ 包裹的 DO 块会炸
--    改成 TEXT 的迁移已经手动跑过（见 chat 里的 docker exec ALTER），
--    这里不再放 DO 块。如果以后需要重启库 + 已有 JSONB 数据，单独跑：
--      docker exec smart_energy_db psql -U energy_user -d smart_energy -c \
--        "ALTER TABLE workorder_maintenance_personnel ALTER COLUMN specializations TYPE TEXT USING specializations::text;"

-- 索引（specializations 是 TEXT 后 GIN 索引不再适用，改为普通 B-tree 索引或干脆不建）
-- 应用层做 contains 过滤，技能数 < 100，无需建索引
CREATE INDEX IF NOT EXISTS idx_workorder_person_on_duty
    ON workorder_maintenance_personnel (is_on_duty, skill_level);
CREATE INDEX IF NOT EXISTS idx_workorder_person_workload
    ON workorder_maintenance_personnel (current_workload);

-- ---------------------------------------------------------------------------
-- 2. 工单-人员指派关系（事实表，人员指派唯一真实源）
--    FK → workorder_maintenance_personnel(id)
--    work_order_id 不建 FK 以兼容现有数据（不依赖现有 work_order 表结构）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workorder_assignment (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,               -- 关联 work_order.id（不建 FK）
    personnel_id BIGINT NOT NULL REFERENCES workorder_maintenance_personnel(id),
    role VARCHAR(16) NOT NULL DEFAULT 'PRIMARY', -- PRIMARY / ASSIST
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,                       -- 转派/闭环时填
    note VARCHAR(256)
);

CREATE INDEX IF NOT EXISTS idx_workorder_assignment_wo
    ON workorder_assignment (work_order_id, released_at);
CREATE INDEX IF NOT EXISTS idx_workorder_assignment_personnel
    ON workorder_assignment (personnel_id, released_at);
CREATE INDEX IF NOT EXISTS idx_workorder_assignment_active
    ON workorder_assignment (personnel_id) WHERE released_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. 种子数据：6 名维修人员（仅写入新表）
--    specializations 现在是 TEXT，存 JSON 字符串
-- ---------------------------------------------------------------------------
INSERT INTO workorder_maintenance_personnel
    (employee_no, name, phone, specializations, skill_level, certification, max_workload)
VALUES
    ('E001', '张工', '13800000001', '["电气","自动化"]',       'EXPERT',       '高级工程师 / 15年', 5),
    ('E002', '李工', '13800000002', '["机械","液压"]',         'SENIOR',       '机械工程师 / 10年', 4),
    ('E003', '王工', '13800000003', '["电气","机械","液压"]',  'SENIOR',       '复合技师 / 8年',   4),
    ('E004', '赵工', '13800000004', '["仪表","自动化"]',       'INTERMEDIATE', '仪表技师 / 5年',    3),
    ('E005', '孙工', '13800000005', '["机械","焊接"]',         'INTERMEDIATE', '机修工 / 3年',      3),
    ('E006', '周工', '13800000006', '["电气","仪表","自动化"]', 'JUNIOR',       '助理工程师 / 1年',  2)
ON CONFLICT (employee_no) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. 🟠 现有 sys_user 表的"一次性"插入 E001 维修账号
--    语义：首次启动 → 写入；后续启动 → ON CONFLICT DO NOTHING 完全 no-op
--    实际效果：sys_user 表只在第一次启动被影响一次，之后所有启动对该行 0 副作用
--
--    BCrypt hash: $2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q
--    对应密码: 123456（cost=10，与现有 8080 BCryptPasswordEncoder 验证通过）
--    ⚠️ 重要：之前用的 $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--       是错的（用 Spring Security BCryptPasswordEncoder 验证 123456 返回 false），
--       现已替换为上面验证通过的哈希
--
--    sys_user 实际只有 4 列（id, username, password, role），SQL 严格匹配列名
--    admin 行不动：保留 deploy/init-sql 种子原密码 admin123
-- ---------------------------------------------------------------------------
INSERT INTO sys_user (username, password, role)
VALUES (
    'E001',
    '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q',
    'MAINTENANCE_ENGINEER'
)
ON CONFLICT (username) DO NOTHING;
