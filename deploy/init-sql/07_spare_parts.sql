-- =====================================================================
-- Epic 07-2: 备件库存管理 (已部署环境手动执行)
-- 用法: docker exec -i smart_energy_db psql -U energy_user -d smart_energy < 07_spare_parts.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS spare_part (
    id BIGSERIAL PRIMARY KEY,
    part_code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    spec VARCHAR(256),
    unit VARCHAR(32) NOT NULL DEFAULT '件',
    quantity INTEGER NOT NULL DEFAULT 0,
    safety_stock INTEGER NOT NULL DEFAULT 0,
    unit_price NUMERIC(12, 2),
    supplier VARCHAR(128),
    location VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_spare_part_code ON spare_part(part_code);
CREATE INDEX IF NOT EXISTS idx_spare_part_quantity ON spare_part(quantity);

CREATE TABLE IF NOT EXISTS spare_part_usage (
    id BIGSERIAL PRIMARY KEY,
    part_id BIGINT NOT NULL REFERENCES spare_part(id) ON DELETE CASCADE,
    work_order_id BIGINT REFERENCES work_order(id) ON DELETE SET NULL,
    quantity INTEGER NOT NULL,
    user_name VARCHAR(64),
    note VARCHAR(256),
    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_spare_part_usage_part ON spare_part_usage(part_id);
CREATE INDEX IF NOT EXISTS idx_spare_part_usage_order ON spare_part_usage(work_order_id);
CREATE INDEX IF NOT EXISTS idx_spare_part_usage_used_at ON spare_part_usage(used_at DESC);