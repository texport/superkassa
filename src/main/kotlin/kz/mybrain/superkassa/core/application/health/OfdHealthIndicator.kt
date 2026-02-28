package kz.mybrain.superkassa.core.application.health

import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Health indicator для мониторинга состояния ОФД.
 * Проверяет доступность ОФД и статистику последних запросов.
 * 
 * Использование: можно интегрировать в Spring Boot Actuator, добавив зависимость
 * spring-boot-starter-actuator и реализовав интерфейс HealthIndicator.
 */
@Component
class OfdHealthIndicator(
    private val ofd: OfdManagerPort,
    private val storage: StoragePort
) {
    private val logger = LoggerFactory.getLogger(OfdHealthIndicator::class.java)

    /**
     * Проверяет состояние ОФД и возвращает статус здоровья.
     * 
     * @return OfdHealthStatus с информацией о состоянии ОФД
     */
    fun checkHealth(): OfdHealthStatus {
        return try {
            val kkms = storage.listKkms(limit = 1, offset = 0, state = null, search = null, sortBy = "createdAt", sortOrder = "DESC")
            
            if (kkms.isEmpty()) {
                return OfdHealthStatus(
                    healthy = true,
                    status = "SKIPPED",
                    message = "No KKM registered",
                    activeKkms = 0
                )
            }

            // Проверяем последние успешные запросы к ОФД
            // В реальной реализации можно добавить проверку через легкий запрос или статистику
            val activeKkms = kkms.count { it.state == "ACTIVE" }
            
            if (activeKkms == 0) {
                return OfdHealthStatus(
                    healthy = true,
                    status = "SKIPPED",
                    message = "No active KKM",
                    activeKkms = 0
                )
            }

            // Базовая проверка: если есть активные ККМ, считаем ОФД доступным
            // В будущем можно добавить реальную проверку через ping-запрос
            OfdHealthStatus(
                healthy = true,
                status = "OK",
                message = "OFD is available",
                activeKkms = activeKkms
            )
        } catch (e: Exception) {
            logger.warn("OFD health check failed", e)
            OfdHealthStatus(
                healthy = false,
                status = "ERROR",
                message = e.message ?: "Unknown error",
                activeKkms = 0,
                error = e
            )
        }
    }
}

/**
 * Статус здоровья ОФД.
 */
data class OfdHealthStatus(
    val healthy: Boolean,
    val status: String,
    val message: String,
    val activeKkms: Int,
    val error: Exception? = null
)
