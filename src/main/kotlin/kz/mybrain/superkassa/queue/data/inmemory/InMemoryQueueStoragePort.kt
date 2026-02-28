package kz.mybrain.superkassa.queue.data.inmemory

import kz.mybrain.superkassa.queue.domain.model.QueueCommand
import kz.mybrain.superkassa.queue.domain.model.QueueLane
import kz.mybrain.superkassa.queue.domain.model.QueueStatus
import kz.mybrain.superkassa.queue.domain.port.QueueStoragePort
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory реализация очереди (для тестов).
 */
class InMemoryQueueStoragePort : QueueStoragePort {
    private val storage = ConcurrentHashMap<String, MutableList<QueueCommand>>()

    override fun enqueue(command: QueueCommand): Boolean {
        val list = storage.computeIfAbsent(command.cashboxId) { mutableListOf() }
        synchronized(list) {
            list.add(command)
        }
        return true
    }

    override fun nextPending(cashboxId: String, lane: QueueLane, now: Long): QueueCommand? {
        val list = storage[cashboxId] ?: return null
        synchronized(list) {
            return list
                .asSequence()
                .filter { it.lane == lane }
                .filter { it.status == QueueStatus.PENDING || it.status == QueueStatus.FAILED }
                .filter { it.nextAttemptAt == null || it.nextAttemptAt <= now }
                .sortedBy { it.createdAt }
                .firstOrNull()
        }
    }

    override fun updateStatus(
        id: String,
        status: QueueStatus,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean {
        storage.values.forEach { list ->
            synchronized(list) {
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val current = list[idx]
                    list[idx] = current.copy(
                        status = status,
                        attempt = attempt,
                        lastError = lastError,
                        nextAttemptAt = nextAttemptAt
                    )
                    return true
                }
            }
        }
        return false
    }

    override fun markInProgress(id: String, now: Long): Boolean {
        storage.values.forEach { list ->
            synchronized(list) {
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val current = list[idx]
                    list[idx] = current.copy(status = QueueStatus.IN_PROGRESS)
                    return true
                }
            }
        }
        return false
    }

    override fun listByCashbox(cashboxId: String, lane: QueueLane, limit: Int, offset: Int): List<QueueCommand> {
        val list = storage[cashboxId] ?: return emptyList()
        synchronized(list) {
            return list
                .filter { it.lane == lane }
                .sortedBy { it.createdAt }
                .drop(offset)
                .take(limit)
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        return storage.remove(cashboxId) != null
    }
}
