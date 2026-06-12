-- ============================================================================
-- 数据库结构重构 v4
-- 实施日期：2026-06-12
-- 前置条件：已通过 10 项 Pre-Migration Validation
-- 设计文档：docs/开发计划/数据库结构重构方案.md
--
-- 7 大改动组（按顺序执行）：
-- A. 备份
-- B. 改动组 1：两张人员表明确定位（A+P0-1+M-1+M-2 修复）
-- C. 改动组 2：删 spare_part_usage.created_at + 加 user_id FK（I 修复）
-- D. 改动组 3：work_order.latest* 字段 COMMENT 文档化
-- E. 改动组 4：拆 maintenance_sop 子表（B/C/D 修复：纯字符串数组+WITH ORDINALITY）
-- F. 改动组 5：清孤儿+补 FK（E/F+P0-2+P1-4+K 修复）
-- G. 改动组 6：CHECK 约束（G 修复：NOT VALID）
-- H. 改动组 7：workorder_assignment.status（H 修复：backfill 顺序）
-- ============================================================================

-- 设置时区，避免 COMMENT/字符串里有特殊字符
SET client_min_messages = WARNING;

-- ============================================================================
-- A. 备份（留 1 周回退能力）
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = '_backup_workorder_maintenance_personnel') THEN
        CREATE TABLE _backup_workorder_maintenance_personnel AS
            SELECT * FROM workorder_maintenance_personnel;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = '_backup_maintenance_personnel') THEN
        CREATE TABLE _backup_maintenance_personnel AS
            SELECT * FROM maintenance_personnel;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = '_backup_spare_part_usage') THEN
        CREATE TABLE _backup_spare_part_usage AS
            SELECT * FROM spare_part_usage;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = '_backup_workorder_assignment') THEN
        CREATE TABLE _backup_workorder_assignment AS
            SELECT * FROM workorder_assignment;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = '_backup_maintenance_sop') THEN
        CREATE TABLE _backup_maintenance_sop AS
            SELECT * FROM maintenance_sop;
    END IF;
END $$;

-- ============================================================================
-- B. 改动组 1：两张人员表明确定位
-- ============================================================================
BEGIN;

-- 0. P0-1 修复：先在 workorder_maintenance_personnel 加 user_id 列（原表无此列）
ALTER TABLE workorder_maintenance_personnel ADD COLUMN IF NOT EXISTS user_id INTEGER;

-- 1. 把 workorder_maintenance_personnel 的冗余字段搬到 maintenance_personnel
INSERT INTO maintenance_personnel (user_id, employee_no, name, phone, email, specializations, skill_level, certification)
SELECT NULL, wmp.employee_no, wmp.name, wmp.phone, wmp.email,
       wmp.specializations::jsonb, wmp.skill_level, wmp.certification
FROM workorder_maintenance_personnel wmp
ON CONFLICT (employee_no) DO NOTHING;

-- 2. 关联 sys_user（用 employee_no 当登录账号匹配）—— 默认只有首位维修工程师能匹配
UPDATE maintenance_personnel mp
SET user_id = su.id
FROM sys_user su
WHERE su.username = mp.employee_no AND mp.user_id IS NULL;

-- 3. 同步 user_id 到 workorder_maintenance_personnel
UPDATE workorder_maintenance_personnel wmp
SET user_id = mp.user_id
FROM maintenance_personnel mp
WHERE mp.employee_no = wmp.employee_no AND wmp.user_id IS NULL;

-- 4. M-1 修复：先 DROP 引用 current_workload/max_workload 的 CHECK 约束
ALTER TABLE maintenance_personnel DROP CONSTRAINT IF EXISTS ck_maintenance_personnel_workload;

-- 5. M-2 修复：从 maintenance_personnel 删排班列
ALTER TABLE maintenance_personnel
  DROP COLUMN IF EXISTS current_workload,
  DROP COLUMN IF EXISTS max_workload,
  DROP COLUMN IF EXISTS is_on_duty;

