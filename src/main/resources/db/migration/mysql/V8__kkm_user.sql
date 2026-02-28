CREATE TABLE IF NOT EXISTS kkm_user (
    id VARCHAR(64) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    pin VARCHAR(32),
    pin_hash VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_kkm_user_cashbox
    ON kkm_user (cashbox_id);

CREATE UNIQUE INDEX idx_kkm_user_cashbox_pin
    ON kkm_user (cashbox_id, pin_hash);

INSERT INTO kkm_user (id, cashbox_id, name, role, pin, pin_hash, created_at)
SELECT id, cashbox_id, 'Оператор', role, NULL, pin_hash, created_at
FROM kkm_operator
WHERE NOT EXISTS (
    SELECT 1 FROM kkm_user WHERE kkm_user.id = kkm_operator.id
);
