-- Epic 01-2-2: spare part inventory and usage records.

CREATE TABLE IF NOT EXISTS spare_part (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    specification VARCHAR(255),
    category VARCHAR(64),
    unit VARCHAR(16) NOT NULL DEFAULT 'pcs',
    stock_quantity NUMERIC(12, 2) NOT NULL DEFAULT 0,
    safety_stock NUMERIC(12, 2) NOT NULL DEFAULT 0,
    unit_price NUMERIC(12, 2),
    supplier VARCHAR(128),
    location VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_spare_part_stock CHECK (stock_quantity >= 0 AND safety_stock >= 0)
);

CREATE INDEX IF NOT EXISTS ix_spare_part_category_status
    ON spare_part (category, status);
CREATE INDEX IF NOT EXISTS ix_spare_part_low_stock
    ON spare_part (stock_quantity, safety_stock);

CREATE TABLE IF NOT EXISTS spare_part_usage (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES work_order(id) ON DELETE CASCADE,
    spare_part_id BIGINT NOT NULL REFERENCES spare_part(id) ON DELETE RESTRICT,
    quantity NUMERIC(12, 2) NOT NULL,
    unit_price NUMERIC(12, 2),
    used_by VARCHAR(64),
    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(255),
    CONSTRAINT ck_spare_part_usage_quantity CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS ix_spare_part_usage_order
    ON spare_part_usage (work_order_id, used_at DESC);
CREATE INDEX IF NOT EXISTS ix_spare_part_usage_part
    ON spare_part_usage (spare_part_id, used_at DESC);
