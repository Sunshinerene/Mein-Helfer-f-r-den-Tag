package de.meinhelfer.tag.export

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.summary.MeasurePlans
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.VitalStats
import de.meinhelfer.tag.summary.Vitals
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bericht über Messwerte und Trinkmenge für einen frei gewählten Zeitraum (A4 hoch):
 * je Messwert eine Zusammenfassung (Anzahl, Spanne, Schnitt, je Angabe wie „nüchtern“), auf Wunsch ein Verlauf
 * als Grafik und alle Messungen mit Uhrzeit, Angabe und Notiz. Nur Zahlen, keine Bewertung.
 */
object VitalReportPdf {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    private val DAY = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMANY)
    private val SHORT = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
    private val BLUE = Color.rgb(7, 48, 74)
    private val LIGHT = Color.rgb(120, 160, 190)
    private val GRID = Color.rgb(205, 205, 205)
    private val RED = Color.rgb(180, 30, 30)

    fun title(fromDay: Long, toDay: Long): String =
        "Messwerte " + LocalDate.ofEpochDay(fromDay).format(SHORT) + "–" + LocalDate.ofEpochDay(toDay).format(DATE)

    fun build(
        context: Context,
        person: Person?,
        entries: List<VitalEntry>,
        kinds: List<VitalKind>,
        fromDay: Long,
        toDay: Long,
        drinkGoal: Int,
        ranges: Map<String, Pair<Double?, Double?>>,
        charts: Boolean,
        plan: Map<VitalKind, List<LocalTime>> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        val name = (person?.name ?: "").replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]"), "_")
        val file = File(PdfPages.dir(context, "messwerte"), "Messwerte_${name}_${LocalDate.ofEpochDay(fromDay)}_${LocalDate.ofEpochDay(toDay)}.pdf")
        val inRange = entries.filter { it.day in fromDay..toDay }
        val pdf = PdfPages(595, 842, margin = 40f, footer = "Messwerte · Mein Helfer für den Tag")
        pdf.newPage()
        pdf.text(if (VitalKind.Trinken in kinds) "Messwerte und Trinkprotokoll" else "Messwerte", 18f, bold = true)
        pdf.text(
            listOfNotNull(
                person?.name,
                person?.birthDate?.let { "geb. " + LocalDate.ofEpochDay(it).format(DATE) },
                LocalDate.ofEpochDay(fromDay).format(DATE) + " bis " + LocalDate.ofEpochDay(toDay).format(DATE) + " (${toDay - fromDay + 1} Tage)",
            ).joinToString(" · "),
            10.5f,
        )
        val planned = plan.filterKeys { it in kinds }
        if (planned.isNotEmpty()) {
            val counts = MeasurePlans.adherence(person?.id ?: 1L, planned, inRange, fromDay, toDay, LocalDateTime.now(zone), zone)
            pdf.text(
                "Messzeiten: " + planned.entries.joinToString("; ") { (k, t) ->
                    k.label + " " + MeasurePlans.timesText(t) + " Uhr" + (counts[k]?.let { (p, d) -> " (gemessen $d von $p)" } ?: "")
                },
                9.5f,
                gray = true,
            )
        }
        pdf.gap(6f)
        pdf.rule()
        pdf.gap(6f)

        var any = false
        kinds.forEach { k ->
            val list = inRange.filter { it.kind == k.key }.sortedBy { it.atMillis }
            if (list.isEmpty()) return@forEach
            any = true
            if (k == VitalKind.Trinken) drinks(pdf, list, fromDay, toDay, drinkGoal, charts) else measure(pdf, k, list, ranges[k.key], fromDay, toDay, charts, zone)
        }
        if (!any) pdf.text("Keine Einträge im gewählten Zeitraum.", 11f)
        pdf.gap(10f)
        pdf.text(
            "Selbst erfasst mit „Mein Helfer für den Tag“. ! = außerhalb des selbst eingetragenen Bereichs, ↓ = unter dem Trinkziel. " +
                "Die App bewertet keine Werte; die Einschätzung bleibt Ärztin, Arzt oder Pflegefachkraft vorbehalten.",
            8.5f,
            gray = true,
        )
        return pdf.save(file)
    }

    private fun section(pdf: PdfPages, title: String) {
        pdf.ensure(80f)
        pdf.gap(8f)
        pdf.text(title, 13f, bold = true, color = BLUE)
        pdf.gap(2f)
    }

    private fun drinks(pdf: PdfPages, list: List<VitalEntry>, fromDay: Long, toDay: Long, goal: Int, charts: Boolean) {
        section(pdf, "Trinkmenge")
        val perDay = list.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.value } }
        val avg = perDay.values.average()
        val low = if (goal > 0) perDay.count { it.value < goal } else 0
        pdf.text(
            "Eingetragen an ${perDay.size} Tag(en), im Schnitt ${Vitals.number(avg, 0)} ml pro Tag" +
                (if (goal > 0) ", Trinkziel $goal ml: an $low Tag(en) nicht erreicht" else ""),
            10f,
        )
        if (charts) {
            val days = (fromDay..toDay).toList()
            chart(
                pdf,
                series = listOf(days.map { d -> d.toDouble() to (perDay[d] ?: 0.0) }),
                xFrom = fromDay.toDouble(),
                xTo = toDay.toDouble() + 1,
                lines = if (goal > 0) listOf(goal.toDouble()) else emptyList(),
                bars = true,
                decimals = 0,
                xLabel = { LocalDate.ofEpochDay(it.toLong()).format(SHORT) },
            )
        }
        // Tageswerte in drei Spalten
        val cells = perDay.entries.sortedBy { it.key }.map { (d, ml) ->
            LocalDate.ofEpochDay(d).format(DAY) + ": " + Vitals.number(ml, 0) + " ml" + if (goal > 0 && ml < goal) " ↓" else ""
        }
        val colW = pdf.width / 3f
        cells.chunked(3).forEach { row ->
            val layouts = row.map { pdf.layout(it, pdf.paint(9f), (colW - 6).toInt()) }
            val h = layouts.maxOf { it.height } + 3f
            pdf.ensure(h)
            val top = pdf.y
            layouts.forEachIndexed { i, l -> pdf.draw(l, pdf.left + i * colW, top, advance = false) }
            pdf.y = top + h
        }
    }

    private fun measure(
        pdf: PdfPages,
        k: VitalKind,
        list: List<VitalEntry>,
        range: Pair<Double?, Double?>?,
        fromDay: Long,
        toDay: Long,
        charts: Boolean,
        zone: ZoneId,
    ) {
        val stat = VitalStats.of(k, list, range) ?: return
        section(pdf, k.label + if (stat.unit.isNotBlank()) " (${stat.unit})" else "")
        val dec = VitalStats.decimals(k, stat.unit)
        pdf.text(VitalStats.line(stat), 10f)
        VitalStats.contextLine(stat)?.let { pdf.text(it, 10f) }
        range?.let { r ->
            pdf.text(
                "Eigener Bereich (z. B. wie mit dem Arzt besprochen): " + (r.first?.let { Vitals.number(it, dec) } ?: "…") + " bis " +
                    (r.second?.let { Vitals.number(it, dec) } ?: "…") + (if (stat.outside > 0) " · ${stat.outside}-mal außerhalb" else " · alle Werte darin"),
                9.5f,
                gray = stat.outside == 0,
                color = if (stat.outside > 0) RED else null,
            )
        }
        val same = list.filter { it.unit.ifBlank { k.unit } == stat.unit }
        if (charts && same.size >= 2) {
            fun x(e: VitalEntry): Double = e.atMillis / 86_400_000.0
            val start = LocalDate.ofEpochDay(fromDay).atStartOfDay(zone).toInstant().toEpochMilli() / 86_400_000.0
            val end = LocalDate.ofEpochDay(toDay + 1).atStartOfDay(zone).toInstant().toEpochMilli() / 86_400_000.0
            val series = mutableListOf(same.map { x(it) to it.value })
            if (k.second != null) series += same.mapNotNull { e -> e.value2?.let { x(e) to it } }
            chart(
                pdf,
                series = series,
                xFrom = start,
                xTo = end,
                lines = listOfNotNull(range?.first, range?.second),
                bars = false,
                decimals = dec,
                xLabel = { v -> Instant.ofEpochMilli((v * 86_400_000).toLong()).atZone(zone).toLocalDate().format(SHORT) },
            )
        }
        // Tabelle: Datum, Uhrzeit, Wert, Angabe, Notiz
        val cols = floatArrayOf(78f, 42f, 92f, 120f, 0f)
        cols[4] = pdf.width - cols.take(4).sum()
        val head = listOf("Datum", "Uhrzeit", "Wert", "Angabe", "Notiz")
        fun row(cells: List<String>, bold: Boolean, color: Int? = null) {
            val layouts = cells.mapIndexed { i, c -> pdf.layout(c, pdf.paint(9f, bold = bold, color = if (i == 2) color else null), (cols[i] - 6).toInt()) }
            val h = layouts.maxOf { it.height } + 5f
            pdf.ensure(h)
            val top = pdf.y
            var x = pdf.left
            layouts.forEachIndexed { i, l ->
                pdf.draw(l, x + 2f, top + 2f, advance = false)
                x += cols[i]
            }
            pdf.y = top + h
            pdf.canvas.drawLine(pdf.left, pdf.y, pdf.right, pdf.y, Paint().apply { this.color = GRID; strokeWidth = 0.5f })
        }
        pdf.gap(4f)
        row(head, bold = true)
        list.forEach { e ->
            val t = Instant.ofEpochMilli(e.atMillis).atZone(zone)
            val out = Vitals.outside(e, range)
            row(
                listOf(t.format(DAY), t.format(TIME), Vitals.valueText(e) + if (out) " !" else "", e.context, e.note),
                bold = false,
                color = if (out) RED else null,
            )
        }
    }

    /**
     * Einfacher Verlauf: Linien (oder Balken je Tag) mit Achse, drei Hilfslinien und gestrichelten Bereichs- bzw. Ziellinien.
     * [series] = Punkte (x, y); x in Tagen.
     */
    private fun chart(
        pdf: PdfPages,
        series: List<List<Pair<Double, Double>>>,
        xFrom: Double,
        xTo: Double,
        lines: List<Double>,
        bars: Boolean,
        decimals: Int,
        xLabel: (Double) -> String,
    ) {
        val all = series.flatten().map { it.second } + lines
        if (all.isEmpty()) return
        var lo = all.min()
        var hi = all.max()
        if (bars) lo = 0.0
        if (hi - lo < 1e-6) {
            hi += 1.0
            lo -= if (bars) 0.0 else 1.0
        }
        val pad = (hi - lo) * 0.08
        if (!bars) lo -= pad
        hi += pad
        val height = 110f
        pdf.ensure(height + 24f)
        pdf.gap(4f)
        val labelW = 34f
        val area = RectF(pdf.left + labelW, pdf.y, pdf.right, pdf.y + height)
        val c = pdf.canvas
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; strokeWidth = 0.6f }
        val text = pdf.paint(7.5f, gray = true)
        fun y(v: Double) = (area.bottom - (v - lo) / (hi - lo) * area.height()).toFloat()
        fun x(v: Double) = (area.left + (v - xFrom) / (xTo - xFrom) * area.width()).toFloat()
        for (i in 0..2) {
            val v = lo + (hi - lo) * i / 2
            val yy = y(v)
            c.drawLine(area.left, yy, area.right, yy, gridPaint)
            c.drawText(Vitals.number(v, decimals), pdf.left, yy + 3f, text)
        }
        c.drawRect(area, Paint().apply { style = Paint.Style.STROKE; color = GRID; strokeWidth = 0.6f })
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = RED
            strokeWidth = 0.9f
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        lines.forEach { v -> c.drawLine(area.left, y(v), area.right, y(v), dash) }
        series.forEachIndexed { i, pts ->
            val color = if (i == 0) BLUE else LIGHT
            if (bars) {
                val w = (area.width() / (xTo - xFrom)).toFloat() * 0.7f
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                pts.filter { it.second > 0 }.forEach { (xv, yv) ->
                    val cx = x(xv + 0.5)
                    c.drawRect(cx - w / 2, y(yv), cx + w / 2, area.bottom, p)
                }
            } else {
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = 1.3f }
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                val path = Path()
                pts.sortedBy { it.first }.forEachIndexed { j, (xv, yv) ->
                    if (j == 0) path.moveTo(x(xv), y(yv)) else path.lineTo(x(xv), y(yv))
                    c.drawCircle(x(xv), y(yv), 1.8f, dot)
                }
                c.drawPath(path, line)
            }
        }
        // Datum links, Mitte, rechts
        val first = xLabel(xFrom)
        val last = xLabel(xTo - if (bars) 1.0 else 0.001)
        val mid = xLabel((xFrom + xTo) / 2)
        c.drawText(first, area.left, area.bottom + 10f, text)
        c.drawText(mid, area.centerX() - text.measureText(mid) / 2, area.bottom + 10f, text)
        c.drawText(last, area.right - text.measureText(last), area.bottom + 10f, text)
        pdf.y = area.bottom + 16f
    }
}
