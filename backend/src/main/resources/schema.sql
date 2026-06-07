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
