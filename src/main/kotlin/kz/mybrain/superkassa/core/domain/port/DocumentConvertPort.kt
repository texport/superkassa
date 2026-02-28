package kz.mybrain.superkassa.core.domain.port

/**
 * Порт конвертации HTML чека в разные форматы для доставки.
 */
interface DocumentConvertPort {
    /** Конвертирует HTML в PDF. */
    fun htmlToPdf(html: String): ByteArray

    /** Конвертирует HTML в PNG-изображение. */
    fun htmlToImage(html: String): ByteArray

    /** Конвертирует HTML в ESC/POS команды для печати. */
    fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray
}
