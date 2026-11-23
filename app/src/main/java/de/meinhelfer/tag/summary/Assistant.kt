package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Responsibility
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.data.Duties
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Gesichtsausdruck des Assistenten. */
enum class Expression { Friendly, Happy, Attentive, Concerned, Thinking, Calm }

/** Was ein Vorschlag tun würde, wenn du ihn übernimmst. */
sealed interface ProposalAction {
    data class RaisePriority(val todoId: Long) : ProposalAction
    data class Postpone(val todoId: Long, val newDueDay: Long) : ProposalAction
    data class ReorderTask(val personId: Long, val text: String, val doctorId: Long?, val dueDay: Long) : ProposalAction
    /** Verbrauch pro Tag anpassen (Vorrat wird dafür auf heute umgerechnet). */
    data class AdjustDailyUse(val medId: Long, val daily: Double) : ProposalAction
    /** Heute neu angebrochen (Haltbarkeit beginnt neu). */
    data class OpenedToday(val medId: Long) : ProposalAction
    /** Neue Beträge der Pflegekasse für ein Jahr übernehmen. */
    data class SetBenefits(val year: Int, val monthly: Double, val yearly: Double, val carryMonth: Int) : ProposalAction
    /** Nur ein Hinweis: gelesen. */
    data object Acknowledge : ProposalAction
    /** Wecker in der Uhr-App stellen (Stunde, Minute, Beschriftung) */
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : ProposalAction
    /** Neue App-Version laden und danach die Installation starten (Android fragt zum Schluss selbst nach). */
    data class StartUpdate(val version: String) : ProposalAction
}

/** Ein Vorschlag des Assistenten. Wird nur nach deiner Bestätigung ausgeführt. */
data class Proposal(
    /** Schlüssel zum Ausblenden („Nein“), enthält den Stand, damit ein neuer Anlass wieder erscheint */
    val key: String,
    val text: String,
    val confirmLabel: String,
    val action: ProposalAction,
)

/** Vorbereitung auf einen Termin in den nächsten 36 Stunden. */
data class AppointmentPrep(val appointment: Appointment, val personName: String, val whenText: String, val hints: List<String>)

/**
 * Persönlicher Assistent: für die pflegende Person. Rechnet auf dem Handy, schlägt vor und erklärt;
 * ändert nichts ohne Bestätigung. Nutzt die gemeinsame [Tageslage].
 */
object Assistant {
    const val DEFAULT_NAME = "Sunny"
    val NAME_SUGGESTIONS = listOf("Sunny", "Lumi", "Elio", "Hella", "Mila", "Frieda", "Kompass")

    private val DOCTOR_WORDS = Regex("(?i)arzt|ärztin|dr\\.|praxis|klinik|ambulanz|krankenhaus|zahn|facharzt|sprechstunde|kontrolle|untersuchung")

