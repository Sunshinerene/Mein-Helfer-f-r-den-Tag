package de.meinhelfer.tag.summary

import java.time.LocalDateTime
import java.time.ZoneOffset

/** Eine Datei aus einem Sammel-Import (mehrere Dateien oder ein Ordner), schon gelesen, noch nicht gespeichert. */
data class BatchFile(
    /** Dateiname mit Endung. */
    val name: String,
    /** Foto oder PDF: lässt sich mit anderen Fotos oder PDFs zu einem Dokument zusammenfügen. */
    val mergeable: Boolean,
    /** Foto (z. B. von der Kamera), dann zählt auch, wie kurz nacheinander es aufgenommen wurde. */
    val isPhoto: Boolean = false,
    /** Aufnahme- oder Änderungszeit in Millisekunden, falls bekannt. */
    val time: Long? = null,
    /** Erkannter Text. */
    val text: String = "",
)

/**
 * Vorschlag für einen Sammel-Import: Reihenfolge der Dateien und je Position, ob sie zur Datei davor gehört.
 * [order] enthält Indizes in die Eingabeliste, [join] und [reasons] gelten je Position in [order].
 */
data class BatchPlan(
    val order: List<Int>,
    val join: List<Boolean>,
    val reasons: List<String?>,
) {
    /** Dokumente als Listen von Indizes in die Eingabeliste. */
    val groups: List<List<Int>> get() = DocGrouping.groups(order, join)
    val hasSuggestion: Boolean get() = join.any { it }
}

/**
 * Prüft vor dem Übernehmen mehrerer Dateien, welche wohl zusammen ein Dokument sind (z. B. Seite 1 und Seite 2).
 * Hinweise: Seitenangaben im Dateinamen („Befund Seite 2“, „Scan_p2“, „1von2“), im Text („Seite 2 von 3“, „- 2 -“)
 * und bei Fotos, ob sie kurz nacheinander aufgenommen wurden. Gegenhinweise: „Seite 1“ oder eine neue Anrede am Anfang,
 * „Seite 3 von 3“ oder ein Gruß am Ende der vorigen Datei. Es ist nur ein Vorschlag, entschieden wird im Dialog.
 */
object DocGrouping {
    /** Höchstens so viele Dateien auf einmal. */
    const val MAX_FILES = 30
    /** Fotos, die höchstens so weit auseinander liegen, gelten als „kurz nacheinander“. */
    const val PHOTO_GAP_MS = 3 * 60_000L
    private const val THRESHOLD = 2

    /** Seitenangabe im Dateinamen. [strength] 2 = eindeutig (Seite, page, p2, 1von2), 1 = Nummer am Ende, 0 = Kopie „(2)“. */
    data class NameMark(val stem: String, val page: Int, val total: Int?, val strength: Int)

    /** Seitenangaben im Text: erste und letzte gefundene Seite, Gesamtzahl falls angegeben. */
    data class TextPages(val first: Int, val last: Int, val total: Int?)

    private const val SEP = "[\\s_\\-.]"
    private val withTotal = Regex(
        "^(.*?)$SEP*\\(?(?:(?:seite|page|pg|blatt|teil|part|s|p)$SEP*)?(\\d{1,2})$SEP*(?:von|of|aus)$SEP*(\\d{1,2})\\)?$",
    )
    private val pageWord = Regex("^(.*?)$SEP*\\(?(?:seite|page|pg|blatt|teil|part)$SEP*(\\d{1,3})\\)?$")
    private val pageLetter = Regex("^(.*?)$SEP+\\(?(?:s|p)$SEP*(\\d{1,3})\\)?$")
    private val copyMark = Regex("^(.*?)\\s*\\((\\d{1,2})\\)$")
    private val trailing = Regex("^(.*?\\D)$SEP*(\\d{1,2})$")

