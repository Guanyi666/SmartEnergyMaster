DROP TABLE IF EXISTS work_order CASCADE;
DROP TABLE IF EXISTS sensor_data CASCADE;
DROP TABLE IF EXISTS device CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;

CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE sys_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'OPERATOR'
);

INSERT INTO sys_user (username, password, role)
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'ADMIN');  -- 明文: admin123

CREATE TABLE device (
    id SERIAL PRIMARY KEY,
    device_code VARCHAR(64) UNIQUE NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
    location VARCHAR(128),
    maintainer VARCHAR(64),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO device (device_code, device_name, device_type, status, location, maintainer, description)
VALUES
    ('EAF-01', '1号电弧炉', 'ARC_FURNACE', 'RUNNING', '炼钢一车间', '张工', '核心高耗能设备——废钢熔化与初步合金化'),
    ('PUMP-01', '循环水泵', 'PUMP', 'STOPPED', '公辅站', '李工', '冷却系统关键泵组——向电弧炉及连铸机提供冷却水'),
    ('COMP-01', '空压机A', 'COMPRESSOR', 'STOPPED', '动力站', '王工', '压缩空气主设备——气动阀门与仪表风气源'),
    ('LF-01', '钢包精炼炉', 'LADLE_FURNACE', 'STOPPED', '炼钢一车间', '赵工', '钢水二次精炼——合金化、脱硫、成分与温度调整'),
    ('CC-01', '1号连铸机', 'CONTINUOUS_CASTER', 'STOPPED', '连铸跨', '钱工', '钢水连续浇铸成坯——弧形连铸机'),
    ('DC-01', '主除尘系统', 'DUST_COLLECTOR', 'STOPPED', '环保站', '孙工', '电弧炉烟气捕集与布袋除尘——环保合规');

CREATE TABLE sensor_data (
    id BIGSERIAL NOT NULL,
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
    operating_status INTEGER,
    PRIMARY KEY (id, time)
);

SELECT create_hypertable('sensor_data', 'time', if_not_exists => TRUE);
CREATE INDEX ix_sensor_data_device_time ON sensor_data (device_id, time DESC);

CREATE TABLE work_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(64) UNIQUE NOT NULL,
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

CREATE INDEX ix_work_order_status_created_at ON work_order (status, created_at DESC);

-- ============================================================
-- Epic 05：维修人员调度模块（与 workorder-backend 合并后统一建表）
-- ============================================================

CREATE TABLE workorder_maintenance_personnel (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(128),
    avatar_color VARCHAR(16) DEFAULT '#52c8ff',
    specializations JSONB DEFAULT '[]'::jsonb,
    skill_level VARCHAR(16) NOT NULL DEFAULT 'JUNIOR',
    certification VARCHAR(255),
    current_workload INT NOT NULL DEFAULT 0,
    max_workload INT NOT NULL DEFAULT 5,
    is_on_duty BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_personnel_skill_level ON workorder_maintenance_personnel (skill_level);
CREATE INDEX ix_personnel_on_duty ON workorder_maintenance_personnel (is_on_duty);

CREATE TABLE workorder_assignment (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    personnel_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    note VARCHAR(255)
);

CREATE INDEX ix_assignment_work_order ON workorder_assignment (work_order_id);
CREATE INDEX ix_assignment_personnel ON workorder_assignment (personnel_id);
CREATE INDEX ix_assignment_active ON workorder_assignment (work_order_id, personnel_id) WHERE released_at IS NULL;
