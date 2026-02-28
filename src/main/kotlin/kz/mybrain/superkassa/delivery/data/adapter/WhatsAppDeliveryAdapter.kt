package kz.mybrain.superkassa.delivery.data.adapter

import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Адаптер отправки чеков через WhatsApp Cloud API.
 * Требует accessToken и phoneNumberId от Meta Business.
 */
class WhatsAppDeliveryAdapter(
    private val accessToken: String,
    private val phoneNumberId: String
) : DeliveryAdapter {
    override val channel: DeliveryChannel = DeliveryChannel.WHATSAPP
    private val logger = LoggerFactory.getLogger(WhatsAppDeliveryAdapter::class.java)
    private val http = HttpClient.newBuilder().build()

    override fun send(request: DeliveryRequest): DeliveryResult {
        val to = request.destination ?: return DeliveryResult(false, "WhatsApp phone number required")
        val text = when {
            request.payloadUrl != null -> "Чек: ${request.payloadUrl}"
            else -> "Чек ${request.documentId} готов"
        }
        val normalizedPhone = to.replace(Regex("[^0-9]"), "")
        val body = """
            {
                "messaging_product": "whatsapp",
                "to": "$normalizedPhone",
                "type": "text",
                "text": { "body": ${text.toJsonString()} }
            }
        """.trimIndent()
        return try {
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v18.0/$phoneNumberId/messages"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                logger.debug("WhatsApp sent for document {} to {}", request.documentId, to)
                DeliveryResult(true)
            } else {
                DeliveryResult(false, "WhatsApp failed: ${response.statusCode()} ${response.body()}")
            }
        } catch (e: Exception) {
            logger.error("WhatsApp failed for document {}: {}", request.documentId, e.message)
            DeliveryResult(false, e.message ?: "WhatsApp failed")
        }
    }

    private fun String.toJsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
