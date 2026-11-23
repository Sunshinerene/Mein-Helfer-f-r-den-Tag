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

    @Test
    fun readsOfficialOverview() {
        val text = "Pflegeaufwendungen 721,00* 1.357,00* * Pflegebedürftige können (zusätzlich) den Entlastungsbetrag in Höhe von bis zu " +
            "131 Euro pro Monat unter bestimmten Voraussetzungen einsetzen. Der Gemeinsame\nJahresbetrag in Höhe von bis zu 3.539,00 Euro steht insgesamt zur Verfügung."
        val r = BenefitRates.fromOfficialText(text, 2027, 2026)!!
        assertEquals(131.0, r.monthly, 1e-9)
        assertEquals(3539.0, r.yearly, 1e-9)
        assertEquals("beschlossen", r.status)
        assertEquals("jahr;2027;131;3539;6;beschlossen;BMG, Übersicht Leistungsbeträge 2027", BenefitRates.line(r))
        assertEquals(null, BenefitRates.fromOfficialText("Kein Betrag hier", 2027, 2026))
        // Spätere Zeile gewinnt
        val merged = BenefitRates.parse("jahr;2027;131;3539;6;gilt;Repo\n" + BenefitRates.line(r.copy(monthly = 140.0)))
        assertEquals(140.0, BenefitRates.changes(merged, 2026) { Triple(131.0, 3539.0, 6) }.single().monthlyNew, 1e-9)
    }
}
