package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.data.ofd.OfdRequestFactory
import kotlinx.serialization.json.JsonObject

/**
 * Стратегия построения запросов для команд SYSTEM и INFO.
 */
class ServiceRequestBuilderStrategy : OfdRequestBuilderStrategy {
    override fun canHandle(commandType: OfdCommandType): Boolean {
        return commandType == OfdCommandType.SYSTEM || commandType == OfdCommandType.INFO
    }

    override fun build(command: OfdCommandRequest, config: OfdConfig): JsonObject? {
        val serviceInfo = command.serviceInfo ?: return null
        val registrationNumber = command.registrationNumber ?: return null
        val factoryNumber = command.factoryNumber ?: return null
        val systemId = command.ofdSystemId ?: return null
        val offlineBegin = command.offlineBeginMillis ?: System.currentTimeMillis()
        val offlineEnd = command.offlineEndMillis ?: System.currentTimeMillis()
        val ofdId = command.ofdProviderId.lowercase()

        return OfdRequestFactory.buildServiceRequest(
            ofdId = ofdId,
            protocolVersion = config.protocolVersion,
            commandType = command.commandType.value,
            deviceId = command.deviceId,
            token = command.token,
            reqNum = command.reqNum,
            offlineBeginMillis = offlineBegin,
            offlineEndMillis = offlineEnd,
            registrationNumber = registrationNumber,
            factoryNumber = factoryNumber,
            systemId = systemId,
            serviceInfo = serviceInfo
        )
    }
}
