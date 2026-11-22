package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/** Ein Medikament aus einem Medikamentenplan. [amounts] = morgens, mittags, abends, zur Nacht ("" = keine Angabe). */
data class PlanMed(
    val name: String,
    val strength: String = "",
    val form: String = "",
    val amounts: List<String> = listOf("", "", "", ""),
    /** Freitext-Dosierung, wenn es kein Schema gibt (z. B. "bei Bedarf", "1x wöchentlich") */
    val freeSchema: String = "",
    val unit: String = "",
    val notes: String = "",
    val reason: String = "",
    val pzn: String = "",
    /** Name fehlt im Code (nur PZN) und muss ergaenzt werden */
    val nameMissing: Boolean = false,
) {
    private fun value(a: String): Double = MedPlans.amount(a) ?: 0.0

    val hasSchema: Boolean get() = amounts.any { it.isNotBlank() }

    /** "1-0-1-0" bzw. Freitext */
    fun schemaText(): String = if (hasSchema) amounts.joinToString("-") { it.ifBlank { "0" } } else freeSchema

    fun times(): List<String> = if (!hasSchema) emptyList() else amounts.mapIndexedNotNull { i, a -> if (value(a) > 0) MedPlans.SLOT_TIMES[i] else null }

    fun dosage(): String = listOf(strength, listOf(schemaText(), unit).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" · ")

    fun dailyUse(): Int = if (!hasSchema) 1 else ceil(amounts.sumOf { value(it) }).toInt().coerceAtLeast(0)

    fun noteText(): String = listOf(notes, reason.takeIf { it.isNotBlank() }?.let { "Grund: $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
}

data class MedPlan(
    /** Stand des Plans (Epoch-Tag), falls erkannt */
    val day: Long?,
    val meds: List<PlanMed>,
    /** "Code" (Barcode auf dem Plan), "Text" (Texterkennung) oder "KI" */
    val source: String,
)

/** Vergleich eines Plan-Eintrags mit der Medikamentenliste. */
enum class PlanStatus(val label: String) { New("Neu"), Changed("Geändert"), Same("Unverändert") }

data class PlanMatch(val med: PlanMed, val existing: Medication?, val status: PlanStatus)

object MedPlans {
    val SLOT_TIMES = listOf("08:00", "12:00", "18:00", "22:00")
    val SLOT_LABELS = listOf("morgens", "mittags", "abends", "zur Nacht")

    private const val AMT = """(?:\d{1,2}(?:[.,]\d{1,2})?|\d?\s?[½¼¾]|\d?\s?1/2|1/4|3/4)"""
    private val SCHEMA = Regex("""(?<![\d./])($AMT)\s*[-–]\s*($AMT)\s*[-–]\s*($AMT)(?:\s*[-–]\s*($AMT))?(?![\d./])""")
    private val STRENGTH = Regex("""(\d+(?:[.,]\d+)?\s?(?:mg|µg|mcg|g|ml|I\.?\s?E\.?|IE|%|mmol)(?:\s?/\s?\d*\s?(?:ml|h|g))?)\b""", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""")
    private val PLAN_DATE = Regex("""(?i)(ausgedruckt|ausdruck|erstellt|stand|gedruckt|datum)\D{0,25}(\d{1,2}\.\d{1,2}\.\d{2,4})""")
    private val FORMS = listOf("Filmtabletten", "Tabletten", "Tablette", "Tabl.", "Tbl.", "Kapseln", "Kapsel", "Hartkapseln", "Tropfen", "Lösung", "Saft", "Spray", "Pflaster", "Salbe", "Creme", "Brausetabletten", "Retardtabletten", "Dragees", "Granulat", "Pen", "Injektionslösung", "Zäpfchen")
    private val SKIP = listOf("medikationsplan", "medikamentenplan", "wirkstoff", "handelsname", "stärke", "form", "hinweise", "grund", "seite", "patient", "ausgedruckt", "geb.", "geboren", "telefon", "e-mail", "dosierung", "einheit", "morgens", "mittags", "abends", "nachts")

    /** "1", "0,5", "½", "1/2", "1½" -> Zahl */
    fun amount(raw: String): Double? {
        val t = raw.trim().replace(" ", "")
        if (t.isEmpty() || t == "-") return null
        var whole = 0.0
        var rest = t
        if (rest.length > 1 && rest.last() in "½¼¾") {
            whole = rest.dropLast(1).replace(',', '.').toDoubleOrNull() ?: 0.0
            rest = rest.last().toString()
        }
        return whole + when (rest) {
            "½", "1/2" -> 0.5
            "¼", "1/4" -> 0.25
            "¾", "3/4" -> 0.75
            else -> rest.replace(',', '.').toDoubleOrNull() ?: return null
        }
    }

    private fun date(d: String, m: String, y: String): LocalDate? = runCatching {
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        LocalDate.of(year, m.toInt(), d.toInt())
    }.getOrNull()

    /** Schema aus einer Dosierung wie "47,5 mg · 1-0-1-0 Stk.": Mengen (morgens, mittags, abends, Nacht) und der Rest. */
    fun schemaOf(dosage: String): Pair<List<String>, String>? {
        val m = SCHEMA.find(dosage) ?: return null
        val amounts = (1..4).map { m.groupValues.getOrNull(it).orEmpty().trim() }
        val rest = (dosage.substring(0, m.range.first) + " " + dosage.substring(m.range.last + 1))
            .replace("·", " ").replace(Regex("\\bStk\\.?"), "").replace(Regex("\\s{2,}"), " ").trim().trim(',', '-', '·')
        return amounts to rest
    }

    /** Sieht der Text nach einem Medikamentenplan aus? */
    fun looksLikePlan(text: String): Boolean {
        val t = text.lowercase()
        if ("medikationsplan" in t || "medikamentenplan" in t) return true
        return text.lines().count { SCHEMA.containsMatchIn(it) } >= 2
    }

    fun isPlanDocument(doc: Document): Boolean =
        doc.planCode.isNotBlank() || (doc.text.isNotBlank() && (doc.category == "Medikamentenplan" || looksLikePlan(doc.text)))

    /** Plan aus dem beim Hochladen gelesenen Code, null = kein (lesbarer) Code. */
    fun fromCode(doc: Document): MedPlan? =
        doc.planCode.takeIf { it.isNotBlank() }?.let { parseBmp(it) }?.takeIf { it.meds.isNotEmpty() }

    /** Stand des Plans: aus dem Text ("Ausgedruckt am ..."), sonst Datum des Dokuments, sonst Tag des Hochladens. */
    fun planDay(doc: Document, plan: MedPlan? = null, zone: ZoneId = ZoneId.systemDefault()): Long {
        plan?.day?.let { return it }
        fromCode(doc)?.day?.let { return it }
        PLAN_DATE.find(doc.text)?.groupValues?.get(2)?.let { s ->
            DATE.find(s)?.let { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { return it.toEpochDay() } }
        }
        doc.docDate?.let { return it }
        return Instant.ofEpochMilli(doc.createdMillis).atZone(zone).toLocalDate().toEpochDay()
    }

    private fun cleanName(raw: String): Pair<String, String> {
        var name = raw.replace(Regex("""^[\s\d.)•*\-–|]+"""), "").replace(Regex("""\s{2,}"""), " ").trim().trim('|', '-', ':', ',')
        var form = ""
        FORMS.firstOrNull { name.contains(it, ignoreCase = true) }?.let { f ->
            form = f
            name = name.replace(Regex(Regex.escape(f), RegexOption.IGNORE_CASE), "").trim().trim(',', '-')
        }
        return name.replace(Regex("""\s{2,}"""), " ").trim() to form
    }

    private fun nameLike(s: String): Boolean {
        val letters = s.count { it.isLetter() }
        val low = s.lowercase()
        return letters >= 3 && SKIP.none { low.startsWith(it) } && !DATE.containsMatchIn(s)
    }

    /** Liest einen Plan aus erkanntem Text (Zeile fuer Zeile). Nur ein Vorschlag, alles wird vorher geprueft. */
    fun parseText(text: String): MedPlan {
        val meds = mutableListOf<PlanMed>()
        var pending: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val schema = SCHEMA.find(line)
            if (schema != null) {
                val before = line.substring(0, schema.range.first)
                val after = line.substring(schema.range.last + 1).trim().trim('|', '-', ',')
                val source = if (nameLike(before)) before else pending ?: continue
                val strength = STRENGTH.find(source)?.value?.trim() ?: STRENGTH.find(line)?.value?.trim().orEmpty()
                val namePart = STRENGTH.find(source)?.let { source.substring(0, it.range.first) + " " + source.substring(it.range.last + 1) } ?: source
                val (name, form) = cleanName(namePart)
                if (name.count { it.isLetter() } < 3) continue
                val amounts = (1..4).map { schema.groupValues.getOrNull(it).orEmpty().trim() }
                meds += PlanMed(
                    name = name.take(80),
                    strength = strength,
                    form = form,
                    amounts = amounts,
                    unit = if (form.isNotBlank()) "Stück" else "",
                    notes = after.takeIf { it.count(Char::isLetter) >= 3 }?.take(120).orEmpty(),
                )
                pending = null
            } else {
                // Zeile mit Name und Staerke, das Schema steht vielleicht in der naechsten Zeile
                pending = if (STRENGTH.containsMatchIn(line) && nameLike(line)) line else pending?.takeIf { line.length < 4 }
            }
        }
        val day = PLAN_DATE.find(text)?.groupValues?.get(2)?.let { s -> DATE.find(s)?.let { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3]) } }
        return MedPlan(day?.toEpochDay(), meds.distinctBy { it.name.lowercase() + it.strength }, "Text")
    }

    /**
     * Liest den Barcode (Data Matrix) des bundeseinheitlichen Medikationsplans. Bei Eintraegen nur mit PZN fehlt der Name
     * und muss ergaenzt werden.
     */
    fun parseBmp(xml: String): MedPlan? {
        if (!xml.trimStart().startsWith("<MP")) return null
        return runCatching {
            val parser = android.util.Xml.newPullParser()
            parser.setInput(java.io.StringReader(xml))
            val meds = mutableListOf<PlanMed>()
            var day: Long? = null
            var current: PlanMed? = null
            var event = parser.eventType
            fun attr(n: String) = parser.getAttributeValue(null, n)?.trim().orEmpty()
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "A" -> attr("t").takeIf { it.length >= 10 }?.let { t -> day = runCatching { LocalDate.parse(t.take(10)).toEpochDay() }.getOrNull() }
                        "M" -> {
                            current?.let { meds += it }
                            val pzn = attr("p")
                            val name = attr("a")
                            val unitCode = attr("du")
                            current = PlanMed(
                                name = name.ifBlank { if (pzn.isNotBlank()) "PZN $pzn" else "" },
                                form = attr("fd").ifBlank { attr("f") },
                                amounts = listOf(attr("m"), attr("d"), attr("v"), attr("h")),
                                freeSchema = attr("t"),
                                unit = attr("dud").ifBlank { if (unitCode == "1") "Stück" else "" },
                                notes = attr("i"),
                                reason = attr("r"),
                                pzn = pzn,
                                nameMissing = name.isBlank(),
                            )
                        }
                        "W" -> current = current?.let { c ->
                            val w = attr("w")
                            val s = attr("s")
                            c.copy(
                                name = if (c.nameMissing && w.isNotBlank()) w else c.name,
                                strength = listOf(c.strength, s).filter { it.isNotBlank() }.joinToString(" / "),
                                nameMissing = c.nameMissing && w.isBlank(),
                            )
                        }
                        "S", "X", "R" -> {
                            current?.let { meds += it }
                            current = null
                        }
                    }
                }
                event = parser.next()
            }
            current?.let { meds += it }
            MedPlan(day, meds.filter { it.name.isNotBlank() }, "Code")
        }.getOrNull()
    }

    private fun key(name: String): String =
        name.lowercase().replace(Regex("[^a-zäöüß ]"), " ").split(" ").firstOrNull { it.length >= 3 }.orEmpty()

    /** Ordnet Plan-Eintraege den vorhandenen Medikamenten zu. */
    fun match(plan: MedPlan, meds: List<Medication>): Pair<List<PlanMatch>, List<Medication>> {
        val used = mutableSetOf<Long>()
        val matches = plan.meds.map { pm ->
            val k = key(pm.name)
            val existing = meds.firstOrNull { m -> m.id !in used && ((pm.pzn.isNotBlank() && m.pzn == pm.pzn) || (k.isNotBlank() && key(m.name) == k)) }
                ?: meds.firstOrNull { m -> m.id !in used && k.length >= 5 && m.name.lowercase().contains(k) }
            existing?.let { used += it.id }
            val status = when {
                existing == null -> PlanStatus.New
                existing.dosage.trim().equals(pm.dosage().trim(), ignoreCase = true) &&
                    existing.timeList().sorted() == pm.times().sorted() -> PlanStatus.Same
                else -> PlanStatus.Changed
            }
            PlanMatch(pm, existing, status)
        }
        return matches to meds.filter { it.id !in used }
    }
}
