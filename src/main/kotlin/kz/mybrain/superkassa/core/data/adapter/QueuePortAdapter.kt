package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.QueueCommandRequest
import kz.mybrain.superkassa.core.domain.port.QueuePort
import kz.mybrain.superkassa.queue.application.policy.DefaultBackoffPolicy
import kz.mybrain.superkassa.queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.queue.application.service.QueueService
import kz.mybrain.superkassa.queue.domain.model.QueueCommand
import kz.mybrain.superkassa.queue.domain.model.QueueCommandType
import kz.mybrain.superkassa.queue.domain.model.QueueLane
import kz.mybrain.superkassa.queue.domain.model.QueueStatus
import kz.mybrain.superkassa.queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.queue.domain.port.QueueStoragePort

/**
 * Адаптер очереди core -> superkassa-queue.
 */
class QueuePortAdapter(
    private val storage: QueueStoragePort,
    lockPort: LeaseLockPort,
    handler: QueueCommandHandler,
    ownerId: String
) : QueuePort {
    private val queueService = QueueService(
        storage = storage,
        lockPort = lockPort,
        handler = handler,
        backoffPolicy = DefaultBackoffPolicy(),
        ownerId = ownerId
    )

    override fun enqueueOnline(command: QueueCommandRequest): Boolean {
        return queueService.enqueue(
            QueueCommand(
                id = commandId(command),
                cashboxId = command.kkmId,
                lane = QueueLane.ONLINE,
                type = mapType(command.type),
                payloadRef = command.payloadRef,
                createdAt = System.currentTimeMillis(),
                status = QueueStatus.PENDING,
                attempt = 0
            )
        )
    }

    override fun enqueueOffline(command: QueueCommandRequest): Boolean {
        return queueService.enqueue(
            QueueCommand(
                id = commandId(command),
                cashboxId = command.kkmId,
                lane = QueueLane.OFFLINE,
                type = mapType(command.type),
                payloadRef = command.payloadRef,
                createdAt = System.currentTimeMillis(),
                status = QueueStatus.PENDING,
                attempt = 0
            )
        )
    }

    override fun hasQueuedCommands(kkmId: String): Boolean {
        val online = storage.listByCashbox(kkmId, QueueLane.ONLINE, 100)
        val offline = storage.listByCashbox(kkmId, QueueLane.OFFLINE, 100)
        return (online + offline).any { it.status != QueueStatus.SENT }
    }

    override fun deleteQueuedCommands(kkmId: String): Boolean {
        return storage.deleteByCashbox(kkmId)
    }

    private fun commandId(command: QueueCommandRequest): String {
        return "${command.kkmId}:${command.type}:${command.payloadRef}"
    }

    private fun mapType(type: String): QueueCommandType {
        return when (type) {
            "COMMAND_TICKET" -> QueueCommandType.TICKET
            "COMMAND_REPORT" -> QueueCommandType.REPORT_X
            "COMMAND_CLOSE_SHIFT" -> QueueCommandType.CLOSE_SHIFT
            "COMMAND_MONEY_PLACEMENT" -> QueueCommandType.MONEY_PLACEMENT
            "COMMAND_INFO" -> QueueCommandType.INFO
            "COMMAND_SYSTEM" -> QueueCommandType.SYSTEM
            else -> QueueCommandType.TICKET
        }
    }
}
