package de.meinhelfer.tag.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Termine über alle Personen eines Haushalts abgleichen: gleichzeitige Termine, gleicher Arzt kurz nacheinander,
 * volle Tage (mit Aufwand für Weg und Termin), Termine in der Werkstatt- oder Tagespflegezeit, Ausweis läuft ab.
 * Ergebnis sind Vorschläge – angelegt wird nur nach Tipp.
 */
object HouseholdPlanner {
    data class Member(val id: Long, val name: String, val household: Household, val disability: Disability = Disability())
    data class Appt(val id: Long, val personId: Long, val title: String, val whenMillis: Long, val doctorId: Long? = null, val doctorName: String = "")

    /** Vorschlag: [task] = Text für eine Aufgabe (null = nur Hinweis). */
    data class Hint(val key: String, val text: String, val personId: Long, val task: String? = null, val doctorId: Long? = null, val due: Long? = null)

    /** Haushalte: Gruppen von Personen, die laut Profil zusammenleben (Verknüpfung in beide Richtungen zählt). */
    fun groups(members: List<Member>): List<List<Member>> {
        val ids = members.map { it.id }.toSet()
        val parent = members.associate { it.id to it.id }.toMutableMap()
        fun find(x: Long): Long = if (parent[x] == x) x else find(parent[x]!!).also { parent[x] = it }
        members.forEach { m -> m.household.withPersons.filter { it in ids }.forEach { o -> parent[find(m.id)] = find(o) } }
        return members.groupBy { find(it.id) }.values.filter { it.size > 1 }
    }

    /** Aufwand eines Termins in Minuten: Hin- und Rückweg plus etwa eine Stunde vor Ort. */
    fun effort(travel: Int) = travel * 2 + 60

    fun hints(
        members: List<Member>,
        appointments: List<Appt>,
        now: Long,
        zone: ZoneId,
        travelFor: (Appt) -> Int = { 30 },
        horizonDays: Int = 21,
    ): List<Hint> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val future = appointments.filter { it.whenMillis >= now && it.whenMillis < now + horizonDays * 86_400_000L }.sortedBy { it.whenMillis }
        fun date(a: Appt) = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate()
        fun hm(a: Appt) = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalTime().let { "%d:%02d".format(it.hour, it.minute) }
        fun dayWord(d: LocalDate) = when (d.toEpochDay() - today.toEpochDay()) {
            0L -> "heute"
            1L -> "morgen"
            else -> listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")[d.dayOfWeek.value - 1] + ", " +
                "%d.%d.".format(d.dayOfMonth, d.monthValue)
        }
        val byId = members.associateBy { it.id }
        val out = mutableListOf<Hint>()

