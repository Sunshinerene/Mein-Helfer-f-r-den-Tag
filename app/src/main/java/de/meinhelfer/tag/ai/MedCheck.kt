package de.meinhelfer.tag.ai

import de.meinhelfer.tag.data.Medication
import org.json.JSONArray
import org.json.JSONObject

/** Einschaetzung eines einzelnen Medikaments innerhalb des Gesamt-Checks. */
data class MedCheckItem(
    val name: String,
    val strength: Int,
    val sideEffects: Int,
    val interactions: Int,
    val notes: List<String>,
)

/** Ein Paar von Medikamenten, bei dem etwas zu beachten ist. Stufe 1 = gering, 2 = beachten, 3 = wichtig. */
data class MedCombo(val a: String, val b: String, val level: Int, val note: String)

/**
 * KI-Gesamt-Check des Medikamentenplans: alle Medikamente zusammen, Kombinationen, Einnahmezeiten,
 * Gesamtbild und Fragen an Arzt oder Apotheke. Nur ein Anhaltspunkt, kein Urteil.
 */
data class MedCheck(
    val items: List<MedCheckItem>,
    val combos: List<MedCombo>,
    val timing: List<String>,
    /** 1 bis 5: wie komplex bzw. belastend der Plan insgesamt ist */
    val overall: Int,
    val summary: String,
    val questions: List<String>,
    val day: Long,
    val provider: String,
    /** Fingerabdruck des Plans zur Zeit der Einschaetzung (erkennt spaetere Aenderungen) */
    val planKey: String,
) {
    fun toJson(): String = JSONObject()
        .put("items", JSONArray(items.map { JSONObject().put("n", it.name).put("s", it.strength).put("e", it.sideEffects).put("w", it.interactions).put("h", JSONArray(it.notes)) }))
        .put("combos", JSONArray(combos.map { JSONObject().put("a", it.a).put("b", it.b).put("l", it.level).put("t", it.note) }))
        .put("timing", JSONArray(timing))
        .put("overall", overall)
        .put("summary", summary)
        .put("questions", JSONArray(questions))
        .put("day", day)
        .put("provider", provider)
        .put("plan", planKey)
        .toString()

    companion object {
        const val SYSTEM_PROMPT =
            "Du unterstützt pflegende Angehörige. Du bist kein Arzt und kein Apotheker und ersetzt beide nie. " +
                "Gib nur eine grobe Orientierung, keine Diagnose, keine Dosierungsänderung und keine Empfehlung zum Absetzen. " +
                "Antworte ausschließlich mit einem JSON-Objekt, ohne Text davor oder danach."

        /** Fingerabdruck: Name, Dosierung und Zeiten aller Medikamente. */
        fun planKey(meds: List<Medication>): String =
            meds.map { "${it.name.trim().lowercase()}|${it.dosage.trim().lowercase()}|${it.timeList().sorted().joinToString(",")}" }
                .sorted().joinToString(";").hashCode().toString()

        fun prompt(meds: List<Medication>, age: Int?): String = buildString {
            appendLine("Medikamentenplan einer betreuten Person (Orientierung für pflegende Angehörige):")
            meds.forEachIndexed { i, m ->
                val parts = listOfNotNull(
                    m.dosage.takeIf { it.isNotBlank() }?.let { "Dosierung: $it" },
                    m.timeList().takeIf { it.isNotEmpty() }?.let { "Einnahme: ${it.joinToString(", ")} Uhr" },
                )
                appendLine("${i + 1}. ${m.name.trim()}" + if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else "")
            }
            if (age != null) appendLine("Alter: etwa $age Jahre")
            appendLine()
            appendLine("Gib eine grobe Orientierung als JSON mit genau diesen Feldern:")
            append("{\"medikamente\": [{\"name\": Name wie oben, \"wirkstaerke\": 1 bis 5, \"nebenwirkungen\": 1 bis 5, ")
            append("\"wechselwirkungen\": 0 bis 5 (mögliche Probleme mit den übrigen Medikamenten), \"hinweise\": [höchstens 3 kurze Stichpunkte]}], ")
            append("\"kombinationen\": [{\"a\": Name, \"b\": Name, \"stufe\": 1 (gering, nur beachten), 2 (beachten, ansprechen) oder 3 (wichtig, zeitnah mit Arzt oder Apotheke besprechen), \"hinweis\": ein kurzer Satz}] ")
            append("(nur Paare, bei denen etwas bekannt ist; leere Liste, wenn nichts bekannt), ")
            append("\"zeiten\": [höchstens 3 kurze Hinweise zu Abständen oder Einnahme, z. B. nüchtern oder nicht zusammen mit ...], ")
            append("\"gesamt\": 1 bis 5 (wie komplex bzw. belastend der Plan insgesamt ist), ")
            append("\"einschaetzung\": ein bis zwei Sätze zum Gesamtbild, ")
            append("\"fragen\": [höchstens 4 kurze Fragen, die man Arzt oder Apotheke stellen könnte]}")
        }

        private fun strings(arr: JSONArray?, max: Int): List<String> {
            if (arr == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { out += it }
            return out.take(max)
        }

        fun parse(text: String, day: Long, provider: String, planKey: String): MedCheck? = runCatching {
            val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
            val items = mutableListOf<MedCheckItem>()
            json.optJSONArray("medikamente")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    items += MedCheckItem(
                        name = o.optString("name").trim(),
                        strength = o.optInt("wirkstaerke", 0).coerceIn(0, 5),
                        sideEffects = o.optInt("nebenwirkungen", 0).coerceIn(0, 5),
                        interactions = o.optInt("wechselwirkungen", 0).coerceIn(0, 5),
                        notes = strings(o.optJSONArray("hinweise"), 3),
                    )
                }
            }
            val combos = mutableListOf<MedCombo>()
            json.optJSONArray("kombinationen")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val note = o.optString("hinweis").trim()
                    if (note.isBlank()) continue
                    combos += MedCombo(o.optString("a").trim(), o.optString("b").trim(), o.optInt("stufe", 1).coerceIn(1, 3), note)
                }
            }
            MedCheck(
                items = items,
                combos = combos.sortedByDescending { it.level }.take(12),
                timing = strings(json.optJSONArray("zeiten"), 3),
                overall = json.optInt("gesamt", 0).coerceIn(0, 5),
                summary = json.optString("einschaetzung").trim(),
                questions = strings(json.optJSONArray("fragen"), 4),
                day = day,
                provider = provider,
                planKey = planKey,
            )
        }.getOrNull()?.takeIf { it.items.isNotEmpty() || it.summary.isNotBlank() }

        fun fromStored(stored: String): MedCheck? = runCatching {
            if (stored.isBlank()) return null
            val json = JSONObject(stored)
            fun list(a: JSONArray?): List<String> = a?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
            val items = json.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { o -> MedCheckItem(o.optString("n"), o.optInt("s"), o.optInt("e"), o.optInt("w"), list(o.optJSONArray("h"))) }
                }
            }.orEmpty()
            val combos = json.optJSONArray("combos")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { o -> MedCombo(o.optString("a"), o.optString("b"), o.optInt("l", 1), o.optString("t")) }
                }
            }.orEmpty()
            MedCheck(
                items, combos, list(json.optJSONArray("timing")), json.optInt("overall"), json.optString("summary"),
                list(json.optJSONArray("questions")), json.optLong("day"), json.optString("provider"), json.optString("plan"),
            )
        }.getOrNull()
    }
}
