package kz.mybrain.superkassa.delivery.domain.model

/**
 * Канал доставки чека.
 */
enum class DeliveryChannel {
    PRINT,
    EMAIL,
    SMS,
    WHATSAPP,
    TELEGRAM
}
