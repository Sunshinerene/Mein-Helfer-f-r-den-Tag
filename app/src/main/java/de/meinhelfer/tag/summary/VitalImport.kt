package de.meinhelfer.tag.summary

import java.time.LocalDate

/**
 * Messwerte aus Dokumenten übernehmen, z. B. ein von Hand geführtes Blutzucker-Heft oder eine Blutdruck-Liste.
 * Liest den erkannten Text Zeile für Zeile: Datum, Uhrzeit oder Tageszeit, Werte. Alles wird vor dem Speichern gezeigt.
 */
object VitalImport {
    data class Candidate(
        val day: Long,
        /** Minute des Tages */
        val minute: Int,
        val kind: VitalKind,
        val value: Double,
        val value2: Double? = null,
        val unit: String = kind.unit,
        val context: String = "",
        /** Ganze Zeile aus dem Dokument (zum Prüfen) */
        val line: String = "",
    )

    data class Result(val kind: VitalKind?, val candidates: List<Candidate>) {
        val firstDay get() = candidates.minOfOrNull { it.day }
        val lastDay get() = candidates.maxOfOrNull { it.day }
    }

    /** Arten, die sich aus Listen gut lesen lassen. */
    val KINDS = listOf(VitalKind.Blutzucker, VitalKind.Blutdruck, VitalKind.Puls, VitalKind.Temperatur, VitalKind.Gewicht, VitalKind.Sauerstoff, VitalKind.INR)

    private val KEYWORDS: Map<VitalKind, Regex> = mapOf(
        VitalKind.Blutzucker to Regex("blutzucker|\\bbz\\b|mg/?dl|mmol|glukose|glucose|zucker|insulin|diabet", RegexOption.IGNORE_CASE),
        VitalKind.Blutdruck to Regex("blutdruck|\\brr\\b|mmhg|systol|diastol|\\bsys\\b|\\bdia\\b", RegexOption.IGNORE_CASE),
        VitalKind.Temperatur to Regex("temperatur|fieber|°c|\\btemp\\b", RegexOption.IGNORE_CASE),
        VitalKind.Gewicht to Regex("gewicht|\\bkg\\b|waage", RegexOption.IGNORE_CASE),
        VitalKind.Sauerstoff to Regex("spo2|sättigung|saettigung|sauerstoff", RegexOption.IGNORE_CASE),
        VitalKind.INR to Regex("\\binr\\b|quick|marcumar|gerinnung", RegexOption.IGNORE_CASE),
    )

    /** Tageszeiten in Tabellenköpfen oder Zeilen (Wort → Minute des Tages, Angabe zur Messung). */
    private val SLOTS: List<Triple<Regex, Int, String>> = listOf(
        Triple(Regex("nüchtern|nuechtern|vor dem frühstück|vor frühstück", RegexOption.IGNORE_CASE), 7 * 60, "nüchtern"),
        Triple(Regex("morgens|früh\\b|frueh\\b|morgen\\b", RegexOption.IGNORE_CASE), 7 * 60, ""),
        Triple(Regex("mittags|mittag\\b", RegexOption.IGNORE_CASE), 12 * 60, ""),
        Triple(Regex("nachmittags|nachmittag\\b", RegexOption.IGNORE_CASE), 15 * 60, ""),
        Triple(Regex("abends|abend\\b", RegexOption.IGNORE_CASE), 18 * 60, ""),
        Triple(Regex("spät|spaet|vor dem schlafen|zur nacht|nachts|nacht\\b", RegexOption.IGNORE_CASE), 22 * 60, "vor dem Schlafen"),
    )

