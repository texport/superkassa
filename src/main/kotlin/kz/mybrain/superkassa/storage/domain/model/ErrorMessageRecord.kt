package kz.mybrain.superkassa.storage.domain.model

/**
 * Централизованное хранение ошибок с сообщениями на двух языках.
 * Сообщения предназначены для передачи за пределы библиотеки.
 */
data class ErrorMessageRecord(
    // Идентификатор ошибки.
    val id: String,
    // Время фиксации (epoch millis).
    val createdAt: Long,
    // Компонент, где произошла ошибка.
    val component: String,
    // Код ошибки.
    val code: String,
    // Сообщение на русском.
    val messageRu: String,
    // Сообщение на английском.
    val messageEn: String,
    // Уровень серьезности (INFO/WARN/ERROR).
    val severity: String,
    // Идентификатор кассы, если применимо.
    val cashboxId: String? = null,
    // Идентификатор операции, если применимо.
    val operationId: String? = null,
    // Технические детали.
    val details: String? = null
)
