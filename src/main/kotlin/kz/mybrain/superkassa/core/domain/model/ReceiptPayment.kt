package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/** Оплата по чеку. */
@Serializable
data class ReceiptPayment(
        val type: PaymentType,
        val sum: Money
)
