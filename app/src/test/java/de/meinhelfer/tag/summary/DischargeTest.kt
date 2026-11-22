package de.meinhelfer.tag.summary


import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Entlassbericht: Medikation bei Entlassung und Kontrollen auslesen (nur Vorschläge). */
class DischargeTest {
    val letter = """
Klinikum Musterstadt · Klinik für Innere Medizin
Vorläufiger Entlassungsbericht

Sehr geehrte Frau Kollegin,
wir berichten über Ihre Patientin, die sich vom 02.11.2026 bis 12.11.2026 in unserer stationären Behandlung befand.

Diagnosen:
Dekompensierte Herzinsuffizienz

Medikation bei Aufnahme:
Ramipril 2,5 mg 1-0-0
Torasemid 10 mg 1-0-0

Verlauf:
Am 05.11.2026 erfolgte eine Kontrollsonographie, die unauffällig war.

Medikation bei Entlassung:
Ramipril 5 mg Tabletten 1-0-1
Torasemid 20 mg 1-1-0
Bisoprolol 2,5 mg 1-0-0
Metformin 500 mg abgesetzt
Pantoprazol 40 mg 1-0-0-0

Procedere:
Wir empfehlen eine Laborkontrolle (Kalium, Kreatinin) beim Hausarzt in einer Woche.
Wiedervorstellung in unserer Ambulanz am 03.12.2026 um 10:30 Uhr.
Die Fäden können in 10 Tagen durch den Hausarzt entfernt werden.
Eine kardiologische Kontrolle mit Echokardiographie sollte in 3 Monaten erfolgen.

Mit freundlichen Grüßen
Dr. med. Beispiel
""".trimIndent()

    @Test
    fun readsDischargeMedsAndFollowUps() {
        val info = Discharge.read(letter, null, LocalDate.of(2026, 11, 22))
        println(info)
        assertEquals(LocalDate.of(2026, 11, 12).toEpochDay(), info.dischargeDay)
        assertTrue(info.fromSection)
        val names = info.plan.meds.map { it.name }
        println(names)
        assertEquals(listOf("Ramipril", "Torasemid", "Bisoprolol", "Pantoprazol"), names)
        assertEquals(listOf("1", "0", "1", ""), info.plan.meds[0].amounts)
        assertTrue(info.stopped.any { it.contains("Metformin") })
        info.followUps.forEach { println(it) }
        val lab = info.followUps.first { it.title.startsWith("Laborkontrolle") }
        assertEquals("Laborkontrolle (Blutwerte) – Hausarzt", lab.title)
        assertEquals(LocalDate.of(2026, 11, 19).toEpochDay(), lab.day)
        assertFalse(lab.exact)
        val amb = info.followUps.first { it.place == "Klinik-Ambulanz" }
        assertEquals(LocalDate.of(2026, 12, 3).toEpochDay(), amb.day)
        assertTrue(amb.exact)
        assertEquals("10:30", amb.time)
        assertTrue(info.followUps.any { it.title.startsWith("Fäden") && it.day == LocalDate.of(2026, 11, 22).toEpochDay() })
        assertTrue(info.followUps.any { it.title.startsWith("Ultraschall") && it.place == "Kardiologe" })
        // Rückblick auf die Kontrollsonographie im Aufenthalt ist kein Vorschlag
        assertTrue(info.followUps.none { it.sentence.contains("05.11.2026") })
        assertEquals(4, info.followUps.size)
    }

    @Test
    fun recognizesReport() {
        assertTrue(Discharge.looksLikeReport("Sonstiges", letter))
        assertTrue(Discharge.looksLikeReport("Krankenhaus / Entlassbericht", "x"))
        assertFalse(Discharge.looksLikeReport("Rezept / Verordnung", "Rezept für Ramipril"))
    }

    @Test
    fun sentencesKeepAbbreviations() {
        val s = Discharge.sentences("Vorstellung bei Dr. Müller am 03.12. empfohlen. Danach weiter.")
        assertEquals(2, s.size)
        assertTrue(s[0].contains("Dr. Müller"))
    }
}
