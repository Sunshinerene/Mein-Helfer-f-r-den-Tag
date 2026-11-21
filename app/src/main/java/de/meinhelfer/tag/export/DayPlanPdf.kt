package de.meinhelfer.tag.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import de.meinhelfer.tag.summary.PlanSection
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tagesplan als A4-PDF mit Kaestchen zum Abhaken auf Papier. Entsteht nur auf dem Handy. */
object DayPlanPdf {
    private const val W = 595
    private const val H = 842
    private const val LEFT = 56f
    private const val RIGHT = 539f
    private const val TOP = 56f
    private const val BOTTOM = 790f
    private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

    private fun paint(size: Float, bold: Boolean = false, gray: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (gray) Color.rgb(90, 90, 90) else Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    private fun dir(context: Context): File = File(ReorderExport.exportDir(context), "tagesplan").apply { mkdirs() }

    fun create(context: Context, title: String, subtitle: String, sections: List<PlanSection>, date: LocalDate): File {
        // Alte Tagesplaene aufraeumen (aelter als 14 Tage)
        val limit = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        dir(context).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }

        val file = File(dir(context), "Tagesplan_$date.pdf")
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas = Canvas()
        var y = TOP
        val footer = paint(8f, gray = true)
        val created = LocalDateTime.now().format(STAMP)

        fun finish() {
            val p = page ?: return
            p.canvas.drawText("Mein Helfer für den Tag · erstellt am $created · Seite $pageNo", LEFT, H - 30f, footer)
            doc.finishPage(p)
            page = null
        }
        fun newPage() {
            finish()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page!!.canvas
            y = TOP
        }
        fun ensure(height: Float) {
            if (page == null || y + height > BOTTOM) newPage()
        }
        fun draw(l: StaticLayout, x: Float) {
            canvas.save()
            canvas.translate(x, y)
            l.draw(canvas)
            canvas.restore()
            y += l.height
        }

        val width = (RIGHT - LEFT).toInt()
        newPage()
        draw(layout(title, paint(20f, bold = true), width), LEFT)
        y += 4f
        draw(layout(subtitle, paint(11f, gray = true), width), LEFT)
        y += 14f

        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.BLACK }
        val rule = Paint().apply { color = Color.rgb(180, 180, 180); strokeWidth = 0.7f }
        if (sections.isEmpty()) draw(layout("Für heute ist nichts eingetragen.", paint(11f), width), LEFT)
        sections.forEach { s ->
            val head = layout(s.title, paint(13f, bold = true), width)
            ensure(head.height + 30f)
            y += 6f
            draw(head, LEFT)
            canvas.drawLine(LEFT, y + 3f, RIGHT, y + 3f, rule)
            y += 9f
            s.lines.forEach { line ->
                val indent = if (line.check) 20f else 0f
                val main = layout(line.text, paint(11f), width - indent.toInt())
                val sub = line.sub?.let { layout(it, paint(9.5f, gray = true), width - indent.toInt()) }
                ensure(main.height + (sub?.height ?: 0) + 8f)
                if (line.check) canvas.drawRect(LEFT, y + 2f, LEFT + 10f, y + 12f, box)
                draw(main, LEFT + indent)
                if (sub != null) draw(sub, LEFT + indent)
                y += 6f
            }
        }
        y += 10f
        ensure(40f)
        draw(
            layout(
                "Nur ein Hilfsmittel. Ersetzt keine ärztliche Anweisung. Im Notfall 112, ärztlicher Bereitschaftsdienst 116 117.",
                paint(8.5f, gray = true),
                width,
            ),
            LEFT,
        )
        finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
