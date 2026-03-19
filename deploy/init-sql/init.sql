DROP TABLE IF EXISTS sensor_data CASCADE;
DROP TABLE IF EXISTS alarm_log CASCADE;
DROP TABLE IF EXISTS alarm_rule CASCADE;
DROP TABLE IF EXISTS device CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;

-- 1. 启用 TimescaleDB 扩展
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- 2. 系统用户表 (Spring Security 鉴权)
CREATE TABLE sys_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'OPERATOR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认管理员 (密码是 123456 的 BCrypt 加密密文)
INSERT INTO sys_user (username, password, role) 
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'ADMIN');

-- 3. 设备台账表
CREATE TABLE device (
    id SERIAL PRIMARY KEY,
    device_code VARCHAR(50) UNIQUE NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'OFFLINE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化一台测试用的高炉设备
INSERT INTO device (device_code, device_name, device_type, status) 
VALUES ('EAF-01', '1号电弧炉', 'FURNACE', 'ONLINE');

-- 4. 传感器打点与能耗时序表 (大满贯版)
CREATE TABLE sensor_data (
    time TIMESTAMPTZ NOT NULL,
    device_id INT NOT NULL,
    usage_kwh NUMERIC(10, 2),
    co2_emission NUMERIC(10, 2),
    nsm INT,
    week_status INT,
    day_of_week VARCHAR(20),
    load_type VARCHAR(50),
    xian_price_tier VARCHAR(20),
    temperature NUMERIC(10, 2),
    vibration NUMERIC(10, 2),
    pressure NUMERIC(10, 2),
    operating_status INT
);

-- 转换为 TimescaleDB 超表并建立设备索引
SELECT create_hypertable('sensor_data', 'time');
CREATE INDEX ix_sensor_data_device_id ON sensor_data (device_id, time DESC);