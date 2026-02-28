package kz.mybrain.superkassa.storage.domain.model

/**
 * Пользователь ККМ (кассир/администратор).
 */
data class KkmUserRecord(
    val id: String,
    val cashboxId: String,
    val name: String,
    val role: String,
    val pin: String?,
    val pinHash: String,
    val createdAt: Long
)
