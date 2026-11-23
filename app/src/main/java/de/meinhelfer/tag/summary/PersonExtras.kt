package de.meinhelfer.tag.summary

import java.time.LocalDate
import java.time.YearMonth

/** Einfache Ablage „schluessel=wert;…“ für Zusatzangaben einer Person (ohne JSON-Bibliothek, testbar). */
internal object KeyValues {
    fun decode(text: String): Map<String, String> = text.split(';').mapNotNull { p ->
        val k = p.substringBefore('=', "").trim()
        if (k.isEmpty()) null else k to p.substringAfter('=').replace("%3B", ";").replace("%3D", "=")
    }.toMap()

    fun encode(map: Map<String, String?>): String = map.filterValues { !it.isNullOrBlank() }
        .entries.joinToString(";") { (k, v) -> k + "=" + v!!.replace(";", "%3B").replace("=", "%3D") }
}

/** Schwerbehindertenausweis: Grad der Behinderung, Merkzeichen, Gültigkeit. Nur auf dem Handy. */
data class Disability(
    /** Grad der Behinderung (GdB) 20 bis 100, 0 = keiner eingetragen */
    val gdb: Int = 0,
    val marks: Set<String> = emptySet(),
    /** Gültig bis (Epoch-Tag, letzter Tag des Monats), null = nicht eingetragen */
    val validUntil: Long? = null,
    val unlimited: Boolean = false,
    /** Ausstellende Stelle, z. B. Versorgungsamt oder Landratsamt */
    val office: String = "",
    val reference: String = "",
) {
    val isSet get() = gdb > 0 || marks.isNotEmpty()
    val severe get() = gdb >= 50

    fun encode(): String = KeyValues.encode(
        mapOf(
            "gdb" to gdb.takeIf { it > 0 }?.toString(),
            "mz" to marks.sorted().joinToString(",").ifBlank { null },
            "bis" to validUntil?.toString(),
            "unbefristet" to if (unlimited) "1" else null,
            "amt" to office,
            "az" to reference,
        ),
    )

    /** „GdB 80, Merkzeichen G, B · gültig bis 11/2028“ */
    fun short(): String = listOfNotNull(
        gdb.takeIf { it > 0 }?.let { "GdB $it" },
        marks.takeIf { it.isNotEmpty() }?.let { "Merkzeichen " + MARKS.keys.filter { k -> k in it }.joinToString(", ") },
        when {
            unlimited -> "unbefristet"
            validUntil != null -> LocalDate.ofEpochDay(validUntil).let { "gültig bis %02d/%d".format(it.monthValue, it.year) }
            else -> null
        },
    ).joinToString(" · ")

    companion object {
        /** Merkzeichen mit kurzer Erklärung. */
        val MARKS: Map<String, String> = linkedMapOf(
            "G" to "erheblich gehbehindert",
            "aG" to "außergewöhnlich gehbehindert",
            "H" to "hilflos",
            "B" to "Begleitperson berechtigt",
            "Bl" to "blind",
            "Gl" to "gehörlos",
            "TBl" to "taubblind",
            "RF" to "Rundfunkbeitrag ermäßigt",
            "1.Kl." to "1. Klasse Bahn",
        )

        fun decode(text: String): Disability {
            val m = KeyValues.decode(text)
            return Disability(
                gdb = m["gdb"]?.toIntOrNull() ?: 0,
                marks = m["mz"]?.split(',')?.map { it.trim() }?.filter { it in MARKS }?.toSet().orEmpty(),
                validUntil = m["bis"]?.toLongOrNull(),
                unlimited = m["unbefristet"] == "1",
                office = m["amt"].orEmpty(),
                reference = m["az"].orEmpty(),
            )
        }

        /** Behinderten-Pauschbetrag nach § 33b EStG (Stand ab 2021). Anhaltspunkt, bitte beim Finanzamt prüfen. */
        fun taxAllowance(d: Disability): Int? = when {
            "H" in d.marks || "Bl" in d.marks || "TBl" in d.marks -> 7400
            d.gdb >= 100 -> 2840
            d.gdb >= 90 -> 2460
            d.gdb >= 80 -> 2120
            d.gdb >= 70 -> 1780
            d.gdb >= 60 -> 1440
            d.gdb >= 50 -> 1140
            d.gdb >= 40 -> 860
            d.gdb >= 30 -> 620
            d.gdb >= 20 -> 384
            else -> null
        }

        /** Mögliche Nachteilsausgleiche als Anhaltspunkte (Einzelheiten bei Versorgungsamt, Finanzamt, Verkehrsbetrieb). */
        fun benefits(d: Disability): List<String> = buildList {
            taxAllowance(d)?.let { add("Behinderten-Pauschbetrag bei der Steuer: etwa $it € im Jahr (Steuererklärung).") }
            if ("G" in d.marks || "aG" in d.marks || "H" in d.marks || "Bl" in d.marks || "Gl" in d.marks) {
                add("Freifahrt im Nahverkehr mit Wertmarke oder alternativ Ermäßigung der Kfz-Steuer.")
            }
            if ("aG" in d.marks) add("Blauer EU-Parkausweis für Behindertenparkplätze (bei der Straßenverkehrsbehörde).")
            if ("aG" in d.marks || "H" in d.marks || "Bl" in d.marks) add("Befreiung von der Kfz-Steuer möglich.")
            if ("B" in d.marks) add("Eine Begleitperson fährt in Bus und Bahn kostenlos mit.")
            if ("RF" in d.marks) add("Rundfunkbeitrag ermäßigt (Antrag beim Beitragsservice).")
            if ("Bl" in d.marks) add("Blindengeld bzw. Blindenhilfe (je nach Bundesland).")
            if (d.severe) add("Ab GdB 50: Schwerbehinderung – z. B. Zusatzurlaub und besonderer Kündigungsschutz bei Arbeit, früherer Rentenbeginn möglich.")
            if (d.gdb in 30..40) add("GdB 30 oder 40: Gleichstellung mit Schwerbehinderten bei der Agentur für Arbeit möglich, wenn es um den Arbeitsplatz geht.")
        }

        private val GDB = Regex("(?:GdB|Grad der Behinderung)\\D{0,15}(\\d{2,3})", RegexOption.IGNORE_CASE)
        private val VALID = Regex("gültig\\s*bis\\D{0,8}(\\d{1,2})\\s*[./]\\s*(\\d{2,4})", RegexOption.IGNORE_CASE)

        /** Angaben aus dem erkannten Text eines Ausweis-Fotos lesen (Vorschlag, bitte prüfen). */
        fun fromText(text: String, today: LocalDate = LocalDate.now()): Disability? {
            if (!Regex("schwerbehind|grad der behinderung|\\bGdB\\b|merkzeichen", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
            val gdb = GDB.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 20..100 } ?: 0
            val unlimited = Regex("unbefristet", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val until = VALID.find(text)?.let { m ->
                val mo = m.groupValues[1].toInt()
                val y = m.groupValues[2].toInt().let { if (it < 100) 2000 + it else it }
                runCatching { YearMonth.of(y, mo).atEndOfMonth().toEpochDay() }.getOrNull()
            }
            // Merkzeichen: Zeile nach „Merkzeichen“ oder einzeln stehende Kürzel
            val after = Regex("merkzeichen[:\\s]*([A-Za-z.\\s,]{1,30})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1).orEmpty()
            val marks = MARKS.keys.filter { k ->
                Regex("(?<![A-Za-z])" + Regex.escape(k) + "(?![A-Za-z])").containsMatchIn(after)
            }.toSet()
            return Disability(gdb, marks, if (unlimited) null else until, unlimited)
        }

        /** Tage bis zum Ablauf (negativ = abgelaufen), null = unbefristet oder unbekannt. */
        fun daysLeft(d: Disability, today: Long): Long? = if (d.unlimited) null else d.validUntil?.let { it - today }
    }
}

/** Wohnsituation und Tagesstruktur einer betreuten Person. */
data class Household(
    /** Weitere Menschen im Haushalt (ohne die Person selbst) nach Alter */
    val under6: Int = 0,
    val age6to17: Int = 0,
    val age18to64: Int = 0,
    val age65plus: Int = 0,
    /** Wohnt die pflegende Person (du) mit im Haushalt? null = nicht angegeben */
    val caregiverHere: Boolean? = null,
    /** Andere in der App angelegte Personen, die im selben Haushalt leben */
    val withPersons: Set<Long> = emptySet(),
    /** Tagsüber: Schlüssel aus [DAYTIME] */
    val daytime: String = "",
    /** Name der Einrichtung, z. B. „Lebenshilfe-Werkstatt“ */
    val place: String = "",
    /** Wochentage 1 = Montag … 7 = Sonntag */
    val days: Set<Int> = emptySet(),
    val from: String = "",
    val to: String = "",
    /** Wird abgeholt und gebracht (Fahrdienst der Einrichtung) */
    val transport: Boolean = false,
    /** Wohnform, Schlüssel aus [LIVING] */
    val living: String = "",
) {
    val others get() = under6 + age6to17 + age18to64 + age65plus
    val hasDaytime get() = daytime.isNotBlank() && daytime != "zuhause" && days.isNotEmpty() && from.isNotBlank() && to.isNotBlank()

    fun encode(): String = KeyValues.encode(
        mapOf(
            "u6" to under6.takeIf { it > 0 }?.toString(),
            "u18" to age6to17.takeIf { it > 0 }?.toString(),
            "u65" to age18to64.takeIf { it > 0 }?.toString(),
            "a65" to age65plus.takeIf { it > 0 }?.toString(),
            "du" to caregiverHere?.let { if (it) "1" else "0" },
            "mit" to withPersons.sorted().joinToString(",").ifBlank { null },
            "tag" to daytime,
            "ort" to place,
            "tage" to days.sorted().joinToString(",").ifBlank { null },
            "von" to from,
            "bis" to to,
            "fahrt" to if (transport) "1" else null,
            "wohnt" to living,
        ),
    )

    /** Kurzbeschreibung für Profil, Übergabe und Sunny. */
    fun short(nameOf: (Long) -> String?): String = listOfNotNull(
        LIVING[living],
        others.takeIf { it > 0 }?.let { n ->
            "$n weitere im Haushalt (" + listOfNotNull(
                under6.takeIf { it > 0 }?.let { "$it unter 6" },
                age6to17.takeIf { it > 0 }?.let { "$it 6–17" },
                age18to64.takeIf { it > 0 }?.let { "$it 18–64" },
                age65plus.takeIf { it > 0 }?.let { "$it ab 65" },
            ).joinToString(", ") + ")"
        },
        withPersons.mapNotNull(nameOf).takeIf { it.isNotEmpty() }?.let { "lebt mit " + it.joinToString(", ") },
        caregiverHere?.let { if (it) "du wohnst mit im Haushalt" else "du wohnst nicht im Haushalt" },
        DAYTIME[daytime]?.takeIf { daytime != "zuhause" }?.let { d ->
            d + (if (place.isNotBlank()) " ($place)" else "") +
                (if (days.isNotEmpty()) " " + dayText(days) else "") +
                (if (from.isNotBlank() && to.isNotBlank()) " $from–$to" else "") +
                (if (transport) ", mit Fahrdienst" else "")
        },
    ).joinToString(" · ")

    companion object {
        val DAYTIME: Map<String, String> = linkedMapOf(
            "zuhause" to "tagsüber zu Hause",
            "werkstatt" to "Werkstatt für behinderte Menschen",
            "tagesfoerderstaette" to "Tagesförderstätte",
            "tagespflege" to "Tagespflege",
            "schule" to "Schule / Förderschule",
            "kita" to "Kita / Kindergarten",
            "arbeit" to "Arbeit / Ausbildung",
            "sonstiges" to "andere Betreuung",
        )
        val LIVING: Map<String, String> = linkedMapOf(
            "allein" to "lebt allein",
            "familie" to "lebt mit Familie",
            "partner" to "lebt mit Partner/in",
            "wg" to "Wohngemeinschaft / betreutes Wohnen",
            "heim" to "Pflegeheim / Wohnheim",
        )
        private val DAY_NAMES = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

        fun dayText(days: Set<Int>): String {
            val s = days.sorted()
            return if (s.size >= 3 && s.zipWithNext().all { (a, b) -> b == a + 1 }) DAY_NAMES[s.first() - 1] + "–" + DAY_NAMES[s.last() - 1]
            else s.joinToString(", ") { DAY_NAMES[it - 1] }
        }

        fun decode(text: String): Household {
            val m = KeyValues.decode(text)
            fun int(k: String) = m[k]?.toIntOrNull() ?: 0
            return Household(
                under6 = int("u6"), age6to17 = int("u18"), age18to64 = int("u65"), age65plus = int("a65"),
                caregiverHere = m["du"]?.let { it == "1" },
                withPersons = m["mit"]?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty(),
                daytime = m["tag"].orEmpty(),
                place = m["ort"].orEmpty(),
                days = m["tage"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }?.toSet().orEmpty(),
                from = m["von"].orEmpty(),
                to = m["bis"].orEmpty(),
                transport = m["fahrt"] == "1",
                living = m["wohnt"].orEmpty(),
            )
        }
    }
}
