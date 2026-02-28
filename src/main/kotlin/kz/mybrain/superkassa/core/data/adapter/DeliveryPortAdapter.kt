package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.DeliveryRequest as CoreDeliveryRequest
import kz.mybrain.superkassa.core.domain.port.DeliveryPort
import kz.mybrain.superkassa.delivery.application.service.DeliveryService
import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import org.slf4j.LoggerFactory

/**
 * Адаптер доставки core -> superkassa-delivery.
 */
class DeliveryPortAdapter(
    private val deliveryService: DeliveryService
) : DeliveryPort {
    private val logger = LoggerFactory.getLogger(DeliveryPortAdapter::class.java)

    override fun deliver(request: CoreDeliveryRequest): Boolean {
        return try {
            val channel = try {
                DeliveryChannel.valueOf(request.channel.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown delivery channel: ${request.channel}, using PRINT")
                DeliveryChannel.PRINT
            }
            val result = deliveryService.deliver(
                DeliveryRequest(
                    cashboxId = request.kkmId,
                    documentId = request.documentId,
                    channel = channel,
                    destination = request.destination,
                    payloadUrl = request.payloadUrl,
                    payloadBytes = request.payloadBytes
                )
            )
            result.ok
        } catch (ex: Exception) {
            logger.error("Failed to deliver document: ${request.documentId} for KKM: ${request.kkmId}", ex)
            false
        }
    }
}
