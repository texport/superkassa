package kz.mybrain.superkassa.delivery.application.service

import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory

/**
 * Сервис доставки чеков.
 */
class DeliveryService(
    adapters: List<DeliveryAdapter>
) {
    private val logger = LoggerFactory.getLogger(DeliveryService::class.java)
    private val adapterByChannel = adapters.associateBy { it.channel }

    fun deliver(request: DeliveryRequest): DeliveryResult {
        val adapter = adapterByChannel[request.channel]
            ?: return DeliveryResult(false, "No adapter for channel ${request.channel}")
        logger.info("Delivery start. channel={}, documentId={}", request.channel, request.documentId)
        val result = adapter.send(request)
        if (result.ok) {
            logger.info("Delivery success. documentId={}", request.documentId)
        } else {
            logger.warn("Delivery failed. documentId={}, message={}", request.documentId, result.message)
        }
        return result
    }
}
