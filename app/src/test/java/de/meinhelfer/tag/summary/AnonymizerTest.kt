package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymizerTest {
    @Test
    fun removesPersonalData() {
        val text = "Patientin Erna Schmidt, geb. 14.03.1936, Hauptstraße 12, 74906 Bad Rappenau, Tel. 07264 123456, " +
            "erna@example.de, Versichertennr. A123456789. Hausarzt Dr. med. Klaus Weber. Ramipril 5 mg morgens, Blutzucker 145 mg/dl am 12.11.2026."
        val out = Anonymizer.scrub(text, listOf("Erna Schmidt", "Dr. med. Klaus Weber"))
        listOf("Erna", "Schmidt", "1936", "Hauptstraße", "74906", "Rappenau", "07264", "example", "A123456789", "Klaus", "Weber").forEach {
            assertFalse("$it noch drin: $out", out.contains(it))
        }
        assertTrue(out.contains("Ramipril 5 mg morgens"))
        assertTrue(out.contains("145 mg/dl am 12.11.2026"))
        assertTrue(out.contains("[Geburtsdatum]"))
    }

    @Test
    fun keepsDosesAndShortWords() {
        assertEquals("Metformin 1000 mg 1-0-1", Anonymizer.scrub("Metformin 1000 mg 1-0-1", listOf("Al Bo")))
        assertEquals(setOf("Klaus", "Weber"), Anonymizer.nameParts(listOf("Dr. Klaus Weber")))
    }
}
