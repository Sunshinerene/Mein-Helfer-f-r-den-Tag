package de.meinhelfer.tag.summary

/**
 * Entfernt personenbezogene Angaben, bevor Text an einen KI-Dienst geht:
 * bekannte Namen (Personen, Kontakte, du selbst), E-Mail, Telefon, Adresse, PLZ mit Ort, Versicherten- und Kontonummern, Geburtsdaten.
 * Gesundheitsangaben (Medikamente, Werte) bleiben, sonst kann die KI nicht helfen.
 */
object Anonymizer {
    /** Namen, die zuletzt in der App bekannt waren (für die Vorschau ohne Datenbankzugriff). */
    @Volatile
    var known: Set<String> = emptySet()

    private val TITLES = setOf("dr", "dr.", "prof", "prof.", "med", "med.", "frau", "herr", "praxis", "dipl", "dipl.", "und", "von", "van", "der", "den", "die", "zu")

    /** Namensteile (ab 3 Buchstaben, ohne Titel) aus vollen Namen. */
    fun nameParts(names: Collection<String>): Set<String> = names.flatMap { n ->
        n.split(Regex("[\\s,]+")).map { it.trim('.', '-', '(', ')') }
    }.filter { it.length >= 3 && it.lowercase() !in TITLES && it.any { c -> c.isLetter() } }.toSet()

    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val IBAN = Regex("\\b[A-Z]{2}\\d{2}(?:\\s?[0-9A-Z]{4}){3,7}(?:\\s?[0-9A-Z]{1,4})?\\b")
    private val KVNR = Regex("\\b[A-Z]\\d{9}\\b")
    private val PHONE = Regex("(?<![\\w/])(?:\\+\\d{2}|0)\\d[\\d\\s/()-]{5,}\\d")
    private val STREET = Regex(
        "\\b[A-ZÄÖÜ][\\wäöüß.-]*(?:straße|strasse|str\\.|weg|gasse|allee|platz|ring|damm|ufer|chaussee|steig|pfad)\\s*\\d+\\s*[a-zA-Z]?\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ZIP_CITY = Regex("\\b\\d{5}\\s+[A-ZÄÖÜ][\\wäöüß-]+(?:\\s+(?:(?:an der|am|im|bei|in der|ob der)\\s+)?[A-ZÄÖÜ][\\wäöüß-]+){0,2}")
    private val BIRTH = Regex("(?:geb(?:oren|\\.)?\\s*(?:am)?\\s*:?\\s*|\\*\\s*)\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}", RegexOption.IGNORE_CASE)
    private val OLD_DATE = Regex("\\b\\d{1,2}\\.\\d{1,2}\\.(19\\d{2})\\b")

    fun scrub(text: String, names: Collection<String> = known): String {
        var t = text
        t = EMAIL.replace(t, "[E-Mail]")
        t = IBAN.replace(t, "[Kontonummer]")
        t = KVNR.replace(t, "[Versichertennummer]")
        t = BIRTH.replace(t, "[Geburtsdatum]")
        t = OLD_DATE.replace(t, "[Datum]")
        t = STREET.replace(t, "[Adresse]")
        t = ZIP_CITY.replace(t, "[Ort]")
        t = PHONE.replace(t, "[Telefon]")
        nameParts(names).sortedByDescending { it.length }.forEach { p ->
            t = Regex("(?<![\\wäöüßÄÖÜ])" + Regex.escape(p) + "(?![\\wäöüßÄÖÜ])", RegexOption.IGNORE_CASE).replace(t, "[Name]")
        }
        return t.replace(Regex("\\[Name\\](\\s+\\[Name\\])+"), "[Name]")
    }
}
