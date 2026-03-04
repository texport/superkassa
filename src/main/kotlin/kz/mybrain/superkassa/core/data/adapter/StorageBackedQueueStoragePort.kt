package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.QueueTaskDto
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommandType
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort

/**
 * Реализация QueueStoragePort через StoragePort.
 * Offline queue не общается с БД напрямую — только через storage.
 */
class StorageBackedQueueStoragePort(
    private val storage: StoragePort
) : QueueStoragePort {
    override fun enqueue(command: QueueCommand): Boolean {
        return storage.enqueueQueueTask(
            QueueTaskDto(
                id = command.id,
                cashboxId = command.cashboxId,
                lane = command.lane.name,
                type = command.type.name,
                payloadRef = command.payloadRef,
                createdAt = command.createdAt,
                status = command.status.name,
                attempt = command.attempt,
                nextAttemptAt = command.nextAttemptAt,
                lastError = command.lastError
            )
        )
    }

    override fun nextPending(cashboxId: String, lane: QueueLane, now: Long): QueueCommand? {
        return storage.nextPendingQueueTask(cashboxId, lane.name, now)?.let { toCommand(it) }
    }

    override fun updateStatus(
        id: String,
        status: QueueStatus,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean {
        return storage.updateQueueTaskStatus(id, status.name, attempt, lastError, nextAttemptAt)
    }

    override fun markInProgress(id: String, now: Long): Boolean {
        return storage.markQueueTaskInProgress(id, now)
    }

    override fun listByCashbox(
        cashboxId: String,
        lane: QueueLane,
        limit: Int,
        offset: Int
    ): List<QueueCommand> {
        return storage.listQueueTasksByCashbox(cashboxId, lane.name, limit, offset)
            .map { toCommand(it) }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        return storage.deleteQueueTasksByCashbox(cashboxId)
    }

    private fun toCommand(dto: QueueTaskDto): QueueCommand =
        QueueCommand(
            id = dto.id,
            cashboxId = dto.cashboxId,
            lane = QueueLane.valueOf(dto.lane),
            type = QueueCommandType.valueOf(dto.type),
            payloadRef = dto.payloadRef,
            createdAt = dto.createdAt,
            status = QueueStatus.valueOf(dto.status),
            attempt = dto.attempt,
            nextAttemptAt = dto.nextAttemptAt,
            lastError = dto.lastError
        )
}
