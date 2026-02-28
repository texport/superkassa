-- Таблица для хранения задач очереди
CREATE TABLE IF NOT EXISTS queue_task (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    lane TEXT NOT NULL CHECK (lane IN ('ONLINE', 'OFFLINE')),
    type TEXT NOT NULL,
    payload_ref TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED')),
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at INTEGER,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_queue_task_cashbox_lane_status
    ON queue_task (cashbox_id, lane, status, created_at);

CREATE INDEX IF NOT EXISTS idx_queue_task_next_attempt
    ON queue_task (next_attempt_at) WHERE next_attempt_at IS NOT NULL;

-- Таблица для lease/lock при обработке очереди
CREATE TABLE IF NOT EXISTS queue_lock (
    cashbox_id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    lease_until INTEGER NOT NULL,
    acquired_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_queue_lock_lease_until
    ON queue_lock (lease_until);
