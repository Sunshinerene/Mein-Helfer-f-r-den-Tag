package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasebookTest {
    @Test
    fun pickRotatesAndFillsPlaceholders() {
        val b = Phrasebook.withDefaults()
        assertEquals("Guten Morgen, hier ist Sunny.", b.pick("gruss_morgen", "Guten Morgen, hier ist {name}.", 0, "Sunny"))
        assertEquals("Einen guten Start in den Tag, ich bin's, Sunny.", b.pick("gruss_morgen", "Guten Morgen, hier ist {name}.", 1, "Sunny"))
        assertEquals("In meiner Karte warten 3 Vorschläge auf dich.", b.pick("vorschlaege", "Ich habe {n} Vorschläge für dich, schau gern in meine Karte.", 1, n = 3))
        assertEquals("Fallback", b.pick("unbekannt", "Fallback", 5))
    }

    @Test
    fun mergeSkipsDuplicatesAndPersonalData() {
        val b = Phrasebook.withDefaults()
        val added = b.merge("lob", listOf("Das hast du gut gemacht!", "Klasse, wieder ein Punkt geschafft.", "Um 8 Uhr Ramipril", "[Name] hat gut getrunken"))
        assertEquals(1, added)
        val again = Phrasebook.decode(b.encode())
        assertTrue(again.themes()["lob"]!!.contains("Klasse, wieder ein Punkt geschafft."))
    }
}
