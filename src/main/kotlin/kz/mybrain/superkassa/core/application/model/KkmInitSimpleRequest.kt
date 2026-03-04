package kz.mybrain.superkassa.core.application.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.Serializable
import kz.mybrain.superkassa.core.domain.model.VatGroup

/**
 * Упрощенный запрос на инициализацию ККМ без черновика.
 * Используется для ККМ, которые уже были зарегистрированы в ОФД ранее.
 * Все необходимые данные (регистрационный номер, заводской номер, сервисная информация)
 * получаются из ОФД автоматически.
 */
@Serializable
@Schema(description = "Упрощенный запрос на инициализацию ККМ (данные получаются из ОФД)")
data class KkmInitSimpleRequest(
    @Schema(description = "ID провайдера ОФД", example = "kazakhtelecom")
    @field:NotBlank(message = "OFD provider ID is required")
    val ofdId: String,
    
    @Schema(description = "Среда ОФД (test/prod)", example = "test")
    @field:NotBlank(message = "OFD environment is required")
    val ofdEnvironment: String,
    
    @Schema(description = "Системный ID ККМ в ОФД", example = "200367")
    @field:NotBlank(message = "OFD System ID is required")
    val ofdSystemId: String,
    
    @Schema(description = "Токен доступа ОФД", example = "32876190")
    @field:NotBlank(message = "OFD Token is required")
    val ofdToken: String,

    @Schema(
        description = "Базовая группа НДС по умолчанию: NO_VAT, VAT_0, VAT_5, VAT_10, VAT_16. " +
            "Если не указана — считается NO_VAT (касса не плательщик НДС).",
        example = "NO_VAT"
    )
    val defaultVatGroup: VatGroup = VatGroup.NO_VAT
)
