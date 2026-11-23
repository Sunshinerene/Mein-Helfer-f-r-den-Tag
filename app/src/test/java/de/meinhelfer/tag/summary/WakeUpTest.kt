package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WakeUpTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val persons = listOf(Person(id = 1, name = "Mama"))
    private fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private val now = LocalDateTime.of(2026, 11, 23, 19, 0)
    private val early = Appointment(id = 5, title = "Zahnarzt", whenMillis = at(LocalDateTime.of(2026, 11, 24, 8, 0)), personId = 1)
    private val later = Appointment(id = 6, title = "Physio", whenMillis = at(LocalDateTime.of(2026, 11, 24, 9, 30)), personId = 1)
    private val noon = Appointment(id = 7, title = "Hausarzt", whenMillis = at(LocalDateTime.of(2026, 11, 24, 12, 0)), personId = 1)

    @Test
    fun earliestTomorrowWithLead() {
        val p = WakeUp.plans(persons, listOf(later, early, noon), emptyList(), now, zone)
        assertEquals(1, p.size)
        assertEquals(5L, p[0].appointment.id)
        assertEquals(6 * 60 + 30, p[0].alarmMinute)
        val prop = WakeUp.proposal(p[0], multi = false, label = "Sunny")
        assertEquals("Morgen früh um 8:00: Zahnarzt. Soll ich dir einen Wecker für 6:30 stellen?", prop.text)
        assertTrue(WakeUp.eveningLine(p).contains("6:30"))
    }

    @Test
    fun notBeforeOfferTimeAndNotWhenCancelled() {
        assertTrue(WakeUp.plans(persons, listOf(early), emptyList(), LocalDateTime.of(2026, 11, 23, 11, 0), zone).isEmpty())
        val cancelled = Checkin(kind = Checkin.APPT, refId = 5, day = early.let { java.time.LocalDate.of(2026, 11, 24).toEpochDay() }, status = Checkin.CANCELLED)
        assertTrue(WakeUp.plans(persons, listOf(early), listOf(cancelled), now, zone).isEmpty())
    }

    @Test
    fun todayOnlyWhileAlarmStillAhead() {
        val today = Appointment(id = 8, title = "Labor", whenMillis = at(LocalDateTime.of(2026, 11, 23, 8, 0)), personId = 1)
        assertEquals(1, WakeUp.plans(persons, listOf(today), emptyList(), LocalDateTime.of(2026, 11, 23, 3, 0), zone).size)
        assertTrue(WakeUp.plans(persons, listOf(today), emptyList(), LocalDateTime.of(2026, 11, 23, 7, 0), zone).isEmpty())
    }
}
