package de.meinhelfer.tag.ai

import de.meinhelfer.tag.summary.MedPlan
import de.meinhelfer.tag.summary.PlanMed
import org.json.JSONObject
import java.time.LocalDate

/** KI liest einen Medikamentenplan aus dem erkannten Text eines Dokuments (Name und Nummern der Person maskiert). */
object MedPlanAi {
    const val SYSTEM_PROMPT =
        "Du liest Medikamentenpläne aus erkanntem Text (OCR) ab, z. B. den bundeseinheitlichen Medikationsplan oder einen Entlassbrief. " +
            "Übernimm nur, was im Text steht; erfinde nichts, ergänze keine Dosierungen und gib keine Empfehlungen. " +
            "Antworte ausschließlich mit einem JSON-Objekt, ohne Text davor oder danach."

    fun prompt(maskedText: String): String = buildString {
        appendLine("Erkannter Text eines Dokuments (Namen und Nummern der Person sind ersetzt):")
        appendLine("\"\"\"")
        appendLine(maskedText.take(10_000))
        appendLine("\"\"\"")
        appendLine()
        appendLine("Lies alle Medikamente ab. Antworte als JSON mit genau diesen Feldern:")
        append("{\"stand\": Datum des Plans als TT.MM.JJJJ oder leer, \"medikamente\": [{\"name\": Handelsname oder Wirkstoff, \"staerke\": z. B. 47,5 mg oder leer, ")
        append("\"form\": z. B. Tablette oder leer, \"morgens\": Menge oder leer, \"mittags\": Menge oder leer, \"abends\": Menge oder leer, \"nachts\": Menge oder leer, ")
        append("\"freitext\": Dosierung als Text, wenn es kein Schema gibt (z. B. bei Bedarf), sonst leer, \"einheit\": z. B. Stück, Tropfen, Hub oder leer, ")
        append("\"hinweis\": kurzer Hinweis aus dem Plan oder leer, \"grund\": Behandlungsgrund aus dem Plan oder leer}]}. ")
        append("Mengen als Zahl wie im Plan (z. B. 1, 0,5, ½); steht 0, gib 0 an.")
    }

    fun parse(text: String): MedPlan? = runCatching {
        val o = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        val arr = o.optJSONArray("medikamente") ?: return null
        val meds = (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = m.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            fun s(k: String) = m.optString(k).trim().takeUnless { it == "null" }.orEmpty()
            PlanMed(
                name = name.take(80),
                strength = s("staerke"),
                form = s("form"),
                amounts = listOf(s("morgens"), s("mittags"), s("abends"), s("nachts")),
                freeSchema = s("freitext"),
                unit = s("einheit"),
                notes = s("hinweis"),
                reason = s("grund"),
            )
        }
        val day = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(o.optString("stand"))?.let { r ->
            runCatching { LocalDate.of(r.groupValues[3].toInt(), r.groupValues[2].toInt(), r.groupValues[1].toInt()).toEpochDay() }.getOrNull()
        }
        if (meds.isEmpty()) null else MedPlan(day, meds, "KI")
    }.getOrNull()
}
