package de.meinhelfer.tag.summary

/** Beträge eines Jahres: Entlastungsbetrag je Monat, Gemeinsamer Jahresbetrag, Übertrag aus dem Vorjahr nutzbar bis Monat. */
data class RateYear(val year: Int, val monthly: Double, val yearly: Double, val carryMonth: Int, val status: String, val source: String)

/** Hinweis zu geplanten Änderungen (z. B. Reform noch nicht beschlossen). */
data class RateNote(val date: String, val status: String, val text: String) {
    val key: String get() = "leistung-hinweis:$date:${text.hashCode()}"
}

data class Rates(val stand: String, val years: List<RateYear>, val notes: List<RateNote>)

/** Abweichung zwischen veröffentlichten und in der App eingestellten Beträgen. */
data class RateChange(
    val year: Int,
    val monthlyOld: Double,
    val monthlyNew: Double,
    val yearlyOld: Double,
    val yearlyNew: Double,
    val carryOld: Int,
    val carryNew: Int,
    val source: String,
) {
    val key: String get() = "leistung:$year:$monthlyNew:$yearlyNew:$carryNew"
}

/**
 * Beträge der Pflegekasse aus der Datei daten/leistungen.txt im Repo (gepflegt nach den Leistungsübersichten
 * des Bundesgesundheitsministeriums). Übernommen wird nur, was als „gilt“ oder „beschlossen“ markiert ist,
 * und nur nach Bestätigung. „geplant“ erscheint als Hinweis.
 *
 * Format (Semikolon): „stand;2026-11-23“, „jahr;2026;131;3539;6;gilt;Quelle“, „hinweis;2026-11-23;geplant;Text“.
 */
object BenefitRates {
    private fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    fun parse(text: String): Rates {
        var stand = ""
        val years = mutableListOf<RateYear>()
        val notes = mutableListOf<RateNote>()
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val p = line.split(";")
            when (p[0].trim().lowercase()) {
                "stand" -> stand = p.getOrNull(1)?.trim().orEmpty()
                "jahr" -> {
                    val y = p.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
                    val m = p.getOrNull(2)?.let { num(it) } ?: return@forEach
                    val j = p.getOrNull(3)?.let { num(it) } ?: return@forEach
                    val c = p.getOrNull(4)?.trim()?.toIntOrNull()?.coerceIn(0, 12) ?: 6
                    years += RateYear(y, m, j, c, p.getOrNull(5)?.trim()?.lowercase().orEmpty(), p.drop(6).joinToString(";").trim())
                }
                "hinweis" -> {
                    if (p.size < 4) return@forEach
                    notes += RateNote(p[1].trim(), p[2].trim().lowercase(), p.drop(3).joinToString(";").trim())
                }
            }
        }
        return Rates(stand, years, notes)
    }

    /**
     * Was weicht ab? Nur gültige oder beschlossene Werte für dieses und nächstes Jahr.
     * [current] liefert je Jahr (Monatsbetrag, Jahresbetrag, Übertrag bis Monat) wie in der App eingestellt.
     */
    fun changes(rates: Rates, thisYear: Int, current: (Int) -> Triple<Double, Double, Int>): List<RateChange> =
        rates.years.filter { it.year in thisYear..thisYear + 1 && (it.status == "gilt" || it.status == "beschlossen") }
            .mapNotNull { r ->
                val (m, j, c) = current(r.year)
                if (kotlin.math.abs(m - r.monthly) < 0.005 && kotlin.math.abs(j - r.yearly) < 0.005 && c == r.carryMonth) null
                else RateChange(r.year, m, r.monthly, j, r.yearly, c, r.carryMonth, r.source)
            }

    /** Offene Hinweise (geplant oder beschlossen), höchstens die letzten drei. */
    fun openNotes(rates: Rates): List<RateNote> = rates.notes.takeLast(3)

    fun changeText(c: RateChange): String {
        val parts = mutableListOf<String>()
        if (kotlin.math.abs(c.monthlyOld - c.monthlyNew) >= 0.005) {
            parts += if (c.monthlyNew <= 0) "Entlastungsbetrag entfällt (bisher ${Budgets.euro(c.monthlyOld)} im Monat)"
            else "Entlastungsbetrag ${Budgets.euro(c.monthlyOld)} → ${Budgets.euro(c.monthlyNew)} im Monat"
        }
        if (kotlin.math.abs(c.yearlyOld - c.yearlyNew) >= 0.005) {
            parts += if (c.yearlyNew <= 0) "Gemeinsamer Jahresbetrag entfällt" else "Gemeinsamer Jahresbetrag ${Budgets.euro(c.yearlyOld)} → ${Budgets.euro(c.yearlyNew)}"
        }
        if (c.carryOld != c.carryNew) {
            parts += if (c.carryNew == 0) "Rest aus ${c.year - 1} verfällt am 31.12.${c.year - 1}" else "Rest aus ${c.year - 1} nutzbar bis Ende ${monthName(c.carryNew)} ${c.year}"
        }
        return "Neue Beträge der Pflegekasse für ${c.year}: " + parts.joinToString("; ") + "." +
            (if (c.source.isNotBlank()) " Quelle: ${c.source}." else "") + " In der App übernehmen?"
    }

    private fun monthName(m: Int): String = listOf("", "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")[m.coerceIn(0, 12)]
}