    /**
     * Vorschläge: Fristen bald fällig (hochstufen), länger überfällig (verschieben), knapper Vorrat ohne Aufgabe
     * (Nachbestellen beim zuständigen Arzt, Vertretung beachtet). Ausgeblendete ([dismissed]) fehlen.
     */
    fun proposals(
        persons: List<Person>,
        todos: List<Todo>,
        meds: List<Medication>,
        duties: List<Responsibility>,
        doctors: List<Doctor>,
        today: Long,
        dismissed: Set<String>,
        /** Abhak-Einträge der letzten zwei Wochen (für die tatsächlich gegebenen Mengen) */
        checkins: List<de.meinhelfer.tag.data.Checkin> = emptyList(),
        /** Abweichende Beträge der Pflegekasse und Hinweise ([BenefitRates]) */
        rateChanges: List<RateChange> = emptyList(),
        rateNotes: List<RateNote> = emptyList(),
    ): List<Proposal> {
        val out = mutableListOf<Proposal>()
        // Beträge der Pflegekasse: nur mit Pflegegrad interessant
        if (persons.any { (it.careLevel ?: 0) in 1..5 }) {
            rateChanges.forEach { c ->
                out += Proposal(c.key, BenefitRates.changeText(c), "Übernehmen", ProposalAction.SetBenefits(c.year, c.monthlyNew, c.yearlyNew, c.carryNew))
            }
            rateNotes.forEach { n ->
                out += Proposal(n.key, "Pflegekasse: " + n.text, "Gelesen", ProposalAction.Acknowledge)
            }
        }
        val open = todos.filter { !it.done && !it.archived }
        val multi = persons.size > 1
        fun who(personId: Long) = if (multi) persons.firstOrNull { it.id == personId }?.name?.let { " ($it)" }.orEmpty() else ""

        // Frist in den nächsten zwei Tagen, aber nicht dringend
        open.filter { it.dueDay != null && it.dueDay in today..(today + 2) && it.priority < 2 }
            .sortedBy { it.dueDay }
            .forEach { t ->
                out += Proposal(
                    key = "prio:${t.id}:${t.dueDay}",
                    text = "„${t.text}“${who(t.personId)} ist ${Tageslage.dayWord(t.dueDay!!, today)} fällig. Soll ich sie auf dringend setzen?",
                    confirmLabel = "Auf dringend",
                    action = ProposalAction.RaisePriority(t.id),
                )
            }
        // Seit mindestens drei Tagen überfällig: ehrlich neu planen
        open.filter { it.dueDay != null && it.dueDay <= today - 3 }
            .sortedBy { it.dueDay }
            .forEach { t ->
                val days = today - t.dueDay!!
                out += Proposal(
                    key = "spaeter:${t.id}:${t.dueDay}",
                    text = "„${t.text}“${who(t.personId)} ist seit $days Tagen überfällig. Neue Frist in drei Tagen, oder abgeben?",
                    confirmLabel = "Frist +3 Tage",
                    action = ProposalAction.Postpone(t.id, today + 3),
                )
            }
        // Knapper Vorrat ohne offene Aufgabe dazu; beim selben Arzt werden Rezepte gebündelt
        // (was dort in den nächsten zwei Wochen ohnehin knapp wird, gleich mitbestellen)
        data class Need(val m: Medication, val name: String, val left: Long, val doctorId: Long?, val substitute: Boolean)
        val needs = meds.mapNotNull { m ->
            val left = m.daysLeft(today) ?: return@mapNotNull null
            if (left > 14) return@mapNotNull null
            val name = m.name.trim().ifEmpty { return@mapNotNull null }
            if (open.any { it.personId == m.personId && it.text.contains(name, ignoreCase = true) }) return@mapNotNull null
            val resolved = Duties.resolve(duties, m.doctorId, m.personId, Duties.REZEPT, today)
            Need(m, name, left.toLong(), resolved.id, resolved.substitute != null)
        }
        val groups = needs.groupBy { it.doctorId ?: -it.m.id }.values.filter { g -> g.any { it.left <= 7 } }
        groups.forEach { g ->
            val urgent = g.filter { it.left <= 7 }.sortedBy { it.left }
            val first = urgent.first()
            val doctor = doctors.firstOrNull { it.id == first.doctorId }
            val due = if (first.left <= 3) today else today + 1
            fun leftText(n: Need) = if (n.left <= 0) "heute" else if (n.left == 1L) "einen Tag" else "${n.left} Tage"
            if (g.size == 1) {
                val text = "Rezept für ${first.name}" + (doctor?.let { " bei ${it.name}" } ?: "") + " bestellen"
                out += Proposal(
                    key = "bestellen:${first.m.id}:${today / 7}",
                    text = "${first.name}${who(first.m.personId)} reicht nur noch ${leftText(first)}. Aufgabe „$text“ anlegen?" +
                        (if (first.substitute) " (Vertretung ist eingetragen.)" else ""),
                    confirmLabel = "Aufgabe anlegen",
                    action = ProposalAction.ReorderTask(first.m.personId, text, first.doctorId, due),
                )
            } else {
                val names = g.sortedBy { it.left }.map { it.name + who(it.m.personId) }
                val later = g.filter { it.left > 7 }
                val text = "Rezepte" + (doctor?.let { " bei ${it.name}" } ?: "") + " bestellen: " + g.sortedBy { it.left }.joinToString(", ") { it.name }
                out += Proposal(
                    key = "bestellen:" + g.map { it.m.id }.sorted().joinToString("-") + ":${today / 7}",
                    text = "${first.name}${who(first.m.personId)} reicht nur noch ${leftText(first)}" +
                        (if (later.isNotEmpty()) ", " + later.joinToString(", ") { "${it.name} noch ${it.left} Tage" } else "") +
                        ". In einem Weg bestellen und abholen: " + names.joinToString(", ") + ". Aufgabe anlegen?" +
                        (if (g.any { it.substitute }) " (Vertretung ist eingetragen.)" else ""),
                    confirmLabel = "Eine Aufgabe für alle",
                    action = ProposalAction.ReorderTask(first.m.personId, text, first.doctorId, due),
                )
            }
        }
        // Angebrochen: Haltbarkeit läuft ab (Packungsbeilage), dann neuen anbrechen
        meds.forEach { m ->
            val left = MedOpen.daysLeft(m.openedDay, m.openLifeDays, today) ?: return@forEach
            if (left > 1) return@forEach
            val name = m.name.trim().ifEmpty { return@forEach }
            out += Proposal(
                key = "anbruch:${m.id}:${m.openedDay}",
                text = when {
                    left < 0 -> "$name${who(m.personId)} ist seit ${if (left == -1L) "gestern" else "${-left} Tagen"} über der Haltbarkeit nach dem Anbruch. " +
                        "Neuen anbrechen und hier vermerken?"
                    left == 0L -> "$name${who(m.personId)}: Heute ist der letzte Tag nach dem Anbruch. Ab morgen einen neuen nehmen."
                    else -> "$name${who(m.personId)} ist nur noch bis morgen haltbar (nach dem Anbruch). Schon mal einen neuen bereitlegen."
                },
                confirmLabel = "Heute neu angebrochen",
                action = ProposalAction.OpenedToday(m.id),
            )
        }
        // Tatsächlich gegebene Mengen weichen vom eingetragenen Verbrauch ab (z. B. Insulin nach Schema)
        meds.forEach { m ->
            val (actual, days) = IntakeStats.actualDaily(checkins, m.id, today) ?: return@forEach
            if (!IntakeStats.differs(actual, m.dailyUse)) return@forEach
            val rounded = kotlin.math.round(actual * 2) / 2.0
            out += Proposal(
                key = "verbrauch:${m.id}:${Amounts.fmt(rounded)}:${today / 7}",
                text = "${m.name.trim()}${who(m.personId)}: An $days Tagen wurden im Schnitt ${Amounts.withUnit(actual, m.unit)} pro Tag gegeben, " +
                    "eingetragen sind ${Amounts.withUnit(m.dailyUse, m.unit)}. Verbrauch für die Vorrat-Rechnung auf ${Amounts.withUnit(rounded, m.unit)} anpassen?",
                confirmLabel = "Anpassen",
                action = ProposalAction.AdjustDailyUse(m.id, rounded),
            )
        }
        return out.filter { it.key !in dismissed }.take(5)
    }

