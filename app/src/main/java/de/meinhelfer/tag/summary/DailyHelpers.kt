package de.meinhelfer.tag.summary

import java.time.LocalDateTime
import java.time.LocalTime

/** Essen und Trinken einer Person: worauf beim Kochen und Einkaufen zu achten ist. Nur auf dem Handy. */
data class Nutrition(
    /** "" = kein Diabetes, "1" = Typ 1, "2" = Typ 2, "andere" */
    val diabetes: String = "",
    val flags: Set<String> = emptySet(),
    /** Allergien und Unverträglichkeiten (Freitext) */
    val allergies: String = "",
    val dislikes: String = "",
    val likes: String = "",
) {
    val isSet get() = diabetes.isNotBlank() || flags.isNotEmpty() || allergies.isNotBlank() || dislikes.isNotBlank() || likes.isNotBlank()

    fun encode(): String = KeyValues.encode(
        mapOf("dm" to diabetes, "f" to flags.sorted().joinToString(",").ifBlank { null }, "all" to allergies, "nein" to dislikes, "gern" to likes),
    )

    /** Für die KI (ohne Namen): „Diabetes Typ 2, weiche Kost; verträgt nicht: Nüsse; mag: Suppen“ */
    fun aiText(): String = listOfNotNull(
        when (diabetes) {
            "1" -> "Diabetes Typ 1"
            "2" -> "Diabetes Typ 2"
            "andere" -> "Diabetes (anderer Typ)"
            else -> null
        },
        FLAGS.filterKeys { it in flags }.values.joinToString(", ").ifBlank { null },
        allergies.takeIf { it.isNotBlank() }?.let { "verträgt nicht / Allergie: $it" },
        dislikes.takeIf { it.isNotBlank() }?.let { "mag nicht: $it" },
        likes.takeIf { it.isNotBlank() }?.let { "isst gern: $it" },
    ).joinToString("; ")

    companion object {
        val FLAGS: Map<String, String> = linkedMapOf(
            "weich" to "weiche Kost (Kauen schwierig)",
            "pueriert" to "pürierte Kost / Schluckstörung",
            "andicken" to "Getränke andicken",
            "salzarm" to "salzarm",
            "niere" to "Nierenerkrankung (kalium- und phosphatarm)",
            "gicht" to "Gicht (purinarm)",
            "vitk" to "Gerinnungshemmer mit Vitamin K (z. B. Marcumar): Vitamin-K-Menge gleichmäßig",
            "laktose" to "laktosefrei",
            "gluten" to "glutenfrei",
            "vegetarisch" to "vegetarisch",
            "vegan" to "vegan",
            "kein_schwein" to "kein Schweinefleisch",
            "untergewicht" to "kalorienreich (Untergewicht)",
            "trinken" to "trinkt zu wenig – Getränke und wasserreiches Essen anbieten",
        )

        fun decode(text: String): Nutrition {
            val m = KeyValues.decode(text)
            return Nutrition(
                diabetes = m["dm"].orEmpty(),
                flags = m["f"]?.split(',')?.map { it.trim() }?.filter { it in FLAGS }?.toSet().orEmpty(),
                allergies = m["all"].orEmpty(),
                dislikes = m["nein"].orEmpty(),
                likes = m["gern"].orEmpty(),
            )
        }
    }
}

/** Wer ist wann zu Hause (nach Tagesstruktur und Terminen)? */
object Presence {
    data class Who(val id: Long, val name: String, val household: Household, val nutrition: Nutrition, val age: Int? = null)

    /** Zu Hause um [at]: nicht in der Werkstatt/Tagespflege usw. und kein Termin ±90 Minuten. */
    fun homeAt(people: List<Who>, at: LocalDateTime, appointmentsMillisByPerson: Map<Long, List<LocalDateTime>> = emptyMap()): List<Who> = people.filter { p ->
        val h = p.household
        val away = h.hasDaytime && at.dayOfWeek.value in h.days && run {
            val from = runCatching { LocalTime.parse(h.from) }.getOrNull()
            val to = runCatching { LocalTime.parse(h.to) }.getOrNull()
            from != null && to != null && !at.toLocalTime().isBefore(from) && at.toLocalTime().isBefore(to)
        }
        val atAppointment = appointmentsMillisByPerson[p.id].orEmpty().any { kotlin.math.abs(java.time.Duration.between(it, at).toMinutes()) < 90 }
        !away && !atAppointment
    }

    /** Buchstaben statt Namen für die KI: Person A, B, C … */
    fun label(i: Int) = "Person " + ('A' + i)
}

