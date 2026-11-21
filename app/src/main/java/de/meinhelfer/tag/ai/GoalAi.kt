package de.meinhelfer.tag.ai

import de.meinhelfer.tag.data.Goal
import de.meinhelfer.tag.data.GoalKind
import de.meinhelfer.tag.data.GoalStep
import de.meinhelfer.tag.summary.Motivator
import de.meinhelfer.tag.summary.MotivatorPlan
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ein von der KI vorgeschlagener Schritt; [days] = bis wann, ab heute gerechnet. */
data class SuggestedStep(val title: String, val detail: String, val days: Int?)

/** Vorschlag fuer den Weg zu einem Ziel. */
data class GoalPath(val steps: List<SuggestedStep>, val assessment: String, val tips: List<String>)

/** KI-Einschaetzung, wie der Weg zum Ziel laeuft. Wird beim Ziel gespeichert. */
data class GoalCheck(
    /** 1 = stockt, 5 = sehr gut im Plan */
    val level: Int,
    val assessment: String,
    val next: String,
    val tips: List<String>,
    val newSteps: List<SuggestedStep>,
    val day: Long,
    val provider: String,
) {
    fun toJson(): String = JSONObject()
        .put("l", level).put("a", assessment).put("n", next).put("t", JSONArray(tips))
        .put("s", JSONArray(newSteps.map { JSONObject().put("t", it.title).put("d", it.detail) }))
        .put("day", day).put("p", provider)
        .toString()

    companion object {
        fun fromJson(text: String): GoalCheck? = if (text.isBlank()) null else runCatching {
            val o = JSONObject(text)
            GoalCheck(
                level = o.optInt("l"),
                assessment = o.optString("a"),
                next = o.optString("n"),
                tips = GoalAi.strings(o.optJSONArray("t"), 3),
                newSteps = o.optJSONArray("s")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { SuggestedStep(it.optString("t"), it.optString("d"), null) } }
                } ?: emptyList(),
                day = o.optLong("day"),
                provider = o.optString("p"),
            )
        }.getOrNull()
    }
}

/** Persoenlicher Impuls der KI fuer den Tag. */
data class MotivatorImpulse(val sentence: String, val intentions: List<String>, val tips: List<String>, val day: Long, val provider: String) {
    fun toJson(): String = JSONObject()
        .put("s", sentence).put("v", JSONArray(intentions)).put("t", JSONArray(tips)).put("day", day).put("p", provider)
        .toString()

    companion object {
        fun fromJson(text: String): MotivatorImpulse? = if (text.isBlank()) null else runCatching {
            val o = JSONObject(text)
            MotivatorImpulse(o.optString("s"), GoalAi.strings(o.optJSONArray("v"), 3), GoalAi.strings(o.optJSONArray("t"), 3), o.optLong("day"), o.optString("p"))
        }.getOrNull()
    }
}

/** Texte fuer die KI rund um Ziele und den Tages-Motivator. Es werden keine Namen gesendet. */
object GoalAi {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    const val SYSTEM_PROMPT =
        "Du unterstützt pflegende Angehörige und Pflegekräfte dabei, Ziele in kleinen, machbaren Schritten zu erreichen " +
            "(z. B. Anträge bei Pflegekasse oder Ämtern, Entlastung, Alltag, eigene Gesundheit). " +
            "Du bist kein Arzt, kein Anwalt und keine Behörde und ersetzt keine Beratung. " +
            "Keine Diagnosen, keine Therapie- und keine Medikamentenempfehlungen; bei Gesundheitsthemen gehört die Abklärung mit Arzt oder Ärztin als Schritt dazu. " +
            "Bei Anträgen nenne die übliche zuständige Stelle und weise darauf hin, Fristen im Bescheid zu prüfen. " +
            "Sei warm, ermutigend und konkret. Antworte ausschließlich mit einem JSON-Objekt, ohne Text davor oder danach."

