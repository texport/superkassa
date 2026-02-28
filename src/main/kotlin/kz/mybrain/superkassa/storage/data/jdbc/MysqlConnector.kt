package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.connector.StorageConnector
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для MySQL.
 */
class MysqlConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.MYSQL

    override fun connect(config: StorageConfig): Connection {
        return JdbcSupport.openConnection(config, "com.mysql.cj.jdbc.Driver")
    }
}
