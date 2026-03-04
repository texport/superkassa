package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.OfflineQueueCommandRequest

/**
 * Порт offline-очереди команд.
 * Offline queue — единственный источник истины о том, онлайн ли касса.
 * Core спрашивает разрешение через canSendDirectly перед прямой отправкой в ОФД.
 */
interface OfflineQueuePort {
    /**
     * Разрешает ли offline queue отправить документ напрямую в ОФД для этой кассы.
     * false = у кассы есть автономная очередь → core должен отдать в enqueueOffline.
     */
    fun canSendDirectly(kkmId: String): Boolean

    fun enqueueOffline(command: OfflineQueueCommandRequest): Boolean
    fun deleteQueuedCommands(kkmId: String): Boolean
    /**
     * Обрабатывает пакет команд из OFFLINE-очереди (вызывает воркер).
     * @return количество обработанных команд
     */
    fun processOfflineBatch(kkmId: String, limit: Int = 10): Int
}
