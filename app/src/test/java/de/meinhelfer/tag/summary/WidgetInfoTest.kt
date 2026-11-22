package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Inhalt des Startbildschirm-Widgets: nächste Einnahme und nächster Termin. */
class WidgetInfoTest {
    private val day = LocalDate.of(2026, 11, 22)
    private val d = day.toEpochDay()
    private val meds = listOf(
        Medication(id = 1, name = "Ramipril", times = "08:00,20:00", createdDay = d - 10),
        Medication(id = 2, name = "Torasemid", times = "08:00,12:00", createdDay = d - 10),
        Medication(id = 3, name = "Neu ab morgen", times = "12:00", createdDay = d + 1),
    )

    @Test
    fun openMorningIntakeStaysVisibleUntilGraceEnds() {
        val now = LocalDateTime.of(2026, 11, 22, 9, 0)
        val taken = listOf(Checkin(kind = Checkin.MED, refId = 1, day = d, slot = "08:00", done = true))
        val next = WidgetInfo.nextIntake(meds, taken, now)!!
        assertEquals("08:00", next.time)
        assertEquals(listOf("Torasemid"), next.names)
        assertTrue(next.overdue)
        assertEquals("Offen seit 08:00 · Torasemid", WidgetInfo.intakeLine(next, day, details = true))
        assertEquals("Offen seit 08:00 · 1 Medikament", WidgetInfo.intakeLine(next, day, details = false))
    }

    @Test
    fun handledSlotsAreSkippedAndTomorrowFollows() {
        val vergessen = listOf(
            Checkin(kind = Checkin.MED, refId = 1, day = d, slot = "08:00", done = true),
            Checkin(kind = Checkin.MED, refId = 2, day = d, slot = "08:00", status = Checkin.MISSED),
        )
        val noon = WidgetInfo.nextIntake(meds, vergessen, LocalDateTime.of(2026, 11, 22, 10, 0))!!
        assertEquals("12:00", noon.time)
        assertEquals(listOf("Torasemid"), noon.names) // „Neu ab morgen“ zählt heute noch nicht
        val late = WidgetInfo.nextIntake(meds, emptyList(), LocalDateTime.of(2026, 11, 22, 23, 30))!!
        assertEquals(d + 1, late.day)
        assertEquals("08:00", late.time)
        assertEquals("Morgen 08:00 · Ramipril, Torasemid", WidgetInfo.intakeLine(late, day, details = true))
        assertNull(WidgetInfo.nextIntake(emptyList(), emptyList(), LocalDateTime.of(2026, 11, 22, 10, 0)))
    }

    @Test
    fun nextAppointmentSkipsCancelled() {
        val zone = ZoneOffset.UTC
        fun at(h: Int, plusDays: Long = 0) = day.plusDays(plusDays).atTime(h, 0).toInstant(zone).toEpochMilli()
        val list = listOf(
            Appointment(id = 1, title = "Hausarzt", whenMillis = at(7)),
            Appointment(id = 2, title = "Kardiologie", whenMillis = at(14)),
            Appointment(id = 3, title = "Fußpflege", whenMillis = at(10, 1)),
        )
        val now = LocalDateTime.of(2026, 11, 22, 9, 0)
        val cancelled = listOf(Checkin(kind = Checkin.APPT, refId = 2, day = d, status = Checkin.CANCELLED))
        assertEquals(2L, WidgetInfo.nextAppointment(list, emptyList(), now, zone)?.id)
        val next = WidgetInfo.nextAppointment(list, cancelled, now, zone)
        assertEquals(3L, next?.id)
        assertEquals("Morgen 10:00 · Fußpflege", WidgetInfo.appointmentLine(next, now, details = true, zone = zone))
        assertEquals("Morgen 10:00", WidgetInfo.appointmentLine(next, now, details = false, zone = zone))
        assertEquals("Kein Termin geplant", WidgetInfo.appointmentLine(null, now, details = true, zone = zone))
    }
}
