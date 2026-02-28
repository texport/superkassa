package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Результат создания чека.
 */
@Serializable
data class ReceiptResult(
    val documentId: String,
    val fiscalSign: String? = null,
    val autonomousSign: String? = null,
    val deliveryPayload: ByteArray? = null
)
