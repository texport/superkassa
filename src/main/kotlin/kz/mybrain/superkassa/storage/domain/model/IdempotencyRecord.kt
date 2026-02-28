package kz.mybrain.superkassa.storage.domain.model

/**
 * Идемпотентность операций для защиты от повторных вызовов.
 */
data class IdempotencyRecord(
    // Ключ идемпотентности (уникален в рамках кассы).
    val idempotencyKey: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Тип операции (например CREATE_RECEIPT).
    val operation: String,
    // Время создания записи (epoch millis).
    val createdAt: Long,
    // Статус обработки.
    val status: String,
    // Ссылка на результат (id документа/операции).
    val responseRef: String? = null
)
