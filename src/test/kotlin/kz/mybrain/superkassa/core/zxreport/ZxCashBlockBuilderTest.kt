package kz.mybrain.superkassa.core.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.zxreport.ZxCashBlockBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class ZxCashBlockBuilderTest {

    @Test
    fun `resolveStartShiftNonNullableSums and resolveNonNullableSums return full operation set with zeros by default`() {
        val counters = emptyMap<String, Long>()

        val startShift = ZxCashBlockBuilder.resolveStartShiftNonNullableSums(counters)
        val endShift = ZxCashBlockBuilder.resolveNonNullableSums(counters)

        // В обоих списках должен быть полный набор операций ZX (4 элемента).
        assertEquals(4, startShift.size)
        assertEquals(4, endShift.size)

        startShift.forEach { (_, sum) -> assertEquals(0L, sum) }
        endShift.forEach { (_, sum) -> assertEquals(0L, sum) }
    }

    @Test
    fun `resolveNonNullableSums prefers explicit NON_NULLABLE_SUM and otherwise uses start plus delta`() {
        val counters = mutableMapOf<String, Long>().apply {
            // Для SELL заданы все счётчики.
            put(CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format("OPERATION_SELL"), 10_000L)
            put(CounterKeyFormats.OPERATION_SUM.format("OPERATION_SELL"), 2_000L)
            // Явное конечное значение необнуляемой суммы.
            put(CounterKeyFormats.NON_NULLABLE_SUM.format("OPERATION_SELL"), 12_345L)

            // Для SELL_RETURN конечное значение не задано, считаем как start + delta.
            put(CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format("OPERATION_SELL_RETURN"), 5_000L)
            put(CounterKeyFormats.OPERATION_SUM.format("OPERATION_SELL_RETURN"), 500L)
        }

        val startShift = ZxCashBlockBuilder.resolveStartShiftNonNullableSums(counters).toMap()
        val endShift = ZxCashBlockBuilder.resolveNonNullableSums(counters).toMap()

        // Стартовые значения читаются напрямую из START_SHIFT_*.
        assertEquals(10_000L, startShift["OPERATION_SELL"])
        assertEquals(5_000L, startShift["OPERATION_SELL_RETURN"])

        // Для SELL используется явное NON_NULLABLE_SUM, а не start + delta.
        assertEquals(12_345L, endShift["OPERATION_SELL"])

        // Для SELL_RETURN значение считается как start + delta.
        assertEquals(5_000L + 500L, endShift["OPERATION_SELL_RETURN"])
    }
}

