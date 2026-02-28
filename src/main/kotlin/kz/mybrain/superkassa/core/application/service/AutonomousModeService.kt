package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.QueuePort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory

/**
 * Сервис управления автономным режимом ККМ.
 * Выделен для устранения дублирования и упрощения логики enforceAutonomousLimits.
 */
class AutonomousModeService(
    private val storage: StoragePort,
    private val queue: QueuePort,
    private val clock: ClockPort,
    private val maxAutonomousDurationMs: Long = 72 * 60 * 60 * 1000L
) {
    private val logger = LoggerFactory.getLogger(AutonomousModeService::class.java)

    /**
     * Проверяет и обновляет состояние автономного режима ККМ.
     * ВАЖНО: должен вызываться внутри транзакции (storage.inTransaction) для атомарности операций.
     * Читает состояние ККМ, проверяет очередь и обновляет состояние атомарно.
     *
     * @param kkm ККМ для проверки.
     * @throws ConflictException Если автономный режим превысил максимальную длительность.
     */
    fun enforceAutonomousLimits(kkm: KkmInfo) {
        val now = clock.now()
        val hasQueue = queue.hasQueuedCommands(kkm.id) || storage.hasOfflineQueue(kkm.id)
        val autonomousSince = kkm.autonomousSince

        // Если есть очередь, но автономный режим не начат - начинаем его
        if (autonomousSince == null && hasQueue) {
            storage.updateKkm(kkm.copy(updatedAt = now, autonomousSince = now))
            return
        }

        // Если автономный режим был, но очередь очистилась - завершаем его
        if (autonomousSince != null && !hasQueue && kkm.state != KkmState.BLOCKED.name) {
            storage.updateKkm(kkm.copy(updatedAt = now, autonomousSince = null))
            return
        }

        // Если автономный режим превысил максимальную длительность - блокируем ККМ
        if (autonomousSince != null && now - autonomousSince > maxAutonomousDurationMs) {
            if (kkm.state != KkmState.BLOCKED.name) {
                storage.updateKkm(kkm.copy(updatedAt = now, state = KkmState.BLOCKED.name))
            }
            throw ConflictException(ErrorMessages.kkmAutonomousTooLong(), "KKM_AUTONOMOUS_TOO_LONG")
        }

        // Если ККМ заблокирована, но очередь очистилась - разблокируем
        if (kkm.state == KkmState.BLOCKED.name) {
            if (hasQueue) {
                throw ConflictException(
                    ErrorMessages.kkmAutonomousTooLong(),
                    "KKM_AUTONOMOUS_TOO_LONG"
                )
            }
            storage.updateKkm(
                kkm.copy(
                    updatedAt = now,
                    state = KkmState.ACTIVE.name,
                    autonomousSince = null
                )
            )
        }
    }
}