        groups(members).forEach { group ->
            val ids = group.map { it.id }.toSet()
            val list = future.filter { it.personId in ids }
            // 1. Gleichzeitig: zwei Personen innerhalb von 90 Minuten
            list.zipWithNext().forEach { (a, b) ->
                if (a.personId != b.personId && date(a) == date(b) && b.whenMillis - a.whenMillis < 90 * 60_000L) {
                    val na = byId[a.personId]!!.name
                    val nb = byId[b.personId]!!.name
                    out += Hint(
                        "haushalt_gleichzeitig:${a.id}:${b.id}",
                        "${dayWord(date(a)).replaceFirstChar { it.uppercase() }} fast gleichzeitig: $na ${hm(a)} ${a.title} und $nb ${hm(b)} ${b.title}. " +
                            "Beides zu begleiten wird knapp – Begleitung organisieren oder einen Termin verschieben?",
                        a.personId,
                        "Begleitung klären: $na ${hm(a)} ${a.title} / $nb ${hm(b)} ${b.title}",
                        due = today.toEpochDay(),
                    )
                }
            }
            // 2. Gleicher Arzt für zwei Personen an verschiedenen Tagen (innerhalb 14 Tagen)
            list.filter { it.doctorId != null }.groupBy { it.doctorId }.forEach { (doc, l) ->
                val firstPerPerson = l.distinctBy { it.personId }
                if (firstPerPerson.size >= 2) {
                    val (a, b) = firstPerPerson.take(2)
                    if (date(a) != date(b) && kotlin.math.abs(b.whenMillis - a.whenMillis) < 14 * 86_400_000L) {
                        val who = a.doctorName.ifBlank { "derselben Praxis" }
                        out += Hint(
                            "haushalt_zusammen:$doc:${a.id}:${b.id}",
                            "${byId[a.personId]!!.name} ist ${dayWord(date(a))} bei $who, ${byId[b.personId]!!.name} ${dayWord(date(b))}. " +
                                "Termine auf einen Tag legen spart einen Weg – in der Praxis fragen?",
                            a.personId,
                            "Bei $who fragen: Termine für ${byId[a.personId]!!.name} und ${byId[b.personId]!!.name} auf einen Tag legen",
                            doctorId = doc,
                            due = today.toEpochDay() + 1,
                        )
                    }
                }
            }
            // 3. Volle Tage: ab drei Terminen oder etwa 4 Stunden Aufwand im Haushalt
            list.groupBy { date(it) }.forEach { (d, l) ->
                val minutes = l.sumOf { effort(travelFor(it)) }
                if (l.map { it.personId }.toSet().size >= 2 && (l.size >= 3 || minutes >= 240)) {
                    out += Hint(
                        "haushalt_voll:$d",
                        "${dayWord(d).replaceFirstChar { it.uppercase() }} wird voll: ${l.size} Termine für ${l.map { it.personId }.toSet().size} Personen, " +
                            "mit Wegen etwa ${hoursText(minutes)}. Plane Pausen ein oder verschiebe etwas.",
                        l.first().personId,
                    )
                }
            }
        }

        // 4. Termin in der Werkstatt-, Schul- oder Tagespflegezeit
        future.forEach { a ->
            val m = byId[a.personId] ?: return@forEach
            val h = m.household
            if (!h.hasDaytime) return@forEach
            val d = date(a)
            val t = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalTime()
            val from = runCatching { java.time.LocalTime.parse(h.from) }.getOrNull() ?: return@forEach
            val to = runCatching { java.time.LocalTime.parse(h.to) }.getOrNull() ?: return@forEach
            if (d.dayOfWeek.value in h.days && !t.isBefore(from) && t.isBefore(to)) {
                val where = h.place.ifBlank { Household.DAYTIME[h.daytime] ?: "Einrichtung" }
                out += Hint(
                    "tagesstruktur:${a.id}",
                    "${m.name} hat ${dayWord(d)} um ${hm(a)} ${a.title} – das ist in der Zeit in $where." +
                        (if (h.transport) " Fahrdienst abbestellen und" else "") + " $where Bescheid geben?",
                    a.personId,
                    "$where über Termin informieren: ${m.name}, ${dayWord(d)} ${hm(a)} ${a.title}" + if (h.transport) " (Fahrdienst abbestellen)" else "",
                    due = (d.toEpochDay() - 2).coerceAtLeast(today.toEpochDay()),
                )
            }
        }

        // 5. Schwerbehindertenausweis läuft ab (in 4 Monaten oder schon abgelaufen)
        members.forEach { m ->
            val left = Disability.daysLeft(m.disability, today.toEpochDay()) ?: return@forEach
            if (left <= 120) {
                val until = LocalDate.ofEpochDay(m.disability.validUntil!!)
                out += Hint(
                    "ausweis:${m.id}:${m.disability.validUntil}",
                    "Der Schwerbehindertenausweis von ${m.name} " + (if (left < 0) "ist seit %02d/%d abgelaufen".format(until.monthValue, until.year)
                    else "läuft %02d/%d ab".format(until.monthValue, until.year)) +
                        ". Verlängerung beim Versorgungsamt beantragen (dauert oft einige Wochen)?",
                    m.id,
                    "Schwerbehindertenausweis für ${m.name} verlängern lassen (Versorgungsamt)",
                    due = today.toEpochDay() + 7,
                )
            }
        }
        return out.distinctBy { it.key }
    }

    fun hoursText(minutes: Int): String {
        val h = minutes / 60
        val half = minutes % 60 >= 30
        return when {
            h == 0 -> "$minutes Minuten"
            half -> "${h}½ Stunden"
            h == 1 -> "eine Stunde"
            else -> "$h Stunden"
        }
    }
}
