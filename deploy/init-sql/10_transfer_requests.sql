CREATE TABLE IF NOT EXISTS work_order_transfer_request (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES work_order(id) ON DELETE CASCADE,
    requester_personnel_id BIGINT NOT NULL REFERENCES workorder_maintenance_personnel(id),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_username VARCHAR(64),
    new_personnel_id BIGINT REFERENCES workorder_maintenance_personnel(id),
    review_note VARCHAR(500),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    CONSTRAINT ck_transfer_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS ix_transfer_request_status_time
    ON work_order_transfer_request (status, requested_at DESC);
CREATE INDEX IF NOT EXISTS ix_transfer_request_requester
    ON work_order_transfer_request (requester_personnel_id, requested_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ix_transfer_request_unique_pending
    ON work_order_transfer_request (work_order_id, requester_personnel_id)
    WHERE status = 'PENDING';