-- 6. 删 workorder_maintenance_personnel 的冗余列
ALTER TABLE workorder_maintenance_personnel
  DROP COLUMN IF EXISTS name,
  DROP COLUMN IF EXISTS phone,
  DROP COLUMN IF EXISTS email,
  DROP COLUMN IF EXISTS specializations,
  DROP COLUMN IF EXISTS skill_level,
  DROP COLUMN IF EXISTS certification;

-- 7. 加 FK（ON DELETE RESTRICT，不强制 NOT NULL）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_wmp_user' AND conrelid = 'workorder_maintenance_personnel'::regclass
    ) THEN
        ALTER TABLE workorder_maintenance_personnel
          ADD CONSTRAINT fk_wmp_user
          FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE RESTRICT NOT VALID;
        ALTER TABLE workorder_maintenance_personnel VALIDATE CONSTRAINT fk_wmp_user;
    END IF;
END $$;

-- 8. 加 UNIQUE 索引（仅约束非 NULL 值）
CREATE UNIQUE INDEX IF NOT EXISTS ux_workorder_personnel_user
  ON workorder_maintenance_personnel(user_id);

COMMIT;

-- ============================================================================
-- C. 改动组 2：删除 1 个真死字段 + 新增 spare_part_usage.user_id FK（带 backfill）
-- ============================================================================
BEGIN;

ALTER TABLE spare_part_usage DROP COLUMN IF EXISTS created_at;

-- 加 user_id 列（nullable 以兼容历史 'SOP自动' 字符串）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'spare_part_usage' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE spare_part_usage
          ADD COLUMN user_id INTEGER REFERENCES sys_user(id) ON DELETE SET NULL;
    END IF;
END $$;

-- backfill：从 user_name 推断
UPDATE spare_part_usage spu
SET user_id = su.id
FROM sys_user su
WHERE su.username = spu.user_name AND spu.user_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_spare_part_usage_user_id ON spare_part_usage(user_id);

COMMIT;

-- ============================================================================
-- D. 改动组 3：work_order 触发快照字段语义文档化（不改 schema）
-- ============================================================================
DO $$
BEGIN
    EXECUTE 'COMMENT ON COLUMN work_order.latest_temperature IS ''故障触发时刻温度快照（℃），sensor_data 表保留完整时序''';
    EXECUTE 'COMMENT ON COLUMN work_order.latest_vibration IS ''故障触发时刻振动快照（mm/s），sensor_data 表保留完整时序''';
    EXECUTE 'COMMENT ON COLUMN work_order.latest_pressure IS ''故障触发时刻压力快照（kPa），sensor_data 表保留完整时序''';
EXCEPTION WHEN OTHERS THEN
    NULL;  -- 容错：列不存在时跳过
END $$;

-- ============================================================================
-- E. 改动组 4：拆分 maintenance_sop 的 JSON 数组
-- ============================================================================
BEGIN;

-- 4.1 maintenance_sop_step
CREATE TABLE IF NOT EXISTS maintenance_sop_step (
    id BIGSERIAL PRIMARY KEY,
    sop_id BIGINT NOT NULL REFERENCES maintenance_sop(id) ON DELETE CASCADE,
    step_no INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    expected_minutes INTEGER,
    warning TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sop_id, step_no)
);
CREATE INDEX IF NOT EXISTS ix_sop_step_sop ON maintenance_sop_step(sop_id, step_no);

-- 数据迁移：steps 是纯字符串数组
INSERT INTO maintenance_sop_step (sop_id, step_no, title, content)
SELECT
  s.id,
  t.ord::int,
  '步骤 ' || t.ord,
  t.step_text
FROM maintenance_sop s,
LATERAL jsonb_array_elements_text(s.steps::jsonb) WITH ORDINALITY AS t(step_text, ord)
WHERE s.steps IS NOT NULL AND s.steps::text != '[]'
ON CONFLICT (sop_id, step_no) DO NOTHING;

ALTER TABLE maintenance_sop DROP COLUMN IF EXISTS steps;

