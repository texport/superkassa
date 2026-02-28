package kz.mybrain.superkassa.delivery.data.adapter

import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.net.Socket

/**
 * Адаптер печати чеков по сети (ESC/POS).
 */
class PrintDeliveryAdapter(
    private val host: String,
    private val port: Int
) : DeliveryAdapter {
    override val channel: DeliveryChannel = DeliveryChannel.PRINT
    private val logger = LoggerFactory.getLogger(PrintDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val bytes = request.payloadBytes ?: return DeliveryResult(false, "No print payload")
        return try {
            Socket(host, port).use { socket ->
                val out: OutputStream = socket.getOutputStream()
                out.write(bytes)
                out.flush()
            }
            logger.debug("Printed document {} to {}:{}", request.documentId, host, port)
            DeliveryResult(true)
        } catch (e: Exception) {
            logger.error("Print failed for document {}: {}", request.documentId, e.message)
            DeliveryResult(false, e.message ?: "Print failed")
        }
    }
}
