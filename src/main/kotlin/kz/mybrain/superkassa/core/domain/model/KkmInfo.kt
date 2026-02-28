package kz.mybrain.superkassa.core.domain.model

/**
 * Снимок состояния ККМ для core.
 * Используется для регистрации/удаления ККМ и передачи состояния наружу.
 */
data class KkmInfo(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mode: String,
    val state: String,
    val ofdProvider: String? = null,
    val registrationNumber: String? = null,
    val factoryNumber: String? = null,
    val manufactureYear: Int? = null,
    val systemId: String? = null,
    val ofdServiceInfo: OfdServiceInfo? = null,
    // Base64, чтобы безопасно отдавать через JSON.
    val tokenEncryptedBase64: String? = null,
    val tokenUpdatedAt: Long? = null,
    val lastShiftNo: Int? = null,
    val lastReceiptNo: Int? = null,
    val lastZReportNo: Int? = null,
    val autonomousSince: Long? = null,
    val autoCloseShift: Boolean = false,
    // Base64, чтобы безопасно отдавать через JSON.
    val lastFiscalHashBase64: String? = null,
    /**
     * Налоговый режим ККМ.
     * По умолчанию касса считается не плательщиком НДС.
     */
    val taxRegime: TaxRegime = TaxRegime.NO_VAT,
    /**
     * Базовая группа НДС, применяемая по умолчанию к позициям без явной группы.
     */
    val defaultVatGroup: VatGroup = VatGroup.NO_VAT
)
