package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Eine Zeile im Tagesplan, optional mit Kaestchen zum Abhaken auf Papier. */
data class PlanLine(val text: String, val sub: String? = null, val check: Boolean = true)

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
}
