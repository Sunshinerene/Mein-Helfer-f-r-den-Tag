package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VitalImportTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test
    fun sugarTableWithHeader() {
        val text = """
            Blutzucker-Tagebuch mg/dl
            Datum  nüchtern  mittags  abends
            01.11.  112  145  130
            02.11.  98  8 IE  160  121
            03.11.  105
        """.trimIndent()
        val r = VitalImport.parse(text, today = today)
        assertEquals(VitalKind.Blutzucker, r.kind)
        assertEquals(7, r.candidates.size)
        val first = r.candidates[0]
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), first.day)
        assertEquals(7 * 60, first.minute)
        assertEquals("nüchtern", first.context)
        assertEquals(18 * 60, r.candidates[2].minute)
        // „8 IE“ ist Insulin, kein Blutzucker
        assertTrue(r.candidates.none { it.value == 8.0 })
        assertTrue(VitalImport.worthOffering(r))
        assertEquals("7 Blutzucker-Werte vom 01.11.2026 bis 03.11.2026", VitalImport.summary(r))
    }

    @Test
    fun pressureWithTimesAndYearGuess() {
        val text = "RR Liste\n28.12. 08:15 135/85 72\n28.12. 19:30 142/90\n29.12. 8.00 Uhr 128/80"
        val r = VitalImport.parse(text, today = today)
        assertEquals(VitalKind.Blutdruck, r.kind)
        assertEquals(3, r.candidates.size)
        assertEquals(LocalDate.of(2025, 12, 28).toEpochDay(), r.candidates[0].day) // Datum ohne Jahr in der Zukunft → Vorjahr
        assertEquals(8 * 60 + 15, r.candidates[0].minute)
        assertEquals(85.0, r.candidates[0].value2)
        assertEquals(8 * 60, r.candidates[2].minute)
    }

    @Test
    fun nothingInLetters() {
        val r = VitalImport.parse("Sehr geehrte Frau Müller, am 12.03.2026 um 10:00 Uhr ist Ihr Termin.", today = today)
        assertNull(r.kind)
        assertTrue(!VitalImport.worthOffering(r))
    }

    @Test
    fun mmolAndForcedKind() {
        val r = VitalImport.parse("Werte in mmol/l\n05.11.2026 7,2 9,8", today = today)
        assertEquals("mmol/l", r.candidates[0].unit)
        assertEquals(7.2, r.candidates[0].value, 0.0)
        val t = VitalImport.parse("05.11. 37,8\n06.11. 38,4\n07.11. 37,1", VitalKind.Temperatur, today = today)
        assertEquals(3, t.candidates.size)
    }
}
