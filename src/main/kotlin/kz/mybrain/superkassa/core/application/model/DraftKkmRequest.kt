package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.Serializable

/** Запрос на создание черновика ККМ (Draft KKM). */
@Serializable
@Schema(description = "Запрос на создание черновика ККМ")
data class DraftKkmRequest(
        @Schema(description = "ID провайдера ОФД", example = "kazakhtelecom")
        @field:NotBlank(message = "OFD provider ID is required")
        val ofdId: String,
        @Schema(description = "Среда ОФД (test/prod)", example = "test")
        @field:NotBlank(message = "OFD environment is required")
        val ofdEnvironment: String,
        @Schema(
                description = "Не используется. ПИН-код администратора передаётся только в заголовке Authorization",
                example = "deprecated"
        )
        val _unused: String? = null
)
