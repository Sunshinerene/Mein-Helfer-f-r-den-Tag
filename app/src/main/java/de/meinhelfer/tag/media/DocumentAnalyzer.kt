package de.meinhelfer.tag.media

import de.meinhelfer.tag.data.Doctor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DOC_CATEGORIES = listOf(
    "Arztbericht", "Krankenhaus / Entlassbericht", "Befund / Labor", "Rezept / Verordnung", "Medikamentenplan", "Pflegegrad / Bescheid",
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
    fun suggestKnownCategory(title: String, text: String): String? =
        // Bei genug Text zaehlt nur der Text, damit ein falscher alter Titel nicht in die Irre fuehrt.
        if (text.length >= 80) bestCategory(text) else bestCategory(title + "\n" + text)

    private val DATE = Regex("""\b(\d{1,2})\.\s?(\d{1,2})\.\s?(\d{4})\b""")
    private val SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

    /** Kategorie mit eindeutigen (stark) und typischen (schwach) Woertern. */
    private class Rule(val category: String, strong: String, weak: String) {
        val strong = Regex(strong, RegexOption.IGNORE_CASE)
        val weak = Regex(weak, RegexOption.IGNORE_CASE)
    }

    private val RULES: List<Rule> = listOf(
        Rule(
            "Krankenhaus / Entlassbericht",
            "Entlass(?:ungs)?(?:brief|bericht)|Entlassung|entlassen|stationär|Krankenhausaufenthalt|Klinikaufenthalt|Aufnahmedatum|Notaufnahme|Rettungsstelle|vorläufiger Arztbrief",
            "Krankenhaus|Klinikum|Klinik\\b|Station\\b|Chefarzt|Oberarzt|Aufenthalt|aufgenommen",
        ),
        Rule(
            "Medikamentenplan",
            "Medikationsplan|Medikamentenplan|Bundeseinheitlicher Medikationsplan",
            "Wirkstoff|morgens|mittags|abends|zur Nacht",
        ),
        Rule(
            "Rezept / Verordnung",
            "\\bRezept\\b|E-?Rezept|Kassenrezept|Muster 16|Arzneimittelverordnung|Heilmittelverordnung|Verordnung häuslicher Krankenpflege|Rezeptgebühr",
            "Verordnung|verordnet|Gültig bis|\\bPZN\\b",
        ),
        Rule(
            "Pflegegrad / Bescheid",
            "Pflegegrad|Pflegekasse|Medizinischer Dienst|Begutachtung|Pflegegutachten|Medicproof",
            "Bescheid|Widerspruchsfrist|Rechtsbehelf|Leistungsbescheid",
        ),
        Rule(
            "Vollmacht / Verfügung",
            "Vollmacht|Patientenverfügung|Betreuungsverfügung|Vorsorgevollmacht",
            "Bevollmächtigte|bevollmächtige|Betreuer",
        ),
        Rule(
            "Rechnung / Quittung",
            "Rechnung|Quittung|Rechnungsbetrag|Rechnungsnummer",
            "Zuzahlung|Betrag|\\bIBAN\\b|zahlbar",
        ),
        Rule(
            "Überweisung",
            "Überweisungsschein|Überweisung an|Überweisung zur|Muster 6\\b",
            "Überweisung|Mitbehandlung|Auftragsleistung",
        ),
        Rule(
            "Arztbericht",
            "Arztbrief|Arztbericht|Epikrise",
            "Anamnese|Diagnose|Therapie|Procedere|Kollegin|Kollege|Empfehlung",
        ),
        Rule(
            "Befund / Labor",
            "Laborbefund|Befundbericht|Blutbild|Laborwerte|Röntgen|\\bMRT\\b|\\bCT\\b|Sonographie|Ultraschall|\\bEKG\\b",
            "Befund|Labor|Referenzbereich|Normwert|mg/dl|mmol",
        ),
    )

    /**
     * Gewichtete Erkennung statt "erster Treffer gewinnt": Eindeutige Woerter zaehlen mehr, im Kopf (Anfang) noch mehr.
     * So wird z. B. ein Entlassbericht, in dem auch "Verordnung" steht, trotzdem richtig eingeordnet.
     */
    private fun bestCategory(text: String): String? {
        val head = text.take(500)
        val scored = RULES.mapIndexed { index, r ->
            val strongHead = r.strong.findAll(head).count().coerceAtMost(3)
            val strongAll = r.strong.findAll(text).count().coerceAtMost(5)
            val weakAll = r.weak.findAll(text).count().coerceAtMost(4)
            Triple(r.category, strongHead * 4 + strongAll * 3 + weakAll, index)
        }
        val best = scored.filter { it.second >= 3 }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()
        return best?.first
    }

    fun analyze(text: String, doctors: List<Doctor>, today: LocalDate = LocalDate.now()): DocSuggestion {
        val category = bestCategory(text) ?: "Sonstiges"
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
