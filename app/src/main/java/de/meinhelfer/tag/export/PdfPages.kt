package de.meinhelfer.tag.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Kleiner Baukasten fuer mehrseitige PDFs (Seitenwechsel, Text mit Umbruch, Linien, Fusszeile). */
internal class PdfPages(
    private val w: Int,
    private val h: Int,
    val margin: Float = 40f,
    private val footer: String = "",
    private val onNewPage: (PdfPages) -> Unit = {},
) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    var canvas: Canvas = Canvas()
        private set
    var y = margin
    var pageNo = 0
        private set
    val left get() = margin
    val right get() = w - margin
    val width get() = (w - 2 * margin).toInt()
    val bottom get() = h - margin - 18f
    private val created = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY))

    fun paint(size: Float, bold: Boolean = false, gray: Boolean = false, color: Int? = null): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color ?: if (gray) Color.rgb(90, 90, 90) else Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

    fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()

    private fun finishPage() {
        val p = page ?: return
        val f = paint(7.5f, gray = true)
        p.canvas.drawText(
            listOf(footer, "erstellt am $created", "Seite $pageNo").filter { it.isNotBlank() }.joinToString(" · "),
            margin, h - margin + 6f, f,
        )
        doc.finishPage(p)
        page = null
    }

    fun newPage() {
        finishPage()
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        canvas = page!!.canvas
        y = margin
        onNewPage(this)
    }

    /** Neue Seite, wenn [height] nicht mehr passt. */
    fun ensure(height: Float) {
        if (page == null || y + height > bottom) newPage()
    }

    fun draw(l: StaticLayout, x: Float = left, atY: Float = y, advance: Boolean = true) {
        canvas.save()
        canvas.translate(x, atY)
        l.draw(canvas)
        canvas.restore()
        if (advance) y = atY + l.height
    }

    /** Text mit Umbruch an der aktuellen Position. */
    fun text(t: String, size: Float = 10.5f, bold: Boolean = false, gray: Boolean = false, color: Int? = null, x: Float = left, w: Int = width) {
        val l = layout(t, paint(size, bold, gray, color), w)
        ensure(l.height.toFloat())
        draw(l, x)
    }

    fun gap(d: Float) {
        y += d
    }

    fun rule(color: Int = Color.rgb(170, 170, 170), x1: Float = left, x2: Float = right) {
        canvas.drawLine(x1, y, x2, y, Paint().apply { this.color = color; strokeWidth = 0.7f })
    }

    fun box(r: RectF, stroke: Int = Color.BLACK, dashed: Boolean = false, width: Float = 1f, radius: Float = 6f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            color = stroke
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        canvas.drawRoundRect(r, radius, radius, p)
    }

    fun fill(r: RectF, color: Int, radius: Float = 6f) {
        canvas.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    fun save(file: File): File {
        finishPage()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    companion object {
        /** Ordner fuer erzeugte PDFs; Dateien aelter als 14 Tage werden geloescht. */
        fun dir(context: Context, name: String): File {
            val d = File(ReorderExport.exportDir(context), name).apply { mkdirs() }
            val limit = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
            d.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
            return d
        }
    }
}
