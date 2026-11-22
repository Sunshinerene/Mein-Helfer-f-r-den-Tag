package de.meinhelfer.tag.letters

import de.meinhelfer.tag.data.Document

/**
 * Ordnet ein eingescanntes, unterschriebenes Schreiben dem Entwurf aus der App zu.
 * Verglichen werden nur Woerter aus dem erkannten Text, alles auf dem Handy.
 */
object SignedCopies {
    /** So beginnt der Zusatz im Titel eines Entwurfs: "Antrag auf Pflegegrad (Entwurf, 2026-11-22)". */
    const val DRAFT_MARK = " (Entwurf, "
    const val SIGNED_NOTE = "Unterschriebene Fassung eingescannt"

    private val WORD = Regex("[A-Za-zÄÖÜäöüß]{5,}")
    private val COMMON = setOf(
        "sehr", "geehrte", "damen", "herren", "hiermit", "bitte", "diese", "dieses", "dieser", "einen", "einer", "eines",
        "meine", "meinen", "meiner", "ihnen", "ihren", "ihrer", "unter", "sowie", "werden", "wurde", "haben", "nicht",
        "freundlichen", "grüßen", "schreiben", "datum", "ergänzen", "schriftlich",
    )

    private fun words(text: String): Set<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filter { it !in COMMON }.toSet()

    fun isOpenDraft(d: Document): Boolean = d.title.contains(DRAFT_MARK) && !d.notes.contains(SIGNED_NOTE)

    /** Titel ohne den Entwurfs-Zusatz. */
    fun baseTitle(d: Document): String = d.title.substringBefore(DRAFT_MARK).trim()

    /**
     * Bester offener Entwurf zum erkannten Text oder null. Es muss ein grosser Teil der markanten Woerter
     * des Entwurfs im neuen Text vorkommen (Texterkennung ist nie ganz fehlerfrei).
     */
    fun bestMatch(text: String, docs: List<Document>): Document? {
        if (text.length < 80) return null
        val found = words(text)
        if (found.size < 10) return null
        return docs.filter { isOpenDraft(it) && it.text.isNotBlank() }
            .map { d ->
                val w = words(d.text)
                d to if (w.size < 8) 0.0 else w.count { it in found }.toDouble() / w.size
            }
            .filter { it.second >= 0.5 }
            .maxByOrNull { it.second }
            ?.first
    }
}
