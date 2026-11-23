package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Checkin
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Haltbarkeit nach dem Anbruch (z. B. Insulin-Pen oder Augentropfen meist 4 Wochen, siehe Packungsbeilage).
 * Angebrochen am Tag 0 mit 28 Tagen Haltbarkeit heißt: verwendbar bis einschließlich Tag 27.
 */
object MedOpen {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)

    /** Typische Angaben zum Antippen (immer die Packungsbeilage beachten). */
    val PRESETS = listOf(7, 14, 28, 42)

    /** Letzter Tag, an dem das angebrochene Mittel noch verwendet werden darf, oder null ohne Angaben. */
    fun lastDay(openedDay: Long?, lifeDays: Int): Long? = if (openedDay == null || lifeDays <= 0) null else openedDay + lifeDays - 1

    /** Tage bis einschließlich zum letzten Tag (0 = heute letzter Tag, negativ = abgelaufen), null ohne Angaben. */
    fun daysLeft(openedDay: Long?, lifeDays: Int, today: Long): Long? = lastDay(openedDay, lifeDays)?.let { it - today }

    /** „Angebrochen 01.11., haltbar bis 28.11. (noch 6 Tage)“ bzw. „… seit 2 Tagen abgelaufen“ */
    fun text(openedDay: Long?, lifeDays: Int, today: Long): String? {
        val last = lastDay(openedDay, lifeDays) ?: return null
        val left = last - today
        val head = "Angebrochen " + LocalDate.ofEpochDay(openedDay!!).format(DATE) + ", haltbar bis " + LocalDate.ofEpochDay(last).format(DATE)
        return head + when {
            left > 1 -> " (noch $left Tage)"
            left == 1L -> " (noch bis morgen)"
            left == 0L -> " (heute letzter Tag)"
            left == -1L -> " – seit gestern abgelaufen"
            else -> " – seit ${-left} Tagen abgelaufen"
        }
    }
}

/** Tatsächlich gegebene Mengen (beim Abhaken eingetragen) im Vergleich zum eingetragenen Verbrauch. */
object IntakeStats {
    /**
     * Durchschnitt pro Tag über die Tage der letzten [days] Tage (ohne heute), an denen für [medId] Mengen eingetragen wurden.
     * Null, wenn es weniger als [minDays] solche Tage gibt.
     */
    fun actualDaily(checkins: List<Checkin>, medId: Long, today: Long, days: Int = 14, minDays: Int = 5): Pair<Double, Int>? {
        val perDay = checkins.filter { it.kind == Checkin.MED && it.refId == medId && it.done && it.amount != null && it.day in (today - days) until today }
            .groupBy { it.day }
            .mapValues { (_, l) -> l.sumOf { it.amount!! } }
        if (perDay.size < minDays) return null
        return perDay.values.average() to perDay.size
    }

    /** Lohnt sich ein Vorschlag? Abweichung mindestens 10 % und mindestens eine halbe Einheit. */
    fun differs(actual: Double, planned: Double): Boolean =
        kotlin.math.abs(actual - planned) >= 0.5 && (planned <= 0 || kotlin.math.abs(actual - planned) / planned >= 0.1)
}
