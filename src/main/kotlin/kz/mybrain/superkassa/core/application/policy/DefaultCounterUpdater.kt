package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.tax.TaxCalculationService
import kz.mybrain.superkassa.core.domain.port.CounterUpdaterPort
import kz.mybrain.superkassa.core.domain.port.StoragePort

/**
 * Базовое обновление счетчиков для X/Z отчетов.
 */
class DefaultCounterUpdater(
    private val storage: StoragePort
) : CounterUpdaterPort {
    override fun updateForReceipt(kkmId: String, shiftId: String, request: ReceiptRequest, isOffline: Boolean) {
        val operationKey = operationKey(request.operation)
        val sumValue = request.total.bills

        // Суммы скидок/наценок/сдачи в тенге (только bills для счетчиков).
        val totalItemDiscountBills = request.items.mapNotNull { it.discount?.bills }.sum()
        val totalItemMarkupBills = request.items.mapNotNull { it.markup?.bills }.sum()
        val discountBills = request.discount?.bills ?: totalItemDiscountBills
        val markupBills = request.markup?.bills ?: totalItemMarkupBills
        val changeBills = request.change?.bills ?: 0L

        // Обновление операционных счетчиков.

        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.OPERATION_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.OPERATION_SUM.format(operationKey), sumValue)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.DISCOUNT_SUM.format(operationKey), discountBills)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.MARKUP_SUM.format(operationKey), markupBills)

        // Секционные счётчики по позициям чека.
        request.items.forEach { item ->
            val sectionCode = item.sectionCode.ifBlank { "001" }
            increment(
                kkmId,
                CounterScopes.SHIFT,
                shiftId,
                CounterKeyFormats.SECTION_OPERATION_COUNT.format(sectionCode, operationKey),
                1
            )
            increment(
                kkmId,
                CounterScopes.SHIFT,
                shiftId,
                CounterKeyFormats.SECTION_OPERATION_SUM.format(sectionCode, operationKey),
                item.sum.bills
            )
        }

        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_TOTAL_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_SUM.format(operationKey), sumValue)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_DISCOUNT_SUM.format(operationKey), discountBills)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_MARKUP_SUM.format(operationKey), markupBills)
        increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_CHANGE_SUM.format(operationKey), changeBills)

        if (isOffline) {
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TICKET_OFFLINE_COUNT.format(operationKey), 1)
        }

        // Необнуляемые суммы: глобальные и по смене.
        // NON_NULLABLE_SUM накапливает абсолютные суммы по каждой операции,
        // начиная с значения, зафиксированного при открытии смены.
        if (sumValue != 0L) {
            val nonNullableKey = CounterKeyFormats.NON_NULLABLE_SUM.format(operationKey)
            increment(kkmId, CounterScopes.SHIFT, shiftId, nonNullableKey, sumValue)
            increment(kkmId, CounterScopes.GLOBAL, null, nonNullableKey, sumValue)
        }

        // Платежи по типам.
        request.payments.forEach { payment ->
            val payKey = paymentKey(payment.type)
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.PAYMENT_SUM.format(operationKey, payKey), payment.sum.bills)
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.PAYMENT_COUNT.format(operationKey, payKey), 1)
        }

        // Кассовая сумма (наличные) и выручка по смене.
        val cashBills = request.payments.filter { it.type == PaymentType.CASH }.sumOf { it.sum.bills }
        if (cashBills != 0L) {
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.CASH_SUM, cashBills)
        }
        val revenueDelta = when (request.operation) {
            ReceiptOperationType.SELL, ReceiptOperationType.BUY -> sumValue
            ReceiptOperationType.SELL_RETURN, ReceiptOperationType.BUY_RETURN -> -sumValue
        }
        if (revenueDelta != 0L) {
            val currentRevenue = storage.loadCounters(kkmId, CounterScopes.SHIFT, shiftId)[CounterKeyFormats.REVENUE_SUM] ?: 0L
            val newRevenue = currentRevenue + revenueDelta
            storage.upsertCounter(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.REVENUE_SUM, newRevenue)
            storage.upsertCounter(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.REVENUE_IS_NEGATIVE, if (newRevenue < 0) 1 else 0)
        }

        // Налоговые счетчики (по данным чека).
        val taxService = TaxCalculationService()
        val taxResult = taxService.calculateTicketTaxes(
            items = request.items,
            taxRegime = request.taxRegime,
            defaultVatGroup = request.defaultVatGroup ?: kz.mybrain.superkassa.core.domain.model.VatGroup.NO_VAT
        )
        taxResult.ticketTaxes.forEach { line ->
            val taxKey = line.vatGroup.name
            val opKey = operationKey
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TAX_TURNOVER.format(taxKey, opKey), line.taxBase.bills)
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TAX_SUM.format(taxKey, opKey), line.taxSum.bills)
            val turnoverWithoutTax = line.taxBase.bills - line.taxSum.bills
            increment(kkmId, CounterScopes.SHIFT, shiftId, CounterKeyFormats.TAX_TURNOVER_NO_TAX.format(taxKey, opKey), turnoverWithoutTax)
        }

        // Глобальные счетчики (агрегаты по кассе).
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.OPERATION_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.OPERATION_SUM.format(operationKey), sumValue)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.DISCOUNT_SUM.format(operationKey), discountBills)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.MARKUP_SUM.format(operationKey), markupBills)

        // Глобальные секционные счётчики по позициям чека.
        request.items.forEach { item ->
            val sectionCode = item.sectionCode.ifBlank { "001" }
            increment(
                kkmId,
                CounterScopes.GLOBAL,
                null,
                CounterKeyFormats.SECTION_OPERATION_COUNT.format(sectionCode, operationKey),
                1
            )
            increment(
                kkmId,
                CounterScopes.GLOBAL,
                null,
                CounterKeyFormats.SECTION_OPERATION_SUM.format(sectionCode, operationKey),
                item.sum.bills
            )
        }

        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_TOTAL_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_COUNT.format(operationKey), 1)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_SUM.format(operationKey), sumValue)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_DISCOUNT_SUM.format(operationKey), discountBills)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_MARKUP_SUM.format(operationKey), markupBills)
        increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_CHANGE_SUM.format(operationKey), changeBills)
        if (isOffline) {
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.TICKET_OFFLINE_COUNT.format(operationKey), 1)
        }

        request.payments.forEach { payment ->
            val payKey = paymentKey(payment.type)
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.PAYMENT_SUM.format(operationKey, payKey), payment.sum.bills)
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.PAYMENT_COUNT.format(operationKey, payKey), 1)
        }

        if (cashBills != 0L) {
            increment(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.CASH_SUM, cashBills)
        }
        if (revenueDelta != 0L) {
            val currentRevenue = storage.loadCounters(kkmId, CounterScopes.GLOBAL, null)[CounterKeyFormats.REVENUE_SUM] ?: 0L
            val newRevenue = currentRevenue + revenueDelta
            storage.upsertCounter(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.REVENUE_SUM, newRevenue)
            storage.upsertCounter(kkmId, CounterScopes.GLOBAL, null, CounterKeyFormats.REVENUE_IS_NEGATIVE, if (newRevenue < 0) 1 else 0)
        }
    }

    private fun increment(kkmId: String, scope: String, shiftId: String?, key: String, delta: Long) {
        val current = storage.loadCounters(kkmId, scope, shiftId)[key] ?: 0L
        storage.upsertCounter(kkmId, scope, shiftId, key, current + delta)
    }

    private fun operationKey(operation: ReceiptOperationType): String {
        return when (operation) {
            ReceiptOperationType.SELL -> "OPERATION_SELL"
            ReceiptOperationType.SELL_RETURN -> "OPERATION_SELL_RETURN"
            ReceiptOperationType.BUY -> "OPERATION_BUY"
            ReceiptOperationType.BUY_RETURN -> "OPERATION_BUY_RETURN"
        }
    }

    private fun paymentKey(payment: PaymentType): String {
        return when (payment) {
            PaymentType.CASH -> "PAYMENT_CASH"
            PaymentType.CARD -> "PAYMENT_CARD"
            PaymentType.ELECTRONIC -> "PAYMENT_ELECTRONIC"
        }
    }
}
