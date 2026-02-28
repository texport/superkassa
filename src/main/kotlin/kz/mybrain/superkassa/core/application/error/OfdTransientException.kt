package kz.mybrain.superkassa.core.application.error

/**
 * Исключение при временном сбое связи с ОФД (сеть, таймаут).
 * Используется для retry логики.
 */
class OfdTransientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
