CREATE TABLE IF NOT EXISTS outbox_event (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_bin BYTEA NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt INTEGER NOT NULL,
    next_attempt_at BIGINT,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_event (status, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_cashbox_created
    ON outbox_event (cashbox_id, created_at);

CREATE INDEX IF NOT EXISTS idx_document_cashbox_shift
    ON fiscal_document (cashbox_id, shift_id, created_at);

CREATE INDEX IF NOT EXISTS idx_shift_cashbox_shiftno
    ON shift (cashbox_id, shift_no);
