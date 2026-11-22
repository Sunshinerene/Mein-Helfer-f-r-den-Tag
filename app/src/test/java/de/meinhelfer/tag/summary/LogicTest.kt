package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.BudgetEntry
import de.meinhelfer.tag.data.CareLogEntry
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Duties
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Responsibility
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.letters.SignedCopies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Rechenlogik ohne Android: Budgets, Zustaendigkeiten, Messwerte, Pflegetagebuch, Zuordnung unterschriebener Schreiben. */
class LogicTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // ---------------------------------------------------------------- Budgets

    @Test
    fun reliefCarryOverIsUsedFirstUntilJune() {
        val entries = listOf(
            BudgetEntry(personId = 1, kind = "entlastung", day = day(2025, 10, 1), amount = 500.0),
            BudgetEntry(personId = 1, kind = "entlastung", day = day(2026, 3, 1), amount = 300.0),
            BudgetEntry(personId = 1, kind = "entlastung", day = day(2026, 8, 1), amount = 100.0),
        )
        val r = Budgets.relief(entries, 2026, LocalDate.of(2026, 11, 22), { 12 }) { 131.0 }
        // 2025: 1572 - 500 = 1072 Uebertrag; Maerz 2026 nimmt 300 aus dem Uebertrag, August 100 aus 2026
        assertEquals(1572.0, r.yearTotal, 0.001)
        assertEquals(1072.0, r.carryIn, 0.001)
        assertEquals(300.0, r.usedFromCarry, 0.001)
        assertEquals(100.0, r.usedCurrent, 0.001)
        assertEquals(772.0, r.carryLeft, 0.001)
        assertEquals(1472.0, r.left, 0.001)
        // November: 11 Monate angesammelt, Uebertrag ist verfallen
        assertEquals(11 * 131.0, r.accruedSoFar, 0.001)
        assertEquals(11 * 131.0 - 100.0, r.availableNow, 0.001)
    }

    @Test
    fun reliefRespectsStartMonth() {
        // Pflegegrad seit September: 4 Monate im Jahr
        val r = Budgets.relief(emptyList(), 2026, LocalDate.of(2026, 10, 15), { y -> if (y == 2026) 4 else 0 }) { 131.0 }
        assertEquals(4 * 131.0, r.yearTotal, 0.001)
        assertEquals(2 * 131.0, r.accruedSoFar, 0.001)
    }

    @Test
    fun yearlyIgnoresRejectedAndCountsDays() {
        val entries = listOf(
            BudgetEntry(personId = 1, kind = "verhinderung", day = day(2026, 5, 1), amount = 1000.0, days = 7),
            BudgetEntry(personId = 1, kind = "kurzzeit", day = day(2026, 7, 1), amount = 1500.0, days = 14),
            BudgetEntry(personId = 1, kind = "kurzzeit", day = day(2026, 9, 1), amount = 800.0, days = 5, status = "abgelehnt"),
            BudgetEntry(personId = 1, kind = "kurzzeit", day = day(2025, 9, 1), amount = 800.0, days = 5),
        )
        val y = Budgets.yearly(entries, 2026, 3539.0)
        assertEquals(2500.0, y.used, 0.001)
        assertEquals(1039.0, y.left, 0.001)
        assertEquals(7, y.daysRespite)
        assertEquals(14, y.daysShort)
    }

    // ---------------------------------------------------------------- Zustaendigkeiten

    @Test
    fun dutiesDefaultExplicitAndSubstitute() {
        val duties = listOf(
            Responsibility(personId = 1, role = Duties.REZEPT, contactId = 10),
            Responsibility(personId = 0, role = Duties.VERTRETUNG, contactId = 11, replacesId = 10, fromDay = day(2026, 12, 1), untilDay = day(2026, 12, 14)),
        )
        val med = Medication(name = "A", personId = 1)
        assertEquals(10L, Duties.resolve(duties, med.doctorId, 1, Duties.REZEPT, day(2026, 11, 22)).id)
        val during = Duties.resolve(duties, med.doctorId, 1, Duties.REZEPT, day(2026, 12, 5))
        assertEquals(11L, during.id)
        assertNotNull(during.substitute)
        // Eigene Angabe beim Medikament geht vor
        assertEquals(12L, Duties.resolve(duties, 12L, 1, Duties.REZEPT, day(2026, 12, 5)).id)
        // Andere Person ohne Standard
        assertNull(Duties.resolve(duties, null, 2, Duties.REZEPT, day(2026, 12, 5)).id)
        assertEquals(11L, Duties.effective(med, duties, day(2026, 12, 5)).doctorId)
    }

    // ---------------------------------------------------------------- Messwerte

    @Test
    fun vitalsParseFormatAndRange() {
        assertEquals(72.5, Vitals.parse("72,5")!!, 0.0001)
        assertNull(Vitals.parse("abc"))
        val bp = VitalEntry(personId = 1, atMillis = 0, day = 0, kind = "blutdruck", value = 135.0, value2 = 85.0, unit = "mmHg")
        assertEquals("135/85 mmHg", Vitals.valueText(bp))
        val w = VitalEntry(personId = 1, atMillis = 0, day = 0, kind = "gewicht", value = 72.46, unit = "kg")
        assertEquals("72,5 kg", Vitals.valueText(w))
        assertTrue(Vitals.outside(bp, 100.0 to 130.0))
        assertFalse(Vitals.outside(bp, null to 140.0))
        val drinks = listOf(
            VitalEntry(personId = 1, atMillis = 0, day = 5, kind = "trinken", value = 250.0),
            VitalEntry(personId = 1, atMillis = 0, day = 5, kind = "trinken", value = 200.0),
            VitalEntry(personId = 2, atMillis = 0, day = 5, kind = "trinken", value = 999.0),
            VitalEntry(personId = 1, atMillis = 0, day = 4, kind = "trinken", value = 999.0),
        )
        assertEquals(450, Vitals.drinkToday(drinks, 1, 5))
    }

    // ---------------------------------------------------------------- Pflegetagebuch

    @Test
    fun careStatsPerActivity() {
        val list = listOf(
            CareLogEntry(personId = 1, day = 1, atMillis = 0, module = 4, activity = "Duschen oder Baden", help = "teilweise", times = 1, minutes = 20),
            CareLogEntry(personId = 1, day = 2, atMillis = 0, module = 4, activity = "Duschen oder Baden", help = "komplett", times = 1, minutes = 30, night = true),
            CareLogEntry(personId = 1, day = 2, atMillis = 0, module = 1, activity = "Treppen steigen", help = "aufsicht", times = 4),
        )
        val stats = CareAreas.stats(list)
        assertEquals(1, stats.first().module)
        val shower = stats.first { it.activity == "Duschen oder Baden" }
        assertEquals(2, shower.days)
        assertEquals(1.0, shower.timesPerDay, 0.001)
        assertEquals(25, shower.minutesPerDay)
        assertEquals(1, shower.nights)
        assertEquals(6, CareAreas.ALL.size)
    }

    // ---------------------------------------------------------------- Unterschriebene Fassung

    @Test
    fun signedCopyMatchesDraft() {
        val draftText = "Antrag auf Leistungen der Pflegeversicherung (Feststellung eines Pflegegrades)\n\nVersicherte Person: Anita Kunig, geboren am 01.01.1940\n\n" +
            "Sehr geehrte Damen und Herren, hiermit beantrage ich Leistungen der Pflegeversicherung und die Feststellung eines Pflegegrades. " +
            "Hilfe im Alltag wird benötigt wegen: Mobilität, Körperpflege, Gedächtnis. Bitte senden Sie mir die erforderlichen Antragsformulare zu und veranlassen Sie die Begutachtung."
        val draft = Document(id = 7, title = "Antrag auf Pflegegrad (Entwurf, 2026-11-22)", fileName = "x", text = draftText)
        val other = Document(id = 8, title = "Widerspruch (Entwurf, 2026-11-20)", fileName = "y", text = "Widerspruch gegen Ihren Bescheid vom 01.10.2026 Aktenzeichen Begründung Unterlagen Gutachten Medicproof Eingang")
        val scanned = "Max Muster\nMusterstr. 1\n\nAOK Pflegekasse\n\n" + draftText.replace("Pflegegrades", "Pflegegrades ").replace("ö", "o") + "\n\nMit freundlichen Grüßen\nAnita Kunig"
        assertEquals(7L, SignedCopies.bestMatch(scanned, listOf(other, draft))?.id)
        assertEquals("Antrag auf Pflegegrad", SignedCopies.baseTitle(draft))
        assertNull(SignedCopies.bestMatch("Ganz anderer Text über Rezepte und Apotheken in der Stadt, nichts mit Anträgen zu tun, wirklich gar nichts.", listOf(draft)))
        val signed = draft.copy(notes = SignedCopies.SIGNED_NOTE + " am 22.11.2026.")
        assertNull(SignedCopies.bestMatch(scanned, listOf(signed)))
    }

    // ---------------------------------------------------------------- Medikationsplan-Schema

    @Test
    fun schemaOfDosage() {
        val (amounts, rest) = MedPlans.schemaOf("47,5 mg · 1-0-1-0 Stk.")!!
        assertEquals(listOf("1", "0", "1", "0"), amounts)
        assertTrue(rest.contains("47,5 mg"))
        assertNull(MedPlans.schemaOf("bei Bedarf"))
    }
}
