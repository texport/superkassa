package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.CashboxLock
import kz.mybrain.superkassa.storage.domain.model.CashboxRecord
import kz.mybrain.superkassa.storage.domain.model.CounterRecord
import kz.mybrain.superkassa.storage.domain.model.ErrorMessageRecord
import kz.mybrain.superkassa.storage.domain.model.FiscalDocumentRecord
import kz.mybrain.superkassa.storage.domain.model.FiscalJournalRecord
import kz.mybrain.superkassa.storage.domain.model.IdempotencyRecord
import kz.mybrain.superkassa.storage.domain.model.KkmUserRecord
import kz.mybrain.superkassa.storage.domain.model.OfdMessageRecord
import kz.mybrain.superkassa.storage.domain.model.OfflineQueueItem
import kz.mybrain.superkassa.storage.domain.model.OutboxEventRecord
import kz.mybrain.superkassa.storage.domain.model.ShiftRecord

/**
 * Репозиторий состояния кассы.
 *
 * Зачем нужен:
 * - восстановить режим/счетчики после перезапуска,
 * - хранить реквизиты кассы и токены,
 * - контролировать ограничения (например, длительность смены).
 */
interface CashboxRepository {
    /**
     * Создает запись кассы.
     */
    fun insert(record: CashboxRecord): Boolean
    /**
     * Обновляет запись кассы.
     */
    fun update(record: CashboxRecord): Boolean
    /**
     * Ищет кассу по id.
     */
    fun findById(id: String): CashboxRecord?
    /**
     * Ищет кассу по регистрационному номеру.
     */
    fun findByRegistrationNumber(registrationNumber: String): CashboxRecord?
    /**
     * Ищет кассу по system_id.
     */
    fun findBySystemId(systemId: String): CashboxRecord?
    /**
     * Список касс (постранично).
     */
    fun listAll(limit: Int, offset: Int = 0): List<CashboxRecord>
    
    /**
     * Список касс с фильтрацией и сортировкой.
     * @param limit максимальное количество записей
     * @param offset смещение для пагинации
     * @param state фильтр по состоянию кассы (например, "ACTIVE")
     * @param search поиск по регистрационному номеру (частичное совпадение)
     * @param sortBy поле для сортировки (created_at, updated_at, state, registration_number)
     * @param sortOrder порядок сортировки ("ASC" или "DESC")
     */
    fun listAllFiltered(
        limit: Int,
        offset: Int = 0,
        state: String? = null,
        search: String? = null,
        sortBy: String = "created_at",
        sortOrder: String = "DESC"
    ): List<CashboxRecord>
    
    /**
     * Подсчет касс с учетом фильтров.
     * @param state фильтр по состоянию кассы
     * @param search поиск по регистрационному номеру
     */
    fun countAll(
        state: String? = null,
        search: String? = null
    ): Int
    /**
     * Обновляет токен ОФД для кассы.
     */
    fun updateToken(
        id: String,
        tokenEncrypted: ByteArray,
        tokenUpdatedAt: Long
    ): Boolean
    /**
     * Удаляет кассу по id.
     * Используется при деактивации/удалении кассы.
     */
    fun deleteById(id: String): Boolean
}

/**
 * Репозиторий пользователей ККМ.
 */
interface KkmUserRepository {
    fun insert(record: KkmUserRecord): Boolean
    fun update(
        cashboxId: String,
        userId: String,
        name: String?,
        role: String?,
        pin: String?,
        pinHash: String?
    ): Boolean
    fun deleteById(cashboxId: String, userId: String): Boolean
    fun deleteByCashbox(cashboxId: String): Boolean
    fun listByCashbox(cashboxId: String): List<KkmUserRecord>
    fun findById(cashboxId: String, userId: String): KkmUserRecord?
    fun findByCashboxAndPinHash(cashboxId: String, pinHash: String): KkmUserRecord?
}

/**
 * Репозиторий фискальных документов (чеки/отчеты/операции).
 *
 * Зачем нужен:
 * - хранить все документы в едином формате,
 * - связывать документ с кассой и сменой,
 * - фиксировать фискальные признаки и статус доставки.
 */
