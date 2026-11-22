package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Medication
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Nächste Einnahme: Tag, Uhrzeit, Medikamente; [overdue] = Zeit ist vorbei, aber noch nicht abgehakt. */
data class NextIntake(val day: Long, val time: String, val names: List<String>, val overdue: Boolean)

/** Inhalt des Startbildschirm-Widgets (nur Text, ohne Android). */
object WidgetInfo {
    private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm", Locale.GERMANY)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun parse(t: String): LocalTime? = runCatching {
        val (h, m) = t.trim().split(":").map { it.trim().toInt() }
        LocalTime.of(h, m)
    }.getOrNull()

    /**
     * Nächste offene Einnahme. Offen heißt: weder abgehakt noch als vergessen oder nicht gegeben vermerkt.
     * Bis [graceMinutes] nach der Uhrzeit gilt eine offene Einnahme noch als „offen seit …“, danach zählt die nächste.
     */
    fun nextIntake(meds: List<Medication>, checkins: List<Checkin>, now: LocalDateTime, graceMinutes: Long = 120): NextIntake? {
        val today = now.toLocalDate().toEpochDay()
        fun slots(day: Long): Map<LocalTime, List<Medication>> =
            meds.filter { it.createdDay <= day }
                .flatMap { m -> m.timeList().mapNotNull { t -> parse(t)?.let { it to m } } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()

        fun handled(m: Medication, day: Long, time: LocalTime): Boolean = checkins.any { c ->
            c.kind == Checkin.MED && c.refId == m.id && c.day == day && parse(c.slot) == time && (c.done || c.status.isNotEmpty())
        }

        for ((time, list) in slots(today)) {
            val open = list.filterNot { handled(it, today, time) }
            if (open.isEmpty()) continue
            val at = now.toLocalDate().atTime(time)
            if (at.plusMinutes(graceMinutes).isBefore(now)) continue
            return NextIntake(today, time.format(TIME), open.map { it.name }.distinct(), overdue = at.isBefore(now))
        }
        // Morgen: erste Einnahme des Tages
        val tomorrow = today + 1
        val first = slots(tomorrow).entries.firstOrNull() ?: return null
        return NextIntake(tomorrow, first.key.format(TIME), first.value.map { it.name }.distinct(), overdue = false)
    }

    /** Nächster Termin ab jetzt (laufende bis 30 Minuten nach Beginn), abgesagte und verschobene zählen nicht. */
    fun nextAppointment(appointments: List<Appointment>, checkins: List<Checkin>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Appointment? {
        val from = now.minusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        return appointments.filter { it.whenMillis >= from }
            .sortedBy { it.whenMillis }
            .firstOrNull { a ->
                val day = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
                checkins.none { c ->
                    c.kind == Checkin.APPT && c.refId == a.id && c.day == day && (c.status == Checkin.CANCELLED || c.status == Checkin.MOVED)
                }
            }
    }

    /** „Heute“, „Morgen“ oder „Mo 24.11.,“ mit Uhrzeit. */
    fun whenText(millis: Long, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        val days = at.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        return when (days) {
            0L -> "Heute " + at.format(TIME)
            1L -> "Morgen " + at.format(TIME)
            else -> at.format(DAY_TIME)
        }
    }

    /** Zeile für die Einnahme, z. B. „Heute 12:00 · Ramipril, Torasemid“ oder ohne Namen „Heute 12:00 · 2 Medikamente“. */
    fun intakeLine(next: NextIntake?, now: LocalDate, details: Boolean): String {
        if (next == null) return "Keine Einnahmen geplant"
        val prefix = when {
            next.overdue -> "Offen seit ${next.time}"
            next.day == now.toEpochDay() -> "Heute ${next.time}"
            next.day == now.toEpochDay() + 1 -> "Morgen ${next.time}"
            else -> next.time
        }
        val what = if (details) {
            next.names.take(3).joinToString(", ") + if (next.names.size > 3) " +${next.names.size - 3}" else ""
        } else {
            if (next.names.size == 1) "1 Medikament" else "${next.names.size} Medikamente"
        }
        return "$prefix · $what"
    }

    fun appointmentLine(next: Appointment?, now: LocalDateTime, details: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        if (next == null) return "Kein Termin geplant"
        val w = whenText(next.whenMillis, now, zone)
        return if (details && next.title.isNotBlank()) "$w · ${next.title}" else w
    }
}
