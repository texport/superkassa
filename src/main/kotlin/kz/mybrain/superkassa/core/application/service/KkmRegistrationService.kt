package kz.mybrain.superkassa.core.application.service

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kz.mybrain.superkassa.core.application.error.ConflictException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ForbiddenException
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.model.DraftKkmRequest
import kz.mybrain.superkassa.core.application.model.DraftKkmResponse
import kz.mybrain.superkassa.core.application.model.KkmDraftUpdateRequest
import kz.mybrain.superkassa.core.application.model.KkmInitDirectRequest
import kz.mybrain.superkassa.core.application.model.KkmInitDraftRequest
import kz.mybrain.superkassa.core.application.model.KkmInitSimpleRequest
import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.application.policy.SystemTimeGuard
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmMode
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.CounterUpdaterPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.OfdConfigPort
import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.superkassa.core.domain.port.PinHasherPort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory

/**
 * Сервис регистрации ККМ (создание черновиков, инициализация).
 * Выделен из KkmService для соблюдения SRP и упрощения кода.
 */
class KkmRegistrationService(
    private val storage: StoragePort,
    private val ofd: OfdManagerPort,
    private val ofdConfig: OfdConfigPort,
    private val tokenCodec: kz.mybrain.superkassa.core.domain.port.TokenCodecPort,
    private val idGenerator: IdGenerator,
    private val clock: ClockPort,
    private val pinHasher: PinHasherPort,
    private val authorization: AuthorizationService,
    private val ofdCommandRequestBuilder: OfdCommandRequestBuilder,
    private val reqNumService: ReqNumService,
    private val counters: CounterUpdaterPort
) {
    private val logger = LoggerFactory.getLogger(KkmRegistrationService::class.java)
    private val draftMode = KkmMode.REGISTRATION.name
    private val draftState = KkmState.IDLE.name
    private val registeredMode = KkmMode.REGISTRATION.name
    private val registeredState = KkmState.ACTIVE.name
    private val systemCounterId = "_system"
    private val factoryCounterKey = "kkm.factory.seq"
    private val defaultAdminPin = "0000"
    private val defaultAdminName = "Администратор"
    private val defaultCashierName = "Кассир"

    /**
     * Инициализация черновика ККМ через команду COMMAND_SYSTEM и COMMAND_INFO.
     *
     * @param request Параметры для инициализации черновика.
     * @return Обновленная информация о ККМ.
     * @throws ValidationException Если параметры некорректны (например, пустой systemId).
     * @throws ConflictException Если ККМ уже существует или есть конфликт по systemId.
     */
    fun initDraftKkm(pin: String, request: KkmInitDraftRequest): KkmInfo {
        ensureSystemTimeValid()
        requireBootstrapAdminPin(pin)
        if (request.ofdSystemId.isBlank()) {
            throw ValidationException(ErrorMessages.kkmSystemIdRequired(), "KKM_SYSTEM_ID_REQUIRED")
        }
        val draft = authorization.requireKkm(request.kkmId)
        if (!isDraft(draft)) {
            throw ConflictException(ErrorMessages.kkmExists(), "KKM_EXISTS")
        }
        val existingBySystem = storage.findKkmBySystemId(request.ofdSystemId)
        if (existingBySystem != null && existingBySystem.id != draft.id) {
            throw ConflictException(
                ErrorMessages.kkmSystemIdExists(request.ofdSystemId),
                "KKM_SYSTEM_ID_EXISTS"
            )
        }
        val ofdTag =
            draft.ofdProvider
                ?: throw ValidationException(
                    ErrorMessages.ofdProviderRequired(),
                    "OFD_PROVIDER_REQUIRED"
                )
        val (providerId, environmentId) = ofdConfig.parseTag(ofdTag)
        ofdConfig.validateAndFormatTag(providerId, environmentId)
        val factoryNumber = draft.factoryNumber ?: generateFactoryNumber()
        val year = draft.manufactureYear ?: currentYear()
        val baseInfo =
            draft.copy(
                updatedAt = clock.now(),
                ofdProvider = ofdTag,
                factoryNumber = factoryNumber,
                manufactureYear = year,
                systemId = request.ofdSystemId,
                ofdServiceInfo = request.serviceInfo ?: defaultServiceInfo()
            )

        return performKkmInitialization(
            KkmInitializationParams(
                baseInfo = baseInfo,
                ofdToken = request.ofdToken,
                registrationNumber = request.kkmKgdId,
                factoryNumber = factoryNumber,
                ofdTag = ofdTag,
                updateKkm = { updatedKkm ->
                    val updated = storage.updateKkm(updatedKkm)
                    if (!updated) {
                        throw ConflictException(ErrorMessages.kkmExists(), "KKM_EXISTS")
                    }
                }
            )
        )
    }

    /**
     * Инициализация ККМ без черновика через COMMAND_SYSTEM и COMMAND_INFO.
     *
     * @param request Параметры прямой инициализации.
     * @return Созданная запись ККМ.
     * @throws ValidationException Если systemId пуст или невалидные параметры ОФД.
     * @throws ConflictException Если ККМ с таким регистрационным номером или systemId уже
     * существует.
     */
    fun initKkm(pin: String, request: KkmInitDirectRequest): KkmInfo {
        ensureSystemTimeValid()
        requireBootstrapAdminPin(pin)
        if (request.ofdSystemId.isBlank()) {
            throw ValidationException(ErrorMessages.kkmSystemIdRequired(), "KKM_SYSTEM_ID_REQUIRED")
        }
        val now = clock.now()
        val ofdTag = validateOfd(request.ofdId, request.ofdEnvironment)
        val registrationNumber = request.kkmKgdId
        val existingByReg = storage.findKkmByRegistrationNumber(registrationNumber)
        if (existingByReg != null) {
            throw ConflictException(ErrorMessages.kkmExists(), "KKM_EXISTS")
        }
        val existingBySystem = storage.findKkmBySystemId(request.ofdSystemId)
        if (existingBySystem != null) {
            throw ConflictException(
                ErrorMessages.kkmSystemIdExists(request.ofdSystemId),
                "KKM_SYSTEM_ID_EXISTS"
            )
        }

        val kkmId = idGenerator.nextId()
        val baseInfo =
            KkmInfo(
                id = kkmId,
                createdAt = now,
                updatedAt = now,
                mode = registeredMode,
                state = registeredState,
                ofdProvider = ofdTag,
                registrationNumber = registrationNumber,
                factoryNumber = request.factoryNumber,
                manufactureYear = request.manufactureYear,
                systemId = request.ofdSystemId,
                ofdServiceInfo = request.serviceInfo ?: defaultServiceInfo()
            )

        return performKkmInitialization(
            KkmInitializationParams(
                baseInfo = baseInfo,
                ofdToken = request.ofdToken,
                registrationNumber = registrationNumber,
                factoryNumber = request.factoryNumber,
                ofdTag = ofdTag,
                updateKkm = { updatedKkm ->
                    val created = storage.createKkm(updatedKkm)
                    if (!created) {
                        throw ConflictException(ErrorMessages.kkmExists(), "KKM_EXISTS")
                    }
                }
            )
        )
    }

    /**
     * Упрощенная инициализация ККМ без черновика.
     * Используется для ККМ, которые уже были зарегистрированы в ОФД ранее.
     * Все необходимые данные (регистрационный номер, заводской номер, сервисная информация)
     * получаются из ОФД автоматически через COMMAND_INFO.
     *
     * @param request Упрощенные параметры инициализации (только минимальные данные для подключения к ОФД).
     * @return Созданная запись ККМ с данными из ОФД.
     */
    fun initKkmSimple(pin: String, request: KkmInitSimpleRequest): KkmInfo {
        ensureSystemTimeValid()
        requireBootstrapAdminPin(pin)
        if (request.ofdSystemId.isBlank()) {
            throw ValidationException(ErrorMessages.kkmSystemIdRequired(), "KKM_SYSTEM_ID_REQUIRED")
        }
        val now = clock.now()
        val ofdTag = validateOfd(request.ofdId, request.ofdEnvironment)
        
        // Проверка на существование ККМ с таким systemId
        val existingBySystem = storage.findKkmBySystemId(request.ofdSystemId)
        if (existingBySystem != null) {
            throw ConflictException(
                ErrorMessages.kkmSystemIdExists(request.ofdSystemId),
                "KKM_SYSTEM_ID_EXISTS"
            )
        }

        val kkmId = idGenerator.nextId()
        val initialToken = tokenCodec.parseToken(request.ofdToken)
        
        // Создаем временную ККМ для отправки запросов к ОФД
        val tempKkm = KkmInfo(
            id = kkmId,
            createdAt = now,
            updatedAt = now,
            mode = registeredMode,
            state = registeredState,
            ofdProvider = ofdTag,
            systemId = request.ofdSystemId,
            ofdServiceInfo = defaultServiceInfo()
        )

        // Получаем данные из ОФД через последовательность COMMAND_SYSTEM + COMMAND_INFO
        // Для этих команд нужны registrationNumber и factoryNumber, но мы их получим из ответа
        // Используем временные значения для первого запроса
        val tempRegistrationNumber = "TEMP_REG_${kkmId.take(8)}"
        val tempFactoryNumber = "TEMP_FACTORY_${kkmId.take(8)}"
        val tempServiceInfo = defaultServiceInfo()
        
        // Сначала отправляем COMMAND_SYSTEM с временными данными
        val systemResult = sendOfdCommand(
            kkm = tempKkm,
            commandType = OfdCommandType.SYSTEM,
            payloadRef = idGenerator.nextId(),
            tokenOverride = initialToken,
            serviceInfoOverride = tempServiceInfo,
            registrationNumberOverride = tempRegistrationNumber,
            factoryNumberOverride = tempFactoryNumber,
            ofdProviderOverride = ofdTag
        )
        
        if (systemResult.status != OfdCommandStatus.OK) {
            throw ValidationException(
                ErrorMessages.ofdRequestFailed(systemResult.errorMessage),
                "OFD_COMMAND_FAILED"
            )
        }
        
        // Затем отправляем COMMAND_INFO с обновленным токеном
        val infoToken = systemResult.responseToken ?: initialToken
        val infoResult = sendOfdCommand(
            kkm = tempKkm,
            commandType = OfdCommandType.INFO,
            payloadRef = idGenerator.nextId(),
            tokenOverride = infoToken,
            serviceInfoOverride = tempServiceInfo,
            registrationNumberOverride = tempRegistrationNumber,
            factoryNumberOverride = tempFactoryNumber,
            ofdProviderOverride = ofdTag
        )
        
        if (infoResult.status != OfdCommandStatus.OK) {
            throw ValidationException(
                ErrorMessages.ofdRequestFailed(infoResult.errorMessage),
                "OFD_COMMAND_FAILED"
            )
        }

        // Извлекаем данные из ответа ОФД
        val resolvedServiceInfo = extractServiceInfo(infoResult.responseJson, defaultServiceInfo())
        
        // Извлекаем регистрационный номер и заводской номер из ответа ОФД
        // Если они не найдены в ответе, используем временные значения
        val registrationNumber = extractRegistrationNumber(infoResult.responseJson) ?: tempRegistrationNumber
        val factoryNumber = extractFactoryNumber(infoResult.responseJson) ?: tempFactoryNumber
        
        // Проверка на существование ККМ с таким регистрационным номером
        val existingByReg = storage.findKkmByRegistrationNumber(registrationNumber)
        if (existingByReg != null && existingByReg.id != kkmId) {
            throw ConflictException(ErrorMessages.kkmExists(), "KKM_EXISTS")
        }

        val now2 = clock.now()
        val shiftNo = extractShiftNumber(infoResult.responseJson)
        val finalToken = infoResult.responseToken ?: initialToken
        
        val finalKkm = KkmInfo(
            id = kkmId,
            createdAt = now,
            updatedAt = now2,
            mode = registeredMode,
            state = registeredState,
            ofdProvider = ofdTag,
            registrationNumber = registrationNumber,
            factoryNumber = factoryNumber,
            manufactureYear = currentYear(),
            systemId = request.ofdSystemId,
            ofdServiceInfo = resolvedServiceInfo,
            tokenEncryptedBase64 = tokenCodec.encodeToken(finalToken),
            tokenUpdatedAt = now2,
            lastShiftNo = shiftNo
        )

        storage.inTransaction {
            val created = storage.createKkm(finalKkm)
            if (!created) {
                throw ConflictException(ErrorMessages.kkmExists(), "KKM_EXISTS")
            }
            updateCountersFromOfdInfo(finalKkm.id, infoResult.responseJson)
            ensureDefaultUsers(finalKkm.id, clock.now())
        }
        
        return finalKkm
    }

    /**
     * Извлекает регистрационный номер из ответа ОФД.
     * Ищет в regInfo.kkm.fnsKkmId (основной путь) или в regInfo.pos (fallback для совместимости).
     */
    private fun extractRegistrationNumber(responseJson: JsonObject?): String? {
        val payload = responseJson?.get("payload")?.jsonObject ?: return null
        val service = payload["service"] as? JsonObject ?: return null
        val regInfo = service["regInfo"] as? JsonObject ?: return null
        
        // Основной путь: regInfo.kkm.fnsKkmId
        val kkm = regInfo["kkm"] as? JsonObject
        val fnsKkmId = kkm?.get("fnsKkmId")?.jsonPrimitive?.contentOrNull
        if (!fnsKkmId.isNullOrBlank()) {
            return fnsKkmId
        }
        
        // Fallback для совместимости: regInfo.pos
        val pos = regInfo["pos"] as? JsonObject
        return pos?.get("registrationNumber")?.jsonPrimitive?.contentOrNull
            ?: pos?.get("regNumber")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Извлекает заводской номер из ответа ОФД.
     * Ищет в regInfo.kkm.serialNumber (основной путь) или в regInfo.pos (fallback для совместимости).
     */
    private fun extractFactoryNumber(responseJson: JsonObject?): String? {
        val payload = responseJson?.get("payload")?.jsonObject ?: return null
        val service = payload["service"] as? JsonObject ?: return null
        val regInfo = service["regInfo"] as? JsonObject ?: return null
        
        // Основной путь: regInfo.kkm.serialNumber
        val kkm = regInfo["kkm"] as? JsonObject
        val serialNumber = kkm?.get("serialNumber")?.jsonPrimitive?.contentOrNull
        if (!serialNumber.isNullOrBlank()) {
            return serialNumber
        }
        
        // Fallback для совместимости: regInfo.pos
        val pos = regInfo["pos"] as? JsonObject
        return pos?.get("factoryNumber")?.jsonPrimitive?.contentOrNull
            ?: pos?.get("factoryNum")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Создает черновик ККМ: генерирует идентификатор, заводской номер и год выпуска. Черновик
     * сохраняется в storage как обычная запись ККМ.
     *
     * @param request Параметры создания черновика.
     * @return Ответ с данными черновика (id, заводской номер, год).
     */
    fun createDraftKkm(pin: String, request: DraftKkmRequest): DraftKkmResponse {
        ensureSystemTimeValid()
        requireBootstrapAdminPin(pin)
        val now = clock.now()
        val ofdTag = validateOfd(request.ofdId, request.ofdEnvironment)
        val kkmId = idGenerator.nextId()
        val factoryNumber = generateFactoryNumber()
        val year = currentYear()
        val info =
            KkmInfo(
                id = kkmId,
                createdAt = now,
                updatedAt = now,
                mode = draftMode,
                state = draftState,
                ofdProvider = ofdTag,
                factoryNumber = factoryNumber,
                manufactureYear = year
            )
        storage.inTransaction {
            storage.createKkm(info)
            ensureDefaultUsers(kkmId, now)
        }
        return DraftKkmResponse(
            kkmId = kkmId,
            factoryNumber = factoryNumber,
            manufactureYear = year
        )
    }

    /**
     * Обновляет данные черновика ККМ. Возможно только если ККМ находится в статусе IDLE (черновик).
     *
     * @param kkmId Идентификатор ККМ.
     * @param request Новые данные черновика.
     * @return Обновленная информация о ККМ.
     * @throws ValidationException Если ККМ не является черновиком или не в статусе IDLE.
     * @throws ConflictException Если есть конфликт по systemId.
     */
    fun updateDraftKkm(kkmId: String, pin: String, request: KkmDraftUpdateRequest): KkmInfo {
        authorization.requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        val existing = authorization.requireKkm(kkmId)
        if (!isDraft(existing)) {
            throw ValidationException(ErrorMessages.kkmNotDraft(), "KKM_NOT_DRAFT")
        }
        if (existing.state != KkmState.IDLE.name) {
            throw ValidationException(ErrorMessages.kkmDraftNotIdle(), "KKM_DRAFT_NOT_IDLE")
        }
        if (request.ofdId == null &&
            request.ofdEnvironment == null &&
            request.ofdSystemId == null &&
            request.factoryNumber == null &&
            request.manufactureYear == null
        ) {
            throw ValidationException(ErrorMessages.draftUpdateEmpty(), "DRAFT_UPDATE_EMPTY")
        }
        if ((request.ofdId == null) != (request.ofdEnvironment == null)) {
            throw ValidationException(ErrorMessages.ofdPairRequired(), "OFD_PAIR_REQUIRED")
        }
        if (request.ofdSystemId != null && request.ofdSystemId.isBlank()) {
            throw ValidationException(ErrorMessages.kkmSystemIdRequired(), "KKM_SYSTEM_ID_REQUIRED")
        }
        if (request.ofdSystemId != null) {
            val existingBySystem = storage.findKkmBySystemId(request.ofdSystemId)
            if (existingBySystem != null && existingBySystem.id != existing.id) {
                throw ConflictException(
                    ErrorMessages.kkmSystemIdExists(request.ofdSystemId),
                    "KKM_SYSTEM_ID_EXISTS"
                )
            }
        }
        val ofdTag =
            if (request.ofdId != null && request.ofdEnvironment != null) {
                validateOfd(request.ofdId, request.ofdEnvironment)
            } else {
                existing.ofdProvider
            }
        val updated =
            existing.copy(
                updatedAt = clock.now(),
                ofdProvider = ofdTag,
                systemId = request.ofdSystemId ?: existing.systemId,
                factoryNumber = request.factoryNumber ?: existing.factoryNumber,
                manufactureYear = request.manufactureYear ?: existing.manufactureYear
            )
        storage.updateKkm(updated)
        return updated
    }

    /**
     * Параметры для инициализации ККМ.
     * Используется для упрощения сигнатуры метода performKkmInitialization.
     */
    private data class KkmInitializationParams(
        val baseInfo: KkmInfo,
        val ofdToken: String,
        val registrationNumber: String,
        val factoryNumber: String?,
        val ofdTag: String,
        val updateKkm: (KkmInfo) -> Unit
    )

    /**
     * Общая логика инициализации ККМ через ОФД.
     * Вынесена для устранения дублирования между initDraftKkm и initKkm.
     */
    private fun performKkmInitialization(params: KkmInitializationParams): KkmInfo {
        val serviceInfo = params.baseInfo.ofdServiceInfo ?: defaultServiceInfo()
        val factoryNum = params.factoryNumber ?: params.baseInfo.factoryNumber
            ?: throw ValidationException(ErrorMessages.kkmFactoryRequired(), "KKM_FACTORY_REQUIRED")
        val infoResult = performOfdSystemAndInfo(
            baseInfo = params.baseInfo,
            initialToken = tokenCodec.parseToken(params.ofdToken),
            serviceInfo = serviceInfo,
            registrationNumber = params.registrationNumber,
            factoryNumber = factoryNum,
            ofdTag = params.ofdTag
        ) ?: return params.baseInfo

        val now = clock.now()
        val resolvedServiceInfo = extractServiceInfo(infoResult.responseJson, serviceInfo)
        val updatedKkm = applyOfdInitialization(
            baseInfo = params.baseInfo,
            registrationNumber = params.registrationNumber,
            serviceInfo = resolvedServiceInfo,
            ofdTag = params.ofdTag,
            token = infoResult.responseToken ?: tokenCodec.parseToken(params.ofdToken),
            responseJson = infoResult.responseJson,
            updatedAt = now
        )

        storage.inTransaction {
            params.updateKkm(updatedKkm)
            updateCountersFromOfdInfo(updatedKkm.id, infoResult.responseJson)
            ensureDefaultUsers(updatedKkm.id, clock.now())
        }
        return updatedKkm
    }

    /**
     * Общая последовательность COMMAND_SYSTEM + COMMAND_INFO для инициализации ККМ.
     * Возвращает infoResult при успехе, null при раннем выходе.
     */
    private fun performOfdSystemAndInfo(
        baseInfo: KkmInfo,
        initialToken: Long,
        serviceInfo: OfdServiceInfo,
        registrationNumber: String,
        factoryNumber: String,
        ofdTag: String
    ): OfdCommandResult? {
        val systemResult = sendOfdCommand(
            kkm = baseInfo,
            commandType = OfdCommandType.SYSTEM,
            payloadRef = idGenerator.nextId(),
            tokenOverride = initialToken,
            serviceInfoOverride = serviceInfo,
            registrationNumberOverride = registrationNumber,
            factoryNumberOverride = factoryNumber,
            ofdProviderOverride = ofdTag
        )
        if (systemResult.status != OfdCommandStatus.OK) return null
        val infoResult = sendOfdCommand(
            kkm = baseInfo,
            commandType = OfdCommandType.INFO,
            payloadRef = idGenerator.nextId(),
            tokenOverride = systemResult.responseToken ?: initialToken,
            serviceInfoOverride = serviceInfo,
            registrationNumberOverride = registrationNumber,
            factoryNumberOverride = factoryNumber,
            ofdProviderOverride = ofdTag
        )
        if (infoResult.status != OfdCommandStatus.OK) {
            infoResult.responseToken?.let {
                storage.updateKkmToken(baseInfo.id, tokenCodec.encodeToken(it), clock.now())
            }
            return null
        }
        return infoResult
    }

    private fun sendOfdCommand(
        kkm: KkmInfo,
        commandType: OfdCommandType,
        payloadRef: String,
        tokenOverride: Long? = null,
        serviceInfoOverride: OfdServiceInfo? = null,
        registrationNumberOverride: String? = null,
        factoryNumberOverride: String? = null,
        ofdProviderOverride: String? = null,
        updateToken: Boolean = true
    ): OfdCommandResult {
        val token = tokenOverride
            ?: tokenCodec.decodeToken(kkm.tokenEncryptedBase64)
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
            serviceInfoOverride = serviceInfoOverride,
            registrationNumberOverride = registrationNumberOverride,
            factoryNumberOverride = factoryNumberOverride,
            ofdProviderOverride = ofdProviderOverride,
            defaultServiceInfo = ::defaultServiceInfo
        )
        val result = ofd.send(request)
        if (updateToken && result.responseToken != null) {
            storage.updateKkmToken(kkm.id, tokenCodec.encodeToken(result.responseToken), now)
        }
        return result
    }

    private fun applyOfdInitialization(
        baseInfo: KkmInfo,
        registrationNumber: String,
        serviceInfo: OfdServiceInfo,
        ofdTag: String,
        token: Long,
        responseJson: JsonObject?,
        updatedAt: Long
    ): KkmInfo {
        val shiftNo = extractShiftNumber(responseJson)
        return baseInfo.copy(
            updatedAt = updatedAt,
            mode = registeredMode,
            state = registeredState,
            ofdProvider = ofdTag,
            registrationNumber = registrationNumber,
            ofdServiceInfo = serviceInfo,
            tokenEncryptedBase64 = tokenCodec.encodeToken(token),
            tokenUpdatedAt = updatedAt,
            lastShiftNo = shiftNo ?: baseInfo.lastShiftNo
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
        return zxReport["shiftNumber"]?.jsonPrimitive?.intOrNull
    }

    private fun extractServiceInfo(
        responseJson: JsonObject?,
        fallback: OfdServiceInfo
    ): OfdServiceInfo {
        val payload = responseJson?.get("payload")?.jsonObject ?: return fallback
        val service = payload["service"] as? JsonObject ?: return fallback
        val regInfo = service["regInfo"] as? JsonObject ?: return fallback
        val org = regInfo["org"] as? JsonObject
        val pos = regInfo["pos"] as? JsonObject
        return OfdServiceInfo(
            orgTitle = org?.get("title")?.jsonPrimitive?.contentOrNull ?: fallback.orgTitle,
            orgAddress = org?.get("address")?.jsonPrimitive?.contentOrNull
                ?: pos?.get("address")?.jsonPrimitive?.contentOrNull
                ?: fallback.orgAddress,
            orgAddressKz = org?.get("addressKz")?.jsonPrimitive?.contentOrNull
                ?: pos?.get("addressKz")?.jsonPrimitive?.contentOrNull
                ?: fallback.orgAddressKz,
            orgInn = org?.get("inn")?.jsonPrimitive?.contentOrNull ?: fallback.orgInn,
            orgOkved = org?.get("okved")?.jsonPrimitive?.contentOrNull ?: fallback.orgOkved,
            geoLatitude = pos?.get("latitude")?.jsonPrimitive?.intOrNull
                ?: fallback.geoLatitude,
            geoLongitude = pos?.get("longitude")?.jsonPrimitive?.intOrNull
                ?: fallback.geoLongitude,
            geoSource = fallback.geoSource
        )
    }

    private fun extractZxReport(responseJson: JsonObject?): JsonObject? {
        val payload = responseJson?.get("payload")?.jsonObject ?: return null
        val report = payload["report"] as? JsonObject
        val zxReport = report?.get("zxReport") as? JsonObject
        if (zxReport != null) return zxReport
        val service = payload["service"] as? JsonObject ?: return null
        val lastZReport = service["lastZReport"] as? JsonObject
        return (lastZReport?.get("zxReport") as? JsonObject)
            ?: lastZReport ?: service["zxReport"] as? JsonObject
    }

    private fun isDraft(kkm: KkmInfo): Boolean {
        return kkm.registrationNumber.isNullOrBlank()
    }

    private fun requireBootstrapAdminPin(pin: String) {
        if (pin != defaultAdminPin) {
            throw ForbiddenException(ErrorMessages.userForbidden(), "USER_FORBIDDEN")
        }
    }

    private fun ensureDefaultUsers(kkmId: String, now: Long) {
        val existing = storage.listUsers(kkmId)
        if (existing.isNotEmpty()) return
        storage.createUser(
            kkmId = kkmId,
            userId = idGenerator.nextId(),
            name = defaultAdminName,
            role = UserRole.ADMIN,
            pin = defaultAdminPin,
            pinHash = pinHasher.hash(defaultAdminPin),
            createdAt = now
        )
        storage.createUser(
            kkmId = kkmId,
            userId = idGenerator.nextId(),
            name = defaultCashierName,
            role = UserRole.CASHIER,
            pin = "1111",
            pinHash = pinHasher.hash("1111"),
            createdAt = now
        )
    }

    private fun validateOfd(providerId: String, environmentId: String): String =
        ofdConfig.validateAndFormatTag(providerId, environmentId)

    private fun generateFactoryNumber(): String {
        val next = nextSystemCounter(factoryCounterKey)
        val year = currentYear()
        return "SN$year${next.toString().padStart(6, '0')}"
    }

    private fun nextSystemCounter(key: String): Long {
        val current = storage.loadCounters(systemCounterId, CounterScopes.GLOBAL, null)[key] ?: 0L
        val next = current + 1L
        storage.upsertCounter(systemCounterId, CounterScopes.GLOBAL, null, key, next)
        return next
    }

    private fun currentYear(): Int {
        val now = clock.now()
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC).year
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

    private fun ensureSystemTimeValid() {
        val result = SystemTimeGuard.validate(clock)
        if (!result.ok) {
            throw ValidationException(ErrorMessages.systemTimeInvalid(), "SYSTEM_TIME_INVALID")
        }
    }
}
