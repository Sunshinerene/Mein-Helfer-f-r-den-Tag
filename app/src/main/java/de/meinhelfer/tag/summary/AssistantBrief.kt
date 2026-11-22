package de.meinhelfer.tag.summary

import java.util.Locale

/** Eigene Werte der pflegenden Person aus Health Connect (nur gelesen, nicht gespeichert). */
data class OwnHealth(
    /** Schlaf der letzten Nacht in Minuten */
    val sleepMinutes: Int? = null,
    /** Schritte heute */
    val stepsToday: Long? = null,
    /** Letzter Ruhepuls */
    val restingNow: Int? = null,
    /** Durchschnitt Ruhepuls der Tage davor (bis 7) */
    val restingAvg: Int? = null,
)

/**
 * Texte des Assistenten für Benachrichtigungen (Morgen, Abend, vor dem Termin), Ruhezeiten und Hinweise zu deinen eigenen Werten.
 * Ohne Android, damit es getestet werden kann.
 */
object AssistantBrief {
    /** Liegt die Minute des Tages in der Ruhezeit (auch über Mitternacht, z. B. 22:00–07:00)? */
    fun inQuiet(minute: Int, from: Int, to: Int): Boolean = when {
        from == to -> false
        from < to -> minute in from until to
        else -> minute >= from || minute < to
    }

    fun hm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    /** Kurze Checkliste aus der Terminvorbereitung (höchstens drei Punkte, ohne Klammern). */
    fun prepShort(p: AppointmentPrep): String =
        p.hints.take(3).joinToString("; ") { it.substringBefore(" (") }

    /** Morgenüberblick: was heute ansteht, Vorbereitung für heutige Termine, Anzahl der Vorschläge. */
    fun morning(name: String, line: List<NextUp>, todayPreps: List<AppointmentPrep>, proposals: Int, multi: Boolean): Pair<String, String> {
        val title = "$name: Guten Morgen"
        if (line.isEmpty() && todayPreps.isEmpty()) {
            return title to "Heute steht laut App nichts Festes an." + (if (proposals > 0) " Ich habe ${count(proposals, "Vorschlag", "Vorschläge")} für dich." else "") +
                " Gönn dir heute etwas Gutes."
        }
        val intakes = line.count { it.kind == NextKind.Intake }
        val appts = line.count { it.kind == NextKind.Appointment }
        val tasks = line.count { it.kind == NextKind.Task }
        val parts = listOfNotNull(
            intakes.takeIf { it > 0 }?.let { count(it, "Einnahmezeit", "Einnahmezeiten") },
            appts.takeIf { it > 0 }?.let { count(it, "Termin", "Termine") },
            tasks.takeIf { it > 0 }?.let { count(it, "fällige Aufgabe", "fällige Aufgaben") },
        )
        val first = line.firstOrNull()?.let { n -> " Als Nächstes: " + n.title + (if (multi && n.personName.isNotBlank()) " (${n.personName})" else "") + "." }.orEmpty()
        val prep = todayPreps.filter { it.hints.isNotEmpty() }.joinToString(" ") { p ->
            "Für ${p.appointment.title} um ${p.whenText.substringAfterLast(' ')}: ${prepShort(p)}."
        }
        val text = "Heute: " + parts.joinToString(", ") + "." + first +
            (if (prep.isNotBlank()) " $prep" else "") +
            (if (proposals > 0) " Ich habe ${count(proposals, "Vorschlag", "Vorschläge")} für dich." else "")
        return title to text
    }

    /** Abend: was geschafft ist, was offen bleibt, Vorbereitung für morgen, Tagebuch. */
    fun evening(
        name: String,
        doneToday: Int,
        open: List<NextUp>,
        tomorrowPreps: List<AppointmentPrep>,
        diaryMissingFor: List<String>,
        multi: Boolean,
    ): Pair<String, String> {
        val title = "$name: Dein Abend"
        val done = if (doneToday > 0) "Heute geschafft: ${count(doneToday, "Punkt", "Punkte")}. Danke dir!" else "Heute war viel los, auch wenn es nicht abgehakt ist."
        val stillOpen = if (open.isEmpty()) " Für heute ist alles Wichtige erledigt."
        else " Noch offen: " + open.take(3).joinToString(", ") { n -> n.title + if (multi && n.personName.isNotBlank()) " (${n.personName})" else "" } +
            (if (open.size > 3) " und ${open.size - 3} weitere" else "") + "."
        val prep = tomorrowPreps.joinToString(" ") { p ->
            "Morgen ${p.whenText.substringAfterLast(' ')} ${p.appointment.title}" + (if (multi && p.personName.isNotBlank()) " (${p.personName})" else "") +
                (if (p.hints.isNotEmpty()) ": schon heute bereitlegen – ${prepShort(p)}." else ".")
        }
        val diary = when {
            diaryMissingFor.isEmpty() -> ""
            multi -> " Pflegetagebuch fehlt noch für " + diaryMissingFor.joinToString(", ") + "."
            else -> " Das Pflegetagebuch für heute fehlt noch."
        }
        return title to (done + stillOpen + (if (prep.isNotBlank()) " $prep" else "") + diary + " Gute Nacht!")
    }

    /** Hinweis-Zusatz für die Terminerinnerung, z. B. „Denk an: Versichertenkarte; Medikamentenplan“. */
    fun appointmentAddon(p: AppointmentPrep?): String = p?.takeIf { it.hints.isNotEmpty() }?.let { "\nDenk an: " + prepShort(it) + "." }.orEmpty()

    /** Sanfte Hinweise zu deinen eigenen Werten. Keine Bewertung im medizinischen Sinn. */
    fun selfHints(own: OwnHealth?, hour: Int): List<String> {
        if (own == null) return emptyList()
        val out = mutableListOf<String>()
        own.sleepMinutes?.let { m ->
            val text = "%d:%02d Std.".format(m / 60, m % 60)
            when {
                m in 1 until 360 -> out += "Du hast nur $text geschlafen. Nimm dir heute nur das Nötigste vor und plane bewusst eine Pause ein."
                m >= 450 -> out += "Gut geschlafen ($text). Schön!"
                else -> {}
            }
        }
        own.stepsToday?.let { s ->
            val text = String.format(Locale.GERMANY, "%,d", s)
            when {
                s >= 9000 -> out += "Schon $text Schritte heute, du bist viel auf den Beinen. Setz dich zwischendurch bewusst hin."
                hour >= 15 && s in 1 until 1500 -> out += "Heute erst $text Schritte. Ein kurzer Gang an die frische Luft tut auch dir gut."
                else -> {}
            }
        }
        val now = own.restingNow
        val avg = own.restingAvg
        if (now != null && avg != null && avg > 0 && now >= avg + 8) {
            out += "Dein Ruhepuls liegt heute über deinem Schnitt ($now statt etwa $avg). Das kann an Stress oder wenig Schlaf liegen. " +
                "Achte auf Pausen; hält es an, sprich mit deiner Hausarztpraxis."
        }
        return out
    }
}
