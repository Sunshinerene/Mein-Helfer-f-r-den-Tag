package de.meinhelfer.tag.ai

import de.meinhelfer.tag.summary.FollowUp
import de.meinhelfer.tag.summary.MedPlan
import org.json.JSONObject
import java.time.LocalDate

/** KI liest einen Entlassbericht: Medikation bei Entlassung und empfohlene Kontrollen (Name und Nummern der Person maskiert). */
object DischargeAi {
    const val SYSTEM_PROMPT =
        "Du liest Entlassberichte aus dem Krankenhaus (erkannter Text, OCR) für pflegende Angehörige ab. " +
            "Übernimm nur, was im Text steht; erfinde nichts, ergänze keine Dosierungen, stelle keine Diagnosen und gib keine eigenen Empfehlungen. " +
            "Antworte ausschließlich mit einem JSON-Objekt, ohne Text davor oder danach."

    fun prompt(maskedText: String): String = buildString {
        appendLine("Erkannter Text eines Entlassberichts (Namen und Nummern der Person sind ersetzt):")
        appendLine("\"\"\"")
        appendLine(maskedText.take(12_000))
        appendLine("\"\"\"")
        appendLine()
        appendLine("1. Lies nur die Medikation bei Entlassung ab (nicht die Medikation bei Aufnahme, nicht abgesetzte Mittel).")
        appendLine("2. Lies die empfohlenen Kontrollen, Wiedervorstellungen und Termine nach der Entlassung ab (nicht Untersuchungen während des Aufenthalts).")
        appendLine("Antworte als JSON mit genau diesen Feldern:")
        append("{\"entlassung\": Entlassungsdatum als TT.MM.JJJJ oder leer, ")
        append("\"medikamente\": [{\"name\": Handelsname oder Wirkstoff, \"staerke\": z. B. 5 mg oder leer, \"form\": z. B. Tablette oder leer, ")
        append("\"morgens\": Menge oder leer, \"mittags\": Menge oder leer, \"abends\": Menge oder leer, \"nachts\": Menge oder leer, ")
        append("\"freitext\": Dosierung als Text, wenn es kein Schema gibt, sonst leer, \"einheit\": z. B. Stück oder leer, \"hinweis\": kurzer Hinweis aus dem Text oder leer, \"grund\": leer}], ")
        append("\"abgesetzt\": [Namen der laut Bericht abgesetzten oder pausierten Mittel], ")
        append("\"kontrollen\": [{\"was\": kurz, z. B. Laborkontrolle Kalium oder Fäden ziehen, \"stelle\": z. B. Hausarzt, Kardiologe, Klinik-Ambulanz oder leer, ")
        append("\"datum\": TT.MM.JJJJ, wenn ein Datum genannt ist, sonst leer, \"uhrzeit\": HH:MM, wenn genannt, sonst leer, ")
        append("\"in_tagen\": Zahl der Tage nach der Entlassung, wenn nur ein Zeitraum genannt ist (z. B. in 2 Wochen = 14), sonst null, ")
        append("\"satz\": der Satz aus dem Text, gekürzt}]}. ")
        append("Mengen als Zahl wie im Text (z. B. 1, 0,5, ½); steht 0, gib 0 an.")
    }

    private val DATE = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")

    private fun day(s: String): LocalDate? = DATE.find(s)?.let { r ->
        runCatching { LocalDate.of(r.groupValues[3].toInt(), r.groupValues[2].toInt(), r.groupValues[1].toInt()) }.getOrNull()
    }

    /** Medikamente (wie beim Medikamentenplan), Kontrollen und abgesetzte Mittel. [fallbackBase] für „in_tagen“ ohne Entlassungsdatum. */
    fun parse(text: String, fallbackBase: LocalDate): Triple<MedPlan?, List<FollowUp>, List<String>>? = runCatching {
        val json = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
        val o = JSONObject(json)
        val discharge = day(o.optString("entlassung"))
        val base = discharge ?: fallbackBase
        val plan = MedPlanAi.parse(json)?.let { it.copy(day = discharge?.toEpochDay(), source = "KI") }
        val follow = mutableListOf<FollowUp>()
        o.optJSONArray("kontrollen")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 8)) {
                val k = arr.optJSONObject(i) ?: continue
                fun s(key: String) = k.optString(key).trim().takeUnless { it == "null" }.orEmpty()
                val what = s("was").take(80)
                if (what.isBlank()) continue
                val place = s("stelle").take(40)
                val exact = day(s("datum"))
                val inDays = if (k.isNull("in_tagen")) null else k.optInt("in_tagen", -1).takeIf { it in 0..730 }
                val time = Regex("""(\d{1,2})[:.](\d{2})""").find(s("uhrzeit"))?.let { m ->
                    val h = m.groupValues[1].toInt()
                    val min = m.groupValues[2].toInt()
                    if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
                }
                follow += FollowUp(
                    title = if (place.isNotBlank()) "$what – $place" else what,
                    sentence = s("satz").take(260),
                    day = (exact ?: inDays?.let { base.plusDays(it.toLong()) })?.toEpochDay(),
                    exact = exact != null,
                    time = if (exact != null) time else null,
                    place = place,
                )
            }
        }
        val stopped = o.optJSONArray("abgesetzt")?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() && it != "null" } }.orEmpty()
        if (plan == null && follow.isEmpty()) null else Triple(plan, follow, stopped.take(8))
    }.getOrNull()
}
