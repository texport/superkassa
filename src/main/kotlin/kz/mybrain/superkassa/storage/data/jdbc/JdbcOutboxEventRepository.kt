package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.OutboxEventRecord
import kz.mybrain.superkassa.storage.domain.repository.OutboxEventRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория outbox-событий.
 */
class JdbcOutboxEventRepository(
    private val connection: Connection
) : OutboxEventRepository {
    override fun insert(record: OutboxEventRecord): Boolean {
        val sql = """
            INSERT INTO outbox_event (
                id, cashbox_id, event_type, payload_bin, created_at,
                status, attempt, next_attempt_at, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setString(2, record.cashboxId)
            stmt.setString(3, record.eventType)
            stmt.setBytes(4, record.payloadBin)
            stmt.setLong(5, record.createdAt)
            stmt.setString(6, record.status)
            stmt.setInt(7, record.attempt)
            stmt.bindLong(8, record.nextAttemptAt)
            stmt.bindString(9, record.lastError)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findById(id: String): OutboxEventRecord? {
        val sql = "SELECT * FROM outbox_event WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun listPending(limit: Int): List<OutboxEventRecord> {
        val sql = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, limit)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<OutboxEventRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        nextAttemptAt: Long?,
        lastError: String?
    ): Boolean {
        val sql = """
            UPDATE outbox_event SET
                status = ?,
                attempt = ?,
                next_attempt_at = ?,
                last_error = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status)
            stmt.setInt(2, attempt)
            stmt.bindLong(3, nextAttemptAt)
            stmt.bindString(4, lastError)
            stmt.setString(5, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteById(id: String): Boolean {
        val sql = "DELETE FROM outbox_event WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM outbox_event WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): OutboxEventRecord {
        return OutboxEventRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            eventType = rs.getString("event_type"),
            payloadBin = rs.getBytes("payload_bin"),
            createdAt = rs.getLong("created_at"),
            status = rs.getString("status"),
            attempt = rs.getInt("attempt"),
            nextAttemptAt = rs.getLong("next_attempt_at").takeIf { !rs.wasNull() },
            lastError = rs.getString("last_error")
        )
    }
}
