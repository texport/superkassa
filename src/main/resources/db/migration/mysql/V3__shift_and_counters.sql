CREATE TABLE IF NOT EXISTS shift (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    shift_no BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    opened_at BIGINT NOT NULL,
    closed_at BIGINT,
    open_document_id VARCHAR(64),
    close_document_id VARCHAR(64)
);

CREATE INDEX idx_shift_cashbox_opened
    ON shift (cashbox_id, opened_at);

CREATE INDEX idx_shift_status
    ON shift (cashbox_id, status);

CREATE TABLE IF NOT EXISTS counter (
    cashbox_id VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    shift_id VARCHAR(64),
    counter_key VARCHAR(128) NOT NULL,
    value BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (cashbox_id, scope, shift_id, counter_key)
);

CREATE INDEX idx_counter_cashbox_scope
    ON counter (cashbox_id, scope, shift_id);
