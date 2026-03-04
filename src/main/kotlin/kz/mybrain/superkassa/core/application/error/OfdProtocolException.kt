package kz.mybrain.superkassa.core.application.error

/**
 * Ошибка протокола ОФД (encode/decode, отсутствующие поля и т.п.).
 * Не является ошибкой связи — в офлайн очередь не помещаем.
 */
class OfdProtocolException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
