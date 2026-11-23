package de.meinhelfer.tag.ai

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Zwischenspeicher für KI-Antworten. Gleiche Anfrage (gleiche Aufgabe, gleicher Text) → Antwort aus dem Speicher,
 * ohne das freie Kontingent zu belasten. Ändern sich die Daten, ändert sich der Text und es wird neu gefragt.
 * Nach einem Neustart der App gilt alles als veraltet und wird bei Bedarf aktualisiert.
 */
object AiCache {
    private const val MAX = 300
    private fun prefs(context: Context) = context.getSharedPreferences("ki_zwischenspeicher", Context.MODE_PRIVATE)

    /** Bei jedem Start der App aufrufen: neue Sitzung, alte Antworten gelten als veraltet. */
    fun newSession(context: Context) {
        val p = prefs(context)
        p.edit().putInt("sitzung", p.getInt("sitzung", 0) + 1).apply()
    }

    private fun session(context: Context) = prefs(context).getInt("sitzung", 0)

    fun key(task: AiTask, system: String, user: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((system + "\n" + user).toByteArray())
        return task.key + "_" + d.take(12).joinToString("") { "%02x".format(it) }
    }

    /** Gültige Antwort aus dieser Sitzung, sonst null. */
    fun get(context: Context, key: String): String? = runCatching {
        val o = JSONObject(prefs(context).getString("e_$key", null) ?: return null)
        if (o.getInt("s") != session(context)) null else o.getString("a")
    }.getOrNull()

    /** Letzte Antwort, auch aus früheren Sitzungen (z. B. ohne Verbindung). */
    fun any(context: Context, key: String): String? = runCatching {
        JSONObject(prefs(context).getString("e_$key", null) ?: return null).getString("a")
    }.getOrNull()

    fun put(context: Context, key: String, answer: String) {
        val p = prefs(context)
        val e = p.edit().putString("e_$key", JSONObject().put("s", session(context)).put("t", System.currentTimeMillis()).put("a", answer).toString())
        // Älteste entfernen, wenn zu viele
        val entries = p.all.keys.filter { it.startsWith("e_") }
        if (entries.size > MAX) {
            entries.mapNotNull { k -> runCatching { k to JSONObject(p.getString(k, "{}")!!).optLong("t") }.getOrNull() }
                .sortedBy { it.second }.take(entries.size - MAX + 20).forEach { e.remove(it.first) }
        }
        e.apply()
    }

    fun count(context: Context) = prefs(context).all.keys.count { it.startsWith("e_") }

    fun clear(context: Context) {
        val s = session(context)
        prefs(context).edit().clear().putInt("sitzung", s).apply()
    }
}
