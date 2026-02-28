package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Сохраняемое содержимое чека (без pin и idempotencyKey).
 * Используется для хранения в payload_bin и последующего рендера чека по documentId.
 */
@Serializable
data class ReceiptStoredPayload(
    val kkmId: String,
    val operation: ReceiptOperationType,
    val items: List<ReceiptItem>,
    val payments: List<ReceiptPayment>,
    val total: Money,
    val taken: Money? = null,
    val change: Money? = null,
    val parentTicket: ParentTicket? = null,
    val taxRegime: TaxRegime = TaxRegime.NO_VAT,
    val defaultVatGroup: VatGroup = VatGroup.NO_VAT,
    val discount: Money? = null,
    val markup: Money? = null
) {
    fun toReceiptRequest(): ReceiptRequest = ReceiptRequest(
        kkmId = kkmId,
        pin = "",
        operation = operation,
        items = items,
        payments = payments,
        total = total,
        taken = taken,
        change = change,
        idempotencyKey = "",
        parentTicket = parentTicket,
        taxRegime = taxRegime,
        defaultVatGroup = defaultVatGroup,
        discount = discount,
        markup = markup
    )

    companion object {
        fun fromReceiptRequest(request: ReceiptRequest): ReceiptStoredPayload = ReceiptStoredPayload(
            kkmId = request.kkmId,
            operation = request.operation,
            items = request.items,
            payments = request.payments,
            total = request.total,
            taken = request.taken,
            change = request.change,
            parentTicket = request.parentTicket,
            taxRegime = request.taxRegime,
            defaultVatGroup = request.defaultVatGroup ?: VatGroup.NO_VAT,
            discount = request.discount,
            markup = request.markup
        )
    }
}
