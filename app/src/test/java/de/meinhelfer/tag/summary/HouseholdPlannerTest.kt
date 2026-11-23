package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HouseholdPlannerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(23, 9)

    private val mama = HouseholdPlanner.Member(1, "Mama", Household(withPersons = setOf(2)))
    private val papa = HouseholdPlanner.Member(2, "Papa", Household())
    private val sohn = HouseholdPlanner.Member(3, "Tom", Household(daytime = "werkstatt", place = "Werkstatt", days = setOf(1, 2, 3, 4, 5), from = "08:00", to = "15:30", transport = true))

    @Test
    fun householdConflictsAndSameDoctor() {
        val appts = listOf(
            HouseholdPlanner.Appt(10, 1, "Zahnarzt", at(26, 10), doctorId = 7, doctorName = "Dr. Weber"),
            HouseholdPlanner.Appt(11, 2, "Hausarzt", at(26, 10, 45)),
            HouseholdPlanner.Appt(12, 2, "Zahnarzt", at(30, 9), doctorId = 7, doctorName = "Dr. Weber"),
            HouseholdPlanner.Appt(13, 1, "Physio", at(26, 15)),
        )
        val h = HouseholdPlanner.hints(listOf(mama, papa), appts, now, zone)
        val same = h.first { it.key.startsWith("haushalt_gleichzeitig") }
        assertTrue(same.text, same.text.startsWith("Donnerstag, 26.11. fast gleichzeitig: Mama 10:00 Zahnarzt und Papa 10:45 Hausarzt."))
        val together = h.first { it.key.startsWith("haushalt_zusammen") }
        assertEquals(7L, together.doctorId)
        val full = h.first { it.key.startsWith("haushalt_voll") }
        assertTrue(full.text, full.text.contains("3 Termine für 2 Personen, mit Wegen etwa 6 Stunden"))
    }

    @Test
    fun workshopTimeAndCardExpiry() {
        val appts = listOf(HouseholdPlanner.Appt(20, 3, "Neurologe", at(24, 11)), HouseholdPlanner.Appt(21, 3, "Friseur", at(24, 16)))
        val card = Disability(gdb = 80, marks = setOf("G", "B"), validUntil = LocalDate.of(2027, 1, 31).toEpochDay())
        val h = HouseholdPlanner.hints(listOf(sohn.copy(disability = card)), appts, now, zone)
        val w = h.single { it.key.startsWith("tagesstruktur") }
        assertTrue(w.text, w.text.contains("morgen um 11:00 Neurologe – das ist in der Zeit in Werkstatt. Fahrdienst abbestellen"))
        assertNotNull(h.single { it.key.startsWith("ausweis") }.task)
    }

    @Test
    fun cardTextAndCodecs() {
        val d = Disability.fromText("Schwerbehindertenausweis\nGrad der Behinderung 80\nMerkzeichen G B\ngültig bis 11/2028")!!
        assertEquals(80, d.gdb)
        assertEquals(setOf("G", "B"), d.marks)
        assertEquals(LocalDate.of(2028, 11, 30).toEpochDay(), d.validUntil)
        assertEquals(d, Disability.decode(d.encode()))
        assertEquals("GdB 80 · Merkzeichen G, B · gültig bis 11/2028", d.short())
        assertEquals(2120, Disability.taxAllowance(d))
        val h = sohn.household.copy(withPersons = setOf(1, 2), caregiverHere = true, age65plus = 2)
        assertEquals(h, Household.decode(h.encode()))
        assertTrue(h.short { if (it == 1L) "Mama" else null }.contains("Werkstatt für behinderte Menschen (Werkstatt) Mo–Fr 08:00–15:30, mit Fahrdienst"))
    }
}
