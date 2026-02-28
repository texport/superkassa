package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Результат формирования отчета.
 */
@Serializable
data class ReportResult(
    val documentId: String,
    val deliveryPayload: ByteArray? = null
)
