package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Результат формирования отчета (X или Z).
 */
@Serializable
data class ReportResult(
    val documentId: String,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.NOT_SENT,
    val deliveryError: String? = null,
    val deliveryPayload: ByteArray? = null
)
