package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.data.ofd.OfdRequestFactory
import kotlinx.serialization.json.JsonObject

/**
 * Стратегия построения запросов для команд с чеками (TICKET и др.).
 * Используется как fallback для команд, не обработанных другими стратегиями.
 */
class TicketRequestBuilderStrategy : OfdRequestBuilderStrategy {
    override fun canHandle(commandType: OfdCommandType): Boolean {
        // Только TICKET; REPORT, CLOSE_SHIFT, MONEY_PLACEMENT — отдельные стратегии
        return commandType == OfdCommandType.TICKET
    }

    override fun build(command: OfdCommandRequest, config: OfdConfig): JsonObject? {
        // Временная реализация для обратной совместимости
        // В будущем здесь должна быть логика извлечения ReceiptRequest из command
        val receipt =
            ReceiptRequest(
                kkmId = command.kkmId,
                pin = "0000",
                operation = kz.mybrain.superkassa.core.domain.model.ReceiptOperationType.SELL,
                items = emptyList(),
                payments = emptyList(),
                total = kz.mybrain.superkassa.core.domain.model.Money(1000, 0),
                idempotencyKey = "tmp",
                parentTicket = null
            )
        val ofdId = command.ofdProviderId.lowercase()
        val serviceBlock = buildServiceBlock(command)

        return OfdRequestFactory.buildTicketRequest(
            ofdId = ofdId,
            protocolVersion = config.protocolVersion,
            deviceId = command.deviceId,
            token = command.token,
            reqNum = command.reqNum,
            request = receipt,
            serviceBlock = serviceBlock
        )
    }

    private fun buildServiceBlock(command: OfdCommandRequest): kotlinx.serialization.json.JsonObject? {
        val serviceInfo = command.serviceInfo ?: return null
        val regNo = command.registrationNumber ?: return null
        val factoryNo = command.factoryNumber ?: return null
        val systemId = command.ofdSystemId ?: return null
        val begin = command.offlineBeginMillis ?: System.currentTimeMillis()
        val end = command.offlineEndMillis ?: System.currentTimeMillis()
        return OfdRequestFactory.buildServicePayload(
            serviceInfo = serviceInfo,
            registrationNumber = regNo,
            factoryNumber = factoryNo,
            systemId = systemId,
            offlineBeginMillis = begin,
            offlineEndMillis = end
        )
    }
}
