package de.meinhelfer.tag.letters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import de.meinhelfer.tag.media.DocumentStore
import java.io.File

/** Ein druckfertiges Schreiben. Alle Texte sind schon vom Nutzer geprueft und gegebenenfalls geaendert. */
class Letter(
    val senderLines: List<String>,
    val recipientLines: List<String>,
    val placeDate: String,
    val subject: String,
    val reference: String,
    val body: String,
    val signatures: List<String>,
    val enclosures: List<String>,
    val form: Boolean,
)

/**
 * Erzeugt aus einem Schreiben eine A4-PDF (Briefaufbau nach DIN 5008 angelehnt): Absender, Anschrift,
 * Ort und Datum, Betreff, Text, Unterschriftsfeld, Anlagen. Alles direkt auf dem Handy.
 */
object LetterPdf {
    private const val W = 595
    private const val H = 842
    private const val LEFT = 70f
    private const val RIGHT = 539f
    private const val TOP = 56f
    private const val BOTTOM = 770f
    private val TEXT_WIDTH = (RIGHT - LEFT).toInt()

    private fun paint(size: Float, bold: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

    fun create(context: Context, letter: Letter): File {
        val file = DocumentStore.newTempFile(context, "pdf")
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        var canvas = Canvas()
        var pageNo = 0
        var y = TOP
        val footer = paint(8f)

        fun finishCurrent(multi: Boolean) {
            val p = page ?: return
            if (multi) canvas.drawText("Seite $pageNo", W / 2f - 16f, H - 30f, footer)
            doc.finishPage(p)
            page = null
        }

        fun newPage() {
            finishCurrent(multi = true)
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            page = p
            canvas = p.canvas
            y = TOP
        }

        fun block(text: String, paint: TextPaint, x: Float = LEFT, width: Int = TEXT_WIDTH, after: Float = 0f) {
            if (text.isEmpty()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2.5f, 1f)
                .build()
            for (i in 0 until layout.lineCount) {
                val top = layout.getLineTop(i)
                val h = (layout.getLineBottom(i) - top).toFloat()
                if (y + h > BOTTOM) newPage()
                val line = text.substring(layout.getLineStart(i), layout.getLineEnd(i)).trimEnd('\n', '\r')
                canvas.drawText(line, x, y + (layout.getLineBaseline(i) - top), paint)
                y += h
            }
            y += after
        }

        fun signatureRow(labels: List<String>) {
            if (labels.isEmpty()) return
            val colWidth = if (labels.size > 1) 215f else 240f
            val gap = if (labels.size > 1) RIGHT - LEFT - 2 * colWidth else 0f
            val label = paint(8.5f)
            val needed = 90f
            if (y + needed > BOTTOM) newPage()
            y += 48f
            val line = Paint().apply { color = Color.BLACK; strokeWidth = 0.7f }
            labels.take(2).forEachIndexed { idx, text ->
                val x = LEFT + idx * (colWidth + gap)
                canvas.drawLine(x, y, x + colWidth, y, line)
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, label, colWidth.toInt()).build()
                canvas.save()
                canvas.translate(x, y + 3f)
                layout.draw(canvas)
                canvas.restore()
            }
            y += 40f
        }

        newPage()
        val normal = paint(11f)
        val bold = paint(11f, bold = true)

        if (!letter.form) {
            // Absender oben rechts
            val senderPaint = paint(9.5f)
            var sy = TOP
            letter.senderLines.filter { it.isNotBlank() }.forEach { line ->
                canvas.drawText(line, 380f, sy + 9f, senderPaint)
                sy += 12.5f
            }
            // Kleine Absenderzeile ueber der Anschrift
            val small = paint(7.5f)
            val smallLine = letter.senderLines.filter { it.isNotBlank() }.take(3).joinToString(" · ")
            if (smallLine.isNotBlank()) {
                canvas.drawText(smallLine.take(80), LEFT, 128f, small)
                canvas.drawLine(LEFT, 131f, LEFT + 240f, 131f, Paint().apply { color = Color.GRAY; strokeWidth = 0.5f })
            }
            var ry = 148f
            letter.recipientLines.filter { it.isNotBlank() }.take(6).forEach { line ->
                canvas.drawText(line, LEFT, ry, normal)
                ry += 14f
            }
            val dateLayoutWidth = normal.measureText(letter.placeDate)
            canvas.drawText(letter.placeDate, RIGHT - dateLayoutWidth, 250f, normal)
            y = 290f
            block(letter.subject, bold, after = 4f)
            if (letter.reference.isNotBlank()) block(letter.reference, paint(10f), after = 6f)
            y += 12f
        } else {
            val title = paint(18f, bold = true)
            block(letter.subject, title, after = 20f)
        }

        block(letter.body, normal, after = 10f)

        if (!letter.form) {
            if (y + 40f > BOTTOM) newPage()
            block("Mit freundlichen Grüßen", normal)
        }
        // Mehrere Unterschriften nebeneinander, mehr als zwei untereinander
        letter.signatures.chunked(2).forEach { signatureRow(it) }

        if (letter.enclosures.isNotEmpty()) {
            if (y + 30f > BOTTOM) newPage()
            y += 6f
            block("Anlagen: " + letter.enclosures.joinToString(", "), paint(10f))
        }

        finishCurrent(multi = pageNo > 1)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Erste Seite als Bild zur Vorschau (nur auf dem Handy). */
    fun preview(file: File, widthPx: Int = 900): Bitmap? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) null else r.openPage(0).use { p ->
                    val height = (widthPx.toFloat() * p.height / p.width).toInt()
                    val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}
