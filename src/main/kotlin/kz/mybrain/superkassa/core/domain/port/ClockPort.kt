package kz.mybrain.superkassa.core.domain.port

/**
 * Источник времени.
 */
fun interface ClockPort {
    fun now(): Long
}
