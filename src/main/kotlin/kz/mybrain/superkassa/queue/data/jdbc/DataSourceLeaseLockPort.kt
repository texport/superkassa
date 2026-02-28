package kz.mybrain.superkassa.queue.data.jdbc

import kz.mybrain.superkassa.queue.domain.port.LeaseLockPort
import javax.sql.DataSource
import org.slf4j.LoggerFactory

/**
 * DataSource-based реализация LeaseLockPort.
 * Использует пул соединений для эффективной работы в режиме POWERMOVE.
 */
class DataSourceLeaseLockPort(
    private val dataSource: DataSource
) : LeaseLockPort {
    private val logger = LoggerFactory.getLogger(DataSourceLeaseLockPort::class.java)

    override fun tryAcquire(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        return dataSource.connection.use { conn ->
            JdbcLeaseLockPort(conn).tryAcquire(cashboxId, ownerId, leaseUntil, now)
        }
    }

    override fun renew(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        return dataSource.connection.use { conn ->
            JdbcLeaseLockPort(conn).renew(cashboxId, ownerId, leaseUntil, now)
        }
    }

    override fun release(cashboxId: String, ownerId: String): Boolean {
        return dataSource.connection.use { conn ->
            JdbcLeaseLockPort(conn).release(cashboxId, ownerId)
        }
    }
}