interface FiscalDocumentRepository {
    /**
     * Создает фискальный документ.
     */
    fun insert(record: FiscalDocumentRecord): Boolean
    /**
     * Ищет документ по id.
     */
    fun findById(id: String): FiscalDocumentRecord?
    /**
     * Ищет документ по кассе и номеру документа.
     */
    fun findByCashboxAndDocNo(cashboxId: String, docNo: Long): FiscalDocumentRecord?
    /**
     * Обновляет статус доставки и фискальные признаки.
     * @param isAutonomous если не null — обновляет признак автономного документа.
     */
    fun updateStatus(
        id: String,
        ofdStatus: String,
        fiscalSign: String?,
        autonomousSign: String?,
        deliveredAt: Long?,
        isAutonomous: Boolean? = null
    ): Boolean
    /**
     * Список документов по кассе (постранично).
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<FiscalDocumentRecord>
    /**
     * Список документов по смене (постранично).
     */
    fun listByShift(
        cashboxId: String,
        shiftId: String,
        limit: Int,
        offset: Int = 0
    ): List<FiscalDocumentRecord>
    /**
     * Список документов по кассе за период по created_at (from включительно, to исключая; epoch millis).
     */
    fun listByCashboxAndCreatedAtBetween(
        cashboxId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int = 0
    ): List<FiscalDocumentRecord>
    /**
     * Удаляет документ по id.
     * Не используется для фискального журнала (он append-only).
     */
    fun deleteById(id: String): Boolean
    /**
     * Удаляет все документы по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
    /**
     * Общее количество фискальных документов (по всем кассам). Если docType != null — только данного типа (CHECK, CASH_IN и т.д.).
     */
    fun countAll(docType: String? = null): Long
}

/**
 * Репозиторий фискального журнала (append-only + hash-chain).
 *
 * Зачем нужен:
 * - контроль целостности истории,
 * - невозможность тихого изменения записей,
 * - аудит операций по кассе.
 */
