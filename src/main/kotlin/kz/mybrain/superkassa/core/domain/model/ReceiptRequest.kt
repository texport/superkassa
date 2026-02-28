package kz.mybrain.superkassa.core.domain.model

import kotlinx.serialization.Serializable

/** Запрос на создание чека. */
@Serializable
data class ReceiptRequest(
        val kkmId: String,
        val pin: String,
        val operation: ReceiptOperationType,
        val items: List<ReceiptItem>,
        val payments: List<ReceiptPayment>,
        val total: Money,
        val taken: Money? = null,
        val change: Money? = null,
        val idempotencyKey: String,
        val parentTicket: ParentTicket? = null,
        val taxRegime: TaxRegime = TaxRegime.NO_VAT,
        /** Группа НДС на весь чек. Если null — используется defaultVatGroup из настроек ККМ. У позиции может быть свой vatGroup (приоритет у позиции). */
        val defaultVatGroup: VatGroup? = null,
        /** Скидка на весь чек (в формате Money). Опционально; при отсутствии не передаётся в ticket.amounts.discount. */
        val discount: Money? = null,
        /** Наценка на весь чек (в формате Money). Опционально; при отсутствии не передаётся в ticket.amounts.markup. */
        val markup: Money? = null
)
