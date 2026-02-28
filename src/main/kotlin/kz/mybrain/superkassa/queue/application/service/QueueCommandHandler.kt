package kz.mybrain.superkassa.queue.application.service

import kz.mybrain.superkassa.queue.application.model.DispatchResult
import kz.mybrain.superkassa.queue.domain.model.QueueCommand

/**
 * Обработчик команд очереди (интеграция с ofd-manager).
 */
fun interface QueueCommandHandler {
    fun handle(command: QueueCommand): DispatchResult
}
