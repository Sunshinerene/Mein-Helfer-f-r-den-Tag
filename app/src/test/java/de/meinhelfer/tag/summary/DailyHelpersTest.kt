package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DailyHelpersTest {
    private val werkstatt = Household(daytime = "werkstatt", days = setOf(1, 2, 3, 4, 5), from = "08:00", to = "15:30")
    private val people = listOf(
        Presence.Who(1, "Mama", Household(), Nutrition(diabetes = "2", flags = setOf("weich")), 82),
        Presence.Who(2, "Tom", werkstatt, Nutrition(allergies = "Nüsse")),
        Presence.Who(3, "Lea", werkstatt, Nutrition()),
    )

    @Test
    fun presenceAtNoonOnWorkday() {
        val monNoon = LocalDateTime.of(2026, 11, 23, 12, 30)
        assertEquals(listOf(1L), Presence.homeAt(people, monNoon).map { it.id })
        assertEquals(3, Presence.homeAt(people, LocalDateTime.of(2026, 11, 28, 12, 30)).size)
        // Termin um 12 Uhr: Mama ist dann unterwegs
        assertTrue(Presence.homeAt(people, monNoon, mapOf(1L to listOf(LocalDateTime.of(2026, 11, 23, 12, 0)))).isEmpty())
    }

    @Test
    fun promptHasNoNamesAndOnlyPresentNeeds() {
        val home = Presence.homeAt(people, LocalDateTime.of(2026, 11, 23, 12, 30))
        val p = HelperPrompt.build(HelperTool.COOK, mapOf("mahlzeit" to "Mittagessen", "da" to "Kartoffeln, Quark", "foto" to "1"), people, home, "heute 12:30 Uhr")
        assertFalse(p.contains("Mama"))
        assertTrue(p, p.contains("Zu Hause zu dieser Zeit: Person A (2 weitere sind unterwegs)"))
        assertTrue(p, p.contains("Person A: etwa 82 Jahre; Diabetes Typ 2; weiche Kost (Kauen schwierig)"))
        assertEquals("deine Mutter", Household.article("Mutter"))
        assertEquals("dein Opa", Household.article("Opa"))
        assertFalse(p.contains("Nüsse"))
        assertTrue(p.contains("Foto ist angehängt"))
        assertEquals(Nutrition(diabetes = "2", flags = setOf("weich"), allergies = "Nüsse; Kiwi"), Nutrition.decode(Nutrition(diabetes = "2", flags = setOf("weich"), allergies = "Nüsse; Kiwi").encode()))
    }

    @Test
    fun parseAnswer() {
        val r = HelperPrompt.parse("{\"kurz\": \"Heute passt eine Kartoffelsuppe.\", \"punkte\": [{\"titel\": \"Kartoffelsuppe\", \"text\": \"weich, sättigend\"}, {\"titel\": \"Quarkkeulchen\"}], \"hinweis\": \"Bei Diabetes kleine Portion.\"}")
        assertEquals("Heute passt eine Kartoffelsuppe.", r.short)
        assertEquals(listOf(HelperPrompt.Item("Kartoffelsuppe", "weich, sättigend"), HelperPrompt.Item("Quarkkeulchen")), r.items)
        val shop = HelperPrompt.parse("{\"kurz\": \"x\", \"punkte\": [{\"titel\": \"Milch 1 l\", \"art\": \"allgemein\"}, {\"titel\": \"Inkontinenzvorlagen\", \"text\": \"fast leer\", \"art\": \"apotheke\"}]}")
        assertEquals(listOf(false, true), shop.items.map { it.pharmacy })
        assertEquals("Bei Diabetes kleine Portion.", r.note)
        assertEquals(2, HelperPrompt.parse("Milch\n- Brot\n- Äpfel").items.size)
    }
}
