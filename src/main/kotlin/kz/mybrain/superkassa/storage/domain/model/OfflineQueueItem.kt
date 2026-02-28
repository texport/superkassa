package kz.mybrain.superkassa.storage.domain.model

/**
 * Элемент автономной очереди для последующей доставки в ОФД.
 */
data class OfflineQueueItem(
    // Идентификатор элемента очереди.
    val id: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Порядковый номер внутри кассы (FIFO).
    val sequenceNo: Long,
    // Тип операции (ticket/report/money_placement).
    val operationType: String,
    // Ссылка на payload (обычно id документа).
    val payloadRef: String,
    // Время постановки в очередь (epoch millis).
    val createdAt: Long,
    // Статус обработки (PENDING/SENT/FAILED).
    val status: String,
    // Количество попыток обработки.
    val attempt: Int,
    // Последняя ошибка.
    val lastError: String? = null,
    // Время следующей попытки (epoch millis).
    val nextAttemptAt: Long? = null
)
