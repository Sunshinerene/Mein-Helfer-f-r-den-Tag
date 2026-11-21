package de.meinhelfer.tag.ai

import org.json.JSONObject

/** Grobe KI-Einschaetzung zu einem Medikament. Nur ein Anhaltspunkt, kein Urteil. Werte 1 bis 5, 0 = nicht beurteilt. */
data class MedRating(
    val strength: Int,
    val sideEffects: Int,
    val interactions: Int,
    val notes: List<String>,
    /** Epoch-Tag der Einschaetzung */
    val day: Long,
    val provider: String,
) {
    fun toJson(): String = JSONObject()
        .put("s", strength).put("n", sideEffects).put("w", interactions)
        .put("h", org.json.JSONArray(notes)).put("d", day).put("p", provider)
        .toString()

    companion object {
        const val SYSTEM_PROMPT =
            "Du unterstützt pflegende Angehörige. Du bist kein Arzt und kein Apotheker und ersetzt beide nie. " +
                "Gib nur eine grobe Orientierung, keine Diagnose und keine Dosierung. " +
                "Antworte ausschließlich mit einem JSON-Objekt, ohne Text davor oder danach."

        fun prompt(medName: String, others: List<String>): String {
            val otherText = if (others.isEmpty()) "keine weiteren" else others.joinToString(", ")
            return "Medikament: $medName\n" +
                "Weitere Medikamente derselben Person: $otherText\n\n" +
                "Gib eine grobe Orientierung als JSON mit genau diesen Feldern:\n" +
                "{\"wirkstaerke\": 1 bis 5 (wie stark wirksam bzw. eingreifend das Mittel allgemein ist), " +
                "\"nebenwirkungen\": 1 bis 5 (wie viele bzw. wie häufige Nebenwirkungen allgemein bekannt sind), " +
                "\"wechselwirkungen\": 0 bis 5 (mögliche Probleme mit den weiteren Medikamenten, 0 wenn es keine weiteren gibt), " +
                "\"hinweise\": [höchstens 4 kurze deutsche Stichpunkte, worauf man achten oder was man Arzt oder Apotheke fragen könnte]}"
        }

        fun parse(text: String, day: Long, provider: String): MedRating? = runCatching {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            val json = JSONObject(text.substring(start, end + 1))
            val notes = mutableListOf<String>()
            json.optJSONArray("hinweise")?.let { for (i in 0 until it.length()) notes.add(it.optString(i).trim()) }
            MedRating(
                strength = json.optInt("wirkstaerke", 0).coerceIn(0, 5),
                sideEffects = json.optInt("nebenwirkungen", 0).coerceIn(0, 5),
                interactions = json.optInt("wechselwirkungen", 0).coerceIn(0, 5),
                notes = notes.filter { it.isNotBlank() }.take(4),
                day = day,
                provider = provider,
            )
        }.getOrNull()?.takeIf { it.strength > 0 || it.sideEffects > 0 }

        fun fromStored(stored: String): MedRating? = runCatching {
            val json = JSONObject(stored)
            val notes = mutableListOf<String>()
            json.optJSONArray("h")?.let { for (i in 0 until it.length()) notes.add(it.optString(i)) }
            MedRating(json.getInt("s"), json.getInt("n"), json.getInt("w"), notes, json.optLong("d"), json.optString("p"))
        }.getOrNull()
    }
}
