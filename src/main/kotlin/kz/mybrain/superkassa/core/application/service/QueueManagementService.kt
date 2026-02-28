package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.domain.model.KkmMode
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.QueuePort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.queue.domain.model.QueueLane
import kz.mybrain.superkassa.queue.domain.model.QueueStatus
import kz.mybrain.superkassa.queue.domain.port.QueueStoragePort

/**
 * Сервис управления очередью ОФД для одной ККМ.
 *
 * Используется только в режиме программирования при закрытой смене.
 */
class QueueManagementService(
    private val storage: StoragePort,
    private val queuePort: QueuePort,
    private val queueStorage: QueueStoragePort,
    private val authorization: AuthorizationService
) {

    data class QueueItemView(
        val id: String,
        val lane: String,
        val type: String,
        val status: String,
        val attempt: Int,
        val nextAttemptAt: Long?,
        val lastError: String?
    )

    /**
     * Возвращает список задач очереди по ККМ (online/offline) для диагностики.
     */
    fun listQueue(kkmId: String, pin: String): List<QueueItemView> {
        requireProgrammingAndNoOpenShift(kkmId, pin)

        val online = queueStorage.listByCashbox(kkmId, QueueLane.ONLINE, limit = 100, offset = 0)
        val offline = queueStorage.listByCashbox(kkmId, QueueLane.OFFLINE, limit = 100, offset = 0)

        return (online + offline).map {
            QueueItemView(
                id = it.id,
                lane = it.lane.name,
                type = it.type.name,
                status = it.status.name,
                attempt = it.attempt,
                nextAttemptAt = it.nextAttemptAt,
                lastError = it.lastError
            )
        }
    }

    /**
     * Переводит все задачи со статусом FAILED в PENDING для повторной отправки.
     * Работает только в режиме программирования и при закрытой смене.
     */
    fun retryFailed(kkmId: String, pin: String): Int {
        requireProgrammingAndNoOpenShift(kkmId, pin)

        val nowHasQueued = queuePort.hasQueuedCommands(kkmId)
        if (!nowHasQueued) {
            // Нечего ретраить – просто возвращаем 0.
            return 0
        }

        var updated = 0
        storage.inTransaction {
            val online = queueStorage.listByCashbox(kkmId, QueueLane.ONLINE, limit = 100, offset = 0)
            val offline = queueStorage.listByCashbox(kkmId, QueueLane.OFFLINE, limit = 100, offset = 0)

            (online + offline)
                .filter { it.status == QueueStatus.FAILED }
                .forEach { cmd ->
                    val nextAttemptAt: Long? = null
                    val ok = queueStorage.updateStatus(
                        id = cmd.id,
                        status = QueueStatus.PENDING,
                        attempt = cmd.attempt,
                        lastError = null,
                        nextAttemptAt = nextAttemptAt
                    )
                    if (ok) {
                        updated++
                    }
                }
        }
        return updated
    }

    /**
     * Проверяет, что:
     * - ККМ существует;
     * - пользователь с PIN имеет роль ADMIN;
     * - ККМ в режиме программирования;
     * - нет открытой смены.
     */
    private fun requireProgrammingAndNoOpenShift(kkmId: String, pin: String) {
        val kkm = authorization.requireKkm(kkmId)
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))

        if (kkm.mode != KkmMode.PROGRAMMING.name || kkm.state != KkmState.PROGRAMMING.name) {
            throw ValidationException(
                ErrorMessages.kkmInProgramming(),
                "KKM_NOT_IN_PROGRAMMING_FOR_QUEUE"
            )
        }

        val openShift = storage.findOpenShift(kkmId)
        if (openShift != null) {
            throw ConflictException(
                ErrorMessages.kkmDeleteShiftOpen(),
                "QUEUE_MANAGEMENT_SHIFT_OPEN"
            )
        }
    }
}

