package kz.mybrain.superkassa.storage.application.health

import kz.mybrain.superkassa.storage.application.connector.StorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import org.slf4j.LoggerFactory

/**
 * Проверка доступности подключения к БД.
 */
class StorageHealthChecker(
    private val registry: StorageConnectorRegistry
) {
    private val logger = LoggerFactory.getLogger(StorageHealthChecker::class.java)

    /**
     * Проверяет соединение, возвращает результат и сообщение.
     */
    fun check(config: StorageConfig, timeoutSeconds: Int = 5): StorageHealthStatus {
        val engine = config.resolvedEngine()
        logger.info("Storage health check started. engine={}", engine)
        return try {
            registry.connectorFor(engine).connect(config).use { connection ->
                val ok = connection.isValid(timeoutSeconds)
                val message = if (ok) "Connection OK" else "Connection failed"
                logger.info("Storage health check result: {}", message)
                StorageHealthStatus(ok, message)
            }
        } catch (ex: Exception) {
            logger.error("Storage health check failed", ex)
            StorageHealthStatus(false, ex.message ?: "Health check error")
        }
    }
}

data class StorageHealthStatus(
    val ok: Boolean,
    val message: String
)
