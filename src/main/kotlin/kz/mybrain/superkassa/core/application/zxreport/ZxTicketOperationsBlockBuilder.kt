package kz.mybrain.superkassa.core.application.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats

/**
 * Блок zxReport, отвечающий за ticketOperations.
 */
object ZxTicketOperationsBlockBuilder {

    fun resolveTicketOperations(counters: Map<String, Long>): List<ZxReportBuilder.TicketOperationAggregate> {
        val result = mutableListOf<ZxReportBuilder.TicketOperationAggregate>()
        for (op in operationsList()) {
            val totalCount = counters[CounterKeyFormats.TICKET_TOTAL_COUNT.format(op)] ?: 0L
            val count = counters[CounterKeyFormats.TICKET_COUNT.format(op)] ?: 0L
            val sum = counters[CounterKeyFormats.TICKET_SUM.format(op)] ?: 0L
            val offlineCount = counters[CounterKeyFormats.TICKET_OFFLINE_COUNT.format(op)] ?: 0L
            val discountSum = counters[CounterKeyFormats.TICKET_DISCOUNT_SUM.format(op)] ?: 0L
            val markupSum = counters[CounterKeyFormats.TICKET_MARKUP_SUM.format(op)] ?: 0L
            val changeSum = counters[CounterKeyFormats.TICKET_CHANGE_SUM.format(op)] ?: 0L

            val payments = paymentTypes().map { payKey ->
                val sumKey = CounterKeyFormats.PAYMENT_SUM.format(op, payKey)
                val countKey = CounterKeyFormats.PAYMENT_COUNT.format(op, payKey)
                val paySum = counters[sumKey] ?: 0L
                val payCount = counters[countKey] ?: 0L
                ZxReportBuilder.TicketPaymentAggregate(payment = payKey, sumBills = paySum, count = payCount)
            }

            result += ZxReportBuilder.TicketOperationAggregate(
                operation = op,
                ticketsTotalCount = totalCount,
                ticketsCount = count,
                ticketsSumBills = sum,
                payments = payments,
                offlineCount = offlineCount,
                discountSumBills = discountSum,
                markupSumBills = markupSum,
                changeSumBills = changeSum
            )
        }
        return result
    }

    private fun operationsList(): List<String> =
        listOf("OPERATION_SELL", "OPERATION_SELL_RETURN", "OPERATION_BUY", "OPERATION_BUY_RETURN")

    private fun paymentTypes(): List<String> =
        listOf("PAYMENT_CASH", "PAYMENT_CARD", "PAYMENT_CREDIT", "PAYMENT_TARE", "PAYMENT_MOBILE")
}