    /** Dateiname ohne Endung. */
    fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.length - dot in 2..6) name.substring(0, dot) else name
    }

    private fun stemOf(raw: String): String = raw.lowercase().filter { it.isLetterOrDigit() }

    fun nameMark(fileName: String): NameMark? {
        val base = baseName(fileName).trim().lowercase()
        withTotal.find(base)?.let { m ->
            val page = m.groupValues[2].toInt()
            val total = m.groupValues[3].toInt()
            if (page in 1..total) return NameMark(stemOf(m.groupValues[1]), page, total, 2)
        }
        pageWord.find(base)?.let { m -> return NameMark(stemOf(m.groupValues[1]), m.groupValues[2].toInt(), null, 2) }
        pageLetter.find(base)?.let { m ->
            val stem = stemOf(m.groupValues[1])
            if (stem.isNotEmpty()) return NameMark(stem, m.groupValues[2].toInt(), null, 2)
        }
        copyMark.find(base)?.let { m -> return NameMark(stemOf(m.groupValues[1]), m.groupValues[2].toInt(), null, 0) }
        trailing.find(base)?.let { m ->
            val stem = stemOf(m.groupValues[1])
            if (stem.isNotEmpty()) return NameMark(stem, m.groupValues[2].toInt(), null, 1)
        }
        return null
    }

    /** Dateiname ohne Seitenangabe als Titel für ein zusammengefügtes Dokument, z. B. „Befund Seite 1“ → „Befund“. */
    fun baseTitle(fileName: String): String {
        val base = baseName(fileName).trim()
        val lower = base.lowercase()
        val prefix = listOf(withTotal, pageWord, pageLetter, copyMark, trailing).firstNotNullOfOrNull { it.find(lower) }
            ?.groupValues?.get(1)
        val cut = prefix?.let { base.substring(0, it.length) }?.trimEnd(' ', '_', '-', '.', '(')
        return cut?.takeIf { it.isNotBlank() } ?: base
    }

    private val textWithTotal = Regex("(?i)(?:seite|page|blatt)\\s*(\\d{1,2})\\s*(?:von|of|aus|/)\\s*(\\d{1,2})")
    private val textDash = Regex("(?m)^\\s*[-–]\\s*(\\d{1,2})\\s*[-–]\\s*$")
    private val textAlone = Regex("(?im)^\\s*(?:seite|page)\\s*(\\d{1,2})\\s*$")

    fun textPages(text: String): TextPages? {
        val withTotals = textWithTotal.findAll(text)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .filter { (p, t) -> p in 1..t }
            .toList()
        if (withTotals.isNotEmpty()) {
            val total = withTotals.groupingBy { it.second }.eachCount().maxByOrNull { it.value }!!.key
            val pages = withTotals.filter { it.second == total }.map { it.first }
            return TextPages(pages.min(), pages.max(), total)
        }
        val pages = (textDash.findAll(text) + textAlone.findAll(text)).map { it.groupValues[1].toInt() }.filter { it in 1..60 }.toList()
        if (pages.isEmpty()) return null
        return TextPages(pages.min(), pages.max(), null)
    }

    private val timeInName = Regex("(20\\d{2})(\\d{2})(\\d{2})[_\\-]?(\\d{2})(\\d{2})(\\d{2})")

    /** Aufnahmezeit aus Kamera-Dateinamen wie „PXL_20261122_101530123.jpg“ oder „IMG_20261122_101530.jpg“. */
    fun timeFromName(name: String): Long? {
        val m = timeInName.find(name) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        return runCatching {
            LocalDateTime.of(v[0], v[1], v[2], v[3], v[4], v[5]).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }

    private val chunk = Regex("\\d+|\\D+")

    /** Vergleicht Dateinamen „natürlich“: Seite 2 vor Seite 10. */
    val naturalOrder: Comparator<String> = Comparator { a, b ->
        val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else {
                x.compareTo(y)
            }
            if (c != 0) return@Comparator c
        }
        ca.size - cb.size
    }

    private fun photoTime(f: BatchFile): Long? = if (f.isPhoto) f.time ?: timeFromName(f.name) else null

    /** Reihenfolge: Fotos mit bekannter Zeit nach Aufnahmezeit, sonst nach Dateinamen. */
    fun order(files: List<BatchFile>): List<Int> {
        val byName = files.indices.sortedWith { a, b -> naturalOrder.compare(files[a].name, files[b].name) }
        val allTimedPhotos = files.isNotEmpty() && files.all { photoTime(it) != null }
        return if (allTimedPhotos) byName.sortedBy { photoTime(files[it]) } else byName
    }

    private val greeting = Regex("(?i)sehr geehrte|liebe frau|lieber herr|guten tag")
    private val closing = Regex("(?i)mit freundlichen gr|mit freundlichem gr|hochachtungsvoll|viele grüße|beste grüße")

    /** Wie sehr spricht etwas dafür, dass [b] direkt auf [a] folgt? Mit Begründung für den Dialog. */
    fun score(a: BatchFile, b: BatchFile): Pair<Int, String?> {
        if (!a.mergeable || !b.mergeable) return 0 to null
        var score = 0
        val why = mutableListOf<String>()
        val ma = nameMark(a.name)
        val mb = nameMark(b.name)
        // Kopien wie „Befund (2)“ sind meist eigene Dokumente und zählen hier nicht
        if (ma != null && mb != null && ma.stem == mb.stem && mb.page == ma.page + 1 && ma.strength > 0 && mb.strength > 0) {
            if (maxOf(ma.strength, mb.strength) == 2) {
                score += 3
                why += "Dateiname: Seite ${ma.page} und ${mb.page}"
            } else {
                score += 2
                why += "Dateinamen fortlaufend nummeriert"
            }
        }
        if (ma?.total != null && ma.page >= ma.total) score -= 4
        if (mb?.total != null && mb.page == 1) score -= 4
        val ta = textPages(a.text)
        val tb = textPages(b.text)
        if (tb != null && tb.first == 1) score -= 4
        if (ta?.total != null && ta.last >= ta.total) score -= 4
        if (ta != null && tb != null && tb.first == ta.last + 1 && (ta.total == null || tb.total == null || ta.total == tb.total)) {
            if (ta.total != null && tb.total != null) {
                score += 3
                why += "Text: Seite ${ta.last} von ${ta.total}, dann Seite ${tb.first} von ${tb.total}"
            } else {
                score += 2
                why += "Text: Seitenzahlen ${ta.last} und ${tb.first}"
            }
        } else if (ta == null && tb != null && tb.first == 2) {
            score += 1
            why += "Text: Seite 2"
        }
        val pa = photoTime(a)
        val pb = photoTime(b)
        if (pa != null && pb != null && kotlin.math.abs(pb - pa) <= PHOTO_GAP_MS) {
            score += 2
            why += "kurz nacheinander fotografiert"
        }
        val startsNew = greeting.containsMatchIn(b.text.take(800))
        val endedBefore = closing.containsMatchIn(a.text.takeLast(800))
        if (startsNew && endedBefore) score -= 3 else if (startsNew || endedBefore) score -= 1
        return score to why.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** Vorschlag für alle Dateien: Reihenfolge und welche zur vorigen gehören. */
    fun suggest(files: List<BatchFile>): BatchPlan {
        val order = order(files)
        val join = MutableList(order.size) { false }
        val reasons = MutableList<String?>(order.size) { null }
        for (i in 1 until order.size) {
            val (s, why) = score(files[order[i - 1]], files[order[i]])
            if (s >= THRESHOLD) {
                join[i] = true
                reasons[i] = why
            }
        }
        return BatchPlan(order, join, reasons)
    }

    /** Positionen zu Dokumenten zusammenfassen: eine neue Gruppe, wo [join] falsch ist. */
    fun groups(order: List<Int>, join: List<Boolean>): List<List<Int>> {
        val out = mutableListOf<MutableList<Int>>()
        order.forEachIndexed { pos, idx ->
            if (pos == 0 || !join.getOrElse(pos) { false } || out.isEmpty()) out.add(mutableListOf(idx)) else out.last().add(idx)
        }
        return out
    }
}
