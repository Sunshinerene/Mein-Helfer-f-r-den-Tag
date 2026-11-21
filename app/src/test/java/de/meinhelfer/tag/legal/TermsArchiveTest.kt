package de.meinhelfer.tag.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Text der Nutzungsbedingungen in der App muss genau der archivierten Fassung entsprechen
 * (docs/nutzungsbedingungen/fassung-<VERSION>.txt). Sonst schlaegt der Build fehl.
 */
class TermsArchiveTest {
    private fun archive(): File {
        val name = "docs/nutzungsbedingungen/fassung-${Terms.VERSION}.txt"
        return listOf(File(name), File("../$name")).firstOrNull { it.exists() } ?: File("../$name")
    }

    @Test
    fun archivedTextMatchesApp() {
        val file = archive()
        assertTrue("Archiv fehlt: ${file.path} – neue Fassung bitte ablegen", file.exists())
        assertEquals(
            "Text in der App weicht von der archivierten Fassung ab. Fassung erhöhen und neu ablegen.",
            file.readText(Charsets.UTF_8),
            Terms.fullText(),
        )
        assertEquals(Terms.sha256(file.readText(Charsets.UTF_8)), Terms.textHash())
    }

    @Test
    fun archiveListsHash() {
        val readme = File(archive().parentFile, "README.md")
        assertTrue("Prüfsumme fehlt in docs/nutzungsbedingungen/README.md", readme.readText().contains(Terms.textHash()))
    }
}
