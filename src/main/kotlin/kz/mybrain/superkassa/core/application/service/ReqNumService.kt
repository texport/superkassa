package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.domain.port.StoragePort

/**
 * Сервис управления номерами запросов к ОФД.
 * Вынесен для устранения дублирования между KkmService и OfdSyncService.
 */
class ReqNumService(
    private val storage: StoragePort
) {
    private val reqNumCounterKey = "ofd.req_num"
    private val maxReqNum = 65535L

    /**
     * Вычисляет следующий номер запроса к ОФД.
     * @param kkmId ID ККМ
     * @param persist Если true, сохраняет новое значение в storage. Если false, только вычисляет без сохранения.
     * @return Следующий номер запроса
     */
    fun nextReqNum(kkmId: String, persist: Boolean = true): Int {
        val current = storage.loadCounters(kkmId, CounterScopes.GLOBAL, null)[reqNumCounterKey] ?: 0L
        val next = if (current >= maxReqNum) 0L else current + 1L
        if (persist) {
            storage.upsertCounter(kkmId, CounterScopes.GLOBAL, null, reqNumCounterKey, next)
        }
        return next.toInt()
    }

    /**
     * Вычисляет следующий номер запроса без сохранения (preview).
     */
    fun nextReqNumPreview(kkmId: String): Int = nextReqNum(kkmId, persist = false)
}
