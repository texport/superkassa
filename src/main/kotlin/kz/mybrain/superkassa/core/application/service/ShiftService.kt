package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.OfflineQueueCommandRequest
import kz.mybrain.superkassa.core.domain.model.ReportRequest
import kz.mybrain.superkassa.core.domain.model.ReportResult
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.model.DeliveryStatus
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.OfflineQueuePort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory

/**
 * Сервис управления сменами ККМ.
 * Выделен из KkmService для соблюдения SRP.
 */
class ShiftService(
    private val storage: StoragePort,
    private val queue: OfflineQueuePort,
    private val ofdSyncService: OfdSyncService,
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
            // Нумерация смен:
            // - если локальных смен ещё нет, то:
            //   - при lastShiftNo == 1 (новая касса, первая смена в ОФД, пустые счётчики) — первая локальная смена тоже №1;
            //   - иначе начинаем с lastShiftNo + 1 (касса уже работала где‑то ещё).
            // - если локальные смены есть — продолжаем от последнего локального номера.
            val lastLocalShiftNo = storage.listShifts(kkmId, limit = 1, offset = 0)
                .firstOrNull()
                ?.shiftNo

            val shiftNo = if (lastLocalShiftNo == null) {
                if (kkm.lastShiftNo == 1) {
                    1L
                } else {
                    (kkm.lastShiftNo?.toLong() ?: 0L) + 1L
                }
            } else {
                lastLocalShiftNo + 1L
            }
            val shift = ShiftInfo(
                id = shiftId,
                kkmId = kkmId,
                shiftNo = shiftNo,
                status = ShiftStatus.OPEN,
                openedAt = now
            )
            storage.createShift(shift)
            storage.updateKkm(kkm.copy(updatedAt = now, lastShiftNo = shiftNo.toInt()))

            // Инициализируем необнуляемые суммы на начало смены на основе глобальных счётчиков.
            val globalCounters = storage.loadCounters(kkmId, CounterScopes.GLOBAL, null)
            val operations = listOf(
                "OPERATION_SELL",
                "OPERATION_SELL_RETURN",
                "OPERATION_BUY",
                "OPERATION_BUY_RETURN"
            )
            operations.forEach { op ->
                val globalKey = CounterKeyFormats.NON_NULLABLE_SUM.format(op)
                val startValue = globalCounters[globalKey] ?: 0L
                if (startValue != 0L) {
                    val startShiftKey = CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format(op)
                    storage.upsertCounter(kkmId, CounterScopes.SHIFT, shiftId, startShiftKey, startValue)

                    // Одновременно фиксируем «финальные» необнуляемые суммы для смены,
                    // инициализируя их текущими глобальными значениями. В течение смены
                    // они будут накапливаться вместе с глобальными NON_NULLABLE_SUM.
                    val shiftNonNullableKey = CounterKeyFormats.NON_NULLABLE_SUM.format(op)
                    storage.upsertCounter(kkmId, CounterScopes.SHIFT, shiftId, shiftNonNullableKey, startValue)
                }
            }

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

            val hasQueue = !queue.canSendDirectly(kkmId)
            val (deliveryStatus, deliveryError) =
                if (hasQueue) {
                    val command = OfflineQueueCommandRequest(
                        kkmId = kkmId,
                        type = OfdCommandType.CLOSE_SHIFT.value,
                        payloadRef = documentId
                    )
                    queue.enqueueOffline(command)
                    DeliveryStatus.OFFLINE_QUEUED to null
                } else {
                    val result = ofdSyncService.sendFiscalCommand(kkmId, OfdCommandType.CLOSE_SHIFT, documentId)
                    when (result.status) {
                        OfdCommandStatus.OK -> DeliveryStatus.ONLINE_OK to null
                        OfdCommandStatus.TIMEOUT -> DeliveryStatus.OFFLINE_QUEUED to result.errorMessage
                        OfdCommandStatus.FAILED -> DeliveryStatus.ONLINE_ERROR to result.errorMessage
                    }
                }

            storage.closeShift(shift.id, ShiftStatus.CLOSED, now, documentId)
            ReportResult(
                documentId = documentId,
                deliveryStatus = deliveryStatus,
                deliveryError = deliveryError
            )
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
        val hasQueue = !queue.canSendDirectly(kkm.id)
        val command = OfflineQueueCommandRequest(
            kkmId = kkm.id,
            type = OfdCommandType.CLOSE_SHIFT.value,
            payloadRef = documentId
        )
        if (hasQueue) {
            queue.enqueueOffline(command)
        } else {
            ofdSyncService.sendFiscalCommand(kkm.id, OfdCommandType.CLOSE_SHIFT, documentId)
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
