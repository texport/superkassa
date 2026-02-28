package kz.mybrain.superkassa.storage.application.connector

import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.application.session.StorageSessionFactory
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import org.slf4j.LoggerFactory

/**
 * Точка входа для открытия сессии хранения.
 *
 * Зачем это нужно:
 * - выбрать нужный коннектор по движку БД,
 * - создать StorageSession через фабрику,
 * - скрыть детали подключения от бизнес-логики.
 */
class StorageManager(
    private val registry: StorageConnectorRegistry,
    private val sessionFactory: StorageSessionFactory
) {
    private val logger = LoggerFactory.getLogger(StorageManager::class.java)

    /**
     * Открывает новую сессию хранения.
     * Возвращает фасад, через который работают репозитории.
     */
    fun openSession(config: StorageConfig): StorageSession {
        val engine = config.resolvedEngine()
        logger.info("Opening storage session. engine={}", engine)
        val connection = registry.connectorFor(engine).connect(config)
        logger.info("Storage session connection opened. engine={}", engine)
        return sessionFactory.open(connection)
    }
}