/** Ein kleines Werkzeug in Sunnys Bereich: Felder, Anweisung, Art der Ausgabe, welche App-Daten es nutzt. */
data class HelperTool(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val fields: List<Field>,
    /** Aufgabe für die KI, z. B. „Schlage drei Gerichte vor …“ */
    val instruction: String,
    /** "liste" (Vorschläge), "checkliste" (wird zur Einkaufsliste), "text" */
    val output: String = "liste",
    /** Welche Daten aus der App mitgehen: anwesend, ernaehrung, haushalt, vorrat, termine */
    val context: Set<String> = emptySet(),
    val builtin: Boolean = false,
) {
    /** [type]: text, auswahl (eine), mehrfach, foto */
    data class Field(val key: String, val label: String, val type: String = "text", val options: List<String> = emptyList(), val hint: String = "")

    companion object {
        val CONTEXTS = linkedMapOf(
            "anwesend" to "Wer zur gewählten Zeit zu Hause ist (nach Werkstatt-/Tagespflegezeiten und Terminen)",
            "ernaehrung" to "Worauf beim Essen zu achten ist (Diabetes, weiche Kost, Allergien …)",
            "haushalt" to "Wie viele Menschen im Haushalt leben und welches Alter",
            "vorrat" to "Medikamente und Hilfsmittel, die bald knapp werden",
            "termine" to "Termine der nächsten Tage",
        )

        val COOK = HelperTool(
            "kochen", "Was koche ich heute?", "🍳",
            "Vorschläge für eine Mahlzeit – passend zu allen, die dann zu Hause sind, und zu dem, was da ist.",
            listOf(
                Field("mahlzeit", "Mahlzeit", "auswahl", listOf("Frühstück", "Mittagessen", "Abendessen", "Snack")),
                Field("zeit", "Wie viel Zeit?", "auswahl", listOf("15 Minuten", "30 Minuten", "eine Stunde", "egal")),
                Field("da", "Was ist da? (Kühlschrank, Vorrat)", "text", hint = "z. B. Kartoffeln, Eier, Spinat, Quark"),
                Field("foto", "Oder Foto vom Kühlschrank", "foto"),
                Field("wunsch", "Wünsche", "text", hint = "z. B. warm, ohne Backofen, schnell"),
            ),
            "Schlage drei einfache, alltagstaugliche Gerichte vor, die für alle passen, die dann zu Hause sind. Beachte die Hinweise je Person " +
                "(z. B. Diabetes: wenig schnelle Kohlenhydrate, Kohlenhydrate grob in Broteinheiten oder Gramm nennen; weiche oder pürierte Kost; Allergien strikt meiden). " +
                "Nutze vor allem, was da ist; nenne, was fehlt. Pro Gericht: Name, Zutaten, Schritte kurz, Hinweis je Person falls nötig. Keine medizinischen Ratschläge.",
            "liste", setOf("anwesend", "ernaehrung"), builtin = true,
        )

        val SHOPPING = HelperTool(
            "einkauf", "Einkaufsliste", "🛒",
            "Eine Einkaufsliste für den Alltag – mit Vorschlägen, was im Pflegealltag oft gebraucht wird.",
            listOf(
                Field("fehlt", "Was fehlt sicher?", "text", hint = "z. B. Milch, Brot, Toilettenpapier"),
                Field("foto", "Oder Foto vom Vorrat/Kühlschrank", "foto"),
                Field("anlass", "Für", "auswahl", listOf("Wocheneinkauf", "Drogerie", "Apotheke/Sanitätshaus", "Kleiner Einkauf")),
            ),
            "Erstelle eine sinnvolle Einkaufsliste: zuerst das Genannte, dann wenige passende Ergänzungen für den Haushalt und den Pflegealltag " +
                "(z. B. Pflegeprodukte, Getränke, leicht zu essende Lebensmittel), passend zu den Hinweisen je Person. Jeder Punkt kurz mit Menge.",
            "checkliste", setOf("haushalt", "ernaehrung", "vorrat"), builtin = true,
        )

        val BUILTIN = listOf(COOK, SHOPPING)

        /** Grundgerüst für ein neues Werkzeug ohne KI: ein Textfeld und die Bitte als Anweisung. */
        fun basic(id: String, topic: String) = HelperTool(
            id, topic.trim().replaceFirstChar { it.uppercase() }.take(40), "🧩",
            "Eigenes Werkzeug: $topic",
            listOf(Field("frage", "Worum geht es genau?", "text")),
            "Hilf pflegenden Angehörigen im Alltag beim Thema „$topic“. Gib konkrete, kurze, alltagstaugliche Vorschläge.",
            "liste", setOf("haushalt"),
        )

        /** Gemeinsame Regeln für alle Werkzeuge (Antwortform zum Anzeigen und Vorlesen). */
        const val SYSTEM =
            "Du bist Sunny, Alltagshelferin in der App „Mein Helfer für den Tag“ für pflegende Angehörige. Personen heißen „Person A, B …“, Namen kennst du nicht. " +
                "Antworte auf Deutsch, Du-Form, freundlich und praktisch. Keine Diagnosen, keine Dosierungen, keine medizinischen oder rechtlichen Ratschläge. " +
                "Antworte nur als JSON: {\"kurz\": \"ein bis zwei Sätze zum Vorlesen, natürliche gesprochene Sprache\", " +
                "\"punkte\": [{\"titel\": \"...\", \"text\": \"...\"}], \"hinweis\": \"optional ein kurzer Hinweis\"}."
    }
}

