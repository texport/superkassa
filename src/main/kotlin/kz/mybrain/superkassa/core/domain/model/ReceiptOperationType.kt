package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Тип операции чека.
 */
@Serializable
enum class ReceiptOperationType {
    SELL,
    SELL_RETURN,
    BUY,
    BUY_RETURN
}
