package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.domain.port.TokenCodecPort

/**
 * Сервис работы с токенами ОФД (кодирование/декодирование).
 * Выделен из KkmService для соблюдения SRP.
 * Делегирует работу TokenCodecPort для соблюдения DIP.
 *
 * @deprecated Используйте TokenCodecPort напрямую. Этот класс оставлен для обратной совместимости.
 */
@Deprecated("Используйте TokenCodecPort напрямую", ReplaceWith("TokenCodecPort"))
class TokenService(private val tokenCodec: TokenCodecPort) {
    /**
     * Кодирует токен в строку для безопасного хранения.
     */
    fun encodeToken(token: Long): String = tokenCodec.encodeToken(token)

    /**
     * Декодирует токен из строки.
     * @return Токен или null, если строка пустая или некорректная.
     */
    fun decodeToken(tokenEncryptedBase64: String?): Long? = tokenCodec.decodeToken(tokenEncryptedBase64)

    /**
     * Парсит токен из строки с валидацией.
     */
    fun parseToken(token: String): Long = tokenCodec.parseToken(token)
}
