package kz.mybrain.superkassa.storage.domain.model

/**
 * Append-only запись фискального журнала с hash-chain.
 */
data class FiscalJournalRecord(
    // Идентификатор записи.
    val id: String,
    // Идентификатор кассы.
    val cashboxId: String,
    // Тип записи (DOCUMENT/SHIFT/EVENT и т.д.).
    val recordType: String,
    // Ссылка на сущность (например, id документа).
    val recordRef: String,
    // Время записи (epoch millis).
    val createdAt: Long,
    // Предыдущий hash цепочки.
    val prevHash: ByteArray? = null,
    // Текущий hash записи.
    val recordHash: ByteArray
)
