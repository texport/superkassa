package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Тип оплаты (соответствует протоколу ОФД: PAYMENT_CASH, PAYMENT_CARD, PAYMENT_ELECTRONIC).
 */
@Serializable
enum class PaymentType {
    CASH,
    CARD,
    ELECTRONIC
}
