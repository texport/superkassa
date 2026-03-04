package kz.mybrain.superkassa.storage.domain.model

/**
 * Запись очереди команд (queue_task).
 * Storage-internal модель, не зависит от offline_queue-модуля.
 */
data class QueueTaskRecord(
    val id: String,
    val cashboxId: String,
    val lane: String,
    val type: String,
    val payloadRef: String,
    val createdAt: Long,
    val status: String,
    val attempt: Int,
    val nextAttemptAt: Long?,
    val lastError: String?
)
