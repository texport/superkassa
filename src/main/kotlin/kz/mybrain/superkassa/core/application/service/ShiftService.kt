package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.QueueCommandRequest
import kz.mybrain.superkassa.core.domain.model.ReportRequest
import kz.mybrain.superkassa.core.domain.model.ReportResult
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.QueuePort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory

/**
 * Сервис управления сменами ККМ.
 * Выделен из KkmService для соблюдения SRP.
 */
class ShiftService(
    private val storage: StoragePort,
    private val queue: QueuePort,
    private val idGenerator: IdGenerator,
    private val clock: ClockPort,
    private val authorization: AuthorizationService,
    private val maxShiftDurationMs: Long = 24 * 60 * 60 * 1000L
) {
    private val logger = LoggerFactory.getLogger(ShiftService::class.java)

    /**
     * Открывает новую смену для ККМ.
     * Предполагается, что проверка операционности (requireOperational) выполняется вызывающим кодом.
     *
     * @param kkmId Идентификатор ККМ.
     * @param pin ПИН-код администратора.
     * @return Информация об открытой смене.
     * @throws ConflictException Если смена уже открыта.
     */
    fun openShift(kkmId: String, pin: String): ShiftInfo {
        return storage.inTransaction {
            val kkm = authorization.requireKkm(kkmId)
            requireNotProgramming(kkm)
            authorization.requireRole(kkm.id, pin, setOf(UserRole.ADMIN))
            val now = clock.now()
            val shiftId = idGenerator.nextId()
            val existingShift = storage.findOpenShift(kkmId)
            if (existingShift != null) {
                throw ConflictException(
                    message = ErrorMessages.shiftAlreadyOpen(),
                    code = "SHIFT_ALREADY_OPEN"
                )
            }
            val shiftNo = (kkm.lastShiftNo?.toLong() ?: 0L) + 1L
            val shift = ShiftInfo(
                id = shiftId,
                kkmId = kkmId,
                shiftNo = shiftNo,
                status = ShiftStatus.OPEN,
                openedAt = now
            )
            storage.createShift(shift)
            storage.updateKkm(kkm.copy(updatedAt = now, lastShiftNo = shiftNo.toInt()))
            shift
        }
    }

    /**
     * Закрывает смену и создает Z отчет.
     * Предполагается, что проверка операционности (requireOperational) выполняется вызывающим кодом.
     * @throws ConflictException Если смена не открыта.
     */
    fun closeShift(kkmId: String, pin: String): ReportResult {
        return storage.inTransaction {
            val kkm = authorization.requireKkm(kkmId)
            requireNotProgramming(kkm)
            authorization.requireRole(kkm.id, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))
            val shift = storage.findOpenShift(kkmId)
                ?: throw ConflictException(
                    ErrorMessages.shiftNotOpen(),
                    "SHIFT_NOT_OPEN"
                )
            val documentId = idGenerator.nextId()
            val now = clock.now()

            val hasQueue = queue.hasQueuedCommands(kkmId) || storage.hasOfflineQueue(kkmId)
            val command = QueueCommandRequest(
                kkmId = kkmId,
                type = OfdCommandType.CLOSE_SHIFT.value,
                payloadRef = documentId
            )
            if (hasQueue) {
                // По протоколу ОФД (п. 5.2) CLOSE_SHIFT при наличии OFFLINE-очереди должен идти как OFFLINE:
                // не отправляем сразу, помещаем в автономную линию.
                queue.enqueueOffline(command)
            } else {
                queue.enqueueOnline(command)
            }

            storage.closeShift(shift.id, ShiftStatus.CLOSED, now, documentId)
            ReportResult(documentId = documentId)
        }
    }

    /**
     * Проверяет и при необходимости автоматически закрывает смену, если она превышает максимальную длительность.
     *
     * @param kkm ККМ для проверки.
     * @throws ConflictException Если смена слишком длинная и autoCloseShift = false.
     */
    fun enforceShiftDuration(kkm: KkmInfo) {
        val openShift = storage.findOpenShift(kkm.id) ?: return
        val now = clock.now()
        if (now - openShift.openedAt <= maxShiftDurationMs) return
        if (kkm.autoCloseShift) {
            autoCloseShift(kkm, openShift, now)
            return
        }
        throw ConflictException(ErrorMessages.shiftTooLong(), "SHIFT_TOO_LONG")
    }

    /**
     * Автоматически закрывает смену.
     */
    private fun autoCloseShift(kkm: KkmInfo, shift: ShiftInfo, now: Long) {
        val documentId = idGenerator.nextId()
        val hasQueue = queue.hasQueuedCommands(kkm.id) || storage.hasOfflineQueue(kkm.id)
        val command = QueueCommandRequest(
            kkmId = kkm.id,
            type = OfdCommandType.CLOSE_SHIFT.value,
            payloadRef = documentId
        )
        if (hasQueue) {
            queue.enqueueOffline(command)
        } else {
            queue.enqueueOnline(command)
        }
        storage.closeShift(shift.id, ShiftStatus.CLOSED, now, documentId)
    }

    private fun requireNotProgramming(kkm: KkmInfo) {
        if (kkm.state == KkmState.PROGRAMMING.name) {
            throw kz.mybrain.superkassa.core.application.error.ValidationException(
                ErrorMessages.kkmInProgramming(),
                "KKM_IN_PROGRAMMING"
            )
        }
    }
}
