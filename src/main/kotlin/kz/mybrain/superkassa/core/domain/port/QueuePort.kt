package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.QueueCommandRequest

/**
 * Порт очереди команд (online/offline).
 */
interface QueuePort {
    fun enqueueOnline(command: QueueCommandRequest): Boolean
    fun enqueueOffline(command: QueueCommandRequest): Boolean
    fun hasQueuedCommands(kkmId: String): Boolean
    fun deleteQueuedCommands(kkmId: String): Boolean
}
