package kz.mybrain.superkassa.delivery.domain.model

/**
 * Запрос на доставку.
 *
 * @param payloadUrl URL чека (при payloadType=LINK).
 * @param payloadBytes Документ в бинарном виде (PDF/IMAGE/HTML/ESC_POS). Один из payloadUrl или payloadBytes должен быть задан.
 */
data class DeliveryRequest(
    val cashboxId: String,
    val documentId: String,
    val channel: DeliveryChannel,
    val destination: String? = null,
    val payloadUrl: String? = null,
    val payloadBytes: ByteArray? = null
) {
    init {
        require(payloadUrl != null || payloadBytes != null) {
            "Either payloadUrl or payloadBytes must be set"
        }
    }
}
