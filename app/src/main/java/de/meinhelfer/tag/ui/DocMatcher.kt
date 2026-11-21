package de.meinhelfer.tag.ui

import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import java.time.LocalDate

/** Ein Dokument mit Punkten und den Gruenden, warum es passen koennte. */
internal class DocMatch(val doc: Document, val score: Int, val reasons: List<String>) {
    val reasonText: String get() = reasons.joinToString(" · ")
}

/**
 * Ordnet Dokumente einem Termin oder einer Aufgabe zu. Jedes Dokument bekommt Punkte fuer: gleicher Arzt,
 * passende Kategorie, Stichwoerter im Titel und Text, Aktualitaet. Zu alte oder spaeter datierte
 * Dokumente werden aussortiert, bei Plaenen und Bescheiden zaehlt nur der neueste. Alles nur lokal.
 */
internal object DocMatcher {
    const val MIN_SCORE = 4
    private const val MAX_SUGGESTIONS = 6

    private val STOP = setOf(
        "termin", "beim", "bei", "der", "die", "das", "und", "mit", "fur", "für", "zum", "zur", "nach", "vor", "von",
        "ein", "eine", "einen", "wegen", "kontrolle", "besuch", "arzt", "arztbesuch", "ausmachen", "neuen", "neue",
        "machen", "holen", "abholen", "anrufen", "fragen", "noch", "bitte", "wieder", "den", "dem", "des", "auf", "aus",
        "mal", "ist", "sind", "wird", "werden", "uhr", "heute", "morgen", "woche", "frau", "herr",
    )

    /** Facharzt -> Woerter, die in passenden Dokumenten vorkommen. */
    private val SPECIALTY_WORDS: List<Pair<String, List<String>>> = listOf(
        "kardio" to listOf("ekg", "herz", "blutdruck", "echo", "rhythmus", "kardio"),
        "nephro" to listOf("niere", "kreatinin", "egfr", "dialyse", "nephro"),
        "augen" to listOf("augen", "netzhaut", "sehkraft", "grüner star", "grauer star"),
        "neuro" to listOf("neuro", "mrt", "gehirn", "parkinson", "demenz", "epilepsie", "schlaganfall"),
        "ortho" to listOf("orthop", "knochen", "gelenk", "röntgen", "hüfte", "knie", "wirbelsäule"),
        "uro" to listOf("urolog", "prostata", "blase", "harn"),
        "haut" to listOf("haut", "derma"),
        "diabet" to listOf("hba1c", "diabetes", "blutzucker", "insulin"),
        "schild" to listOf("schilddrüse", "tsh", "thyrox"),
        "hno" to listOf("hals", "nase", "ohr", "gehör", "hörgerät"),
        "psych" to listOf("psych", "depress", "angst", "therapie"),
        "geriat" to listOf("geriat", "sturz", "demenz", "mobilität"),
        "zahn" to listOf("zahn", "gebiss", "prothese"),
        "gyn" to listOf("gynäk", "frauenarzt"),
        "hausarzt" to listOf("blutwerte", "laborwerte", "check-up", "impf"),
        "pflege" to listOf("pflegegrad", "pflegedienst", "pflege"),
    )

    /** Kategorien, die zu einem Anlass passen. Die erste ist die wichtigste. Leer = keine Vorgabe. */
    fun categoriesFor(text: String, general: Boolean): List<String> {
        fun has(vararg words: String) = words.any { text.contains(it) }
        return when {
            has("krankenhaus", "klinik", "aufnahme", "entlass", "einweisung") ->
                listOf("Arztbericht", "Medikamentenplan", "Vollmacht / Verfügung", "Pflegegrad / Bescheid", "Überweisung")
            has("pflegebegutachtung", "begutachtung", "pflegekasse", "medicproof", "md ", "pflegegrad", "höherstufung") ->
                listOf("Pflegegrad / Bescheid", "Arztbericht", "Medikamentenplan", "Vollmacht / Verfügung")
            has("physio", "ergo", "logo", "krankengymnastik") -> listOf("Rezept / Verordnung", "Arztbericht")
            has("blut", "labor") -> listOf("Befund / Labor", "Überweisung")
            has("rezept", "apotheke") -> listOf("Rezept / Verordnung", "Medikamentenplan")
            has("überweisung") -> listOf("Überweisung", "Arztbericht")
            has("vollmacht", "verfügung", "betreuung") -> listOf("Vollmacht / Verfügung")
            has("rechnung", "quittung", "erstattung", "kosten") -> listOf("Rechnung / Quittung")
            has("antrag", "widerspruch", "bescheid") -> listOf("Pflegegrad / Bescheid", "Arztbericht")
            general -> listOf("Arztbericht", "Befund / Labor", "Medikamentenplan", "Rezept / Verordnung")
            else -> emptyList()
        }
    }

