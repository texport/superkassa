package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.OfflineQueueItem
import kz.mybrain.superkassa.storage.domain.repository.OfflineQueueRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория автономной очереди.
 */
class JdbcOfflineQueueRepository(
    private val connection: Connection
) : OfflineQueueRepository {
    override fun enqueue(item: OfflineQueueItem): Boolean {
        val sql = """
            INSERT INTO offline_queue (
                id, cashbox_id, sequence_no, operation_type, payload_ref,
                created_at, status, attempt, last_error, next_attempt_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, item.id)
            stmt.setString(2, item.cashboxId)
            stmt.setLong(3, item.sequenceNo)
            stmt.setString(4, item.operationType)
            stmt.setString(5, item.payloadRef)
            stmt.setLong(6, item.createdAt)
            stmt.setString(7, item.status)
            stmt.setInt(8, item.attempt)
            stmt.bindString(9, item.lastError)
            stmt.bindLong(10, item.nextAttemptAt)
            return stmt.executeUpdate() == 1
        }
    }

    override fun nextPending(cashboxId: String): OfflineQueueItem? {
        val sql = """
            SELECT * FROM offline_queue
            WHERE cashbox_id = ? AND status = 'PENDING'
            ORDER BY sequence_no ASC
            LIMIT 1
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun updateAttempt(
        id: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?,
        status: String
    ): Boolean {
        val sql = """
            UPDATE offline_queue SET
                attempt = ?,
                last_error = ?,
                next_attempt_at = ?,
                status = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, attempt)
            stmt.bindString(2, lastError)
            stmt.bindLong(3, nextAttemptAt)
            stmt.setString(4, status)
            stmt.setString(5, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun markCompleted(id: String, status: String): Boolean {
        val sql = "UPDATE offline_queue SET status = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status)
            stmt.setString(2, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun listByCashbox(cashboxId: String, limit: Int, offset: Int): List<OfflineQueueItem> {
        val sql = """
            SELECT * FROM offline_queue
            WHERE cashbox_id = ?
            ORDER BY sequence_no ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<OfflineQueueItem>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteById(id: String): Boolean {
        val sql = "DELETE FROM offline_queue WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM offline_queue WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    override fun countAll(): Long {
        val sql = "SELECT COUNT(*) FROM offline_queue"
        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    private fun mapRecord(rs: ResultSet): OfflineQueueItem {
        return OfflineQueueItem(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            sequenceNo = rs.getLong("sequence_no"),
            operationType = rs.getString("operation_type"),
            payloadRef = rs.getString("payload_ref"),
            createdAt = rs.getLong("created_at"),
            status = rs.getString("status"),
            attempt = rs.getInt("attempt"),
            lastError = rs.getString("last_error"),
            nextAttemptAt = rs.getLong("next_attempt_at").takeIf { !rs.wasNull() }
        )
    }
}
