package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.VitalEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Eine geplante Messung an einem Tag, z. B. Blutzucker um 07:30, mit dem passenden Eintrag (falls schon gemessen). */
data class MeasureDue(val personId: Long, val kind: VitalKind, val day: Long, val time: LocalTime, val entry: VitalEntry?) {
    val done: Boolean get() = entry != null
    val timeText: String get() = time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Zusammenfassung eines Messwerts über einen Zeitraum (nur Zahlen, keine Bewertung). */
data class VitalStat(
    val kind: VitalKind,
    val unit: String,
    val count: Int,
    val days: Int,
    val min: Double,
    val max: Double,
    val avg: Double,
    /** Unterer Wert (Blutdruck): Durchschnitt, sonst null */
    val avg2: Double?,
    val min2: Double?,
    val max2: Double?,
    /** Außerhalb des selbst eingetragenen Bereichs */
    val outside: Int,
    /** Durchschnitt je Angabe, z. B. „nüchtern“ → 112 */
    val byContext: Map<String, Double>,
)

/**
 * Messzeiten je Person (z. B. Blutzucker um 07:30, 12:00 und 18:00) und was davon erledigt ist.
 * Eine Messung zählt für eine Uhrzeit, wenn sie höchstens 2 Stunden davor oder 3 Stunden danach liegt;
 * jede Messung zählt nur einmal, und zwar für die nächstgelegene Uhrzeit.
 */
object MeasurePlans {
    const val BEFORE_MINUTES = 120L
    const val AFTER_MINUTES = 180L
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    private fun time(t: String): LocalTime? = runCatching {
        val (h, m) = t.trim().split(":").map { it.trim().toInt() }
        LocalTime.of(h, m)
    }.getOrNull()

    /** "blutzucker=07:30,12:00;blutdruck=08:00" → Plan */
    fun parse(text: String): Map<VitalKind, List<LocalTime>> =
        text.split(";").mapNotNull { part ->
            val kind = VitalKind.of(part.substringBefore("=", "").trim()) ?: return@mapNotNull null
            if (kind == VitalKind.Trinken) return@mapNotNull null
            val times = part.substringAfter("=", "").split(",").mapNotNull { time(it) }.distinct().sorted()
            if (times.isEmpty()) null else kind to times
        }.toMap()

    fun format(plan: Map<VitalKind, List<LocalTime>>): String =
        VitalKind.entries.filter { !plan[it].isNullOrEmpty() }.joinToString(";") { k ->
            k.key + "=" + plan.getValue(k).distinct().sorted().joinToString(",") { it.format(HM) }
        }

    fun timesText(times: List<LocalTime>): String = times.sorted().joinToString(", ") { it.format(HM) }

    private fun millis(day: Long, t: LocalTime, zone: ZoneId): Long = LocalDate.ofEpochDay(day).atTime(t).atZone(zone).toInstant().toEpochMilli()

