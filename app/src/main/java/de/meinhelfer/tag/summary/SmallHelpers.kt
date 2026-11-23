package de.meinhelfer.tag.summary

import java.time.LocalDate
import java.time.MonthDay

/** Geburtstage: der betreuten Personen und eigene Einträge (z. B. Oma, Nachbarin). Nur lokal. */
object Birthdays {
    data class Entry(val name: String, val month: Int, val day: Int, val year: Int? = null)
    data class Upcoming(val entry: Entry, val date: LocalDate, val daysUntil: Long, val age: Int?)

    private val LINE = Regex("""^\s*(.+?)[\s,:;–-]+(\d{1,2})\.(\d{1,2})\.(\d{4})?\s*$""")

    /** Eine Zeile wie „Oma Erna, 14.03.1936“ oder „Nachbarin Gabi 2.7.“. Ungültiges wird übersprungen. */
    fun parse(text: String): List<Entry> = text.lines().mapNotNull { l ->
        val m = LINE.find(l) ?: return@mapNotNull null
        val (name, d, mo, y) = m.destructured
        val day = d.toInt()
        val month = mo.toInt()
        if (month !in 1..12 || runCatching { MonthDay.of(month, day) }.isFailure) return@mapNotNull null
        Entry(name.trim(), month, day, y.toIntOrNull())
    }

    fun format(e: Entry): String = "${e.name}, %02d.%02d.".format(e.day, e.month) + (e.year?.toString() ?: "")

    /** Nächster Geburtstag ab [today] (29.02. in Nicht-Schaltjahren am 28.02.). */
    fun next(e: Entry, today: LocalDate): Upcoming {
        fun inYear(y: Int): LocalDate = if (e.month == 2 && e.day == 29 && !java.time.Year.isLeap(y.toLong())) LocalDate.of(y, 2, 28) else LocalDate.of(y, e.month, e.day)
        var date = inYear(today.year)
        if (date.isBefore(today)) date = inYear(today.year + 1)
        val days = date.toEpochDay() - today.toEpochDay()
        return Upcoming(e, date, days, e.year?.let { date.year - it })
    }

    /** Geburtstage in den nächsten [within] Tagen (heute eingeschlossen), nächste zuerst. */
    fun upcoming(entries: List<Entry>, today: LocalDate, within: Int = 7): List<Upcoming> =
        entries.map { next(it, today) }.filter { it.daysUntil <= within }.sortedBy { it.daysUntil }

    fun sentence(u: Upcoming): String {
        val age = u.age?.let { " ($it)" }.orEmpty()
        return when (u.daysUntil) {
            0L -> "Heute hat ${u.entry.name} Geburtstag$age – vielleicht kurz gratulieren?"
            1L -> "Morgen hat ${u.entry.name} Geburtstag$age."
            else -> "In ${u.daysUntil} Tagen hat ${u.entry.name} Geburtstag$age."
        }
    }
}

/** Wetter zum Termin als kurzer Hinweis (Daten von Open-Meteo, siehe [de.meinhelfer.tag.info.Weather]). Nur ein Anhaltspunkt. */
object WeatherHint {
    /**
     * [temp] in °C, [rain] Regenwahrscheinlichkeit in %, [code] WMO-Wettercode.
     * Leer, wenn das Wetter nichts Besonderes verlangt.
     */
    fun text(temp: Double, rain: Int, code: Int, time: String): String {
        val snow = code in 71..77 || code in 85..86
        val storm = code in 95..99
        val parts = mutableListOf<String>()
        when {
            snow -> parts += "Schnee erwartet – warme Schuhe, mehr Zeit für den Weg"
            storm -> parts += "Gewitter möglich – wenn es geht, Abfahrt anpassen"
            rain >= 50 -> parts += "Regen wahrscheinlich ($rain %) – Schirm oder Regenjacke"
        }
        if (temp <= 1.0 && !snow && (rain >= 30 || code in 48..67)) parts += "Glätte möglich – vorsichtig, mehr Zeit einplanen"
        if (temp <= 3.0 && !snow) parts += "kalt – warme Jacke"
        if (temp >= 28.0) parts += "heiß – Wasser mitnehmen, Sonne meiden"
        if (parts.isEmpty()) return ""
        return "Wetter um $time: ${kotlin.math.round(temp).toInt()} °C, " + parts.joinToString("; ")
    }
}

/** Losfahren: wann die Erinnerung kommt (Wegzeit plus Puffer vor dem Termin). */
object Leave {
    fun at(whenMillis: Long, travelMinutes: Int, bufferMinutes: Int): Long = whenMillis - (travelMinutes + bufferMinutes) * 60_000L

    fun text(title: String, time: String, travelMinutes: Int, bufferMinutes: Int, place: String): String =
        "In $bufferMinutes Minuten losfahren: $title um $time Uhr (ca. $travelMinutes Min. Weg)" + (if (place.isNotBlank()) ", $place" else "") + "."
}
