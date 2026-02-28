package kz.mybrain.superkassa.core.domain.port

/**
 * Порт для кодирования/декодирования токенов ОФД.
 * Соблюдает принцип Dependency Inversion (DIP).
 */
interface TokenCodecPort {
    /**
     * Кодирует токен в строку для безопасного хранения.
     */
    fun encodeToken(token: Long): String

    /**
     * Декодирует токен из строки.
     * @return Токен или null, если строка пустая или некорректная.
     */
    fun decodeToken(tokenEncryptedBase64: String?): Long?

    /**
     * Парсит токен из строки с валидацией.
     * @throws kz.mybrain.superkassa.core.application.error.ValidationException Если токен невалидный.
     */
    fun parseToken(token: String): Long
}
