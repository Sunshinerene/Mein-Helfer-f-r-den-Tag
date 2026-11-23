package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmallHelpersTest {
    @Test
    fun birthdaysParseAndUpcoming() {
        val list = Birthdays.parse("Oma Erna, 28.11.1936\nNachbarin Gabi 2.7.\nQuatsch\nFalsch 31.02.")
        assertEquals(2, list.size)
        assertEquals(1936, list[0].year)
        val today = LocalDate.of(2026, 11, 23)
        val up = Birthdays.upcoming(list, today)
        assertEquals(1, up.size)
        assertEquals(5L, up[0].daysUntil)
        assertEquals("In 5 Tagen hat Oma Erna Geburtstag (90).", Birthdays.sentence(up[0]))
        assertEquals(0L, Birthdays.next(list[0], LocalDate.of(2026, 11, 28)).daysUntil)
        assertEquals(LocalDate.of(2027, 2, 28), Birthdays.next(Birthdays.Entry("X", 2, 29), today).date)
        assertEquals("Oma Erna, 28.11.1936", Birthdays.format(list[0]))
    }

    @Test
    fun weatherHints() {
        assertEquals("", WeatherHint.text(15.0, 10, 1, "8:00"))
        assertEquals("Wetter um 8:00: 0 °C, Regen wahrscheinlich (70 %) – Schirm oder Regenjacke; Glätte möglich – vorsichtig, mehr Zeit einplanen; kalt – warme Jacke", WeatherHint.text(0.4, 70, 61, "8:00"))
        assertTrue(WeatherHint.text(-2.0, 80, 73, "9:30").contains("Schnee"))
    }

    @Test
    fun leave() {
        assertEquals(1_000_000L - 40 * 60_000L, Leave.at(1_000_000L, 30, 10))
        assertEquals("In 10 Minuten losfahren: Zahnarzt um 08:00 Uhr (ca. 30 Min. Weg), Hauptstr. 1.", Leave.text("Zahnarzt", "08:00", 30, 10, "Hauptstr. 1"))
    }
}
