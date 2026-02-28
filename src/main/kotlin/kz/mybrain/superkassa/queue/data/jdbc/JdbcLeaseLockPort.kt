package kz.mybrain.superkassa.queue.data.jdbc

import kz.mybrain.superkassa.queue.domain.port.LeaseLockPort
import java.sql.Connection
import java.sql.SQLException
import org.slf4j.LoggerFactory

/**
 * JDBC-реализация lease/lock для кассы при обработке очереди.
 * Используется в режиме POWERMOVE для распределённых блокировок.
 */
class JdbcLeaseLockPort(
    private val connection: Connection
) : LeaseLockPort {
    private val logger = LoggerFactory.getLogger(JdbcLeaseLockPort::class.java)

    override fun tryAcquire(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        // Пытаемся вставить новую запись
        val insertSql = """
            INSERT INTO queue_lock (cashbox_id, owner_id, lease_until, acquired_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        return try {
            connection.prepareStatement(insertSql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.setString(2, ownerId)
                stmt.setLong(3, leaseUntil)
                stmt.setLong(4, now)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            // Если запись уже существует, пытаемся обновить только если lease истёк
            val updateSql = """
                UPDATE queue_lock SET
                    owner_id = ?,
                    lease_until = ?,
                    acquired_at = ?
                WHERE cashbox_id = ? AND lease_until < ?
            """.trimIndent()
            try {
                connection.prepareStatement(updateSql).use { stmt ->
                    stmt.setString(1, ownerId)
                    stmt.setLong(2, leaseUntil)
                    stmt.setLong(3, now)
                    stmt.setString(4, cashboxId)
                    stmt.setLong(5, now)
                    stmt.executeUpdate() == 1
                }
            } catch (ex2: SQLException) {
                logger.debug("Failed to acquire lock for cashbox: $cashboxId", ex2)
                false
            }
        }
    }

    override fun renew(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        val sql = """
            UPDATE queue_lock SET
                lease_until = ?
            WHERE cashbox_id = ? AND owner_id = ? AND lease_until >= ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, leaseUntil)
                stmt.setString(2, cashboxId)
                stmt.setString(3, ownerId)
                stmt.setLong(4, now)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to renew lock for cashbox: $cashboxId", ex)
            false
        }
    }

    override fun release(cashboxId: String, ownerId: String): Boolean {
        val sql = "DELETE FROM queue_lock WHERE cashbox_id = ? AND owner_id = ?"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.setString(2, ownerId)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to release lock for cashbox: $cashboxId", ex)
            false
        }
    }
}
