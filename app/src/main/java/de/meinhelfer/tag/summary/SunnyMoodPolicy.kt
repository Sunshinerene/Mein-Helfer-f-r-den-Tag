package de.meinhelfer.tag.summary

/** Lokale Reaktionsvorschläge; keine Diagnose oder automatischen Aktionen. */
object SunnyMoodPolicy {
    enum class Face { Friendly, Happy, Attentive, Concerned }
    data class Reaction(val expression: Face, val animation: String, val message: String, val relief: String, val quietMinutes: Int)

    fun react(mood: Int, tags: List<String>, professional: Boolean): Reaction {
        require(mood in 1..5)
        val t = tags.map { it.trim().lowercase(java.util.Locale.ROOT) }.toSet()
        val help = if (professional) "Bitte das Team um Unterstützung oder eine sichere Ablösung."
            else "Überlege, ob eine vertraute Person oder ein Pflegedienst etwas übernehmen kann."
        return when {
            mood == 1 -> Reaction(Face.Concerned, "gentle_nod", "Das klingt nach einem schweren Moment. Möchtest du Unterstützung?", help, 180)
            "schmerzen" in t -> Reaction(Face.Concerned, "gentle_nod", "Deine Gesundheit ist ebenfalls wichtig.", "Lass anhaltende oder starke Beschwerden ärztlich abklären.", 120)
            "erschöpft" in t -> Reaction(Face.Concerned, "slow_blink", "Du darfst dir Entlastung wünschen.", help, 120)
            "gestresst" in t || "unruhig" in t -> Reaction(Face.Attentive, "slow_blink", "Was würde dir gerade am meisten helfen?", "Wähle eine nicht dringende Aufgabe, die warten kann.", 90)
            "traurig" in t || "allein" in t -> Reaction(Face.Concerned, "gentle_nod", "Möchtest du darüber sprechen oder lieber Ruhe haben?", "Wenn du magst, melde dich bei einer vertrauten Person.", 120)
            mood == 2 -> Reaction(Face.Concerned, "gentle_nod", "Heute darf es etwas weniger sein.", help, 120)
            mood == 3 -> Reaction(Face.Friendly, "gentle_nod", "Was würde deinen Alltag heute etwas leichter machen?", "Wenn die Versorgung gesichert ist, gönne dir eine kurze Pause.", 90)
            mood == 4 -> Reaction(Face.Friendly, "soft_smile", "Schön, dass es dir heute gut geht.", "Bewahre dir einen kleinen Moment für dich.", 120)
            else -> Reaction(Face.Happy, "soft_smile", "Schön, dass du dich so gut fühlst!", "Genieße den Moment, ohne dir zusätzliche Aufgaben aufzubürden.", 120)
        }
    }

    /** Nur freiwillige Motivationsimpulse; wichtige Pflege-Erinnerungen bleiben unberührt. */
    fun mayPrompt(enabled: Boolean, quietHours: Boolean, dismissed: Boolean,
                  nowMillis: Long, quietUntilMillis: Long): Boolean =
        enabled && !quietHours && !dismissed && nowMillis >= quietUntilMillis
}
