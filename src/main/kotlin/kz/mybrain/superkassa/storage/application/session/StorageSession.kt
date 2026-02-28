package kz.mybrain.superkassa.storage.application.session

import kz.mybrain.superkassa.storage.domain.repository.CashboxLockRepository
import kz.mybrain.superkassa.storage.domain.repository.CashboxRepository
import kz.mybrain.superkassa.storage.domain.repository.CounterRepository
import kz.mybrain.superkassa.storage.domain.repository.ErrorMessageRepository
import kz.mybrain.superkassa.storage.domain.repository.FiscalDocumentRepository
import kz.mybrain.superkassa.storage.domain.repository.FiscalJournalRepository
import kz.mybrain.superkassa.storage.domain.repository.IdempotencyRepository
import kz.mybrain.superkassa.storage.domain.repository.KkmUserRepository
import kz.mybrain.superkassa.storage.domain.repository.OfdMessageRepository
import kz.mybrain.superkassa.storage.domain.repository.OfflineQueueRepository
import kz.mybrain.superkassa.storage.domain.repository.OutboxEventRepository
import kz.mybrain.superkassa.storage.domain.repository.ShiftRepository

/**
 * Единая сессия доступа к репозиториям.
 *
 * Зачем это нужно:
 * - чтобы сгруппировать работу с разными таблицами в одном месте,
 * - чтобы гарантировать атомарность операций через транзакцию,
 * - чтобы можно было подменять реализацию (JDBC/Room/корпоративный storage).
 */
interface StorageSession : AutoCloseable {
    val cashboxes: CashboxRepository
    val documents: FiscalDocumentRepository
    val journal: FiscalJournalRepository
    val users: KkmUserRepository
    val ofdMessages: OfdMessageRepository
    val offlineQueue: OfflineQueueRepository
    val idempotency: IdempotencyRepository
    val locks: CashboxLockRepository
    val shifts: ShiftRepository
    val counters: CounterRepository
    val errors: ErrorMessageRepository
    val outbox: OutboxEventRepository

    /**
     * Выполняет блок в транзакции.
     * Используется для операций, которые должны быть атомарными.
     */
    fun <T> inTransaction(block: () -> T): T
    override fun close()
}
