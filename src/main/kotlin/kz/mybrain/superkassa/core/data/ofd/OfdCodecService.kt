package kz.mybrain.superkassa.core.data.ofd

import kz.mybrain.ofdcodec.application.DefaultRegistry
import kz.mybrain.ofdcodec.application.OfdCodec
import kz.mybrain.ofdcodec.domain.model.OfdCodecException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Сервис сериализации запросов ОФД через ofd-proto-codec.
 */
class OfdCodecService(
    private val codec: OfdCodec = OfdCodec(DefaultRegistry.create())
) {
    fun encode(json: JsonElement): ByteArray {
        val result = codec.encode(json)
        if (result.isFailure) {
            val ex = result.exceptionOrNull()
            val details = (ex as? OfdCodecException)?.errors?.joinToString("\n") {
                "RU: ${it.messageRu} | EN: ${it.messageEn} | path=${it.path} | code=${it.code}"
            } ?: ""
            error("OFD encode error.\n$details")
        }
        val output = result.getOrNull() ?: error("OFD encode returned null")
        val base64 = output["messageBase64"]?.jsonPrimitive?.content ?: error("messageBase64 missing")
        return try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            error("Invalid Base64 format in OFD response: ${e.message}")
        }
    }

    fun decode(bytes: ByteArray): JsonObject {
        val result = codec.decode(bytes)
        if (result.isFailure) {
            val ex = result.exceptionOrNull()
            val details = (ex as? OfdCodecException)?.errors?.joinToString("\n") {
                "RU: ${it.messageRu} | EN: ${it.messageEn} | path=${it.path} | code=${it.code}"
            } ?: ""
            error("OFD decode error.\n$details")
        }
        return result.getOrNull() ?: error("OFD decode returned null")
    }

    companion object {
        fun parseJson(text: String): JsonElement = Json.parseToJsonElement(text)
    }
}