    private fun stem(w: String): String = if (w.length >= 6) w.take(5) else w

    /** Aussagekraeftige Woerter aus Titel, Notizen und Fachrichtung. */
    fun keywords(texts: List<String>, doctor: Doctor?): Set<String> {
        val words = texts.flatMap { it.lowercase().split(Regex("[^a-zäöüß0-9]+")) }
            .filter { it.length >= 4 && it !in STOP && !it.all { c -> c.isDigit() } }
            .map { stem(it) }
        val spec = (doctor?.specialty.orEmpty() + " " + doctor?.name.orEmpty()).lowercase()
        val fromSpecialty = SPECIALTY_WORDS.filter { (key, _) -> spec.contains(key) }.flatMap { it.second }.map { stem(it) }
        return (words + fromSpecialty).toSet()
    }

    private val SINGLE_NEWEST = setOf("Medikamentenplan", "Pflegegrad / Bescheid")
    private val EXPIRES = setOf("Medikamentenplan", "Befund / Labor", "Rezept / Verordnung")

    fun match(
        docs: List<Document>,
        categories: List<String>,
        doctor: Doctor?,
        keywords: Set<String>,
        refDay: Long,
        today: Long = LocalDate.now().toEpochDay(),
    ): List<DocMatch> {
        val newestOfSingle = SINGLE_NEWEST.associateWith { cat ->
            docs.filter { it.category == cat && docEpochDay(it) <= refDay + 7 }.maxByOrNull { docEpochDay(it) }?.id
        }
        val out = mutableListOf<DocMatch>()
        for (d in docs) {
            val day = docEpochDay(d)
            val age = refDay - day
            if (age < -7) continue // erst nach dem Anlass entstanden
            var score = 0
            val reasons = mutableListOf<String>()

            if (doctor != null && d.doctorId == doctor.id) {
                score += 3
                reasons += "gleicher Arzt / gleiche Stelle"
            }
            val ci = categories.indexOf(d.category)
            if (ci >= 0) {
                score += if (ci == 0) 3 else 2
                reasons += d.category
            }

            if (keywords.isNotEmpty()) {
                val head = (d.title + " " + d.category).lowercase()
                val body = (d.text + " " + d.summary + " " + d.notes).lowercase()
                val inHead = keywords.filter { head.contains(it) }
                val inBody = keywords.filter { it !in inHead && body.contains(it) }
                if (inHead.isNotEmpty()) {
                    score += minOf(inHead.size * 2, 4)
                    reasons += "Stichwort im Titel: " + inHead.take(2).joinToString(", ")
                }
                if (inBody.isNotEmpty()) {
                    score += minOf(inBody.size, 3)
                    reasons += "Stichwort im Text: " + inBody.take(2).joinToString(", ")
                }
            }

            // Nur wenn schon etwas passt, zaehlt die Aktualitaet mit
            if (score > 0) {
                when {
                    age <= 180 -> {
                        score += 2
                        reasons += "aktuell"
                    }
                    age <= 365 -> score += 1
                    age > 1095 -> score -= 2
                }
                if (d.category in EXPIRES && age > 365) score -= 2
                if (d.category in SINGLE_NEWEST && newestOfSingle[d.category] != d.id) score -= 5
                if (d.category == "Rechnung / Quittung" && "Rechnung / Quittung" !in categories) score -= 2
            }
            if (score >= MIN_SCORE) out += DocMatch(d, score, reasons)
        }
        return out.sortedWith(compareByDescending<DocMatch> { it.score }.thenByDescending { docEpochDay(it.doc) })
            .take(MAX_SUGGESTIONS)
    }
}
