package kz.mybrain.superkassa.core.ofd

import kz.mybrain.superkassa.core.application.policy.CounterKeyFormats
import kz.mybrain.superkassa.core.application.zxreport.ZxReportBuilder
import kz.mybrain.superkassa.core.data.ofd.OfdCodecService
import kz.mybrain.superkassa.core.data.ofd.OfdRequestFactory
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Интеграционные тесты REPORT/CLOSE_SHIFT с полным zxReport.
 *
 * Цель: проверить, что:
 * - zxReport внутри REPORT/CLOSE_SHIFT содержит корректные cashSum и moneyPlacements;
 * - все временные поля (report.dateTime, zxReport.dateTime/openShiftTime/closeShiftTime, closeShift.closeTime)
 *   сериализуются в локальной временной зоне (ZoneId.systemDefault());
 * - сформированный JSON успешно кодируется через OfdCodecService (т.е. валиден по протоколу ofd-proto-codec).
 */
class ReportAndCloseShiftZxReportIntegrationTest {

    private val codecService = OfdCodecService()

    private fun buildServicePayload(now: Long): JsonObject {
        val serviceInfo = OfdServiceInfo(
            orgTitle = "Test Org",
            orgAddress = "Test Address",
            orgAddressKz = "Test Address KZ",
            orgInn = "123456789012",
            orgOkved = "47301",
            geoLatitude = 1,
            geoLongitude = 1,
            geoSource = "TEST"
        )
        return OfdRequestFactory.buildServicePayload(
            serviceInfo = serviceInfo,
            registrationNumber = "RN-1",
            factoryNumber = "FN-1",
            systemId = "SYS-1",
            offlineBeginMillis = now - 1_000,
            offlineEndMillis = now
        )
    }

