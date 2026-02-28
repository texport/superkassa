package kz.mybrain.superkassa.core.domain.model

/**
 * Пользователь ККМ (администратор/кассир).
 */
data class KkmUser(
    val id: String,
    val name: String,
    val role: UserRole,
    val pin: String?,
    val createdAt: Long
)
