package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vorschläge beim Sammel-Import: welche Dateien gehören zu einem Dokument? */
class DocGroupingTest {
    private fun pdf(name: String, text: String = "") = BatchFile(name, mergeable = true, text = text)
    private fun photo(name: String, time: Long? = null, text: String = "") = BatchFile(name, mergeable = true, isPhoto = true, time = time, text = text)

    @Test
    fun pageMarksInFileNames() {
        val a = DocGrouping.nameMark("Befund Seite 2.pdf")!!
        assertEquals("befund", a.stem)
        assertEquals(2, a.page)
        assertEquals(2, a.strength)
        assertEquals(3, DocGrouping.nameMark("Scan_p3.jpg")!!.page)
        val b = DocGrouping.nameMark("Arztbrief_1von2.pdf")!!
        assertEquals(1, b.page)
        assertEquals(2, b.total)
        assertEquals(1, DocGrouping.nameMark("Rechnung 2.pdf")!!.strength)
        assertEquals(0, DocGrouping.nameMark("Dokument (2).pdf")!!.strength)
        assertNull(DocGrouping.nameMark("Arztbrief_2023.pdf"))
        assertNull(DocGrouping.nameMark("PXL_20261122_101530123.jpg"))
    }

    @Test
    fun titleWithoutPageMark() {
        assertEquals("Befund", DocGrouping.baseTitle("Befund Seite 1.pdf"))
        assertEquals("Arztbrief", DocGrouping.baseTitle("Arztbrief_1von2.pdf"))
        assertEquals("Rechnung", DocGrouping.baseTitle("Rechnung.pdf"))
    }

    @Test
    fun pageMarksInText() {
        val t = DocGrouping.textPages("Kopf\nText\nSeite 1 von 3\nmehr\nSeite 2 von 3")!!
        assertEquals(1, t.first)
        assertEquals(2, t.last)
        assertEquals(3, t.total)
        assertEquals(2, DocGrouping.textPages("Weiter im Text\n- 2 -\n")!!.first)
        assertNull(DocGrouping.textPages("Ohne Seitenzahl"))
    }

    @Test
    fun naturalOrderOfNames() {
        val files = listOf(pdf("Befund Seite 10.pdf"), pdf("Befund Seite 2.pdf"), pdf("Befund Seite 1.pdf"))
        assertEquals(listOf(2, 1, 0), DocGrouping.order(files))
    }

    @Test
    fun pagesByNameBelongTogether() {
        val files = listOf(pdf("Rechnung.pdf"), pdf("Befund Seite 2.pdf"), pdf("Befund Seite 1.pdf"))
        val plan = DocGrouping.suggest(files)
        assertEquals(listOf(listOf(2, 1), listOf(0)), plan.groups)
        assertTrue(plan.hasSuggestion)
        assertNotNull(plan.reasons[1])
    }

    @Test
    fun pagesByTextBelongTogether() {
        val files = listOf(
            pdf("scan_a.pdf", "Arztbrief\nSehr geehrte Frau Doktor\nSeite 1 von 2"),
            pdf("scan_b.pdf", "Weiterer Verlauf\nMit freundlichen Grüßen\nSeite 2 von 2"),
            pdf("scan_c.pdf", "Rechnung\nSeite 1 von 1"),
        )
        assertEquals(listOf(listOf(0, 1), listOf(2)), DocGrouping.suggest(files).groups)
    }

    @Test
    fun completeDocumentsStaySeparate() {
        val files = listOf(
            pdf("Befund 1.pdf", "Seite 1 von 1"),
            pdf("Befund 2.pdf", "Seite 1 von 1"),
        )
        val plan = DocGrouping.suggest(files)
        assertFalse(plan.hasSuggestion)
        assertEquals(2, plan.groups.size)
    }

    @Test
    fun photosTakenShortlyAfterEachOther() {
        val files = listOf(
            photo("PXL_20261122_101530123.jpg"),
            photo("PXL_20261122_101545456.jpg"),
            photo("PXL_20261122_113000000.jpg"),
        )
        assertEquals(listOf(listOf(0, 1), listOf(2)), DocGrouping.suggest(files).groups)
    }

    @Test
    fun newLetterAfterClosingIsSeparate() {
        val files = listOf(
            photo("a.jpg", time = 1_000_000, text = "Bescheid ... Mit freundlichen Grüßen"),
            photo("b.jpg", time = 1_020_000, text = "Sehr geehrte Frau Muster, anbei ..."),
        )
        assertFalse(DocGrouping.suggest(files).hasSuggestion)
    }

    @Test
    fun otherFilesAreNeverJoined() {
        val files = listOf(
            BatchFile("Notiz Seite 1.txt", mergeable = false),
            BatchFile("Notiz Seite 2.txt", mergeable = false),
        )
        assertFalse(DocGrouping.suggest(files).hasSuggestion)
    }

    @Test
    fun copiesAreNotPages() {
        val files = listOf(pdf("Befund (1).pdf"), pdf("Befund (2).pdf"))
        assertFalse(DocGrouping.suggest(files).hasSuggestion)
    }

    @Test
    fun timeFromCameraNames() {
        val a = DocGrouping.timeFromName("PXL_20261122_101530123.jpg")!!
        val b = DocGrouping.timeFromName("IMG_20261122_101545.jpg")!!
        assertEquals(15_000L, b - a)
        assertNull(DocGrouping.timeFromName("Befund.pdf"))
    }

    @Test
    fun groupsFollowJoinFlags() {
        assertEquals(
            listOf(listOf(3), listOf(1, 0), listOf(2)),
            DocGrouping.groups(listOf(3, 1, 0, 2), listOf(false, false, true, false)),
        )
    }
}
