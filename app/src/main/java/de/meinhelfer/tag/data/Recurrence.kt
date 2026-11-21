package de.meinhelfer.tag.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wiederkehrende Termine: Die Serie wird als einzelne Termine angelegt (jeder mit eigener Erinnerung,
 * eigenem Abhaken und eigenen Unterlagen). Ohne Ende werden immer etwa 6 Monate im Voraus angelegt.
 */
object Recurrence {
    const val MONTHLY = -1
    private const val HORIZON_DAYS = 183L
    private const val MAX_COUNT = 60
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

    val OPTIONS: List<Pair<Int, String>> = listOf(
        0 to "Nicht wiederholen",
        7 to "Jede Woche",
        14 to "Alle 2 Wochen",
        21 to "Alle 3 Wochen",
        28 to "Alle 4 Wochen",
        MONTHLY to "Jeden Monat (gleicher Tag)",
    )

    fun label(rule: Int): String = OPTIONS.firstOrNull { it.first == rule }?.second ?: "Alle $rule Tage"

    /** "Jede Woche, bis 21.05.2027" bzw. "Jede Woche" */
    fun describe(a: Appointment): String? {
        if (a.repeatDays == 0) return null
        return label(a.repeatDays) + (a.repeatUntil?.let { ", bis " + LocalDate.ofEpochDay(it).format(DATE) } ?: "")
    }

    private fun shift(base: LocalDateTime, rule: Int, k: Long): LocalDateTime =
        if (rule == MONTHLY) base.plusMonths(k) else base.plusDays(rule.toLong() * k)

    /** Folgetermine nach [anchor] nach dessen Regel (ohne den Anker selbst). */
    fun following(anchor: Appointment, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Appointment> {
        if (anchor.repeatDays == 0) return emptyList()
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchor.whenMillis), zone)
        val duration = anchor.endMillis?.let { it - anchor.whenMillis }
        val limitDay = minOf(anchor.repeatUntil ?: Long.MAX_VALUE, today.toEpochDay() + HORIZON_DAYS)
        val out = mutableListOf<Appointment>()
        var k = 1L
        while (out.size < MAX_COUNT) {
            val next = shift(start, anchor.repeatDays, k)
            if (next.toLocalDate().toEpochDay() > limitDay) break
            val w = next.atZone(zone).toInstant().toEpochMilli()
            out += anchor.copy(id = 0, whenMillis = w, endMillis = duration?.let { w + it }, docIds = "")
            k++
        }
        return out
    }
}
