package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.domain.model.CashOperationType
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.core.domain.tax.TaxCalculationService

/**
 * Пересборка счётчиков смены на основе фактических документов.
 *
 * Используется перед формированием X/Z‑отчётов, чтобы:
 * - пересчитать SHIFT‑счётчики из документов (чеки, внесения/изъятия),
 * - сравнять кеш счётчиков с источником истины (fiscal_documents),
 * - передать в ZXReportBuilder консистентную картину.
 *
 * Сейчас пересчитываются только счётчики scope=SHIFT; GLOBAL‑агрегаты не трогаем.
 */
class ShiftCountersRecalculator(
    private val storage: StoragePort
 ) {

    /**
     * Полностью пересобирает счётчики смены из документов и сохраняет их в storage.
     *
     * @return карта key -> value для scope=SHIFT указанной смены.
     */
    fun rebuildAndPersistShiftCounters(kkmId: String, shift: ShiftInfo): Map<String, Long> {
        val rebuilt = rebuildShiftCounters(kkmId, shift)
        // Сохраняем обновлённые значения в таблицу counter (scope=SHIFT).
        rebuilt.forEach { (key, value) ->
            storage.upsertCounter(kkmId, CounterScopes.SHIFT, shift.id, key, value)
        }
        return rebuilt
    }

    /**
     * Пересчитывает счётчики смены из документов в памяти (без сохранения в БД).
     */
    fun rebuildShiftCounters(kkmId: String, shift: ShiftInfo): Map<String, Long> {
        // Берём существующие счётчики только для чтения начальных необнуляемых сумм.
        val existing = storage.loadCounters(kkmId, CounterScopes.SHIFT, shift.id)
        val result = mutableMapOf<String, Long>()

        // 1. Инициализируем стартовые необнуляемые суммы и конечные NON_NULLABLE_SUM как start + delta.
        val operations = listOf(
            "OPERATION_SELL",
            "OPERATION_SELL_RETURN",
            "OPERATION_BUY",
            "OPERATION_BUY_RETURN"
        )
        operations.forEach { op ->
            val startKey = CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format(op)
            val startValue = existing[startKey] ?: 0L
            if (startValue != 0L) {
                result[startKey] = startValue
            }
            val nonNullableKey = CounterKeyFormats.NON_NULLABLE_SUM.format(op)
            result[nonNullableKey] = startValue
        }

        // 2. Проходим по всем документам смены и накапливаем агрегаты.
        val documents = storage.listFiscalDocumentsByShift(kkmId, shift.id, limit = Int.MAX_VALUE, offset = 0)
        documents.forEach { doc ->
            when (doc.docType) {
                "CHECK" -> applyReceiptDocument(doc, result)
                CashOperationType.CASH_IN.name,
                CashOperationType.CASH_OUT.name -> applyCashOperationDocument(doc, result)
                // Остальные типы документов (отчёты, служебные) для ZX‑счётчиков не используются.
            }
        }

        // 3. Обновляем агрегаты выручки (REVENUE_SUM/REVENUE_IS_NEGATIVE) по накопленному значению.
        // Выручка накапливается в applyReceiptDocument в виде отдельного счётчика.
        val revenue = result[CounterKeyFormats.REVENUE_SUM] ?: 0L
        if (revenue != 0L) {
            result[CounterKeyFormats.REVENUE_IS_NEGATIVE] = if (revenue < 0) 1L else 0L
        } else {
            result.remove(CounterKeyFormats.REVENUE_IS_NEGATIVE)
        }

        return result
    }

    private fun applyReceiptDocument(doc: FiscalDocumentSnapshot, counters: MutableMap<String, Long>) {
        val pair = storage.findFiscalDocumentWithReceiptPayload(doc.id) ?: return
        val request: ReceiptRequest = pair.second

        val operationKey = when (request.operation) {
            ReceiptOperationType.SELL -> "OPERATION_SELL"
            ReceiptOperationType.SELL_RETURN -> "OPERATION_SELL_RETURN"
            ReceiptOperationType.BUY -> "OPERATION_BUY"
            ReceiptOperationType.BUY_RETURN -> "OPERATION_BUY_RETURN"
        }
        val sumValue = request.total.bills

        // Суммы скидок/наценок/сдачи в тенге (только bills для счетчиков).
        val totalItemDiscountBills = request.items.mapNotNull { it.discount?.bills }.sum()
        val totalItemMarkupBills = request.items.mapNotNull { it.markup?.bills }.sum()
        val discountBills = request.discount?.bills ?: totalItemDiscountBills
        val markupBills = request.markup?.bills ?: totalItemMarkupBills
        val changeBills = request.change?.bills ?: 0L

        // Операционные счётчики по смене.
        increment(counters, CounterKeyFormats.OPERATION_COUNT.format(operationKey), 1L)
        increment(counters, CounterKeyFormats.OPERATION_SUM.format(operationKey), sumValue)
        increment(counters, CounterKeyFormats.DISCOUNT_SUM.format(operationKey), discountBills)
        increment(counters, CounterKeyFormats.MARKUP_SUM.format(operationKey), markupBills)

        // Секционные счётчики по позициям чека.
        request.items.forEach { item ->
            val sectionCode = item.sectionCode.ifBlank { "001" }
            increment(
                counters,
                CounterKeyFormats.SECTION_OPERATION_COUNT.format(sectionCode, operationKey),
                1L
            )
            increment(
                counters,
                CounterKeyFormats.SECTION_OPERATION_SUM.format(sectionCode, operationKey),
                item.sum.bills
            )
        }

        // Ticket‑агрегаты.
        increment(counters, CounterKeyFormats.TICKET_TOTAL_COUNT.format(operationKey), 1L)
        increment(counters, CounterKeyFormats.TICKET_COUNT.format(operationKey), 1L)
        increment(counters, CounterKeyFormats.TICKET_SUM.format(operationKey), sumValue)
        increment(counters, CounterKeyFormats.TICKET_DISCOUNT_SUM.format(operationKey), discountBills)
        increment(counters, CounterKeyFormats.TICKET_MARKUP_SUM.format(operationKey), markupBills)
        increment(counters, CounterKeyFormats.TICKET_CHANGE_SUM.format(operationKey), changeBills)
        if (doc.isAutonomous || doc.ofdStatus == "TIMEOUT") {
            increment(counters, CounterKeyFormats.TICKET_OFFLINE_COUNT.format(operationKey), 1L)
        }

        // Необнуляемые суммы: NON_NULLABLE_SUM уже инициализировано стартом; наращиваем дельтой смены.
        if (sumValue != 0L) {
            val nonNullableKey = CounterKeyFormats.NON_NULLABLE_SUM.format(operationKey)
            increment(counters, nonNullableKey, sumValue)
        }

        // Платежи по типам.
        request.payments.forEach { payment ->
            val payKey = when (payment.type) {
                PaymentType.CASH -> "PAYMENT_CASH"
                PaymentType.CARD -> "PAYMENT_CARD"
                PaymentType.ELECTRONIC -> "PAYMENT_ELECTRONIC"
            }
            increment(
                counters,
                CounterKeyFormats.PAYMENT_SUM.format(operationKey, payKey),
                payment.sum.bills
            )
            increment(
                counters,
                CounterKeyFormats.PAYMENT_COUNT.format(operationKey, payKey),
                1L
            )
        }

        // Кассовая сумма (наличные) по смене.
        val cashBills = request.payments
            .filter { it.type == PaymentType.CASH }
            .sumOf { it.sum.bills }
        if (cashBills != 0L) {
            increment(counters, CounterKeyFormats.CASH_SUM, cashBills)
        }

        // Выручка по смене.
        val revenueDelta = when (request.operation) {
            ReceiptOperationType.SELL, ReceiptOperationType.BUY -> sumValue
            ReceiptOperationType.SELL_RETURN, ReceiptOperationType.BUY_RETURN -> -sumValue
        }
        if (revenueDelta != 0L) {
            val current = counters[CounterKeyFormats.REVENUE_SUM] ?: 0L
            counters[CounterKeyFormats.REVENUE_SUM] = current + revenueDelta
        }

        // Налоговые счётчики (по данным чека).
        val taxService = TaxCalculationService()
        val taxResult = taxService.calculateTicketTaxes(
            items = request.items,
            taxRegime = request.taxRegime,
            defaultVatGroup = request.defaultVatGroup
                ?: kz.mybrain.superkassa.core.domain.model.VatGroup.NO_VAT
        )
        taxResult.ticketTaxes.forEach { line ->
            val taxKey = line.vatGroup.name
            val opKey = operationKey
            increment(
                counters,
                CounterKeyFormats.TAX_TURNOVER.format(taxKey, opKey),
                line.taxBase.bills
            )
            increment(
                counters,
                CounterKeyFormats.TAX_SUM.format(taxKey, opKey),
                line.taxSum.bills
            )
            val turnoverWithoutTax = line.taxBase.bills - line.taxSum.bills
            increment(
                counters,
                CounterKeyFormats.TAX_TURNOVER_NO_TAX.format(taxKey, opKey),
                turnoverWithoutTax
            )
        }
    }

    private fun applyCashOperationDocument(doc: FiscalDocumentSnapshot, counters: MutableMap<String, Long>) {
        val amount = doc.totalAmount ?: 0L
        if (amount == 0L) return

        val type = when (doc.docType) {
            CashOperationType.CASH_IN.name -> CashOperationType.CASH_IN
            CashOperationType.CASH_OUT.name -> CashOperationType.CASH_OUT
            else -> return
        }

        val opKey = when (type) {
            CashOperationType.CASH_IN -> "MONEY_PLACEMENT_DEPOSIT"
            CashOperationType.CASH_OUT -> "MONEY_PLACEMENT_WITHDRAWAL"
        }

        // Обновляем CASH_SUM: внесение увеличивает, изъятие уменьшает наличность.
        val delta = when (type) {
            CashOperationType.CASH_IN -> amount
            CashOperationType.CASH_OUT -> -amount
        }
        if (delta != 0L) {
            increment(counters, CounterKeyFormats.CASH_SUM, delta)
        }

        // Счётчики money_placement.* по смене.
        increment(counters, CounterKeyFormats.MONEY_PLACEMENT_TOTAL_COUNT.format(opKey), 1L)
        increment(counters, CounterKeyFormats.MONEY_PLACEMENT_COUNT.format(opKey), 1L)
        increment(counters, CounterKeyFormats.MONEY_PLACEMENT_SUM.format(opKey), amount)
        if (doc.isAutonomous || doc.ofdStatus == "TIMEOUT") {
            increment(
                counters,
                CounterKeyFormats.MONEY_PLACEMENT_OFFLINE_COUNT.format(opKey),
                1L
            )
        }
    }

    private fun increment(counters: MutableMap<String, Long>, key: String, delta: Long) {
        if (delta == 0L) return
        val current = counters[key] ?: 0L
        counters[key] = current + delta
    }
}