/** Anfrage-Text für ein Werkzeug zusammenbauen (ohne Namen). */
object HelperPrompt {
    fun build(
        tool: HelperTool,
        values: Map<String, String>,
        people: List<Presence.Who>,
        home: List<Presence.Who>?,
        whenText: String,
        extra: Map<String, String> = emptyMap(),
    ): String = buildString {
        appendLine("Werkzeug: ${tool.title}")
        appendLine("Aufgabe: ${tool.instruction}")
        if (tool.output == "checkliste") appendLine("Die Punkte sind Einträge einer Einkaufs- bzw. Checkliste (titel = Artikel mit Menge, text = optional kurz warum).")
        tool.fields.filter { it.type != "foto" }.forEach { f ->
            values[f.key]?.takeIf { it.isNotBlank() }?.let { appendLine("${f.label}: $it") }
        }
        if (values["foto"] == "1") appendLine("Ein Foto ist angehängt: erkenne, was zu sehen ist, und nutze es.")
        if (whenText.isNotBlank()) appendLine("Zeitpunkt: $whenText")
        val index = people.withIndex().associate { (i, p) -> p.id to Presence.label(i) }
        if ("anwesend" in tool.context && home != null) {
            appendLine("Zu Hause zu dieser Zeit: " + if (home.isEmpty()) "keine der betreuten Personen" else home.joinToString(", ") { index[it.id] ?: "?" } +
                (people.size - home.size).takeIf { it > 0 }?.let { " ($it weitere sind unterwegs)" }.orEmpty())
        }
        if ("ernaehrung" in tool.context) {
            val relevant = if ("anwesend" in tool.context && home != null) home else people
            relevant.forEach { p ->
                val n = p.nutrition.aiText()
                val age = p.age?.let { "etwa $it Jahre" }
                val line = listOfNotNull(age, n.ifBlank { null }).joinToString("; ")
                if (line.isNotBlank()) appendLine("${index[p.id]}: $line")
            }
        }
        if ("haushalt" in tool.context) {
            val h = people.firstOrNull()?.household
            if (h != null && h.others > 0) appendLine("Weitere im Haushalt: ${h.others} (unter 6: ${h.under6}, 6–17: ${h.age6to17}, 18–64: ${h.age18to64}, ab 65: ${h.age65plus})")
            appendLine("Betreute Personen: ${people.size}")
        }
        extra.forEach { (k, v) -> if (v.isNotBlank()) appendLine("$k: $v") }
    }.trim()

    data class Result(val short: String, val items: List<Pair<String, String>>, val note: String)

    private const val STR = "\"((?:[^\"\\\\]|\\\\.)*)\""
    private fun un(s: String) = s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").trim()

    /** Antwort lesen (JSON); sonst Text Zeile für Zeile. */
    fun parse(answer: String): Result {
        val text = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val short = Regex("\"kurz\"\\s*:\\s*$STR").find(text)?.groupValues?.get(1)?.let(::un)
        val note = Regex("\"hinweis\"\\s*:\\s*$STR").find(text)?.groupValues?.get(1)?.let(::un).orEmpty()
        val items = Regex("\\{\\s*\"titel\"\\s*:\\s*$STR\\s*(?:,\\s*\"text\"\\s*:\\s*$STR)?\\s*}").findAll(text)
            .map { un(it.groupValues[1]) to un(it.groupValues[2]) }.toList()
        if (short != null || items.isNotEmpty()) return Result(short ?: items.firstOrNull()?.first.orEmpty(), items, note)
        val lines = text.lines().map { it.trim().removePrefix("- ").removePrefix("* ") }.filter { it.isNotBlank() }
        return Result(lines.firstOrNull().orEmpty(), lines.drop(1).map { it to "" }, "")
    }
}