    @Test
    fun `REPORT_X request with zxReport is accepted by codec and contains expected cashSum moneyPlacements and local times`() {
        val now = 1_700_000_000_000L
        val openShift = now - 10_000
        val shiftNo = 1

        val counters = mutableMapOf<String, Long>().apply {
            // Касса: начальное значение наличных и выручка.
            put(CounterKeyFormats.CASH_SUM, 2_000L)
            put(CounterKeyFormats.REVENUE_SUM, 2_000L)
            put(CounterKeyFormats.REVENUE_IS_NEGATIVE, 0L)

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
            closeShiftTimeMillis = null
        )

        val serviceBlock = buildServicePayload(now)

        val request = OfdRequestFactory.buildReportRequest(
            ofdId = "KAZAKHTELECOM",
            protocolVersion = "203",
            deviceId = 1L,
            token = 12345L,
            reqNum = 1,
            reportType = "REPORT_X",
            zxReport = zxInput,
            serviceBlock = serviceBlock
        )

        // Проверяем, что кодек принимает полный REQUEST с REPORT_X и zxReport.
        val encoded = codecService.encode(request)
        assertNotNull(encoded)

        val payload = request["payload"]!!.jsonObject
        val report = payload["report"]!!.jsonObject
        val zxJson = report["zxReport"]!!.jsonObject

        val checksum = zxJson["checksum"]!!.jsonPrimitive.content
        assertEquals(8, checksum.length)
        assertTrue(checksum.matches(Regex("^[0-9A-F]{8}$")))

        // cashSum
        val cashSum = zxJson["cashSum"]!!.jsonObject
        assertEquals(2_000L, cashSum["bills"]!!.jsonPrimitive.long)
        assertEquals(0, cashSum["coins"]!!.jsonPrimitive.int)

        // moneyPlacements
        val moneyArray = zxJson["moneyPlacements"]!!.jsonArray
        assertEquals(2, moneyArray.size)

        val deposit = moneyArray.first { it.jsonObject["operation"]!!.jsonPrimitive.content == "MONEY_PLACEMENT_DEPOSIT" }.jsonObject
        assertEquals(1L, deposit["operationsTotalCount"]!!.jsonPrimitive.long)
        assertEquals(1L, deposit["operationsCount"]!!.jsonPrimitive.long)
        assertEquals(500L, deposit["operationsSum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        assertEquals(0L, deposit["offlineCount"]!!.jsonPrimitive.long)

        val withdrawal = moneyArray.first { it.jsonObject["operation"]!!.jsonPrimitive.content == "MONEY_PLACEMENT_WITHDRAWAL" }.jsonObject
        assertEquals(1L, withdrawal["operationsTotalCount"]!!.jsonPrimitive.long)
        assertEquals(1L, withdrawal["operationsCount"]!!.jsonPrimitive.long)
        assertEquals(300L, withdrawal["operationsSum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        assertEquals(1L, withdrawal["offlineCount"]!!.jsonPrimitive.long)

        // Время отчёта и zxReport должны соответствовать now/openShift в локальной зоне.
        val expectedZdt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(now),
            ZoneId.systemDefault()
        )
        val expectedOpenZdt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(openShift),
            ZoneId.systemDefault()
        )

        fun extractZdt(obj: JsonObject, field: String): ZonedDateTime {
            val dt = obj[field]!!.jsonObject
            val date = dt["date"]!!.jsonObject
            val time = dt["time"]!!.jsonObject
            return ZonedDateTime.of(
                date["year"]!!.jsonPrimitive.int,
                date["month"]!!.jsonPrimitive.int,
                date["day"]!!.jsonPrimitive.int,
                time["hour"]!!.jsonPrimitive.int,
                time["minute"]!!.jsonPrimitive.int,
                time["second"]!!.jsonPrimitive.int,
                0,
                ZoneId.systemDefault()
            )
        }

        val reportDateTime = extractZdt(report, "dateTime")
        val zxDateTime = extractZdt(zxJson, "dateTime")
        val zxOpenShiftTime = extractZdt(zxJson, "openShiftTime")

        assertEquals(expectedZdt.year, reportDateTime.year)
        assertEquals(expectedZdt.hour, reportDateTime.hour)
        assertEquals(expectedZdt.year, zxDateTime.year)
        assertEquals(expectedZdt.hour, zxDateTime.hour)
        assertEquals(expectedOpenZdt.year, zxOpenShiftTime.year)
        assertEquals(expectedOpenZdt.hour, zxOpenShiftTime.hour)
    }

    @Test
    fun `CLOSE_SHIFT request with zxReport is accepted by codec and contains expected cashSum moneyPlacements and local times`() {
        val now = 1_700_000_100_000L
        val openShift = now - 10_000
        val shiftNo = 2

        val counters = mutableMapOf<String, Long>().apply {
            // Касса: значение наличных на конец смены.
            put(CounterKeyFormats.CASH_SUM, 5_000L)

            // Внесение и изъятие для moneyPlacements.
            put(
                CounterKeyFormats.MONEY_PLACEMENT_TOTAL_COUNT.format("MONEY_PLACEMENT_DEPOSIT"),
                2L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_COUNT.format("MONEY_PLACEMENT_DEPOSIT"),
                2L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_SUM.format("MONEY_PLACEMENT_DEPOSIT"),
                1_500L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_OFFLINE_COUNT.format("MONEY_PLACEMENT_DEPOSIT"),
                0L
            )

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
                500L
            )
            put(
                CounterKeyFormats.MONEY_PLACEMENT_OFFLINE_COUNT.format("MONEY_PLACEMENT_WITHDRAWAL"),
                0L
            )
        }

        val zxInput = ZxReportBuilder.build(
            counters = counters,
            dateTimeMillis = now,
            shiftNumber = shiftNo,
            openShiftTimeMillis = openShift,
            closeShiftTimeMillis = now
        )
        val zxJson = OfdRequestFactory.buildZxReportInternal(zxInput)

        val serviceBlock = buildServicePayload(now)

        val request = OfdRequestFactory.buildCloseShiftRequest(
            ofdId = "KAZAKHTELECOM",
            protocolVersion = "203",
            deviceId = 1L,
            token = 12345L,
            reqNum = 2,
            closeTimeMillis = now,
            frShiftNumber = shiftNo,
            zxReport = zxJson,
            serviceBlock = serviceBlock
        )

        // Проверяем, что кодек принимает полный REQUEST с CLOSE_SHIFT и zReport.
        val encoded = codecService.encode(request)
        assertNotNull(encoded)

        val payload = request["payload"]!!.jsonObject
        val closeShift = payload["closeShift"]!!.jsonObject
        val zReport = closeShift["zReport"]!!.jsonObject

        val checksum = zReport["checksum"]!!.jsonPrimitive.content
        assertEquals(8, checksum.length)
        assertTrue(checksum.matches(Regex("^[0-9A-F]{8}$")))

        // cashSum
        val cashSum = zReport["cashSum"]!!.jsonObject
        assertEquals(5_000L, cashSum["bills"]!!.jsonPrimitive.long)
        assertEquals(0, cashSum["coins"]!!.jsonPrimitive.int)

        // moneyPlacements
        val moneyArray = zReport["moneyPlacements"]!!.jsonArray
        assertEquals(2, moneyArray.size)

        val deposit = moneyArray.first { it.jsonObject["operation"]!!.jsonPrimitive.content == "MONEY_PLACEMENT_DEPOSIT" }.jsonObject
        assertEquals(2L, deposit["operationsTotalCount"]!!.jsonPrimitive.long)
        assertEquals(2L, deposit["operationsCount"]!!.jsonPrimitive.long)
        assertEquals(1_500L, deposit["operationsSum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        assertEquals(0L, deposit["offlineCount"]!!.jsonPrimitive.long)

        val withdrawal = moneyArray.first { it.jsonObject["operation"]!!.jsonPrimitive.content == "MONEY_PLACEMENT_WITHDRAWAL" }.jsonObject
        assertEquals(1L, withdrawal["operationsTotalCount"]!!.jsonPrimitive.long)
        assertEquals(1L, withdrawal["operationsCount"]!!.jsonPrimitive.long)
        assertEquals(500L, withdrawal["operationsSum"]!!.jsonObject["bills"]!!.jsonPrimitive.long)
        assertEquals(0L, withdrawal["offlineCount"]!!.jsonPrimitive.long)

        // Времена: closeShift.closeTime и поля zReport должны соответствовать now/openShift в локальной зоне.
        val expectedZdt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(now),
            ZoneId.systemDefault()
        )
        val expectedOpenZdt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(openShift),
            ZoneId.systemDefault()
        )

        fun extractZdt(obj: JsonObject, field: String): ZonedDateTime {
            val dt = obj[field]!!.jsonObject
            val date = dt["date"]!!.jsonObject
            val time = dt["time"]!!.jsonObject
            return ZonedDateTime.of(
                date["year"]!!.jsonPrimitive.int,
                date["month"]!!.jsonPrimitive.int,
                date["day"]!!.jsonPrimitive.int,
                time["hour"]!!.jsonPrimitive.int,
                time["minute"]!!.jsonPrimitive.int,
                time["second"]!!.jsonPrimitive.int,
                0,
                ZoneId.systemDefault()
            )
        }

        val closeShiftTime = extractZdt(closeShift, "closeTime")
        val zReportDateTime = extractZdt(zReport, "dateTime")
        val zReportOpenShiftTime = extractZdt(zReport, "openShiftTime")
        val zReportCloseShiftTime = extractZdt(zReport, "closeShiftTime")

        assertEquals(expectedZdt.year, closeShiftTime.year)
        assertEquals(expectedZdt.hour, closeShiftTime.hour)
        assertEquals(expectedZdt.year, zReportDateTime.year)
        assertEquals(expectedZdt.hour, zReportDateTime.hour)
        assertEquals(expectedOpenZdt.year, zReportOpenShiftTime.year)
        assertEquals(expectedOpenZdt.hour, zReportOpenShiftTime.hour)
        assertEquals(expectedZdt.year, zReportCloseShiftTime.year)
        assertEquals(expectedZdt.hour, zReportCloseShiftTime.hour)
    }
}

