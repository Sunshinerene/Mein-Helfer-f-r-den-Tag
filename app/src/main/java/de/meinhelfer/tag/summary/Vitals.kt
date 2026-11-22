package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.CareLogEntry
import de.meinhelfer.tag.data.VitalEntry
import java.util.Locale
import kotlin.math.roundToInt

/** Art eines Messwerts. Die Grenzen dienen nur gegen Tippfehler, sie bewerten nichts. */
enum class VitalKind(
    val key: String,
    val label: String,
    val unit: String,
    val emoji: String,
    val decimals: Int,
    val plausible: ClosedFloatingPointRange<Double>,
    val second: ClosedFloatingPointRange<Double>? = null,
    /** Vorauswahl für die Angabe zur Messung, z. B. „nüchtern“ oder „Ohr“ (eine davon oder keine). */
    val contexts: List<String> = emptyList(),
) {
    Trinken("trinken", "Trinkmenge", "ml", "💧", 0, 10.0..3000.0),
    Blutdruck("blutdruck", "Blutdruck", "mmHg", "🩺", 0, 50.0..260.0, 30.0..160.0, listOf("im Sitzen", "im Liegen", "im Stehen", "nach Belastung")),
    Puls("puls", "Puls", "/min", "❤️", 0, 20.0..250.0, contexts = listOf("in Ruhe", "nach Belastung")),
    Blutzucker(
        "blutzucker", "Blutzucker", "mg/dl", "🩸", 0, 20.0..600.0,
        contexts = listOf("nüchtern", "vor dem Essen", "nach dem Essen", "vor dem Schlafen", "nachts"),
    ),
    Temperatur("temperatur", "Temperatur", "°C", "🌡️", 1, 33.0..43.0, contexts = listOf("Ohr", "Stirn", "Mund", "Achsel", "im Po (rektal)")),
    Sauerstoff("sauerstoff", "Sauerstoff (SpO₂)", "%", "🫁", 0, 50.0..100.0, contexts = listOf("in Ruhe", "nach Belastung", "mit Sauerstoffgerät")),
    Gewicht("gewicht", "Gewicht", "kg", "⚖️", 1, 20.0..300.0, contexts = listOf("morgens nüchtern", "mit Kleidung")),
    Atmung("atmung", "Atemfrequenz", "/min", "🌬️", 0, 4.0..60.0, contexts = listOf("in Ruhe", "im Schlaf", "nach Belastung")),
    Schmerz("schmerz", "Schmerz", "von 10", "🤕", 0, 0.0..10.0, contexts = listOf("in Ruhe", "bei Bewegung", "nach Schmerzmittel")),
    INR("inr", "INR (Gerinnung)", "", "🧪", 1, 0.5..10.0, contexts = listOf("Selbstmessung", "in der Praxis")),
    Urin("urin", "Urin (Ausfuhr)", "ml", "🚻", 0, 10.0..3000.0, contexts = listOf("Katheterbeutel", "Toilette/Steckbecken")),
    ;

    companion object {
        fun of(key: String): VitalKind? = entries.firstOrNull { it.key == key }
        val MEASURES = entries.filter { it != Trinken }

        /** Ohne eigene Auswahl: diese Werte als Schnellknöpfe. */
        val DEFAULT_QUICK = listOf(Blutdruck, Blutzucker, Temperatur)

        /** Blutzucker in mmol/l: andere Grenzen gegen Tippfehler */
        val MMOL_RANGE = 1.0..35.0
    }
}

object Vitals {
    /** Richtwert der DGE fuer Menschen ab 65 Jahren: etwa 1,3 Liter am Tag ueber Getraenke. */
    const val DEFAULT_DRINK_GOAL = 1300

    fun number(v: Double, decimals: Int): String =
        if (decimals == 0) v.roundToInt().toString() else String.format(Locale.GERMANY, "%.${decimals}f", v)

    /** "135/85 mmHg", "72,5 kg", "6,1 mmol/l" */
    fun valueText(e: VitalEntry): String {
        val k = VitalKind.of(e.kind) ?: return number(e.value, 1)
        val unit = e.unit.ifBlank { k.unit }
        val dec = if (k == VitalKind.Blutzucker && unit == "mmol/l") 1 else k.decimals
        val main = number(e.value, dec) + (e.value2?.let { "/" + number(it, dec) } ?: "")
        return if (unit.isBlank()) main else "$main $unit"
    }

    /** Text wie "1,2" oder "120" in eine Zahl; Komma und Punkt sind erlaubt. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    fun drinkToday(list: List<VitalEntry>, personId: Long, day: Long): Int =
        list.filter { it.personId == personId && it.day == day && it.kind == VitalKind.Trinken.key }.sumOf { it.value }.roundToInt()

    /** Liegt ein Wert ausserhalb des selbst eingetragenen Bereichs (z. B. vom Arzt genannt)? */
    fun outside(e: VitalEntry, range: Pair<Double?, Double?>?): Boolean {
        if (range == null) return false
        val (min, max) = range
        return (min != null && e.value < min) || (max != null && e.value > max)
    }
}

