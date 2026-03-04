package kz.mybrain.superkassa.core.data.ofd

import kz.mybrain.superkassa.core.application.zxreport.ZxReportBuilder
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.OfdServiceInfo
import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.UnitOfMeasurement
import kz.mybrain.superkassa.core.domain.model.VatGroup
import kz.mybrain.superkassa.core.domain.tax.TaxCalculationService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.CRC32

/**
 * Фабрика JSON-запросов к ОФД.
 */
object OfdRequestFactory {
    /**
     * Строит JSON-блок payload.service для встраивания в команды TICKET, MONEY_PLACEMENT и др.
     * security_stats — координаты из базы (при добавлении кассы), offline_period, get_reg_info, reg_info.
     */
    fun buildServicePayload(
        serviceInfo: OfdServiceInfo,
        registrationNumber: String,
        factoryNumber: String,
        systemId: String,
        offlineBeginMillis: Long,
        offlineEndMillis: Long
    ): JsonObject {
        val begin = toDateTime(offlineBeginMillis)
        val end = toDateTime(offlineEndMillis)
        return buildJsonObject {
            put("getRegInfo", JsonPrimitive(true))
            put(
                "offlinePeriod",
                buildJsonObject {
                    put("beginTime", begin)
                    put("endTime", end)
                }
            )
            put(
                "securityStats",
                buildJsonObject {
                    put(
                        "geoPosition",
                        buildJsonObject {
                            put("latitude", JsonPrimitive(serviceInfo.geoLatitude))
                            put("longitude", JsonPrimitive(serviceInfo.geoLongitude))
                            put("source", JsonPrimitive(serviceInfo.geoSource))
                        }
                    )
                }
            )
            put(
                "regInfo",
                buildJsonObject {
                    put(
                        "kkm",
                        buildJsonObject {
                            put("fnsKkmId", JsonPrimitive(registrationNumber))
                            put("serialNumber", JsonPrimitive(factoryNumber))
                            put("kkmId", JsonPrimitive(systemId))
                        }
                    )
                    put(
                        "org",
                        buildJsonObject {
                            put("title", JsonPrimitive(serviceInfo.orgTitle))
                            put("address", JsonPrimitive(serviceInfo.orgAddress))
                            put("addressKz", JsonPrimitive(serviceInfo.orgAddressKz))
                            put("inn", JsonPrimitive(serviceInfo.orgInn))
                            put("okved", JsonPrimitive(serviceInfo.orgOkved))
                        }
                    )
                }
            )
        }
    }

    fun buildServiceRequest(
        ofdId: String,
        protocolVersion: String,
        commandType: String,
        deviceId: Long,
        token: Long,
        reqNum: Int,
        offlineBeginMillis: Long,
        offlineEndMillis: Long,
        registrationNumber: String,
        factoryNumber: String,
        systemId: String,
        serviceInfo: OfdServiceInfo
    ): JsonObject {
        val servicePayload = buildServicePayload(
            serviceInfo, registrationNumber, factoryNumber, systemId,
            offlineBeginMillis, offlineEndMillis
        )
        return buildJsonObject {
            put("ofdId", JsonPrimitive(ofdId))
            put("protocolVersion", JsonPrimitive(protocolVersion))
            put("messageType", JsonPrimitive("REQUEST"))
            put("commandType", JsonPrimitive(commandType))
            put(
                "header",
                buildJsonObject {
                    put("deviceId", JsonPrimitive(deviceId))
                    put("token", JsonPrimitive(token))
                    put("reqNum", JsonPrimitive(reqNum))
                }
            )
            put(
                "payload",
                buildJsonObject { put("service", servicePayload) }
            )
        }
    }

