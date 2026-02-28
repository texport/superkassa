package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.ShiftRecord
import kz.mybrain.superkassa.storage.domain.repository.ShiftRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория смен.
 */
class JdbcShiftRepository(
    private val connection: Connection
) : ShiftRepository {
    override fun insert(record: ShiftRecord): Boolean {
        val sql = """
            INSERT INTO shift (
                id, cashbox_id, shift_no, status, opened_at, closed_at,
                open_document_id, close_document_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setString(2, record.cashboxId)
            stmt.setLong(3, record.shiftNo)
            stmt.setString(4, record.status)
            stmt.setLong(5, record.openedAt)
            stmt.bindLong(6, record.closedAt)
            stmt.bindString(7, record.openDocumentId)
            stmt.bindString(8, record.closeDocumentId)
            return stmt.executeUpdate() == 1
        }
    }

    override fun updateClose(
        id: String,
        status: String,
        closedAt: Long,
        closeDocumentId: String?
    ): Boolean {
        val sql = """
            UPDATE shift SET
                status = ?,
                closed_at = ?,
                close_document_id = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status)
            stmt.setLong(2, closedAt)
            stmt.bindString(3, closeDocumentId)
            stmt.setString(4, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findById(id: String): ShiftRecord? {
        val sql = "SELECT * FROM shift WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun findByShiftNo(cashboxId: String, shiftNo: Long): ShiftRecord? {
        val sql = """
            SELECT * FROM shift
            WHERE cashbox_id = ? AND shift_no = ?
            ORDER BY opened_at DESC
            LIMIT 1
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setLong(2, shiftNo)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun findOpenByCashbox(cashboxId: String): ShiftRecord? {
        val sql = """
            SELECT * FROM shift
            WHERE cashbox_id = ? AND status = 'OPEN'
            ORDER BY opened_at DESC
            LIMIT 1
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun listByCashbox(cashboxId: String, limit: Int, offset: Int): List<ShiftRecord> {
        val sql = """
            SELECT * FROM shift
            WHERE cashbox_id = ?
            ORDER BY opened_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ShiftRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM shift WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    override fun countAll(status: String?): Long {
        val sql = if (status != null) {
            "SELECT COUNT(*) FROM shift WHERE status = ?"
        } else {
            "SELECT COUNT(*) FROM shift"
        }
        connection.prepareStatement(sql).use { stmt ->
            if (status != null) stmt.setString(1, status)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    private fun mapRecord(rs: ResultSet): ShiftRecord {
        return ShiftRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            shiftNo = rs.getLong("shift_no"),
            status = rs.getString("status"),
            openedAt = rs.getLong("opened_at"),
            closedAt = rs.getLong("closed_at").takeIf { !rs.wasNull() },
            openDocumentId = rs.getString("open_document_id"),
            closeDocumentId = rs.getString("close_document_id")
        )
    }
}
