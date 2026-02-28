package kz.mybrain.superkassa.core.application.model

import kotlinx.serialization.Serializable

/**
 * Обновление настроек ККМ.
 */
@Serializable
data class KkmSettingsUpdateRequest(
    val autoCloseShift: Boolean? = null
)
