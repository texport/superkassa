package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.port.ClockPort

/**
 * Системное время.
 */
object SystemClock : ClockPort {
    override fun now(): Long = System.currentTimeMillis()
}
