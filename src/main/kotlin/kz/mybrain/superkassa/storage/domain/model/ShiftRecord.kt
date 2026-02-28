package kz.mybrain.superkassa.storage.domain.model

/**
 * Смена кассы: фиксирует открытие/закрытие и связь с отчетами.
 */
data class ShiftRecord(
    // Идентификатор смены.
    val id: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Порядковый номер смены.
    val shiftNo: Long,
    // Статус смены (OPEN/CLOSED).
    val status: String,
    // Время открытия смены (epoch millis).
    val openedAt: Long,
    // Время закрытия смены (epoch millis).
    val closedAt: Long? = null,
    // Документ открытия смены (если фиксируется).
    val openDocumentId: String? = null,
    // Документ закрытия смены (Z-отчет).
    val closeDocumentId: String? = null
)
