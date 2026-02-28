package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.ErrorMessageRecord
import kz.mybrain.superkassa.storage.domain.repository.ErrorMessageRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория централизованных ошибок.
 */
class JdbcErrorMessageRepository(
    private val connection: Connection
) : ErrorMessageRepository {
    override fun insert(record: ErrorMessageRecord): Boolean {
        val sql = """
            INSERT INTO error_log (
                id, created_at, component, code, message_ru, message_en,
                severity, cashbox_id, operation_id, details
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setLong(2, record.createdAt)
            stmt.setString(3, record.component)
            stmt.setString(4, record.code)
            stmt.setString(5, record.messageRu)
            stmt.setString(6, record.messageEn)
            stmt.setString(7, record.severity)
            stmt.bindString(8, record.cashboxId)
            stmt.bindString(9, record.operationId)
            stmt.bindString(10, record.details)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findById(id: String): ErrorMessageRecord? {
        val sql = "SELECT * FROM error_log WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun listRecent(limit: Int): List<ErrorMessageRecord> {
        val sql = """
            SELECT * FROM error_log
            ORDER BY created_at DESC
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, limit)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ErrorMessageRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun listByCashbox(cashboxId: String, limit: Int): List<ErrorMessageRecord> {
        val sql = """
            SELECT * FROM error_log
            WHERE cashbox_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ErrorMessageRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteById(id: String): Boolean {
        val sql = "DELETE FROM error_log WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM error_log WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): ErrorMessageRecord {
        return ErrorMessageRecord(
            id = rs.getString("id"),
            createdAt = rs.getLong("created_at"),
            component = rs.getString("component"),
            code = rs.getString("code"),
            messageRu = rs.getString("message_ru"),
            messageEn = rs.getString("message_en"),
            severity = rs.getString("severity"),
            cashboxId = rs.getString("cashbox_id"),
            operationId = rs.getString("operation_id"),
            details = rs.getString("details")
        )
    }
}
