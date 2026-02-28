package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.connector.StorageConnector
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для SQLite.
 */
class SqliteConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.SQLITE

    override fun connect(config: StorageConfig): Connection {
        return JdbcSupport.openConnection(config, "org.sqlite.JDBC")
    }
}
