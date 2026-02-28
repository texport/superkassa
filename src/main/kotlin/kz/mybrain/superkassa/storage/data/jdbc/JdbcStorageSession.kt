package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.session.StorageSession
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
import java.sql.Connection

/**
 * JDBC-реализация StorageSession.
 */
class JdbcStorageSession(
    private val connection: Connection
) : StorageSession {
    override val cashboxes: CashboxRepository = JdbcCashboxRepository(connection)
    override val documents: FiscalDocumentRepository = JdbcFiscalDocumentRepository(connection)
    override val journal: FiscalJournalRepository = JdbcFiscalJournalRepository(connection)
    override val users: KkmUserRepository = JdbcKkmUserRepository(connection)
    override val ofdMessages: OfdMessageRepository = JdbcOfdMessageRepository(connection)
    override val offlineQueue: OfflineQueueRepository = JdbcOfflineQueueRepository(connection)
    override val idempotency: IdempotencyRepository = JdbcIdempotencyRepository(connection)
    override val locks: CashboxLockRepository = JdbcCashboxLockRepository(connection)
    override val shifts: ShiftRepository = JdbcShiftRepository(connection)
    override val counters: CounterRepository = JdbcCounterRepository(connection)
    override val errors: ErrorMessageRepository = JdbcErrorMessageRepository(connection)
    override val outbox: OutboxEventRepository = JdbcOutboxEventRepository(connection)

    override fun <T> inTransaction(block: () -> T): T {
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            val result = block()
            connection.commit()
            result
        } catch (ex: Exception) {
            connection.rollback()
            throw ex
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    override fun close() {
        connection.close()
    }
}
