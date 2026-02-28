package kz.mybrain.superkassa.storage.domain.model

/**
 * Универсальная запись для чека/отчета/операции.
 * payloadBin хранится как есть, без сериализации.
 */
data class FiscalDocumentRecord(
    // Идентификатор документа.
    val id: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Идентификатор смены (если относится к смене).
    val shiftId: String? = null,
    // Тип документа (CHECK/X_REPORT/Z_REPORT и т.д.).
    val docType: String,
    // Номер документа в кассе.
    val docNo: Long? = null,
    // Номер смены, к которой относится документ.
    val shiftNo: Long? = null,
    // Время создания документа (epoch millis).
    val createdAt: Long,
    // Общая сумма документа.
    val totalAmount: Long? = null,
    // Валюта (например KZT).
    val currency: String? = null,
    // Бинарное тело документа (не сериализуется внутри storage).
    val payloadBin: ByteArray? = null,
    // Хэш payload для контроля целостности.
    val payloadHash: ByteArray? = null,
    // Фискальный признак от ОФД.
    val fiscalSign: String? = null,
    // Автономный фискальный признак.
    val autonomousSign: String? = null,
    // Признак автономного режима.
    val isAutonomous: Boolean = false,
    // Статус доставки в ОФД.
    val ofdStatus: String? = null,
    // Время доставки документа (epoch millis).
    val deliveredAt: Long? = null
)
