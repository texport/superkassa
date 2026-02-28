package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.KkmUser
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus

/**
 * Порт хранения для core.
 * Реализация находится в superkassa-storage и использует его репозитории/сессии.
 */
interface StoragePort {
    /**
     * Открывает транзакцию, которая видна всем операциям порта.
     */
    fun <T> inTransaction(block: () -> T): T

    /**
     * Регистрирует ККМ в хранилище.
     */
    fun createKkm(info: KkmInfo): Boolean
    /**
     * Обновляет состояние ККМ.
     */
    fun updateKkm(info: KkmInfo): Boolean
    /**
     * Возвращает ККМ по идентификатору.
     */
    fun findKkm(id: String): KkmInfo?
    /**
     * Ищет ККМ по регистрационному номеру.
     */
    fun findKkmByRegistrationNumber(registrationNumber: String): KkmInfo?
    /**
     * Ищет ККМ по systemId.
     */
    fun findKkmBySystemId(systemId: String): KkmInfo?
    /**
     * Возвращает список ККМ постранично с фильтрацией и сортировкой.
     */
    fun listKkms(
        limit: Int,
        offset: Int = 0,
        state: String? = null,
        search: String? = null,
        sortBy: String = "createdAt",
        sortOrder: String = "DESC"
    ): List<KkmInfo>
    /**
     * Возвращает общее количество ККМ с учётом фильтров.
     */
    fun countKkms(
        state: String? = null,
        search: String? = null
    ): Int
    /**
     * Удаляет ККМ по id.
     */
    fun deleteKkm(id: String): Boolean
    /**
     * Проверяет наличие автономной очереди по кассе.
     */
    fun hasOfflineQueue(kkmId: String): Boolean
    /**
     * Полностью удаляет кассу и все связанные данные.
     */
    fun deleteKkmCompletely(kkmId: String): Boolean
    /**
     * Обновляет токен ККМ (зашифрованный).
     */
    fun updateKkmToken(id: String, tokenEncryptedBase64: String, updatedAt: Long): Boolean

    /**
     * Возвращает пользователей ККМ.
     */
    fun listUsers(kkmId: String): List<KkmUser>
    /**
     * Создает пользователя ККМ.
     */
    fun createUser(
        kkmId: String,
        userId: String,
        name: String,
        role: UserRole,
        pin: String,
        pinHash: String,
        createdAt: Long
    ): Boolean
    /**
     * Обновляет пользователя ККМ.
     */
    fun updateUser(
        kkmId: String,
        userId: String,
        name: String?,
        role: UserRole?,
        pin: String?,
        pinHash: String?
    ): Boolean
    /**
     * Удаляет пользователя ККМ.
     */
    fun deleteUser(kkmId: String, userId: String): Boolean
    /**
     * Находит пользователя по id.
     */
    fun findUserById(kkmId: String, userId: String): KkmUser?
    /**
     * Находит пользователя по PIN.
     */
    fun findUserByPin(kkmId: String, pinHash: String): KkmUser?

    /**
     * Возвращает открытую смену по ККМ.
     */
    fun findOpenShift(kkmId: String): ShiftInfo?
    /**
     * Возвращает смену по идентификатору.
     */
    fun findShiftById(shiftId: String): ShiftInfo?
    /**
     * Список смен по ККМ (постранично, по убыванию времени открытия).
     */
    fun listShifts(kkmId: String, limit: Int, offset: Int = 0): List<ShiftInfo>
    /**
     * Создает смену.
     */
    fun createShift(shift: ShiftInfo): Boolean
    /**
     * Закрывает смену.
     */
    fun closeShift(shiftId: String, status: ShiftStatus, closedAt: Long, closeDocumentId: String?): Boolean

    /**
     * Сохраняет чек.
     */
    fun saveReceipt(request: ReceiptRequest, documentId: String, shiftId: String, createdAt: Long): Boolean
    /**
     * Сохраняет операцию с наличными (внесение/изъятие).
     */
    fun saveCashOperation(
        kkmId: String,
        type: String,
        amount: Money,
        documentId: String,
        shiftId: String,
        createdAt: Long
    ): Boolean
    /**
     * Обновляет статус чека.
     * @param isAutonomous true для чека, пробитого в автономном режиме (нет ответа ОФД).
     */
    fun updateReceiptStatus(
        documentId: String,
        fiscalSign: String?,
        autonomousSign: String?,
        ofdStatus: String,
        deliveredAt: Long?,
        isAutonomous: Boolean? = null
    ): Boolean

    /**
     * Возвращает фискальный документ по id (для чтения признаков после обновления).
     */
    fun findFiscalDocumentById(id: String): FiscalDocumentSnapshot?

    /**
     * Возвращает фискальный документ и сохранённое содержимое чека по id документа.
     * Используется для экспорта чека в HTML/PDF/image. null, если документ не найден или не чек без payload.
     */
    fun findFiscalDocumentWithReceiptPayload(documentId: String): Pair<FiscalDocumentSnapshot, ReceiptRequest>?

    /**
     * Список фискальных документов за смену (чеки, внесения, изъятия, отчёты).
     */
    fun listFiscalDocumentsByShift(
        kkmId: String,
        shiftId: String,
        limit: Int,
        offset: Int = 0
    ): List<FiscalDocumentSnapshot>

    /**
     * Список фискальных документов по ККМ за период по created_at (включительно from, исключая to; epoch millis).
     */
    fun listFiscalDocumentsByPeriod(
        kkmId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int = 0
    ): List<FiscalDocumentSnapshot>

    /**
     * Общее количество фискальных документов. Если docType != null — только данного типа (CHECK, CASH_IN и т.д.).
     */
    fun countFiscalDocuments(docType: String? = null): Long

    /**
     * Количество закрытых смен (Z-отчётов).
     */
    fun countClosedShifts(): Long

    /**
     * Количество записей в автономной очереди (по всем кассам).
     */
    fun countOfflineQueue(): Long

    /**
     * Загружает счетчики.
     */
    fun loadCounters(kkmId: String, scope: String, shiftId: String? = null): Map<String, Long>
    /**
     * Возвращает все счетчики ККМ.
     */
    fun listCounters(kkmId: String): List<CounterSnapshot>
    /**
     * Обновляет счетчик.
     */
    fun upsertCounter(kkmId: String, scope: String, shiftId: String?, key: String, value: Long): Boolean

    /**
     * Создает запись идемпотентности.
     */
    fun insertIdempotency(kkmId: String, idempotencyKey: String, operation: String): Boolean
    /**
     * Возвращает сохраненный результат для идемпотентного запроса.
     */
    fun findIdempotencyResponse(kkmId: String, idempotencyKey: String): String?
    /**
     * Обновляет результат для идемпотентного запроса.
     */
    fun updateIdempotencyResponse(kkmId: String, idempotencyKey: String, responseRef: String?): Boolean
}