-- 4.2 maintenance_sop_required_part
CREATE TABLE IF NOT EXISTS maintenance_sop_required_part (
    id BIGSERIAL PRIMARY KEY,
    sop_id BIGINT NOT NULL REFERENCES maintenance_sop(id) ON DELETE CASCADE,
    part_id BIGINT REFERENCES spare_part(id) ON DELETE SET NULL,
    part_code VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sop_req_part_qty CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS ix_sop_req_part_sop ON maintenance_sop_required_part(sop_id);
CREATE INDEX IF NOT EXISTS ix_sop_req_part_part ON maintenance_sop_required_part(part_id);

-- 数据迁移：required_parts 是纯字符串数组
INSERT INTO maintenance_sop_required_part (sop_id, part_id, part_code)
SELECT
  s.id,
  sp.id,
  t.part_code
FROM maintenance_sop s,
LATERAL jsonb_array_elements_text(s.required_parts::jsonb) AS t(part_code)
LEFT JOIN spare_part sp ON sp.part_code = t.part_code
WHERE s.required_parts IS NOT NULL AND s.required_parts::text != '[]';

ALTER TABLE maintenance_sop DROP COLUMN IF EXISTS required_parts;

COMMIT;

-- ============================================================================
-- F. 改动组 5：补齐孤儿外键 + 索引（E/F+P0-2+P1-4+K 修复）
-- ============================================================================
BEGIN;

-- 1. 清孤儿（E 修复）：work_order.sop_id
UPDATE work_order SET sop_id = NULL
WHERE sop_id IS NOT NULL
  AND sop_id NOT IN (SELECT id FROM maintenance_sop);

-- 2. 清孤儿（F 修复）：repair_case.related_work_order_id
UPDATE repair_case SET related_work_order_id = NULL
WHERE related_work_order_id IS NOT NULL
  AND related_work_order_id NOT IN (SELECT id FROM work_order);

-- 3. P0-2 修复：workorder_assignment.personnel_id 改为可空
ALTER TABLE workorder_assignment ALTER COLUMN personnel_id DROP NOT NULL;

-- 4. P1-4 修复：workorder_assignment.work_order_id 加 FK
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_assignment_work_order' AND conrelid = 'workorder_assignment'::regclass
    ) THEN
        ALTER TABLE workorder_assignment
          ADD CONSTRAINT fk_assignment_work_order
          FOREIGN KEY (work_order_id) REFERENCES work_order(id) ON DELETE CASCADE NOT VALID;
        ALTER TABLE workorder_assignment VALIDATE CONSTRAINT fk_assignment_work_order;
    END IF;
END $$;

-- 5. 加 work_order.sop_id FK
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_work_order_sop' AND conrelid = 'work_order'::regclass
    ) THEN
        ALTER TABLE work_order
          ADD CONSTRAINT fk_work_order_sop
          FOREIGN KEY (sop_id) REFERENCES maintenance_sop(id) ON DELETE SET NULL NOT VALID;
        ALTER TABLE work_order VALIDATE CONSTRAINT fk_work_order_sop;
    END IF;
END $$;

-- 6. 加 repair_case.related_work_order_id FK
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_repair_case_work_order' AND conrelid = 'repair_case'::regclass
    ) THEN
        ALTER TABLE repair_case
          ADD CONSTRAINT fk_repair_case_work_order
          FOREIGN KEY (related_work_order_id) REFERENCES work_order(id) ON DELETE SET NULL NOT VALID;
        ALTER TABLE repair_case VALIDATE CONSTRAINT fk_repair_case_work_order;
    END IF;
END $$;

-- 7. 加新 FK 索引（K 修复）
CREATE INDEX IF NOT EXISTS ix_work_order_sop_id ON work_order(sop_id);
CREATE INDEX IF NOT EXISTS ix_repair_case_related_work_order_id ON repair_case(related_work_order_id);
CREATE INDEX IF NOT EXISTS ix_workorder_assignment_work_order_id ON workorder_assignment(work_order_id);

-- 8. audit_log 新增字段
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS result VARCHAR(16);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS duration_ms INT;

-- 9. CHECK 约束 result
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_log_result
  CHECK (result IS NULL OR result IN ('SUCCESS', 'FAILED'));

