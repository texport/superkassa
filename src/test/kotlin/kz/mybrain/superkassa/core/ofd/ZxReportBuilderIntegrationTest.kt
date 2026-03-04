package kz.mybrain.superkassa.core.ofd

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.zxreport.ZxReportBuilder
import kz.mybrain.superkassa.core.data.ofd.OfdRequestFactory
import kz.mybrain.superkassa.core.domain.model.VatGroup
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Интеграционные тесты zxReport против внешнего ZXReportBuilder из ofd-proto-codec.
 *
 * Цель: убедиться, что JSON, собранный нашим OfdRequestFactory.buildZxReportInternal,
 * корректно парсится ZXReportBuilder'ом (т.е. полностью валиден по схеме v203).
 */
class ZxReportBuilderIntegrationTest {

    private val externalZxReportBuilder =
        kz.mybrain.ofdcodec.ofd.kazakhtelecom.v203.codec.report.ZXReportBuilder()

    @Test
    fun `zxReport for simple SELL shift is accepted by external ZXReportBuilder`() {
        val now = System.currentTimeMillis()
        val openShift = now - 10_000
        val closeShift = now
        val shiftNo = 1

        val counters = mutableMapOf<String, Long>().apply {
            // Операция SELL: 2 чека по 1000 (итого 2000).
            put(CounterKeyFormats.OPERATION_COUNT.format("OPERATION_SELL"), 2L)
            put(CounterKeyFormats.OPERATION_SUM.format("OPERATION_SELL"), 2_000L)

            // Необнуляемые суммы на начало и конец смены.
            put(CounterKeyFormats.START_SHIFT_NON_NULLABLE_SUM.format("OPERATION_SELL"), 10_000L)
            put(CounterKeyFormats.NON_NULLABLE_SUM.format("OPERATION_SELL"), 12_000L)

            // Скидки/наценки.
            put(CounterKeyFormats.DISCOUNT_SUM.format("OPERATION_SELL"), 100L)
            put(CounterKeyFormats.MARKUP_SUM.format("OPERATION_SELL"), 50L)

            // Сводка по билетам (чекам) SELL.
            put(CounterKeyFormats.TICKET_TOTAL_COUNT.format("OPERATION_SELL"), 2L)
            put(CounterKeyFormats.TICKET_COUNT.format("OPERATION_SELL"), 2L)
            put(CounterKeyFormats.TICKET_SUM.format("OPERATION_SELL"), 2_000L)
            put(CounterKeyFormats.TICKET_DISCOUNT_SUM.format("OPERATION_SELL"), 100L)
            put(CounterKeyFormats.TICKET_MARKUP_SUM.format("OPERATION_SELL"), 50L)
            put(CounterKeyFormats.TICKET_CHANGE_SUM.format("OPERATION_SELL"), 0L)
            put(CounterKeyFormats.TICKET_OFFLINE_COUNT.format("OPERATION_SELL"), 0L)

            // Платежи: оба чека оплатили наличными.
            put(
                CounterKeyFormats.PAYMENT_SUM.format("OPERATION_SELL", "PAYMENT_CASH"),
                2_000L
            )
            put(
                CounterKeyFormats.PAYMENT_COUNT.format("OPERATION_SELL", "PAYMENT_CASH"),
                2L
            )

            // Касса: наличные и выручка.
            put(CounterKeyFormats.CASH_SUM, 2_000L)
            put(CounterKeyFormats.REVENUE_SUM, 2_000L)
            put(CounterKeyFormats.REVENUE_IS_NEGATIVE, 0L)

            // Внесения/изъятия (для moneyPlacements) в этом сценарии отсутствуют.
        }

        val zxInput = ZxReportBuilder.build(
            counters = counters,
            dateTimeMillis = now,
            shiftNumber = shiftNo,
            openShiftTimeMillis = openShift,
            closeShiftTimeMillis = closeShift
        )

        val zxJson = OfdRequestFactory.buildZxReportInternal(zxInput)

        // checksum должен присутствовать и быть 8-символьной hex-строкой.
        val checksum = zxJson["checksum"]!!.jsonPrimitive.content
        assertEquals(8, checksum.length)
        assertTrue(checksum.matches(Regex("^[0-9A-F]{8}$")))

        // startShiftNonNullableSums / nonNullableSums: по одному элементу на каждую операцию.
        val startShiftNonNullable = zxJson["startShiftNonNullableSums"]!!.jsonArray
        val nonNullable = zxJson["nonNullableSums"]!!.jsonArray
        assertEquals(4, startShiftNonNullable.size)
        assertEquals(4, nonNullable.size)

        // operations / discounts / markups / totalResult: полный набор операций (4 элемента).
        val operationsJson = zxJson["operations"]!!.jsonArray
        val discountsJson = zxJson["discounts"]!!.jsonArray
        val markupsJson = zxJson["markups"]!!.jsonArray
        val totalResultJson = zxJson["totalResult"]!!.jsonArray
        assertEquals(4, operationsJson.size)
        assertEquals(4, discountsJson.size)
        assertEquals(4, markupsJson.size)
        assertEquals(4, totalResultJson.size)

        fun findOp(array: kotlinx.serialization.json.JsonArray, op: String) =
            array.first { it.jsonObject["operation"]!!.jsonPrimitive.content == op }.jsonObject

        // Для OPERATION_SELL значения берутся из счётчиков.
        val opSell = findOp(operationsJson, "OPERATION_SELL")
        assertEquals(2L, opSell["count"]!!.jsonPrimitive.long)
        assertEquals(2_000L, opSell["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

        val discountSell = findOp(discountsJson, "OPERATION_SELL")
        assertEquals(2L, discountSell["count"]!!.jsonPrimitive.long)
        assertEquals(100L, discountSell["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

        val markupSell = findOp(markupsJson, "OPERATION_SELL")
        assertEquals(2L, markupSell["count"]!!.jsonPrimitive.long)
        assertEquals(50L, markupSell["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

        val totalResultSell = findOp(totalResultJson, "OPERATION_SELL")
        assertEquals(2L, totalResultSell["count"]!!.jsonPrimitive.long)
        assertEquals(2_000L - 100L + 50L, totalResultSell["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

        // Для остальных операций значения должны быть нулевыми.
        listOf("OPERATION_SELL_RETURN", "OPERATION_BUY", "OPERATION_BUY_RETURN").forEach { op ->
            val base = findOp(operationsJson, op)
            val disc = findOp(discountsJson, op)
            val mark = findOp(markupsJson, op)
            val total = findOp(totalResultJson, op)

            assertEquals(0L, base["count"]!!.jsonPrimitive.long)
            assertEquals(0L, base["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

            assertEquals(0L, disc["count"]!!.jsonPrimitive.long)
            assertEquals(0L, disc["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

            assertEquals(0L, mark["count"]!!.jsonPrimitive.long)
            assertEquals(0L, mark["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)

            assertEquals(0L, total["count"]!!.jsonPrimitive.long)
            assertEquals(0L, total["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        }

        // sections: хотя секционные счётчики отсутствуют, по умолчанию строится одна секция "1"
        // с полным набором операций.
        val sectionsJson = zxJson["sections"]!!.jsonArray
        assertEquals(1, sectionsJson.size)
        val firstSection = sectionsJson.first().jsonObject
        assertEquals("1", firstSection["sectionCode"]!!.jsonPrimitive.content)
        val sectionOps = firstSection["operations"]!!.jsonArray
        assertEquals(4, sectionOps.size)

        // ticketOperations: по одному элементу на каждую операцию, внутри payments — по одному на каждый тип оплаты.
        val ticketOps = zxJson["ticketOperations"]!!.jsonArray
        assertEquals(4, ticketOps.size)
        ticketOps.forEach { t ->
            val tObj = t.jsonObject
            val payments = tObj["payments"]!!.jsonArray
            assertEquals(5, payments.size)
        }

        // moneyPlacements: всегда два элемента (DEPOSIT/WITHDRAWAL), даже при нулевых счётчиках.
        val moneyPlacements = zxJson["moneyPlacements"]!!.jsonArray
        assertEquals(2, moneyPlacements.size)

        // taxes: по одному элементу на каждую VatGroup, в каждой — полный набор операций.
        val taxes = zxJson["taxes"]!!.jsonArray
        // В taxes идут только налоговые группы (без NO_VAT).
        assertEquals(VatGroup.values().size - 1, taxes.size)
        taxes.forEach { tax ->
            val taxObj = tax.jsonObject
            assertTrue(taxObj["operations"]!!.jsonArray.size == 4)
        }

        // Если JSON невалиден по схеме v203, build бросит исключение.
        val proto = externalZxReportBuilder.build(zxJson)
        assertNotNull(proto)
    }

    @Test
    fun `zxReport with tax aggregates is accepted by external ZXReportBuilder`() {
        val now = System.currentTimeMillis()
        val openShift = now - 10_000
        val closeShift = now
        val shiftNo = 1

        val counters = mutableMapOf<String, Long>().apply {
            // Операция SELL: одна операция для выручки/кассы.
            put(CounterKeyFormats.OPERATION_COUNT.format("OPERATION_SELL"), 1L)
            put(CounterKeyFormats.OPERATION_SUM.format("OPERATION_SELL"), 1_000L)
            put(CounterKeyFormats.CASH_SUM, 1_000L)
            put(CounterKeyFormats.REVENUE_SUM, 1_000L)
            put(CounterKeyFormats.REVENUE_IS_NEGATIVE, 0L)

            // Налоговые счётчики для группы VAT_16 по операции SELL.
            put(CounterKeyFormats.TAX_TURNOVER.format("VAT_16", "OPERATION_SELL"), 1_000L)
            put(CounterKeyFormats.TAX_SUM.format("VAT_16", "OPERATION_SELL"), 160L)
            put(
                CounterKeyFormats.TAX_TURNOVER_NO_TAX.format("VAT_16", "OPERATION_SELL"),
                840L
            )
        }

        val zxInput = ZxReportBuilder.build(
            counters = counters,
            dateTimeMillis = now,
            shiftNumber = shiftNo,
            openShiftTimeMillis = openShift,
            closeShiftTimeMillis = closeShift
        )

        val zxJson = OfdRequestFactory.buildZxReportInternal(zxInput)

        val proto = externalZxReportBuilder.build(zxJson)
        assertNotNull(proto)

        // Проверяем, что блок taxes содержит полный набор групп и операций,
        // а значения для VAT_16/SELL взяты из счётчиков.
        val taxes = zxJson["taxes"]!!.jsonArray
        assertEquals(VatGroup.values().size - 1, taxes.size)

        val vat16 = taxes.first {
            it.jsonObject["taxTypeCode"]!!.jsonPrimitive.content == "TAX_TYPE_VAT_16"
        }.jsonObject
        assertEquals(16_000, vat16["percent"]!!.jsonPrimitive.int)
        assertEquals(100, vat16["taxType"]!!.jsonPrimitive.int)

        val vat16Ops = vat16["operations"]!!.jsonArray
        assertEquals(4, vat16Ops.size)
        val sellOp = vat16Ops.first {
            it.jsonObject["operation"]!!.jsonPrimitive.content == "OPERATION_SELL"
        }.jsonObject
        assertEquals(1_000L, sellOp["turnover"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        assertEquals(840L, sellOp["turnoverWithoutTax"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        assertEquals(160L, sellOp["sum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
    }

    @Test
    fun `zxReport with money placements is accepted by external ZXReportBuilder`() {
        val now = System.currentTimeMillis()
        val openShift = now - 10_000
        val closeShift = now
        val shiftNo = 1

        val counters = mutableMapOf<String, Long>().apply {
            // Касса: начальное значение наличных.
            put(CounterKeyFormats.CASH_SUM, 2_000L)

            // Внесение наличных (DEPOSIT): одна операция на 500.
            put(
                CounterKeyFormats.MONEY_PLACEMENT_TOTAL_COUNT.format("MONEY_PLACEMENT_DEPOSIT"),
                1L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_COUNT.format("MONEY_PLACEMENT_DEPOSIT"),
                1L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_SUM.format("MONEY_PLACEMENT_DEPOSIT"),
                500L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_OFFLINE_COUNT.format("MONEY_PLACEMENT_DEPOSIT"),
                0L
            )

            // Изъятие наличных (WITHDRAWAL): одна операция на 300, ушедшая в офлайн.
            put(
                CounterKeyFormats.MONEY_PLACEMENT_TOTAL_COUNT.format("MONEY_PLACEMENT_WITHDRAWAL"),
                1L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_COUNT.format("MONEY_PLACEMENT_WITHDRAWAL"),
                1L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_SUM.format("MONEY_PLACEMENT_WITHDRAWAL"),
                300L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_OFFLINE_COUNT.format("MONEY_PLACEMENT_WITHDRAWAL"),
                1L
            )
        }

        val zxInput = ZxReportBuilder.build(
            counters = counters,
            dateTimeMillis = now,
            shiftNumber = shiftNo,
            openShiftTimeMillis = openShift,
            closeShiftTimeMillis = closeShift
        )

        val zxJson = OfdRequestFactory.buildZxReportInternal(zxInput)

        // Если JSON невалиден по схеме v203, build бросит исключение.
        val proto = externalZxReportBuilder.build(zxJson)
        assertNotNull(proto)
    }

    @Test
    fun `zxReport dateTime fields use system default timezone`() {
        val epochMillis = 1_700_000_000_000L
        val shiftNo = 1

        val counters = mutableMapOf<String, Long>()

        val zxInput = ZxReportBuilder.build(
            counters = counters,
            dateTimeMillis = epochMillis,
            shiftNumber = shiftNo,
            openShiftTimeMillis = epochMillis,
            closeShiftTimeMillis = epochMillis
        )

        val zxJson = OfdRequestFactory.buildZxReportInternal(zxInput)

        val dateTimeJson = zxJson["dateTime"]!!.jsonObject
        val dateJson = dateTimeJson["date"]!!.jsonObject
        val timeJson = dateTimeJson["time"]!!.jsonObject

        val actualZdt = ZonedDateTime.of(
            dateJson["year"]!!.jsonPrimitive.int,
            dateJson["month"]!!.jsonPrimitive.int,
            dateJson["day"]!!.jsonPrimitive.int,
            timeJson["hour"]!!.jsonPrimitive.int,
            timeJson["minute"]!!.jsonPrimitive.int,
            timeJson["second"]!!.jsonPrimitive.int,
            0,
            ZoneId.systemDefault()
        )

        val expectedZdt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault()
        )

        assertEquals(expectedZdt.year, actualZdt.year)
        assertEquals(expectedZdt.monthValue, actualZdt.monthValue)
        assertEquals(expectedZdt.dayOfMonth, actualZdt.dayOfMonth)
        assertEquals(expectedZdt.hour, actualZdt.hour)
        assertEquals(expectedZdt.minute, actualZdt.minute)
        assertEquals(expectedZdt.second, actualZdt.second)
    }
}
