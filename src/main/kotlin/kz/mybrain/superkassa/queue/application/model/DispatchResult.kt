package kz.mybrain.superkassa.queue.application.model

import kz.mybrain.superkassa.queue.domain.model.QueueStatus

/**
 * Результат обработки команды очереди.
 */
data class DispatchResult(
    val status: QueueStatus,
    val errorMessage: String? = null,
    val retryAt: Long? = null
)
