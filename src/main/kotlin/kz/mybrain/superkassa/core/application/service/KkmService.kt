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
import kz.mybrain.superkassa.core.application.error.NotFoundException
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.model.DraftKkmRequest
import kz.mybrain.superkassa.core.application.model.DraftKkmResponse
import kz.mybrain.superkassa.core.application.model.KkmDraftUpdateRequest
import kz.mybrain.superkassa.core.application.model.KkmInitDirectRequest
import kz.mybrain.superkassa.core.application.model.KkmInitDraftRequest
import kz.mybrain.superkassa.core.application.model.KkmListParams
import kz.mybrain.superkassa.core.application.model.KkmListResult
import kz.mybrain.superkassa.core.application.model.OfdAuthInfoResponse
import kz.mybrain.superkassa.core.application.model.PrintDocumentType
import kz.mybrain.superkassa.core.application.model.UserCreateRequest
import kz.mybrain.superkassa.core.application.model.UserResponse
import kz.mybrain.superkassa.core.application.model.UserUpdateRequest
import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.application.policy.SystemTimeGuard
import kz.mybrain.superkassa.core.domain.model.CashOperationRequest
import kz.mybrain.superkassa.core.domain.model.CashOperationResult
import kz.mybrain.superkassa.core.domain.model.CashOperationType
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmMode
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.model.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.QueueCommandRequest
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ReceiptResult
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.ReportRequest
import kz.mybrain.superkassa.core.domain.model.ReportResult
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.core.domain.model.TaxRegime
import kz.mybrain.superkassa.core.domain.model.VatGroup
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.data.receipt.ReportPrintRenderer
import kz.mybrain.superkassa.core.data.ofd.OfdResponseUtils
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.CounterUpdaterPort
import kz.mybrain.superkassa.core.domain.port.DeliveryPort
import kz.mybrain.superkassa.core.domain.port.DocumentConvertPort
import kz.mybrain.superkassa.core.domain.port.IdGenerator
import kz.mybrain.superkassa.core.domain.port.OfdConfigPort
import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.superkassa.core.domain.port.PinHasherPort
import kz.mybrain.superkassa.core.domain.port.QueuePort
import kz.mybrain.superkassa.core.domain.port.ReceiptRenderPort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory

