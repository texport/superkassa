package kz.mybrain.superkassa.core.domain.model

/**
 * Команда для очереди (online/offline).
 */
data class QueueCommandRequest(
    val kkmId: String,
    val type: String,
    val payloadRef: String
)

