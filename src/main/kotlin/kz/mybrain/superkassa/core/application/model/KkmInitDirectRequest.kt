package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.Serializable
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo

/** Запрос на инициализацию ККМ без черновика. */
@Serializable
@Schema(description = "Запрос на прямую инициализацию ККМ (без черновика)")
data class KkmInitDirectRequest(
        @Schema(description = "ID провайдера ОФД", example = "kazakhtelecom")
        @field:NotBlank(message = "OFD provider ID is required")
        val ofdId: String,
        @Schema(description = "Среда ОФД (test/prod)", example = "test")
        @field:NotBlank(message = "OFD environment is required")
        val ofdEnvironment: String,
        @Schema(description = "Системный ID ККМ в ОФД", example = "system-id-12345")
        @field:NotBlank(message = "OFD System ID is required")
        val ofdSystemId: String,
        @Schema(description = "Токен доступа ОФД", example = "token-abc-123")
        @field:NotBlank(message = "OFD Token is required")
        val ofdToken: String,
        @Schema(description = "Регистрационный номер ККМ (КГД)", example = "123456789012")
        @field:NotBlank(message = "Registration number is required")
        val kkmKgdId: String,
        @Schema(description = "Заводской номер ККМ", example = "SWK-0001")
        @field:NotBlank(message = "Factory number is required")
        val factoryNumber: String,
        @Schema(description = "Год выпуска", example = "2024")
        @field:Min(2000)
        @field:Max(2100)
        val manufactureYear: Int,
        @Schema(description = "Сервисная информация ОФД") val serviceInfo: OfdServiceInfo? = null,
        @Schema(
                description = "Не используется. ПИН-код администратора передаётся только в заголовке Authorization",
                example = "deprecated"
        )
        val _unused: String? = null
)
