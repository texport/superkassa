package kz.mybrain.superkassa.core.domain.port

import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest

/**
 * Порт рендеринга чека в HTML (для печати, PDF, доставки).
 */
interface ReceiptRenderPort {
    /**
     * Рендерит чек в HTML.
     * @param receipt Данные чека (позиции, оплата, итог).
     * @param doc Фискальный документ (номер, подпись, дата).
     * @return HTML-строка чека.
     */
    fun renderHtml(receipt: ReceiptRequest, doc: FiscalDocumentSnapshot): String
}
