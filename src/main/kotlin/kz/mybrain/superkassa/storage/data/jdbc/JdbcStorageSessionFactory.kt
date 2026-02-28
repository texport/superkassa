package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.application.session.StorageSessionFactory
import java.sql.Connection

/**
 * JDBC-фабрика сессий хранения.
 */
class JdbcStorageSessionFactory : StorageSessionFactory {
    override fun open(connection: Connection): StorageSession {
        return JdbcStorageSession(connection)
    }
}
