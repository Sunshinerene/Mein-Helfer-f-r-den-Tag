package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.VitalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Tagesplan: Abschnitt „Trinken und Werte“ mit Kästchen und Zeilen zum Eintragen. */
class DayPlanTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 22)
    private val d = today.toEpochDay()
    private fun at(day: Long, h: Int) = LocalDate.ofEpochDay(day).atTime(h, 10).toInstant(zone).toEpochMilli()

    @Test
    fun drinkBoxesAndLastValues() {
        val vitals = listOf(
            VitalEntry(personId = 1, atMillis = at(d - 1, 9), day = d - 1, kind = "trinken", value = 600.0),
            VitalEntry(personId = 1, atMillis = at(d - 1, 15), day = d - 1, kind = "trinken", value = 350.0),
            VitalEntry(personId = 1, atMillis = at(d - 3, 8), day = d - 3, kind = "blutdruck", value = 140.0, value2 = 85.0),
            VitalEntry(personId = 1, atMillis = at(d - 1, 8), day = d - 1, kind = "blutdruck", value = 135.0, value2 = 80.0),
            VitalEntry(personId = 1, atMillis = at(d - 30, 8), day = d - 30, kind = "gewicht", value = 70.5),
        )
        val s = DayPlan.drinkAndValues(vitals, 1300, d, zone)!!
        assertEquals("Trinken und Werte", s.title)
        val drink = s.lines[0]
        assertEquals(7, drink.boxes) // 1300 ml = 7 Gläser à 200 ml
        assertEquals("Gestern getrunken: 950 ml", drink.sub)
        assertTrue(drink.text.startsWith("Trinkziel heute: 1.300 ml"))
        // Blutdruck mit letztem Wert, Gewicht ist älter als 14 Tage und fehlt
        assertEquals(2, s.lines.size)
        assertTrue(s.lines[1].text.startsWith("Blutdruck: ______ / ______ mmHg"))
        assertEquals("Zuletzt: 135/80 mmHg am 21.11. um 08:10 Uhr", s.lines[1].sub)
    }

    @Test
    fun goalZeroWithoutValuesGivesNoSection() {
        assertNull(DayPlan.drinkAndValues(emptyList(), 0, d, zone))
        assertEquals(1, DayPlan.drinkAndValues(emptyList(), 1500, d, zone)!!.lines.size)
    }

    @Test
    fun cancelledAppointmentsAreLeftOut() {
        val appts = listOf(
            de.meinhelfer.tag.data.Appointment(id = 1, title = "Hausarzt", whenMillis = at(d, 9), personId = 1),
            de.meinhelfer.tag.data.Appointment(id = 2, title = "Fußpflege", whenMillis = at(d, 14), personId = 1),
            de.meinhelfer.tag.data.Appointment(id = 3, title = "Zahnarzt", whenMillis = at(d + 1, 10), personId = 1),
        )
        val checkins = listOf(
            de.meinhelfer.tag.data.Checkin(kind = de.meinhelfer.tag.data.Checkin.APPT, refId = 1, day = d, status = de.meinhelfer.tag.data.Checkin.CANCELLED),
            de.meinhelfer.tag.data.Checkin(kind = de.meinhelfer.tag.data.Checkin.APPT, refId = 3, day = d + 1, status = de.meinhelfer.tag.data.Checkin.MOVED),
        )
        val all = DayPlan.build(null, emptyList(), appts, emptyList(), emptyList(), today, zone)
        val filtered = DayPlan.build(null, emptyList(), appts, emptyList(), emptyList(), today, zone, checkins = checkins)
        assertEquals(2, all.first { it.title == "Termine heute" }.lines.size)
        assertEquals(listOf("14:10 Uhr – Fußpflege"), filtered.first { it.title == "Termine heute" }.lines.map { it.text })
        assertTrue(filtered.none { it.title == "Morgen" })
    }
}
