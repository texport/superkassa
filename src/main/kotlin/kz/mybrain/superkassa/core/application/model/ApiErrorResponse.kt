package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

/** Единый формат ответа об ошибке. */
@Serializable
@Schema(description = "Ответ с ошибкой")
data class ApiErrorResponse(
        @Schema(description = "Код ошибки", example = "VALIDATION_ERROR") val code: String,
        @Schema(
                description = "Сообщение об ошибке",
                example = "Validation failed for argument [0]..."
        )
        val message: String,
        @Schema(
                description = "Детали ошибки (опционально)",
                example = "Field 'pin' must not be blank"
        )
        val details: String? = null
)
