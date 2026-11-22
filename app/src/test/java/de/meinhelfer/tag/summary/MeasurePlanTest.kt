package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.VitalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Messzeiten (z. B. Blutzucker 3× täglich), Zuordnung der Messungen und Zahlen für Bericht und Auswertung. */
class MeasurePlanTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 23)
    private val d = day.toEpochDay()
    private var nextId = 1L
    private fun at(h: Int, m: Int = 0, plus: Long = 0) = day.plusDays(plus).atTime(h, m).toInstant(zone).toEpochMilli()
    private fun sugar(h: Int, m: Int, v: Double, context: String = "", plus: Long = 0) =
        VitalEntry(id = nextId++, personId = 1, atMillis = at(h, m, plus), day = d + plus, kind = "blutzucker", value = v, unit = "mg/dl", context = context)

    private val plan = MeasurePlans.parse("blutzucker=07:30,12:00,18:00;blutdruck=08:00")

    @Test
    fun planReadAndWrite() {
        assertEquals(listOf(LocalTime.of(7, 30), LocalTime.of(12, 0), LocalTime.of(18, 0)), plan[VitalKind.Blutzucker])
        assertEquals("blutdruck=08:00;blutzucker=07:30,12:00,18:00", MeasurePlans.format(plan))
        assertTrue(MeasurePlans.parse("unsinn=08:00;trinken=10:00").isEmpty())
    }

    @Test
    fun measurementsMatchNearestTime() {
        val entries = listOf(sugar(7, 10, 110.0), sugar(12, 40, 160.0))
        val dues = MeasurePlans.forDay(1, plan, entries, d, zone)
        val sugarDues = dues.filter { it.kind == VitalKind.Blutzucker }
        assertEquals(3, sugarDues.size)
        assertTrue(sugarDues[0].done)
        assertTrue(sugarDues[1].done)
        assertFalse(sugarDues[2].done)
        // Blutdruck um 08:00 wurde nicht gemessen
        assertFalse(dues.first { it.kind == VitalKind.Blutdruck }.done)
        // Reihenfolge nach Uhrzeit
        assertEquals(LocalTime.of(7, 30), dues.first().time)
    }

    @Test
    fun oneMeasurementCountsOnce() {
        val close = MeasurePlans.parse("blutzucker=07:30,09:00")
        val dues = MeasurePlans.forDay(1, close, listOf(sugar(8, 50, 120.0)), d, zone)
        assertEquals(1, dues.count { it.done })
        assertTrue(dues.first { it.time == LocalTime.of(9, 0) }.done)
    }

    @Test
    fun reminderSkipsWhenAlreadyMeasured() {
        val entries = listOf(sugar(11, 45, 130.0))
        assertTrue(MeasurePlans.measured(entries, 1, VitalKind.Blutzucker, day.atTime(12, 0), zone))
        assertFalse(MeasurePlans.measured(entries, 1, VitalKind.Blutzucker, day.atTime(18, 0), zone))
        assertFalse(MeasurePlans.measured(entries, 2, VitalKind.Blutzucker, day.atTime(12, 0), zone))
    }

    @Test
    fun adherenceCountsOnlyPastTimesToday() {
        val entries = listOf(sugar(7, 30, 100.0, plus = -1), sugar(12, 0, 140.0, plus = -1), sugar(7, 40, 105.0))
        val sugarOnly = MeasurePlans.parse("blutzucker=07:30,12:00,18:00")
        val result = MeasurePlans.adherence(1, sugarOnly, entries, d - 1, d, day.atTime(13, 0), zone)
        // gestern 3 geplant, 2 gemessen; heute 07:30 gemessen, 12:00 läuft noch (bis 15 Uhr), 18:00 noch nicht dran
        assertEquals(4 to 3, result[VitalKind.Blutzucker])
    }

    @Test
    fun statsWithContexts() {
        val entries = listOf(sugar(7, 0, 100.0, "nüchtern"), sugar(7, 0, 120.0, "nüchtern", plus = 1), sugar(13, 0, 160.0, "nach dem Essen"))
        val s = VitalStats.of(VitalKind.Blutzucker, entries, null to 150.0)!!
        assertEquals(3, s.count)
        assertEquals(2, s.days)
        assertEquals(100.0, s.min, 1e-9)
        assertEquals(160.0, s.max, 1e-9)
        assertEquals(1, s.outside)
        assertEquals("3 Messung(en) an 2 Tag(en), 100 bis 160, im Schnitt 127 mg/dl", VitalStats.line(s))
        assertEquals("nüchtern 110, nach dem Essen 160 mg/dl (im Schnitt)", VitalStats.contextLine(s))
        assertNull(VitalStats.of(VitalKind.Temperatur, entries, null))
    }

    @Test
    fun bloodPressureStats() {
        val bp = listOf(
            VitalEntry(id = 90, personId = 1, atMillis = at(8), day = d, kind = "blutdruck", value = 130.0, value2 = 80.0),
            VitalEntry(id = 91, personId = 1, atMillis = at(8, plus = 1), day = d + 1, kind = "blutdruck", value = 150.0, value2 = 90.0),
        )
        val s = VitalStats.of(VitalKind.Blutdruck, bp, null)
        assertNotNull(s)
        assertEquals("2 Messung(en) an 2 Tag(en), 130–150 / 80–90, im Schnitt 140/85 mmHg", VitalStats.line(s!!))
    }
}
