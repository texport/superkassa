package kz.mybrain.superkassa.queue.application.policy

/**
 * Поставщик времени для тестов и контроля.
 */
fun interface TimeProvider {
    fun now(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
