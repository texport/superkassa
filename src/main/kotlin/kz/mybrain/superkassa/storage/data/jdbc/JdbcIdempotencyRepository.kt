package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.IdempotencyRecord
import kz.mybrain.superkassa.storage.domain.repository.IdempotencyRepository
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/**
 * JDBC-реализация репозитория идемпотентности.
 */
class JdbcIdempotencyRepository(
    private val connection: Connection
) : IdempotencyRepository {
    override fun insertIfAbsent(record: IdempotencyRecord): Boolean {
        val sql = """
            INSERT INTO idempotency (
                idempotency_key, cashbox_id, operation, created_at, status, response_ref
            ) VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, record.idempotencyKey)
                stmt.setString(2, record.cashboxId)
                stmt.setString(3, record.operation)
                stmt.setLong(4, record.createdAt)
                stmt.setString(5, record.status)
                stmt.bindString(6, record.responseRef)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            false
        }
    }

    override fun findByKey(cashboxId: String, idempotencyKey: String): IdempotencyRecord? {
        val sql = "SELECT * FROM idempotency WHERE cashbox_id = ? AND idempotency_key = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, idempotencyKey)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun updateResponse(
        cashboxId: String,
        idempotencyKey: String,
        status: String,
        responseRef: String?
    ): Boolean {
        val sql = """
            UPDATE idempotency SET
                status = ?,
                response_ref = ?
            WHERE cashbox_id = ? AND idempotency_key = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status)
            stmt.bindString(2, responseRef)
            stmt.setString(3, cashboxId)
            stmt.setString(4, idempotencyKey)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByKey(cashboxId: String, idempotencyKey: String): Boolean {
        val sql = "DELETE FROM idempotency WHERE cashbox_id = ? AND idempotency_key = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, idempotencyKey)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM idempotency WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): IdempotencyRecord {
        return IdempotencyRecord(
            idempotencyKey = rs.getString("idempotency_key"),
            cashboxId = rs.getString("cashbox_id"),
            operation = rs.getString("operation"),
            createdAt = rs.getLong("created_at"),
            status = rs.getString("status"),
            responseRef = rs.getString("response_ref")
        )
    }
}
