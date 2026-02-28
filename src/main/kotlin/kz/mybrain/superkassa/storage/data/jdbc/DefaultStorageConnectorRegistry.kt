package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.connector.StorageConnector
import kz.mybrain.superkassa.storage.application.connector.StorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageEngine

/**
 * Реестр коннекторов по умолчанию.
 */
class DefaultStorageConnectorRegistry(
    connectors: List<StorageConnector> = listOf(
        SqliteConnector(),
        PostgresConnector(),
        MysqlConnector()
    )
) : StorageConnectorRegistry {
    private val connectorByEngine = connectors.associateBy { it.engine }

    override fun connectorFor(engine: StorageEngine): StorageConnector {
        return connectorByEngine[engine]
            ?: error("No connector registered for engine: $engine")
    }
}
