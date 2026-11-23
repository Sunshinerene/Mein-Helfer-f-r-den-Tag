package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Beträge der Pflegekasse aus der Datei im Repo und Vorschläge zur Anpassung. */
class BenefitRatesTest {
    private val sample = """
        # Kommentar
        stand;2026-11-23
        jahr;2026;131;3539;6;gilt;BMG 2026
        jahr;2027;0;0;0;beschlossen;PNOG
        jahr;2028;140;3700;6;geplant;Entwurf
        hinweis;2026-11-23;geplant;Reform geplant; Details folgen
    """.trimIndent()

    @Test
    fun parsesYearsAndNotes() {
        val r = BenefitRates.parse(sample)
        assertEquals("2026-11-23", r.stand)
        assertEquals(3, r.years.size)
        assertEquals(131.0, r.years[0].monthly, 1e-9)
        assertEquals(0, r.years[1].carryMonth)
        assertEquals("Reform geplant; Details folgen", r.notes.single().text)
    }

    @Test
    fun onlyValidOrDecidedChangesAreProposed() {
        val r = BenefitRates.parse(sample)
        val changes = BenefitRates.changes(r, 2026) { Triple(131.0, 3539.0, 6) }
        assertEquals(listOf(2027), changes.map { it.year })
        val text = BenefitRates.changeText(changes.single())
        assertTrue(text, text.contains("Entlastungsbetrag entfällt"))
        assertTrue(text, text.contains("verfällt am 31.12.2026"))
        // Schon übernommen: kein Vorschlag mehr
        assertTrue(BenefitRates.changes(r, 2026) { y -> if (y == 2027) Triple(0.0, 0.0, 0) else Triple(131.0, 3539.0, 6) }.isEmpty())
    }

    @Test
    fun repoFileIsReadable() {
        val f = listOf(File("../daten/leistungen.txt"), File("daten/leistungen.txt")).firstOrNull { it.exists() } ?: return
        val r = BenefitRates.parse(f.readText())
        assertTrue(r.years.any { it.year == 2026 && it.monthly == 131.0 && it.yearly == 3539.0 })
    }
}
