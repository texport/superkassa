package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Информация о смене.
 */
@Serializable
data class ShiftInfo(
    val id: String,
    val kkmId: String,
    val shiftNo: Long,
    val status: ShiftStatus,
    val openedAt: Long,
    val closedAt: Long? = null
)