-- 10. audit_log 6 预留字段加 COMMENT（P1-3 决策）
DO $$
BEGIN
    EXECUTE 'COMMENT ON COLUMN audit_log.actor_user_id IS ''预留：审计追溯，后续从 actor_username 解析 sys_user.id''';
    EXECUTE 'COMMENT ON COLUMN audit_log.request_id IS ''预留：request 拦截器写入，关联同一次 HTTP 调用''';
    EXECUTE 'COMMENT ON COLUMN audit_log.ip_address IS ''预留：request 拦截器写入，HttpServletRequest.getRemoteAddr()''';
    EXECUTE 'COMMENT ON COLUMN audit_log.user_agent IS ''预留：request 拦截器写入，HttpServletRequest.getHeader("User-Agent")''';
    EXECUTE 'COMMENT ON COLUMN audit_log.old_value IS ''预留：diff tracking，UPDATE 操作前的值''';
    EXECUTE 'COMMENT ON COLUMN audit_log.new_value IS ''预留：diff tracking，UPDATE 操作后的值''';
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- 11. 其他新索引
CREATE INDEX IF NOT EXISTS ix_device_type_status ON device(device_type, status);
CREATE INDEX IF NOT EXISTS ix_audit_log_actor_username_time ON audit_log(actor_username, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_log_action ON audit_log(action, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_work_order_fault_type ON work_order(fault_type, created_at DESC);

COMMIT;

-- ============================================================================
-- G. 改动组 6：状态字段 CHECK 约束（NOT VALID）
-- ============================================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_work_order_status') THEN
        ALTER TABLE work_order ADD CONSTRAINT ck_work_order_status
          CHECK (status IN ('PENDING', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')) NOT VALID;
        ALTER TABLE work_order VALIDATE CONSTRAINT ck_work_order_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_work_order_priority') THEN
        ALTER TABLE work_order ADD CONSTRAINT ck_work_order_priority
          CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')) NOT VALID;
        ALTER TABLE work_order VALIDATE CONSTRAINT ck_work_order_priority;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_work_order_source') THEN
        ALTER TABLE work_order ADD CONSTRAINT ck_work_order_source
          CHECK (source IN ('AUTO', 'MANUAL', 'SCHEDULED')) NOT VALID;
        ALTER TABLE work_order VALIDATE CONSTRAINT ck_work_order_source;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_device_status') THEN
        ALTER TABLE device ADD CONSTRAINT ck_device_status
          CHECK (status IN ('STOPPED', 'IDLE', 'RUNNING', 'HIGH_LOAD', 'FAULT', 'MAINTENANCE', 'OFFLINE')) NOT VALID;
        ALTER TABLE device VALIDATE CONSTRAINT ck_device_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_user_status') THEN
        ALTER TABLE sys_user ADD CONSTRAINT ck_sys_user_status
          CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')) NOT VALID;
        ALTER TABLE sys_user VALIDATE CONSTRAINT ck_sys_user_status;
    END IF;
END $$;

COMMIT;

-- ============================================================================
-- H. 改动组 7：workorder_assignment 释放语义优化
-- ============================================================================
BEGIN;

-- 1. 加列（nullable，不设默认）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workorder_assignment' AND column_name = 'status'
    ) THEN
        ALTER TABLE workorder_assignment ADD COLUMN status VARCHAR(16);
    END IF;
END $$;

-- 2. backfill（H 修复）
UPDATE workorder_assignment SET status = 'RELEASED' WHERE released_at IS NOT NULL;
UPDATE workorder_assignment SET status = 'ACTIVE'   WHERE released_at IS NULL;

-- 3. 加 CHECK
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assignment_status') THEN
        ALTER TABLE workorder_assignment ADD CONSTRAINT ck_assignment_status
          CHECK (status IN ('ACTIVE', 'RELEASED', 'TRANSFERRED'));
    END IF;
END $$;

-- 4. SET NOT NULL
ALTER TABLE workorder_assignment ALTER COLUMN status SET NOT NULL;

-- 5. 设默认值
ALTER TABLE workorder_assignment ALTER COLUMN status SET DEFAULT 'ACTIVE';

COMMIT;

-- 收尾日志
DO $$
BEGIN
    RAISE NOTICE '11_db_restructure_v1.sql 执行完成';
END $$;
