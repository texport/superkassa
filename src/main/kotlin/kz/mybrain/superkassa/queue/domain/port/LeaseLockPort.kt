package kz.mybrain.superkassa.queue.domain.port

/**
 * Порт lease/lock для кассы, чтобы исключать параллельные операции.
 */
interface LeaseLockPort {
    fun tryAcquire(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean
    fun renew(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean
    fun release(cashboxId: String, ownerId: String): Boolean
}
