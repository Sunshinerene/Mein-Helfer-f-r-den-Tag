package de.meinhelfer.tag.summary

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Einheit eines Medikaments für Vorrat, Verbrauch und Menge je Einnahme. Gespeichert wird [key]. */
enum class MedUnit(val key: String, val examples: String) {
    Stueck("Stück", "Tabletten, Kapseln, Zäpfchen, Pflaster, Beutel"),
    Ml("ml", "Saft, Lösung, Suspension"),
    IE("IE", "Insulin, Heparin (Internationale Einheiten)"),
    Tropfen("Tropfen", "Tropfen zum Einnehmen, Augen- oder Ohrentropfen"),
    Hub("Hub", "Spray, Inhalator, Dosieraerosol"),
    Gramm("g", "Salbe, Creme, Gel"),
    ;

    /** Kurzer Hinweis zum Eintragen, nur als Anhaltspunkt. */
    val hint: String
        get() = when (this) {
            Stueck -> "Vorrat in Stück. Halbe Tabletten gehen auch, z. B. 0,5 oder ½."
            Ml -> "Vorrat und Mengen in ml. Beispiel: Eine Flasche mit 100 ml reicht bei 3 × 5 ml am Tag etwa 6 Tage."
            IE -> "Vorrat und Mengen in IE (Internationale Einheiten). Ein Pen oder eine Patrone mit 3 ml U100 enthält 300 IE, " +
                "5 Pens also 1500 IE. Beim Entlüften vor jeder Spritze gehen meist ein paar IE verloren (siehe Gebrauchsanweisung); " +
                "wer es genau will, rechnet das bei „Pro Tag“ mit ein."
            Tropfen -> "Vorrat in Tropfen. Als Faustregel sind etwa 20 Tropfen 1 ml (je nach Mittel, siehe Packungsbeilage), 30 ml also etwa 600 Tropfen."
            Hub -> "Vorrat in Hüben (Sprühstößen) laut Packung, z. B. 200 Hub."
            Gramm -> "Vorrat in g. Der Verbrauch von Salben ist nur geschätzt."
        }

    companion object {
        fun of(key: String?): MedUnit = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: fromText(key.orEmpty()) ?: Stueck

        /** Einheit aus einem Medikamentenplan oder von der Packung ("Stück", "IE", "ml", "Trpf.", "Hub" …), sonst null. */
        fun fromText(raw: String): MedUnit? {
            val t = raw.trim().lowercase(Locale.GERMANY).trimEnd('.')
            return when {
                t.isEmpty() -> null
                t == "ml" || t.endsWith(" ml") || t == "milliliter" -> Ml
                t == "ie" || t == "i.e" || t == "e" || t.startsWith("einheit") || t == "iu" -> IE
                t.startsWith("trpf") || t.startsWith("tropf") || t == "gtt" -> Tropfen
                t.startsWith("hub") || t.startsWith("hüb") || t.startsWith("sprühstoß") || t.startsWith("sprühst") || t == "spr" -> Hub
                t == "g" || t == "gramm" -> Gramm
                t.startsWith("st") || t.startsWith("tbl") || t.startsWith("tabl") || t.startsWith("kps") || t.startsWith("kaps") ||
                    t.startsWith("supp") || t.startsWith("zäpf") || t.startsWith("btl") || t.startsWith("beutel") || t.startsWith("pfl") -> Stueck
                else -> null
            }
        }
    }
}

/** Mengen als Kommazahl: lesen ("1,5", "½", "1½") und schreiben ("1,5", "14"). */
object Amounts {
    fun parse(text: String): Double? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        var whole = 0.0
        var rest = t
        if (rest.length > 1 && rest.last() in "½¼¾") {
            whole = rest.dropLast(1).replace(',', '.').toDoubleOrNull() ?: return null
            rest = rest.last().toString()
        }
        val part = when (rest) {
            "½", "1/2" -> 0.5
            "¼", "1/4" -> 0.25
            "¾", "3/4" -> 0.75
            else -> rest.replace(',', '.').toDoubleOrNull() ?: return null
        }
        return (whole + part).takeIf { it >= 0 && it.isFinite() }
    }

    /** Höchstens zwei Nachkommastellen, ohne überflüssige Nullen: 14,0 → "14", 2,50 → "2,5". */
    fun fmt(v: Double): String {
        val rounded = (v * 100).roundToLong() / 100.0
        if (abs(rounded - rounded.roundToLong()) < 1e-9) return rounded.roundToLong().toString()
        return String.format(Locale.GERMANY, "%.2f", rounded).trimEnd('0').trimEnd(',')
    }

    fun withUnit(v: Double, unit: String): String = fmt(v) + " " + unit
}

