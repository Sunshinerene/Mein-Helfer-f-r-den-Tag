package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteuerwerkTest {
    @Test fun category() {
        assertEquals("bestellen", Steuerwerk.category("bestellen:1-2:300"))
        assertEquals("x", Steuerwerk.category("x"))
    }

    @Test fun pausesAfterThreeDeclines() {
        var s = AutoStat()
        s = Steuerwerk.declined(s, 1000)
        s = Steuerwerk.declined(s, 1000)
        assertFalse(Steuerwerk.paused(s, 1000))
        val before = s
        s = Steuerwerk.declined(s, 1000)
        assertTrue(Steuerwerk.justPaused(before, s))
        assertTrue(Steuerwerk.paused(s, 2000))
        assertFalse(Steuerwerk.paused(s, 1000 + Steuerwerk.PAUSE_MILLIS + 1))
    }

    @Test fun noPauseIfEverAccepted() {
        var s = Steuerwerk.accepted(AutoStat())
        repeat(5) { s = Steuerwerk.declined(s, 0) }
        assertFalse(Steuerwerk.paused(s, 1))
    }

    @Test fun offerUpgrade() {
        var s = AutoStat()
        repeat(3) { s = Steuerwerk.accepted(s) }
        assertTrue(Steuerwerk.offerUpgrade(AutoArea.Updates, AutoLevel.Ask, s))
        assertFalse(Steuerwerk.offerUpgrade(AutoArea.Updates, AutoLevel.Report, s))
        assertFalse(Steuerwerk.offerUpgrade(AutoArea.Hints, AutoLevel.Ask, s))
    }

    @Test fun statRoundTrip() {
        val s = AutoStat(2, 1, 12345)
        assertEquals(s, AutoStat.decode(s.encode()))
        assertEquals(AutoStat(), AutoStat.decode(null))
    }

    @Test fun journalKeepsSevenDays() {
        val day = 24L * 3600 * 1000
        val old = JournalEntry(0, "a", "alt")
        val list = JournalEntry.append(listOf(old), JournalEntry(8 * day, "b", "neu|x"))
        assertEquals(1, list.size)
        val back = JournalEntry.decodeAll(list.joinToString("\n") { it.encode() })
        assertEquals("neu/x", back.single().text)
    }
}