    private val DATE = Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})?(?![\\d])")
    private val TIME = Regex("(?<![\\d.])([01]?\\d|2[0-3])[:.]([0-5]\\d)(?:\\s*uhr)?(?![\\d.])", RegexOption.IGNORE_CASE)
    private val PRESSURE = Regex("(?<!\\d)(\\d{2,3})\\s*/\\s*(\\d{2,3})(?!\\d)")
    private val NUMBER = Regex("(?<![\\d.,/])(\\d{1,3}(?:[.,]\\d)?)(?![\\d/])(\\s*(?:ie|i\\.e\\.|e\\b|einheiten|kg|mg|mmol|%|°))?", RegexOption.IGNORE_CASE)

    /** Welche Messwert-Art das Dokument am ehesten enthält (null = keine erkennbar). */
    fun guessKind(text: String): VitalKind? {
        val scores = KEYWORDS.mapValues { (_, r) -> r.findAll(text).count() }
        val pressures = PRESSURE.findAll(text).count { m -> plausiblePressure(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        val best = scores.maxByOrNull { it.value }
        if (pressures >= 3 && (best == null || best.key == VitalKind.Blutdruck || best.value == 0)) return VitalKind.Blutdruck
        return best?.takeIf { it.value > 0 }?.key
    }

    private fun plausiblePressure(a: Double, b: Double) = a in 70.0..260.0 && b in 30.0..160.0 && a > b

    /**
     * Messwerte lesen. [kind] = vorgegebene Art (sonst geraten). [docDate] hilft beim Jahr, wenn im Datum keins steht.
     * Ergebnis leer, wenn nichts Brauchbares gefunden wurde.
     */
    fun parse(text: String, kind: VitalKind? = null, docDate: LocalDate? = null, today: LocalDate = LocalDate.now()): Result {
        val k = kind ?: guessKind(text) ?: return Result(null, emptyList())
        val mmol = k == VitalKind.Blutzucker && Regex("mmol", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !Regex("mg/?dl", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val range = when {
            mmol -> VitalKind.MMOL_RANGE
            k == VitalKind.Blutzucker -> 40.0..600.0
            k == VitalKind.Temperatur -> 34.0..42.5
            k == VitalKind.Sauerstoff -> 70.0..100.0
            k == VitalKind.Puls -> 30.0..200.0
            else -> k.plausible
        }
        val refYear = (docDate ?: today).year
        var current: LocalDate? = null
        var header: List<Pair<Int, String>> = emptyList()
        val out = mutableListOf<Candidate>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // Datum in der Zeile?
            var rest = line
            DATE.find(line)?.let { m ->
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                val date = runCatching { LocalDate.of(y ?: refYear, mo, d) }.getOrNull()
                if (date != null) {
                    current = if (y == null && date.isAfter(today)) date.minusYears(1) else date
                    rest = line.removeRange(m.range)
                }
            }
            // Tabellenkopf mit Tageszeiten (z. B. „Datum | morgens | mittags | abends“)
            val slotsInLine = SLOTS.flatMap { (r, minute, ctx) -> r.findAll(rest).map { it.range.first to (minute to ctx) } }
                .sortedBy { it.first }.distinctBy { it.second.first }.map { it.second }
            val hasNumbers = NUMBER.containsMatchIn(rest.replace(TIME, ""))
            if (slotsInLine.size >= 2 && !hasNumbers) {
                header = slotsInLine
                continue
            }
            val day = current ?: continue
            // Uhrzeit oder Tageszeit in der Zeile
            val time = TIME.find(rest)
            var minute: Int? = time?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
            if (time != null) rest = rest.removeRange(time.range)
            val lineSlot = slotsInLine.singleOrNull()
            var ctx = lineSlot?.second.orEmpty()
            if (minute == null && lineSlot != null) minute = lineSlot.first
            val values = mutableListOf<Pair<Double, Double?>>()
            if (k == VitalKind.Blutdruck) {
                PRESSURE.findAll(rest).forEach { m ->
                    val a = m.groupValues[1].toDouble()
                    val b = m.groupValues[2].toDouble()
                    if (plausiblePressure(a, b)) values += a to b
                }
            } else {
                val cleaned = rest.replace(PRESSURE, " ")
                NUMBER.findAll(cleaned).forEach { m ->
                    val suffix = m.groupValues[2].trim().lowercase()
                    // Insulin-Einheiten, Gewichte usw. nicht als Wert nehmen
                    if (suffix.startsWith("i") || suffix == "e" || suffix.startsWith("einh")) return@forEach
                    if (k != VitalKind.Gewicht && suffix == "kg") return@forEach
                    val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@forEach
                    if (v in range) values += v to null
                }
            }
            if (values.isEmpty()) continue
            values.forEachIndexed { i, (a, b) ->
                val slot = if (minute == null && values.size > 1) header.getOrNull(i) else null
                val m = minute ?: slot?.first ?: header.getOrNull(i)?.first ?: DEFAULT_SLOTS.getOrElse(i) { 12 * 60 }
                val c = if (minute == null) (slot?.second ?: header.getOrNull(i)?.second).orEmpty() else ctx
                out += Candidate(
                    day = day.toEpochDay(),
                    minute = m,
                    kind = k,
                    value = a,
                    value2 = b,
                    unit = if (mmol) "mmol/l" else k.unit,
                    context = c,
                    line = line,
                )
            }
            ctx = ""
        }
        return Result(k, out.distinctBy { Triple(it.day, it.minute, it.value) }.sortedWith(compareBy({ it.day }, { it.minute })))
    }

    private val DEFAULT_SLOTS = listOf(7 * 60, 12 * 60, 18 * 60, 22 * 60)

    /** Lohnt sich ein Vorschlag? Mindestens drei Werte an mindestens einem Tag. */
    fun worthOffering(r: Result): Boolean = r.kind != null && r.candidates.size >= 3

    /** Kurzer Satz für die Empfehlung, z. B. „18 Blutzucker-Werte vom 01.11. bis 14.11.“ */
    fun summary(r: Result): String {
        val k = r.kind ?: return ""
        val f = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val from = r.firstDay?.let { LocalDate.ofEpochDay(it).format(f) }
        val to = r.lastDay?.let { LocalDate.ofEpochDay(it).format(f) }
        val span = if (from == to) "vom $from" else "vom $from bis $to"
        return "${r.candidates.size} ${k.label}-Werte $span"
    }
}
