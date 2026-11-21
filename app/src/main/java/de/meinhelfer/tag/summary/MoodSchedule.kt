package de.meinhelfer.tag.summary

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Wie oft der Stimmungs-Check fragt. */
enum class MoodMode(val key: String, val label: String) {
    Aus("aus", "Aus"),
    Start("start", "Bei jedem Öffnen"),
    Einmal("1", "1× täglich"),
    Zweimal("2", "2× täglich"),
    Dreimal("3", "3× täglich"),
    ;

    val isSlotted: Boolean get() = this == Einmal || this == Zweimal || this == Dreimal

    companion object {
        fun fromKey(key: String?): MoodMode = entries.firstOrNull { it.key == key } ?: Dreimal
    }
}

/** Zeitfenster des Stimmungs-Checks (nachts wird nicht gefragt). */
object MoodSchedule {
    /** Mindestabstand bei "Bei jedem Oeffnen" */
    const val START_GAP_MILLIS = 30L * 60 * 1000

    /** Zeitfenster als Stunden von (einschliesslich) bis (ausschliesslich). */
    fun slots(mode: MoodMode): List<Pair<Int, Int>> = when (mode) {
        MoodMode.Einmal -> listOf(5 to 24)
        MoodMode.Zweimal -> listOf(5 to 14, 14 to 24)
        MoodMode.Dreimal -> listOf(5 to 11, 11 to 17, 17 to 24)
        else -> emptyList()
    }

    /** Uhrzeiten fuer die Erinnerung per Benachrichtigung. */
    fun reminderHours(mode: MoodMode): List<Int> = when (mode) {
        MoodMode.Einmal -> listOf(18)
        MoodMode.Zweimal -> listOf(10, 18)
        MoodMode.Dreimal -> listOf(9, 14, 19)
        else -> emptyList()
    }

    fun slotText(mode: MoodMode): String = when (mode) {
        MoodMode.Einmal -> "einmal am Tag"
        MoodMode.Zweimal -> "vormittags und nachmittags"
        MoodMode.Dreimal -> "morgens, mittags und abends"
        MoodMode.Start -> "bei jedem Öffnen der App (höchstens alle 30 Minuten)"
        MoodMode.Aus -> "nie"
    }

    /** Beginn des aktuellen Zeitfensters in Millisekunden, null = gerade kein Zeitfenster (z. B. nachts). */
    fun slotStart(mode: MoodMode, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val slot = slots(mode).firstOrNull { now.hour >= it.first && now.hour < it.second } ?: return null
        return LocalDate.from(now).atTime(LocalTime.of(slot.first, 0)).atZone(zone).toInstant().toEpochMilli()
    }

    /** Ist eine Abfrage faellig? Ein Eintrag oder ein "Spaeter" im aktuellen Zeitfenster zaehlt als erledigt. */
    fun isDue(mode: MoodMode, nowMillis: Long, lastEntryMillis: Long, lastAskedMillis: Long): Boolean = when (mode) {
        MoodMode.Aus -> false
        MoodMode.Start -> nowMillis - maxOf(lastEntryMillis, lastAskedMillis) >= START_GAP_MILLIS
        else -> {
            val start = slotStart(mode, nowMillis)
            start != null && lastEntryMillis < start && lastAskedMillis < start
        }
    }
}
