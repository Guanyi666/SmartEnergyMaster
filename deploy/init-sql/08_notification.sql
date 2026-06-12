-- Epic 01-2-2: user notification inbox.

CREATE TABLE IF NOT EXISTS notification (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES sys_user(id) ON DELETE SET NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    title VARCHAR(128) NOT NULL,
    content TEXT,
    biz_type VARCHAR(64),
    biz_id VARCHAR(64),
    severity VARCHAR(32) NOT NULL DEFAULT 'INFO',
    channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_notification_user_read_created
    ON notification (user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_notification_biz
    ON notification (biz_type, biz_id);
