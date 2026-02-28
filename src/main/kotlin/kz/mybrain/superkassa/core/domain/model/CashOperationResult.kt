package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Результат операции с наличными.
 */
@Serializable
data class CashOperationResult(
    val documentId: String
)
