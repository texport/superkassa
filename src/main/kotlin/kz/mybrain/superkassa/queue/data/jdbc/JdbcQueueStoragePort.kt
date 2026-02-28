package kz.mybrain.superkassa.queue.data.jdbc

import kz.mybrain.superkassa.queue.domain.model.QueueCommand
import kz.mybrain.superkassa.queue.domain.model.QueueLane
import kz.mybrain.superkassa.queue.domain.model.QueueStatus
import kz.mybrain.superkassa.queue.domain.port.QueueStoragePort
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import org.slf4j.LoggerFactory

/**
 * JDBC-реализация хранения очереди команд.
 * Используется в режиме POWERMOVE для распределённой обработки очереди.
 */
class JdbcQueueStoragePort(
    private val connection: Connection
) : QueueStoragePort {
    private val logger = LoggerFactory.getLogger(JdbcQueueStoragePort::class.java)

    override fun enqueue(command: QueueCommand): Boolean {
        val sql = """
            INSERT INTO queue_task (
                id, cashbox_id, lane, type, payload_ref, created_at,
                status, attempt, next_attempt_at, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, command.id)
                stmt.setString(2, command.cashboxId)
                stmt.setString(3, command.lane.name)
                stmt.setString(4, command.type.name)
                stmt.setString(5, command.payloadRef)
                stmt.setLong(6, command.createdAt)
                stmt.setString(7, command.status.name)
                stmt.setInt(8, command.attempt)
                stmt.setObject(9, command.nextAttemptAt?.let { java.lang.Long.valueOf(it) })
                stmt.setString(10, command.lastError)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to enqueue command: ${command.id}", ex)
            false
        }
    }

    override fun nextPending(cashboxId: String, lane: QueueLane, now: Long): QueueCommand? {
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
                stmt.setString(2, lane.name)
                stmt.setLong(3, now)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        mapRecord(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (ex: SQLException) {
            logger.error("Failed to get next pending command for cashbox: $cashboxId", ex)
            null
        }
    }

    override fun updateStatus(
        id: String,
        status: QueueStatus,
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
                stmt.setString(1, status.name)
                stmt.setInt(2, attempt)
                stmt.setString(3, lastError)
                stmt.setObject(4, nextAttemptAt?.let { java.lang.Long.valueOf(it) })
                stmt.setString(5, id)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to update status for command: $id", ex)
            false
        }
    }

    override fun markInProgress(id: String, now: Long): Boolean {
        val sql = """
            UPDATE queue_task SET status = 'IN_PROGRESS'
            WHERE id = ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to mark command as in progress: $id", ex)
            false
        }
    }

    override fun listByCashbox(
        cashboxId: String,
        lane: QueueLane,
        limit: Int,
        offset: Int
    ): List<QueueCommand> {
        val sql = """
            SELECT * FROM queue_task
            WHERE cashbox_id = ? AND lane = ?
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.setString(2, lane.name)
                stmt.setInt(3, limit)
                stmt.setInt(4, offset)
                stmt.executeQuery().use { rs ->
                    val commands = mutableListOf<QueueCommand>()
                    while (rs.next()) {
                        commands.add(mapRecord(rs))
                    }
                    commands
                }
            }
        } catch (ex: SQLException) {
            logger.error("Failed to list commands for cashbox: $cashboxId", ex)
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
            logger.error("Failed to delete commands for cashbox: $cashboxId", ex)
            false
        }
    }

    private fun mapRecord(rs: ResultSet): QueueCommand {
        return QueueCommand(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            lane = QueueLane.valueOf(rs.getString("lane")),
            type = kz.mybrain.superkassa.queue.domain.model.QueueCommandType.valueOf(rs.getString("type")),
            payloadRef = rs.getString("payload_ref"),
            createdAt = rs.getLong("created_at"),
            status = QueueStatus.valueOf(rs.getString("status")),
            attempt = rs.getInt("attempt"),
            nextAttemptAt = rs.getObject("next_attempt_at")?.let { rs.getLong("next_attempt_at") },
            lastError = rs.getString("last_error")
        )
    }
}
