package kz.mybrain.superkassa.core.application.zxreport

/**
 * Оркестратор построения агрегированных данных zxReport на основе счётчиков смены.
 *
 * Отвечает только за сборку общего [ZxReportInput] из специализированных *BlockBuilder:
 * - [ZxCashBlockBuilder]
 * - [ZxOperationsBlockBuilder]
 * - [ZxTicketOperationsBlockBuilder]
 * - [ZxMoneyPlacementsBlockBuilder]
 */
object ZxReportBuilder {

    data class OperationAggregate(
        val operation: String,
        val count: Long,
        val sumBills: Long
    )

    data class SectionAggregate(
        val sectionCode: String,
        val operations: List<OperationAggregate>
    )

    data class TicketPaymentAggregate(
        val payment: String,
        val sumBills: Long,
        val count: Long
    )

    data class TicketOperationAggregate(
        val operation: String,
        val ticketsTotalCount: Long,
        val ticketsCount: Long,
        val ticketsSumBills: Long,
        val payments: List<TicketPaymentAggregate>,
        val offlineCount: Long,
        val discountSumBills: Long,
        val markupSumBills: Long,
        val changeSumBills: Long
    )

    data class MoneyPlacementAggregate(
        val operation: String,
        val operationsTotalCount: Long,
        val operationsCount: Long,
        val operationsSumBills: Long,
        val offlineCount: Long
    )

    data class TaxOperationAggregate(
        val operation: String,
        val turnoverBills: Long,
        val turnoverWithoutTaxBills: Long,
        val taxSumBills: Long
    )

    data class TaxAggregate(
        val taxType: Int,
        val taxTypeCode: String,
        val percent: Int,
        val operations: List<TaxOperationAggregate>
    )

    data class ZxReportInput(
        val dateTimeMillis: Long,
        val shiftNumber: Int,
        val openShiftTimeMillis: Long,
        val closeShiftTimeMillis: Long?,
        val cashSumBills: Long,
        val revenueBills: Long,
        val revenueCoins: Int,
        val nonNullableSums: List<Pair<String, Long>>,
        val startShiftNonNullableSums: List<Pair<String, Long>>,
        val sections: List<SectionAggregate>,
        val operations: List<OperationAggregate>,
        val discounts: List<OperationAggregate>,
        val markups: List<OperationAggregate>,
        val totalResult: List<OperationAggregate>,
        val ticketOperations: List<TicketOperationAggregate>,
        val moneyPlacements: List<MoneyPlacementAggregate>,
        val taxes: List<TaxAggregate>
    )

    /**
     * Построить агрегированные значения zxReport на основе счетчиков смены.
     *
     * [counters] — карта ключ/значение для scope=SHIFT конкретной смены.
     */
    fun build(
        counters: Map<String, Long>,
        dateTimeMillis: Long,
        shiftNumber: Int,
        openShiftTimeMillis: Long,
        closeShiftTimeMillis: Long?
    ): ZxReportInput {
        val cashSumBills = ZxCashBlockBuilder.resolveCashSum(counters)
        val (revenueBills, revenueCoins) = ZxOperationsBlockBuilder.resolveRevenue(counters)
        val nonNullable = ZxCashBlockBuilder.resolveNonNullableSums(counters)
        val startShiftNonNullable = ZxCashBlockBuilder.resolveStartShiftNonNullableSums(counters)

        val operations = ZxOperationsBlockBuilder.resolveOperations(counters)
        val sections = ZxOperationsBlockBuilder.resolveSections(counters, operations)
        val discounts = ZxOperationsBlockBuilder.resolveDiscounts(counters)
        val markups = ZxOperationsBlockBuilder.resolveMarkups(counters)
        val totalResult = ZxOperationsBlockBuilder.resolveTotalResult(counters)

        val ticketOperations = ZxTicketOperationsBlockBuilder.resolveTicketOperations(counters)
        val moneyPlacements = ZxMoneyPlacementsBlockBuilder.build(counters)
        val taxes = ZxTaxBlockBuilder.resolveTaxes(counters)

        return ZxReportInput(
            dateTimeMillis = dateTimeMillis,
            shiftNumber = shiftNumber,
            openShiftTimeMillis = openShiftTimeMillis,
            closeShiftTimeMillis = closeShiftTimeMillis,
            cashSumBills = cashSumBills,
            revenueBills = revenueBills,
            revenueCoins = revenueCoins,
            nonNullableSums = nonNullable,
            startShiftNonNullableSums = startShiftNonNullable,
            sections = sections,
            operations = operations,
            discounts = discounts,
            markups = markups,
            totalResult = totalResult,
            ticketOperations = ticketOperations,
            moneyPlacements = moneyPlacements,
            taxes = taxes
        )
    }
}

