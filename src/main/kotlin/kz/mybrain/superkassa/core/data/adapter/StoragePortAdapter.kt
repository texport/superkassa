package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.model.KkmUser
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ReceiptStoredPayload
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.storage.application.bootstrap.StorageBootstrap
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.model.CashboxRecord as StorageKkmRecord
import kz.mybrain.superkassa.storage.domain.model.FiscalDocumentRecord
import kz.mybrain.superkassa.storage.domain.model.KkmUserRecord
import kz.mybrain.superkassa.storage.domain.model.ShiftRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.Base64

/**
 * Адаптер StoragePort на superkassa-storage.
 *
 * ## Потокобезопасность и управление сессиями
 *
 * ### Синхронные операции
 * - Все операции выполняются через `withSession`, который автоматически управляет жизненным циклом сессии.
 * - Операции, которые должны быть атомарными, выполняются внутри `inTransaction`, который переиспользует
 *   одну и ту же сессию через `ThreadLocal`.
 * - `ThreadLocal` гарантирует изоляцию сессий между разными потоками в синхронном коде.
 *
 * ### Асинхронные операции и корутины
 * **ВАЖНО:** `ThreadLocal` НЕ работает с корутинами и асинхронными операциями!
 *
 * - Корутины могут переключаться между потоками, поэтому `ThreadLocal` не гарантирует изоляцию.
 * - При использовании `suspend` функций, `@Async`, `CompletableFuture` или других асинхронных механизмов
 *   `ThreadLocal` может потерять значение или вернуть значение из другого потока.
 *
 * **Рекомендации для асинхронного контекста:**
 * 1. **Создавать новую сессию для каждого запроса** - самый простой и безопасный подход:
 *    ```kotlin
 *    suspend fun someAsyncOperation() {
 *        // Каждый вызов создает новую сессию
 *        storage.findKkm(kkmId) // Безопасно в корутинах
 *    }
 *    ```
 *
 * 2. **Использовать CoroutineContext для передачи сессии** (для продвинутых случаев):
 *    ```kotlin
 *    // Пример с ThreadContextElement (требует дополнительной реализации)
 *    suspend fun withStorageSession(block: suspend (StorageSession) -> T): T {
 *        val session = bootstrap.openSession(config)
 *        try {
 *            return withContext(StorageSessionContext(session)) {
 *                block(session)
 *            }
 *        } finally {
 *            session.close()
 *        }
 *    }
 *    ```
 *
 * 3. **Явная передача сессии через параметры** (для сложных транзакций):
 *    ```kotlin
 *    suspend fun complexOperation(session: StorageSession) {
 *        session.inTransaction {
 *            // Использовать session напрямую
 *        }
 *    }
 *    ```
 *
 * ### Гарантии потокобезопасности
 * - ✅ **Потокобезопасность:** Каждый поток имеет свою изолированную сессию через `ThreadLocal`.
 * - ✅ **Атомарность транзакций:** Операции внутри `inTransaction` выполняются атомарно.
 * - ✅ **Изоляция:** Операции разных потоков не влияют друг на друга.
 * - ⚠️ **Корутины:** Требуют явного управления сессиями (см. выше).
 *
 * ### Примеры использования
 *
 * **Синхронный код (безопасно):**
 * ```kotlin
 * storage.inTransaction {
 *     val kkm = storage.findKkm(id) // Использует сессию из ThreadLocal
 *     storage.updateKkm(kkm.copy(...)) // Та же сессия
 * }
 * ```
 *
 * **Корутины (требует осторожности):**
 * ```kotlin
 * suspend fun updateKkmAsync(kkmId: String) {
 *     // Каждый вызов создает новую сессию - безопасно
 *     val kkm = storage.findKkm(kkmId)
 *     storage.updateKkm(kkm.copy(...))
 *     
 *     // НЕ используйте inTransaction в корутинах без дополнительной защиты!
 *     // storage.inTransaction { ... } // Может работать некорректно
 * }
 * ```
 */
