package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.DayNote
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.reminder.ReminderScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Period(val label: String) { Day("Tag"), Week("Woche"), Month("Monat") }

/** Ergebnis der Auswertung – alles aus den lokal gespeicherten Eingaben errechnet, nichts wird uebertragen. */
data class Summary(
    val title: String,
    val overview: List<String>,
    val good: List<String>,
    val notable: List<String>,
    val advice: List<String>,
    val notes: List<String>,
    val perDay: List<String>,
) {
    fun toText(): String = buildString {
        appendLine("Auswertung: $title")
        fun section(head: String, lines: List<String>) {
            if (lines.isEmpty()) return
            appendLine()
            appendLine(head)
            lines.forEach { appendLine("- $it") }
        }
        section("Überblick", overview)
        section("Gut gelaufen", good)
        section("Auffällig", notable)
        section("Empfehlungen", advice)
        section("Bemerkungen", notes)
        section("Verlauf", perDay)
        appendLine()
        append(DISCLAIMER)
    }

    companion object {
        const val DISCLAIMER =
            "Automatische Zusammenfassung aus den eigenen Eingaben – kein ärztlicher Rat. Bei Fragen oder starken Beschwerden bitte Arzt oder Apotheke kontaktieren."
    }
}

object SummaryBuilder {
    private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMANY)
    private val DATE_ONLY = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)
    private val DATE_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMANY)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    private val HEALTH_WORDS = listOf(
        "nebenwirk", "übel", "erbroch", "schwindel", "schmerz", "sturz", "gestürzt", "fieber",
        "atemnot", "ausschlag", "durchfall", "verwirrt", "unruhig", "appetit", "schwach", "müde", "kreislauf",
    )
    private val MOBILITY_WORDS = listOf(
        "sturz", "gestürzt", "schmerz", "rücken", "gelenk", "steif", "schwäche", "mobil", "gangunsicher", "bewegung",
    )
    private val MOVED_WORDS = listOf("verschoben", "abgesagt", "ausgefallen", "neuer termin")

    /** "8:0" / "08:00" -> "08:00". Einheitlicher Schluessel fuer Einnahmezeiten. */
    fun normSlot(slot: String): String =
        ReminderScheduler.parseTime(slot)?.let { "%02d:%02d".format(it.first, it.second) } ?: slot.trim()

    fun range(period: Period, offset: Int, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        Period.Day -> today.plusDays(offset.toLong()).let { it to it }
        Period.Week -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
            monday to monday.plusDays(6)
        }
        Period.Month -> {
            val first = today.withDayOfMonth(1).plusMonths(offset.toLong())
            first to first.with(TemporalAdjusters.lastDayOfMonth())
        }
    }

    fun title(period: Period, start: LocalDate, end: LocalDate, today: LocalDate): String = when (period) {
        Period.Day -> {
            val text = start.format(DAY_FORMAT)
            if (start == today) "Heute, $text" else text
        }
        Period.Week -> "Woche ${start.format(DATE_ONLY)}–${end.format(DATE_FULL)}"
        Period.Month -> start.format(MONTH_FORMAT).replaceFirstChar { it.uppercase() }
    }

    fun build(
        period: Period,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
        now: LocalTime,
        meds: List<Medication>,
        appointments: List<Appointment>,
        checkins: List<Checkin>,
        dayNotes: List<DayNote>,
    ): Summary {
        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val last = if (end.isAfter(today)) today else end

        // ------------------------------------------------ Medikamente
        val medChecks = checkins.filter { it.kind == Checkin.MED }
            .associateBy { Triple(it.refId, it.day, normSlot(it.slot)) }

        var planned = 0
        var taken = 0
        var openToday = 0
        val perMed = LinkedHashMap<String, IntArray>() // Name -> [geplant, abgehakt]
        val missed = mutableListOf<String>()
        val perDay = mutableListOf<String>()

        var day = start
        while (!day.isAfter(last)) {
            var dayPlanned = 0
            var dayTaken = 0
            for (med in meds) {
                if (day.toEpochDay() < med.createdDay) continue
                for (slot in med.timeList().map { normSlot(it) }.distinct()) {
                    val done = medChecks[Triple(med.id, day.toEpochDay(), slot)]?.done == true
                    val stillAhead = day == today && ParseTime.of(slot)?.isAfter(now) == true
                    if (stillAhead && !done) {
                        openToday++
                        continue
                    }
                    planned++
                    dayPlanned++
                    val counter = perMed.getOrPut(med.name) { IntArray(2) }
                    counter[0]++
                    if (done) {
                        taken++
                        dayTaken++
                        counter[1]++
                    } else {
                        missed += "${day.format(DATE_ONLY)} $slot ${med.name}"
                    }
                }
            }
            if (dayPlanned > 0) perDay += "${day.format(SHORT_DAY)}: $dayTaken von $dayPlanned Einnahmen abgehakt"
            day = day.plusDays(1)
        }
        val rate = if (planned > 0) taken * 100 / planned else null

        // ------------------------------------------------ Termine
        val apptsInRange = appointments.filter {
            val d = Instant.ofEpochMilli(it.whenMillis).atZone(zone).toLocalDate()
            !d.isBefore(start) && !d.isAfter(end)
        }.sortedBy { it.whenMillis }
        val apptChecks = checkins.filter { it.kind == Checkin.APPT }.associateBy { it.refId to it.day }
        var apptDone = 0
        var apptUpcoming = 0
        val apptMissed = mutableListOf<String>()
        for (a in apptsInRange) {
            val d = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate()
            val done = apptChecks[a.id to d.toEpochDay()]?.done == true
            when {
                done -> apptDone++
                a.whenMillis > nowMillis -> apptUpcoming++
                else -> apptMissed += "${d.format(DATE_ONLY)} ${a.title}"
            }
        }
        val apptPast = apptDone + apptMissed.size

        // ------------------------------------------------ Bemerkungen
        val notes = mutableListOf<String>()
        val noteTexts = mutableListOf<String>()
        val medById = meds.associateBy { it.id }
        val apptById = appointments.associateBy { it.id }
        checkins.filter { it.note.isNotBlank() }.sortedWith(compareBy({ it.day }, { it.slot })).forEach { c ->
            val date = LocalDate.ofEpochDay(c.day).format(DATE_ONLY)
            val line = when (c.kind) {
                Checkin.MED -> medById[c.refId]?.let { "$date ${normSlot(c.slot)} ${it.name}: ${c.note}" }
                else -> apptById[c.refId]?.let { "$date Termin „${it.title}“: ${c.note}" }
            }
            if (line != null) {
                notes += line
                noteTexts += c.note
            }
        }
        dayNotes.filter { it.text.isNotBlank() }.sortedBy { it.day }.forEach { n ->
            notes += "${LocalDate.ofEpochDay(n.day).format(DATE_ONLY)} Tag: ${n.text}"
            noteTexts += n.text
        }
        val lowerNotes = noteTexts.joinToString(" ").lowercase(Locale.GERMANY)
        val healthHit = HEALTH_WORDS.any { lowerNotes.contains(it) }
        val mobilityHit = MOBILITY_WORDS.any { lowerNotes.contains(it) }
        val movedHit = MOVED_WORDS.any { lowerNotes.contains(it) }

        // ------------------------------------------------ Vorrat
        val lowStock = meds.filter { m -> (m.daysLeft(today.toEpochDay()) ?: Int.MAX_VALUE) <= 7 }

        // ------------------------------------------------ Zusammenstellen
        val overview = mutableListOf<String>()
        if (rate != null) overview += "Medikamente: $taken von $planned Einnahmen abgehakt ($rate %)"
        if (openToday > 0) overview += "Heute noch offen: $openToday Einnahme(n)"
        if (apptsInRange.isNotEmpty()) {
            overview += "Termine: $apptDone erledigt, ${apptMissed.size} nicht abgehakt, $apptUpcoming anstehend"
        }
        if (notes.isNotEmpty()) overview += "Bemerkungen: ${notes.size}"
        if (overview.isEmpty()) overview += "Für diesen Zeitraum liegen noch keine Einträge vor."

        val good = mutableListOf<String>()
        val notable = mutableListOf<String>()
        val advice = mutableListOf<String>()

        if (rate != null) {
            when {
                rate >= 90 -> {
                    good += "Medikamentenplan gut eingehalten ($rate %)."
                    advice += "Medikamentenplan wie bisher beibehalten."
                }
                rate >= 70 -> {
                    notable += "Einige Einnahmen wurden nicht abgehakt ($rate % abgehakt)."
                }
                else -> {
                    notable += "Nur $rate % der Einnahmen wurden abgehakt."
                }
            }
            if (rate < 90) {
                advice += "Prüfen, woran es lag (Uhrzeit, Vergessen, Verweigerung) und ggf. Einnahmezeiten anpassen oder die Erinnerungen nutzen. Bei Unsicherheit Arzt oder Apotheke fragen – Dosierung nie eigenmächtig ändern."
            }
            perMed.filter { (_, v) -> v[0] >= 3 && v[1] * 100 / v[0] < 70 }.forEach { (name, v) ->
                notable += "$name: nur ${v[1]} von ${v[0]} Einnahmen abgehakt."
            }
            if (missed.isNotEmpty()) {
                val shown = missed.take(6).joinToString("; ")
                val more = if (missed.size > 6) " (+${missed.size - 6} weitere)" else ""
                notable += "Nicht abgehakt: $shown$more"
            }
        }
        if (apptPast > 0 && apptMissed.isEmpty()) good += "Alle vergangenen Termine erledigt."
        if (apptMissed.isNotEmpty()) {
            notable += "Termine nicht abgehakt: ${apptMissed.joinToString("; ")}"
            advice += "Vergangene Termine abhaken oder eine Bemerkung ergänzen (z. B. verschoben oder abgesagt)."
        }
        if (healthHit) {
            notable += "In den Bemerkungen sind mögliche gesundheitliche Auffälligkeiten notiert."
            advice += "Die notierten Auffälligkeiten beim nächsten Arztbesuch ansprechen. Bei starken oder plötzlichen Beschwerden sofort ärztlichen Rat holen (Bereitschaftsdienst 116 117, Notfall 112)."
        }
        if (mobilityHit) {
            advice += "Wegen Schmerzen, Sturz oder Beweglichkeit: mit dem Hausarzt über Physiotherapie (Heilmittelverordnung) sprechen und ggf. einen Termin ausmachen."
        }
        if (movedHit) {
            notable += "Ein Termin wurde als verschoben oder abgesagt vermerkt."
            advice += "Prüfen, ob der neue Termin schon eingetragen ist."
        }
        lowStock.forEach { m ->
            val d = m.daysLeft(today.toEpochDay())
            notable += if (d == null || d <= 0) "${m.name}: Vorrat aufgebraucht." else "${m.name}: Vorrat reicht nur noch ca. $d Tage."
        }
        if (lowStock.isNotEmpty()) {
            advice += "Rezept bzw. Nachbestellung rechtzeitig anfragen: ${lowStock.joinToString(", ") { it.name }}."
        } else if (meds.any { it.dailyUse > 0 }) {
            good += "Vorrat reicht bei allen Medikamenten für mehr als 7 Tage."
        }
        if (notable.isEmpty() && (planned > 0 || apptsInRange.isNotEmpty())) good += "Keine Auffälligkeiten festgestellt."
        if (planned == 0 && apptsInRange.isEmpty() && notes.isEmpty()) {
            advice += "Einnahmen und Termine auf der Heute-Seite abhaken und Bemerkungen ergänzen – dann wird die Auswertung aussagekräftiger."
        }

        return Summary(
            title = title(period, start, end, today),
            overview = overview,
            good = good,
            notable = notable,
            advice = advice.distinct(),
            notes = notes,
            perDay = if (period == Period.Day) emptyList() else perDay,
        )
    }
}

private object ParseTime {
    fun of(slot: String): LocalTime? =
        ReminderScheduler.parseTime(slot)?.let { (h, m) -> LocalTime.of(h, m) }
}