interface FiscalJournalRepository {
    /**
     * Добавляет запись в append-only журнал.
     */
    fun append(record: FiscalJournalRecord): Boolean
    /**
     * Возвращает последние записи журнала по кассе.
     */
    fun listByCashbox(cashboxId: String, limit: Int): List<FiscalJournalRecord>
    /**
     * Возвращает последний hash в цепочке.
     */
    fun lastHash(cashboxId: String): ByteArray?
    /**
     * Удаляет все записи журнала по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}

/**
 * Репозиторий сообщений ОФД.
 *
 * Зачем нужен:
 * - хранить request/response для повторной отправки,
 * - диагностировать ошибки связи,
 * - восстанавливать состояние при сбоях.
 */
interface OfdMessageRepository {
    /**
     * Создает запись запроса ОФД.
     */
    fun insert(record: OfdMessageRecord): Boolean
    /**
     * Обновляет ответ/статус по запросу ОФД.
     */
    fun updateResponse(
        id: String,
        responseBin: ByteArray?,
        status: String,
        attempt: Int,
        errorCode: String?,
        updatedAt: Long
    ): Boolean
    /**
     * Ищет запись по id.
     */
    fun findById(id: String): OfdMessageRecord?
    /**
     * Список ожидающих отправки запросов.
     */
    fun listPending(cashboxId: String, limit: Int): List<OfdMessageRecord>
    /**
     * Список сообщений по кассе (постранично).
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<OfdMessageRecord>
    /**
     * Удаляет сообщение по id (например, по политике хранения).
     */
    fun deleteById(id: String): Boolean
    /**
     * Удаляет все сообщения по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}

/**
 * Репозиторий автономной очереди.
 *
 * Зачем нужен:
 * - строго FIFO выгрузка чеков при восстановлении связи,
 * - контроль попыток и backoff,
 * - гарантированный порядок операций по кассе.
 */
interface OfflineQueueRepository {
    /**
     * Добавляет операцию в офлайн-очередь.
     */
    fun enqueue(item: OfflineQueueItem): Boolean
    /**
     * Возвращает следующий элемент для обработки.
     */
    fun nextPending(cashboxId: String): OfflineQueueItem?
    /**
     * Обновляет попытки/статус обработки.
     */
    fun updateAttempt(
        id: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?,
        status: String
    ): Boolean
    /**
     * Помечает элемент как выполненный.
     */
    fun markCompleted(id: String, status: String): Boolean
    /**
     * Список элементов очереди по кассе (постранично).
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<OfflineQueueItem>
    /**
     * Удаляет элемент очереди по id (например, по политике хранения).
     */
    fun deleteById(id: String): Boolean
    /**
     * Удаляет все элементы очереди по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
    /**
     * Общее количество записей в автономной очереди (по всем кассам).
     */
    fun countAll(): Long
}

/**
 * Репозиторий идемпотентности.
 *
 * Зачем нужен:
 * - не пробивать чек дважды при повторном запросе,
 * - возвращать предыдущий результат на дубликаты.
 */
interface IdempotencyRepository {
    /**
     * Вставляет запись, если ключ не существует.
     */
    fun insertIfAbsent(record: IdempotencyRecord): Boolean
    /**
     * Ищет запись по кассе и ключу.
     */
    fun findByKey(cashboxId: String, idempotencyKey: String): IdempotencyRecord?
    /**
     * Обновляет статус и ссылку на результат.
     */
    fun updateResponse(
        cashboxId: String,
        idempotencyKey: String,
        status: String,
        responseRef: String?
    ): Boolean
    /**
     * Удаляет запись идемпотентности.
     */
    fun deleteByKey(cashboxId: String, idempotencyKey: String): Boolean
    /**
     * Удаляет все записи идемпотентности по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}

/**
 * Репозиторий lease/lock на кассу.
 *
 * Зачем нужен:
 * - запретить параллельные фискальные операции,
 * - обеспечить правило \"1 касса = 1 операция\".
 */
interface CashboxLockRepository {
    /**
     * Пытается захватить lease на кассу.
     */
    fun tryAcquire(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean
    /**
     * Продлевает lease при условии, что владелец совпадает.
     */
    fun renew(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean
    /**
     * Освобождает lease.
     */
    fun release(cashboxId: String, ownerId: String): Boolean
    /**
     * Возвращает текущий lease по кассе.
     */
    fun findByCashboxId(cashboxId: String): CashboxLock?
    /**
     * Удаляет все lease по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}

/**
 * Репозиторий смен.
 *
 * Зачем нужен:
 * - понимать, когда смена открыта/закрыта,
 * - связывать документы с конкретной сменой,
 * - корректно формировать X и Z отчеты.
 */
interface ShiftRepository {
    /**
     * Создает смену.
     */
    fun insert(record: ShiftRecord): Boolean
    /**
     * Обновляет статус/время закрытия смены.
     */
    fun updateClose(
        id: String,
        status: String,
        closedAt: Long,
        closeDocumentId: String?
    ): Boolean
    /**
     * Возвращает смену по id.
     */
    fun findById(id: String): ShiftRecord?
    /**
     * Возвращает смену по номеру.
     */
    fun findByShiftNo(cashboxId: String, shiftNo: Long): ShiftRecord?
    /**
     * Возвращает текущую открытую смену.
     */
    fun findOpenByCashbox(cashboxId: String): ShiftRecord?
    /**
     * Возвращает список смен по кассе.
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<ShiftRecord>
    /**
     * Удаляет все смены по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
    /**
     * Общее количество смен. Если status != null — только с данным статусом (OPEN, CLOSED).
     */
    fun countAll(status: String? = null): Long
}

/**
 * Репозиторий счетчиков кассы (глобальных и сменных).
 *
 * Зачем нужен:
 * - хранить счетчики для X/Z отчетов,
 * - разделять глобальные и сменные значения.
 */
interface CounterRepository {
    /**
     * Создает или обновляет счетчик.
     */
    fun upsert(record: CounterRecord): Boolean
    /**
     * Возвращает счетчик по ключу.
     */
    fun findByKey(
        cashboxId: String,
        scope: String,
        shiftId: String?,
        key: String
    ): CounterRecord?
    /**
     * Возвращает список счетчиков по области.
     */
    fun listByScope(
        cashboxId: String,
        scope: String,
        shiftId: String?
    ): List<CounterRecord>
    /**
     * Возвращает все счетчики по кассе.
     */
    fun listByCashbox(cashboxId: String): List<CounterRecord>
    /**
     * Удаляет счетчик.
     */
    fun deleteByKey(
        cashboxId: String,
        scope: String,
        shiftId: String?,
        key: String
    ): Boolean
    /**
     * Удаляет все счетчики по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}

/**
 * Репозиторий централизованных ошибок.
 *
 * Зачем нужен:
 * - хранить ошибки в одном месте,
 * - иметь сообщения на двух языках,
 * - отдавать пользователю понятную диагностику.
 */
interface ErrorMessageRepository {
    /**
     * Сохраняет ошибку.
     */
    fun insert(record: ErrorMessageRecord): Boolean
    /**
     * Ищет ошибку по id.
     */
    fun findById(id: String): ErrorMessageRecord?
    /**
     * Возвращает последние ошибки (глобально).
     */
    fun listRecent(limit: Int): List<ErrorMessageRecord>
    /**
     * Возвращает последние ошибки по кассе.
     */
    fun listByCashbox(cashboxId: String, limit: Int): List<ErrorMessageRecord>
    /**
     * Удаляет ошибку по id.
     */
    fun deleteById(id: String): Boolean
    /**
     * Удаляет все ошибки по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}

/**
 * Репозиторий outbox-событий.
 *
 * Зачем нужен:
 * - надежно отдавать события во внешние системы (Kafka и т.д.),
 * - не терять события при сбоях.
 */
interface OutboxEventRepository {
    /**
     * Создает событие.
     */
    fun insert(record: OutboxEventRecord): Boolean
    /**
     * Возвращает событие по id.
     */
    fun findById(id: String): OutboxEventRecord?
    /**
     * Список ожидающих событий.
     */
    fun listPending(limit: Int): List<OutboxEventRecord>
    /**
     * Обновляет статус и попытки отправки.
     */
    fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        nextAttemptAt: Long?,
        lastError: String?
    ): Boolean
    /**
     * Удаляет событие (по политике хранения).
     */
    fun deleteById(id: String): Boolean
    /**
     * Удаляет все события по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
