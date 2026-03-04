package kz.mybrain.superkassa.core.data.ofd

import kz.mybrain.ofdcodec.application.DefaultRegistry
import kz.mybrain.ofdcodec.application.OfdCodec
import kz.mybrain.ofdcodec.domain.model.OfdCodecException
import kz.mybrain.superkassa.core.application.error.OfdProtocolException
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
            val formatted = formatOfdCodecErrors(result.exceptionOrNull())
            throw OfdProtocolException(formatted, result.exceptionOrNull())
        }
        val output = result.getOrNull()
            ?: throw OfdProtocolException("OFD encode returned null")
        val base64 = output["messageBase64"]?.jsonPrimitive?.content
            ?: throw OfdProtocolException("messageBase64 missing in OFD encode output")
        return try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw OfdProtocolException("Invalid Base64 format in OFD response: ${e.message}", e)
        }
    }

    fun decode(bytes: ByteArray): JsonObject {
        val result = codec.decode(bytes)
        if (result.isFailure) {
            val formatted = formatOfdCodecErrors(result.exceptionOrNull())
            throw OfdProtocolException(formatted, result.exceptionOrNull())
        }
        return result.getOrNull()
            ?: throw OfdProtocolException("OFD decode returned null")
    }

    private fun formatOfdCodecErrors(ex: Throwable?): String {
        val errors = (ex as? OfdCodecException)?.errors ?: return ex?.message ?: "Unknown OFD error"
        return errors.joinToString("; ") { err ->
            val msg = err.messageRu.takeIf { it.isNotBlank() }
                ?: err.messageEn.takeIf { it.isNotBlank() }
                ?: "Unknown error"
            val pathStr = err.path.takeIf { it.isNotBlank() }
            val codeStr = err.code.takeIf { it.isNotBlank() }
            val extra = listOfNotNull(
                pathStr?.let { "путь: $it" },
                codeStr?.let { "код: $it" }
            )
            if (extra.isNotEmpty()) "$msg (${extra.joinToString(", ")})" else msg
        }
    }

    companion object {
        fun parseJson(text: String): JsonElement = Json.parseToJsonElement(text)
    }
}