    fun buildTicketRequest(
        ofdId: String,
        protocolVersion: String,
        deviceId: Long,
        token: Long,
        reqNum: Int,
        request: ReceiptRequest,
        serviceBlock: JsonObject? = null
    ): JsonObject {
        val now = toDateTime(System.currentTimeMillis())
        val operationCode = when (request.operation) {
            ReceiptOperationType.SELL -> "OPERATION_SELL"
            ReceiptOperationType.SELL_RETURN -> "OPERATION_SELL_RETURN"
            ReceiptOperationType.BUY -> "OPERATION_BUY"
            ReceiptOperationType.BUY_RETURN -> "OPERATION_BUY_RETURN"
        }
        return buildJsonObject {
            put("ofdId", JsonPrimitive(ofdId))
            put("protocolVersion", JsonPrimitive(protocolVersion))
            put("messageType", JsonPrimitive("REQUEST"))
            put("commandType", JsonPrimitive("COMMAND_TICKET"))
            put(
                "header",
                buildJsonObject {
                    put("deviceId", JsonPrimitive(deviceId))
                    put("token", JsonPrimitive(token))
                    put("reqNum", JsonPrimitive(reqNum))
                }
            )
            put(
                "payload",
                buildJsonObject {
                    serviceBlock?.let { put("service", it) }
                    put(
                        "ticket",
                        buildJsonObject {
                            put("operation", JsonPrimitive(operationCode))
                            put("dateTime", now)
                            put(
                                "operator",
                                buildJsonObject {
                                    put("code", JsonPrimitive(1))
                                }
                            )

                            // Суммы скидки/наценки (для items и amounts)
                            val totalItemDiscount =
                                sumMoney(request.items.mapNotNull { it.discount })
                            val totalItemMarkup =
                                sumMoney(request.items.mapNotNull { it.markup })
                            val discountMoney = request.discount ?: totalItemDiscount
                            val markupMoney = request.markup ?: totalItemMarkup

                            put(
                                "items",
                                buildJsonArray {
                                    val (itemType, itemField) = when (request.operation) {
                                        ReceiptOperationType.SELL,
                                        ReceiptOperationType.BUY -> "ITEM_TYPE_COMMODITY" to "commodity"
                                        ReceiptOperationType.SELL_RETURN,
                                        ReceiptOperationType.BUY_RETURN -> "ITEM_TYPE_STORNO_COMMODITY" to "stornoCommodity"
                                    }

                                    val taxService = TaxCalculationService()

                                    request.items.forEach { item ->
                                        add(
                                            buildJsonObject {
                                                put("type", JsonPrimitive(itemType))
                                                put(
                                                    itemField,
                                                    buildJsonObject {
                                                        put("name", JsonPrimitive(item.name))
                                                        put("sectionCode", JsonPrimitive(item.sectionCode))
                                                        put("quantity", JsonPrimitive(item.quantity))
                                                        put(
                                                            "price",
                                                            moneyObject(item.price.bills, item.price.coins)
                                                        )
                                                        put(
                                                            "sum",
                                                            moneyObject(item.sum.bills, item.sum.coins)
                                                        )
                                                        put(
                                                            "measureUnitCode",
                                                            JsonPrimitive(item.measureUnitCode ?: UnitOfMeasurement.DEFAULT.code)
                                                        )
                                                        item.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
                                                            put("barcode", JsonPrimitive(barcode))
                                                        }
                                                        item.listExciseStamp?.takeIf { it.isNotEmpty() }?.let { stamps ->
                                                            put(
                                                                "list_excise_stamp",
                                                                buildJsonArray { stamps.forEach { add(JsonPrimitive(it)) } }
                                                            )
                                                        }
                                                        item.ntin?.takeIf { it.isNotBlank() }?.let { ntin ->
                                                            put("ntin", JsonPrimitive(ntin))
                                                        }

                                                        // Налог на уровне позиции (commodity.taxes[])
                                                        val itemVatGroup =
                                                            when (request.taxRegime) {
                                                                kz.mybrain.superkassa.core.domain.model.TaxRegime.NO_VAT ->
                                                                    VatGroup.NO_VAT

                                                                kz.mybrain.superkassa.core.domain.model.TaxRegime.VAT_PAYER,
                                                                kz.mybrain.superkassa.core.domain.model.TaxRegime.MIXED ->
                                                                    item.vatGroup ?: (request.defaultVatGroup ?: VatGroup.NO_VAT)
                                                            }

                                                        val taxResultForItem =
                                                            taxService.calculateTicketTaxes(
                                                                items = listOf(item),
                                                                taxRegime = request.taxRegime,
                                                                defaultVatGroup = request.defaultVatGroup ?: VatGroup.NO_VAT,
                                                                overrideVatGroup = itemVatGroup
                                                            )

                                                        if (taxResultForItem.ticketTaxes.isNotEmpty()) {
                                                            put(
                                                                "taxes",
                                                                buildJsonArray {
                                                                    taxResultForItem.ticketTaxes.forEach { line ->
                                                                        add(
                                                                            buildJsonObject {
                                                                                put(
                                                                                    "taxType",
                                                                                    JsonPrimitive(
                                                                                        taxTypeForGroup(
                                                                                            line.vatGroup
                                                                                        )
                                                                                    )
                                                                                )
                                                                                put(
                                                                                    "taxationType",
                                                                                    JsonPrimitive("TAXATION_TYPE_COMMON")
                                                                                )
                                                                                put(
                                                                                    "percent",
                                                                                    JsonPrimitive(line.percent)
                                                                                )
                                                                                put(
                                                                                    "sum",
                                                                                    moneyObject(
                                                                                        line.taxSum.bills,
                                                                                        line.taxSum.coins
                                                                                    )
                                                                                )
                                                                                put(
                                                                                    "isInTotalSum",
                                                                                    JsonPrimitive(true)
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }

                                    // Отдельные позиции скидки/наценки по протоколу (п. 4.1 плана)
                                    when (request.operation) {
                                        ReceiptOperationType.SELL_RETURN,
                                        ReceiptOperationType.BUY_RETURN -> {
                                            discountMoney?.takeIf { m -> m.bills != 0L || m.coins != 0 }?.let { m ->
                                                add(
                                                    buildJsonObject {
                                                        put("type", JsonPrimitive("ITEM_TYPE_STORNO_DISCOUNT"))
                                                        put(
                                                            "stornoDiscount",
                                                            buildJsonObject {
                                                                put("name", JsonPrimitive("Скидка"))
                                                                put("sum", moneyObject(m.bills, m.coins))
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                            markupMoney?.takeIf { m -> m.bills != 0L || m.coins != 0 }?.let { m ->
                                                add(
                                                    buildJsonObject {
                                                        put("type", JsonPrimitive("ITEM_TYPE_STORNO_MARKUP"))
                                                        put(
                                                            "stornoMarkup",
                                                            buildJsonObject {
                                                                put("name", JsonPrimitive("Наценка"))
                                                                put("sum", moneyObject(m.bills, m.coins))
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        ReceiptOperationType.SELL,
                                        ReceiptOperationType.BUY -> {
                                            discountMoney?.takeIf { m -> m.bills != 0L || m.coins != 0 }?.let { m ->
                                                add(
                                                    buildJsonObject {
                                                        put("type", JsonPrimitive("ITEM_TYPE_DISCOUNT"))
                                                        put(
                                                            "discount",
                                                            buildJsonObject {
                                                                put("name", JsonPrimitive("Скидка"))
                                                                put("sum", moneyObject(m.bills, m.coins))
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                            markupMoney?.takeIf { m -> m.bills != 0L || m.coins != 0 }?.let { m ->
                                                add(
                                                    buildJsonObject {
                                                        put("type", JsonPrimitive("ITEM_TYPE_MARKUP"))
                                                        put(
                                                            "markup",
                                                            buildJsonObject {
                                                                put("name", JsonPrimitive("Наценка"))
                                                                put("sum", moneyObject(m.bills, m.coins))
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            put(
                                "payments",
                                buildJsonArray {
                                    request.payments.forEach { payment ->
                                        val paymentType = when (payment.type) {
                                            PaymentType.CASH -> "PAYMENT_CASH"
                                            PaymentType.CARD -> "PAYMENT_CARD"
                                            PaymentType.ELECTRONIC -> "PAYMENT_ELECTRONIC"
                                        }
                                        add(
                                            buildJsonObject {
                                                put("type", JsonPrimitive(paymentType))
                                                put(
                                                    "sum",
                                                    moneyObject(
                                                        payment.sum.bills,
                                                        payment.sum.coins
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            )
                            put(
                                "amounts",
                                buildJsonObject {
                                    put(
                                        "total",
                                        moneyObject(
                                            request.total.bills,
                                            request.total.coins
                                        )
                                    )
                                    val taken = request.taken ?: request.total
                                    put(
                                        "taken",
                                        moneyObject(
                                            taken.bills,
                                            taken.coins
                                        )
                                    )
                                    // Сдачу в чеке в ОФД не передаём; при необходимости считается на кассе

                                    discountMoney?.let { m ->
                                        put(
                                            "discount",
                                            moneyObject(m.bills, m.coins)
                                        )
                                    }
                                    markupMoney?.let { m ->
                                        put(
                                            "markup",
                                            moneyObject(m.bills, m.coins)
                                        )
                                    }
                                }
                            )

                            val parent = request.parentTicket
                            if (parent != null &&
                                (request.operation == ReceiptOperationType.SELL_RETURN ||
                                 request.operation == ReceiptOperationType.BUY_RETURN)
                            ) {
                                put(
                                    "parentTicket",
                                    buildJsonObject {
                                        put(
                                            "parentTicketNumber",
                                            JsonPrimitive(parent.parentTicketNumber)
                                        )
                                        put(
                                            "parentTicketDateTime",
                                            toDateTime(parent.parentTicketDateTimeMillis)
                                        )
                                        put("kgdKkmId", JsonPrimitive(parent.kgdKkmId))
                                        put(
                                            "parentTicketTotal",
                                            moneyObject(
                                                parent.parentTicketTotal.bills,
                                                parent.parentTicketTotal.coins
                                            )
                                        )
                                        put(
                                            "parentTicketIsOffline",
                                            JsonPrimitive(parent.parentTicketIsOffline)
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    fun buildMoneyPlacementRequest(
        ofdId: String,
        protocolVersion: String,
        deviceId: Long,
        token: Long,
        reqNum: Int,
        docType: String,
        amountBills: Long,
        createdAtMillis: Long,
        serviceBlock: JsonObject
    ): JsonObject {
        val operation = when (docType) {
            "CASH_IN" -> "MONEY_PLACEMENT_DEPOSIT"
            "CASH_OUT" -> "MONEY_PLACEMENT_WITHDRAWAL"
            else -> "MONEY_PLACEMENT_DEPOSIT"
        }
        return buildJsonObject {
            put("ofdId", JsonPrimitive(ofdId))
            put("protocolVersion", JsonPrimitive(protocolVersion))
            put("messageType", JsonPrimitive("REQUEST"))
            put("commandType", JsonPrimitive("COMMAND_MONEY_PLACEMENT"))
            put(
                "header",
                buildJsonObject {
                    put("deviceId", JsonPrimitive(deviceId))
                    put("token", JsonPrimitive(token))
                    put("reqNum", JsonPrimitive(reqNum))
                }
            )
            put(
                "payload",
                buildJsonObject {
                    put("service", serviceBlock)
                    put(
                        "moneyPlacement",
                        buildJsonObject {
                            put("dateTime", toDateTime(createdAtMillis))
                            put("operation", JsonPrimitive(operation))
                            put("sum", moneyObject(amountBills, 0))
                            put(
                                "operator",
                                buildJsonObject {
                                    put("code", JsonPrimitive(1))
                                    put("name", JsonPrimitive("Оператор"))
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    fun buildReportRequest(
        ofdId: String,
        protocolVersion: String,
        deviceId: Long,
        token: Long,
        reqNum: Int,
        reportType: String,
        zxReport: ZxReportBuilder.ZxReportInput,
        serviceBlock: JsonObject
    ): JsonObject {
        val ofdIdNorm = ofdId.lowercase()
        return buildJsonObject {
            put("ofdId", JsonPrimitive(ofdIdNorm))
            put("protocolVersion", JsonPrimitive(protocolVersion))
            put("messageType", JsonPrimitive("REQUEST"))
            put("commandType", JsonPrimitive("COMMAND_REPORT"))
            put(
                "header",
                buildJsonObject {
                    put("deviceId", JsonPrimitive(deviceId))
                    put("token", JsonPrimitive(token))
                    put("reqNum", JsonPrimitive(reqNum))
                }
            )
            put(
                "payload",
                buildJsonObject {
                    put("service", serviceBlock)
                    put(
                        "report",
                        buildJsonObject {
                            put("reportType", JsonPrimitive(reportType))
                            put("dateTime", toDateTime(zxReport.dateTimeMillis))
                            put("isOffline", JsonPrimitive(false))
                            put(
                                "zxReport",
                                buildZxReportInternal(zxReport)
                            )
                        }
                    )
                }
            )
        }
    }

    fun buildCloseShiftRequest(
        ofdId: String,
        protocolVersion: String,
        deviceId: Long,
        token: Long,
        reqNum: Int,
        closeTimeMillis: Long,
        frShiftNumber: Int,
        zxReport: JsonObject,
        serviceBlock: JsonObject
    ): JsonObject {
        val ofdIdNorm = ofdId.lowercase()
        return buildJsonObject {
            put("ofdId", JsonPrimitive(ofdIdNorm))
            put("protocolVersion", JsonPrimitive(protocolVersion))
            put("messageType", JsonPrimitive("REQUEST"))
            put("commandType", JsonPrimitive("COMMAND_CLOSE_SHIFT"))
            put(
                "header",
                buildJsonObject {
                    put("deviceId", JsonPrimitive(deviceId))
                    put("token", JsonPrimitive(token))
                    put("reqNum", JsonPrimitive(reqNum))
                }
            )
            put(
                "payload",
                buildJsonObject {
                    put("service", serviceBlock)
                    put(
                        "closeShift",
                        buildJsonObject {
                            put("closeTime", toDateTime(closeTimeMillis))
                            put("isOffline", JsonPrimitive(false))
                            put("frShiftNumber", JsonPrimitive(frShiftNumber))
                            put("withdrawMoney", JsonPrimitive(false))
                            put("operator", buildJsonObject {
                                put("code", JsonPrimitive(1))
                                put("name", JsonPrimitive("Оператор"))
                            })
                            put("zReport", zxReport)
                        }
                    )
                }
            )
        }
    }

    fun buildZxReportInternal(zx: ZxReportBuilder.ZxReportInput): JsonObject {
        val base = buildJsonObject {
            put("dateTime", toDateTime(zx.dateTimeMillis))
            put("openShiftTime", toDateTime(zx.openShiftTimeMillis))
            zx.closeShiftTimeMillis?.let { put("closeShiftTime", toDateTime(it)) }
            put("shiftNumber", JsonPrimitive(zx.shiftNumber))
            put("cashSum", moneyObject(zx.cashSumBills, 0))
            put(
                "revenue",
                buildJsonObject {
                    put("sum", moneyObject(kotlin.math.abs(zx.revenueBills), kotlin.math.abs(zx.revenueCoins)))
                    put("isNegative", JsonPrimitive(zx.revenueBills < 0 || zx.revenueCoins < 0))
                }
            )
            put(
                "startShiftNonNullableSums",
                buildJsonArray {
                    zx.startShiftNonNullableSums.forEach { (op, sum) ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(op))
                                put("sum", moneyObject(sum, 0))
                            }
                        )
                    }
                }
            )
            put(
                "nonNullableSums",
                buildJsonArray {
                    zx.nonNullableSums.forEach { (op, sum) ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(op))
                                put("sum", moneyObject(sum, 0))
                            }
                        )
                    }
                }
            )
            put(
                "operations",
                buildJsonArray {
                    zx.operations.forEach { op ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(op.operation))
                                put("count", JsonPrimitive(op.count.toInt()))
                                put("sum", moneyObject(op.sumBills, 0))
                            }
                        )
                    }
                }
            )
            put(
                "sections",
                buildJsonArray {
                    zx.sections.forEach { section ->
                        add(
                            buildJsonObject {
                                put("sectionCode", JsonPrimitive(section.sectionCode))
                                put(
                                    "operations",
                                    buildJsonArray {
                                        section.operations.forEach { op ->
                                            add(
                                                buildJsonObject {
                                                    put("operation", JsonPrimitive(op.operation))
                                                    put("count", JsonPrimitive(op.count.toInt()))
                                                    put("sum", moneyObject(op.sumBills, 0))
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
            put(
                "discounts",
                buildJsonArray {
                    zx.discounts.forEach { op ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(op.operation))
                                put("count", JsonPrimitive(op.count.toInt()))
                                put("sum", moneyObject(op.sumBills, 0))
                            }
                        )
                    }
                }
            )
            put(
                "markups",
                buildJsonArray {
                    zx.markups.forEach { op ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(op.operation))
                                put("count", JsonPrimitive(op.count.toInt()))
                                put("sum", moneyObject(op.sumBills, 0))
                            }
                        )
                    }
                }
            )
            put(
                "totalResult",
                buildJsonArray {
                    zx.totalResult.forEach { op ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(op.operation))
                                put("count", JsonPrimitive(op.count.toInt()))
                                put("sum", moneyObject(op.sumBills, 0))
                            }
                        )
                    }
                }
            )
            put(
                "ticketOperations",
                buildJsonArray {
                    zx.ticketOperations.forEach { t ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(t.operation))
                                put("ticketsTotalCount", JsonPrimitive(t.ticketsTotalCount.toInt()))
                                put("ticketsCount", JsonPrimitive(t.ticketsCount.toInt()))
                                put("ticketsSum", moneyObject(t.ticketsSumBills, 0))
                                put(
                                    "payments",
                                    buildJsonArray {
                                        t.payments.forEach { p ->
                                            add(
                                                buildJsonObject {
                                                    put("payment", JsonPrimitive(p.payment))
                                                    put("sum", moneyObject(p.sumBills, 0))
                                                    put("count", JsonPrimitive(p.count.toInt()))
                                                }
                                            )
                                        }
                                    }
                                )
                                put("offlineCount", JsonPrimitive(t.offlineCount.toInt()))
                                put("discountSum", moneyObject(t.discountSumBills, 0))
                                put("markupSum", moneyObject(t.markupSumBills, 0))
                                put("changeSum", moneyObject(t.changeSumBills, 0))
                            }
                        )
                    }
                }
            )
            put(
                "moneyPlacements",
                buildJsonArray {
                    zx.moneyPlacements.forEach { m ->
                        add(
                            buildJsonObject {
                                put("operation", JsonPrimitive(m.operation))
                                put("operationsTotalCount", JsonPrimitive(m.operationsTotalCount.toInt()))
                                put("operationsCount", JsonPrimitive(m.operationsCount.toInt()))
                                put("operationsSum", moneyObject(m.operationsSumBills, 0))
                                put("offlineCount", JsonPrimitive(m.offlineCount.toInt()))
                            }
                        )
                    }
                }
            )
            put(
                "taxes",
                buildJsonArray {
                    zx.taxes.forEach { tax ->
                        add(
                            buildJsonObject {
                                put("taxType", JsonPrimitive(tax.taxType))
                                put("taxTypeCode", JsonPrimitive(tax.taxTypeCode))
                                put("percent", JsonPrimitive(tax.percent))
                                put(
                                    "operations",
                                    buildJsonArray {
                                        tax.operations.forEach { op ->
                                            add(
                                                buildJsonObject {
                                                    put("operation", JsonPrimitive(op.operation))
                                                    put(
                                                        "turnover",
                                                        moneyObject(op.turnoverBills, 0)
                                                    )
                                                    put(
                                                        "turnoverWithoutTax",
                                                        moneyObject(op.turnoverWithoutTaxBills, 0)
                                                    )
                                                    put(
                                                        "sum",
                                                        moneyObject(op.taxSumBills, 0)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

        val checksum = calculateZxReportChecksum(base)

        return JsonObject(
            base.toMutableMap().apply {
                put("checksum", JsonPrimitive(checksum))
            }
        )
    }

    private fun calculateZxReportChecksum(zxJson: JsonObject): String {
        val jsonString = buildJsonObject {
            // Embed zxReport under a fixed key to keep structure stable if needed.
            put("zxReport", zxJson)
        }.toString()
        val crc32 = CRC32()
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        crc32.update(bytes, 0, bytes.size)
        return java.lang.Long.toHexString(crc32.value)
            .padStart(8, '0')
            .uppercase()
    }

    private fun taxTypeForGroup(group: VatGroup): String {
        return when (group) {
            VatGroup.NO_VAT -> "TAX_TYPE_NO_VAT"
            VatGroup.VAT_0 -> "TAX_TYPE_VAT_0"
            VatGroup.VAT_5 -> "TAX_TYPE_VAT_5"
            VatGroup.VAT_10 -> "TAX_TYPE_VAT_10"
            VatGroup.VAT_16 -> "TAX_TYPE_VAT_16"
        }
    }

    private fun moneyObject(bills: Long, coins: Int): JsonObject {
        return buildJsonObject {
            put("bills", JsonPrimitive(bills))
            put("coins", JsonPrimitive(coins))
        }
    }

    private fun sumMoney(values: List<Money?>): Money? {
        if (values.isEmpty()) return null

        var totalCoins = 0L
        values.forEach { money ->
            if (money != null) {
                totalCoins += money.bills * 100 + money.coins
            }
        }

        if (totalCoins == 0L) return Money(0, 0)

        val bills = totalCoins / 100
        val coins = (totalCoins % 100).toInt()
        return Money(bills, coins)
    }

    private fun toDateTime(epochMillis: Long): JsonObject {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        return buildJsonObject {
            put(
                "date",
                buildJsonObject {
                    put("year", JsonPrimitive(zdt.year))
                    put("month", JsonPrimitive(zdt.monthValue))
                    put("day", JsonPrimitive(zdt.dayOfMonth))
                }
            )
            put(
                "time",
                buildJsonObject {
                    put("hour", JsonPrimitive(zdt.hour))
                    put("minute", JsonPrimitive(zdt.minute))
                    put("second", JsonPrimitive(zdt.second))
                }
            )
        }
    }
}
