package kz.mybrain.superkassa.core.data.receipt

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kz.mybrain.superkassa.core.domain.port.DocumentConvertPort
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

/**
 * Адаптер конвертации HTML в PDF, Image, ESC/POS.
 */
class DocumentConvertAdapter : DocumentConvertPort {

    override fun htmlToPdf(html: String): ByteArray {
        ByteArrayOutputStream().use { os ->
            val builder = PdfRendererBuilder()
            builder.useFastMode()
            builder.withHtmlContent(wrapHtml(html), null)
            builder.toStream(os)
            builder.run()
            return os.toByteArray()
        }
    }

    override fun htmlToImage(html: String): ByteArray {
        val pdf = htmlToPdf(html)
        val document = PDDocument.load(pdf)
        try {
            val renderer = PDFRenderer(document)
            val image: BufferedImage = renderer.renderImageWithDPI(0, 150f)
            ByteArrayOutputStream().use { os ->
                ImageIO.write(image, "PNG", os)
                return os.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    override fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray {
        val charsPerLine = when (paperWidthMm) {
            48 -> 24
            58 -> 32
            80 -> 48
            else -> 32
        }
        val text = html
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val lines = wrapText(text, charsPerLine)
        val sb = StringBuilder()
        sb.append(ESC_INIT)
        lines.forEach { line ->
            sb.append(line)
            sb.append('\n')
        }
        sb.append(ESC_CUT_FEED)
        return sb.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun wrapHtml(html: String): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<style>
body { font-family: sans-serif; font-size: 10pt; }
table { width: 100%; }
</style>
</head>
<body>
$html
</body>
</html>
        """.trimIndent()
    }

    private fun wrapText(text: String, width: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val chunk = if (remaining.length <= width) remaining else remaining.take(width)
            result.add(chunk.trim())
            remaining = if (remaining.length <= width) "" else remaining.drop(width).trimStart()
        }
        return result
    }

    companion object {
        private val ESC_INIT = "\u001B@"
        private val ESC_CUT_FEED = "\u001DVA\u0003"
    }
}
