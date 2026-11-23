package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Person
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wecker bei frühen Terminen: Sunny sagt rechtzeitig Bescheid und bietet an, einen Wecker zu stellen.
 * Gestellt wird erst nach deinem Tipp.
 */
object WakeUp {
    /** Ein früher Termin und die vorgeschlagene Weckzeit (Minuten des Tages). */
    data class Plan(
        val appointment: Appointment,
        val personName: String,
        /** Epoch-Tag des Termins */
        val day: Long,
        val apptMinute: Int,
        val alarmMinute: Int,
        val tomorrow: Boolean,
    ) {
        val key get() = "wecker_${appointment.id}_${appointment.whenMillis}"
    }

    /**
     * Frühe Termine (Beginn vor [earlyBefore]) für morgen – angeboten ab [offerFrom] – oder heute, solange die Weckzeit noch kommt.
     * Je Tag nur der früheste Termin. [lead] = Minuten zwischen Wecken und Termin.
     */
    fun plans(
        persons: List<Person>,
        appointments: List<Appointment>,
        checkins: List<Checkin>,
        now: LocalDateTime,
        zone: ZoneId,
        earlyBefore: Int = 10 * 60,
        lead: Int = 90,
        offerFrom: Int = 14 * 60,
    ): List<Plan> {
        val today = now.toLocalDate().toEpochDay()
        val nowMinute = now.hour * 60 + now.minute
        return appointments
            .asSequence()
            .filter { !Tageslage.skipped(it, checkins, zone) }
            .map { a ->
                val t = Instant.ofEpochMilli(a.whenMillis).atZone(zone)
                Triple(a, t.toLocalDate().toEpochDay(), t.hour * 60 + t.minute)
            }
            .filter { (_, day, minute) -> minute < earlyBefore && (day == today || day == today + 1) }
            .groupBy { it.second }
            .mapNotNull { (day, list) ->
                val (a, _, minute) = list.minBy { it.third }
                val alarm = (minute - lead).coerceAtLeast(3 * 60)
                val tomorrow = day == today + 1
                val ok = if (tomorrow) nowMinute >= offerFrom && alarm < nowMinute else alarm > nowMinute
                if (!ok) null
                else Plan(a, persons.firstOrNull { it.id == a.personId }?.name.orEmpty(), day, minute, alarm, tomorrow)
            }
            .sortedBy { it.day }
            .toList()
    }

    fun hm(minute: Int) = "%d:%02d".format(minute / 60, minute % 60)

    /** Vorschlag für die Karte des Assistenten. */
    fun proposal(p: Plan, multi: Boolean, label: String): Proposal = Proposal(
        key = p.key,
        text = (if (p.tomorrow) "Morgen früh" else "Heute") + " um ${hm(p.apptMinute)}: ${p.appointment.title}" +
            (if (multi && p.personName.isNotBlank()) " (${p.personName})" else "") +
            ". Soll ich dir einen Wecker für ${hm(p.alarmMinute)} stellen?",
        confirmLabel = "Wecker ${hm(p.alarmMinute)} stellen",
        action = ProposalAction.SetAlarm(p.alarmMinute / 60, p.alarmMinute % 60, label + ": " + p.appointment.title),
    )

    /** Satz für den Abendüberblick. */
    fun eveningLine(plans: List<Plan>): String = plans.firstOrNull { it.tomorrow }?.let {
        "Morgen geht es früh los (${hm(it.apptMinute)} ${it.appointment.title}) – denk an den Wecker, ich schlage ${hm(it.alarmMinute)} vor. In meiner Karte stelle ich ihn dir mit einem Tipp."
    }.orEmpty()
}
