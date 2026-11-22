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
    /** Im Zeitraum erledigte Aufgaben mit Ergebnis. */
    val tasksDone: List<String> = emptyList(),
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
        section("Erledigte Aufgaben", tasksDone)
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
        todos: List<de.meinhelfer.tag.data.Todo> = emptyList(),
        vitals: List<de.meinhelfer.tag.data.VitalEntry> = emptyList(),
        drinkGoal: Int = 0,
        careLog: List<de.meinhelfer.tag.data.CareLogEntry> = emptyList(),
        ranges: Map<String, Pair<Double?, Double?>> = emptyMap(),
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
        var onTime = 0
        var late = 0
        var early = 0
        var forgot = 0
        var skipped = 0
        var unanswered = 0
        var delaySum = 0
        var delayCount = 0
        val perMed = LinkedHashMap<String, IntArray>() // Name -> [geplant, genommen]
        val forgotPerMed = LinkedHashMap<String, Int>()
        val skippedPerMed = LinkedHashMap<String, Int>()
        val missed = mutableListOf<String>()
        val lateList = mutableListOf<String>()
        val forgotList = mutableListOf<String>()
        val skippedList = mutableListOf<String>()
        val perDay = mutableListOf<String>()

        var day = start
        while (!day.isAfter(last)) {
            var dayPlanned = 0
            var dayTaken = 0
            for (med in meds) {
                if (day.toEpochDay() < med.createdDay) continue
                for (slot in med.timeList().map { normSlot(it) }.distinct()) {
                    val check = medChecks[Triple(med.id, day.toEpochDay(), slot)]
                    val done = check?.done == true
                    val st = check?.status.orEmpty()
                    val stillAhead = day == today && ParseTime.of(slot)?.isAfter(now) == true
                    if (stillAhead && !done) {
                        openToday++
                        continue
                    }
                    planned++
                    dayPlanned++
                    val counter = perMed.getOrPut(med.name) { IntArray(2) }
                    counter[0]++
                    val label = "${day.format(DATE_ONLY)} $slot ${med.name}"
                    if (done) {
                        taken++
                        dayTaken++
                        counter[1]++
                        when (st) {
                            Checkin.LATE -> {
                                late++
                                val a = ParseTime.of(check?.actual.orEmpty())
                                val p = ParseTime.of(slot)
                                val delay = if (a != null && p != null) (a.toSecondOfDay() - p.toSecondOfDay()) / 60 else null
                                if (delay != null && delay > 0) {
                                    delaySum += delay
                                    delayCount++
                                    lateList += "$label (+$delay Min.)"
                                } else {
                                    lateList += label
                                }
                            }
                            Checkin.EARLY -> early++
                            else -> onTime++
                        }
                    } else when (st) {
                        Checkin.MISSED -> {
                            forgot++
                            forgotList += label
                            forgotPerMed[med.name] = (forgotPerMed[med.name] ?: 0) + 1
                        }
                        Checkin.SKIPPED -> {
                            skipped++
                            skippedList += label
                            skippedPerMed[med.name] = (skippedPerMed[med.name] ?: 0) + 1
                        }
                        else -> {
                            unanswered++
                            missed += label
                        }
                    }
                }
            }
            if (dayPlanned > 0) perDay += "${day.format(SHORT_DAY)}: $dayTaken von $dayPlanned Einnahmen genommen"
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
        val apptCancelled = mutableListOf<String>()
        val apptMoved = mutableListOf<String>()
        for (a in apptsInRange) {
            val d = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate()
            val check = apptChecks[a.id to d.toEpochDay()]
            val label = "${d.format(DATE_ONLY)} ${a.title}"
            when {
                check?.done == true -> apptDone++
                check?.status == Checkin.CANCELLED -> apptCancelled += label
                check?.status == Checkin.MOVED -> apptMoved += label
                a.whenMillis > nowMillis -> apptUpcoming++
                else -> apptMissed += label
            }
        }
        val apptPast = apptDone + apptMissed.size + apptCancelled.size + apptMoved.size

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
        if (rate != null) {
            overview += "Medikamente: $taken von $planned Einnahmen genommen ($rate %)"
            val parts = listOfNotNull(
                "pünktlich: $onTime",
                if (late > 0) "später: $late" else null,
                if (early > 0) "früher: $early" else null,
                if (forgot > 0) "vergessen: $forgot" else null,
                if (skipped > 0) "bewusst nicht gegeben: $skipped" else null,
                if (unanswered > 0) "nicht beantwortet: $unanswered" else null,
            )
            if (parts.size > 1) overview += "Davon " + parts.joinToString(", ")
        }
        if (openToday > 0) overview += "Heute noch offen: $openToday Einnahme(n)"
        if (apptsInRange.isNotEmpty()) {
            val absent = apptCancelled.size + apptMoved.size
            overview += "Termine: $apptDone wahrgenommen" +
                (if (absent > 0) ", $absent abgesagt oder verschoben" else "") +
                (if (apptMissed.isNotEmpty()) ", ${apptMissed.size} ohne Antwort" else "") +
                ", $apptUpcoming anstehend"
        }
        // ------------------------------------------------ Erledigte Aufgaben
        val fromMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val doneTasks = todos.filter { t -> t.done && t.doneMillis != null && t.doneMillis in fromMillis until toMillis }
            .sortedBy { it.doneMillis }
        val tasksDone = doneTasks.map { t ->
            val at = java.time.Instant.ofEpochMilli(t.doneMillis ?: 0).atZone(zone).toLocalDate().format(DATE_ONLY)
            val stars = if (t.rating in 1..5) " " + "★".repeat(t.rating) + "☆".repeat(5 - t.rating) else ""
            "$at ${t.text}" + (if (t.result.isNotBlank()) " – Ergebnis: ${t.result}" else "") + stars
        }
        if (doneTasks.isNotEmpty()) overview += "Aufgaben erledigt: ${doneTasks.size}"
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
                    notable += "Einige Einnahmen sind nicht als genommen eingetragen ($rate % genommen)."
                }
                else -> {
                    notable += "Nur $rate % der Einnahmen sind als genommen eingetragen."
                }
            }
            if (rate < 90) {
                advice += "Prüfen, woran es lag (Uhrzeit, Vergessen, Verweigerung) und ggf. Einnahmezeiten anpassen oder die Erinnerungen nutzen. Bei Unsicherheit Arzt oder Apotheke fragen – Dosierung nie eigenmächtig ändern."
            }
            perMed.filter { (_, v) -> v[0] >= 3 && v[1] * 100 / v[0] < 70 }.forEach { (name, v) ->
                notable += "$name: nur ${v[1]} von ${v[0]} Einnahmen genommen."
            }
            fun listed(head: String, items: List<String>) {
                if (items.isEmpty()) return
                val more = if (items.size > 6) " (+${items.size - 6} weitere)" else ""
                notable += "$head: ${items.take(6).joinToString("; ")}$more"
            }
            if (late > 0) {
                val avg = if (delayCount > 0) " – im Schnitt ${delaySum / delayCount} Min. später" else ""
                listed("Später genommen$avg", lateList)
            }
            listed("Vergessen", forgotList)
            listed("Bewusst nicht gegeben", skippedList)
            listed("Ohne Antwort (weder abgehakt noch nachgetragen)", missed)
            forgotPerMed.filter { it.value >= 2 }.forEach { (name, n) ->
                notable += "$name wurde $n-mal vergessen."
            }
            skippedPerMed.filter { it.value >= 2 }.forEach { (name, n) ->
                notable += "$name wurde $n-mal bewusst nicht gegeben."
            }
            if (forgot >= 2) {
                advice += "Mehrfach vergessen: Erinnerungen der App nutzen, eine Wochen-Dosierbox oder feste Anker im Tagesablauf prüfen. Bei Unsicherheit Apotheke fragen."
            }
            if (late >= 3) {
                advice += "Öfter später genommen: mit Arzt oder Apotheke besprechen, ob die Einnahmezeiten besser in den Tag passen. Zeiten nie eigenmächtig ändern, bei manchen Medikamenten sind die Abstände wichtig."
            }
            if (skipped >= 1) {
                advice += "Bewusst nicht gegebene Medikamente (z. B. abgelehnt) beim nächsten Arztbesuch ansprechen und Gründe notieren, z. B. Nebenwirkungen oder Schluckprobleme."
            }
            if (unanswered > 0) {
                advice += "Offene Rückfragen auf der Heute-Seite beantworten (genommen, später, vergessen), dann wird die Auswertung genauer."
            }
        }
        if (apptPast > 0 && apptMissed.isEmpty() && apptCancelled.isEmpty() && apptMoved.isEmpty()) good += "Alle vergangenen Termine wahrgenommen."
        if (apptCancelled.isNotEmpty() || apptMoved.isNotEmpty()) {
            notable += "Termine abgesagt oder verschoben: " + (apptCancelled + apptMoved).joinToString("; ")
            advice += "Neuen Termin ausmachen bzw. prüfen, ob der neue Termin schon eingetragen ist. Die Aufgabe dazu steht auf der Heute-Seite."
        }
        if (apptMissed.isNotEmpty()) {
            notable += "Termine ohne Antwort: ${apptMissed.joinToString("; ")}"
            advice += "Bei vergangenen Terminen angeben, ob sie stattgefunden haben, abgesagt oder verschoben wurden."
        }
        if (healthHit) {
            notable += "In den Bemerkungen sind mögliche gesundheitliche Auffälligkeiten notiert."
            advice += "Die notierten Auffälligkeiten beim nächsten Arztbesuch ansprechen. Bei starken oder plötzlichen Beschwerden sofort ärztlichen Rat holen (Bereitschaftsdienst 116 117, Notfall 112)."
        }
        if (mobilityHit) {
            advice += "Wegen Schmerzen, Sturz oder Beweglichkeit: mit dem Hausarzt über Physiotherapie (Heilmittelverordnung) sprechen und ggf. einen Termin ausmachen."
        }
        if (movedHit && apptCancelled.isEmpty() && apptMoved.isEmpty()) {
            notable += "Ein Termin wurde als verschoben oder abgesagt vermerkt."
            advice += "Prüfen, ob der neue Termin schon eingetragen ist."
        }
        lowStock.forEach { m ->
            val d = m.daysLeft(today.toEpochDay())
            notable += if (d == null || d <= 0) "${m.name}: Vorrat aufgebraucht." else "${m.name}: Vorrat reicht nur noch ca. $d Tage."
        }
        if (lowStock.isNotEmpty()) {
            advice += "Rezept bzw. Nachbestellung rechtzeitig anfragen: ${lowStock.joinToString(", ") { it.name }}."
        } else if (meds.any { it.dailyUse > 0 } && meds.all { it.stockKnown }) {
            good += "Vorrat reicht bei allen Medikamenten für mehr als 7 Tage."
        }
        meds.filter { !it.stockKnown }.takeIf { it.isNotEmpty() }?.let { unknown ->
            advice += "Vorrat noch nicht eingetragen: ${unknown.joinToString(", ") { it.name }}."
        }
        if (notable.isEmpty() && (planned > 0 || apptsInRange.isNotEmpty())) good += "Keine Auffälligkeiten festgestellt."
        if (doneTasks.isNotEmpty()) good += "${doneTasks.size} Aufgabe(n) erledigt."
        // ---- Trinken und Messwerte (nur aus den eigenen Eintraegen, keine Bewertung)
        val drinks = vitals.filter { it.kind == VitalKind.Trinken.key }.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.value }.toInt() }
        if (drinks.isNotEmpty()) {
            val avg = drinks.values.sum() / drinks.size
            overview += "Trinkmenge: im Schnitt $avg ml pro Tag (an ${drinks.size} Tag(en) eingetragen)"
            if (drinkGoal > 0) {
                // Der heutige Tag zaehlt erst am Abend mit
                val low = drinks.filter { (d, ml) -> ml < drinkGoal && (d < today.toEpochDay() || now.hour >= 20) }
                if (low.isEmpty()) {
                    good += "Trinkziel ($drinkGoal ml) an allen eingetragenen Tagen erreicht."
                } else {
                    notable += "Trinkziel ($drinkGoal ml) an ${low.size} Tag(en) nicht erreicht: " +
                        low.entries.sortedBy { it.key }.take(7).joinToString("; ") { LocalDate.ofEpochDay(it.key).format(DATE_ONLY) + " " + it.value + " ml" }
                    advice += "Getränke sichtbar hinstellen und feste Trinkzeiten einplanen (z. B. zu jeder Mahlzeit ein Glas). Bei Herz- oder Nierenerkrankungen die Trinkmenge mit dem Arzt absprechen."
                }
            }
        }
        VitalKind.MEASURES.forEach { k ->
            val list = vitals.filter { it.kind == k.key }
            if (list.isEmpty()) return@forEach
            val unit = list.last().unit.ifBlank { k.unit }
            val same = list.filter { it.unit.ifBlank { k.unit } == unit }
            val dec = if (k == VitalKind.Blutzucker && unit == "mmol/l") 1 else k.decimals
            val avg = Vitals.number(same.map { it.value }.average(), dec) +
                (same.mapNotNull { it.value2 }.takeIf { it.isNotEmpty() }?.let { "/" + Vitals.number(it.average(), dec) } ?: "")
            val text = if (k == VitalKind.Gewicht && same.size >= 2) {
                "Gewicht: von ${Vitals.valueText(same.first())} auf ${Vitals.valueText(same.last())}"
            } else {
                "${k.label}: ${list.size} Messung(en), im Schnitt $avg $unit"
            }
            overview += text
            val outside = list.filter { Vitals.outside(it, ranges[k.key]) }
            if (outside.isNotEmpty()) {
                notable += "${k.label} ${outside.size}-mal außerhalb des eingetragenen Bereichs: " +
                    outside.take(5).joinToString("; ") { LocalDate.ofEpochDay(it.day).format(DATE_ONLY) + " " + Vitals.valueText(it) }
                advice += "Werte außerhalb des Bereichs beim nächsten Arztbesuch ansprechen, bei Beschwerden früher."
            }
        }
        if (careLog.isNotEmpty()) {
            overview += "Pflegetagebuch: ${careLog.size} Einträge an ${careLog.map { it.day }.distinct().size} Tag(en)"
        }

        if (planned == 0 && apptsInRange.isEmpty() && notes.isEmpty() && doneTasks.isEmpty() && vitals.isEmpty() && careLog.isEmpty()) {
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
            tasksDone = tasksDone,
        )
    }
}

private object ParseTime {
    fun of(slot: String): LocalTime? =
        ReminderScheduler.parseTime(slot)?.let { (h, m) -> LocalTime.of(h, m) }
}
