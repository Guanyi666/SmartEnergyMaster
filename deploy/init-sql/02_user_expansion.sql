-- Epic 01-2-2: sys_user expansion and maintenance personnel archive.

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

CREATE TABLE IF NOT EXISTS maintenance_personnel (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES sys_user(id) ON DELETE SET NULL,
    employee_no VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(128),
    specializations JSONB NOT NULL DEFAULT '[]'::jsonb,
    skill_level VARCHAR(16) NOT NULL DEFAULT 'JUNIOR',
    certification VARCHAR(255),
    current_workload INT NOT NULL DEFAULT 0,
    max_workload INT NOT NULL DEFAULT 5,
    is_on_duty BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_maintenance_personnel_workload
        CHECK (current_workload >= 0 AND max_workload >= 0 AND current_workload <= max_workload)
);

CREATE INDEX IF NOT EXISTS ix_maintenance_personnel_duty_skill
    ON maintenance_personnel (is_on_duty, skill_level);
CREATE INDEX IF NOT EXISTS ix_maintenance_personnel_specializations
    ON maintenance_personnel USING GIN (specializations);
