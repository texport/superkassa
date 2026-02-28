package kz.mybrain.superkassa.queue.data.jdbc

import kz.mybrain.superkassa.queue.domain.model.QueueCommand
import kz.mybrain.superkassa.queue.domain.model.QueueLane
import kz.mybrain.superkassa.queue.domain.model.QueueStatus
import kz.mybrain.superkassa.queue.domain.port.QueueStoragePort
import javax.sql.DataSource
import org.slf4j.LoggerFactory

/**
 * DataSource-based реализация QueueStoragePort.
 * Использует пул соединений для эффективной работы в режиме POWERMOVE.
 */
class DataSourceQueueStoragePort(
    private val dataSource: DataSource
) : QueueStoragePort {
    private val logger = LoggerFactory.getLogger(DataSourceQueueStoragePort::class.java)

    override fun enqueue(command: QueueCommand): Boolean {
        return dataSource.connection.use { conn ->
            JdbcQueueStoragePort(conn).enqueue(command)
        }
    }

    override fun nextPending(cashboxId: String, lane: QueueLane, now: Long): QueueCommand? {
        return dataSource.connection.use { conn ->
            JdbcQueueStoragePort(conn).nextPending(cashboxId, lane, now)
        }
    }

    override fun updateStatus(
        id: String,
        status: QueueStatus,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean {
        return dataSource.connection.use { conn ->
            JdbcQueueStoragePort(conn).updateStatus(id, status, attempt, lastError, nextAttemptAt)
        }
    }

    override fun markInProgress(id: String, now: Long): Boolean {
        return dataSource.connection.use { conn ->
            JdbcQueueStoragePort(conn).markInProgress(id, now)
        }
    }

    override fun listByCashbox(
        cashboxId: String,
        lane: QueueLane,
        limit: Int,
        offset: Int
    ): List<QueueCommand> {
        return dataSource.connection.use { conn ->
            JdbcQueueStoragePort(conn).listByCashbox(cashboxId, lane, limit, offset)
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        return dataSource.connection.use { conn ->
            JdbcQueueStoragePort(conn).deleteByCashbox(cashboxId)
        }
    }
}
