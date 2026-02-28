package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Снимок счетчика ККМ.
 */
@Serializable
data class CounterSnapshot(
    val scope: String,
    val shiftId: String? = null,
    val key: String,
    val value: Long,
    val updatedAt: Long
)
