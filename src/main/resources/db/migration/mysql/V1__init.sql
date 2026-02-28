CREATE TABLE IF NOT EXISTS cashbox (
    id VARCHAR(64) PRIMARY KEY,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    mode VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    ofd_provider VARCHAR(64),
    registration_number VARCHAR(64),
    factory_number VARCHAR(64),
    manufacture_year INT,
    system_id VARCHAR(128),
    token_enc BLOB,
    token_updated_at BIGINT,
    last_shift_no INT,
    last_receipt_no INT,
    last_z_report_no INT,
    autonomous_since BIGINT,
    last_fiscal_hash BLOB
);

CREATE UNIQUE INDEX idx_cashbox_reg_number
    ON cashbox (registration_number);

CREATE TABLE IF NOT EXISTS fiscal_document (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    shift_id VARCHAR(64),
    doc_type VARCHAR(64) NOT NULL,
    doc_no BIGINT,
    shift_no BIGINT,
    created_at BIGINT NOT NULL,
    total_amount BIGINT,
    currency VARCHAR(16),
    payload_bin LONGBLOB,
    payload_hash BLOB,
    fiscal_sign VARCHAR(128),
    autonomous_sign VARCHAR(128),
    is_autonomous BOOLEAN NOT NULL,
    ofd_status VARCHAR(32),
    delivered_at BIGINT
);

CREATE UNIQUE INDEX idx_document_cashbox_doc_no
    ON fiscal_document (cashbox_id, doc_no);

CREATE INDEX idx_document_cashbox_created
    ON fiscal_document (cashbox_id, created_at);

CREATE TABLE IF NOT EXISTS fiscal_journal (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    record_type VARCHAR(64) NOT NULL,
    record_ref VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    prev_hash BLOB,
    record_hash BLOB NOT NULL
);

CREATE INDEX idx_journal_cashbox_created
    ON fiscal_journal (cashbox_id, created_at);

CREATE TABLE IF NOT EXISTS ofd_message (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    command VARCHAR(64) NOT NULL,
    request_bin LONGBLOB NOT NULL,
    response_bin LONGBLOB,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL,
    error_code VARCHAR(64),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_ofd_cashbox_status
    ON ofd_message (cashbox_id, status, created_at);

CREATE TABLE IF NOT EXISTS offline_queue (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    sequence_no BIGINT NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    payload_ref VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL,
    last_error VARCHAR(255),
    next_attempt_at BIGINT
);

CREATE UNIQUE INDEX idx_offline_cashbox_seq
    ON offline_queue (cashbox_id, sequence_no);

CREATE INDEX idx_offline_cashbox_status
    ON offline_queue (cashbox_id, status, sequence_no);

CREATE TABLE IF NOT EXISTS idempotency (
    cashbox_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_ref VARCHAR(64),
    PRIMARY KEY (cashbox_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS cashbox_lock (
    cashbox_id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    lease_until BIGINT NOT NULL,
    acquired_at BIGINT NOT NULL
);
