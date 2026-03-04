package kz.mybrain.superkassa.core.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.zxreport.ZxTaxBlockBuilder
import kz.mybrain.superkassa.core.domain.model.VatGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZxTaxBlockBuilderTest {

    @Test
    fun `resolveTaxes returns full matrix of vat groups and operations with zeros when counters are empty`() {
        val counters = emptyMap<String, Long>()

        val taxes = ZxTaxBlockBuilder.resolveTaxes(counters)

        // По одному элементу на каждую налоговую VatGroup (без NO_VAT).
        assertEquals(VatGroup.values().size - 1, taxes.size)

        taxes.forEach { tax ->
            // В каждом налоге — полный набор операций ZX (SELL, SELL_RETURN, BUY, BUY_RETURN).
            assertEquals(4, tax.operations.size)
            tax.operations.forEach { op ->
                assertEquals(0L, op.turnoverBills)
                assertEquals(0L, op.turnoverWithoutTaxBills)
                assertEquals(0L, op.taxSumBills)
            }
        }
    }

    @Test
    fun `resolveTaxes aggregates non-zero counters and maps type code and percent`() {
        val counters = mutableMapOf<String, Long>().apply {
            // Налоговые счётчики для VAT_16 по операции SELL.
            put(CounterKeyFormats.TAX_TURNOVER.format("VAT_16", "OPERATION_SELL"), 1_000L)
            put(CounterKeyFormats.TAX_TURNOVER_NO_TAX.format("VAT_16", "OPERATION_SELL"), 840L)
            put(CounterKeyFormats.TAX_SUM.format("VAT_16", "OPERATION_SELL"), 160L)
        }

        val taxes = ZxTaxBlockBuilder.resolveTaxes(counters)

        val vat16 = taxes.first { it.taxTypeCode == "TAX_TYPE_VAT_16" }
        assertEquals(16_000, vat16.percent)
        assertEquals(100, vat16.taxType)

        val sellOp = vat16.operations.first { it.operation == "OPERATION_SELL" }
        assertEquals(1_000L, sellOp.turnoverBills)
        assertEquals(840L, sellOp.turnoverWithoutTaxBills)
        assertEquals(160L, sellOp.taxSumBills)

        // Для остальных операций по VAT_16 значения должны быть нулевыми.
        vat16.operations.filter { it.operation != "OPERATION_SELL" }.forEach { op ->
            assertEquals(0L, op.turnoverBills)
            assertEquals(0L, op.turnoverWithoutTaxBills)
            assertEquals(0L, op.taxSumBills)
        }
    }
}

