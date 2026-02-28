CREATE TABLE IF NOT EXISTS kkm_operator (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    pin_hash VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kkm_operator_cashbox
    ON kkm_operator (cashbox_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_kkm_operator_cashbox_pin
    ON kkm_operator (cashbox_id, pin_hash);
