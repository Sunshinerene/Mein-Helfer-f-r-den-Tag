package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Benachrichtigungen des Assistenten, Ruhezeit und Hinweise zu eigenen Werten. */
class AssistantBriefTest {
    @Test
    fun quietHoursOverMidnight() {
        assertTrue(AssistantBrief.inQuiet(23 * 60, 22 * 60, 7 * 60))
        assertTrue(AssistantBrief.inQuiet(6 * 60 + 59, 22 * 60, 7 * 60))
        assertFalse(AssistantBrief.inQuiet(7 * 60, 22 * 60, 7 * 60))
        assertFalse(AssistantBrief.inQuiet(12 * 60, 22 * 60, 7 * 60))
        assertTrue(AssistantBrief.inQuiet(13 * 60, 12 * 60, 14 * 60))
        assertFalse(AssistantBrief.inQuiet(13 * 60, 12 * 60, 12 * 60))
    }

    private val prep = AppointmentPrep(
        Appointment(id = 1, title = "Hausarzt", whenMillis = 0, personId = 1),
        "Mama",
        "Morgen 09:00",
        listOf("Versichertenkarte und ggf. Überweisung einstecken", "Medikamentenplan mitnehmen (Medikamente → Plan als PDF)", "2 Unterlagen sind am Termin hinterlegt", "Fragen notieren"),
    )

    @Test
    fun morningAndEvening() {
        val line = listOf(
            NextUp(NextKind.Intake, 1L, "Medikamente 08:00: Ramipril", 1, "Mama", false),
            NextUp(NextKind.Task, null, "Antrag abschicken", 1, "Mama", true),
        )
        val (title, text) = AssistantBrief.morning("Sunny", line, emptyList(), 2, false)
        assertEquals("Sunny: Guten Morgen", title)
        assertEquals("Heute: 1 Einnahmezeit, 1 fällige Aufgabe. Als Nächstes: Medikamente 08:00: Ramipril. Ich habe 2 Vorschläge für dich.", text)
        val (_, empty) = AssistantBrief.morning("Sunny", emptyList(), emptyList(), 0, false)
        assertTrue(empty.startsWith("Heute steht laut App nichts Festes an."))

        val (et, ev) = AssistantBrief.evening("Sunny", 5, line.drop(1), listOf(prep), listOf("Mama"), false)
        assertEquals("Sunny: Dein Abend", et)
        assertEquals(
            "Heute geschafft: 5 Punkte. Danke dir! Noch offen: Antrag abschicken. " +
                "Morgen 09:00 Hausarzt: schon heute bereitlegen – Versichertenkarte und ggf. Überweisung einstecken; Medikamentenplan mitnehmen; 2 Unterlagen sind am Termin hinterlegt. " +
                "Das Pflegetagebuch für heute fehlt noch. Gute Nacht!",
            ev,
        )
        assertEquals("\nDenk an: Versichertenkarte und ggf. Überweisung einstecken; Medikamentenplan mitnehmen; 2 Unterlagen sind am Termin hinterlegt.", AssistantBrief.appointmentAddon(prep))
    }

    @Test
    fun ownHealthHints() {
        val short = AssistantBrief.selfHints(OwnHealth(sleepMinutes = 310), 9)
        assertTrue(short.single().startsWith("Du hast nur 5:10 Std. geschlafen."))
        assertTrue(AssistantBrief.selfHints(OwnHealth(stepsToday = 1200), 16).single().contains("1.200 Schritte"))
        assertTrue(AssistantBrief.selfHints(OwnHealth(stepsToday = 1200), 10).isEmpty())
        assertTrue(AssistantBrief.selfHints(OwnHealth(restingNow = 74, restingAvg = 62), 10).single().contains("74 statt etwa 62"))
        assertTrue(AssistantBrief.selfHints(OwnHealth(restingNow = 66, restingAvg = 62), 10).isEmpty())
        assertTrue(AssistantBrief.selfHints(null, 10).isEmpty())
    }

    @Test
    fun talkIsShortAndPersonal() {
        val line = listOf(
            NextUp(NextKind.Intake, 0L, "Medikamente 12:00: Ramipril", 1, "Mama", overdue = false),
            NextUp(NextKind.Task, null, "Rezept abholen", 1, "Mama", overdue = false),
        )
        val t = AssistantBrief.talk("Sunny", 9, line, emptyList(), 2, listOf("Trink selbst genug."), "Pflegegeld steigt" to "tagesschau.de", multi = false)
        assertEquals(
            "Guten Morgen, hier ist Sunny. Als Nächstes: Medikamente 12:00: Ramipril. Danach noch 1 Punkt heute. " +
                "Ich habe 2 Vorschläge für dich, schau gern in meine Karte. Trink selbst genug. Neu in den Nachrichten bei tagesschau.de: Pflegegeld steigt.",
            t,
        )
        assertEquals("Guten Abend, hier ist Sunny. Gerade steht laut App nichts an.", AssistantBrief.talk("Sunny", 21, emptyList(), emptyList(), 0, emptyList(), null, false))
    }
}
