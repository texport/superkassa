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
 * Адаптер отправки SMS через HTTP API провайдера.
 * Ожидает providerUrl с плейсхолдерами {phone} и {text}, или endpoint + apiKey.
 */
class SmsDeliveryAdapter(
    private val providerUrl: String?,
    private val apiKey: String?
) : DeliveryAdapter {
    override val channel: DeliveryChannel = DeliveryChannel.SMS
    private val logger = LoggerFactory.getLogger(SmsDeliveryAdapter::class.java)
    private val http = HttpClient.newBuilder().build()

    override fun send(request: DeliveryRequest): DeliveryResult {
        val phone = request.destination ?: return DeliveryResult(false, "SMS destination (phone) required")
        val text = when {
            request.payloadUrl != null -> "Чек: ${request.payloadUrl}"
            else -> "Чек ${request.documentId} готов"
        }
        val url = providerUrl ?: return DeliveryResult(false, "SMS provider URL not configured")
        return try {
            val urlWithParams = url
                .replace("{phone}", phone)
                .replace("{text}", java.net.URLEncoder.encode(text, Charsets.UTF_8))
            val reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlWithParams))
                .GET()
            apiKey?.let { reqBuilder.header("Authorization", "Bearer $it") }
            val response = http.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                logger.debug("SMS sent for document {} to {}", request.documentId, phone)
                DeliveryResult(true)
            } else {
                DeliveryResult(false, "SMS failed: ${response.statusCode()} ${response.body()}")
            }
        } catch (e: Exception) {
            logger.error("SMS failed for document {}: {}", request.documentId, e.message)
            DeliveryResult(false, e.message ?: "SMS failed")
        }
    }
}
