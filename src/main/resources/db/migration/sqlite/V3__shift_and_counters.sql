CREATE TABLE IF NOT EXISTS shift (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    shift_no INTEGER NOT NULL,
    status TEXT NOT NULL,
    opened_at INTEGER NOT NULL,
    closed_at INTEGER,
    open_document_id TEXT,
    close_document_id TEXT
);

CREATE INDEX IF NOT EXISTS idx_shift_cashbox_opened
    ON shift (cashbox_id, opened_at);

CREATE INDEX IF NOT EXISTS idx_shift_status
    ON shift (cashbox_id, status);

CREATE TABLE IF NOT EXISTS counter (
    cashbox_id TEXT NOT NULL,
    scope TEXT NOT NULL,
    shift_id TEXT,
    counter_key TEXT NOT NULL,
    value INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (cashbox_id, scope, shift_id, counter_key)
);

CREATE INDEX IF NOT EXISTS idx_counter_cashbox_scope
    ON counter (cashbox_id, scope, shift_id);
