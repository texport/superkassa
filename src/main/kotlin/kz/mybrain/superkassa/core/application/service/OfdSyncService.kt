package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.model.OfdAuthInfoResponse
import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.application.policy.SystemTimeGuard
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.superkassa.core.domain.port.QueuePort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Сервис синхронизации с ОФД.
 * Выделен из KkmService для соблюдения SRP.
 */
class OfdSyncService(
    private val storage: StoragePort,
    private val queue: QueuePort,
    private val ofd: OfdManagerPort,
    private val idGenerator: IdGenerator,
    private val clock: ClockPort,
    private val authorization: AuthorizationService,
    private val ofdCommandRequestBuilder: OfdCommandRequestBuilder,
    private val tokenCodec: kz.mybrain.superkassa.core.domain.port.TokenCodecPort,
    private val autonomousModeService: AutonomousModeService,
    private val reqNumService: ReqNumService
) {
    private val logger = LoggerFactory.getLogger(OfdSyncService::class.java)

    /**
     * Проверяет связь с ОФД (COMMAND_SYSTEM).
     */
    fun checkOfdConnection(kkmId: String): OfdCommandResult {
        val kkm = authorization.requireKkm(kkmId)
        return sendOfdCommand(
            kkm = kkm,
            commandType = OfdCommandType.SYSTEM,
            payloadRef = idGenerator.nextId()
        )
    }

    /**
     * Запрашивает информацию о кассе в ОФД (COMMAND_INFO).
     */
    fun getOfdInfo(kkmId: String): OfdCommandResult {
        val kkm = authorization.requireKkm(kkmId)
        return sendOfdCommand(
            kkm = kkm,
            commandType = OfdCommandType.INFO,
            payloadRef = idGenerator.nextId()
        )
    }

    /**
     * Синхронизирует сервисную информацию о ККМ с ОФД.
     * Обновляет также регистрационный номер и заводской номер из ответа ОФД.
     */
    fun syncOfdServiceInfo(kkmId: String, pin: String): OfdCommandResult {
        return syncFromOfdInfo(kkmId, pin) { kkm, responseJson ->
            val updatedServiceInfo = extractServiceInfo(
                responseJson,
                kkm.ofdServiceInfo ?: defaultServiceInfo()
            )
            val shiftNo = extractShiftNumber(responseJson) ?: kkm.lastShiftNo
            
            // Извлекаем регистрационный номер и заводской номер из ответа ОФД
            val registrationNumber = extractRegistrationNumber(responseJson) ?: kkm.registrationNumber
            val factoryNumber = extractFactoryNumber(responseJson) ?: kkm.factoryNumber
            
            storage.updateKkm(
                kkm.copy(
                    updatedAt = clock.now(),
                    lastShiftNo = shiftNo,
                    ofdServiceInfo = updatedServiceInfo,
                    registrationNumber = registrationNumber,
                    factoryNumber = factoryNumber
                )
            )
        }
    }

    /**
     * Синхронизирует счетчики ККМ с ОФД.
     */
    fun syncOfdCounters(kkmId: String, pin: String): OfdCommandResult {
        return syncFromOfdInfo(kkmId, pin) { kkm, responseJson ->
            updateCountersFromOfdInfo(kkm.id, responseJson)
            val shiftNo = extractShiftNumber(responseJson) ?: return@syncFromOfdInfo
            storage.updateKkm(kkm.copy(updatedAt = clock.now(), lastShiftNo = shiftNo))
        }
    }

    /**
     * Общая логика синхронизации с ОФД через COMMAND_INFO.
     * Вынесена для устранения дублирования между syncOfdServiceInfo и syncOfdCounters.
     */
    private fun syncFromOfdInfo(
        kkmId: String,
        pin: String,
        processResult: (KkmInfo, JsonObject?) -> Unit
    ): OfdCommandResult {
        val kkm = requireSyncAllowed(kkmId, pin)
        val result = sendOfdCommand(
            kkm = kkm,
            commandType = OfdCommandType.INFO,
            payloadRef = idGenerator.nextId()
        )
        if (result.status == OfdCommandStatus.OK) {
            storage.inTransaction {
                processResult(kkm, result.responseJson)
            }
        }
        return result
    }

    /**
     * Получает информацию об авторизации в ОФД (токен и следующий reqNum).
     */
    fun getOfdAuthInfo(kkmId: String, pin: String): OfdAuthInfoResponse {
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        val kkm = authorization.requireKkm(kkmId)
        val token = tokenCodec.decodeToken(kkm.tokenEncryptedBase64)?.toString()
        val nextReq = reqNumService.nextReqNumPreview(kkmId)
        return OfdAuthInfoResponse(token = token, nextReqNum = nextReq)
    }

    /**
     * Обновляет токен ОФД.
     */
    fun updateOfdToken(kkmId: String, pin: String, token: String): Boolean {
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        val parsed = tokenCodec.parseToken(token)
        return storage.updateKkmToken(kkmId, tokenCodec.encodeToken(parsed), clock.now())
    }

    private fun requireSyncAllowed(kkmId: String, pin: String): KkmInfo {
        ensureSystemTimeValid()
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        // Проверка выполняется в транзакции для атомарности операций с состоянием ККМ
        return storage.inTransaction {
            val kkm = authorization.requireKkm(kkmId)
            enforceAutonomousLimits(kkm)
            val openShift = storage.findOpenShift(kkmId)
            if (openShift != null) {
                throw ConflictException(ErrorMessages.kkmSyncShiftOpen(), "KKM_SYNC_SHIFT_OPEN")
            }
            if (queue.hasQueuedCommands(kkmId) || storage.hasOfflineQueue(kkmId)) {
                throw ConflictException(
                    ErrorMessages.kkmSyncQueueNotEmpty(),
                    "KKM_SYNC_QUEUE_NOT_EMPTY"
                )
            }
            kkm
        }
    }

    private fun sendOfdCommand(
        kkm: KkmInfo,
        commandType: OfdCommandType,
        payloadRef: String
    ): OfdCommandResult {
        val token = tokenCodec.decodeToken(kkm.tokenEncryptedBase64)
            ?: throw ValidationException(ErrorMessages.ofdTokenRequired(), "OFD_TOKEN_REQUIRED")
        val reqNum = reqNumService.nextReqNum(kkm.id)
        val now = clock.now()
        val request = ofdCommandRequestBuilder.build(
            kkm = kkm,
            commandType = commandType,
            payloadRef = payloadRef,
            token = token,
            reqNum = reqNum,
            now = now,
            defaultServiceInfo = ::defaultServiceInfo
        )
        val result = ofd.send(request)
        if (result.responseToken != null) {
            storage.updateKkmToken(kkm.id, tokenCodec.encodeToken(result.responseToken), now)
        }
        updateKkmBlockedStateFromOfd(kkm, result, now)
        return result
    }

    /**
     * Обновляет состояние блокировки ККМ в зависимости от resultCode ОФД.
     *
     * - При resultCode == 15 касса переводится в BLOCKED.
     * - При resultCode == 0, если касса была BLOCKED, переводится в ACTIVE.
     */
    private fun updateKkmBlockedStateFromOfd(kkm: KkmInfo, result: OfdCommandResult, now: Long) {
        val code = result.resultCode ?: return
        if (code == 15 && kkm.state != KkmState.BLOCKED.name) {
            storage.updateKkm(kkm.copy(updatedAt = now, state = KkmState.BLOCKED.name))
        } else if (code == 0 && kkm.state == KkmState.BLOCKED.name) {
            storage.updateKkm(kkm.copy(updatedAt = now, state = KkmState.ACTIVE.name))
        }
    }

    private fun enforceAutonomousLimits(kkm: KkmInfo) {
        autonomousModeService.enforceAutonomousLimits(kkm)
    }

    private fun ensureSystemTimeValid() {
        val result = SystemTimeGuard.validate(clock)
        if (!result.ok) {
            throw ValidationException(ErrorMessages.systemTimeInvalid(), "SYSTEM_TIME_INVALID")
        }
    }


    private fun defaultServiceInfo(): OfdServiceInfo {
        return OfdServiceInfo(
            orgTitle = "UNKNOWN",
            orgAddress = "UNKNOWN",
            orgAddressKz = "UNKNOWN",
            orgInn = "000000000000",
            orgOkved = "00000",
            geoLatitude = 0,
            geoLongitude = 0,
            geoSource = "UNKNOWN"
        )
    }

    private fun updateCountersFromOfdInfo(kkmId: String, responseJson: JsonObject?) {
        val zxReport = extractZxReport(responseJson) ?: return
        val nonNullable = zxReport["nonNullableSums"] as? JsonArray ?: return
        nonNullable.forEach { entry ->
            val obj = entry as? JsonObject ?: return@forEach
            val operation = obj["operation"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val sumObj = obj["sum"] as? JsonObject ?: return@forEach
            val bills = sumObj["bills"]?.jsonPrimitive?.longOrNull ?: return@forEach
            val key = CounterKeyFormats.NON_NULLABLE_SUM.format(operation)
            storage.upsertCounter(kkmId, CounterScopes.GLOBAL, null, key, bills)
        }
    }

    private fun extractShiftNumber(responseJson: JsonObject?): Int? {
        val zxReport = extractZxReport(responseJson) ?: return null
        return zxReport.getNestedInt(listOf("shiftNumber"))
    }

    /**
     * Extension-функции для упрощения работы с вложенными JsonObject.
     */
    private fun JsonObject.getNestedString(path: List<String>): String? {
        var current: JsonObject? = this
        for (i in 0 until path.size - 1) {
            current = current?.get(path[i])?.jsonObject
            if (current == null) return null
        }
        return current?.get(path.last())?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.getNestedInt(path: List<String>): Int? {
        var current: JsonObject? = this
        for (i in 0 until path.size - 1) {
            current = current?.get(path[i])?.jsonObject
            if (current == null) return null
        }
        return current?.get(path.last())?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.getNestedObject(path: List<String>): JsonObject? {
        var current: JsonObject? = this
        for (key in path) {
            current = current?.get(key)?.jsonObject
            if (current == null) return null
        }
        return current
    }

    private fun extractServiceInfo(
        responseJson: JsonObject?,
        fallback: OfdServiceInfo
    ): OfdServiceInfo {
        val payload = responseJson?.getNestedObject(listOf("payload")) ?: return fallback
        val service = payload.getNestedObject(listOf("service")) ?: return fallback
        val regInfo = service.getNestedObject(listOf("regInfo")) ?: return fallback
        val org = regInfo.getNestedObject(listOf("org"))
        val pos = regInfo.getNestedObject(listOf("pos"))
        
        return OfdServiceInfo(
            orgTitle = org?.getNestedString(listOf("title")) ?: fallback.orgTitle,
            orgAddress = org?.getNestedString(listOf("address"))
                ?: pos?.getNestedString(listOf("address"))
                ?: fallback.orgAddress,
            orgAddressKz = org?.getNestedString(listOf("addressKz"))
                ?: pos?.getNestedString(listOf("addressKz"))
                ?: fallback.orgAddressKz,
            orgInn = org?.getNestedString(listOf("inn")) ?: fallback.orgInn,
            orgOkved = org?.getNestedString(listOf("okved")) ?: fallback.orgOkved,
            geoLatitude = pos?.getNestedInt(listOf("latitude")) ?: fallback.geoLatitude,
            geoLongitude = pos?.getNestedInt(listOf("longitude")) ?: fallback.geoLongitude,
            geoSource = fallback.geoSource
        )
    }

    /**
     * Извлекает регистрационный номер из ответа ОФД.
     * Ищет в regInfo.kkm.fnsKkmId (основной путь) или в regInfo.pos (fallback для совместимости).
     */
    private fun extractRegistrationNumber(responseJson: JsonObject?): String? {
        val regInfo = responseJson?.getNestedObject(listOf("payload", "service", "regInfo")) ?: return null
        
        // Основной путь: regInfo.kkm.fnsKkmId
        val kkm = regInfo.getNestedObject(listOf("kkm"))
        val fnsKkmId = kkm?.getNestedString(listOf("fnsKkmId"))
        if (!fnsKkmId.isNullOrBlank()) {
            return fnsKkmId
        }
        
        // Fallback для совместимости: regInfo.pos
        val pos = regInfo.getNestedObject(listOf("pos"))
        return pos?.getNestedString(listOf("registrationNumber"))
            ?: pos?.getNestedString(listOf("regNumber"))
    }

    /**
     * Извлекает заводской номер из ответа ОФД.
     * Ищет в regInfo.kkm.serialNumber (основной путь) или в regInfo.pos (fallback для совместимости).
     */
    private fun extractFactoryNumber(responseJson: JsonObject?): String? {
        val regInfo = responseJson?.getNestedObject(listOf("payload", "service", "regInfo")) ?: return null
        
        // Основной путь: regInfo.kkm.serialNumber
        val kkm = regInfo.getNestedObject(listOf("kkm"))
        val serialNumber = kkm?.getNestedString(listOf("serialNumber"))
        if (!serialNumber.isNullOrBlank()) {
            return serialNumber
        }
        
        // Fallback для совместимости: regInfo.pos
        val pos = regInfo.getNestedObject(listOf("pos"))
        return pos?.getNestedString(listOf("factoryNumber"))
            ?: pos?.getNestedString(listOf("factoryNum"))
    }

    private fun extractZxReport(responseJson: JsonObject?): JsonObject? {
        val payload = responseJson?.getNestedObject(listOf("payload")) ?: return null
        val report = payload.getNestedObject(listOf("report"))
        val zxReport = report?.getNestedObject(listOf("zxReport"))
        if (zxReport != null) return zxReport
        val service = payload.getNestedObject(listOf("service")) ?: return null
        val lastZReport = service.getNestedObject(listOf("lastZReport"))
        return lastZReport?.getNestedObject(listOf("zxReport"))
            ?: lastZReport
            ?: service.getNestedObject(listOf("zxReport"))
    }
}
