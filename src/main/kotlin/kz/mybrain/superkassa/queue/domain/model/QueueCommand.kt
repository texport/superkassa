package kz.mybrain.superkassa.queue.domain.model

/**
 * Элемент очереди для отправки команд в ОФД.
 */
data class QueueCommand(
    // Идентификатор команды.
    val id: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Линия очереди (ONLINE/OFFLINE).
    val lane: QueueLane,
    // Тип команды.
    val type: QueueCommandType,
    // Ссылка на payload (например id документа в storage).
    val payloadRef: String,
    // Время постановки в очередь (epoch millis).
    val createdAt: Long,
    // Статус команды.
    val status: QueueStatus,
    // Количество попыток отправки.
    val attempt: Int,
    // Время следующей попытки.
    val nextAttemptAt: Long? = null,
    // Последняя ошибка.
    val lastError: String? = null
)
