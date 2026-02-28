package kz.mybrain.superkassa.storage.domain.model

/**
 * Lease/lock на кассу для исключения параллельных фискальных операций.
 */
data class CashboxLock(
    // Идентификатор кассы.
    val cashboxId: String,
    // Идентификатор владельца lease (экземпляр сервиса).
    val ownerId: String,
    // Время окончания lease (epoch millis).
    val leaseUntil: Long,
    // Время получения lease (epoch millis).
    val acquiredAt: Long
)
