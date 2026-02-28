CREATE TABLE IF NOT EXISTS kkm_operator (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    role TEXT NOT NULL,
    pin_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kkm_operator_cashbox
    ON kkm_operator (cashbox_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_kkm_operator_cashbox_pin
    ON kkm_operator (cashbox_id, pin_hash);
