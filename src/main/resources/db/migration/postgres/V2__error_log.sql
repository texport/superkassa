CREATE TABLE IF NOT EXISTS error_log (
    id VARCHAR(64) PRIMARY KEY,
    created_at BIGINT NOT NULL,
    component VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    message_ru TEXT NOT NULL,
    message_en TEXT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    cashbox_id VARCHAR(64),
    operation_id VARCHAR(64),
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_error_created
    ON error_log (created_at);

CREATE INDEX IF NOT EXISTS idx_error_cashbox
    ON error_log (cashbox_id, created_at);
