package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort

/**
 * Реализация LeaseLockPort через StoragePort.
 * Offline queue не общается с БД напрямую — только через storage.
 */
class StorageBackedLeaseLockPort(
    private val storage: StoragePort
) : LeaseLockPort {
    override fun tryAcquire(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        return storage.tryAcquireQueueLock(cashboxId, ownerId, leaseUntil, now)
    }

    override fun renew(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean {
        return storage.renewQueueLock(cashboxId, ownerId, leaseUntil, now)
    }

    override fun release(cashboxId: String, ownerId: String): Boolean {
        return storage.releaseQueueLock(cashboxId, ownerId)
    }
}
