package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.Serializable
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo

/** Запрос на инициализацию черновика ККМ (COMMAND_SYSTEM + COMMAND_INFO). */
@Serializable
@Schema(description = "Запрос на фискализацию ранее созданного черновика ККМ")
data class KkmInitDraftRequest(
        @Schema(description = "ID черновика ККМ", example = "kkm-draft-1")
        @field:NotBlank(message = "KKM ID is required")
        val kkmId: String,
        @Schema(description = "Системный ID ККМ в ОФД", example = "system-id-12345")
        @field:NotBlank(message = "OFD System ID is required")
        val ofdSystemId: String,
        @Schema(description = "Токен доступа ОФД", example = "token-abc-123")
        @field:NotBlank(message = "OFD Token is required")
        val ofdToken: String,
        @Schema(description = "Регистрационный номер ККМ (КГД)", example = "123456789012")
        @field:NotBlank(message = "Registration number is required")
        val kkmKgdId: String,
        @Schema(description = "Сервисная информация ОФД") val serviceInfo: OfdServiceInfo? = null,
        @Schema(
                description = "Не используется. ПИН-код администратора передаётся только в заголовке Authorization",
                example = "deprecated"
        )
        val _unused: String? = null
)
