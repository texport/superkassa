package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.FiscalJournalRecord
import kz.mybrain.superkassa.storage.domain.repository.FiscalJournalRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория фискального журнала.
 */
class JdbcFiscalJournalRepository(
    private val connection: Connection
) : FiscalJournalRepository {
    override fun append(record: FiscalJournalRecord): Boolean {
        val sql = """
            INSERT INTO fiscal_journal (
                id, cashbox_id, record_type, record_ref, created_at, prev_hash, record_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setString(2, record.cashboxId)
            stmt.setString(3, record.recordType)
            stmt.setString(4, record.recordRef)
            stmt.setLong(5, record.createdAt)
            stmt.bindBytes(6, record.prevHash)
            stmt.bindBytes(7, record.recordHash)
            return stmt.executeUpdate() == 1
        }
    }

    override fun listByCashbox(cashboxId: String, limit: Int): List<FiscalJournalRecord> {
        val sql = """
            SELECT * FROM fiscal_journal
            WHERE cashbox_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<FiscalJournalRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun lastHash(cashboxId: String): ByteArray? {
        val sql = """
            SELECT record_hash FROM fiscal_journal
            WHERE cashbox_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getBytes("record_hash") else null
            }
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM fiscal_journal WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): FiscalJournalRecord {
        return FiscalJournalRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            recordType = rs.getString("record_type"),
            recordRef = rs.getString("record_ref"),
            createdAt = rs.getLong("created_at"),
            prevHash = rs.getBytes("prev_hash"),
            recordHash = rs.getBytes("record_hash")
        )
    }
}
