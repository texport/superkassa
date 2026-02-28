package kz.mybrain.superkassa.storage.domain.model

/**
 * Запрос/ответ ОФД с техническими метаданными.
 */
data class OfdMessageRecord(
    // Идентификатор сообщения.
    val id: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Команда ОФД (command_ticket, command_report и т.д.).
    val command: String,
    // Запрос ОФД (bytes).
    val requestBin: ByteArray,
    // Ответ ОФД (bytes), если получен.
    val responseBin: ByteArray? = null,
    // Статус доставки (PENDING/SENT/FAILED).
    val status: String,
    // Количество попыток отправки.
    val attempt: Int,
    // Код ошибки, если есть.
    val errorCode: String? = null,
    // Время создания записи (epoch millis).
    val createdAt: Long,
    // Время последнего обновления (epoch millis).
    val updatedAt: Long
)