class StoragePortAdapter(
    private val bootstrap: StorageBootstrap,
    private val config: StorageConfig
) : StoragePort {
    private val logger = LoggerFactory.getLogger(StoragePortAdapter::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionHolder = ThreadLocal<kz.mybrain.superkassa.storage.application.session.StorageSession?>()

    override fun <T> inTransaction(block: () -> T): T {
        return withSession { session ->
            session.inTransaction {
                sessionHolder.set(session)
                try {
                    block()
                } finally {
                    sessionHolder.remove()
                }
            }
        }
    }

    override fun createKkm(info: KkmInfo): Boolean {
        return withSession { session ->
            session.cashboxes.insert(mapKkmToRecord(info))
        }
    }

    override fun updateKkm(info: KkmInfo): Boolean {
        return withSession { session ->
            session.cashboxes.update(mapKkmToRecord(info))
        }
    }

    override fun findKkm(id: String): KkmInfo? {
        return withSession { session ->
            session.cashboxes.findById(id)?.let { mapKkm(it) }
        }
    }

    override fun findKkmByRegistrationNumber(registrationNumber: String): KkmInfo? {
        return withSession { session ->
            session.cashboxes.findByRegistrationNumber(registrationNumber)?.let { mapKkm(it) }
        }
    }

    override fun findKkmBySystemId(systemId: String): KkmInfo? {
        return withSession { session ->
            session.cashboxes.findBySystemId(systemId)?.let { mapKkm(it) }
        }
    }

    override fun listKkms(
        limit: Int,
        offset: Int,
        state: String?,
        search: String?,
        sortBy: String,
        sortOrder: String
    ): List<KkmInfo> {
        return withSession { session ->
            session.cashboxes.listAllFiltered(
                limit = limit,
                offset = offset,
                state = state,
                search = search,
                sortBy = sortBy,
                sortOrder = sortOrder
            ).map { mapKkm(it) }
        }
    }

    override fun countKkms(state: String?, search: String?): Int {
        return withSession { session ->
            session.cashboxes.countAll(state, search)
        }
    }

    override fun deleteKkm(id: String): Boolean {
        return withSession { session ->
            session.cashboxes.deleteById(id)
        }
    }

    override fun hasOfflineQueue(kkmId: String): Boolean {
        return withSession { session ->
            session.offlineQueue.listByCashbox(kkmId, 1).isNotEmpty()
        }
    }

    override fun deleteKkmCompletely(kkmId: String): Boolean {
        return withSession { session ->
            session.inTransaction {
                session.locks.deleteByCashbox(kkmId)
                session.idempotency.deleteByCashbox(kkmId)
                session.offlineQueue.deleteByCashbox(kkmId)
                session.users.deleteByCashbox(kkmId)
                session.documents.deleteByCashbox(kkmId)
                session.journal.deleteByCashbox(kkmId)
                session.ofdMessages.deleteByCashbox(kkmId)
                session.shifts.deleteByCashbox(kkmId)
                session.counters.deleteByCashbox(kkmId)
                session.errors.deleteByCashbox(kkmId)
                session.outbox.deleteByCashbox(kkmId)
                session.cashboxes.deleteById(kkmId)
            }
        }
    }

    override fun updateKkmToken(id: String, tokenEncryptedBase64: String, updatedAt: Long): Boolean {
        val tokenBytes = decodeBase64(tokenEncryptedBase64)
            ?: return false
        return withSession { session ->
            session.cashboxes.updateToken(id, tokenBytes, updatedAt)
        }
    }

    override fun listUsers(kkmId: String): List<KkmUser> {
        return withSession { session ->
            session.users.listByCashbox(kkmId).map { it.toDomain() }
        }
    }

    override fun createUser(
        kkmId: String,
        userId: String,
        name: String,
        role: UserRole,
        pin: String,
        pinHash: String,
        createdAt: Long
    ): Boolean {
        return withSession { session ->
            session.users.insert(
                KkmUserRecord(
                    id = userId,
                    cashboxId = kkmId,
                    name = name,
                    role = role.name,
                    pin = pin,
                    pinHash = pinHash,
                    createdAt = createdAt
                )
            )
        }
    }

    override fun updateUser(
        kkmId: String,
        userId: String,
        name: String?,
        role: UserRole?,
        pin: String?,
        pinHash: String?
    ): Boolean {
        return withSession { session ->
            session.users.update(
                cashboxId = kkmId,
                userId = userId,
                name = name,
                role = role?.name,
                pin = pin,
                pinHash = pinHash
            )
        }
    }

    override fun deleteUser(kkmId: String, userId: String): Boolean {
        return withSession { session ->
            session.users.deleteById(kkmId, userId)
        }
    }

    override fun findUserById(kkmId: String, userId: String): KkmUser? {
        return withSession { session ->
            session.users.findById(kkmId, userId)?.toDomain()
        }
    }

    override fun findUserByPin(kkmId: String, pinHash: String): KkmUser? {
        return withSession { session ->
            session.users.findByCashboxAndPinHash(kkmId, pinHash)?.toDomain()
        }
    }

    override fun findOpenShift(kkmId: String): ShiftInfo? {
        return withSession { session ->
            session.shifts.findOpenByCashbox(kkmId)?.let { mapShift(it) }
        }
    }

    override fun findShiftById(shiftId: String): ShiftInfo? {
        return withSession { session ->
            session.shifts.findById(shiftId)?.let { mapShift(it) }
        }
    }

    override fun listShifts(kkmId: String, limit: Int, offset: Int): List<ShiftInfo> {
        return withSession { session ->
            session.shifts.listByCashbox(kkmId, limit, offset).map { mapShift(it) }
        }
    }

    override fun createShift(shift: ShiftInfo): Boolean {
        return withSession { session ->
            session.shifts.insert(
                ShiftRecord(
                    id = shift.id,
                    cashboxId = shift.kkmId,
                    shiftNo = shift.shiftNo,
                    status = shift.status.name,
                    openedAt = shift.openedAt,
                    closedAt = shift.closedAt
                )
            )
        }
    }

    override fun closeShift(shiftId: String, status: ShiftStatus, closedAt: Long, closeDocumentId: String?): Boolean {
        return withSession { session ->
            session.shifts.updateClose(shiftId, status.name, closedAt, closeDocumentId)
        }
    }

    override fun saveReceipt(request: ReceiptRequest, documentId: String, shiftId: String, createdAt: Long): Boolean {
        return withSession { session ->
            val payloadBin = Json.encodeToString(serializer<ReceiptStoredPayload>(), ReceiptStoredPayload.fromReceiptRequest(request)).toByteArray(Charsets.UTF_8)
            session.documents.insert(
                FiscalDocumentRecord(
                    id = documentId,
                    cashboxId = request.kkmId,
                    shiftId = shiftId,
                    docType = "CHECK",
                    docNo = null,
                    shiftNo = null,
                    createdAt = createdAt,
                    totalAmount = request.total.bills,
                    currency = "KZT",
                    payloadBin = payloadBin,
                    ofdStatus = "PENDING"
                )
            )
        }
    }

    override fun saveCashOperation(
        kkmId: String,
        type: String,
        amount: kz.mybrain.superkassa.core.domain.model.Money,
        documentId: String,
        shiftId: String,
        createdAt: Long
    ): Boolean {
        return withSession { session ->
            // Операции с наличными фиксируются тем же типом документа, что и чеки/отчеты.
            session.documents.insert(
                FiscalDocumentRecord(
                    id = documentId,
                    cashboxId = kkmId,
                    shiftId = shiftId,
                    docType = type,
                    docNo = null,
                    shiftNo = null,
                    createdAt = createdAt,
                    totalAmount = amount.bills,
                    currency = "KZT",
                    payloadBin = null,
                    ofdStatus = "PENDING"
                )
            )
        }
    }

    override fun updateReceiptStatus(
        documentId: String,
        fiscalSign: String?,
        autonomousSign: String?,
        ofdStatus: String,
        deliveredAt: Long?,
        isAutonomous: Boolean?
    ): Boolean {
        return withSession { session ->
            session.documents.updateStatus(
                id = documentId,
                ofdStatus = ofdStatus,
                fiscalSign = fiscalSign,
                autonomousSign = autonomousSign,
                deliveredAt = deliveredAt,
                isAutonomous = isAutonomous
            )
        }
    }

    override fun findFiscalDocumentById(id: String): FiscalDocumentSnapshot? {
        return withSession { session ->
            session.documents.findById(id)?.let { toFiscalDocumentSnapshot(it) }
        }
    }

    override fun findFiscalDocumentWithReceiptPayload(documentId: String): Pair<FiscalDocumentSnapshot, ReceiptRequest>? {
        return withSession { session ->
            val record = session.documents.findById(documentId) ?: return@withSession null
            if (record.docType != "CHECK" || record.payloadBin == null || record.payloadBin.isEmpty()) return@withSession null
            val payload = try {
                Json.decodeFromString<ReceiptStoredPayload>(String(record.payloadBin, Charsets.UTF_8))
            } catch (_: Exception) {
                return@withSession null
            }
            toFiscalDocumentSnapshot(record) to payload.toReceiptRequest()
        }
    }

    override fun listFiscalDocumentsByShift(
        kkmId: String,
        shiftId: String,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentSnapshot> {
        return withSession { session ->
            session.documents.listByShift(kkmId, shiftId, limit, offset)
                .map { toFiscalDocumentSnapshot(it) }
        }
    }

    override fun listFiscalDocumentsByPeriod(
        kkmId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentSnapshot> {
        return withSession { session ->
            session.documents.listByCashboxAndCreatedAtBetween(kkmId, fromInclusive, toExclusive, limit, offset)
                .map { toFiscalDocumentSnapshot(it) }
        }
    }

    override fun countFiscalDocuments(docType: String?): Long {
        return withSession { session ->
            session.documents.countAll(docType)
        }
    }

    override fun countClosedShifts(): Long {
        return withSession { session ->
            session.shifts.countAll("CLOSED")
        }
    }

    override fun countOfflineQueue(): Long {
        return withSession { session ->
            session.offlineQueue.countAll()
        }
    }

    private fun toFiscalDocumentSnapshot(r: FiscalDocumentRecord): FiscalDocumentSnapshot = FiscalDocumentSnapshot(
        id = r.id,
        cashboxId = r.cashboxId,
        shiftId = r.shiftId ?: "",
        docType = r.docType,
        docNo = r.docNo,
        shiftNo = r.shiftNo,
        createdAt = r.createdAt,
        totalAmount = r.totalAmount,
        currency = r.currency,
        fiscalSign = r.fiscalSign,
        autonomousSign = r.autonomousSign,
        isAutonomous = r.isAutonomous,
        ofdStatus = r.ofdStatus,
        deliveredAt = r.deliveredAt
    )

    override fun loadCounters(kkmId: String, scope: String, shiftId: String?): Map<String, Long> {
        return withSession { session ->
            session.counters.listByScope(kkmId, scope, shiftId)
                .associate { it.key to it.value }
        }
    }

    override fun listCounters(kkmId: String): List<CounterSnapshot> {
        return withSession { session ->
            session.counters.listByCashbox(kkmId).map {
                CounterSnapshot(
                    scope = it.scope,
                    shiftId = it.shiftId,
                    key = it.key,
                    value = it.value,
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    override fun upsertCounter(kkmId: String, scope: String, shiftId: String?, key: String, value: Long): Boolean {
        return withSession { session ->
            session.counters.upsert(
                kz.mybrain.superkassa.storage.domain.model.CounterRecord(
                    cashboxId = kkmId,
                    scope = scope,
                    shiftId = shiftId,
                    key = key,
                    value = value,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun insertIdempotency(kkmId: String, idempotencyKey: String, operation: String): Boolean {
        return withSession { session ->
            session.idempotency.insertIfAbsent(
                kz.mybrain.superkassa.storage.domain.model.IdempotencyRecord(
                    idempotencyKey = idempotencyKey,
                    cashboxId = kkmId,
                    operation = operation,
                    createdAt = System.currentTimeMillis(),
                    status = "CREATED",
                    responseRef = null
                )
            )
        }
    }

    override fun findIdempotencyResponse(kkmId: String, idempotencyKey: String): String? {
        return withSession { session ->
            session.idempotency.findByKey(kkmId, idempotencyKey)?.responseRef
        }
    }

    override fun updateIdempotencyResponse(kkmId: String, idempotencyKey: String, responseRef: String?): Boolean {
        return withSession { session ->
            session.idempotency.updateResponse(kkmId, idempotencyKey, "DONE", responseRef)
        }
    }

    private fun mapShift(record: ShiftRecord): ShiftInfo {
        return ShiftInfo(
            id = record.id,
            kkmId = record.cashboxId,
            shiftNo = record.shiftNo,
            status = parseShiftStatus(record.status),
            openedAt = record.openedAt,
            closedAt = record.closedAt
        )
    }

    private fun mapKkm(record: StorageKkmRecord): KkmInfo {
        return KkmInfo(
            id = record.id,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            mode = record.mode,
            state = record.state,
            ofdProvider = record.ofdProvider,
            registrationNumber = record.registrationNumber,
            factoryNumber = record.factoryNumber,
            manufactureYear = record.manufactureYear,
            systemId = record.systemId,
            ofdServiceInfo = decodeServiceInfo(record.ofdServiceInfoJson),
            tokenEncryptedBase64 = encodeBase64(record.tokenEncrypted),
            tokenUpdatedAt = record.tokenUpdatedAt,
            lastShiftNo = record.lastShiftNo,
            lastReceiptNo = record.lastReceiptNo,
            lastZReportNo = record.lastZReportNo,
            autonomousSince = record.autonomousSince,
            autoCloseShift = record.autoCloseShift,
            lastFiscalHashBase64 = encodeBase64(record.lastFiscalHash),
            taxRegime = parseTaxRegime(record.taxRegime),
            defaultVatGroup = parseVatGroup(record.defaultVatGroup)
        )
    }

    private fun mapKkmToRecord(info: KkmInfo): StorageKkmRecord {
        return StorageKkmRecord(
            id = info.id,
            createdAt = info.createdAt,
            updatedAt = info.updatedAt,
            mode = info.mode,
            state = info.state,
            ofdProvider = info.ofdProvider,
            registrationNumber = info.registrationNumber,
            factoryNumber = info.factoryNumber,
            manufactureYear = info.manufactureYear,
            systemId = info.systemId,
            ofdServiceInfoJson = encodeServiceInfo(info.ofdServiceInfo),
            tokenEncrypted = decodeBase64(info.tokenEncryptedBase64),
            tokenUpdatedAt = info.tokenUpdatedAt,
            lastShiftNo = info.lastShiftNo,
            lastReceiptNo = info.lastReceiptNo,
            lastZReportNo = info.lastZReportNo,
            autonomousSince = info.autonomousSince,
            autoCloseShift = info.autoCloseShift,
            lastFiscalHash = decodeBase64(info.lastFiscalHashBase64),
            taxRegime = info.taxRegime.name,
            defaultVatGroup = info.defaultVatGroup.name
        )
    }

    private fun encodeServiceInfo(info: OfdServiceInfo?): String? {
        if (info == null) return null
        return runCatching { json.encodeToString(serializer<OfdServiceInfo>(), info) }.getOrNull()
    }

    private fun decodeServiceInfo(payload: String?): OfdServiceInfo? {
        if (payload.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(serializer<OfdServiceInfo>(), payload) }.getOrNull()
    }

    private fun encodeBase64(bytes: ByteArray?): String? {
        if (bytes == null) return null
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun KkmUserRecord.toDomain(): KkmUser {
        return KkmUser(
            id = this.id,
            name = this.name,
            role = parseUserRole(this.role),
            pin = this.pin,
            createdAt = this.createdAt
        )
    }

    private fun parseUserRole(roleString: String): UserRole {
        return try {
            UserRole.valueOf(roleString)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid user role in database: $roleString", ex)
            throw ValidationException(
                ErrorMessages.userRoleInvalid(roleString),
                "INVALID_USER_ROLE"
            )
        }
    }

    private fun parseShiftStatus(statusString: String): ShiftStatus {
        return try {
            ShiftStatus.valueOf(statusString)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid shift status in database: $statusString", ex)
            throw ValidationException(
                ErrorMessages.shiftStatusInvalid(statusString),
                "INVALID_SHIFT_STATUS"
            )
        }
    }

    private fun parseTaxRegime(value: String?): kz.mybrain.superkassa.core.domain.model.TaxRegime {
        if (value.isNullOrBlank()) return kz.mybrain.superkassa.core.domain.model.TaxRegime.NO_VAT
        return try {
            kz.mybrain.superkassa.core.domain.model.TaxRegime.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid tax regime in database: $value", ex)
            kz.mybrain.superkassa.core.domain.model.TaxRegime.NO_VAT
        }
    }

    private fun parseVatGroup(value: String?): kz.mybrain.superkassa.core.domain.model.VatGroup {
        if (value.isNullOrBlank()) return kz.mybrain.superkassa.core.domain.model.VatGroup.NO_VAT
        return try {
            kz.mybrain.superkassa.core.domain.model.VatGroup.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid VAT group in database: $value", ex)
            kz.mybrain.superkassa.core.domain.model.VatGroup.NO_VAT
        }
    }

    private fun decodeBase64(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        return try {
            Base64.getDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid Base64 format in database", ex)
            throw ValidationException(
                ErrorMessages.invalidBase64Format(),
                "INVALID_BASE64_FORMAT"
            )
        }
    }

    private fun <T> withSession(block: (kz.mybrain.superkassa.storage.application.session.StorageSession) -> T): T {
        val existing = sessionHolder.get()
        if (existing != null) {
            return block(existing)
        }
        return openSessionWithRetry(maxAttempts = 3, delayMs = 200).use { session ->
            block(session)
        }
    }

    /**
     * Открывает сессию с повтором при временных сбоях (connection timeout, unavailable).
     * Retry для отказоустойчивости операций с БД (п. 7.3 плана).
     */
    private fun openSessionWithRetry(maxAttempts: Int = 3, delayMs: Long = 200): kz.mybrain.superkassa.storage.application.session.StorageSession {
        var lastEx: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return bootstrap.openSession(config)
            } catch (e: Exception) {
                lastEx = e
                if (!isTransientDbFailure(e) || attempt == maxAttempts) {
                    throw e
                }
                logger.warn("Storage session open failed (attempt {}/{}), retrying in {}ms: {}", attempt, maxAttempts, delayMs, e.message)
                Thread.sleep(delayMs)
            }
        }
        throw lastEx ?: IllegalStateException("openSessionWithRetry failed")
    }

    private fun isTransientDbFailure(e: Exception): Boolean {
        if (e is SQLException) return true
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("connection") || msg.contains("timeout") || msg.contains("unavailable") ||
            msg.contains("refused") || msg.contains("network")
    }
}
