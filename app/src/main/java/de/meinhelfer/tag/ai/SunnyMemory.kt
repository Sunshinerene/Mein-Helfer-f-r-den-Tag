package de.meinhelfer.tag.ai

import android.content.Context
import de.meinhelfer.tag.summary.Phrasebook
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime

/**
 * Sunnys Gedächtnis für Formulierungen: Was sie heute gesagt hat (nur für einen Tag, nur auf dem Handy),
 * und der Sprachschatz, der abends daraus wächst (Stufe 3 mit KI, ohne Namen und Daten).
 */
object SunnyMemory {
    private fun prefs(context: Context) = context.getSharedPreferences("sunny_gedaechtnis", Context.MODE_PRIVATE)

    @Volatile
    private var book: Phrasebook? = null
    private var turn = (System.currentTimeMillis() / 1000 % 97).toInt()

    fun book(context: Context): Phrasebook =
        book ?: Phrasebook.decode(prefs(context).getString("sprachschatz", "")!!).also { book = it }

    private fun saveBook(context: Context) {
        prefs(context).edit().putString("sprachschatz", book(context).encode()).apply()
    }

    /** Formulierung zum Thema (reihum), mit Name und Anzahl. */
    fun say(context: Context, theme: String, default: String, n: Int? = null): String =
        book(context).pick(theme, default, turn++, de.meinhelfer.tag.update.AppPrefs.assistantName(context), n)

    /** Was Sunny gesagt hat, für den heutigen Tag merken. */
    fun log(context: Context, owner: String, text: String) {
        // Spoken care content must not be retained for vocabulary training.
        prefs(context).edit().remove("heute").remove("tag").apply()
    }

    fun todayCount(context: Context): Int = runCatching {
        val p = prefs(context)
        if (p.getString("tag", "") != LocalDate.now().toString()) 0 else JSONArray(p.getString("heute", "[]")).length()
    }.getOrDefault(0)

    fun phraseCount(context: Context): Int = book(context).themes().values.sumOf { it.size }

    fun lastLearned(context: Context): String = prefs(context).getString("gelernt_info", "")!!

    private const val SYSTEM =
        "Du pflegst den Sprachschatz der Assistentin „Sunny“ in einer App für pflegende Angehörige. Ihre Sätze werden von einer Sprachausgabe vorgelesen. " +
            "Du bekommst, was Sunny heute gesagt hat, und je Thema die Formulierungen, die sie schon hat. " +
            "Finde Formulierungen, die wiederkehren oder gut passen, und schreibe je Thema bis zu drei NEUE Varianten mit gleichem Sinn, die es noch nicht gibt. " +
            "Natürliche gesprochene Sprache, freundlich, Du-Form, kurze Sätze, keine Abkürzungen, keine Klammern, keine Emojis. " +
            "Keine Namen, keine Zahlen oder Uhrzeiten, keine Medikamente, keine Gesundheitsangaben, keine Termine – nur allgemein brauchbare Sätze. " +
            "Platzhalter {name} (Name der Assistentin) und {n} (Anzahl) genau so übernehmen, wo das Thema sie hat. " +
            "Antworte nur als JSON: {\"themen\": [{\"thema\": \"schluessel\", \"saetze\": [\"...\"]}]}. Verwende nur die vorgegebenen Themen-Schlüssel."

    /**
     * Abends (ab 19 Uhr) oder am nächsten Tag: Sprachschatz aus den Gesprächen des Tages erweitern.
     * Nur in Stufe 3 mit KI; sonst wird nur das Tagesprotokoll geleert. Gibt eine kurze Meldung zurück oder null.
     */
    suspend fun consolidateIfDue(context: Context): String? {
        val p = prefs(context)
        val logDay = p.getString("tag", "") ?: ""
        if (logDay.isBlank()) return null
        val today = LocalDate.now().toString()
        val due = (logDay == today && LocalTime.now().hour >= 19 && p.getString("gelernt_am", "") != today) || logDay < today
        if (!due) return null
        // Never send historical spoken content to a learning provider.
        p.edit().remove("heute").remove("tag").apply()
        val said = emptyList<String>()
        fun finish(msg: String?): String? {
            p.edit().putString("gelernt_am", today).apply {
                if (logDay < today) remove("heute").remove("tag")
            }.apply {
                if (msg != null) putString("gelernt_info", "$today: $msg")
            }.apply()
            return msg
        }
        if (said.size < 3 || !AiMode.skipConfirm(context) || !AiConfig.isConfigured(context) || !AiTask.enabled(context, AiTask.Lernen)) return finish(null)
        val b = book(context)
        val prompt = buildString {
            appendLine("Themen (Schlüssel: Bedeutung) mit vorhandenen Formulierungen:")
            Phrasebook.THEMES.forEach { (k, v) ->
                appendLine("- $k: ${v.first}")
                b.themes()[k].orEmpty().forEach { appendLine("    • $it") }
            }
            appendLine()
            appendLine("Was Sunny heute gesagt hat:")
            said.take(80).forEach { appendLine("- " + it.take(300)) }
        }
        val answer = AiClient.ask(context, SYSTEM, prompt, 1500, AiTask.Lernen, cache = false).getOrNull() ?: return null
        var added = 0
        Regex("\\{\\s*\"thema\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"saetze\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).findAll(answer).forEach { m ->
            val theme = m.groupValues[1].trim()
            if (theme !in Phrasebook.THEMES) return@forEach
            val phrases = Regex("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").findAll(m.groupValues[2]).map { it.groupValues[1].replace("\\\"", "\"") }.toList()
            added += b.merge(theme, phrases)
        }
        saveBook(context)
        return finish(if (added > 0) "$added neue Formulierungen gelernt" else "nichts Neues")
    }

    /** Sprachschatz als Text (eine Zeile je Formulierung: Thema, Tab, Satz) – für Sicherung und Teilen. */
    fun exportText(context: Context): String = "# Sprachschatz von Sunny (Mein Helfer für den Tag)\n" + book(context).encode()

    /** Sprachschatz einlesen; [replace] = statt Ergänzen ersetzen. Gibt die Zahl der Formulierungen zurück. */
    fun importText(context: Context, text: String, replace: Boolean): Int {
        val incoming = Phrasebook.decode(text.lines().filterNot { it.startsWith("#") }.joinToString("\n"))
        val target = if (replace) incoming else book(context).also { b -> incoming.themes().forEach { (t, l) -> b.merge(t, l) } }
        book = target
        check(prefs(context).edit().putString("sprachschatz", target.encode()).commit()) { "Sprachschatz nicht gespeichert" }
        return target.themes().values.sumOf { it.size }
    }

    /** Sprachschatz auf den Grundbestand zurücksetzen. */
    fun reset(context: Context) {
        book = Phrasebook.withDefaults()
        prefs(context).edit().remove("sprachschatz").remove("gelernt_info").remove("heute").remove("tag").remove("gelernt_am").apply()
    }
}
