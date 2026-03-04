package kz.mybrain.superkassa.storage.domain.repository

/**
 * Репозиторий lease/lock для очереди (queue_lock).
 * Исключает параллельную обработку очереди по кассе.
 */
interface QueueLockRepository {
    fun tryAcquire(cashboxId: String, ownerId: String, leaseUntil: Long, acquiredAt: Long): Boolean
    fun renew(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean
    fun release(cashboxId: String, ownerId: String): Boolean
}
