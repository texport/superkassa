package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.domain.port.OfflineQueuePort
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Воркер OFFLINE-очереди. Периодически обрабатывает команды и отправляет их в ОФД.
 */
@Component
class QueueWorker(
    private val queue: OfflineQueuePort,
    private val storage: StoragePort
) {
    private val logger = LoggerFactory.getLogger(QueueWorker::class.java)

    @Scheduled(fixedDelayString = "\${offline-queue.worker.interval-ms:5000}", initialDelay = 10000)
    fun processOfflineQueues() {
        val kkms = storage.listKkms(limit = 100, offset = 0, state = null, search = null)
        for (kkm in kkms) {
            val processed = queue.processOfflineBatch(kkm.id, limit = 5)
            if (processed > 0) {
                logger.info("Queue worker processed {} commands for cashbox {}", processed, kkm.id)
            }
        }
    }
}
