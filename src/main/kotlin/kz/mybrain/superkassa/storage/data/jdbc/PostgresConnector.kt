package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.connector.StorageConnector
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для PostgreSQL.
 */
class PostgresConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.POSTGRES

    override fun connect(config: StorageConfig): Connection {
        return JdbcSupport.openConnection(config, "org.postgresql.Driver")
    }
}
