CREATE TABLE IF NOT EXISTS sys_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'OPERATOR'
);

CREATE TABLE IF NOT EXISTS device (
    id SERIAL PRIMARY KEY,
    device_code VARCHAR(64) NOT NULL UNIQUE,
    device_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
    location VARCHAR(128),
    maintainer VARCHAR(64),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sensor_data (
    id BIGSERIAL PRIMARY KEY,
    time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_id INTEGER NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    usage_kwh NUMERIC(12, 2) NOT NULL,
    co2_emission NUMERIC(12, 2),
    nsm INTEGER,
    week_status INTEGER,
    day_of_week VARCHAR(32),
    load_type VARCHAR(32),
    xian_price_tier VARCHAR(32),
    temperature NUMERIC(12, 2),
    vibration NUMERIC(12, 2),
    pressure NUMERIC(12, 2),
    operating_status INTEGER
);

CREATE INDEX IF NOT EXISTS idx_sensor_data_device_time ON sensor_data(device_id, time DESC);

CREATE TABLE IF NOT EXISTS work_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    device_id INTEGER NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    title VARCHAR(128) NOT NULL,
    fault_type VARCHAR(64) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    assignee VARCHAR(64),
    source_time TIMESTAMPTZ,
    accepted_at TIMESTAMP,
    resolved_at TIMESTAMP,
    latest_temperature NUMERIC(12, 2),
    latest_vibration NUMERIC(12, 2),
    latest_pressure NUMERIC(12, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_work_order_status_created_at ON work_order(status, created_at DESC);

-- ============================================================================
-- 🆕 合并自 workorder-backend: 维修人员档案 + 工单-人员指派关系
-- 全部 IF NOT EXISTS 幂等，可与 mode:always 安全共存
-- ============================================================================

-- 1. 维修人员档案
CREATE TABLE IF NOT EXISTS workorder_maintenance_personnel (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(64),
    avatar_color VARCHAR(16) DEFAULT '#52c8ff',
    specializations TEXT NOT NULL DEFAULT '[]',
    skill_level VARCHAR(16) NOT NULL,
    certification VARCHAR(256),
    current_workload INT NOT NULL DEFAULT 0,
    max_workload INT NOT NULL DEFAULT 5,
    is_on_duty BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workorder_person_on_duty
    ON workorder_maintenance_personnel (is_on_duty, skill_level);
CREATE INDEX IF NOT EXISTS idx_workorder_person_workload
    ON workorder_maintenance_personnel (current_workload);

-- 2. 工单-人员指派关系（事实表，唯一真实源）
CREATE TABLE IF NOT EXISTS workorder_assignment (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    personnel_id BIGINT NOT NULL REFERENCES workorder_maintenance_personnel(id),
    role VARCHAR(16) NOT NULL DEFAULT 'PRIMARY',
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    note VARCHAR(256)
);

CREATE INDEX IF NOT EXISTS idx_workorder_assignment_wo
    ON workorder_assignment (work_order_id, released_at);
CREATE INDEX IF NOT EXISTS idx_workorder_assignment_personnel
    ON workorder_assignment (personnel_id, released_at);
CREATE INDEX IF NOT EXISTS idx_workorder_assignment_active
    ON workorder_assignment (personnel_id) WHERE released_at IS NULL;

-- 3. 种子数据：6 名维修人员
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

-- 4. 一次性 sys_user 插入 E001 维修账号
INSERT INTO sys_user (username, password, role)
VALUES (
    'E001',
    '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q',
    'MAINTENANCE_ENGINEER'
)
ON CONFLICT (username) DO NOTHING;
