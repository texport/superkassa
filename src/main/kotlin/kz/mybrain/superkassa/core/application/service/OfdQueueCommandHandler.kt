package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommandType
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus
import org.slf4j.LoggerFactory

/**
 * Реализация QueueCommandHandler — отправляет команды из очереди в ОФД.
 */
class OfdQueueCommandHandler(
    private val ofdSyncService: OfdSyncService,
    private val storage: StoragePort,
    private val clock: ClockPort
) : QueueCommandHandler {
    private val logger = LoggerFactory.getLogger(OfdQueueCommandHandler::class.java)

    override fun handle(command: QueueCommand): DispatchResult {
        return try {
            val ofdType = toOfdCommandType(command.type)
            val result = ofdSyncService.sendFiscalCommand(command.cashboxId, ofdType, command.payloadRef)
            when (result.status) {
                OfdCommandStatus.OK -> {
                    updateDocumentOnSuccess(command, result)
                    DispatchResult(QueueStatus.SENT)
                }
                OfdCommandStatus.FAILED -> DispatchResult(
                    QueueStatus.FAILED,
                    errorMessage = result.errorMessage,
                    retryAt = clock.now() + 60_000
                )
                OfdCommandStatus.TIMEOUT -> DispatchResult(
                    QueueStatus.FAILED,
                    errorMessage = "OFD timeout",
                    retryAt = clock.now() + 30_000
                )
            }
        } catch (e: Exception) {
            logger.warn("Queue command failed. id={}", command.id, e)
            DispatchResult(
                QueueStatus.FAILED,
                errorMessage = e.message ?: "Unknown error",
                retryAt = clock.now() + 60_000
            )
        }
    }

    private fun toOfdCommandType(type: QueueCommandType): OfdCommandType = when (type) {
        QueueCommandType.TICKET -> OfdCommandType.TICKET
        QueueCommandType.MONEY_PLACEMENT -> OfdCommandType.MONEY_PLACEMENT
        QueueCommandType.REPORT_X, QueueCommandType.REPORT_Z -> OfdCommandType.REPORT
        QueueCommandType.CLOSE_SHIFT -> OfdCommandType.CLOSE_SHIFT
        QueueCommandType.INFO -> OfdCommandType.INFO
        QueueCommandType.SYSTEM -> OfdCommandType.SYSTEM
    }

    private fun updateDocumentOnSuccess(command: QueueCommand, result: kz.mybrain.superkassa.core.domain.model.OfdCommandResult) {
        if (command.type != QueueCommandType.TICKET && command.type != QueueCommandType.MONEY_PLACEMENT) return
        val now = clock.now()
        storage.updateReceiptStatus(
            documentId = command.payloadRef,
            fiscalSign = result.fiscalSign,
            autonomousSign = result.autonomousSign,
            ofdStatus = "SENT",
            deliveredAt = now,
            isAutonomous = false
        )
    }
}
