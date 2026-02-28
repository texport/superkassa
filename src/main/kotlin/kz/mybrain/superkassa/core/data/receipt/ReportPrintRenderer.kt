package kz.mybrain.superkassa.core.data.receipt

import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Формирует печатные версии (HTML) для X-отчёта, открытия/закрытия смены, внесения/изъятия.
 */
object ReportPrintRenderer {

    private fun formatDate(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
    }

    /** Сумма в тенге (totalAmount в документах хранится как bills). */
    private fun formatAmount(bills: Long): String = "%.2f".format(bills.toDouble())

    private fun htmlPage(title: String, bodyContent: String): String = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <title>$title</title>
    <style>
        body { font-family: monospace; font-size: 12px; margin: 16px; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 4px 8px; text-align: left; border-bottom: 1px solid #eee; }
        .header { text-align: center; margin-bottom: 12px; }
        .footer { margin-top: 12px; font-size: 10px; color: #666; }
    </style>
</head>
<body>
    $bodyContent
</body>
</html>
    """.trimIndent()

    /** Печатная форма X-отчёта (смена не закрывается). */
    fun renderXReportHtml(shift: ShiftInfo, counters: Map<String, Long>): String {
        val rows = formatCounterRows(counters)
        val body = """
    <div class="header"><p><strong>X-ОТЧЁТ</strong></p></div>
    <p>Смена № ${shift.shiftNo}</p>
    <p>Открыта: ${formatDate(shift.openedAt)}</p>
    <table><thead><tr><th>Показатель</th><th>Значение</th></tr></thead>
    <tbody>$rows</tbody></table>
    <div class="footer"><p>Печатная форма X-отчёта. Смена не закрыта.</p></div>
        """.trimIndent()
        return htmlPage("X-отчёт", body)
    }

    /** Печатная форма открытия смены. */
    fun renderOpenShiftHtml(shift: ShiftInfo): String {
        val body = """
    <div class="header"><p><strong>ОТКРЫТИЕ СМЕНЫ</strong></p></div>
    <p>Смена № ${shift.shiftNo}</p>
    <p>Время открытия: ${formatDate(shift.openedAt)}</p>
    <div class="footer"><p>Смена открыта.</p></div>
        """.trimIndent()
        return htmlPage("Открытие смены", body)
    }

    /** Печатная форма закрытия смены (Z-отчёт). */
    fun renderCloseShiftHtml(shift: ShiftInfo, counters: Map<String, Long>): String {
        val rows = formatCounterRows(counters)
        val closedStr = shift.closedAt?.let { formatDate(it) } ?: "-"
        val body = """
    <div class="header"><p><strong>Z-ОТЧЁТ (ЗАКРЫТИЕ СМЕНЫ)</strong></p></div>
    <p>Смена № ${shift.shiftNo}</p>
    <p>Открыта: ${formatDate(shift.openedAt)}</p>
    <p>Закрыта: $closedStr</p>
    <table><thead><tr><th>Показатель</th><th>Значение</th></tr></thead>
    <tbody>$rows</tbody></table>
    <div class="footer"><p>Смена закрыта.</p></div>
        """.trimIndent()
        return htmlPage("Z-отчёт", body)
    }

    /** Печатная форма внесения или изъятия наличных. */
    fun renderCashOperationHtml(doc: FiscalDocumentSnapshot): String {
        val title = when (doc.docType) {
            "CASH_IN" -> "ВНЕСЕНИЕ НАЛИЧНЫХ"
            "CASH_OUT" -> "ИЗЪЯТИЕ НАЛИЧНЫХ"
            else -> "ОПЕРАЦИЯ С НАЛИЧНЫМИ"
        }
        val sumStr = doc.totalAmount?.let { formatAmount(it) } ?: "0.00"
        val body = """
    <div class="header"><p><strong>$title</strong></p></div>
    <p>Дата и время: ${formatDate(doc.createdAt)}</p>
    <p>Сумма: $sumStr ${doc.currency ?: "KZT"}</p>
    <p>Документ № ${doc.docNo ?: doc.id}</p>
    <div class="footer"><p>Печатная форма операции.</p></div>
        """.trimIndent()
        return htmlPage(title, body)
    }

    private fun formatCounterRows(counters: Map<String, Long>): String {
        val labels = mapOf(
            "operation.OPERATION_SELL.count" to "Продажи (кол-во операций)",
            "operation.OPERATION_SELL.sum" to "Продажи (сумма, тг)",
            "operation.OPERATION_SELL_RETURN.count" to "Возвраты продаж (кол-во)",
            "operation.OPERATION_SELL_RETURN.sum" to "Возвраты продаж (сумма, тг)",
            "operation.OPERATION_BUY.count" to "Покупки (кол-во)",
            "operation.OPERATION_BUY.sum" to "Покупки (сумма, тг)",
            "operation.OPERATION_BUY_RETURN.count" to "Возвраты покупок (кол-во)",
            "operation.OPERATION_BUY_RETURN.sum" to "Возвраты покупок (сумма, тг)",
            "ticket.OPERATION_SELL.total_count" to "Чеков продажи",
            "ticket.OPERATION_SELL.offline_count" to "Чеков продажи (офлайн)"
        )
        return counters.entries
            .filter { it.value != 0L }
            .sortedBy { it.key }
            .joinToString("") { (key, value) ->
                val label = labels[key] ?: key
                val display = if (key.contains(".sum")) formatAmount(value) else value.toString()
                "<tr><td>$label</td><td>$display</td></tr>"
            }
            .ifEmpty { "<tr><td colspan=\"2\">Нет данных</td></tr>" }
    }
}