    /** Geplante Messungen eines Tages für eine Person, nach Uhrzeit sortiert, mit zugeordneter Messung. */
    fun forDay(
        personId: Long,
        plan: Map<VitalKind, List<LocalTime>>,
        entries: List<VitalEntry>,
        day: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MeasureDue> {
        val out = mutableListOf<MeasureDue>()
        plan.forEach { (kind, times) ->
            val slots = times.distinct().sorted()
            val candidates = entries.filter { it.personId == personId && it.kind == kind.key && abs(it.day - day) <= 1 }
            // Paare (Uhrzeit, Messung) nach Abstand; jede Uhrzeit und jede Messung nur einmal
            val pairs = slots.flatMap { s ->
                val at = millis(day, s, zone)
                candidates.mapNotNull { e ->
                    val diff = (e.atMillis - at) / 60_000
                    if (diff in -BEFORE_MINUTES..AFTER_MINUTES) Triple(s, e, abs(diff)) else null
                }
            }.sortedBy { it.third }
            val chosen = mutableMapOf<LocalTime, VitalEntry>()
            pairs.forEach { (s, e, _) ->
                if (s !in chosen && chosen.values.none { it === e }) chosen[s] = e
            }
            slots.forEach { s -> out += MeasureDue(personId, kind, day, s, chosen[s]) }
        }
        return out.sortedWith(compareBy<MeasureDue> { it.time }.thenBy { it.kind.ordinal })
    }

    /** Wurde für [kind] um [at] herum (Fenster wie oben) schon gemessen? Für die Erinnerung. */
    fun measured(entries: List<VitalEntry>, personId: Long, kind: VitalKind, at: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val t = at.atZone(zone).toInstant().toEpochMilli()
        return entries.any { e ->
            e.personId == personId && e.kind == kind.key && (e.atMillis - t) / 60_000 in -BEFORE_MINUTES..AFTER_MINUTES
        }
    }

    /**
     * Geplant und gemessen je Art im Zeitraum. Heute zählen nur Uhrzeiten, deren Fenster schon vorbei ist
     * oder die schon gemessen sind; künftige Tage zählen nicht.
     */
    fun adherence(
        personId: Long,
        plan: Map<VitalKind, List<LocalTime>>,
        entries: List<VitalEntry>,
        fromDay: Long,
        toDay: Long,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<VitalKind, Pair<Int, Int>> {
        if (plan.isEmpty()) return emptyMap()
        val today = now.toLocalDate().toEpochDay()
        val result = mutableMapOf<VitalKind, Pair<Int, Int>>()
        for (d in fromDay..minOf(toDay, today)) {
            forDay(personId, plan, entries, d, zone).forEach { m ->
                val counts = d < today || m.done || LocalDate.ofEpochDay(d).atTime(m.time).plusMinutes(AFTER_MINUTES).isBefore(now)
                if (!counts) return@forEach
                val (p, done) = result[m.kind] ?: (0 to 0)
                result[m.kind] = (p + 1) to (done + if (m.done) 1 else 0)
            }
        }
        return result
    }

    /** Offene Messungen eines Tages über alle Personen, z. B. für den Assistenten. */
    fun openToday(
        plans: Map<Long, Map<VitalKind, List<LocalTime>>>,
        entries: List<VitalEntry>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MeasureDue> {
        val day = now.toLocalDate().toEpochDay()
        return plans.flatMap { (pid, plan) -> forDay(pid, plan, entries, day, zone) }.filter { !it.done }
    }
}

/** Zahlen zu Messwerten über einen Zeitraum. */
object VitalStats {
    fun of(kind: VitalKind, entries: List<VitalEntry>, range: Pair<Double?, Double?>?): VitalStat? {
        val list = entries.filter { it.kind == kind.key }.sortedBy { it.atMillis }
        if (list.isEmpty()) return null
        // Blutzucker kann in mg/dl oder mmol/l eingetragen sein: gerechnet wird in der zuletzt genutzten Einheit
        val unit = list.last().unit.ifBlank { kind.unit }
        val same = list.filter { it.unit.ifBlank { kind.unit } == unit }
        val second = same.mapNotNull { it.value2 }
        return VitalStat(
            kind = kind,
            unit = unit,
            count = list.size,
            days = list.map { it.day }.distinct().size,
            min = same.minOf { it.value },
            max = same.maxOf { it.value },
            avg = same.map { it.value }.average(),
            avg2 = second.takeIf { it.isNotEmpty() }?.average(),
            min2 = second.minOrNull(),
            max2 = second.maxOrNull(),
            outside = list.count { Vitals.outside(it, range) },
            byContext = same.filter { it.context.isNotBlank() }.groupBy { it.context }.mapValues { (_, l) -> l.map { it.value }.average() },
        )
    }

    fun decimals(kind: VitalKind, unit: String): Int = if (kind == VitalKind.Blutzucker && unit == "mmol/l") 1 else kind.decimals

    /** „12 Messungen an 7 Tagen, 98 bis 156, im Schnitt 121 mg/dl“ */
    fun line(s: VitalStat): String {
        val dec = decimals(s.kind, s.unit)
        fun n(v: Double) = Vitals.number(v, dec)
        val unit = if (s.unit.isBlank()) "" else " " + s.unit
        val range = if (s.min2 != null && s.max2 != null) {
            n(s.min) + "–" + n(s.max) + " / " + n(s.min2) + "–" + n(s.max2)
        } else {
            n(s.min) + " bis " + n(s.max)
        }
        val avg = n(s.avg) + (s.avg2?.let { "/" + n(it) } ?: "")
        return "${s.count} Messung(en) an ${s.days} Tag(en), $range, im Schnitt $avg$unit"
    }

    /** „nüchtern im Schnitt 112, nach dem Essen 148 mg/dl“ */
    fun contextLine(s: VitalStat): String? {
        if (s.byContext.isEmpty()) return null
        val dec = decimals(s.kind, s.unit)
        val order = s.kind.contexts
        return s.byContext.entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) 99 else i } }
            .joinToString(", ") { (c, v) -> "$c " + Vitals.number(v, dec) } + (if (s.unit.isBlank()) "" else " " + s.unit) + " (im Schnitt)"
    }

    /** Messzeitpunkt als Text, z. B. für Listen. */
    fun at(e: VitalEntry, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime = Instant.ofEpochMilli(e.atMillis).atZone(zone).toLocalDateTime()
}
