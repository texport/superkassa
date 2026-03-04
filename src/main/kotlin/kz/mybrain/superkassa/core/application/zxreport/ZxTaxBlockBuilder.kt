package kz.mybrain.superkassa.core.application.zxreport

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.domain.model.VatGroup

/**
 * Блок zxReport, отвечающий за агрегаты по налогам (taxes).
 *
 * Источником данных служат счётчики TAX_*:
 * - [CounterKeyFormats.TAX_TURNOVER]
 * - [CounterKeyFormats.TAX_TURNOVER_NO_TAX]
 * - [CounterKeyFormats.TAX_SUM]
 */
object ZxTaxBlockBuilder {

    private val operations = listOf(
        "OPERATION_SELL",
        "OPERATION_SELL_RETURN",
        "OPERATION_BUY",
        "OPERATION_BUY_RETURN"
    )

    /**
     * Строит список налоговых агрегатов по всем группам НДС.
     *
     * Для каждой [VatGroup] заполняется:
     * - type  — строковый код TAX_TYPE_* по протоколу;
     * - percent — ставка НДС;
     * - operations — агрегаты по операциям (turnover, turnoverWithoutTax, taxSum).
     *
     * Налог добавляется в результат, только если по нему есть ненулевые значения.
     */
    fun resolveTaxes(counters: Map<String, Long>): List<ZxReportBuilder.TaxAggregate> {
        val result = mutableListOf<ZxReportBuilder.TaxAggregate>()

        for (group in VatGroup.values()) {
            // NO_VAT не считается налогом и не должен попадать в блок taxes ZXReport.
            if (group == VatGroup.NO_VAT) continue

            val type = taxTypeEnumCodeForGroup(group)
            val typeCode = taxTypeCodeForGroup(group)
            val percent = percentForGroup(group)

            val ops = operations.map { op ->
                val turnover = counters[CounterKeyFormats.TAX_TURNOVER.format(group.name, op)] ?: 0L
                val sum = counters[CounterKeyFormats.TAX_SUM.format(group.name, op)] ?: 0L
                val turnoverNoTax =
                    counters[CounterKeyFormats.TAX_TURNOVER_NO_TAX.format(group.name, op)] ?: 0L

                ZxReportBuilder.TaxOperationAggregate(
                    operation = op,
                    turnoverBills = turnover,
                    turnoverWithoutTaxBills = turnoverNoTax,
                    taxSumBills = sum
                )
            }

            result += ZxReportBuilder.TaxAggregate(
                taxType = type,
                taxTypeCode = typeCode,
                percent = percent,
                operations = ops
            )
        }

        return result
    }

    private fun taxTypeCodeForGroup(group: VatGroup): String =
        when (group) {
            VatGroup.NO_VAT -> "TAX_TYPE_NO_VAT"
            VatGroup.VAT_0 -> "TAX_TYPE_VAT_0"
            VatGroup.VAT_5 -> "TAX_TYPE_VAT_5"
            VatGroup.VAT_10 -> "TAX_TYPE_VAT_10"
            VatGroup.VAT_16 -> "TAX_TYPE_VAT_16"
        }

    private fun taxTypeEnumCodeForGroup(@Suppress("UNUSED_PARAMETER") group: VatGroup): Int =
        // По протоколу ZXReport::Tax.type (TaxTypeEnum) для НДС используем
        // единый тип 100.
        100

    private fun percentForGroup(group: VatGroup): Int =
        when (group) {
            // Значения в тысячных долях: 12000 == 12.0%
            VatGroup.NO_VAT -> 0          // не используется, но оставляем для полноты
            VatGroup.VAT_0 -> 0
            VatGroup.VAT_5 -> 5_000
            VatGroup.VAT_10 -> 10_000
            VatGroup.VAT_16 -> 16_000
        }
}

