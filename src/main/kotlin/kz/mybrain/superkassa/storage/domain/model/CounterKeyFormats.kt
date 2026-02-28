package kz.mybrain.superkassa.storage.domain.model

/**
 * Рекомендуемые форматы ключей счетчиков.
 * Хранятся как строковые ключи в CounterRecord.key.
 */
object CounterKeyFormats {
    const val OPERATION_COUNT = "operation.%s.count"
    const val OPERATION_SUM = "operation.%s.sum"
    const val DISCOUNT_SUM = "operation.%s.discount_sum"
    const val MARKUP_SUM = "operation.%s.markup_sum"

    const val SECTION_OPERATION_COUNT = "section.%s.operation.%s.count"
    const val SECTION_OPERATION_SUM = "section.%s.operation.%s.sum"

    const val TICKET_TOTAL_COUNT = "ticket.%s.total_count"
    const val TICKET_COUNT = "ticket.%s.count"
    const val TICKET_SUM = "ticket.%s.sum"
    const val TICKET_OFFLINE_COUNT = "ticket.%s.offline_count"
    const val TICKET_DISCOUNT_SUM = "ticket.%s.discount_sum"
    const val TICKET_MARKUP_SUM = "ticket.%s.markup_sum"
    const val TICKET_CHANGE_SUM = "ticket.%s.change_sum"

    const val PAYMENT_SUM = "ticket.%s.payment.%s.sum"
    const val PAYMENT_COUNT = "ticket.%s.payment.%s.count"

    const val MONEY_PLACEMENT_COUNT = "money_placement.%s.count"
    const val MONEY_PLACEMENT_TOTAL_COUNT = "money_placement.%s.total_count"
    const val MONEY_PLACEMENT_SUM = "money_placement.%s.sum"
    const val MONEY_PLACEMENT_OFFLINE_COUNT = "money_placement.%s.offline_count"

    const val TAX_TURNOVER = "tax.%s.%s.turnover"
    const val TAX_SUM = "tax.%s.%s.sum"
    const val TAX_TURNOVER_NO_TAX = "tax.%s.%s.turnover_without_tax"

    const val NON_NULLABLE_SUM = "non_nullable.%s.sum"
    const val START_SHIFT_NON_NULLABLE_SUM = "start_shift_non_nullable.%s.sum"

    const val CASH_SUM = "cash.sum"
    const val REVENUE_SUM = "revenue.sum"
    const val REVENUE_IS_NEGATIVE = "revenue.is_negative"
}
