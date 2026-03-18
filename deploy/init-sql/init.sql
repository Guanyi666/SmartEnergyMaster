-- ==========================================================
-- 智驭能效 (SmartEnergyMaster) 数据库初始化脚本
-- 引擎: PostgreSQL + TimescaleDB
-- ==========================================================

-- 1. 开启 TimescaleDB 时序数据库扩展
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- 2. 清理历史表 (防止反复执行时报错)
DROP TABLE IF EXISTS sensor_data CASCADE;
DROP TABLE IF EXISTS alarm_log CASCADE;
DROP TABLE IF EXISTS alarm_rule CASCADE;
DROP TABLE IF EXISTS device CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;

-- ==========================================================
-- 基础关系型业务表 (MyBatis-Plus 常用表)
-- ==========================================================

-- 3. 用户表 (对应管理层、车间操作员、生产调度员等)
CREATE TABLE sys_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL, -- 如: ADMIN, OPERATOR, DISPATCHER
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. 设备表 (登记高炉、鼓风机、冷却水泵等物理设备)
CREATE TABLE device (
    id SERIAL PRIMARY KEY,
    device_code VARCHAR(50) NOT NULL UNIQUE, -- 设备编号，如 BF-01 (高炉1号)
    device_name VARCHAR(100) NOT NULL,       -- 设备名称
    device_type VARCHAR(50) NOT NULL,        -- 设备类型: BLAST_FURNACE(高炉), BLOWER(鼓风机), WATER_PUMP(水泵)
    status VARCHAR(20) DEFAULT 'ONLINE',     -- ONLINE, OFFLINE, ALARM
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. 告警规则表 (对应 WBS 2.2.3 基于静态阈值的预警)
CREATE TABLE alarm_rule (
    id SERIAL PRIMARY KEY,
    device_type VARCHAR(50) NOT NULL,
    metric_name VARCHAR(50) NOT NULL,        -- 监控指标，如: temperature, power
    threshold_max NUMERIC(10, 2),            -- 告警上限
    threshold_min NUMERIC(10, 2),            -- 告警下限
    is_active BOOLEAN DEFAULT TRUE,          -- 规则是否启用
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 告警日志表 (记录触发的异常，供前端大屏展示)
CREATE TABLE alarm_log (
    id SERIAL PRIMARY KEY,
    device_id INT REFERENCES device(id),
    alarm_level VARCHAR(20) NOT NULL,        -- WARNING, CRITICAL
    alarm_content TEXT NOT NULL,             -- 异常描述 (含 NLG 生成的三段式报告)
    is_handled BOOLEAN DEFAULT FALSE,        -- 操作员是否已确认处理
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================================
-- 超高速时序数据表 (TimescaleDB 专属)
-- ==========================================================

-- 7. 传感器打点时序表 (高频写入，接收 Python 数据泵的数据)
CREATE TABLE sensor_data (
    time TIMESTAMPTZ NOT NULL,               -- 时间戳 (时序表必须有时间列)
    device_id INT NOT NULL,                  -- 关联的设备 ID
    temperature NUMERIC(10, 2),              -- 温度 (℃)
    pressure NUMERIC(10, 2),                 -- 压力 (kPa)
    power_consumption NUMERIC(10, 2),        -- 功率耗电量 (kWh)
    gas_flow NUMERIC(10, 2),                 -- 煤气流量 (m³/h)
    water_flow NUMERIC(10, 2)                -- 冷却水流量 (t/h)
);

-- 8. 将普通的 sensor_data 表转换为 TimescaleDB 超表 (Hypertable)！
-- 按 'time' 列进行时间分区，这是时序数据库查询极快的核心原因
SELECT create_hypertable('sensor_data', 'time');

-- 9. 为设备 ID 创建索引，加速按设备查询历史曲线
CREATE INDEX ix_sensor_data_device_id ON sensor_data (device_id, time DESC);

-- ==========================================================
-- 插入测试体验数据 (方便后端刚写好就能查出数据)
-- ==========================================================
INSERT INTO sys_user (username, password, role) VALUES ('admin', '123456', 'ADMIN');
INSERT INTO device (device_code, device_name, device_type) VALUES 
('BF-01', '1号高炉', 'BLAST_FURNACE'),
('BL-01', '主鼓风机', 'BLOWER'),
('WP-01', '循环冷却水泵', 'WATER_PUMP');