    fun strings(arr: JSONArray?, max: Int): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { out += it }
        return out.take(max)
    }

    private fun json(text: String): JSONObject = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))

    private fun date(day: Long): String = LocalDate.ofEpochDay(day).format(DATE)

    private fun header(title: String, kind: GoalKind, details: String, targetDay: Long?, role: String?, careLevel: Int?, forPerson: Boolean, today: Long) = buildString {
        appendLine("Ziel: ${title.trim()}")
        appendLine("Art: ${kind.label}" + if (forPerson) " (für die betreute Person)" else " (eigenes Ziel der pflegenden Person)")
        if (details.isNotBlank()) appendLine("Details: ${details.trim()}")
        if (targetDay != null) appendLine("Wunschtermin: ${date(targetDay)} (in ${targetDay - today} Tagen)")
        if (!role.isNullOrBlank()) appendLine("Rolle der pflegenden Person: $role")
        if (careLevel != null && careLevel in 1..5) appendLine("Pflegegrad der betreuten Person: $careLevel")
        appendLine("Heute: ${date(today)}")
    }

    fun pathPrompt(title: String, kind: GoalKind, details: String, targetDay: Long?, role: String?, careLevel: Int?, forPerson: Boolean, today: Long): String = buildString {
        append(header(title, kind, details, targetDay, role, careLevel, forPerson, today))
        appendLine()
        appendLine("Schlage einen realistischen Weg in 3 bis 8 Schritten vor. Antworte als JSON mit genau diesen Feldern:")
        append("{\"schritte\": [{\"titel\": kurzer Titel (höchstens 8 Wörter), \"detail\": ein Satz, was genau zu tun ist, \"tag\": bis wann, als Anzahl Tage ab heute}], ")
        append("\"einschaetzung\": ein bis zwei Sätze, wie realistisch das Ziel und der Wunschtermin sind, ")
        append("\"tipps\": [höchstens 3 kurze Tipps]}")
    }

    fun parsePath(text: String): GoalPath? = runCatching {
        val o = json(text)
        val steps = mutableListOf<SuggestedStep>()
        o.optJSONArray("schritte")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val t = s.optString("titel").trim()
                if (t.isBlank()) continue
                steps += SuggestedStep(t.take(120), s.optString("detail").trim().take(400), if (s.has("tag")) s.optInt("tag").coerceIn(0, 730) else null)
            }
        }
        if (steps.isEmpty()) null else GoalPath(steps.take(10), o.optString("einschaetzung").trim(), strings(o.optJSONArray("tipps"), 3))
    }.getOrNull()

    fun checkPrompt(goal: Goal, steps: List<GoalStep>, role: String?, careLevel: Int?, today: Long, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append(header(goal.title, GoalKind.fromKey(goal.kind), goal.details, goal.targetDay, role, careLevel, goal.personId != 0L, today))
        if (goal.createdMillis > 0) appendLine("Angelegt am: ${date(Instant.ofEpochMilli(goal.createdMillis).atZone(zone).toLocalDate().toEpochDay())}")
        appendLine("Schritte:")
        steps.sortedBy { it.position }.forEachIndexed { i, s ->
            val state = if (s.doneMillis != null) {
                "erledigt am " + date(Instant.ofEpochMilli(s.doneMillis).atZone(zone).toLocalDate().toEpochDay())
            } else {
                "offen" + (s.dueDay?.let { d -> ", bis " + date(d) + if (d < today) ", überfällig" else "" } ?: "")
            }
            appendLine("${i + 1}. ${s.title.trim()} [$state]" + if (s.note.isNotBlank()) " Notiz: ${s.note.trim()}" else "")
        }
        if (steps.isEmpty()) appendLine("(noch keine Schritte)")
        appendLine()
        appendLine("Schätze ein, wie gut der Weg zum Ziel läuft. Antworte als JSON mit genau diesen Feldern:")
        append("{\"stand\": 1 bis 5 (1 = stockt, 3 = läuft, 5 = sehr gut im Plan), \"einschaetzung\": ein bis zwei ermutigende, ehrliche Sätze, ")
        append("\"naechster_schritt\": ein konkreter Vorschlag für heute oder morgen, \"tipps\": [höchstens 3 kurze Tipps], ")
        append("\"neue_schritte\": [höchstens 2 sinnvolle zusätzliche Schritte {\"titel\": kurz, \"detail\": ein Satz}, leer wenn keine nötig]}")
    }

    fun parseCheck(text: String, day: Long, provider: String): GoalCheck? = runCatching {
        val o = json(text)
        val newSteps = o.optJSONArray("neue_schritte")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { s -> s.optString("titel").trim().takeIf { it.isNotBlank() }?.let { SuggestedStep(it.take(120), s.optString("detail").trim().take(400), null) } }
            }
        } ?: emptyList()
        val assessment = o.optString("einschaetzung").trim()
        if (assessment.isBlank()) null
        else GoalCheck(o.optInt("stand", 0).coerceIn(0, 5), assessment, o.optString("naechster_schritt").trim(), strings(o.optJSONArray("tipps"), 3), newSteps.take(2), day, provider)
    }.getOrNull()

    fun impulsePrompt(plan: MotivatorPlan, role: String?): String = buildString {
        appendLine("Tages-Motivator für eine pflegende Person" + if (!role.isNullOrBlank()) " ($role)." else ".")
        appendLine("Stimmung der letzten Tage (1 = schlecht, 5 = sehr gut):")
        plan.moodDays.forEach { d ->
            appendLine("- ${Motivator.dayLabel(d.day, plan.day)}: " + (d.avg?.let { "%.1f".format(java.util.Locale.GERMAN, it) + " (${d.count}× eingetragen)" } ?: "kein Eintrag"))
        }
        plan.lastMood?.tagList()?.takeIf { it.isNotEmpty() }?.let { appendLine("Zuletzt genannte Stichworte: ${it.joinToString(", ")}") }
        if (plan.achieved.isNotEmpty()) appendLine("Geschafft in den letzten drei Tagen: ${plan.achieved.joinToString("; ")}")
        if (plan.goals.isNotEmpty()) {
            appendLine("Aktive Ziele:")
            plan.goals.take(4).forEach { g ->
                appendLine("- ${g.goal.title.trim()}: ${g.done} von ${g.total} Schritten, ${g.pace.label}" + (g.next?.let { ", nächster Schritt: ${it.title.trim()}" } ?: ""))
            }
        }
        val open = plan.intentions.filter { !it.done && (it.stepId != null || it.todoId != null) }
        if (open.isNotEmpty()) appendLine("Heute anstehend: ${open.joinToString("; ") { it.text }}")
        appendLine()
        appendLine("Formuliere für heute einen persönlichen Impuls. Antworte als JSON mit genau diesen Feldern:")
        append("{\"satz\": ein warmer, persönlicher Satz für den Tag (höchstens 20 Wörter), ")
        append("\"vorhaben\": [2 bis 3 kleine, machbare Vorhaben für heute, je höchstens 12 Wörter], ")
        append("\"tipps\": [höchstens 2 kurze Tipps]}. ")
        append("Ist die Stimmung schlecht, bleib sanft, nimm Druck raus und weise auf Unterstützung hin (Pflegeberatung, Pflegetelefon, vertraute Menschen).")
    }

    fun parseImpulse(text: String, day: Long, provider: String): MotivatorImpulse? = runCatching {
        val o = json(text)
        val s = o.optString("satz").trim()
        if (s.isBlank()) null else MotivatorImpulse(s, strings(o.optJSONArray("vorhaben"), 3), strings(o.optJSONArray("tipps"), 2), day, provider)
    }.getOrNull()
}
