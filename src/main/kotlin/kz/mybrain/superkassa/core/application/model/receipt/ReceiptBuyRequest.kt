package kz.mybrain.superkassa.core.application.model.receipt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import kotlinx.serialization.Serializable

@Serializable
@Schema(description = "Запрос на создание чека покупки. Сумма чека и суммы позиций вычисляются на сервере.")
data class ReceiptBuyRequest(
    @Schema(description = "Ключ идемпотентности", example = "unique-key-123")
    @field:NotBlank
    val idempotencyKey: String,
    @Schema(description = "Позиции чека")
    @field:NotEmpty(message = "Список позиций не может быть пустым")
    @field:Valid
    val items: List<ReceiptItemDto>,
    @Schema(description = "Наценка на весь чек: процент (0–100). Взаимоисключающе с markupSum.", example = "0")
    @field:DecimalMin("0")
    @field:DecimalMax("100")
    val markupPercent: Double? = null,
    @Schema(description = "Наценка на весь чек: сумма в тенге. Взаимоисключающе с markupPercent.", example = "0")
    @field:DecimalMin("0")
    val markupSum: Double? = null,
    @Schema(description = "Скидка на весь чек: процент (0–100). Взаимоисключающе с discountSum.", example = "5")
    @field:DecimalMin("0")
    @field:DecimalMax("100")
    val discountPercent: Double? = null,
    @Schema(description = "Скидка на весь чек: сумма в тенге. Взаимоисключающе с discountPercent.", example = "50.00")
    @field:DecimalMin("0")
    val discountSum: Double? = null,
    @Schema(description = "Сдача (в тенге, опционально)", example = "499.25")
    val change: Double? = null,
    @Schema(description = "Группа НДС на весь чек. Если не указана — используется настройка ККМ. NO_VAT, VAT_0, VAT_5, VAT_10, VAT_16.", example = "NO_VAT")
    val defaultVatGroup: String? = null,
    @Schema(description = "Способы оплаты. Допустимые типы: CASH, CARD, ELECTRONIC.")
    @field:NotEmpty(message = "Укажите хотя бы один способ оплаты")
    @field:Valid
    val payments: List<ReceiptPaymentDto>,
    @Schema(description = "Получено от покупателя (в тенге, опционально)", example = "2000.00")
    val taken: Double? = null
) {
    init {
        require(discountPercent == null || discountSum == null) {
            "Укажите скидку на чек либо в процентах (discountPercent), либо суммой (discountSum), но не оба значения"
        }
        require(markupPercent == null || markupSum == null) {
            "Укажите наценку на чек либо в процентах (markupPercent), либо суммой (markupSum), но не оба значения"
        }
    }
}
