package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.CounterRecord
import kz.mybrain.superkassa.storage.domain.repository.CounterRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория счетчиков.
 */
class JdbcCounterRepository(
    private val connection: Connection
) : CounterRepository {
    override fun upsert(record: CounterRecord): Boolean {
        val deleteSql = if (record.shiftId == null) {
            """
                DELETE FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id IS NULL AND counter_key = ?
            """.trimIndent()
        } else {
            """
                DELETE FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id = ? AND counter_key = ?
            """.trimIndent()
        }
        connection.prepareStatement(deleteSql).use { stmt ->
            stmt.setString(1, record.cashboxId)
            stmt.setString(2, record.scope)
            if (record.shiftId == null) {
                stmt.setString(3, record.key)
            } else {
                stmt.setString(3, record.shiftId)
                stmt.setString(4, record.key)
            }
            stmt.executeUpdate()
        }
        val insertSql = """
            INSERT INTO counter (
                cashbox_id, scope, shift_id, counter_key, value, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(insertSql).use { stmt ->
            stmt.setString(1, record.cashboxId)
            stmt.setString(2, record.scope)
            stmt.bindString(3, record.shiftId)
            stmt.setString(4, record.key)
            stmt.setLong(5, record.value)
            stmt.setLong(6, record.updatedAt)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findByKey(
        cashboxId: String,
        scope: String,
        shiftId: String?,
        key: String
    ): CounterRecord? {
        val sql = if (shiftId == null) {
            """
                SELECT * FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id IS NULL AND counter_key = ?
            """.trimIndent()
        } else {
            """
                SELECT * FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id = ? AND counter_key = ?
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, scope)
            if (shiftId == null) {
                stmt.setString(3, key)
            } else {
                stmt.setString(3, shiftId)
                stmt.setString(4, key)
            }
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun listByScope(
        cashboxId: String,
        scope: String,
        shiftId: String?
    ): List<CounterRecord> {
        val sql = if (shiftId == null) {
            """
                SELECT * FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id IS NULL
                ORDER BY counter_key ASC
            """.trimIndent()
        } else {
            """
                SELECT * FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id = ?
                ORDER BY counter_key ASC
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, scope)
            if (shiftId != null) {
                stmt.setString(3, shiftId)
            }
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<CounterRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun listByCashbox(cashboxId: String): List<CounterRecord> {
        val sql = """
            SELECT * FROM counter
            WHERE cashbox_id = ?
            ORDER BY scope ASC, shift_id ASC, counter_key ASC
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<CounterRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteByKey(
        cashboxId: String,
        scope: String,
        shiftId: String?,
        key: String
    ): Boolean {
        val sql = if (shiftId == null) {
            """
                DELETE FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id IS NULL AND counter_key = ?
            """.trimIndent()
        } else {
            """
                DELETE FROM counter
                WHERE cashbox_id = ? AND scope = ? AND shift_id = ? AND counter_key = ?
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, scope)
            if (shiftId == null) {
                stmt.setString(3, key)
            } else {
                stmt.setString(3, shiftId)
                stmt.setString(4, key)
            }
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM counter WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    private fun mapRecord(rs: ResultSet): CounterRecord {
        return CounterRecord(
            cashboxId = rs.getString("cashbox_id"),
            scope = rs.getString("scope"),
            shiftId = rs.getString("shift_id"),
            key = rs.getString("counter_key"),
            value = rs.getLong("value"),
            updatedAt = rs.getLong("updated_at")
        )
    }
}
