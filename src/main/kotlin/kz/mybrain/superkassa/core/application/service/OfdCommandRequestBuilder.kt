package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.port.OfdConfigPort

/**
 * Сборка [OfdCommandRequest] из данных ККМ и переопределений.
 * Упрощает логику sendOfdCommand и централизует валидацию.
 */
class OfdCommandRequestBuilder(
    private val ofdConfig: OfdConfigPort
) {
    /**
     * Собирает запрос к ОФД.
     * @param kkm Текущая ККМ
     * @param commandType Тип команды
     * @param payloadRef Ссылка на payload (documentId и т.п.)
     * @param token Токен ОФД
     * @param reqNum Номер запроса
     * @param now Текущее время (epoch ms) для offlinePeriod
     * @param serviceInfoOverride Переопределение serviceInfo (для SYSTEM/INFO)
     * @param registrationNumberOverride Переопределение регистрационного номера
     * @param factoryNumberOverride Переопределение заводского номера
     * @param ofdProviderOverride Переопределение тега ОФД (provider:environment)
     * @param defaultServiceInfo Дефолтные serviceInfo, если у ККМ не заданы
     */
    fun build(
        kkm: KkmInfo,
        commandType: OfdCommandType,
        payloadRef: String,
        token: Long,
        reqNum: Int,
        now: Long,
        serviceInfoOverride: OfdServiceInfo? = null,
        registrationNumberOverride: String? = null,
        factoryNumberOverride: String? = null,
        ofdProviderOverride: String? = null,
        defaultServiceInfo: () -> OfdServiceInfo
    ): OfdCommandRequest {
        val ofdTag = ofdProviderOverride
            ?: kkm.ofdProvider
            ?: throw ValidationException(ErrorMessages.ofdProviderRequired(), "OFD_PROVIDER_REQUIRED")

        val (providerId, environmentId) = ofdConfig.parseTag(ofdTag)
        ofdConfig.validateAndFormatTag(providerId, environmentId)

        val systemId = kkm.systemId
            ?: throw ValidationException(ErrorMessages.kkmSystemIdRequired(), "KKM_SYSTEM_ID_REQUIRED")
        val deviceId = systemId.toLongOrNull()
            ?: throw ValidationException(ErrorMessages.kkmSystemIdInvalid(systemId), "KKM_SYSTEM_ID_INVALID")

        val requiresService = commandType == OfdCommandType.SYSTEM || commandType == OfdCommandType.INFO
        val serviceInfo = if (requiresService) {
            serviceInfoOverride ?: kkm.ofdServiceInfo ?: defaultServiceInfo()
        } else {
            null
        }
        val registrationNumber = if (requiresService) {
            registrationNumberOverride ?: kkm.registrationNumber
                ?: throw ValidationException(ErrorMessages.kkmRegistrationRequired(), "KKM_REG_REQUIRED")
        } else {
            registrationNumberOverride ?: kkm.registrationNumber
        }
        val factoryNumber = if (requiresService) {
            factoryNumberOverride ?: kkm.factoryNumber
                ?: throw ValidationException(ErrorMessages.kkmFactoryRequired(), "KKM_FACTORY_REQUIRED")
        } else {
            factoryNumberOverride ?: kkm.factoryNumber
        }

        return OfdCommandRequest(
            kkmId = kkm.id,
            commandType = commandType,
            payloadRef = payloadRef,
            ofdProviderId = providerId,
            ofdEnvironmentId = environmentId,
            deviceId = deviceId,
            token = token,
            reqNum = reqNum,
            registrationNumber = registrationNumber,
            factoryNumber = factoryNumber,
            ofdSystemId = systemId,
            serviceInfo = serviceInfo,
            offlineBeginMillis = now - 60_000,
            offlineEndMillis = now
        )
    }
}
