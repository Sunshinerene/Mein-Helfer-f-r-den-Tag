package de.meinhelfer.tag.summary

/**
 * Antworten der KI für Sprach-Themen: eine Kurzform zum Sprechen, eine ausführliche Fassung und Varianten.
 * Mit den Varianten kann Sunny dasselbe öfter sagen, ohne neu zu fragen (spart freies Kontingent).
 */
object SpeechVariants {
    data class Parsed(val short: String, val long: String, val variants: List<String>) {
        /** Alle Kurzformen (erste zuerst). */
        val shorts get() = (listOf(short) + variants).filter { it.isNotBlank() }.distinct()
    }

    /** Anweisung an die KI, wie die Antwort aussehen soll. */
    const val FORMAT =
        "Antworte nur als JSON ohne weiteren Text: {\"kurz\": \"ein kurzer Satz zum Vorlesen\", " +
            "\"lang\": \"zwei bis vier Sätze ausführlicher\", \"varianten\": [\"die Kurzform anders formuliert\", \"noch eine andere Formulierung\"]}."

    private const val STR = "\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\""

    private fun field(json: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*$STR", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.get(1)?.let(::unescape)

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\").trim()

    /** JSON der KI lesen; ist es kein JSON, wird der Text selbst genommen (erster Satz als Kurzform). */
    fun parse(answer: String): Parsed {
        val text = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val short = field(text, "kurz")
        val long = field(text, "lang")
        val list = Regex("\"varianten\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
            ?.let { inner -> Regex(STR).findAll(inner).map { unescape(it.groupValues[1]) }.filter { it.isNotBlank() }.toList() }
            .orEmpty()
        if (short != null || long != null) {
            val s = short ?: firstSentence(long!!)
            return Parsed(s, long ?: s, list)
        }
        val plain = text.lines().joinToString(" ") { it.trim().removePrefix("- ") }.trim()
        return Parsed(firstSentence(plain), plain, emptyList())
    }

    fun firstSentence(text: String): String =
        Regex("^.+?[.!?](?=\\s|$)").find(text.trim())?.value ?: text.trim()

    /** Was Sunny sagt: Kurzform (reihum aus den Varianten, [turn] zählt hoch) oder die ausführliche Fassung. */
    fun pick(p: Parsed, turn: Int, long: Boolean): String =
        if (long) p.long else p.shorts.let { it[Math.floorMod(turn, it.size)] }
}
