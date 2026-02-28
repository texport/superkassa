package kz.mybrain.superkassa.core.data.ofd

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Утилиты для извлечения данных из ответа ОФД.
 */
object OfdResponseUtils {

    /**
     * Извлекает URL чека из ответа ОФД.
     * Ответ содержит payload.ticket.qrCodeBase64 — Base64-кодированный URL.
     *
     * @param responseJson JSON-ответ ОФД (из OfdCommandResult.responseJson).
     * @return URL чека или null, если не найден.
     */
    fun extractReceiptUrl(responseJson: JsonObject?): String? {
        if (responseJson == null) return null
        val payload = responseJson["payload"] ?: return null
        val payloadObj = payload.jsonObject
        val ticket = payloadObj["ticket"] ?: return null
        val ticketObj = ticket.jsonObject
        val qrCodeBase64 = ticketObj["qrCodeBase64"]?.jsonPrimitive?.content ?: return null
        return try {
            String(Base64.getDecoder().decode(qrCodeBase64), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
