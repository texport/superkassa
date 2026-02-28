package kz.mybrain.superkassa.queue.data.inmemory

import kz.mybrain.superkassa.queue.domain.port.LeaseLockPort
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory lease/lock (для тестов).
 */
class InMemoryLeaseLockPort : LeaseLockPort {
    private data class Lock(val ownerId: String, val leaseUntil: Long)

    private val locks = ConcurrentHashMap<String, Lock>()

    override fun tryAcquire(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean {
        return locks.compute(cashboxId) { _, existing ->
            if (existing == null || existing.leaseUntil < now) {
                Lock(ownerId, leaseUntil)
            } else {
                existing
            }
        }?.ownerId == ownerId
    }

    override fun renew(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean {
        return locks.compute(cashboxId) { _, existing ->
            if (existing == null || existing.ownerId != ownerId || existing.leaseUntil < now) {
                existing
            } else {
                Lock(ownerId, leaseUntil)
            }
        }?.ownerId == ownerId
    }

    override fun release(cashboxId: String, ownerId: String): Boolean {
        var released = false
        locks.compute(cashboxId) { _, existing ->
            when {
                existing == null -> null
                existing.ownerId != ownerId -> existing
                else -> {
                    released = true
                    null
                }
            }
        }
        return released
    }
}
