package de.meinhelfer.tag.summary

import java.time.LocalDate

/** Eine Kontrolle oder ein Termin aus einem Entlassbericht (nur Vorschlag, wird vorher geprüft). */
data class FollowUp(
    /** Kurzer Titel, z. B. „Laborkontrolle (Blutwerte) – Hausarzt“. */
    val title: String,
    /** Satz aus dem Bericht, aus dem der Vorschlag stammt. */
    val sentence: String,
    /** Tag der Kontrolle (Epoch-Tag): genau aus dem Text oder geschätzt aus „in 2 Wochen“; null = ohne Zeitangabe. */
    val day: Long?,
    /** true, wenn ein Datum im Text steht (nicht nur geschätzt). */
    val exact: Boolean,
    /** Uhrzeit „HH:mm“, falls im Text. */
    val time: String?,
    /** Zuständige Stelle aus dem Text, z. B. „Hausarzt“, „Kardiologe“, „Klinik-Ambulanz“, sonst leer. */
    val place: String,
)

/** Ergebnis des Auslesens eines Entlassberichts. */
data class DischargeInfo(
    /** Medikation bei Entlassung (leer, wenn nichts sicher erkannt). */
    val plan: MedPlan,
    /** true, wenn ein Abschnitt „Medikation bei Entlassung“ o. ä. gefunden wurde. */
    val fromSection: Boolean,
    /** Laut Bericht abgesetzte oder pausierte Medikamente (Zeilen gekürzt). */
    val stopped: List<String>,
    val followUps: List<FollowUp>,
    /** Entlassungstag, falls im Text. */
    val dischargeDay: Long?,
)

/**
 * Liest Entlassberichte (Arztbriefe aus dem Krankenhaus) auf dem Handy aus: Medikation bei Entlassung und Kontrollen
 * („Wiedervorstellung in 2 Wochen“, „Laborkontrolle beim Hausarzt“). Alles nur Anhaltspunkte, die vor dem Übernehmen geprüft werden.
 */
object Discharge {
    private val DATE = Regex("""(?<![\d.])(\d{1,2})\.\s?(\d{1,2})\.(\d{4}|\d{2})?(?![\d])""")
    private val TIME = Regex("""(?i)\b(?:um\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*uhr\b""")

    private val REPORT = Regex(
        """(?i)entlass(?:ungs)?(?:brief|bericht)|vorläufiger\s+arztbrief|arztbrief|stationäre[nr]?\s+(?:aufenthalt|behandlung|aufnahme)|wir\s+berichten\s+über|entlassen\s+am|entlassung\s+am""",
    )

    private val MED_START = Regex(
        """(?i)^\W*(?:entlass(?:ungs)?medikation|medikation\s+(?:bei|zur|nach)\s+entlassung|empfohlene\s+(?:medikation|dauermedikation|therapie)|medikamentöse\s+therapie(?:\s+bei\s+entlassung)?|therapieempfehlung(?:en)?|aktuelle\s+medikation|medikation\s+bei\s+entlassung|weiterführende\s+medikation|medikation)\b""",
    )
    private val MED_EXCLUDE = Regex(
        """(?i)^\W*(?:aufnahmemedikation|medikation\s+(?:bei|vor|zur)\s+aufnahme|vormedikation|hausmedikation|bisherige\s+medikation|medikation\s+vor\s+aufnahme)\b""",
    )
    private val SECTION_END = Regex(
        """(?i)^\W*(?:procedere|prozedere|empfehlung(?:en)?|weiteres\s+(?:vorgehen|procedere)|epikrise|diagnose[n]?|befund(?:e)?|labor(?:werte|befunde)?|anamnese|verlauf|therapie\s+und\s+verlauf|beurteilung|untersuchung(?:en)?|termine?|wiedervorstellung|kontrolle[n]?|sonstiges|hinweis(?:e)?|mit\s+freundlichen|mit\s+kollegialen|zusammenfassung|ernährung|mobilisation|pflege)\b""",
    )
    private val STOPPED = Regex("""(?i)\b(abgesetzt|pausiert|gestoppt|absetzen|beendet|nicht\s+mehr\s+einnehmen)\b""")

