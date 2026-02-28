package kz.mybrain.superkassa.core.domain.model

/**
 * Запрос к ОФД-менеджеру.
 */
data class OfdCommandRequest(
    val kkmId: String,
    val commandType: OfdCommandType,
    val payloadRef: String,
    val ofdProviderId: String,
    val ofdEnvironmentId: String,
    val deviceId: Long,
    val token: Long,
    val reqNum: Int,
    val registrationNumber: String? = null,
    val factoryNumber: String? = null,
    val ofdSystemId: String? = null,
    val serviceInfo: OfdServiceInfo? = null,
    val offlineBeginMillis: Long? = null,
    val offlineEndMillis: Long? = null
)

