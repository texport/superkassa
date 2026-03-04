package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.application.service.ShiftCountersRecalculator
import kz.mybrain.superkassa.core.application.zxreport.ZxReportBuilder
import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.data.ofd.OfdRequestFactory
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kotlinx.serialization.json.JsonObject

/**
 * Стратегия построения запросов для COMMAND_CLOSE_SHIFT (Z-отчет, закрытие смены).
 */
class CloseShiftRequestBuilderStrategy(
    private val storage: StoragePort,
    private val shiftCountersRecalculator: ShiftCountersRecalculator
) : OfdRequestBuilderStrategy {
    override fun canHandle(commandType: OfdCommandType): Boolean =
        commandType == OfdCommandType.CLOSE_SHIFT

    override fun build(command: OfdCommandRequest, config: OfdConfig): JsonObject? {
        val serviceBlock = buildServiceBlock(command) ?: return null
        val shift = storage.findOpenShift(command.kkmId) ?: return null
        // Для Z-отчета обязательно пересобираем счётчики смены из документов.
        val counters = shiftCountersRecalculator
            .rebuildAndPersistShiftCounters(command.kkmId, shift)
        val now = command.offlineEndMillis ?: System.currentTimeMillis()
        val shiftNo = shift.shiftNo.toInt().coerceAtLeast(0)

        val zxInput = ZxReportBuilder.build(
            counters = counters,
            dateTimeMillis = now,
            shiftNumber = shiftNo,
            openShiftTimeMillis = shift.openedAt,
            closeShiftTimeMillis = now
        )

        val zxReport = OfdRequestFactory.buildZxReportInternal(zxInput)

        return OfdRequestFactory.buildCloseShiftRequest(
            ofdId = command.ofdProviderId.lowercase(),
            protocolVersion = config.protocolVersion,
            deviceId = command.deviceId,
            token = command.token,
            reqNum = command.reqNum,
            closeTimeMillis = now,
            frShiftNumber = shiftNo,
            zxReport = zxReport,
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
