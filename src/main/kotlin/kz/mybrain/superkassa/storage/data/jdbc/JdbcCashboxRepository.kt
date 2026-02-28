package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.CashboxRecord
import kz.mybrain.superkassa.storage.domain.repository.CashboxRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория касс.
 */
class JdbcCashboxRepository(
    private val connection: Connection
) : CashboxRepository {
    override fun insert(record: CashboxRecord): Boolean {
        val sql = """
            INSERT INTO cashbox (
                id, created_at, updated_at, mode, state, ofd_provider, registration_number,
                factory_number, manufacture_year, system_id, ofd_service_info, token_enc, token_updated_at,
                last_shift_no, last_receipt_no, last_z_report_no, autonomous_since, auto_close_shift, last_fiscal_hash,
                tax_regime, default_vat_group
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setLong(2, record.createdAt)
            stmt.setLong(3, record.updatedAt)
            stmt.setString(4, record.mode)
            stmt.setString(5, record.state)
            stmt.bindString(6, record.ofdProvider)
            stmt.bindString(7, record.registrationNumber)
            stmt.bindString(8, record.factoryNumber)
            stmt.bindInt(9, record.manufactureYear)
            stmt.bindString(10, record.systemId)
            stmt.bindString(11, record.ofdServiceInfoJson)
            stmt.bindBytes(12, record.tokenEncrypted)
            stmt.bindLong(13, record.tokenUpdatedAt)
            stmt.bindInt(14, record.lastShiftNo)
            stmt.bindInt(15, record.lastReceiptNo)
            stmt.bindInt(16, record.lastZReportNo)
            stmt.bindLong(17, record.autonomousSince)
            stmt.setBoolean(18, record.autoCloseShift)
            stmt.bindBytes(19, record.lastFiscalHash)
            stmt.bindString(20, record.taxRegime)
            stmt.bindString(21, record.defaultVatGroup)
            return stmt.executeUpdate() == 1
        }
    }

    override fun update(record: CashboxRecord): Boolean {
        val sql = """
            UPDATE cashbox SET
                updated_at = ?,
                mode = ?,
                state = ?,
                ofd_provider = ?,
                registration_number = ?,
                factory_number = ?,
                manufacture_year = ?,
                system_id = ?,
                ofd_service_info = ?,
                token_enc = ?,
                token_updated_at = ?,
                last_shift_no = ?,
                last_receipt_no = ?,
                last_z_report_no = ?,
                autonomous_since = ?,
                auto_close_shift = ?,
                last_fiscal_hash = ?,
                tax_regime = ?,
                default_vat_group = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, record.updatedAt)
            stmt.setString(2, record.mode)
            stmt.setString(3, record.state)
            stmt.bindString(4, record.ofdProvider)
            stmt.bindString(5, record.registrationNumber)
            stmt.bindString(6, record.factoryNumber)
            stmt.bindInt(7, record.manufactureYear)
            stmt.bindString(8, record.systemId)
            stmt.bindString(9, record.ofdServiceInfoJson)
            stmt.bindBytes(10, record.tokenEncrypted)
            stmt.bindLong(11, record.tokenUpdatedAt)
            stmt.bindInt(12, record.lastShiftNo)
            stmt.bindInt(13, record.lastReceiptNo)
            stmt.bindInt(14, record.lastZReportNo)
            stmt.bindLong(15, record.autonomousSince)
            stmt.setBoolean(16, record.autoCloseShift)
            stmt.bindBytes(17, record.lastFiscalHash)
            stmt.bindString(18, record.taxRegime)
            stmt.bindString(19, record.defaultVatGroup)
            stmt.setString(20, record.id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findById(id: String): CashboxRecord? {
        val sql = "SELECT * FROM cashbox WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun findByRegistrationNumber(registrationNumber: String): CashboxRecord? {
        val sql = "SELECT * FROM cashbox WHERE registration_number = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, registrationNumber)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun findBySystemId(systemId: String): CashboxRecord? {
        val sql = "SELECT * FROM cashbox WHERE system_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, systemId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun listAll(limit: Int, offset: Int): List<CashboxRecord> {
        val sql = """
            SELECT * FROM cashbox
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, limit)
            stmt.setInt(2, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<CashboxRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun updateToken(id: String, tokenEncrypted: ByteArray, tokenUpdatedAt: Long): Boolean {
        val sql = """
            UPDATE cashbox SET
                token_enc = ?,
                token_updated_at = ?,
                updated_at = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setBytes(1, tokenEncrypted)
            stmt.setLong(2, tokenUpdatedAt)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.setString(4, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteById(id: String): Boolean {
        val sql = "DELETE FROM cashbox WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() == 1
        }
    }

    private fun mapRecord(rs: ResultSet): CashboxRecord {
        return CashboxRecord(
            id = rs.getString("id"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at"),
            mode = rs.getString("mode"),
            state = rs.getString("state"),
            ofdProvider = rs.getString("ofd_provider"),
            registrationNumber = rs.getString("registration_number"),
            factoryNumber = rs.getString("factory_number"),
            manufactureYear = rs.getInt("manufacture_year").takeIf { !rs.wasNull() },
            systemId = rs.getString("system_id"),
            ofdServiceInfoJson = rs.getString("ofd_service_info"),
            tokenEncrypted = rs.getBytes("token_enc"),
            tokenUpdatedAt = rs.getLong("token_updated_at").takeIf { !rs.wasNull() },
            lastShiftNo = rs.getInt("last_shift_no").takeIf { !rs.wasNull() },
            lastReceiptNo = rs.getInt("last_receipt_no").takeIf { !rs.wasNull() },
            lastZReportNo = rs.getInt("last_z_report_no").takeIf { !rs.wasNull() },
            autonomousSince = rs.getLong("autonomous_since").takeIf { !rs.wasNull() },
            autoCloseShift = rs.getBoolean("auto_close_shift"),
            lastFiscalHash = rs.getBytes("last_fiscal_hash"),
            taxRegime = rs.getString("tax_regime"),
            defaultVatGroup = rs.getString("default_vat_group")
        )
    }

    override fun listAllFiltered(
        limit: Int,
        offset: Int,
        state: String?,
        search: String?,
        sortBy: String,
        sortOrder: String
    ): List<CashboxRecord> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        
        if (state != null) {
            conditions.add("state = ?")
            params.add(state)
        }
        
        if (search != null && search.isNotBlank()) {
            conditions.add("registration_number LIKE ?")
            params.add("%$search%")
        }
        
        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        
        val orderField = when (sortBy) {
            "createdAt" -> "created_at"
            "updatedAt" -> "updated_at"
            "state" -> "state"
            "registrationNumber" -> "registration_number"
            else -> "created_at"
        }
        
        val sql = """
            SELECT * FROM cashbox
            $whereClause
            ORDER BY $orderField $sortOrder
            LIMIT ? OFFSET ?
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            var paramIndex = 1
            for (param in params) {
                when (param) {
                    is String -> stmt.setString(paramIndex++, param)
                    is Int -> stmt.setInt(paramIndex++, param)
                }
            }
            stmt.setInt(paramIndex++, limit)
            stmt.setInt(paramIndex, offset)
            
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<CashboxRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }
    
    override fun countAll(state: String?, search: String?): Int {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        
        if (state != null) {
            conditions.add("state = ?")
            params.add(state)
        }
        
        if (search != null && search.isNotBlank()) {
            conditions.add("registration_number LIKE ?")
            params.add("%$search%")
        }
        
        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        
        val sql = "SELECT COUNT(*) FROM cashbox $whereClause"
        
        connection.prepareStatement(sql).use { stmt ->
            var paramIndex = 1
            for (param in params) {
                when (param) {
                    is String -> stmt.setString(paramIndex++, param)
                    is Int -> stmt.setInt(paramIndex++, param)
                }
            }
            
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }
}
