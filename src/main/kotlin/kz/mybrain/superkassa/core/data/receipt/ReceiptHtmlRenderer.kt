package kz.mybrain.superkassa.core.data.receipt

import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.port.ReceiptRenderPort
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Рендерит чек в HTML для печати и доставки.
 */
class ReceiptHtmlRenderer : ReceiptRenderPort {

    override fun renderHtml(receipt: ReceiptRequest, doc: FiscalDocumentSnapshot): String {
        val dateStr = formatDate(doc.createdAt)
        val sign = doc.fiscalSign ?: doc.autonomousSign ?: "-"
        val totalStr = formatMoney(receipt.total)
        val itemsHtml = receipt.items.joinToString("") { item ->
            """
            <tr>
                <td>${escape(item.name)}</td>
                <td>${item.quantity}</td>
                <td>${formatMoney(item.price)}</td>
                <td>${formatMoney(item.sum)}</td>
            </tr>
            """.trimIndent()
        }
        val paymentsHtml = receipt.payments.joinToString("") { p ->
            val typeStr = when (p.type) {
                PaymentType.CASH -> "Наличные"
                PaymentType.CARD -> "Карта"
                PaymentType.ELECTRONIC -> "Электронно"
            }
            """
            <tr>
                <td colspan="3">$typeStr</td>
                <td>${formatMoney(p.sum)}</td>
            </tr>
            """.trimIndent()
        }
        val docNoStr = doc.docNo?.toString() ?: "-"
        return """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <title>Чек #$docNoStr</title>
    <style>
        body { font-family: monospace; font-size: 12px; margin: 16px; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 4px 8px; text-align: left; border-bottom: 1px solid #eee; }
        .header { text-align: center; margin-bottom: 12px; }
        .total { font-weight: bold; }
        .footer { margin-top: 12px; font-size: 10px; color: #666; }
    </style>
</head>
<body>
    <div class="header">
        <p><strong>ЧЕК</strong></p>
        <p>№ $docNoStr | $dateStr</p>
    </div>
    <table>
        <thead>
            <tr><th>Наименование</th><th>Кол-во</th><th>Цена</th><th>Сумма</th></tr>
        </thead>
        <tbody>
            $itemsHtml
            $paymentsHtml
            <tr class="total">
                <td colspan="3">ИТОГО</td>
                <td>$totalStr</td>
            </tr>
        </tbody>
    </table>
    <div class="footer">
        <p>Фискальный признак: $sign</p>
        ${if (doc.isAutonomous) "<p>Автономный режим</p>" else ""}
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun formatMoney(m: Money): String {
        val value = m.bills + m.coins / 100.0
        return "%.2f".format(value)
    }

    private fun formatDate(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val zoned = instant.atZone(ZoneId.systemDefault())
        return zoned.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