    private val KEYWORD = Regex(
        """(?i)wiedervorstell|vorzustellen|vorstellung\s+(?:in|bei|im|zur)|kontroll|nachsorge|nachuntersuchung|fadenzug|fäden|faeden|nahtentfernung|klammer(?:entfernung|n\s+entfernen)|verbandswechsel|wundkontrolle|termin\b|termine\b|laborkontrolle|untersuchung\s+(?:in|nach)\b""",
    )
    private val ADVICE = Regex(
        """(?i)\b(?:empfehlen|empfohlen|erbeten|bitten|bitte|sollte|soll|sollten|ist\s+geplant|geplant|vereinbart|vorgesehen|notwendig|erforderlich|indiziert|anzuraten|ratsam|erfolgen|durchführen|durchzuführen|wahrnehmen)\b""",
    )
    private val RELATIVE = Regex(
        """(?i)\b(?:in|nach|binnen|innerhalb\s+von)\s+(\d{1,2}|einer|einem|eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|zwölf|vierzehn)(?:\s*(?:-|–|bis)\s*(\d{1,2}))?\s*(tag(?:e|en)?|woche(?:n)?|monat(?:e|en)?|jahr(?:e|en)?)\b""",
    )
    private val SOON = Regex("""(?i)\b(zeitnah|kurzfristig|in\s+den\s+nächsten\s+tagen|baldmöglichst|umgehend)\b""")
    private val DISCHARGE_DATE = Regex(
        """(?i)(?:entlass(?:ung|en)?(?:sdatum|stag)?\s*(?:am|:)?|bis\s+zum|bis)\s*(\d{1,2}\.\s?\d{1,2}\.\d{2,4})""",
    )

    private val NUMBERS = mapOf(
        "einer" to 1, "einem" to 1, "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "sechs" to 6,
        "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "zwölf" to 12, "vierzehn" to 14,
    )

    private val PLACES = listOf(
        "hausärzt|hausarzt|hausärztin" to "Hausarzt",
        "kardiolog" to "Kardiologe",
        "neurolog" to "Neurologe",
        "urolog" to "Urologe",
        "gynäkolog|frauenarzt|frauenärzt" to "Gynäkologe",
        "orthopäd" to "Orthopäde",
        "chirurg" to "Chirurg",
        "nephrolog" to "Nephrologe",
        "gastroenterolog" to "Gastroenterologe",
        "pneumolog|lungenfacharzt" to "Pneumologe",
        "onkolog" to "Onkologe",
        "dermatolog|hautarzt" to "Hautarzt",
        "augenarzt|augenärzt|ophthalmolog" to "Augenarzt",
        "\\bhno\\b|hals-nasen-ohren" to "HNO-Arzt",
        "diabetolog" to "Diabetologe",
        "endokrinolog" to "Endokrinologe",
        "psychiat" to "Psychiater",
        "rheumatolog" to "Rheumatologe",
        "angiolog|gefäßchirurg" to "Angiologe",
        "hämatolog" to "Hämatologe",
        "radiolog" to "Radiologe",
        "zahnarzt|zahnärzt|kieferchirurg" to "Zahnarzt",
        "physiotherap|krankengymnast" to "Physiotherapie",
        "ergotherap" to "Ergotherapie",
        "logopäd" to "Logopädie",
        "pflegedienst" to "Pflegedienst",
        "(?:unsere[rm]?|der|die)\\s+(?:ambulanz|sprechstunde|klinik|notaufnahme)|ambulanz" to "Klinik-Ambulanz",
        "facharzt|fachärzt" to "Facharzt",
    ).map { (re, label) -> Regex(re, RegexOption.IGNORE_CASE) to label }

    private val KINDS = listOf(
        "fäden|faden|naht|klammer" to "Fäden oder Klammern entfernen lassen",
        "verbandswechsel|wund" to "Wundkontrolle / Verbandswechsel",
        "blutbild|labor|blutwert|inr|quick|kalium|kreatinin|nierenwert|leberwert|elektrolyt|hba1c|blutzucker|tsh|spiegel|entzündungswert|crp|hämoglobin|hb-" to "Laborkontrolle (Blutwerte)",
        "röntgen" to "Röntgenkontrolle",
        "echokardiograph|\\becho\\b|sonograph|ultraschall|duplex" to "Ultraschall-Kontrolle",
        "\\bekg\\b|langzeit-ekg|holter" to "EKG-Kontrolle",
        "blutdruck|rr-" to "Blutdruckkontrolle",
        "\\bct\\b|mrt|computertomograph|kernspin" to "Bildgebung (CT/MRT)",
        "wiedervorstell|vorzustellen|vorstellung" to "Wiedervorstellung",
        "nachsorge|nachuntersuchung" to "Nachsorge",
    ).map { (re, label) -> Regex(re, RegexOption.IGNORE_CASE) to label }

    /** Sieht das Dokument nach einem Entlassbericht aus (Kategorie oder Text)? */
    fun looksLikeReport(category: String, text: String): Boolean =
        text.isNotBlank() && (category.contains("Entlass", ignoreCase = true) || category.contains("Krankenhaus", ignoreCase = true) ||
            REPORT.containsMatchIn(text))