/** Menge und Einheit aus einer Dosierung wie „5 ml“, „½ Tablette“, „10 Tropfen“ oder „8 IE“, sonst null. */
object DosageText {
    private val RE = Regex(
        """^\s*(\d{0,3}(?:[.,]\d{1,2})?\s?[½¼¾]?)\s*(ml|IE|I\.E\.|E|Einheiten|Tropfen|Trpf\.?|Hub|Hübe|Sprühstöße?|g|Tabletten|Tablette|Tbl\.?|Kapseln|Kapsel|Stück|Beutel|Pflaster|Zäpfchen|Dragees?)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): Pair<Double, MedUnit>? {
        val m = RE.find(text) ?: return null
        val amount = Amounts.parse(m.groupValues[1]) ?: return null
        if (amount <= 0) return null
        val unit = MedUnit.fromText(m.groupValues[2]) ?: return null
        return amount to unit
    }
}

/** Menge je Einnahmezeit, gespeichert als "08:00=8,18:00=6" (Punkt als Dezimaltrenner). */
object Doses {
    private fun norm(time: String): String? {
        val parts = time.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return String.format(Locale.ROOT, "%02d:%02d", h, m)
    }

    fun parse(doses: String): Map<String, Double> =
        doses.split(",").mapNotNull { part ->
            val t = part.substringBefore("=", "")
            val v = part.substringAfter("=", "").trim().toDoubleOrNull()
            val key = norm(t)
            if (key != null && v != null && v >= 0) key to v else null
        }.toMap()

    fun format(map: Map<String, Double>): String =
        map.mapNotNull { (k, v) -> norm(k)?.let { it to v } }
            .sortedBy { it.first }
            .joinToString(",") { (k, v) -> k + "=" + (if (v == v.roundToLong().toDouble()) v.roundToLong().toString() else v.toString()) }

    /** Summe am Tag, wenn für jede Einnahmezeit eine Menge da ist, sonst null. */
    fun daily(times: List<String>, map: Map<String, Double>): Double? {
        val keys = times.mapNotNull { norm(it) }.distinct()
        if (keys.isEmpty() || keys.any { it !in map }) return null
        return keys.sumOf { map.getValue(it) }
    }

    /** Menge für eine Einnahmezeit ("8:00" und "08:00" gelten gleich). */
    fun at(map: Map<String, Double>, slot: String): Double? = norm(slot)?.let { map[it] }

    /** Kurzer Text: "je 5 ml" oder "08:00 8 IE, 18:00 6 IE"; null ohne Angaben. */
    fun summary(times: List<String>, map: Map<String, Double>, unit: String): String? {
        val keys = times.mapNotNull { norm(it) }.distinct().sorted().filter { it in map }
        if (keys.isEmpty()) return null
        val values = keys.map { map.getValue(it) }
        return if (values.distinct().size == 1) {
            "je " + Amounts.withUnit(values.first(), unit)
        } else {
            keys.joinToString(", ") { k -> k + " " + Amounts.withUnit(map.getValue(k), unit) }
        }
    }
}

/** Inhalt einer Packung, aus dem Text auf der Packung gelesen. */
data class PackInfo(val amount: Double, val unit: MedUnit)

/** Liest aus dem Packungstext, wie viel drin ist: „100 ml“, „5 x 3 ml“ mit „100 E/ml“ (Insulin), „200 Hub“, „20 Tabletten“. */
object PackText {
    private val COUNT = Regex(
        """(\d{1,3})\s?(?:Tabletten|Filmtabletten|Retardtabletten|Brausetabletten|Tabl\.|Kapseln|Hartkapseln|Weichkapseln|Stück|St\.|Dragees|Beutel|Ampullen|Zäpfchen|Suppositorien|Pflaster)""",
        RegexOption.IGNORE_CASE,
    )
    private val MULTI_ML = Regex("""(\d{1,2})\s?[x×]\s?(\d{1,4}(?:[.,]\d{1,2})?)\s?ml\b""", RegexOption.IGNORE_CASE)
    private val ML = Regex("""(?<![/\d.,])(?<!/\s)(\d{1,4}(?:[.,]\d{1,2})?)\s?ml\b""", RegexOption.IGNORE_CASE)
    private val IE_PER_ML = Regex("""(\d{2,4})\s?(?:I\.\s?E\.|IE|E|Einheiten|U|IU)\s?/\s?ml""", RegexOption.IGNORE_CASE)
    private val HUB = Regex("""(\d{2,4})\s?(?:Hübe|Hub|Sprühstöße|Sprühstösse|Einzeldosen|Dosen)\b""", RegexOption.IGNORE_CASE)
    private val GRAMM = Regex("""(?<![/\d.,])(\d{1,4})\s?g\b(?!\s?/)""", RegexOption.IGNORE_CASE)
    private val CREAM = Regex("""(?i)salbe|creme|gel\b|paste""")

    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    fun parse(text: String): PackInfo? {
        val multi = MULTI_ML.find(text)
        val volume = multi?.let { (num(it.groupValues[1]) ?: 0.0) * (num(it.groupValues[2]) ?: 0.0) }
            ?: ML.findAll(text).mapNotNull { num(it.groupValues[1]) }.maxOrNull()
        val iePerMl = IE_PER_ML.find(text)?.let { num(it.groupValues[1]) }
        if (volume != null && volume > 0 && iePerMl != null && iePerMl > 0) return PackInfo(volume * iePerMl, MedUnit.IE)
        HUB.find(text)?.let { m -> num(m.groupValues[1])?.let { return PackInfo(it, MedUnit.Hub) } }
        COUNT.find(text)?.let { m -> num(m.groupValues[1])?.let { return PackInfo(it, MedUnit.Stueck) } }
        if (volume != null && volume > 0) return PackInfo(volume, MedUnit.Ml)
        if (CREAM.containsMatchIn(text)) GRAMM.find(text)?.let { m -> num(m.groupValues[1])?.let { return PackInfo(it, MedUnit.Gramm) } }
        return null
    }
}
