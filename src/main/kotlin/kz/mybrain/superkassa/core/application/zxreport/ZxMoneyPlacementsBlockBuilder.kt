package kz.mybrain.superkassa.core.application.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats

/**
 * Блок zxReport, отвечающий за moneyPlacements.
 *
 * Строит агрегаты по внесениям/изъятиям наличных на основе MONEY_PLACEMENT_* счётчиков.
 */
object ZxMoneyPlacementsBlockBuilder {

    private val operations = listOf(
        "MONEY_PLACEMENT_DEPOSIT",
        "MONEY_PLACEMENT_WITHDRAWAL"
    )

    /**
     * Построить список агрегатов по внесениям/изъятиям наличных.
     *
     * Ожидается, что [counters] — это карта счётчиков scope=SHIFT конкретной смены.
     */
    fun build(counters: Map<String, Long>): List<ZxReportBuilder.MoneyPlacementAggregate> {
        val result = mutableListOf<ZxReportBuilder.MoneyPlacementAggregate>()

        for (op in operations) {
            val totalCount = counters[CounterKeyFormats.MONEY_PLACEMENT_TOTAL_COUNT.format(op)] ?: 0L
            val count = counters[CounterKeyFormats.MONEY_PLACEMENT_COUNT.format(op)] ?: 0L
            val sum = counters[CounterKeyFormats.MONEY_PLACEMENT_SUM.format(op)] ?: 0L
            val offlineCount = counters[CounterKeyFormats.MONEY_PLACEMENT_OFFLINE_COUNT.format(op)] ?: 0L

            result += ZxReportBuilder.MoneyPlacementAggregate(
                operation = op,
                operationsTotalCount = totalCount,
                operationsCount = count,
                operationsSumBills = sum,
                offlineCount = offlineCount
            )
        }

        return result
    }
}

