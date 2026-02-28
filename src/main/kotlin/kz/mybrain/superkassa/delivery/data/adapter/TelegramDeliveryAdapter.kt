package kz.mybrain.superkassa.delivery.data.adapter

import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * Адаптер отправки чеков в Telegram (Bot API).
 */
class TelegramDeliveryAdapter(
    private val botToken: String
) : DeliveryAdapter {
    override val channel: DeliveryChannel = DeliveryChannel.TELEGRAM
    private val logger = LoggerFactory.getLogger(TelegramDeliveryAdapter::class.java)
    private val http = HttpClient.newBuilder().build()

    override fun send(request: DeliveryRequest): DeliveryResult {
        val chatId = request.destination ?: return DeliveryResult(false, "Telegram chat_id required")
        val text = when {
            request.payloadUrl != null -> "Чек: ${request.payloadUrl}"
            else -> "Чек ${request.documentId} готов"
        }
        return try {
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8)
            val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText"
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
            val response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                logger.debug("Telegram sent for document {} to chat {}", request.documentId, chatId)
                DeliveryResult(true)
            } else {
                DeliveryResult(false, "Telegram failed: ${response.statusCode()} ${response.body()}")
            }
        } catch (e: Exception) {
            logger.error("Telegram failed for document {}: {}", request.documentId, e.message)
            DeliveryResult(false, e.message ?: "Telegram failed")
        }
    }
}
