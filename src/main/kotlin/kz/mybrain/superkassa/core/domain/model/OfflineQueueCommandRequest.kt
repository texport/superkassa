package kz.mybrain.superkassa.core.domain.model

/**
 * Команда для offline-очереди.
 */
data class OfflineQueueCommandRequest(
    val kkmId: String,
    val type: String,
    val payloadRef: String
)
