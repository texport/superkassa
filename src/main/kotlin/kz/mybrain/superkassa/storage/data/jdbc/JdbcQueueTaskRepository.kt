package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.QueueTaskRecord
import kz.mybrain.superkassa.storage.domain.repository.QueueTaskRepository
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import org.slf4j.LoggerFactory

/**
 * JDBC-реализация репозитория очереди команд (queue_task).
 */
class JdbcQueueTaskRepository(
    private val connection: Connection
) : QueueTaskRepository {
    private val logger = LoggerFactory.getLogger(JdbcQueueTaskRepository::class.java)

    override fun enqueue(record: QueueTaskRecord): Boolean {
        val sql = """
            INSERT INTO queue_task (
                id, cashbox_id, lane, type, payload_ref, created_at,
                status, attempt, next_attempt_at, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, record.id)
                stmt.setString(2, record.cashboxId)
                stmt.setString(3, record.lane)
                stmt.setString(4, record.type)
                stmt.setString(5, record.payloadRef)
                stmt.setLong(6, record.createdAt)
                stmt.setString(7, record.status)
                stmt.setInt(8, record.attempt)
                stmt.setObject(9, record.nextAttemptAt?.let { java.lang.Long.valueOf(it) })
                stmt.setString(10, record.lastError)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to enqueue queue task: ${record.id}", ex)
            false
        }
    }

    override fun nextPending(cashboxId: String, lane: String, now: Long): QueueTaskRecord? {
        val sql = """
            SELECT * FROM queue_task
            WHERE cashbox_id = ? AND lane = ?
                AND status IN ('PENDING', 'FAILED')
                AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
            ORDER BY created_at ASC
            LIMIT 1
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.setString(2, lane)
                stmt.setLong(3, now)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) mapRecord(rs) else null
                }
            }
        } catch (ex: SQLException) {
            logger.error("Failed to get next pending for cashbox: $cashboxId", ex)
            null
        }
    }

    override fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean {
        val sql = """
            UPDATE queue_task SET
                status = ?,
                attempt = ?,
                last_error = ?,
                next_attempt_at = ?
            WHERE id = ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status)
                stmt.setInt(2, attempt)
                stmt.setString(3, lastError)
                stmt.setObject(4, nextAttemptAt?.let { java.lang.Long.valueOf(it) })
                stmt.setString(5, id)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to update status for queue task: $id", ex)
            false
        }
    }

    override fun markInProgress(id: String, now: Long): Boolean {
        val sql = "UPDATE queue_task SET status = 'IN_PROGRESS' WHERE id = ?"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to mark in progress: $id", ex)
            false
        }
    }

    override fun listByCashbox(cashboxId: String, lane: String, limit: Int, offset: Int): List<QueueTaskRecord> {
        val sql = """
            SELECT * FROM queue_task
            WHERE cashbox_id = ? AND lane = ?
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.setString(2, lane)
                stmt.setInt(3, limit)
                stmt.setInt(4, offset)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<QueueTaskRecord>()
                    while (rs.next()) {
                        result.add(mapRecord(rs))
                    }
                    result
                }
            }
        } catch (ex: SQLException) {
            logger.error("Failed to list queue tasks for cashbox: $cashboxId", ex)
            emptyList()
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM queue_task WHERE cashbox_id = ?"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.executeUpdate()
                true
            }
        } catch (ex: SQLException) {
            logger.error("Failed to delete queue tasks for cashbox: $cashboxId", ex)
            false
        }
    }

    override fun countPendingByLane(lane: String): Long {
        val sql = "SELECT COUNT(*) FROM queue_task WHERE lane = ? AND status IN ('PENDING', 'FAILED', 'IN_PROGRESS')"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, lane)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        } catch (ex: SQLException) {
            logger.error("Failed to count pending queue tasks for lane: $lane", ex)
            0L
        }
    }

    private fun mapRecord(rs: ResultSet): QueueTaskRecord {
        return QueueTaskRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            lane = rs.getString("lane"),
            type = rs.getString("type"),
            payloadRef = rs.getString("payload_ref"),
            createdAt = rs.getLong("created_at"),
            status = rs.getString("status"),
            attempt = rs.getInt("attempt"),
            nextAttemptAt = rs.getObject("next_attempt_at")?.let { rs.getLong("next_attempt_at") },
            lastError = rs.getString("last_error")
        )
    }
}
