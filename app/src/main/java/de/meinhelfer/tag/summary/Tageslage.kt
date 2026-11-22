package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Eine Einnahmezeit an einem Tag mit den noch offenen Medikamenten. */
data class IntakeSlot(val personId: Long, val day: Long, val time: LocalTime, val open: List<Medication>, val all: List<Medication>) {
    val timeText: String get() = time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

enum class NextKind { Intake, Appointment, Task }

/** Ein Punkt im Tageslauf (über alle Personen), z. B. Einnahme, Termin oder fällige Aufgabe. */
data class NextUp(
    val kind: NextKind,
    /** Zeitpunkt; null bei Aufgaben ohne Uhrzeit */
    val atMillis: Long?,
    val title: String,
    val personId: Long,
    val personName: String,
    /** Zeit vorbei bzw. Frist überschritten, aber noch offen */
    val overdue: Boolean,
    /** Termin- bzw. Aufgaben-ID, bei Einnahmen 0 */
    val refId: Long = 0,
)

/**
 * Tageslage: die eine gemeinsame Berechnung, was heute ansteht und was offen ist.
 * Genutzt vom Assistenten und vom Widget (nach und nach auch von Heute-Seite und Tagesplan),
 * damit überall dasselbe steht.
 */
object Tageslage {
    /** Eine offene Einnahme gilt bis so viele Minuten nach der Uhrzeit noch als „offen seit …“. */
    const val GRACE_MINUTES = 120L

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    internal fun parseTime(t: String): LocalTime? = runCatching {
        val (h, m) = t.trim().split(":").map { it.trim().toInt() }
        LocalTime.of(h, m)
    }.getOrNull()

    /** Abgehakt oder als vergessen bzw. nicht gegeben vermerkt. */
    fun handled(m: Medication, day: Long, time: LocalTime, checkins: List<Checkin>): Boolean = checkins.any { c ->
        c.kind == Checkin.MED && c.refId == m.id && c.day == day && parseTime(c.slot) == time && (c.done || c.status.isNotEmpty())
    }

    /** Alle Einnahmezeiten eines Tages (eine Person oder alle), sortiert, mit den noch offenen Medikamenten. */
    fun intakeSlots(meds: List<Medication>, checkins: List<Checkin>, day: Long): List<IntakeSlot> =
        meds.filter { it.createdDay <= day }
            .flatMap { m -> m.timeList().mapNotNull { t -> parseTime(t)?.let { Triple(m.personId, it, m) } } }
            .groupBy({ it.first to it.second }, { it.third })
            .map { (key, list) ->
                val all = list.distinctBy { it.id }
                IntakeSlot(key.first, day, key.second, all.filterNot { handled(it, day, key.second, checkins) }, all)
            }
            .sortedWith(compareBy<IntakeSlot> { it.time }.thenBy { it.personId })

    /** Abgesagter oder verschobener Termin? */
    fun skipped(a: Appointment, checkins: List<Checkin>, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val day = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
        return checkins.any { c ->
            c.kind == Checkin.APPT && c.refId == a.id && c.day == day && (c.status == Checkin.CANCELLED || c.status == Checkin.MOVED)
        }
    }

    /**
     * Was heute noch ansteht, über alle Personen: offene Einnahmen (auch „offen seit“), Termine ab jetzt bis Tagesende,
     * fällige und überfällige Aufgaben. Überfälliges zuerst, dann nach Uhrzeit, Aufgaben ohne Uhrzeit am Ende.
     */
    fun dayLine(
        persons: List<Person>,
        meds: List<Medication>,
        checkins: List<Checkin>,
        appointments: List<Appointment>,
        todos: List<Todo>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NextUp> {
        val today = now.toLocalDate()
        val day = today.toEpochDay()
        val names = persons.associate { it.id to it.name }
        val out = mutableListOf<NextUp>()
        intakeSlots(meds, checkins, day).forEach { s ->
            if (s.open.isEmpty()) return@forEach
            val at = today.atTime(s.time)
            if (at.plusMinutes(GRACE_MINUTES).isBefore(now)) return@forEach
            out += NextUp(
                NextKind.Intake,
                at.atZone(zone).toInstant().toEpochMilli(),
                "Medikamente ${s.timeText}: " + s.open.map { it.name }.distinct().joinToString(", "),
                s.personId,
                names[s.personId].orEmpty(),
                overdue = at.isBefore(now),
            )
        }
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val from = now.minusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        appointments.filter { it.whenMillis in from until endOfDay && !skipped(it, checkins, zone) }.forEach { a ->
            out += NextUp(
                NextKind.Appointment,
                a.whenMillis,
                "Termin " + Instant.ofEpochMilli(a.whenMillis).atZone(zone).format(HM) + ": " + a.title,
                a.personId,
                names[a.personId].orEmpty(),
                overdue = false,
                refId = a.id,
            )
        }
        todos.filter { !it.done && !it.archived && it.dueDay != null && it.dueDay <= day }.forEach { t ->
            out += NextUp(NextKind.Task, null, t.text, t.personId, names[t.personId].orEmpty(), overdue = t.dueDay!! < day, refId = t.id)
        }
        return out.sortedWith(
            compareByDescending<NextUp> { it.overdue && it.kind != NextKind.Task }
                .thenBy { it.atMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.overdue }
                .thenByDescending { t -> todos.firstOrNull { it.id == t.refId && t.kind == NextKind.Task }?.priority ?: 0 },
        )
    }

    /** Uhrzeit eines Zeitpunkts als Text. */
    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(millis).atZone(zone).format(HM)

    /** Tag als Text: „heute“, „morgen“ oder „am 03.12.“ */
    fun dayWord(day: Long, today: Long): String = when (day - today) {
        0L -> "heute"
        1L -> "morgen"
        2L -> "übermorgen"
        else -> "am " + LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd.MM."))
    }
}
