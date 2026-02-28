package kz.mybrain.superkassa.core.domain.model

/**
 * Краткая информация о фискальном документе (для ответов API и чтения после обновления).
 */
data class FiscalDocumentSnapshot(
    val id: String,
    val cashboxId: String,
    val shiftId: String,
    val docType: String,
    val docNo: Long?,
    val shiftNo: Long?,
    val createdAt: Long,
    val totalAmount: Long?,
    val currency: String?,
    val fiscalSign: String?,
    val autonomousSign: String?,
    val isAutonomous: Boolean,
    val ofdStatus: String?,
    val deliveredAt: Long?
)
