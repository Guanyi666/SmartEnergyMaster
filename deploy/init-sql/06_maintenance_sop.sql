-- Epic 01-2-2: maintenance standard operating procedures.

CREATE TABLE IF NOT EXISTS maintenance_sop (
    id BIGSERIAL PRIMARY KEY,
    sop_code VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(128) NOT NULL,
    device_type VARCHAR(64),
    fault_type VARCHAR(64),
    priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_tools JSONB NOT NULL DEFAULT '[]'::jsonb,
    safety_notes TEXT,
    version VARCHAR(32) NOT NULL DEFAULT '1.0',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_maintenance_sop_device_fault
    ON maintenance_sop (device_type, fault_type, is_active);
CREATE INDEX IF NOT EXISTS ix_maintenance_sop_steps
    ON maintenance_sop USING GIN (steps);
