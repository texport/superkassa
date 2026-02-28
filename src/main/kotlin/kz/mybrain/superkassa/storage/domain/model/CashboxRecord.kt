package kz.mybrain.superkassa.storage.domain.model

/**
 * Снимок состояния кассы в хранилище.
 * Данные используются для восстановления состояния и контроля режимов.
 */
data class CashboxRecord(
    // Идентификатор кассы в SuperKassa.
    val id: String,
    // Время создания записи (epoch millis).
    val createdAt: Long,
    // Время последнего обновления (epoch millis).
    val updatedAt: Long,
    // Режим кассы (REGISTRATION/PROGRAMMING и т.д.).
    val mode: String,
    // Текущее состояние (IDLE/ACTIVE и т.д.).
    val state: String,
    // Провайдер ОФД.
    val ofdProvider: String? = null,
    // Регистрационный номер в налоговой.
    val registrationNumber: String? = null,
    // Заводской номер.
    val factoryNumber: String? = null,
    // Год выпуска.
    val manufactureYear: Int? = null,
    // Системный идентификатор кассы.
    val systemId: String? = null,
    // JSON с сервисными данными ОФД (регистрация и геопозиция).
    val ofdServiceInfoJson: String? = null,
    // Зашифрованный токен ОФД.
    val tokenEncrypted: ByteArray? = null,
    // Время последнего обновления токена.
    val tokenUpdatedAt: Long? = null,
    // Последний номер смены.
    val lastShiftNo: Int? = null,
    // Последний номер чека.
    val lastReceiptNo: Int? = null,
    // Последний номер Z-отчета.
    val lastZReportNo: Int? = null,
    // Время начала автономного режима.
    val autonomousSince: Long? = null,
    // Автоматическое закрытие смены при превышении лимита.
    val autoCloseShift: Boolean = false,
    // Последний hash для фискального журнала.
    val lastFiscalHash: ByteArray? = null,
    // Налоговый режим ККМ.
    val taxRegime: String? = null,
    // Базовая группа НДС по умолчанию.
    val defaultVatGroup: String? = null
)
