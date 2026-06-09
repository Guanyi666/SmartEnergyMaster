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
VALUES ('admin', '$2a$10$Fpw7PxEw9SrLN/GdQ2Mg4eVsgPu4B2ptII3zyYsfZNHAm54b.cb4S', 'ADMIN');  -- 明文: 123456

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
    ('EAF-01', 'Electric Arc Furnace 01', 'ARC_FURNACE', 'RUNNING', 'Steel Workshop A', 'Zhang', 'Core high-load equipment'),
    ('PUMP-01', 'Cooling Pump 01', 'PUMP', 'STOPPED', 'Utility Station', 'Li', 'Cooling system key pump group'),
    ('COMP-01', 'Air Compressor A', 'COMPRESSOR', 'STOPPED', 'Power Station', 'Wang', 'Compressed air main equipment');

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
