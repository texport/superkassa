package kz.mybrain.superkassa.core.domain.model

/**
 * Запрос на доставку (печать/почта/sms/мессенджеры).
 *
 * @param payloadType LINK — отправить URL ОФД; PDF/IMAGE/HTML — документ; ESC_POS — для печати.
 * @param payloadUrl URL чека (при payloadType=LINK или BOTH).
 * @param payloadBytes Документ в бинарном виде (PDF/IMAGE/HTML/ESC_POS).
 */
data class DeliveryRequest(
    val kkmId: String,
    val documentId: String,
    val channel: String,
    val destination: String? = null,
    val payloadType: String,
    val payloadUrl: String? = null,
    val payloadBytes: ByteArray? = null
) {
    init {
        require(payloadUrl != null || payloadBytes != null) {
            "Either payloadUrl or payloadBytes must be set"
        }
    }
}

