-- Таблица для хранения задач очереди
CREATE TABLE IF NOT EXISTS queue_task (
    id VARCHAR(255) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    lane VARCHAR(16) NOT NULL CHECK (lane IN ('ONLINE', 'OFFLINE')),
    type VARCHAR(64) NOT NULL,
    payload_ref VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED')),
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at BIGINT,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_queue_task_cashbox_lane_status
    ON queue_task (cashbox_id, lane, status, created_at);

CREATE INDEX IF NOT EXISTS idx_queue_task_next_attempt
    ON queue_task (next_attempt_at) WHERE next_attempt_at IS NOT NULL;

-- Таблица для lease/lock при обработке очереди
CREATE TABLE IF NOT EXISTS queue_lock (
    cashbox_id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    lease_until BIGINT NOT NULL,
    acquired_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_queue_lock_lease_until
    ON queue_lock (lease_until);