/** Art der Hilfe im Pflegetagebuch. */
enum class HelpKind(val key: String, val label: String, val short: String) {
    Erinnern("erinnern", "Erinnern oder anleiten", "erinnert/angeleitet"),
    Beaufsichtigen("aufsicht", "Beaufsichtigen, dabeibleiben", "beaufsichtigt"),
    Teilweise("teilweise", "Teilweise übernommen", "teilweise übernommen"),
    Komplett("komplett", "Vollständig übernommen", "vollständig übernommen"),
    Beruhigen("beruhigen", "Beruhigen, ablenken, eingreifen", "beruhigt/eingegriffen"),
    ;

    companion object {
        fun of(key: String): HelpKind? = entries.firstOrNull { it.key == key }
    }
}

/** Lebensbereich wie bei der Begutachtung (Module 1 bis 6) mit typischen Taetigkeiten. */
class CareArea(val module: Int, val title: String, val short: String, val weight: String, val activities: List<String>)

object CareAreas {
    val ALL = listOf(
        CareArea(
            1, "Mobilität", "Mobilität", "10 %",
            listOf("Im Bett umdrehen / Lage wechseln", "Aufrecht sitzen halten", "Aufstehen und Umsetzen (z. B. Bett – Stuhl)", "In der Wohnung gehen", "Treppen steigen"),
        ),
        CareArea(
            2, "Verstehen und Sprechen", "Kognition", "15 % (Bereich 2 oder 3)",
            listOf(
                "Personen erkennen", "Orientierung (Ort)", "Orientierung (Zeit, Tag)", "Sich an Wichtiges erinnern",
                "Alltagshandlungen in der richtigen Reihenfolge", "Entscheidungen im Alltag", "Sachverhalte verstehen",
                "Gefahren erkennen", "Bedürfnisse mitteilen", "Aufforderungen verstehen", "Gespräch führen",
            ),
        ),
        CareArea(
            3, "Verhalten und Psyche", "Verhalten", "15 % (Bereich 2 oder 3)",
            listOf(
                "Unruhe, Umherlaufen", "Nächtliche Unruhe", "Sich selbst gefährden oder verletzen", "Gegenstände beschädigen",
                "Körperlich aggressiv", "Beschimpfen, verbal aggressiv", "Rufen, Schreien, Jammern", "Pflege abwehren",
                "Wahnvorstellungen", "Ängste", "Antriebslos, niedergeschlagen", "Unangemessenes Verhalten",
            ),
        ),
        CareArea(
            4, "Selbstversorgung", "Selbstversorgung", "40 %",
            listOf(
                "Waschen (Oberkörper vorne)", "Kämmen, Rasieren, Zähne, Prothese", "Intimbereich waschen", "Duschen oder Baden",
                "Oberkörper an- und auskleiden", "Unterkörper an- und auskleiden", "Essen mundgerecht zubereiten, Getränk eingießen",
                "Essen", "Trinken", "Toilette benutzen", "Inkontinenz versorgen (Urin)", "Inkontinenz versorgen (Stuhl)",
            ),
        ),
        CareArea(
            5, "Krankheit und Therapie", "Therapie", "20 %",
            listOf(
                "Medikamente geben", "Spritzen (z. B. Insulin)", "Blutzucker oder Blutdruck messen", "Verband, Wunde versorgen",
                "Kompressionsstrümpfe an- und ausziehen", "Katheter, Stoma, Absaugen", "Übungen, Therapie zu Hause",
                "Arztbesuch begleiten", "Therapie oder Behandlung außer Haus begleiten", "Diät oder Trinkmenge einhalten",
            ),
        ),
        CareArea(
            6, "Alltag und Kontakte", "Alltag", "15 %",
            listOf(
                "Tagesablauf gestalten", "Ruhen und Schlafen", "Sich beschäftigen", "In die Zukunft planen",
                "Mit Menschen im Haus Kontakt halten", "Kontakt zu Menschen außerhalb",
            ),
        ),
    )

    fun of(module: Int): CareArea = ALL.firstOrNull { it.module == module } ?: ALL[3]

    /** Zusammenfassung je Taetigkeit fuer den Bericht: an wie vielen Tagen, wie oft, Minuten, Art der Hilfe. */
    data class ActivityStat(
        val module: Int,
        val activity: String,
        val days: Int,
        val timesPerDay: Double,
        val minutesPerDay: Int,
        val nights: Int,
        val helps: List<String>,
        val notes: List<String>,
    )

    fun stats(entries: List<CareLogEntry>): List<ActivityStat> =
        entries.groupBy { it.module to it.activity }.map { (key, list) ->
            val days = list.map { it.day }.distinct().size.coerceAtLeast(1)
            ActivityStat(
                module = key.first,
                activity = key.second,
                days = days,
                timesPerDay = list.sumOf { it.times }.toDouble() / days,
                minutesPerDay = list.sumOf { it.minutes } / days,
                nights = list.filter { it.night }.map { it.day }.distinct().size,
                helps = list.mapNotNull { HelpKind.of(it.help)?.short }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key },
                notes = list.map { it.note.trim() }.filter { it.isNotBlank() }.distinct().take(3),
            )
        }.sortedWith(compareBy({ it.module }, { -it.days }))
}
