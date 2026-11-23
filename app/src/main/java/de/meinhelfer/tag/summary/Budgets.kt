package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.BudgetEntry
import java.time.LocalDate
import java.util.Locale

/** Budgets der Pflegekasse, die man hier mitverfolgen kann. */
enum class BudgetKind(val key: String, val label: String, val short: String) {
    Entlastung("entlastung", "Entlastungsbetrag", "Entlastung"),
    Verhinderung("verhinderung", "Verhinderungspflege", "Verhinderung"),
    Kurzzeit("kurzzeit", "Kurzzeitpflege", "Kurzzeit"),
    ;

    companion object {
        fun of(key: String): BudgetKind? = entries.firstOrNull { it.key == key }
    }
}

enum class BudgetStatus(val key: String, val label: String) {
    Offen("offen", "noch nicht eingereicht"),
    Eingereicht("eingereicht", "eingereicht"),
    Erstattet("erstattet", "erstattet"),
    Abgelehnt("abgelehnt", "abgelehnt"),
    ;

    companion object {
        fun of(key: String): BudgetStatus = entries.firstOrNull { it.key == key } ?: Offen
    }
}

/**
 * Rechnet die Budgets nach geltendem Recht (Stand November 2026):
 * Entlastungsbetrag 131 Euro im Monat ab Pflegegrad 1 (§ 45b SGB XI), nicht genutzte Betraege bis 30. Juni des Folgejahres;
 * Gemeinsamer Jahresbetrag 3.539 Euro je Kalenderjahr fuer Verhinderungs- und Kurzzeitpflege ab Pflegegrad 2 (§ 42a SGB XI),
 * jeweils bis zu 8 Wochen im Jahr. Die Betraege lassen sich in der App aendern, falls sich das Recht aendert.
 */
object Budgets {
    const val DEFAULT_MONTHLY = 131.0
    const val DEFAULT_YEARLY = 3539.0
    const val MAX_DAYS = 56

    fun euro(v: Double): String = String.format(Locale.GERMANY, "%,.2f €", v)

    /** Abgelehnte Ausgaben zaehlen nicht. */
    private fun counts(e: BudgetEntry) = e.status != BudgetStatus.Abgelehnt.key

    data class Relief(
        val year: Int,
        /** Anspruch im ganzen Jahr */
        val yearTotal: Double,
        /** Bis einschliesslich diesen Monat angesammelt */
        val accruedSoFar: Double,
        /** Uebertrag aus dem Vorjahr (nutzbar bis 30. Juni) */
        val carryIn: Double,
        val usedFromCarry: Double,
        val usedCurrent: Double,
        /** Uebertrag, der am 30. Juni verfaellt bzw. verfallen ist */
        val carryLeft: Double,
        /** Rest aus diesem Jahr (ganzes Jahr gerechnet) */
        val left: Double,
        /** Jetzt verfuegbar: Rest aus dem Vorjahr (bis 30.6.) plus bisher angesammelt minus genutzt */
        val availableNow: Double,
    )

    /**
     * Entlastungsbetrag fuer [year], mit Uebertrag aus den Vorjahren. [months] liefert je Jahr die Monate mit Anspruch
     * (z. B. 12, oder weniger, wenn der Pflegegrad erst im Laufe des Jahres kam), [monthly] den Monatsbetrag je Jahr.
     */
    fun relief(
        entries: List<BudgetEntry>,
        year: Int,
        today: LocalDate,
        months: (Int) -> Int,
        /** Rest aus dem Vorjahr nutzbar bis einschließlich Monat im Jahr (6 = 30. Juni, 0 = verfallen) */
        carryUntil: (Int) -> Int = { 6 },
        monthly: (Int) -> Double,
    ): Relief {
        val own = entries.filter { it.kind == BudgetKind.Entlastung.key && counts(it) }
        val first = (own.minOfOrNull { LocalDate.ofEpochDay(it.day).year } ?: year).coerceAtMost(year - 1).coerceAtLeast(year - 5)
        var carryPrev = 0.0
        var result: Relief? = null
        for (y in first..year) {
            val total = months(y) * monthly(y)
            val carryIn = carryPrev
            var usedCarry = 0.0
            var usedCurrent = 0.0
            own.filter { LocalDate.ofEpochDay(it.day).year == y }.sortedBy { it.day }.forEach { e ->
                val d = LocalDate.ofEpochDay(e.day)
                // Bis 30. Juni (bzw. wie eingestellt) zuerst den Uebertrag aus dem Vorjahr verbrauchen
                val fromCarry = if (d.monthValue <= carryUntil(y)) minOf(e.amount, carryIn - usedCarry).coerceAtLeast(0.0) else 0.0
                usedCarry += fromCarry
                usedCurrent += e.amount - fromCarry
            }
            val left = (total - usedCurrent).coerceAtLeast(0.0)
            if (y == year) {
                val monthsSoFar = if (today.year > year) months(y) else if (today.year < year) 0 else minOf(months(y), monthsUntil(y, today, months(y)))
                val accrued = monthsSoFar * monthly(y)
                val carryUsable = if (today.year == year && today.monthValue <= carryUntil(y)) carryIn - usedCarry else 0.0
                result = Relief(
                    year = y,
                    yearTotal = total,
                    accruedSoFar = accrued,
                    carryIn = carryIn,
                    usedFromCarry = usedCarry,
                    usedCurrent = usedCurrent,
                    carryLeft = (carryIn - usedCarry).coerceAtLeast(0.0),
                    left = left,
                    availableNow = (carryUsable + accrued - usedCurrent).coerceAtLeast(0.0),
                )
            }
            carryPrev = left
        }
        return result!!
    }

    /** Monate mit Anspruch bis einschliesslich heute: Anspruch beginnt im Monat (13 - months) des Jahres. */
    private fun monthsUntil(year: Int, today: LocalDate, months: Int): Int {
        val startMonth = 13 - months
        return (today.monthValue - startMonth + 1).coerceIn(0, months)
    }

    data class Yearly(val year: Int, val total: Double, val usedRespite: Double, val usedShort: Double, val daysRespite: Int, val daysShort: Int) {
        val used get() = usedRespite + usedShort
        val left get() = (total - used).coerceAtLeast(0.0)
    }

    /** Gemeinsamer Jahresbetrag fuer Verhinderungs- und Kurzzeitpflege im Kalenderjahr. */
    fun yearly(entries: List<BudgetEntry>, year: Int, total: Double): Yearly {
        val inYear = entries.filter { counts(it) && LocalDate.ofEpochDay(it.day).year == year }
        val r = inYear.filter { it.kind == BudgetKind.Verhinderung.key }
        val k = inYear.filter { it.kind == BudgetKind.Kurzzeit.key }
        return Yearly(year, total, r.sumOf { it.amount }, k.sumOf { it.amount }, r.sumOf { it.days }, k.sumOf { it.days })
    }
}
