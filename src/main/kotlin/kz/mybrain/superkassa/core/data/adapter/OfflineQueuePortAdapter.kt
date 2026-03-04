package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.OfflineQueueCommandRequest
import kz.mybrain.superkassa.core.domain.port.OfflineQueuePort
import kz.mybrain.superkassa.offline_queue.application.policy.DefaultBackoffPolicy
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.application.service.QueueService
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommandType
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort

/**
 * Адаптер offline-очереди. Offline queue общается с БД только через storage (QueueStoragePort → StoragePort).
 */
class OfflineQueuePortAdapter(
    private val storage: QueueStoragePort,
    lockPort: LeaseLockPort,
    handler: QueueCommandHandler,
    ownerId: String
) : OfflineQueuePort {
    private val queueService = QueueService(
        storage = storage,
        lockPort = lockPort,
        handler = handler,
        backoffPolicy = DefaultBackoffPolicy(),
        ownerId = ownerId
    )

    override fun canSendDirectly(kkmId: String): Boolean = !queueService.hasOfflineQueue(kkmId)

    override fun enqueueOffline(command: OfflineQueueCommandRequest): Boolean {
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

    override fun deleteQueuedCommands(kkmId: String): Boolean {
        return storage.deleteByCashbox(kkmId)
    }

    override fun processOfflineBatch(kkmId: String, limit: Int): Int =
        queueService.processBatch(kkmId, QueueLane.OFFLINE, limit)

    private fun commandId(command: OfflineQueueCommandRequest): String {
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