/** Основной сервис ККМ: чеки, смены, отчеты, настройки. */
class KkmService(
        private val storage: StoragePort,
        private val queue: QueuePort,
        private val ofd: OfdManagerPort,
        private val ofdConfig: OfdConfigPort,
        private val delivery: DeliveryPort,
        private val kkmUserService: KkmUserService,
        private val shiftService: ShiftService,
        private val ofdSyncService: OfdSyncService,
        private val kkmRegistrationService: KkmRegistrationService,
        private val tokenCodec: kz.mybrain.superkassa.core.domain.port.TokenCodecPort,
        private val autonomousModeService: AutonomousModeService,
        private val fiscalOperationExecutor: FiscalOperationExecutor,
        private val reqNumService: ReqNumService,
        private val counters: CounterUpdaterPort,
        private val idGenerator: IdGenerator,
        private val clock: ClockPort,
        private val pinHasher: PinHasherPort,
        private val authorization: AuthorizationService,
        private val ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        private val coreSettings: CoreSettings,
        private val receiptRenderPort: ReceiptRenderPort,
        private val documentConvertPort: DocumentConvertPort
) {
    private val logger = LoggerFactory.getLogger(KkmService::class.java)
    private val draftMode = KkmMode.REGISTRATION.name
    private val draftState = KkmState.IDLE.name
    private val registeredMode = KkmMode.REGISTRATION.name
    private val registeredState = KkmState.ACTIVE.name

    /** Делегирование методов регистрации ККМ в KkmRegistrationService */
    fun initDraftKkm(pin: String, request: KkmInitDraftRequest): KkmInfo =
        kkmRegistrationService.initDraftKkm(pin, request)

    fun initKkm(pin: String, request: KkmInitDirectRequest): KkmInfo =
        kkmRegistrationService.initKkm(pin, request)

    fun initKkmSimple(
        pin: String,
        request: kz.mybrain.superkassa.core.application.model.KkmInitSimpleRequest
    ): KkmInfo =
        kkmRegistrationService.initKkmSimple(pin, request)

    fun createDraftKkm(pin: String, request: DraftKkmRequest): DraftKkmResponse =
        kkmRegistrationService.createDraftKkm(pin, request)

    /**
     * Возвращает ККМ по идентификатору.
     *
     * @param id Идентификатор ККМ.
     * @return Инфо о ККМ.
     * @throws NotFoundException Если ККМ не найдена.
     */
    fun getKkm(id: String): KkmInfo =
            storage.findKkm(id)
                    ?: throw NotFoundException(
                            message = ErrorMessages.kkmNotFound(),
                            code = "KKM_NOT_FOUND"
                    )

    /**
     * Возвращает список ККМ постранично с фильтрацией и сортировкой.
     *
     * @param params Параметры фильтрации и пагинации.
     * @return Результат поиска со списком и общим количеством.
     */
    fun listKkms(params: KkmListParams): KkmListResult {
        val items =
                storage.listKkms(
                        limit = params.limit,
                        offset = params.offset,
                        state = params.state,
                        search = params.search,
                        sortBy = params.sortBy,
                        sortOrder = params.sortOrder
                )
        val total = storage.countKkms(state = params.state, search = params.search)
        return KkmListResult(items = items, total = total)
    }

    /**
     * Удаляет ККМ по id. Требует режим программирования (PROGRAMMING) и закрытую смену.
     *
     * @param id Идентификатор ККМ.
     * @param pin ПИН-код администратора.
     * @return true если удаление прошло успешно.
     * @throws ValidationException Если ККМ не в режиме программирования.
     * @throws ConflictException Если открыта смена или есть задачи в очереди.
     * @throws NotFoundException Если ККМ не найдена.
     * @throws ForbiddenException Если ПИН-код не подходит.
     */
    fun deleteKkm(id: String, pin: String): Boolean {
        requireRole(id, pin, setOf(UserRole.ADMIN))
        val kkm = requireKkm(id)
        if (kkm.state != KkmState.PROGRAMMING.name) {
            throw ValidationException(
                    ErrorMessages.kkmDeleteRequiresProgramming(),
                    "KKM_DELETE_REQUIRES_PROGRAMMING"
            )
        }
        val openShift = storage.findOpenShift(id)
        if (openShift != null) {
            throw ConflictException(ErrorMessages.kkmDeleteShiftOpen(), "KKM_DELETE_SHIFT_OPEN")
        }
        if (queue.hasQueuedCommands(id) || storage.hasOfflineQueue(id)) {
            throw ConflictException(
                    ErrorMessages.kkmDeleteQueueNotEmpty(),
                    "KKM_DELETE_QUEUE_NOT_EMPTY"
            )
        }
        val deleted = storage.deleteKkmCompletely(id)
        if (!deleted) {
            throw NotFoundException(message = ErrorMessages.kkmNotFound(), code = "KKM_NOT_FOUND")
        }
        queue.deleteQueuedCommands(id)
        return true
    }

    fun listCounters(kkmId: String, pin: String): List<CounterSnapshot> {
        requireKkm(kkmId)
        requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        return storage.listCounters(kkmId)
    }

    /**
     * Возвращает чек по документу в формате HTML.
     * Документ должен принадлежать указанной ККМ и содержать сохранённое тело чека.
     */
    fun getReceiptHtml(kkmId: String, documentId: String, pin: String): String {
        requireKkm(kkmId)
        requireRole(kkmId, pin, setOf(UserRole.CASHIER, UserRole.ADMIN))
        val (snapshot, receipt) = storage.findFiscalDocumentWithReceiptPayload(documentId)
            ?: throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        if (snapshot.cashboxId != kkmId) {
            throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        }
        return receiptRenderPort.renderHtml(receipt, snapshot)
    }

    /**
     * Универсальная печатная форма (HTML) по типу документа.
     * DOCUMENT — documentId обязателен (чек, внесение, изъятие).
     * X_REPORT, OPEN_SHIFT — только kkmId.
     * CLOSE_SHIFT — shiftId обязателен.
     */
    fun getPrintHtml(
        kkmId: String,
        type: PrintDocumentType,
        documentId: String?,
        shiftId: String?,
        pin: String
    ): String {
        requireKkm(kkmId)
        requireRole(kkmId, pin, setOf(UserRole.CASHIER, UserRole.ADMIN))
        return when (type) {
            PrintDocumentType.DOCUMENT -> {
                val id = documentId ?: throw ValidationException(ErrorMessages.badRequest(), "DOCUMENT_ID_REQUIRED")
                getDocumentPrintHtmlInternal(kkmId, id, pin)
            }
            PrintDocumentType.X_REPORT -> {
                val shift = getOpenShift(kkmId, pin)
                val counters = storage.loadCounters(kkmId, CounterScopes.SHIFT, shift.id)
                ReportPrintRenderer.renderXReportHtml(shift, counters)
            }
            PrintDocumentType.OPEN_SHIFT -> {
                val shift = getOpenShift(kkmId, pin)
                ReportPrintRenderer.renderOpenShiftHtml(shift)
            }
            PrintDocumentType.CLOSE_SHIFT -> {
                val sid = shiftId ?: throw ValidationException(ErrorMessages.badRequest(), "SHIFT_ID_REQUIRED")
                val shift =
                    storage.findShiftById(sid)
                        ?: throw NotFoundException(ErrorMessages.documentNotFound(), "SHIFT_NOT_FOUND")
                if (shift.kkmId != kkmId) throw NotFoundException(ErrorMessages.documentNotFound(), "SHIFT_NOT_FOUND")
                val counters = storage.loadCounters(kkmId, CounterScopes.SHIFT, sid)
                ReportPrintRenderer.renderCloseShiftHtml(shift, counters)
            }
        }
    }

    /** Универсальная печатная форма в PDF. */
    fun getPrintPdf(
        kkmId: String,
        type: PrintDocumentType,
        documentId: String?,
        shiftId: String?,
        pin: String
    ): ByteArray {
        val html = getPrintHtml(kkmId, type, documentId, shiftId, pin)
        return documentConvertPort.htmlToPdf(html)
    }

    private fun getDocumentPrintHtmlInternal(kkmId: String, documentId: String, pin: String): String {
        val doc =
            storage.findFiscalDocumentById(documentId)
                ?: throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        if (doc.cashboxId != kkmId) throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        return when (doc.docType) {
            "CHECK" -> getReceiptHtml(kkmId, documentId, pin)
            "CASH_IN", "CASH_OUT" -> ReportPrintRenderer.renderCashOperationHtml(doc)
            else -> throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        }
    }

    /**
     * Повторная попытка отправки чека по всем настроенным каналам доставки (печать, email, telegram, whatsapp и т.д.).
     * Отправляется документ (PDF/IMAGE/HTML); каналы с типом LINK пропускаются (нет сохранённого URL ОФД).
     */
    fun retryReceiptDelivery(kkmId: String, documentId: String, pin: String): List<Pair<String, Boolean>> {
        requireKkm(kkmId)
        requireRole(kkmId, pin, setOf(UserRole.CASHIER, UserRole.ADMIN))
        val (snapshot, receipt) =
            storage.findFiscalDocumentWithReceiptPayload(documentId)
                ?: throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        if (snapshot.cashboxId != kkmId) {
            throw NotFoundException(ErrorMessages.documentNotFound(), "DOCUMENT_NOT_FOUND")
        }
        val html = receiptRenderPort.renderHtml(receipt, snapshot)
        val del = coreSettings.delivery ?: return emptyList()
        val results = mutableListOf<Pair<String, Boolean>>()

        del.print?.takeIf { it.enabled }?.connection?.let { conn ->
            if (conn.host != null && conn.port != null) {
                val paperWidth = when (del.print?.paperWidthMm ?: 58) {
                    48 -> 48
                    80 -> 80
                    else -> 58
                }
                val escPos = documentConvertPort.htmlToEscPos(html, paperWidth)
                val req = kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                    kkmId = kkmId,
                    documentId = documentId,
                    channel = "PRINT",
                    payloadType = "ESC_POS",
                    payloadBytes = escPos
                )
                val ok = delivery.deliver(req)
                results.add("PRINT" to ok)
            }
        }

        del.channels.filter { it.enabled }.forEach { ch ->
            val dest = ch.destination ?: run {
                results.add(ch.channel to false)
                return@forEach
            }
            when (ch.payloadType.uppercase()) {
                "LINK" -> results.add(ch.channel to false) // URL не сохраняем, пропускаем
                "DOCUMENT", "BOTH" -> {
                    val fmt = ch.documentFormat.uppercase()
                    val bytes = when (fmt) {
                        "PDF" -> documentConvertPort.htmlToPdf(html)
                        "IMAGE" -> documentConvertPort.htmlToImage(html)
                        else -> html.toByteArray(Charsets.UTF_8)
                    }
                    val req = kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                        kkmId = kkmId,
                        documentId = documentId,
                        channel = ch.channel,
                        destination = dest,
                        payloadType = fmt,
                        payloadBytes = bytes
                    )
                    val ok = delivery.deliver(req)
                    results.add(ch.channel to ok)
                }
                else -> results.add(ch.channel to false)
            }
        }
        return results
    }

    /**
     * Обновляет настройки ККМ. Требует режим программирования (PROGRAMMING).
     *
     * @param kkmId Идентификатор ККМ.
     * @param request Новые настройки.
     * @return Обновленная информация о ККМ.
     * @throws ValidationException Если ККМ не в режиме программирования.
     */
    fun updateKkmSettings(kkmId: String, pin: String, autoCloseShift: Boolean): KkmInfo {
        ensureSystemTimeValid()
        requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        val kkm = requireKkm(kkmId)
        if (kkm.state != KkmState.PROGRAMMING.name) {
            throw ValidationException(
                    ErrorMessages.kkmSettingsRequiresProgramming(),
                    "KKM_SETTINGS_REQUIRES_PROGRAMMING"
            )
        }
        val updated = kkm.copy(updatedAt = clock.now(), autoCloseShift = autoCloseShift)
        storage.updateKkm(updated)
        return updated
    }

    /**
     * Обновляет налоговые настройки ККМ (налоговый режим и базовую группу НДС).
     *
     * Разрешено только:
     * - в режиме программирования (PROGRAMMING),
     * - при отсутствии открытой смены,
     * - при пустых online/offline очередях.
     */
    fun updateTaxSettings(
        kkmId: String,
        pin: String,
        taxRegime: TaxRegime,
        defaultVatGroup: VatGroup
    ): KkmInfo {
        ensureSystemTimeValid()
        requireRole(kkmId, pin, setOf(UserRole.ADMIN))
        val kkm = requireKkm(kkmId)
        if (kkm.mode != KkmMode.PROGRAMMING.name || kkm.state != KkmState.PROGRAMMING.name) {
            throw ValidationException(
                ErrorMessages.kkmSettingsRequiresProgramming(),
                "KKM_TAX_SETTINGS_REQUIRES_PROGRAMMING"
            )
        }
        val openShift = storage.findOpenShift(kkmId)
        if (openShift != null) {
            throw ConflictException(
                ErrorMessages.kkmDeleteShiftOpen(),
                "KKM_TAX_SETTINGS_SHIFT_OPEN"
            )
        }
        if (queue.hasQueuedCommands(kkmId) || storage.hasOfflineQueue(kkmId)) {
            throw ConflictException(
                ErrorMessages.kkmDeleteQueueNotEmpty(),
                "KKM_TAX_SETTINGS_QUEUE_NOT_EMPTY"
            )
        }

        val updated =
            kkm.copy(
                updatedAt = clock.now(),
                taxRegime = taxRegime,
                defaultVatGroup = defaultVatGroup
            )
        storage.updateKkm(updated)
        return updated
    }

    fun updateDraftKkm(kkmId: String, pin: String, request: KkmDraftUpdateRequest): KkmInfo =
        kkmRegistrationService.updateDraftKkm(kkmId, pin, request)

    fun enterProgramming(kkmId: String, pin: String): KkmInfo {
        return storage.inTransaction {
            val kkm = requireKkm(kkmId)
            requireRole(kkm.id, pin, setOf(UserRole.ADMIN))
            val updated =
                    kkm.copy(
                            updatedAt = clock.now(),
                            mode = KkmMode.PROGRAMMING.name,
                            state = KkmState.PROGRAMMING.name
                    )
            storage.updateKkm(updated)
            updated
        }
    }

    fun exitProgramming(kkmId: String, pin: String): KkmInfo {
        return storage.inTransaction {
            val kkm = requireKkm(kkmId)
            requireRole(kkm.id, pin, setOf(UserRole.ADMIN))
            val isDraft = kkm.registrationNumber.isNullOrBlank()
            val restoredMode = if (isDraft) draftMode else registeredMode
            val restoredState = if (isDraft) draftState else registeredState
            val updated =
                    kkm.copy(updatedAt = clock.now(), mode = restoredMode, state = restoredState)
            storage.updateKkm(updated)
            updated
        }
    }

    fun listUsers(kkmId: String, pin: String): List<UserResponse> = kkmUserService.listUsers(kkmId, pin)

    fun createUser(kkmId: String, pin: String, request: UserCreateRequest): UserResponse =
        kkmUserService.createUser(kkmId, pin, request)

    fun updateUser(
        kkmId: String,
        userId: String,
        pin: String,
        request: UserUpdateRequest
    ): UserResponse =
        kkmUserService.updateUser(kkmId, userId, pin, request)

    fun deleteUser(kkmId: String, userId: String, pin: String): Boolean =
        kkmUserService.deleteUser(kkmId, userId, pin)

    fun getOfdAuthInfo(kkmId: String, pin: String): OfdAuthInfoResponse =
        ofdSyncService.getOfdAuthInfo(kkmId, pin)

    fun updateOfdToken(kkmId: String, pin: String, token: String): Boolean =
        ofdSyncService.updateOfdToken(kkmId, pin, token)

    /**
     * Создает чек, обновляет счетчики и отправляет запрос в ОФД. Использует storage для
     * идемпотентности, сохранения документа и статуса доставки.
     *
     * @param request Данные чека.
     * @return Результат создания чека.
     * @throws ConflictException Если смена не открыта.
     * @throws ValidationException Если данные чека невалидны.
     */
    fun createReceipt(request: ReceiptRequest): ReceiptResult {
        val kkm = requireKkm(request.kkmId)
        val requestWithTaxSettings =
            request.copy(
                taxRegime = kkm.taxRegime,
                defaultVatGroup = request.defaultVatGroup ?: kkm.defaultVatGroup
            )
        return fiscalOperationExecutor.executeIdempotentFiscalOperation(
            kkmId = requestWithTaxSettings.kkmId,
            pin = requestWithTaxSettings.pin,
            idempotencyKey = requestWithTaxSettings.idempotencyKey,
            operationType = "CREATE_RECEIPT",
            checkShift = {
                val shift = storage.findOpenShift(requestWithTaxSettings.kkmId)
                    ?: throw ConflictException(ErrorMessages.shiftNotOpen(), "SHIFT_NOT_OPEN")
                shift.id
            },
            saveOperation = { documentId, now, shiftId ->
                storage.saveReceipt(requestWithTaxSettings, documentId, shiftId, now)
            },
            sendOfdCommand = { currentKkm, documentId ->
                if (queue.hasQueuedCommands(requestWithTaxSettings.kkmId) || storage.hasOfflineQueue(requestWithTaxSettings.kkmId)) {
                    ofdResultQueuedOffline()
                } else {
                    sendOfdCommand(
                        kkm = currentKkm,
                        commandType = OfdCommandType.TICKET,
                        payloadRef = documentId
                    )
                }
            },
            processResult = { currentKkm, documentId, currentKkmId, ofdResult, commandType, now, receiptContext ->
                processOfdDocumentResult(
                    kkm = currentKkm,
                    documentId = documentId,
                    kkmId = currentKkmId,
                    ofdResult = ofdResult,
                    commandType = commandType,
                    now = now,
                    receiptContext = receiptContext
                )
                if (commandType == OfdCommandType.TICKET && receiptContext != null && ofdResult.resultCode == 0) {
                    val (receipt, _) = receiptContext
                    val doc = storage.findFiscalDocumentById(documentId)
                    if (doc != null) {
                    val html = receiptRenderPort.renderHtml(receipt, doc)
                    val receiptUrl = OfdResponseUtils.extractReceiptUrl(ofdResult.responseJson)
                    val del = coreSettings.delivery
                    if (del != null) {
                        del.print?.takeIf { it.enabled }?.connection?.let { conn ->
                            conn.host?.let { conn.port?.let {
                                val paperWidth = when (del.print?.paperWidthMm ?: 58) {
                                    48 -> 48
                                    80 -> 80
                                    else -> 58
                                }
                                val escPos = documentConvertPort.htmlToEscPos(html, paperWidth)
                                delivery.deliver(
                                    kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                                        kkmId = request.kkmId,
                                        documentId = documentId,
                                        channel = "PRINT",
                                        payloadType = "ESC_POS",
                                        payloadBytes = escPos
                                    )
                                )
                            }}
                        }
                        del.channels.filter { it.enabled }.forEach { ch ->
                            val dest = ch.destination
                            when (ch.payloadType.uppercase()) {
                                "LINK" -> receiptUrl?.let { url ->
                                    if (dest != null) delivery.deliver(
                                        kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                                            kkmId = request.kkmId,
                                            documentId = documentId,
                                            channel = ch.channel,
                                            destination = dest,
                                            payloadType = "LINK",
                                            payloadUrl = url
                                        )
                                    )
                                }
                                "DOCUMENT" -> if (dest != null) {
                                    val fmt = ch.documentFormat.uppercase()
                                    val bytes = when (fmt) {
                                        "PDF" -> documentConvertPort.htmlToPdf(html)
                                        "IMAGE" -> documentConvertPort.htmlToImage(html)
                                        else -> html.toByteArray(Charsets.UTF_8)
                                    }
                                    delivery.deliver(
                                        kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                                            kkmId = request.kkmId,
                                            documentId = documentId,
                                            channel = ch.channel,
                                            destination = dest,
                                            payloadType = fmt,
                                            payloadBytes = bytes
                                        )
                                    )
                                }
                                "BOTH" -> receiptUrl?.let { url ->
                                    if (dest != null) {
                                        delivery.deliver(
                                            kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                                                kkmId = request.kkmId,
                                                documentId = documentId,
                                                channel = ch.channel,
                                                destination = dest,
                                                payloadType = "LINK",
                                                payloadUrl = url
                                            )
                                        )
                                        val fmt = ch.documentFormat.uppercase()
                                        val bytes = when (fmt) {
                                            "PDF" -> documentConvertPort.htmlToPdf(html)
                                            "IMAGE" -> documentConvertPort.htmlToImage(html)
                                            else -> html.toByteArray(Charsets.UTF_8)
                                        }
                                        delivery.deliver(
                                            kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                                                kkmId = request.kkmId,
                                                documentId = documentId,
                                                channel = ch.channel,
                                                destination = dest,
                                                payloadType = fmt,
                                                payloadBytes = bytes
                                            )
                                        )
                                    }
                                }
                                else -> { }
                            }
                        }
                    } else if (ofdResult.responseBin != null) {
                        delivery.deliver(
                            kz.mybrain.superkassa.core.domain.model.DeliveryRequest(
                                kkmId = request.kkmId,
                                documentId = documentId,
                                channel = "PRINT",
                                payloadType = "BINARY",
                                payloadBytes = ofdResult.responseBin
                            )
                        )
                    }
                }
                }
            },
            buildResult = { documentId, ofdResult ->
                val doc = storage.findFiscalDocumentById(documentId)
                ReceiptResult(
                    documentId = documentId,
                    fiscalSign = doc?.fiscalSign ?: ofdResult.fiscalSign,
                    autonomousSign = doc?.autonomousSign ?: ofdResult.autonomousSign,
                    deliveryPayload = ofdResult.responseBin
                )
            },
            receiptContextProvider = { shiftId -> Pair(requestWithTaxSettings, shiftId) }
        )
    }

    /**
     * Внесение наличных в кассу.
     *
     * @param kkmId Идентификатор ККМ.
     * @param pin ПИН-код пользователя.
     * @param request Параметры операции (без pin).
     * @return Результат операции.
     */
    fun cashIn(kkmId: String, pin: String, request: CashOperationRequest): CashOperationResult {
        val requestWithPin = request.copy(pin = pin)
        return createCashOperation(kkmId, requestWithPin, CashOperationType.CASH_IN)
    }

    /**
     * Изъятие наличных из кассы.
     *
     * @param kkmId Идентификатор ККМ.
     * @param pin ПИН-код пользователя.
     * @param request Параметры операции (без pin).
     * @return Результат операции.
     */
    fun cashOut(kkmId: String, pin: String, request: CashOperationRequest): CashOperationResult {
        val requestWithPin = request.copy(pin = pin)
        return createCashOperation(kkmId, requestWithPin, CashOperationType.CASH_OUT)
    }

    /**
     * Открывает смену для ККМ и фиксирует номер смены в storage.
     *
     * @param kkmId Идентификатор ККМ.
     * @param pin ПИН-код пользователя.
     * @return Информация об открытой смене.
     * @throws ConflictException Если смена уже открыта.
     */
    fun openShift(kkmId: String, pin: String): ShiftInfo {
        val kkm = requireKkm(kkmId)
        requireOperational(kkm)
        return shiftService.openShift(kkmId, pin)
    }

    /**
     * Создает X отчет (смену не закрывает).
     */
    fun createReport(kkmId: String, pin: String): ReportResult {
        return storage.inTransaction {
            val kkm = requireKkm(kkmId)
            requireOperational(kkm, allowExpiredShift = true)
            requireRole(kkm.id, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))
            val documentId = idGenerator.nextId()
            val hasQueue = queue.hasQueuedCommands(kkmId) || storage.hasOfflineQueue(kkmId)
            val command = QueueCommandRequest(
                kkmId = kkmId,
                type = OfdCommandType.REPORT.value,
                payloadRef = documentId
            )
            if (hasQueue) {
                // По протоколу ОФД (п. 5.2) REPORT при наличии OFFLINE-очереди работает как OFFLINE:
                // не отправляем сразу, помещаем в автономную линию.
                queue.enqueueOffline(command)
            } else {
                queue.enqueueOnline(command)
            }
            ReportResult(documentId = documentId)
        }
    }

    /**
     * Закрывает смену и создает Z отчет.
     * @throws ConflictException Если смена не открыта.
     */
    fun closeShift(kkmId: String, pin: String): ReportResult {
        val kkm = requireKkm(kkmId)
        requireOperational(kkm)
        return shiftService.closeShift(kkmId, pin)
    }

    /** Проверяет связь с ОФД. */
    fun checkOfdConnection(kkmId: String): OfdCommandResult =
        ofdSyncService.checkOfdConnection(kkmId)

    /** Запрашивает информацию о кассе в ОФД (command_info). */
    fun getOfdInfo(kkmId: String): OfdCommandResult =
        ofdSyncService.getOfdInfo(kkmId)

    /** Синхронизирует сервисную информацию о ККМ с ОФД. */
    fun syncOfdServiceInfo(kkmId: String, pin: String): OfdCommandResult =
        ofdSyncService.syncOfdServiceInfo(kkmId, pin)

    /** Синхронизирует счетчики ККМ с ОФД. */
    fun syncOfdCounters(kkmId: String, pin: String): OfdCommandResult =
        ofdSyncService.syncOfdCounters(kkmId, pin)

    /** Текущая открытая смена; бросает ConflictException, если смена не открыта. */
    fun getOpenShift(kkmId: String, pin: String): ShiftInfo {
        val kkm = requireKkm(kkmId)
        requireOperational(kkm, allowExpiredShift = true)
        requireRole(kkmId, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))
        return storage.findOpenShift(kkmId)
            ?: throw ConflictException(ErrorMessages.shiftNotOpen(), "SHIFT_NOT_OPEN")
    }

    /**
     * Список фискальных документов за смену (чеки, внесения, изъятия, отчёты).
     * Требует права CASHIER или ADMIN.
     */
    fun listShiftDocuments(
        kkmId: String,
        shiftId: String,
        limit: Int,
        offset: Int = 0,
        pin: String
    ): List<FiscalDocumentSnapshot> {
        val kkm = requireKkm(kkmId)
        requireOperational(kkm, allowExpiredShift = true)
        requireRole(kkmId, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))
        return storage.listFiscalDocumentsByShift(kkmId, shiftId, limit.coerceIn(1, 500), offset)
    }

    /**
     * Список смен по ККМ (постранично). Требует права CASHIER или ADMIN.
     */
    fun listShifts(
        kkmId: String,
        limit: Int,
        offset: Int,
        pin: String
    ): List<ShiftInfo> {
        requireKkm(kkmId)
        requireRole(kkmId, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))
        return storage.listShifts(kkmId, limit.coerceIn(1, 500), offset)
    }

    /**
     * Список фискальных документов за период по created_at (from включительно, to исключительно; epoch millis).
     * Требует права CASHIER или ADMIN.
     */
    fun listFiscalDocumentsByPeriod(
        kkmId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int,
        pin: String
    ): List<FiscalDocumentSnapshot> {
        requireKkm(kkmId)
        requireRole(kkmId, pin, setOf(UserRole.ADMIN, UserRole.CASHIER))
        return storage.listFiscalDocumentsByPeriod(
            kkmId,
            fromInclusive,
            toExclusive,
            limit.coerceIn(1, 500),
            offset
        )
    }

    /** Унифицированная операция наличных с записью в storage и учетом идемпотентности. */
    private fun createCashOperation(
            kkmId: String,
            request: CashOperationRequest,
            type: CashOperationType
    ): CashOperationResult {
        val amountMoney = Money.fromTenge(request.amount)
        return fiscalOperationExecutor.executeIdempotentFiscalOperation(
            kkmId = kkmId,
            pin = request.pin,
            idempotencyKey = request.idempotencyKey,
            operationType = type.name,
            checkShift = {
                val shift = storage.findOpenShift(kkmId)
                    ?: throw ConflictException(ErrorMessages.shiftNotOpen(), "SHIFT_NOT_OPEN")
                shift.id
            },
            saveOperation = { documentId, now, shiftId ->
                storage.saveCashOperation(
                    kkmId = kkmId,
                    type = type.name,
                    amount = amountMoney,
                    documentId = documentId,
                    shiftId = shiftId,
                    createdAt = now
                )
            },
            sendOfdCommand = { kkm, documentId ->
                if (queue.hasQueuedCommands(kkmId) || storage.hasOfflineQueue(kkmId)) {
                    ofdResultQueuedOffline()
                } else {
                    sendOfdCommand(
                        kkm = kkm,
                        commandType = OfdCommandType.MONEY_PLACEMENT,
                        payloadRef = documentId
                    )
                }
            },
            processResult = { kkm, documentId, currentKkmId, ofdResult, commandType, now, _ ->
                processOfdDocumentResult(
                    kkm = kkm,
                    documentId = documentId,
                    kkmId = currentKkmId,
                    ofdResult = ofdResult,
                    commandType = commandType,
                    now = now,
                    receiptContext = null
                )
            },
            buildResult = { documentId, _ ->
                CashOperationResult(documentId = documentId)
            }
        )
    }

    /**
     * Результат «документ в офлайн» по протоколу ОФД п. 5.2: при непустой очереди OFFLINE
     * новые сообщения не отправляются на сервер, а помещаются в конец очереди.
     */
    private fun ofdResultQueuedOffline(): OfdCommandResult = OfdCommandResult(
        status = OfdCommandStatus.TIMEOUT,
        responseBin = null,
        responseJson = null,
        responseToken = null,
        responseReqNum = null,
        resultCode = null,
        resultText = null,
        errorMessage = "OFFLINE queue not empty; document queued",
        fiscalSign = null,
        autonomousSign = null
    )

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
            defaultServiceInfo = {
                OfdServiceInfo(
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
        )
        val result = ofd.send(request)
        if (updateToken && result.responseToken != null) {
            storage.updateKkmToken(kkm.id, tokenCodec.encodeToken(result.responseToken), now)
        }
        return result
    }

    private fun requireKkm(kkmId: String): KkmInfo {
        return authorization.requireKkm(kkmId)
    }

    private fun requireOperational(kkm: KkmInfo, allowExpiredShift: Boolean = false) {
        ensureSystemTimeValid()
        if (kkm.state == KkmState.BLOCKED.name) {
            throw ValidationException(ErrorMessages.kkmBlocked(), "KKM_BLOCKED")
        }
        if (kkm.state == KkmState.PROGRAMMING.name) {
            throw ValidationException(ErrorMessages.kkmInProgramming(), "KKM_IN_PROGRAMMING")
        }
        enforceAutonomousLimits(kkm)
        if (!allowExpiredShift) {
            enforceShiftDuration(kkm)
        }
    }

    private fun ensureSystemTimeValid() {
        val result = SystemTimeGuard.validate(clock)
        if (!result.ok) {
            throw ValidationException(ErrorMessages.systemTimeInvalid(), "SYSTEM_TIME_INVALID")
        }
    }

    private fun enforceAutonomousLimits(kkm: KkmInfo) {
        autonomousModeService.enforceAutonomousLimits(kkm)
    }

    private fun enforceShiftDuration(kkm: KkmInfo) {
        shiftService.enforceShiftDuration(kkm)
    }

    /**
     * Обрабатывает результат ОФД-команды: обновляет статус документа, управляет блокировкой и автономным режимом.
     * Автономный режим и перенос в офлайн — только при отсутствии ответа ОФД (resultCode == null).
     * Код 15 только блокирует кассу, в офлайн не переносит.
     */
    private fun processOfdDocumentResult(
        kkm: KkmInfo,
        documentId: String,
        kkmId: String,
        ofdResult: OfdCommandResult,
        commandType: OfdCommandType,
        now: Long,
        receiptContext: Pair<ReceiptRequest, String>?
    ) {
        val resultCode = ofdResult.resultCode
        updateKkmBlockedStateFromOfd(kkm, ofdResult, now)

        if (resultCode != null) {
            // Есть ответ ОФД — в автономный режим не переводим, в офлайн не кладём по коду
            val success = resultCode == 0
            storage.updateReceiptStatus(
                documentId = documentId,
                fiscalSign = ofdResult.fiscalSign,
                autonomousSign = ofdResult.autonomousSign,
                ofdStatus = if (success) "SENT" else "FAILED",
                deliveredAt = if (success) now else null,
                isAutonomous = false
            )
            if (success) {
                clearAutonomousIfReady(kkm, now)
                if (commandType == OfdCommandType.TICKET && receiptContext != null) {
                    counters.updateForReceipt(
                        kkmId,
                        receiptContext.second,
                        receiptContext.first,
                        isOffline = false
                    )
                }
            }
            return
        }

        // Нет ответа ОФД (обрыв связи) — считаем документ автономным, кладём в офлайн
        val autonomousSign = java.lang.String.valueOf(clock.now())
        storage.updateReceiptStatus(
            documentId = documentId,
            fiscalSign = null,
            autonomousSign = autonomousSign,
            ofdStatus = "PENDING",
            deliveredAt = null,
            isAutonomous = true
        )
        queue.enqueueOffline(
            QueueCommandRequest(
                kkmId = kkmId,
                type = commandType.value,
                payloadRef = documentId
            )
        )
        markAutonomousStarted(kkm, now)
        if (commandType == OfdCommandType.TICKET && receiptContext != null) {
            counters.updateForReceipt(
                kkmId,
                receiptContext.second,
                receiptContext.first,
                isOffline = true
            )
        }
    }

    /**
     * Обновляет состояние блокировки ККМ в зависимости от resultCode ОФД.
     *
     * - Если resultCode == 15 и касса не заблокирована — переводим в BLOCKED.
     * - Если resultCode == 0 и касса была в BLOCKED — переводим обратно в ACTIVE.
     */
    private fun updateKkmBlockedStateFromOfd(kkm: KkmInfo, ofdResult: OfdCommandResult, now: Long) {
        val code = ofdResult.resultCode ?: return
        if (code == 15 && kkm.state != KkmState.BLOCKED.name) {
            storage.updateKkm(kkm.copy(updatedAt = now, state = KkmState.BLOCKED.name))
        } else if (code == 0 && kkm.state == KkmState.BLOCKED.name) {
            storage.updateKkm(kkm.copy(updatedAt = now, state = KkmState.ACTIVE.name))
        }
    }

    private fun markAutonomousStarted(kkm: KkmInfo, now: Long) {
        if (kkm.autonomousSince != null) return
        storage.updateKkm(kkm.copy(updatedAt = now, autonomousSince = now))
    }

    private fun clearAutonomousIfReady(kkm: KkmInfo, now: Long) {
        if (kkm.autonomousSince == null && kkm.state != KkmState.BLOCKED.name) return
        if (queue.hasQueuedCommands(kkm.id) || storage.hasOfflineQueue(kkm.id)) return
        val nextState = if (kkm.state == KkmState.BLOCKED.name) KkmState.ACTIVE.name else kkm.state
        storage.updateKkm(kkm.copy(updatedAt = now, autonomousSince = null, state = nextState))
    }

    private fun requireRole(kkmId: String, pin: String, allowed: Set<UserRole>) {
        authorization.requireRole(kkmId, pin, allowed)
    }
}
