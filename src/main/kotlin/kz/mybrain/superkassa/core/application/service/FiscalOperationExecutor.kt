package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory

/**
 * Исполнитель идемпотентных фискальных операций.
 * Вынесен для устранения дублирования между createReceipt и createCashOperation.
 */
class FiscalOperationExecutor(
    private val storage: StoragePort,
    private val idGenerator: IdGenerator,
    private val clock: ClockPort,
    private val authorization: AuthorizationService
) {
    private val logger = LoggerFactory.getLogger(FiscalOperationExecutor::class.java)

    /**
     * Выполняет идемпотентную фискальную операцию.
     *
     * @param kkmId ID ККМ
     * @param pin ПИН-код пользователя
     * @param idempotencyKey Ключ идемпотентности
     * @param operationType Тип операции (для логирования)
     * @param checkShift Проверка наличия открытой смены
     * @param saveOperation Сохранение операции в storage
     * @param sendOfdCommand Отправка команды в ОФД
     * @param processResult Обработка результата ОФД (последний аргумент — контекст чека (request, shiftId) или null для не-чеков).
     * @param buildResult Построение результата операции
     * @return Результат операции
     */
    fun <T> executeIdempotentFiscalOperation(
        kkmId: String,
        pin: String,
        idempotencyKey: String,
        operationType: String,
        checkShift: () -> String, // Возвращает shiftId
        saveOperation: (String, Long, String) -> Unit, // documentId, now, shiftId
        sendOfdCommand: (KkmInfo, String) -> OfdCommandResult, // kkm, documentId -> result
        processResult: (KkmInfo, String, String, OfdCommandResult, OfdCommandType, Long, Pair<ReceiptRequest, String>?) -> Unit,
        buildResult: (String, OfdCommandResult) -> T,
        receiptContextProvider: ((String) -> Pair<ReceiptRequest, String>?)? = null
    ): T {
        return storage.inTransaction {
            val kkm = authorization.requireKkm(kkmId)
            requireOperational(kkm)
            authorization.requireRole(kkm.id, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))

            // Проверка идемпотентности
            val existing = storage.findIdempotencyResponse(kkmId, idempotencyKey)
            if (existing != null) {
                logger.info(
                    "Idempotency hit. kkmId={}, key={}, operation={}",
                    kkmId,
                    idempotencyKey,
                    operationType
                )
                // Для идемпотентности возвращаем результат с существующим documentId
                // Создаем пустой OfdCommandResult, так как реальные данные уже сохранены
                val emptyResult = kz.mybrain.superkassa.core.domain.model.OfdCommandResult(
                    status = kz.mybrain.superkassa.core.domain.model.OfdCommandStatus.OK,
                    responseBin = null,
                    responseJson = null,
                    responseToken = null,
                    responseReqNum = null,
                    resultCode = 0,
                    resultText = null,
                    errorMessage = null,
                    fiscalSign = null,
                    autonomousSign = null
                )
                @Suppress("UNCHECKED_CAST")
                return@inTransaction buildResult(existing, emptyResult) as T
            }

            storage.insertIdempotency(kkmId, idempotencyKey, operationType)

            // Проверка смены
            val shiftId = checkShift()

            // Сохранение операции
            val documentId = idGenerator.nextId()
            val now = clock.now()
            saveOperation(documentId, now, shiftId)

            // Отправка команды в ОФД
            val ofdResult = sendOfdCommand(kkm, documentId)

            // Определение типа команды из результата (упрощенно)
            val commandType = when (operationType) {
                "CREATE_RECEIPT" -> OfdCommandType.TICKET
                "CASH_IN", "CASH_OUT" -> OfdCommandType.MONEY_PLACEMENT
                else -> OfdCommandType.TICKET
            }

            val receiptContext = receiptContextProvider?.invoke(shiftId)

            // Обработка результата
            processResult(kkm, documentId, kkmId, ofdResult, commandType, now, receiptContext)

            // Обновление идемпотентности
            storage.updateIdempotencyResponse(kkmId, idempotencyKey, documentId)

            // Построение результата
            buildResult(documentId, ofdResult)
        }
    }

    private fun requireOperational(kkm: KkmInfo) {
        if (kkm.state == kz.mybrain.superkassa.core.domain.model.KkmState.PROGRAMMING.name) {
            throw kz.mybrain.superkassa.core.application.error.ValidationException(
                ErrorMessages.kkmInProgramming(),
                "KKM_IN_PROGRAMMING"
            )
        }
    }
}
