package kz.mybrain.superkassa.core.domain.model

/**
 * DTO элемента очереди для передачи через StoragePort.
 * Core-модель, не зависит от offline_queue-модуля.
 */
data class QueueTaskDto(
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
