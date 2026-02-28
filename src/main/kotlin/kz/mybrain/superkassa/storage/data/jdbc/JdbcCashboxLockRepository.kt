package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.CashboxLock
import kz.mybrain.superkassa.storage.domain.repository.CashboxLockRepository
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/**
 * JDBC-реализация lease/lock для кассы.
 */
class JdbcCashboxLockRepository(
    private val connection: Connection
) : CashboxLockRepository {
    override fun tryAcquire(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        val insertSql = """
            INSERT INTO cashbox_lock (cashbox_id, owner_id, lease_until, acquired_at)
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
            val updateSql = """
                UPDATE cashbox_lock SET
                    owner_id = ?,
                    lease_until = ?,
                    acquired_at = ?
                WHERE cashbox_id = ? AND lease_until < ?
            """.trimIndent()
            connection.prepareStatement(updateSql).use { stmt ->
                stmt.setString(1, ownerId)
                stmt.setLong(2, leaseUntil)
                stmt.setLong(3, now)
                stmt.setString(4, cashboxId)
                stmt.setLong(5, now)
                stmt.executeUpdate() == 1
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
            UPDATE cashbox_lock SET
                lease_until = ?
            WHERE cashbox_id = ? AND owner_id = ? AND lease_until >= ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, leaseUntil)
            stmt.setString(2, cashboxId)
            stmt.setString(3, ownerId)
            stmt.setLong(4, now)
            return stmt.executeUpdate() == 1
        }
    }

    override fun release(cashboxId: String, ownerId: String): Boolean {
        val sql = "DELETE FROM cashbox_lock WHERE cashbox_id = ? AND owner_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, ownerId)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findByCashboxId(cashboxId: String): CashboxLock? {
        val sql = "SELECT * FROM cashbox_lock WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM cashbox_lock WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): CashboxLock {
        return CashboxLock(
            cashboxId = rs.getString("cashbox_id"),
            ownerId = rs.getString("owner_id"),
            leaseUntil = rs.getLong("lease_until"),
            acquiredAt = rs.getLong("acquired_at")
        )
    }
}
