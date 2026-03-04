package kz.mybrain.superkassa.core.application.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats

/**
 * Блок zxReport, отвечающий за:
 * - cashSum
 * - startShiftNonNullableSums
 * - nonNullableSums
 */
object ZxCashBlockBuilder {

    fun resolveCashSum(counters: Map<String, Long>): Long {
        // Предпочитаем явный счетчик CASH_SUM, если он есть.
        val explicitCash = counters[CounterKeyFormats.CASH_SUM]
        if (explicitCash != null) {
            return explicitCash
        }

        // Фолбэк на старую логику: сумма продаж по операции SELL.
        val sellKey = CounterKeyFormats.OPERATION_SUM.format("OPERATION_SELL")
        return counters[sellKey] ?: 0L
    }

    fun resolveNonNullableSums(counters: Map<String, Long>): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        for (op in operationsList()) {
            val explicitEndKey = CounterKeyFormats.NON_NULLABLE_SUM.format(op)
            val explicitEnd = counters[explicitEndKey]
            if (explicitEnd != null) {
                result += op to explicitEnd
            } else {
                val startKey = CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format(op)
                val opSumKey = CounterKeyFormats.OPERATION_SUM.format(op)
                val start = counters[startKey] ?: 0L
                val delta = counters[opSumKey] ?: 0L
                val endValue = start + delta
                result += op to endValue
            }
        }
        return result
    }

    fun resolveStartShiftNonNullableSums(counters: Map<String, Long>): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        for (op in operationsList()) {
            val key = CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format(op)
            val sum = counters[key] ?: 0L
            result += op to sum
        }
        return result
    }

    private fun operationsList(): List<String> =
        listOf("OPERATION_SELL", "OPERATION_SELL_RETURN", "OPERATION_BUY", "OPERATION_BUY_RETURN")
}

