package kz.mybrain.superkassa.storage.domain.model

/**
 * Outbox-событие для надежной доставки во внешние системы.
 */
data class OutboxEventRecord(
    // Идентификатор события.
    val id: String,
    // Идентификатор кассы (для шардирования/фильтрации).
    val cashboxId: String,
    // Тип события (RECEIPT_CREATED, SHIFT_CLOSED и т.д.).
    val eventType: String,
    // Полезная нагрузка события (bytes).
    val payloadBin: ByteArray,
    // Время создания события (epoch millis).
    val createdAt: Long,
    // Статус обработки (PENDING/SENT/FAILED).
    val status: String,
    // Количество попыток отправки.
    val attempt: Int,
    // Время следующей попытки.
    val nextAttemptAt: Long? = null,
    // Последняя ошибка.
    val lastError: String? = null
)
