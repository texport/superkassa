package kz.mybrain.superkassa.core.application.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats

/**
 * Блок zxReport, отвечающий за:
 * - operations
 * - sections
 * - discounts
 * - markups
 * - totalResult
 * - revenue
 */
object ZxOperationsBlockBuilder {

    fun resolveRevenue(counters: Map<String, Long>): Pair<Long, Int> {
        val revenueBillsCounter = counters[CounterKeyFormats.REVENUE_SUM]
        val isNegativeFlag = counters[CounterKeyFormats.REVENUE_IS_NEGATIVE] == 1L

        if (revenueBillsCounter != null) {
            val abs = kotlin.math.abs(revenueBillsCounter)
            val signed = if (isNegativeFlag) -abs else abs
            return signed to 0
        }

        // Фолбэк на старую схему через операции / неотменяемые суммы.
        val operations = operationsList()

        var total = 0L
        operations.forEach { op ->
            val nonNullableKey = CounterKeyFormats.NON_NULLABLE_SUM.format(op)
            val opSumKey = CounterKeyFormats.OPERATION_SUM.format(op)
            val sum = counters[nonNullableKey] ?: counters[opSumKey] ?: 0L
            if (sum != 0L) {
                total += when (op) {
                    "OPERATION_SELL", "OPERATION_BUY" -> sum
                    "OPERATION_SELL_RETURN", "OPERATION_BUY_RETURN" -> -sum
                    else -> sum
                }
            }
        }
        return total to 0
    }

    fun resolveOperations(counters: Map<String, Long>): List<ZxReportBuilder.OperationAggregate> {
        val result = mutableListOf<ZxReportBuilder.OperationAggregate>()
        for (op in operationsList()) {
            val count = counters[CounterKeyFormats.OPERATION_COUNT.format(op)] ?: 0L
            val sum = counters[CounterKeyFormats.OPERATION_SUM.format(op)] ?: 0L
            result += ZxReportBuilder.OperationAggregate(operation = op, count = count, sumBills = sum)
        }
        return result
    }

    fun resolveSections(
        counters: Map<String, Long>,
        fallbackOperations: List<ZxReportBuilder.OperationAggregate>
    ): List<ZxReportBuilder.SectionAggregate> {
        // Если секционных счётчиков нет, сохраняем поведение по умолчанию: одна секция "1".
        val sectionKeys = counters.keys.filter { it.startsWith("section.") }
        if (sectionKeys.isEmpty()) {
            if (fallbackOperations.isEmpty()) return emptyList()
            return listOf(ZxReportBuilder.SectionAggregate(sectionCode = "1", operations = fallbackOperations))
        }

        // Собираем коды секций из ключей формата "section.%s.operation.%s.(count|sum)".
        val sectionCodes = sectionKeys.mapNotNull { key ->
            val parts = key.split('.')
            if (parts.size >= 4 && parts[0] == "section" && parts[2] == "operation") {
                parts[1]
            } else {
                null
            }
        }.toSortedSet()

        val result = mutableListOf<ZxReportBuilder.SectionAggregate>()
        for (sectionCode in sectionCodes) {
            val ops = operationsList().map { op ->
                val countKey = CounterKeyFormats.SECTION_OPERATION_COUNT.format(sectionCode, op)
                val sumKey = CounterKeyFormats.SECTION_OPERATION_SUM.format(sectionCode, op)
                val count = counters[countKey] ?: 0L
                val sum = counters[sumKey] ?: 0L
                ZxReportBuilder.OperationAggregate(operation = op, count = count, sumBills = sum)
            }
            result += ZxReportBuilder.SectionAggregate(sectionCode = sectionCode, operations = ops)
        }
        return result
    }

    fun resolveDiscounts(counters: Map<String, Long>): List<ZxReportBuilder.OperationAggregate> {
        val result = mutableListOf<ZxReportBuilder.OperationAggregate>()
        for (op in operationsList()) {
            val sum = counters[CounterKeyFormats.DISCOUNT_SUM.format(op)] ?: 0L
            val count = counters[CounterKeyFormats.OPERATION_COUNT.format(op)] ?: 0L
            result += ZxReportBuilder.OperationAggregate(operation = op, count = count, sumBills = sum)
        }
        return result
    }

    fun resolveMarkups(counters: Map<String, Long>): List<ZxReportBuilder.OperationAggregate> {
        val result = mutableListOf<ZxReportBuilder.OperationAggregate>()
        for (op in operationsList()) {
            val sum = counters[CounterKeyFormats.MARKUP_SUM.format(op)] ?: 0L
            val count = counters[CounterKeyFormats.OPERATION_COUNT.format(op)] ?: 0L
            result += ZxReportBuilder.OperationAggregate(operation = op, count = count, sumBills = sum)
        }
        return result
    }

    fun resolveTotalResult(counters: Map<String, Long>): List<ZxReportBuilder.OperationAggregate> {
        val result = mutableListOf<ZxReportBuilder.OperationAggregate>()
        for (op in operationsList()) {
            val count = counters[CounterKeyFormats.OPERATION_COUNT.format(op)] ?: 0L
            val baseSum = counters[CounterKeyFormats.OPERATION_SUM.format(op)] ?: 0L
            val discount = counters[CounterKeyFormats.DISCOUNT_SUM.format(op)] ?: 0L
            val markup = counters[CounterKeyFormats.MARKUP_SUM.format(op)] ?: 0L
            val adjustedSum = baseSum - discount + markup
            result += ZxReportBuilder.OperationAggregate(
                operation = op,
                count = count,
                sumBills = adjustedSum
            )
        }
        return result
    }

    private fun operationsList(): List<String> =
        listOf("OPERATION_SELL", "OPERATION_SELL_RETURN", "OPERATION_BUY", "OPERATION_BUY_RETURN")
}

