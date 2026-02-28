CREATE TABLE IF NOT EXISTS cashbox (
    id TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    mode TEXT NOT NULL,
    state TEXT NOT NULL,
    ofd_provider TEXT,
    registration_number TEXT,
    factory_number TEXT,
    manufacture_year INTEGER,
    system_id TEXT,
    token_enc BLOB,
    token_updated_at INTEGER,
    last_shift_no INTEGER,
    last_receipt_no INTEGER,
    last_z_report_no INTEGER,
    autonomous_since INTEGER,
    last_fiscal_hash BLOB
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cashbox_reg_number
    ON cashbox (registration_number);

CREATE TABLE IF NOT EXISTS fiscal_document (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    shift_id TEXT,
    doc_type TEXT NOT NULL,
    doc_no INTEGER,
    shift_no INTEGER,
    created_at INTEGER NOT NULL,
    total_amount INTEGER,
    currency TEXT,
    payload_bin BLOB,
    payload_hash BLOB,
    fiscal_sign TEXT,
    autonomous_sign TEXT,
    is_autonomous INTEGER NOT NULL,
    ofd_status TEXT,
    delivered_at INTEGER
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_cashbox_doc_no
    ON fiscal_document (cashbox_id, doc_no);

CREATE INDEX IF NOT EXISTS idx_document_cashbox_created
    ON fiscal_document (cashbox_id, created_at);

CREATE TABLE IF NOT EXISTS fiscal_journal (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    record_type TEXT NOT NULL,
    record_ref TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    prev_hash BLOB,
    record_hash BLOB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_journal_cashbox_created
    ON fiscal_journal (cashbox_id, created_at);

CREATE TABLE IF NOT EXISTS ofd_message (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    command TEXT NOT NULL,
    request_bin BLOB NOT NULL,
    response_bin BLOB,
    status TEXT NOT NULL,
    attempt INTEGER NOT NULL,
    error_code TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ofd_cashbox_status
    ON ofd_message (cashbox_id, status, created_at);

CREATE TABLE IF NOT EXISTS offline_queue (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    sequence_no INTEGER NOT NULL,
    operation_type TEXT NOT NULL,
    payload_ref TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL,
    attempt INTEGER NOT NULL,
    last_error TEXT,
    next_attempt_at INTEGER
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_offline_cashbox_seq
    ON offline_queue (cashbox_id, sequence_no);

CREATE INDEX IF NOT EXISTS idx_offline_cashbox_status
    ON offline_queue (cashbox_id, status, sequence_no);

CREATE TABLE IF NOT EXISTS idempotency (
    cashbox_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    operation TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL,
    response_ref TEXT,
    PRIMARY KEY (cashbox_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS cashbox_lock (
    cashbox_id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    lease_until INTEGER NOT NULL,
    acquired_at INTEGER NOT NULL
);
