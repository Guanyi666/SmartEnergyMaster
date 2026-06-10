-- Epic 01-2-2: append-only audit log for sensitive operations.

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id INTEGER REFERENCES sys_user(id) ON DELETE SET NULL,
    actor_username VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    module VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(64),
    request_id VARCHAR(64),
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    old_value JSONB,
    new_value JSONB,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_audit_log_actor_time
    ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_log_module_time
    ON audit_log (module, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_log_target
    ON audit_log (target_type, target_id);
CREATE INDEX IF NOT EXISTS ix_audit_log_detail
    ON audit_log USING GIN (detail);
