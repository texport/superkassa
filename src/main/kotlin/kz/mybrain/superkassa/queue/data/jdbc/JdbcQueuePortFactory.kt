package kz.mybrain.superkassa.queue.data.jdbc

import kz.mybrain.superkassa.queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.queue.domain.port.QueueStoragePort
import kz.mybrain.superkassa.storage.application.connector.StorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import java.sql.Connection
import org.slf4j.LoggerFactory

/**
 * Фабрика для создания JDBC-реализаций портов очереди.
 * Используется в режиме POWERMOVE для распределённой обработки очереди.
 */
class JdbcQueuePortFactory(
    private val connectorRegistry: StorageConnectorRegistry,
    private val storageConfig: StorageConfig
) {
    private val logger = LoggerFactory.getLogger(JdbcQueuePortFactory::class.java)

    /**
     * Создаёт JDBC-реализацию QueueStoragePort.
     * Внимание: Connection должен управляться вызывающим кодом (закрываться после использования).
     * Для production рекомендуется использовать DataSource с пулом соединений.
     */
    fun createQueueStoragePort(connection: Connection): QueueStoragePort {
        return JdbcQueueStoragePort(connection)
    }

    /**
     * Создаёт JDBC-реализацию LeaseLockPort.
     * Внимание: Connection должен управляться вызывающим кодом.
     */
    fun createLeaseLockPort(connection: Connection): LeaseLockPort {
        return JdbcLeaseLockPort(connection)
    }

    /**
     * Создаёт новое Connection для использования в queue портах.
     * Внимание: вызывающий код должен закрывать Connection после использования.
     */
    fun createConnection(): Connection {
        val engine = storageConfig.resolvedEngine()
        logger.debug("Creating connection for queue ports. engine={}", engine)
        return connectorRegistry.connectorFor(engine).connect(storageConfig)
    }
}
