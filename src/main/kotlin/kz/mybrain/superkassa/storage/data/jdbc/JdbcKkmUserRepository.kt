package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.KkmUserRecord
import kz.mybrain.superkassa.storage.domain.repository.KkmUserRepository
import java.sql.Connection

/**
 * JDBC-репозиторий пользователей ККМ.
 */
class JdbcKkmUserRepository(
    private val connection: Connection
) : KkmUserRepository {
    override fun insert(record: KkmUserRecord): Boolean {
        val sql = """
            INSERT INTO kkm_user (id, cashbox_id, name, role, pin, pin_hash, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setString(2, record.cashboxId)
            stmt.setString(3, record.name)
            stmt.setString(4, record.role)
            stmt.setString(5, record.pin)
            stmt.setString(6, record.pinHash)
            stmt.setLong(7, record.createdAt)
            return stmt.executeUpdate() == 1
        }
    }

    override fun update(
        cashboxId: String,
        userId: String,
        name: String?,
        role: String?,
        pin: String?,
        pinHash: String?
    ): Boolean {
        val sql = """
            UPDATE kkm_user
            SET name = COALESCE(?, name),
                role = COALESCE(?, role),
                pin = COALESCE(?, pin),
                pin_hash = COALESCE(?, pin_hash)
            WHERE cashbox_id = ? AND id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, role)
            stmt.setString(3, pin)
            stmt.setString(4, pinHash)
            stmt.setString(5, cashboxId)
            stmt.setString(6, userId)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteById(cashboxId: String, userId: String): Boolean {
        val sql = "DELETE FROM kkm_user WHERE cashbox_id = ? AND id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, userId)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM kkm_user WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    override fun listByCashbox(cashboxId: String): List<KkmUserRecord> {
        val sql = "SELECT * FROM kkm_user WHERE cashbox_id = ? ORDER BY created_at ASC"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<KkmUserRecord>()
                while (rs.next()) {
                    result.add(
                        KkmUserRecord(
                            id = rs.getString("id"),
                            cashboxId = rs.getString("cashbox_id"),
                            name = rs.getString("name"),
                            role = rs.getString("role"),
                            pin = rs.getString("pin"),
                            pinHash = rs.getString("pin_hash"),
                            createdAt = rs.getLong("created_at")
                        )
                    )
                }
                return result
            }
        }
    }

    override fun findById(cashboxId: String, userId: String): KkmUserRecord? {
        val sql = "SELECT * FROM kkm_user WHERE cashbox_id = ? AND id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, userId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    KkmUserRecord(
                        id = rs.getString("id"),
                        cashboxId = rs.getString("cashbox_id"),
                        name = rs.getString("name"),
                        role = rs.getString("role"),
                        pin = rs.getString("pin"),
                        pinHash = rs.getString("pin_hash"),
                        createdAt = rs.getLong("created_at")
                    )
                } else {
                    null
                }
            }
        }
    }

    override fun findByCashboxAndPinHash(cashboxId: String, pinHash: String): KkmUserRecord? {
        val sql = "SELECT * FROM kkm_user WHERE cashbox_id = ? AND pin_hash = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, pinHash)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    KkmUserRecord(
                        id = rs.getString("id"),
                        cashboxId = rs.getString("cashbox_id"),
                        name = rs.getString("name"),
                        role = rs.getString("role"),
                        pin = rs.getString("pin"),
                        pinHash = rs.getString("pin_hash"),
                        createdAt = rs.getLong("created_at")
                    )
                } else {
                    null
                }
            }
        }
    }
}
