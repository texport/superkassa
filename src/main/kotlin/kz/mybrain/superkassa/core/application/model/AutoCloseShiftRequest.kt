package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

/**
 * Запрос на обновление настройки автозакрытия смены ККМ.
 */
@Serializable
@Schema(description = "Запрос на обновление настройки автозакрытия смены")
data class AutoCloseShiftRequest(
    @Schema(description = "Включить/выключить автоматическое закрытие смены", example = "true")
    val autoCloseShift: Boolean
)
