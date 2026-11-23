package de.meinhelfer.tag.ai

import android.content.Context
import java.time.LocalDate

/**
 * Wofür Sunny KI nutzt. Jede Aufgabe lässt sich einzeln abschalten und einem eigenen KI-Dienst zuordnen,
 * so verteilen sich die Anfragen auf mehrere freie Kontingente.
 */
enum class AiTask(val key: String, val title: String, val hint: String) {
    Seite("seite", "Kommentar zur Seite", "„Was meinst du zu dieser Seite?“ und automatisch beim Seitenwechsel (Stufe 3). Kurze Anfragen, aber am häufigsten."),
    Frage("frage", "Frag mich", "Fragen zur App und zur Pflege frei beantworten (nur Frage und Anleitung, keine Daten)."),
    Dokument("dokument", "Dokumente", "Zusammenfassung und Aufgaben aus einem Dokument. Lange Texte, eher selten."),
    Medikamente("medikamente", "Medikamente", "Medikamenten-Check, Bewertung und Hinweise zu Wechselwirkungen."),
    Plan("plan", "Pläne und Berichte lesen", "Medikationsplan und Entlassbericht auslesen. Lange Texte, selten."),
    Ziele("ziele", "Ziele und Motivation", "Ziele aufteilen, Motivation und Tipps."),
    Helfer("helfer", "Alltagshelfer (Beta)", "Kochen, Einkaufsliste und eigene kleine Werkzeuge. Personen als „Person A, B …“, ohne Namen; Fotos nur nach deinem Tipp."),
    Lernen("lernen", "Sprachschatz abends erweitern", "Einmal am Abend: aus dem, was Sunny heute gesagt hat, neue Formulierungen mit gleichem Sinn (ohne Namen und Daten). Stufe 3."),
    Allgemein("allgemein", "Sonstiges", "Alles andere, z. B. der Verbindungstest."),
    ;

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("ki_aufgaben", Context.MODE_PRIVATE)

        fun enabled(context: Context, t: AiTask) = t == Allgemein || prefs(context).getBoolean("an_${t.key}", true)

        fun setEnabled(context: Context, t: AiTask, on: Boolean) = prefs(context).edit().putBoolean("an_${t.key}", on).apply()

        /** Eigener Dienst für die Aufgabe, null = Standard-Dienst. */
        fun provider(context: Context, t: AiTask): AiProvider? =
            prefs(context).getString("dienst_${t.key}", null)?.let { k -> AiProvider.entries.firstOrNull { it.name == k } }

        fun setProvider(context: Context, t: AiTask, p: AiProvider?) =
            prefs(context).edit().putString("dienst_${t.key}", p?.name).apply()

        /** Bei Limit automatisch auf einen anderen eingerichteten Dienst ausweichen. */
        fun fallback(context: Context) = prefs(context).getBoolean("ausweichen", true)

        fun setFallback(context: Context, on: Boolean) = prefs(context).edit().putBoolean("ausweichen", on).apply()

        /** Sunny spricht bei Sprach-Themen die ausführliche statt der kurzen Fassung. */
        fun speakLong(context: Context) = prefs(context).getBoolean("lang_sprechen", false)

        fun setSpeakLong(context: Context, on: Boolean) = prefs(context).edit().putBoolean("lang_sprechen", on).apply()

        /** Tageslimit für automatische Kommentare zur Seite (Schonung des Kontingents). */
        fun autoLimit(context: Context) = prefs(context).getInt("auto_limit", 20)

        fun setAutoLimit(context: Context, n: Int) = prefs(context).edit().putInt("auto_limit", n).apply()
    }
}

/** Zählt KI-Anfragen je Tag – je Dienst und je Aufgabe. Nur auf dem Handy. */
object AiUsage {
    private fun prefs(context: Context) = context.getSharedPreferences("ki_verbrauch", Context.MODE_PRIVATE)

    private fun today() = LocalDate.now().toString()

    private fun clean(context: Context) {
        val p = prefs(context)
        if (p.getString("tag", "") != today()) p.edit().clear().putString("tag", today()).apply()
    }

    fun count(context: Context, provider: AiProvider, task: AiTask, ok: Boolean) {
        clean(context)
        val p = prefs(context)
        p.edit()
            .putInt("d_${provider.name}", p.getInt("d_${provider.name}", 0) + 1)
            .putInt("a_${task.key}", p.getInt("a_${task.key}", 0) + 1)
            .apply {
                if (!ok) putInt("f_${provider.name}", p.getInt("f_${provider.name}", 0) + 1)
            }
            .apply()
    }

    fun limitHit(context: Context, provider: AiProvider) {
        clean(context)
        prefs(context).edit().putBoolean("l_${provider.name}", true).apply()
    }

    fun byProvider(context: Context): Map<AiProvider, Int> {
        clean(context)
        val p = prefs(context)
        return AiProvider.entries.associateWith { p.getInt("d_${it.name}", 0) }.filterValues { it > 0 }
    }

    fun limited(context: Context, provider: AiProvider): Boolean {
        clean(context)
        return prefs(context).getBoolean("l_${provider.name}", false)
    }

    /** Aus dem Zwischenspeicher beantwortet (keine Anfrage nötig). */
    fun saved(context: Context) {
        clean(context)
        val p = prefs(context)
        p.edit().putInt("gespart", p.getInt("gespart", 0) + 1).apply()
    }

    fun savedToday(context: Context): Int {
        clean(context)
        return prefs(context).getInt("gespart", 0)
    }

    fun byTask(context: Context, task: AiTask): Int {
        clean(context)
        return prefs(context).getInt("a_${task.key}", 0)
    }
}