    /** Termine in den nächsten 36 Stunden mit kurzer Checkliste (ohne abgesagte und verschobene). */
    fun preps(
        persons: List<Person>,
        appointments: List<Appointment>,
        checkins: List<de.meinhelfer.tag.data.Checkin>,
        meds: List<Medication>,
        doctors: List<Doctor>,
        vitalsKinds: Map<Long, Boolean>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<AppointmentPrep> {
        val from = now.atZone(zone).toInstant().toEpochMilli()
        val to = now.plusHours(36).atZone(zone).toInstant().toEpochMilli()
        val today = now.toLocalDate().toEpochDay()
        return appointments.filter { it.whenMillis in from..to && !Tageslage.skipped(it, checkins, zone) }
            .sortedBy { it.whenMillis }
            .take(3)
            .map { a ->
                val doctor = doctors.firstOrNull { it.id == a.doctorId }
                val medical = doctor != null || DOCTOR_WORDS.containsMatchIn(a.title + " " + a.place)
                val hints = buildList {
                    if (medical) add("Versichertenkarte und ggf. Überweisung einstecken")
                    if (medical && meds.any { it.personId == a.personId }) add("Medikamentenplan mitnehmen (Medikamente → Plan als PDF)")
                    if (medical && vitalsKinds[a.personId] == true) add("Werte der letzten Tage zeigen (Trinken und Werte → PDF)")
                    val docs = a.docIds.split(",").count { it.isNotBlank() }
                    if (docs > 0) add(if (docs == 1) "1 Unterlage ist am Termin hinterlegt" else "$docs Unterlagen sind am Termin hinterlegt")
                    if (doctor != null && (doctor.floor.isNotBlank() || doctor.elevator == 2)) {
                        add("Vor Ort: " + listOfNotNull(doctor.floor.takeIf { it.isNotBlank() }, if (doctor.elevator == 2) "kein Aufzug" else null).joinToString(", "))
                    }
                    if (medical) add("Fragen an die Praxis vorher als Bemerkung am Termin notieren")
                }
                val day = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
                AppointmentPrep(
                    appointment = a,
                    personName = persons.firstOrNull { it.id == a.personId }?.name.orEmpty(),
                    whenText = Tageslage.dayWord(day, today).replaceFirstChar { it.uppercase() } + " " + Tageslage.time(a.whenMillis, zone),
                    hints = hints,
                )
            }
    }

    /** Ausdruck passend zur Lage. */
    fun expression(line: List<NextUp>, support: SupportLevel, doneToday: Int, now: LocalDateTime): Expression {
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val soon = line.any { it.atMillis != null && it.atMillis - nowMillis in 0..3_600_000 }
        return when {
            support != SupportLevel.None -> Expression.Concerned
            line.any { it.overdue } || soon -> Expression.Attentive
            now.hour >= 21 || now.hour < 6 -> Expression.Calm
            doneToday >= 3 || line.isEmpty() -> Expression.Happy
            else -> Expression.Friendly
        }
    }

    /** Ein Satz in der Ich-Form zur Lage, z. B. „Als Nächstes: Medikamente 12:00 …“. */
    fun headline(line: List<NextUp>, multiPerson: Boolean): String {
        fun label(n: NextUp) = n.title + if (multiPerson && n.personName.isNotBlank()) " (${n.personName})" else ""
        val overdue = line.filter { it.overdue }
        return when {
            line.isEmpty() -> "Für heute sehe ich nichts Offenes mehr. Gönn dir eine Pause."
            overdue.isNotEmpty() -> "Noch offen: ${label(overdue.first())}." +
                if (line.size > 1) " Insgesamt ${line.size} Punkte für heute." else ""
            else -> "Als Nächstes: ${label(line.first())}." +
                if (line.size > 1) " Danach noch ${line.size - 1} " + (if (line.size == 2) "Punkt" else "Punkte") + " heute." else ""
        }
    }
}
