package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Medication
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Nächste Einnahme: Tag, Uhrzeit, Medikamente; [overdue] = Zeit ist vorbei, aber noch nicht abgehakt. */
data class NextIntake(
    val day: Long,
    val time: String,
    val names: List<String>,
    val overdue: Boolean,
    /** Offene Medikamente dieser Uhrzeit (zum Abhaken im Widget) */
    val medIds: List<Long> = emptyList(),
    /** Mindestens eines mit Einnahme-Fenster (Menge und Werte): dann öffnet das Widget die App */
    val ask: Boolean = false,
)

/** Inhalt des Startbildschirm-Widgets (nur Text, ohne Android). */
object WidgetInfo {
    private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm", Locale.GERMANY)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Nächste offene Einnahme (gleiche Regeln wie die Tageslage). Offen heißt: weder abgehakt noch als vergessen
     * oder nicht gegeben vermerkt. Bis [graceMinutes] nach der Uhrzeit gilt sie als „offen seit …“, danach zählt die nächste.
     */
    fun nextIntake(meds: List<Medication>, checkins: List<Checkin>, now: LocalDateTime, graceMinutes: Long = Tageslage.GRACE_MINUTES): NextIntake? {
        val today = now.toLocalDate().toEpochDay()
        for (s in Tageslage.intakeSlots(meds, checkins, today)) {
            if (s.open.isEmpty()) continue
            val at = now.toLocalDate().atTime(s.time)
            if (at.plusMinutes(graceMinutes).isBefore(now)) continue
            return NextIntake(
                today, s.timeText, s.open.map { it.name }.distinct(), overdue = at.isBefore(now),
                medIds = s.open.map { it.id }.distinct(), ask = s.open.any { it.askAtIntake },
            )
        }
        // Morgen: erste Einnahme des Tages
        val first = Tageslage.intakeSlots(meds, checkins, today + 1).firstOrNull() ?: return null
        return NextIntake(today + 1, first.timeText, first.all.map { it.name }.distinct(), overdue = false)
    }

    /** Nächster Termin ab jetzt (laufende bis 30 Minuten nach Beginn), abgesagte und verschobene zählen nicht. */
    fun nextAppointment(appointments: List<Appointment>, checkins: List<Checkin>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Appointment? {
        val from = now.minusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        return appointments.filter { it.whenMillis >= from }
            .sortedBy { it.whenMillis }
            .firstOrNull { a -> !Tageslage.skipped(a, checkins, zone) }
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
