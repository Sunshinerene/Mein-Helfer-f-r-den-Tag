package de.meinhelfer.tag.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Eine Zeile der Nachbestell-Liste (fertig formatierte Texte). */
data class ReorderRow(
    val name: String,
    val intake: String,
    val stock: String,
    val doctor: String,
    val pharmacy: String,
)

object ReorderExport {
    private const val DIR = "exports"
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.GERMANY)

    fun exportDir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Gespeicherte Listen, neueste zuerst. */
    fun savedFiles(context: Context): List<File> =
        exportDir(context).listFiles { f -> f.isFile && f.name.endsWith(".pdf") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    private fun doctorName(doctors: List<Doctor>, id: Long?): String =
        doctors.firstOrNull { it.id == id }?.name.orEmpty()

    fun rows(meds: List<Medication>, doctors: List<Doctor>, today: Long = LocalDate.now().toEpochDay()): List<ReorderRow> =
        meds.map { m ->
            val days = m.daysLeft(today)
            ReorderRow(
                name = m.name + (if (m.dosage.isNotBlank()) " (${m.dosage})" else "") +
                    (if (m.pzn.isNotBlank()) "\nPZN ${m.pzn}" else ""),
                intake = m.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                    .let { if (it.isBlank()) "" else "$it Uhr" },
                stock = (if (m.stockKnown) m.stockAmountText(today) else "unbekannt") + (days?.let { ", ca. $it Tage" } ?: ""),
                doctor = doctorName(doctors, m.doctorId),
                pharmacy = doctorName(doctors, m.pharmacyId),
            )
        }

    /** Text fuer die E-Mail (Rezept-Anfrage beim Arzt oder Bestellung bei der Apotheke). */
    fun mailSubject(personName: String?, toPharmacy: Boolean): String =
        (if (toPharmacy) "Bestellung Medikamente" else "Bitte um Folgerezept") +
            (personName?.takeIf { it.isNotBlank() }?.let { " – $it" } ?: "")

    fun mailText(
        personName: String?,
        meds: List<Medication>,
        today: Long,
        toPharmacy: Boolean,
        caregiver: de.meinhelfer.tag.data.CaregiverProfile? = null,
    ): String {
        val who = personName?.takeIf { it.isNotBlank() }?.let { " für $it" } ?: ""
        val intro = caregiver?.takeIf { it.isSet }?.let { it.intro(personName) + "\n\n" }.orEmpty()
        val head = if (toPharmacy) {
            "Guten Tag,\n\n${intro}ich möchte folgende Medikamente$who bestellen bzw. abholen:\n\n"
        } else {
            "Guten Tag,\n\n${intro}ich bitte um ein Folgerezept für folgende Medikamente$who:\n\n"
        }
        val lines = meds.joinToString("\n") { m ->
            val days = m.daysLeft(today)
            "- ${m.name}" + (if (m.dosage.isNotBlank()) " (${m.dosage})" else "") +
                (if (m.pzn.isNotBlank()) ", PZN ${m.pzn}" else "") +
                (days?.let { " – Vorrat reicht noch ca. $it Tage" } ?: "")
        }
        val tail = if (caregiver?.isSet == true) "\n\nVielen Dank!\n\n" + caregiver.signature()
        else "\n\nVielen Dank und freundliche Grüße"
        return head + lines + tail
    }

    /** Erstellt die PDF im privaten App-Ordner und gibt die Datei zurueck. */
    fun createPdf(context: Context, personName: String?, rows: List<ReorderRow>): File {
        val file = File(exportDir(context), "Nachbestellung_${LocalDateTime.now().format(STAMP)}.pdf")
        val pageW = 595
        val pageH = 842
        val margin = 40
        val widths = intArrayOf(165, 85, 85, 90, 90) // Summe 515 = Seitenbreite minus Raender
        val headers = listOf("Medikament", "Einnahme", "Vorrat", "Arzt", "Apotheke")

        val title = Paint().apply { isAntiAlias = true; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val sub = TextPaint().apply { isAntiAlias = true; textSize = 11f; color = Color.DKGRAY }
        val head = TextPaint().apply { isAntiAlias = true; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val body = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = Color.BLACK }
        val footer = Paint().apply { isAntiAlias = true; textSize = 8f; color = Color.GRAY }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 8)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        fun drawRow(cells: List<String>, paint: TextPaint) {
            val layouts = cells.mapIndexed { i, t -> layout(t, paint, widths[i]) }
            val h = (layouts.maxOf { it.height } + 10).toFloat()
            val c = canvas ?: return
            var x = margin.toFloat()
            layouts.forEachIndexed { i, l ->
                c.save()
                c.translate(x + 4, y + 5)
                l.draw(c)
                c.restore()
                x += widths[i]
            }
            y += h
            c.drawLine(margin.toFloat(), y, (pageW - margin).toFloat(), y, line)
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            canvas = p.canvas
            val c = p.canvas
            y = margin.toFloat()
            c.drawText(
                "Erstellt mit Mein Helfer für den Tag · by Sunshine@R · Seite $pageNo",
                margin.toFloat(), (pageH - 24).toFloat(), footer,
            )
            if (pageNo == 1) {
                c.drawText("Nachbestellung Medikamente", margin.toFloat(), y + 20, title)
                y += 36
                val info = buildString {
                    append("Stand: ${LocalDate.now().format(DATE)}")
                    if (!personName.isNullOrBlank()) append("   ·   Person: $personName")
                }
                c.drawText(info, margin.toFloat(), y + 10, sub)
                y += 28
            }
            drawRow(headers, head)
        }

        newPage()
        rows.forEach { r ->
            val cells = listOf(r.name, r.intake, r.stock, r.doctor, r.pharmacy)
            val needed = cells.mapIndexed { i, t -> layout(t, body, widths[i]).height }.max() + 10
            if (y + needed > pageH - margin - 20) newPage()
            drawRow(cells, body)
        }
        if (rows.isEmpty()) {
            canvas?.drawText("Keine Medikamente ausgewählt.", margin.toFloat(), y + 20, sub)
        }
        page?.let { doc.finishPage(it) }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

/** Gibt eine fertige PDF-Datei an den Druckdienst des Handys weiter. */
class PdfPrintAdapter(private val file: File, private val documentName: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
