package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Роль пользователя ККМ.
 */
@Serializable
enum class UserRole {
    ADMIN,
    CASHIER
}
