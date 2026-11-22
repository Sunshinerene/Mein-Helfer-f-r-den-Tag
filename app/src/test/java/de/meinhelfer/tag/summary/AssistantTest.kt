package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Tageslage und Vorschläge des Assistenten. */
class AssistantTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 23)
    private val d = day.toEpochDay()
    private val persons = listOf(Person(id = 1, name = "Mama"), Person(id = 2, name = "Papa"))
    private fun at(h: Int, m: Int = 0, plus: Long = 0) = day.plusDays(plus).atTime(h, m).toInstant(zone).toEpochMilli()

    @Test
    fun dayLineAcrossPersonsInOrder() {
        val meds = listOf(
            Medication(id = 1, name = "Ramipril", times = "08:00,20:00", createdDay = d - 5, personId = 1),
            Medication(id = 2, name = "Insulin", times = "12:00", createdDay = d - 5, personId = 2),
        )
        val taken = listOf(Checkin(kind = Checkin.MED, refId = 1, day = d, slot = "08:00", done = true))
        val appts = listOf(
            Appointment(id = 5, title = "Kardiologie", whenMillis = at(14, 30), personId = 1),
            Appointment(id = 6, title = "Fußpflege", whenMillis = at(10, 0, plus = 1), personId = 2),
        )
        val todos = listOf(
            Todo(id = 7, personId = 1, text = "Antrag abschicken", dueDay = d - 1),
            Todo(id = 8, personId = 2, text = "Später", dueDay = d + 4),
        )
        val now = LocalDateTime.of(2026, 11, 23, 11, 0)
        val line = Tageslage.dayLine(persons, meds, taken, appts, todos, now, zone)
        assertEquals(
            listOf("Medikamente 12:00: Insulin", "Termin 14:30: Kardiologie", "Medikamente 20:00: Ramipril", "Antrag abschicken"),
            line.map { it.title },
        )
        assertEquals("Papa", line[0].personName)
        assertTrue(line.last().overdue)
        assertEquals("Als Nächstes: Medikamente 12:00: Insulin (Papa). Danach noch 2 Punkte heute.", Assistant.headline(line.filterNot { it.overdue }, true))
        // Abgesagter Termin fällt weg
        val cancelled = taken + Checkin(kind = Checkin.APPT, refId = 5, day = d, status = Checkin.CANCELLED)
        assertTrue(Tageslage.dayLine(persons, meds, cancelled, appts, todos, now, zone).none { it.refId == 5L && it.kind == NextKind.Appointment })
    }

    @Test
    fun proposalsForDeadlinesOverdueAndStock() {
        val todos = listOf(
            Todo(id = 1, personId = 1, text = "Widerspruch einlegen", dueDay = d + 1, priority = 1),
            Todo(id = 2, personId = 1, text = "Rollator bestellen", dueDay = d - 4, priority = 1),
            Todo(id = 3, personId = 1, text = "Schon dringend", dueDay = d, priority = 2),
        )
        val meds = listOf(
            Medication(id = 1, name = "Ramipril", personId = 1, stock = 6, stockKnown = true, dailyUse = 2, stockDay = d, doctorId = 9),
            Medication(id = 2, name = "Torasemid", personId = 1, stock = 100, stockKnown = true, dailyUse = 1, stockDay = d),
        )
        val doctors = listOf(Doctor(id = 9, name = "Dr. Müller"))
        val list = Assistant.proposals(persons.take(1), todos, meds, emptyList(), doctors, d, emptySet())
        assertEquals(3, list.size)
        assertTrue(list[0].action is ProposalAction.RaisePriority)
        assertTrue(list[0].text.contains("morgen fällig"))
        assertEquals(ProposalAction.Postpone(2, d + 3), list[1].action)
        val reorder = list[2].action as ProposalAction.ReorderTask
        assertEquals("Rezept für Ramipril bei Dr. Müller bestellen", reorder.text)
        assertEquals(9L, reorder.doctorId)
        assertEquals(d, reorder.dueDay) // reicht nur 3 Tage: heute erledigen
        // „Nein“ blendet aus; eine offene Aufgabe zum Medikament verhindert den Vorschlag
        assertEquals(2, Assistant.proposals(persons.take(1), todos, meds, emptyList(), doctors, d, setOf(list[0].key)).size)
        val withTask = todos + Todo(id = 4, personId = 1, text = "Ramipril nachbestellen")
        assertTrue(Assistant.proposals(persons.take(1), withTask, meds, emptyList(), doctors, d, emptySet()).none { it.action is ProposalAction.ReorderTask })
    }

    @Test
    fun appointmentPrepAndExpression() {
        val appts = listOf(Appointment(id = 1, title = "Hausarzt", whenMillis = at(9, 0, plus = 1), personId = 1, docIds = "3,4", doctorId = 9))
        val meds = listOf(Medication(id = 1, name = "Ramipril", personId = 1))
        val doctors = listOf(Doctor(id = 9, name = "Dr. Müller", floor = "2. Stock", elevator = 2))
        val now = LocalDateTime.of(2026, 11, 23, 18, 0)
        val preps = Assistant.preps(persons, appts, emptyList(), meds, doctors, mapOf(1L to true), now, zone)
        assertEquals(1, preps.size)
        assertEquals("Morgen 09:00", preps[0].whenText)
        val hints = preps[0].hints.joinToString(" | ")
        assertTrue(hints.contains("Versichertenkarte"))
        assertTrue(hints.contains("Medikamentenplan"))
        assertTrue(hints.contains("2 Unterlagen"))
        assertTrue(hints.contains("2. Stock, kein Aufzug"))
        assertEquals(Expression.Concerned, Assistant.expression(emptyList(), SupportLevel.Relief, 0, now))
        assertEquals(Expression.Happy, Assistant.expression(emptyList(), SupportLevel.None, 0, now))
        assertEquals(Expression.Calm, Assistant.expression(listOf(NextUp(NextKind.Task, null, "x", 1, "", false)), SupportLevel.None, 0, LocalDateTime.of(2026, 11, 23, 22, 0)))
    }
}
