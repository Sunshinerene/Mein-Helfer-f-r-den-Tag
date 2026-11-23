package de.meinhelfer.tag.summary

/**
 * Sunnys „Steuerwerk“: wie selbstständig sie je Bereich handeln darf und was sie aus
 * deinen Antworten lernt. Reine Logik ohne Android, damit sie sich testen lässt.
 */
enum class AutoLevel(val key: Int, val label: String) {
    Ask(0, "Nur fragen"),
    Report(1, "Machen und Bescheid sagen"),
    Silent(2, "Still erledigen"),
    ;

    companion object {
        fun of(key: Int) = entries.firstOrNull { it.key == key } ?: Ask
    }
}

enum class AutoArea(val key: String, val title: String, val text: String, val levels: List<AutoLevel>) {
    Updates(
        "updates", "App-Updates",
        "Neue Version laden. Installieren musst du immer selbst bestätigen (Android fragt).",
        AutoLevel.entries,
    ),
    Cleanup(
        "aufraeumen", "Aufräumen",
        "Erledigte Aufgaben nach 14 Tagen aus der Liste nehmen (im Verlauf bleiben sie).",
        AutoLevel.entries,
    ),
    Reorder(
        "nachbestellen", "Nachbestellen vorbereiten",
        "Wird ein Medikament knapp: Aufgabe zum Nachbestellen anlegen und das Medikament auf die Apotheken-Liste setzen.",
        AutoLevel.entries,
    ),
    Backup(
        "sicherung", "Sicherung",
        "Erinnert, wenn die letzte Sicherung zu lange her ist, und öffnet die Sicherung. Passwort und Speicherort wählst du selbst.",
        listOf(AutoLevel.Ask),
    ),
    Permissions(
        "freigaben", "Fehlende Freigaben",
        "Fehlt eine Android-Freigabe für Erinnerungen, führt Sunny Schritt für Schritt hin. Einschalten musst du selbst.",
        listOf(AutoLevel.Ask),
    ),
    DataCheck(
        "datencheck", "Daten-Check",
        "Einmal pro Woche: fehlende Angaben finden (z. B. Medikament ohne Einnahmezeit, Arzt ohne Telefonnummer). Ändert nichts.",
        listOf(AutoLevel.Ask),
    ),
    Hints(
        "hinweise", "Andere Vorschläge zu Aufgaben und Medikamenten",
        "Wichtiger machen, verschieben, Verbrauch anpassen … ändert deine Daten, deshalb fragt Sunny hier immer.",
        listOf(AutoLevel.Ask),
    ),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** Was Sunny zu einer Art Vorschlag gelernt hat. */
data class AutoStat(val accepted: Int = 0, val declined: Int = 0, val pausedUntil: Long = 0L) {
    fun encode() = "$accepted,$declined,$pausedUntil"

    companion object {
        fun decode(s: String?): AutoStat {
            val p = s.orEmpty().split(",")
            return AutoStat(
                p.getOrNull(0)?.toIntOrNull() ?: 0,
                p.getOrNull(1)?.toIntOrNull() ?: 0,
                p.getOrNull(2)?.toLongOrNull() ?: 0L,
            )
        }
    }
}

object Steuerwerk {
    const val DECLINES_TO_PAUSE = 3
    const val ACCEPTS_TO_OFFER = 3
    const val PAUSE_MILLIS = 7L * 24 * 3600 * 1000

    /** Art eines Vorschlags aus seinem Schlüssel („bestellen:12:345“ → „bestellen“). */
    fun category(key: String) = key.substringBefore(':')

    fun accepted(s: AutoStat) = s.copy(accepted = s.accepted + 1, declined = 0)

    /** Nach „Nein, danke“: oft abgelehnt und nie angenommen → eine Woche Ruhe zu dieser Art. */
    fun declined(s: AutoStat, now: Long): AutoStat {
        val d = s.declined + 1
        return if (d >= DECLINES_TO_PAUSE && s.accepted == 0) {
            s.copy(declined = 0, pausedUntil = now + PAUSE_MILLIS)
        } else {
            s.copy(declined = d)
        }
    }

    fun justPaused(before: AutoStat, after: AutoStat) = after.pausedUntil > before.pausedUntil

    fun paused(s: AutoStat, now: Long) = s.pausedUntil > now

    /** Immer „Ja“ gesagt → Sunny bietet an, es künftig ohne Frage zu erledigen. */
    fun offerUpgrade(area: AutoArea, level: AutoLevel, s: AutoStat) =
        level == AutoLevel.Ask && area.levels.size > 1 && s.accepted >= ACCEPTS_TO_OFFER && s.declined == 0

    /** Verständlicher Name einer Vorschlagsart (für „Pausiert“ in den Einstellungen). */
    fun categoryName(cat: String) = when (cat) {
        "prio" -> "Aufgabe wichtiger machen"
        "spaeter" -> "Aufgabe verschieben"
        "bestellen" -> "Nachbestellen"
        "anbruch" -> "Anbruch-Datum"
        "verbrauch" -> "Verbrauch anpassen"
        "tagesstruktur" -> "Termin und Tagesstruktur"
        "ausweis" -> "Ausweis läuft ab"
        "geburtstag" -> "Geburtstage"
        "wecker" -> "Wecker"
        "update" -> "App-Updates"
        "aufraeumen" -> "Aufräumen"
        "stufe" -> "Mehr Selbstständigkeit"
        "sicherung" -> "Sicherung"
        "freigabe" -> "Fehlende Freigaben"
        "datencheck" -> "Daten-Check"
        else -> cat
    }
}

/** Ein Eintrag im Protokoll „Was Sunny gemacht hat“. */
data class JournalEntry(val millis: Long, val area: String, val text: String) {
    fun encode() = "$millis|$area|" + text.replace("\n", " ").replace("|", "/")

    companion object {
        const val KEEP_MILLIS = 7L * 24 * 3600 * 1000
        const val MAX = 200

        fun decode(line: String): JournalEntry? {
            val p = line.split("|", limit = 3)
            if (p.size < 3) return null
            return JournalEntry(p[0].toLongOrNull() ?: return null, p[1], p[2])
        }

        fun decodeAll(s: String): List<JournalEntry> = s.lines().mapNotNull { decode(it) }

        /** Neuen Eintrag anhängen, ältere als 7 Tage und Überzähliges entfernen. */
        fun append(all: List<JournalEntry>, e: JournalEntry): List<JournalEntry> =
            (all + e).filter { e.millis - it.millis <= KEEP_MILLIS }.takeLast(MAX)
    }
}
