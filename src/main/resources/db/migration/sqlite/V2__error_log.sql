CREATE TABLE IF NOT EXISTS error_log (
    id TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL,
    component TEXT NOT NULL,
    code TEXT NOT NULL,
    message_ru TEXT NOT NULL,
    message_en TEXT NOT NULL,
    severity TEXT NOT NULL,
    cashbox_id TEXT,
    operation_id TEXT,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_error_created
    ON error_log (created_at);

CREATE INDEX IF NOT EXISTS idx_error_cashbox
    ON error_log (cashbox_id, created_at);
