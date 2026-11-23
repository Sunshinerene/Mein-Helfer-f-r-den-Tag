package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Checkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Haltbarkeit nach dem Anbruch und tatsächlich gegebene Mengen. */
class MedCareTest {
    private val d = LocalDate.of(2026, 11, 23).toEpochDay()

    @Test
    fun shelfLifeAfterOpening() {
        val opened = LocalDate.of(2026, 11, 1).toEpochDay()
        assertEquals(LocalDate.of(2026, 11, 28).toEpochDay(), MedOpen.lastDay(opened, 28))
        assertEquals(5L, MedOpen.daysLeft(opened, 28, d))
        assertNull(MedOpen.lastDay(null, 28))
        assertNull(MedOpen.lastDay(opened, 0))
        assertEquals("Angebrochen 01.11., haltbar bis 28.11. (noch 5 Tage)", MedOpen.text(opened, 28, d))
        assertEquals("Angebrochen 01.11., haltbar bis 28.11. – seit 2 Tagen abgelaufen", MedOpen.text(opened, 28, d + 7))
        assertEquals("Angebrochen 01.11., haltbar bis 28.11. (heute letzter Tag)", MedOpen.text(opened, 28, d + 5))
    }

    @Test
    fun actualAmountsPerDay() {
        val checks = (1..6).flatMap { back ->
            listOf(
                Checkin(kind = "MED", refId = 7, day = d - back, slot = "08:00", done = true, amount = 10.0),
                Checkin(kind = "MED", refId = 7, day = d - back, slot = "18:00", done = true, amount = 8.0),
            )
        } + Checkin(kind = "MED", refId = 7, day = d, slot = "08:00", done = true, amount = 30.0)
        val (avg, days) = IntakeStats.actualDaily(checks, 7, d)!!
        assertEquals(18.0, avg, 1e-9)
        assertEquals(6, days)
        assertNull(IntakeStats.actualDaily(checks.take(4), 7, d))
        assertTrue(IntakeStats.differs(18.0, 14.0))
        assertFalse(IntakeStats.differs(14.2, 14.0))
    }
}
