package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechVariantsTest {
    @Test
    fun parsesJsonWithVariants() {
        val a = "```json\n{\"kurz\": \"Heute ist alles im Lot.\", \"lang\": \"Alle Einnahmen sind erledigt. Morgen steht der Zahnarzt an.\", " +
            "\"varianten\": [\"Alles erledigt für heute.\", \"Sag \\\"danke\\\" zu dir selbst.\"]}\n```"
        val p = SpeechVariants.parse(a)
        assertEquals("Heute ist alles im Lot.", p.short)
        assertEquals("Alle Einnahmen sind erledigt. Morgen steht der Zahnarzt an.", p.long)
        assertEquals(listOf("Alles erledigt für heute.", "Sag \"danke\" zu dir selbst."), p.variants)
        assertEquals("Heute ist alles im Lot.", SpeechVariants.pick(p, 0, false))
        assertEquals("Alles erledigt für heute.", SpeechVariants.pick(p, 1, false))
        assertEquals("Heute ist alles im Lot.", SpeechVariants.pick(p, 3, false))
        assertEquals(p.long, SpeechVariants.pick(p, 5, true))
    }

    @Test
    fun plainTextFallback() {
        val p = SpeechVariants.parse("- Trink heute noch etwas mehr. Es ist warm draußen.")
        assertEquals("Trink heute noch etwas mehr.", p.short)
        assertEquals("Trink heute noch etwas mehr. Es ist warm draußen.", p.long)
    }
}
