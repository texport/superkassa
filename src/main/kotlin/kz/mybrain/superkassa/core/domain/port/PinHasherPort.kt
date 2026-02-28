package kz.mybrain.superkassa.core.domain.port

/**
 * Порт для хеширования PIN-кодов пользователей.
 * Позволяет подменить алгоритм хеширования при необходимости.
 */
interface PinHasherPort {
    /**
     * Хеширует PIN-код пользователя.
     * @param pin Исходный PIN-код
     * @return Хеш PIN-кода в виде hex-строки
     */
    fun hash(pin: String): String
}
