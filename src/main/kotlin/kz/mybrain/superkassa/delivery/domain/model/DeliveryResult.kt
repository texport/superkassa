package kz.mybrain.superkassa.delivery.domain.model

/**
 * Результат доставки.
 */
data class DeliveryResult(
    val ok: Boolean,
    val message: String? = null
)
