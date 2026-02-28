package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.OfdMessageRecord
import kz.mybrain.superkassa.storage.domain.repository.OfdMessageRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория сообщений ОФД.
 */
class JdbcOfdMessageRepository(
    private val connection: Connection
) : OfdMessageRepository {
    override fun insert(record: OfdMessageRecord): Boolean {
        val sql = """
            INSERT INTO ofd_message (
                id, cashbox_id, command, request_bin, response_bin, status, attempt,
                error_code, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setString(2, record.cashboxId)
            stmt.setString(3, record.command)
            stmt.bindBytes(4, record.requestBin)
            stmt.bindBytes(5, record.responseBin)
            stmt.setString(6, record.status)
            stmt.setInt(7, record.attempt)
            stmt.bindString(8, record.errorCode)
            stmt.setLong(9, record.createdAt)
            stmt.setLong(10, record.updatedAt)
            return stmt.executeUpdate() == 1
        }
    }

    override fun updateResponse(
        id: String,
        responseBin: ByteArray?,
        status: String,
        attempt: Int,
        errorCode: String?,
        updatedAt: Long
    ): Boolean {
        val sql = """
            UPDATE ofd_message SET
                response_bin = ?,
                status = ?,
                attempt = ?,
                error_code = ?,
                updated_at = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.bindBytes(1, responseBin)
            stmt.setString(2, status)
            stmt.setInt(3, attempt)
            stmt.bindString(4, errorCode)
            stmt.setLong(5, updatedAt)
            stmt.setString(6, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findById(id: String): OfdMessageRecord? {
        val sql = "SELECT * FROM ofd_message WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun listPending(cashboxId: String, limit: Int): List<OfdMessageRecord> {
        val sql = """
            SELECT * FROM ofd_message
            WHERE cashbox_id = ? AND status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<OfdMessageRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun listByCashbox(cashboxId: String, limit: Int, offset: Int): List<OfdMessageRecord> {
        val sql = """
            SELECT * FROM ofd_message
            WHERE cashbox_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<OfdMessageRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteById(id: String): Boolean {
        val sql = "DELETE FROM ofd_message WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM ofd_message WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): OfdMessageRecord {
        return OfdMessageRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            command = rs.getString("command"),
            requestBin = rs.getBytes("request_bin"),
            responseBin = rs.getBytes("response_bin"),
            status = rs.getString("status"),
            attempt = rs.getInt("attempt"),
            errorCode = rs.getString("error_code"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }
}
