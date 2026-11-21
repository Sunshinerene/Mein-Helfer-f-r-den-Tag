package de.meinhelfer.tag.media

import de.meinhelfer.tag.data.Doctor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DOC_CATEGORIES = listOf(
    "Arztbericht", "Befund / Labor", "Rezept / Verordnung", "Medikamentenplan", "Pflegegrad / Bescheid",
    "Vollmacht / Verfügung", "Rechnung / Quittung", "Überweisung", "Sonstiges",
)

/** Vorschlaege fuer ein neues Dokument, aus dem erkannten Text. Alles bleibt aenderbar. */
data class DocSuggestion(
    val title: String,
    val category: String,
    val date: LocalDate?,
    val doctorId: Long?,
)

object DocumentAnalyzer {
    /** Zusaetzliche Kategorien, die vorgeschlagen werden, wenn keine der Standard-Kategorien passt. */
    private val EXTRA_CATEGORIES: List<Pair<String, Regex>> = listOf(
        "Wohnung / Miete" to Regex("Mietvertrag|Miete|Vermieter|Mieter|Wohnungs(?:übergabe|vertrag)|Nebenkosten", RegexOption.IGNORE_CASE),
        "Schwerbehinderung" to Regex("Schwerbehinder|Grad der Behinderung|\\bGdB\\b|Merkzeichen", RegexOption.IGNORE_CASE),
        "Pflegeheim / Tagespflege" to Regex("Heimvertrag|Pflegeheim|Heimplatz|Tagespflege|Kurzzeitpflege", RegexOption.IGNORE_CASE),
        "Pflegedienst" to Regex("Pflegedienst|Pflegevertrag|Leistungsnachweis|Pflegetagebuch", RegexOption.IGNORE_CASE),
        "Hilfsmittel" to Regex("Hilfsmittel|Rollator|Rollstuhl|Pflegebett|Hausnotruf", RegexOption.IGNORE_CASE),
        "Reha / Kur" to Regex("Rehabilitation|\\bReha\\b|\\bKur\\b", RegexOption.IGNORE_CASE),
        "Antrag / Widerspruch" to Regex("Antrag|Widerspruch|Kostenübernahme|Genehmigung", RegexOption.IGNORE_CASE),
        "Versicherung" to Regex("Versicherungsschein|Police|Haftpflicht|Unfallversicherung|Sterbegeld|Versicherung", RegexOption.IGNORE_CASE),
        "Rente / Finanzen" to Regex("Rentenbescheid|Rentenversicherung|Rente|Kontoauszug|Sparkasse|Einkommensteuer|Steuer", RegexOption.IGNORE_CASE),
        "Testament / Nachlass" to Regex("Testament|Erbschein|Nachlass|Erbe\\b", RegexOption.IGNORE_CASE),
        "Impfungen" to Regex("Impfpass|Impfausweis|Impfung", RegexOption.IGNORE_CASE),
        "Zahnarzt / Bonusheft" to Regex("Bonusheft|Zahnersatz|Heil- und Kostenplan|Zahnarzt", RegexOption.IGNORE_CASE),
        "Urkunden / Ausweise" to Regex("Personalausweis|Reisepass|Geburtsurkunde|Heiratsurkunde|Sterbeurkunde|Urkunde", RegexOption.IGNORE_CASE),
    )

    /** Name fuer eine neue Kategorie, wenn Titel oder Text auf ein anderes Thema hindeuten. Titel zaehlt zuerst. */
    fun suggestNewCategory(title: String, text: String): String? {
        EXTRA_CATEGORIES.firstOrNull { (_, regex) -> regex.containsMatchIn(title) }?.let { return it.first }
        return EXTRA_CATEGORIES.firstOrNull { (_, regex) -> regex.containsMatchIn(text.take(4000)) }?.first
    }

    /** Passende Standard-Kategorie aus dem Text, oder null. */
    fun suggestKnownCategory(title: String, text: String): String? {
        val joined = title + "\n" + text
        return RULES.firstOrNull { (_, regex) -> regex.containsMatchIn(joined) }?.first
    }

    private val DATE = Regex("""\b(\d{1,2})\.\s?(\d{1,2})\.\s?(\d{4})\b""")
    private val SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

    private val RULES: List<Pair<String, Regex>> = listOf(
        "Medikamentenplan" to Regex("Medikationsplan|Medikamentenplan|Bundeseinheitlicher Medikationsplan", RegexOption.IGNORE_CASE),
        "Rezept / Verordnung" to Regex("Rezept|Verordnung|Arzneimittelverordnung|Muster 16", RegexOption.IGNORE_CASE),
        "Pflegegrad / Bescheid" to Regex("Pflegegrad|Pflegekasse|Medizinischer Dienst|Begutachtung|Bescheid", RegexOption.IGNORE_CASE),
        "Vollmacht / Verfügung" to Regex("Vollmacht|Patientenverfügung|Betreuungsverfügung", RegexOption.IGNORE_CASE),
        "Rechnung / Quittung" to Regex("Rechnung|Quittung|Rechnungsbetrag|Zuzahlung", RegexOption.IGNORE_CASE),
        "Überweisung" to Regex("Überweisung", RegexOption.IGNORE_CASE),
        "Arztbericht" to Regex("Arztbrief|Arztbericht|Entlassbrief|Entlassungsbrief|Epikrise|Anamnese|Diagnose", RegexOption.IGNORE_CASE),
        "Befund / Labor" to Regex("Befund|Labor|Blutbild|Röntgen|MRT|Sonographie|Ultraschall|EKG|Messwert", RegexOption.IGNORE_CASE),
    )

    fun analyze(text: String, doctors: List<Doctor>, today: LocalDate = LocalDate.now()): DocSuggestion {
        val category = RULES.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first ?: "Sonstiges"
        val date = findDate(text, today)
        val doctor = findDoctor(text, doctors)
        val title = buildString {
            append(if (category == "Sonstiges") "Dokument" else category)
            if (date != null) append(" ").append(date.format(SHOWN))
        }
        return DocSuggestion(title, category, date, doctor?.id)
    }

    /** Neuestes Datum bis heute, Geburtsdaten ("geb. ...") werden ignoriert. */
    private fun findDate(text: String, today: LocalDate): LocalDate? =
        DATE.findAll(text).mapNotNull { m ->
            val before = text.substring(maxOf(0, m.range.first - 14), m.range.first).lowercase()
            if ("geb" in before) return@mapNotNull null
            runCatching {
                LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            }.getOrNull()
        }.filter { it.year >= 1990 && !it.isAfter(today) }.maxOrNull()

    /** Kontakt, dessen Name (oder Nachname) im Text vorkommt. */
    private fun findDoctor(text: String, doctors: List<Doctor>): Doctor? {
        val lower = text.lowercase()
        doctors.firstOrNull { it.name.length >= 4 && lower.contains(it.name.lowercase()) }?.let { return it }
        return doctors.firstOrNull { d ->
            val last = d.name.trim().split(Regex("\\s+")).lastOrNull().orEmpty()
            last.length >= 4 && last.all { it.isLetter() || it == '-' } && Regex("\\b" + Regex.escape(last.lowercase()) + "\\b").containsMatchIn(lower)
        }
    }
}
