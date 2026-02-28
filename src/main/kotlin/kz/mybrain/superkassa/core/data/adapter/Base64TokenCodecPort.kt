package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.domain.port.TokenCodecPort
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Реализация TokenCodecPort на базе Base64.
 */
class Base64TokenCodecPort : TokenCodecPort {
    private val logger = LoggerFactory.getLogger(Base64TokenCodecPort::class.java)

    override fun encodeToken(token: Long): String {
        return Base64.getEncoder().encodeToString(token.toString().toByteArray())
    }

    override fun decodeToken(tokenEncryptedBase64: String?): Long? {
        if (tokenEncryptedBase64.isNullOrBlank()) return null
        return try {
            val decoded = Base64.getDecoder().decode(tokenEncryptedBase64)
            val text = decoded.toString(Charsets.UTF_8)
            text.toLongOrNull()
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid Base64 format in token: {}", e.message)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error decoding token", e)
            null
        }
    }

    override fun parseToken(token: String): Long {
        return token.toLongOrNull()
            ?: throw ValidationException(
                ErrorMessages.ofdTokenInvalid(token),
                "OFD_TOKEN_INVALID"
            )
    }
}
