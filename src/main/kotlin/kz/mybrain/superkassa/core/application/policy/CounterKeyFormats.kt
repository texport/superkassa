package kz.mybrain.superkassa.core.application.policy

/**
 * Форматы ключей счетчиков.
 */
object CounterKeyFormats {
    const val OPERATION_COUNT = "operation.%s.count"
    const val OPERATION_SUM = "operation.%s.sum"
    const val TICKET_TOTAL_COUNT = "ticket.%s.total_count"
    const val TICKET_COUNT = "ticket.%s.count"
    const val TICKET_SUM = "ticket.%s.sum"
    const val TICKET_OFFLINE_COUNT = "ticket.%s.offline_count"
    const val PAYMENT_SUM = "ticket.%s.payment.%s.sum"
    const val PAYMENT_COUNT = "ticket.%s.payment.%s.count"
    const val NON_NULLABLE_SUM = "non_nullable.%s.sum"
}
