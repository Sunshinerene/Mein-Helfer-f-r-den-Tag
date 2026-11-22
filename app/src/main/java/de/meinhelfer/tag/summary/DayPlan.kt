package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.data.VitalEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Eine Zeile im Tagesplan, optional mit Kaestchen zum Abhaken auf Papier.
 * [boxes] = Reihe kleiner Kaestchen unter der Zeile (z. B. je ein Glas Wasser), 0 = keine.
 */
data class PlanLine(val text: String, val sub: String? = null, val check: Boolean = true, val boxes: Int = 0)

data class PlanSection(val title: String, val lines: List<PlanLine>)

/** Tagesplan fuer heute: Einnahmen, Termine, Aufgaben, Nachbestellen, Ausblick auf morgen. Rein lokal. */
object DayPlan {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    val TITLE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)

    private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun timeText(a: Appointment, zone: ZoneId): String {
        val start = Instant.ofEpochMilli(a.whenMillis).atZone(zone).format(TIME)
        val end = a.endMillis?.let { "–" + Instant.ofEpochMilli(it).atZone(zone).format(TIME) }.orEmpty()
        return "$start$end Uhr"
    }

    private fun contact(doctors: List<Doctor>, id: Long?): String? = doctors.firstOrNull { it.id == id }?.let { d ->
        listOfNotNull(d.name + if (d.specialty.isNotBlank()) " (${d.specialty})" else "", d.phone.takeIf { it.isNotBlank() }?.let { "Tel. $it" })
            .joinToString(", ")
    }

    private fun taskLine(t: Todo, today: Long, doctors: List<Doctor>): PlanLine {
        val due = t.dueDay
        val meta = listOfNotNull(
            if (t.priority == 2) "dringend" else null,
            when {
                due == null -> null
                due < today -> "überfällig seit " + LocalDate.ofEpochDay(due).format(DATE)
                due == today -> "heute fällig"
                else -> "fällig bis " + LocalDate.ofEpochDay(due).format(DATE)
            },
            contact(doctors, t.doctorId),
        ).joinToString(" · ")
        return PlanLine(t.text, meta.ifBlank { null })
    }

    fun build(
        person: Person?,
        meds: List<Medication>,
        appointments: List<Appointment>,
        todos: List<Todo>,
        doctors: List<Doctor>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        vitals: List<VitalEntry> = emptyList(),
        drinkGoal: Int = 0,
    ): List<PlanSection> {
        val day = today.toEpochDay()
        val out = mutableListOf<PlanSection>()

        person?.important?.takeIf { it.isNotBlank() }?.let {
            out += PlanSection("Wichtig", listOf(PlanLine(it, check = false)))
        }

        val intakes = meds.filter { day >= it.createdDay }
            .flatMap { m -> m.timeList().map { SummaryBuilder.normSlot(it) }.distinct().map { it to m } }
            .sortedBy { it.first }
            .map { (time, m) -> PlanLine("$time Uhr – ${m.name}" + if (m.dosage.isNotBlank()) " (${m.dosage})" else "", m.notes.takeIf { it.isNotBlank() }) }
        if (intakes.isNotEmpty()) out += PlanSection("Einnahmen heute", intakes)

        val appts = appointments.filter { dayOf(it.whenMillis, zone) == today }.sortedBy { it.whenMillis }.map { a ->
            val doctor = doctors.firstOrNull { it.id == a.doctorId }
            val where = a.place.ifBlank { doctor?.address.orEmpty() }
            PlanLine(
                "${timeText(a, zone)} – ${a.title}",
                listOfNotNull(contact(doctors, a.doctorId), where.takeIf { it.isNotBlank() }, a.notes.takeIf { it.isNotBlank() })
                    .joinToString(" · ").ifBlank { null },
            )
        }
        if (appts.isNotEmpty()) out += PlanSection("Termine heute", appts)

        val open = todos.filter { !it.done && !it.archived }
        val now = open.filter { (it.dueDay != null && it.dueDay <= day) || it.priority == 2 }
            .sortedWith(compareBy<Todo> { it.dueDay ?: Long.MAX_VALUE }.thenByDescending { it.priority })
        if (now.isNotEmpty()) out += PlanSection("Aufgaben: heute erledigen", now.map { taskLine(it, day, doctors) })
        val soon = open.filter { it !in now && it.dueDay != null && it.dueDay in (day + 1)..(day + 3) }.sortedBy { it.dueDay }
        if (soon.isNotEmpty()) out += PlanSection("Aufgaben: in den nächsten 3 Tagen", soon.map { taskLine(it, day, doctors) })

        drinkAndValues(vitals, drinkGoal, day, zone)?.let { out += it }

        val low = meds.filter { (it.daysLeft(day) ?: Int.MAX_VALUE) <= 7 }.map { m ->
            val d = m.daysLeft(day) ?: 0
            PlanLine("${m.name} nachbestellen", if (d <= 0) "Vorrat aufgebraucht" else "reicht noch ca. $d Tage")
        }
        if (low.isNotEmpty()) out += PlanSection("Bald nachbestellen", low)

        val tomorrow = today.plusDays(1)
        val next = appointments.filter { dayOf(it.whenMillis, zone) == tomorrow }.sortedBy { it.whenMillis }
            .map { PlanLine("${timeText(it, zone)} – ${it.title}", contact(doctors, it.doctorId), check = false) }
        if (next.isNotEmpty()) out += PlanSection("Morgen", next)
        return out
    }

    /** Glas = 200 ml; hoechstens 12 Kaestchen auf dem Papier. */
    const val GLASS_ML = 200

    private val SHORT = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)

    /**
     * Trinken und Werte: Trinkziel (ausser bei Ziel 0) mit Kaestchen zum Abhaken (je Glas), gestern getrunken, und fuer jeden Wert,
     * der in den letzten 14 Tagen gemessen wurde, eine Zeile zum Eintragen mit dem letzten Wert als Hinweis.
     */
    internal fun drinkAndValues(vitals: List<VitalEntry>, drinkGoal: Int, day: Long, zone: ZoneId): PlanSection? {
        val lines = mutableListOf<PlanLine>()
        val drinks = vitals.filter { it.kind == VitalKind.Trinken.key }
        // Trinkziel 0 (in „Trinken und Werte“ einstellbar) blendet die Zeile aus
        if (drinkGoal > 0) {
            val yesterday = drinks.filter { it.day == day - 1 }.sumOf { it.value }.toInt()
            val glasses = ((drinkGoal + GLASS_ML - 1) / GLASS_ML).coerceIn(1, 12)
            lines += PlanLine(
                "Trinkziel heute: ${String.format(Locale.GERMANY, "%,d", drinkGoal)} ml – je Kästchen ein Glas (${GLASS_ML} ml)",
                if (yesterday > 0) "Gestern getrunken: ${String.format(Locale.GERMANY, "%,d", yesterday)} ml" else null,
                check = false,
                boxes = glasses,
            )
        }
        VitalKind.MEASURES.forEach { k ->
            val last = vitals.filter { it.kind == k.key && it.day in (day - 14)..day }.maxByOrNull { it.atMillis } ?: return@forEach
            val unit = last.unit.ifBlank { k.unit }
            val at = Instant.ofEpochMilli(last.atMillis).atZone(zone)
            val blank = if (k == VitalKind.Blutdruck) "______ / ______" else "__________"
            lines += PlanLine(
                "${k.label}: $blank $unit   um ______ Uhr",
                "Zuletzt: " + Vitals.valueText(last) + " am " + at.format(SHORT) + " um " + at.format(TIME) + " Uhr",
                check = false,
            )
        }
        return if (lines.isEmpty()) null else PlanSection("Trinken und Werte", lines)
    }
}
