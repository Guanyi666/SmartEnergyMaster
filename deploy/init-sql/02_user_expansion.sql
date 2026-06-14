-- Epic 01-2-2: sys_user 扩展 + 维修人员员工档案（V2 重构：排班字段迁出，employee_no 由 sys_user.username 统一管理）

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS nickname VARCHAR(64),
    ADD COLUMN IF NOT EXISTS department VARCHAR(64),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS email VARCHAR(128),
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255),
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS ix_sys_user_status ON sys_user (status);
CREATE INDEX IF NOT EXISTS ix_sys_user_department ON sys_user (department);

-- 🆕 V2 重构：maintenance_personnel = 员工档案（姓名/电话/邮箱/技能/证书）
--     工号 → sys_user.username（主表统一），不再冗余 employee_no
--     排班字段 → workorder_maintenance_personnel（current_workload/max_workload/is_on_duty）
CREATE TABLE IF NOT EXISTS maintenance_personnel (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES sys_user(id) ON DELETE SET NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(128),
    specializations JSONB NOT NULL DEFAULT '[]'::jsonb,
    skill_level VARCHAR(16) NOT NULL DEFAULT 'JUNIOR',
    certification VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 非 NULL 的 user_id 必须唯一（部分唯一索引，允许多个 NULL 供历史兼容）
CREATE UNIQUE INDEX IF NOT EXISTS ux_maintenance_personnel_user_id
    ON maintenance_personnel(user_id) WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_maintenance_personnel_skill_level
    ON maintenance_personnel (skill_level);
CREATE INDEX IF NOT EXISTS ix_maintenance_personnel_specializations
    ON maintenance_personnel USING GIN (specializations);
