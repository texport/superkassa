CREATE TABLE IF NOT EXISTS kkm_user (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    name TEXT NOT NULL,
    role TEXT NOT NULL,
    pin TEXT,
    pin_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kkm_user_cashbox
    ON kkm_user (cashbox_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_kkm_user_cashbox_pin
    ON kkm_user (cashbox_id, pin_hash);

INSERT INTO kkm_user (id, cashbox_id, name, role, pin, pin_hash, created_at)
SELECT id, cashbox_id, 'Оператор', role, NULL, pin_hash, created_at
FROM kkm_operator
WHERE NOT EXISTS (
    SELECT 1 FROM kkm_user WHERE kkm_user.id = kkm_operator.id
);