    private fun date(d: String, m: String, y: String?, base: LocalDate): LocalDate? = runCatching {
        if (y.isNullOrBlank()) {
            val candidate = LocalDate.of(base.year, m.toInt(), d.toInt())
            if (candidate.isBefore(base.minusDays(30))) candidate.plusYears(1) else candidate
        } else {
            LocalDate.of(y.toInt().let { if (it < 100) 2000 + it else it }, m.toInt(), d.toInt())
        }
    }.getOrNull()

    /** Entlassungstag aus „Entlassung am 12.11.2026“ oder „stationär vom … bis 12.11.2026“. */
    fun dischargeDay(text: String): LocalDate? {
        val m = DISCHARGE_DATE.find(text) ?: return null
        val d = DATE.find(m.groupValues[1]) ?: return null
        val y = d.groupValues[3].takeIf { it.isNotBlank() } ?: return null
        return date(d.groupValues[1], d.groupValues[2], y, LocalDate.now())
    }

    /** Abschnitt „Medikation bei Entlassung“ (ohne Überschrift) oder null. Aufnahme- und Vormedikation zählen nicht. */
    fun medSection(text: String): String? {
        val lines = text.lines()
        var start = -1
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.length > 70) continue
            if (MED_EXCLUDE.containsMatchIn(line)) continue
            if (MED_START.containsMatchIn(line)) {
                start = i
                // Ein ausdrücklicher Entlass-Abschnitt hat Vorrang vor „Medikation“ allgemein
                if (Regex("(?i)entlass|empfohl|therapieempfehl").containsMatchIn(line)) break
            }
        }
        if (start < 0) return null
        val out = mutableListOf<String>()
        // Steht hinter der Überschrift noch Text in derselben Zeile, gehört er dazu
        lines[start].substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in start + 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isNotEmpty() && line.length <= 60 && (SECTION_END.containsMatchIn(line) || MED_EXCLUDE.containsMatchIn(line)) &&
                !MedPlansSchema.has(line)
            ) {
                break
            }
            out += lines[i]
        }
        return out.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Text ohne Abschnitte zur Aufnahme- oder Vormedikation (für Berichte ohne eigenen Entlass-Abschnitt). */
    private fun withoutAdmission(text: String): String {
        val out = mutableListOf<String>()
        var skipping = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.length <= 70 && MED_EXCLUDE.containsMatchIn(line)) {
                skipping = true
                continue
            }
            if (skipping && line.isNotEmpty() && line.length <= 60 && (SECTION_END.containsMatchIn(line) || MED_START.containsMatchIn(line)) && !MedPlansSchema.has(line)) {
                skipping = false
            }
            if (!skipping) out += raw
        }
        return out.joinToString("\n")
    }

    /** Medikation bei Entlassung; abgesetzte oder pausierte Mittel werden nicht vorgeschlagen, sondern genannt. */
    fun meds(text: String): Triple<MedPlan, Boolean, List<String>> {
        val section = medSection(text)
        val source = section ?: withoutAdmission(text)
        val stopped = source.lines().map { it.trim() }.filter { it.isNotEmpty() && STOPPED.containsMatchIn(it) && it.count(Char::isLetter) >= 4 }
            .map { it.take(90) }
        val kept = source.lines().filterNot { STOPPED.containsMatchIn(it) }.joinToString("\n")
        val plan = MedPlans.parseText(kept)
        return Triple(plan.copy(day = null), section != null, stopped.distinct().take(8))
    }

    /** Text in Sätze bzw. Aufzählungspunkte zerlegen (Zeilenumbrüche aus der Texterkennung werden zusammengefügt). */
    internal fun sentences(text: String): List<String> {
        val chunks = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        for (raw in text.lines()) {
            val line = raw.trim()
            val startsNew = line.isEmpty() || line.startsWith("-") || line.startsWith("•") || line.startsWith("*") ||
                current.endsWith(":") || (line.length <= 40 && line.endsWith(":"))
            if (startsNew && current.isNotBlank()) {
                chunks += current
                current = StringBuilder()
            }
            if (line.isNotEmpty()) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(line.trimStart('-', '•', '*', ' '))
            }
        }
        if (current.isNotBlank()) chunks += current
        // Satzenden: Punkt, Ausrufe- oder Fragezeichen vor Großbuchstaben; nicht nach Abkürzungen oder in Datumsangaben
        val abbreviations = Regex("""(?i)(?:\b(?:dr|prof|ca|bzw|ggf|evtl|tgl|std|hr|fr|nr|tel|str|z\.\s?b|u\.\s?a|o\.\s?g|v\.\s?a|z\.\s?n|s\.\s?o|inkl|max|min|med|priv|doz|sog|mind)\.)$""")
        val out = mutableListOf<String>()
        for (chunk in chunks) {
            val s = chunk.toString()
            var from = 0
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if ((c == '.' || c == '!' || c == '?') && i + 2 < s.length && s[i + 1] == ' ' && s[i + 2].isUpperCase()) {
                    val piece = s.substring(from, i + 1)
                    if (!abbreviations.containsMatchIn(piece)) {
                        out += piece.trim()
                        from = i + 2
                    }
                }
                i++
            }
            if (from < s.length) out += s.substring(from).trim()
        }
        return out.filter { it.isNotBlank() }
    }

    private fun relativeDays(m: MatchResult): Int? {
        val n = m.groupValues[1].lowercase().let { it.toIntOrNull() ?: NUMBERS[it] } ?: return null
        val unit = m.groupValues[3].lowercase()
        return when {
            unit.startsWith("tag") -> n
            unit.startsWith("woche") -> n * 7
            unit.startsWith("monat") -> n * 30
            unit.startsWith("jahr") -> n * 365
            else -> null
        }
    }

    private fun titleFor(sentence: String, place: String): String {
        val kind = KINDS.firstOrNull { it.first.containsMatchIn(sentence) }?.second ?: "Kontrolltermin"
        return if (place.isNotBlank()) "$kind – $place" else kind
    }

    /**
     * Kontrollen und Termine aus dem Text. [base] = Entlassungstag bzw. Datum des Berichts (für „in 2 Wochen“).
     * Befunde aus der Vergangenheit („Kontrollsonographie vom 03.11.: unauffällig“) werden übergangen.
     */
    fun followUps(text: String, base: LocalDate): List<FollowUp> {
        val out = mutableListOf<FollowUp>()
        for (sentence in sentences(text)) {
            if (sentence.length < 12 || !KEYWORD.containsMatchIn(sentence)) continue
            // Datum im Satz (nur heute bzw. nach dem Entlassungstag zählt als Termin)
            val dates = DATE.findAll(sentence).mapNotNull { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3].takeIf { it.isNotBlank() }, base) }.toList()
            val future = dates.firstOrNull { !it.isBefore(base) }
            val rel = RELATIVE.find(sentence)?.let { relativeDays(it) }
            val soon = SOON.containsMatchIn(sentence)
            val advice = ADVICE.containsMatchIn(sentence)
            if (future == null && rel == null && !soon && !advice) continue
            // Rückblick auf Untersuchungen im Aufenthalt überspringen (nur vergangene Daten, keine Empfehlung)
            if (future == null && dates.isNotEmpty() && rel == null && !advice) continue
            val day = when {
                future != null -> future
                rel != null -> base.plusDays(rel.toLong())
                soon -> base.plusDays(7)
                else -> null
            }
            val time = if (future != null) TIME.find(sentence)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()?.takeIf { it in 6..20 } ?: return@let null
                val min = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: 0
                "%02d:%02d".format(h, min)
            } else null
            val place = PLACES.firstOrNull { it.first.containsMatchIn(sentence) }?.second.orEmpty()
            out += FollowUp(
                title = titleFor(sentence, place),
                sentence = sentence.take(260),
                day = day?.toEpochDay(),
                exact = future != null,
                time = time,
                place = place,
            )
        }
        return out.distinctBy { it.title + "|" + it.day }.take(8)
    }

    /** Alles zusammen; [docDay] = Datum des Dokuments, [today] als Rückfall. */
    fun read(text: String, docDay: Long?, today: LocalDate = LocalDate.now()): DischargeInfo {
        val discharge = dischargeDay(text)
        val base = discharge ?: docDay?.let { LocalDate.ofEpochDay(it) } ?: today
        val (plan, fromSection, stopped) = meds(text)
        return DischargeInfo(
            plan = plan.copy(day = (discharge ?: docDay?.let { LocalDate.ofEpochDay(it) })?.toEpochDay()),
            fromSection = fromSection,
            stopped = stopped,
            followUps = followUps(text, base),
            dischargeDay = discharge?.toEpochDay(),
        )
    }
}

/** Erkennt Einnahme-Schemata wie „1-0-1“ (für Abschnittsgrenzen). */
private object MedPlansSchema {
    private val SCHEMA = Regex("""(?<![\d./])\d{1,2}(?:[.,]\d)?\s*[-–]\s*\d{1,2}(?:[.,]\d)?\s*[-–]\s*\d{1,2}(?:[.,]\d)?""")
    fun has(line: String) = SCHEMA.containsMatchIn(line)
}
