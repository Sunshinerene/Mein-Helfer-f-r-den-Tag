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
