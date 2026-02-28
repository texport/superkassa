package kz.mybrain.superkassa.core.application.model.receipt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import kotlinx.serialization.Serializable

/**
 * Способ оплаты чека (DTO для HTTP запросов).
 * Допустимые типы по протоколу ОФД: CASH, CARD, ELECTRONIC.
 */
@Serializable
@Schema(description = "Способ оплаты чека")
data class ReceiptPaymentDto(
    @Schema(
        description = "Тип оплаты. Допустимые значения: CASH (наличные), CARD (карта), ELECTRONIC (электронные средства).",
        allowableValues = ["CASH", "CARD", "ELECTRONIC"],
        example = "CASH"
    )
    val type: String,
    @Schema(description = "Сумма оплаты (в тенге)", example = "500.00")
    @field:NotNull
    @field:DecimalMin("0", message = "Сумма оплаты не может быть отрицательной")
    val sum: Double
)
