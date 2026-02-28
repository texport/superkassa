package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Статус смены.
 */
@Serializable
enum class ShiftStatus {
    OPEN,
    CLOSED
}
