package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.data.ofd.OfdRequestFactory
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kotlinx.serialization.json.JsonObject

/**
 * Стратегия построения запросов для COMMAND_MONEY_PLACEMENT (внесение/изъятие наличных).
 */
class MoneyPlacementRequestBuilderStrategy(
    private val storage: StoragePort
) : OfdRequestBuilderStrategy {
    override fun canHandle(commandType: OfdCommandType): Boolean {
        return commandType == OfdCommandType.MONEY_PLACEMENT
    }

    override fun build(command: OfdCommandRequest, config: OfdConfig): JsonObject? {
        val serviceBlock = buildServiceBlock(command) ?: return null
        val doc = storage.findFiscalDocumentById(command.payloadRef) ?: return null
        if (doc.docType != "CASH_IN" && doc.docType != "CASH_OUT") return null
        val amountBills = doc.totalAmount ?: 0L
        val ofdId = command.ofdProviderId.lowercase()
        return OfdRequestFactory.buildMoneyPlacementRequest(
            ofdId = ofdId,
            protocolVersion = config.protocolVersion,
            deviceId = command.deviceId,
            token = command.token,
            reqNum = command.reqNum,
            docType = doc.docType,
            amountBills = amountBills,
            createdAtMillis = doc.createdAt,
            serviceBlock = serviceBlock
        )
    }

    private fun buildServiceBlock(command: OfdCommandRequest): JsonObject? {
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
