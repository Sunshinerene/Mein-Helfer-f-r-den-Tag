package de.meinhelfer.tag.export

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.summary.MedPlans
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.Vitals
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Notfallkarte und Medikamentenplan als PDF zum Ausdrucken. Entstehen nur auf dem Handy. */
object CarePdfs {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    private val RED = Color.rgb(176, 32, 32)
    private const val MM = 72f / 25.4f

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "_").trim('_').ifBlank { "Person" }

    private fun address(p: Person) =
        listOf(p.street, listOf(p.zip, p.city).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ")

    /** Hausarzt fuer die Kopfzeile: Fachrichtung mit "Allgemein"/"Haus", sonst der erste Arzt, der keine Apotheke ist. */
    private fun familyDoctor(doctors: List<Doctor>): Doctor? =
        doctors.firstOrNull { d -> listOf("allgemein", "haus").any { d.specialty.contains(it, ignoreCase = true) } }
            ?: doctors.firstOrNull { !it.specialty.contains("apotheke", ignoreCase = true) }

    // ------------------------------------------------------------------ Notfallkarte

    fun emergency(
        context: Context,
        person: Person?,
        meds: List<Medication>,
        doctors: List<Doctor>,
        caregiver: CaregiverProfile,
        withInsuranceNumber: Boolean,
    ): File {
        val file = File(PdfPages.dir(context, "notfall"), "Notfallkarte_${safe(person?.name ?: "")}.pdf")
        val pdf = PdfPages(595, 842, margin = 44f, footer = "Notfallkarte · Mein Helfer für den Tag")
        val own = meds.filter { person == null || it.personId == person.id }
        val contacts = doctors.filter { it.phone.isNotBlank() }
        pdf.newPage()

        // Kopf
        pdf.text("NOTFALLKARTE", 24f, bold = true, color = RED)
        pdf.gap(4f)
        person?.let { p ->
            pdf.text(p.name, 20f, bold = true)
            val facts = listOfNotNull(
                p.birthDate?.let { "geboren am " + LocalDate.ofEpochDay(it).format(DATE) },
                p.careLevel.takeIf { it in 1..5 }?.let { "Pflegegrad $it" },
            ).joinToString(" · ")
            if (facts.isNotBlank()) pdf.text(facts, 12f)
            pdf.gap(8f)
            val lines = listOfNotNull(
                address(p).takeIf { it.isNotBlank() }?.let { "Adresse: $it" },
                p.phone.takeIf { it.isNotBlank() }?.let { "Telefon: $it" },
                de.meinhelfer.tag.ui.accessSummary(p.floor, p.elevator, p.access).takeIf { it.isNotBlank() }?.let { "Zugang: $it" },
                p.insurer.takeIf { it.isNotBlank() }?.let { "Krankenkasse: $it" + if (withInsuranceNumber && p.insuranceNumber.isNotBlank()) ", Versichertennummer ${p.insuranceNumber}" else "" },
            )
            lines.forEach { pdf.text(it, 11f) }
            if (p.important.isNotBlank()) {
                pdf.gap(10f)
                val l = pdf.layout(p.important.trim(), pdf.paint(11.5f), pdf.width - 24)
                val head = pdf.layout("Wichtig zu wissen", pdf.paint(12f, bold = true, color = RED), pdf.width - 24)
                val hgt = head.height + l.height + 20f
                pdf.ensure(hgt)
                val top = pdf.y
                pdf.box(RectF(pdf.left, top, pdf.right, top + hgt), stroke = RED, width = 1.4f)
                pdf.draw(head, pdf.left + 12f, top + 8f)
                pdf.draw(l, pdf.left + 12f, pdf.y + 4f)
                pdf.y = top + hgt
            }
        }

        if (own.isNotEmpty()) {
            pdf.gap(14f)
            pdf.text("Medikamente", 13f, bold = true)
            pdf.gap(2f)
            pdf.rule()
            pdf.gap(4f)
            own.sortedBy { it.timeList().firstOrNull() ?: "99" }.forEach { m ->
                val times = m.timeList().joinToString(", ")
                pdf.text("• " + m.name + (if (m.dosage.isNotBlank()) " – ${m.dosage}" else "") + (if (times.isNotBlank()) " · $times Uhr" else "") + (m.doseSummary()?.let { " · $it" } ?: ""), 10.5f)
            }
        }
        if (contacts.isNotEmpty()) {
            pdf.gap(14f)
            pdf.text("Ärzte und Kontakte", 13f, bold = true)
            pdf.gap(2f)
            pdf.rule()
            pdf.gap(4f)
            contacts.forEach { d -> pdf.text("• " + d.name + (if (d.specialty.isNotBlank()) " (${d.specialty})" else "") + ": " + d.phone, 10.5f) }
        }
        if (caregiver.isSet && (caregiver.name.isNotBlank() || caregiver.phone.isNotBlank())) {
            pdf.gap(14f)
            pdf.text("Ansprechperson", 13f, bold = true)
            pdf.gap(2f)
            pdf.rule()
            pdf.gap(4f)
            pdf.text(
                listOfNotNull(
                    caregiver.name.ifBlank { null },
                    caregiver.roleValue?.label,
                    caregiver.phone.ifBlank { null },
                    caregiver.address.ifBlank { null },
                ).joinToString(" · "),
                11f,
            )
        }
        pdf.gap(12f)
        pdf.text("Notruf 112 · Ärztlicher Bereitschaftsdienst 116 117", 11f, bold = true)
        pdf.text("Stand: " + LocalDate.now().format(DATE) + ". Ohne Gewähr, Angaben bitte prüfen.", 9f, gray = true)

        // Karte fuer die Geldboerse (Scheckkartenformat), Vorder- und Rueckseite zum Ausschneiden und Falten
        val cw = 85.6f * MM
        val ch = 54f * MM
        pdf.gap(16f)
        pdf.ensure(ch + 40f)
        pdf.text("Zum Ausschneiden für die Geldbörse (an der Mittellinie falten):", 9.5f, gray = true)
        pdf.gap(6f)
        val top = pdf.y
        val x1 = pdf.left
        val x2 = pdf.left + cw
        pdf.box(RectF(x1, top, x2 + cw, top + ch), stroke = Color.GRAY, dashed = true, width = 0.8f, radius = 8f)
        pdf.canvas.drawLine(x2, top, x2, top + ch, android.graphics.Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f })
        val inner = (cw - 16f).toInt()
        val front = buildString {
            person?.let { p ->
                append(p.name)
                p.birthDate?.let { append(", geb. ").append(LocalDate.ofEpochDay(it).format(DATE)) }
                append('\n')
                if (p.careLevel in 1..5) append("Pflegegrad ").append(p.careLevel).append('\n')
                if (p.insurer.isNotBlank()) append(p.insurer).append('\n')
            }
            if (caregiver.isSet && caregiver.phone.isNotBlank()) append("Im Notfall: ").append(caregiver.name.ifBlank { "Ansprechperson" }).append(", ").append(caregiver.phone).append('\n')
            familyDoctor(doctors)?.takeIf { it.phone.isNotBlank() }?.let { append("Arzt: ").append(it.name).append(", ").append(it.phone) }
        }
        pdf.draw(pdf.layout("NOTFALLKARTE", pdf.paint(10f, bold = true, color = RED), inner), x1 + 8f, top + 7f, advance = false)
        pdf.draw(pdf.layout(front.trim(), pdf.paint(7.6f), inner, maxLines = 8), x1 + 8f, top + 22f, advance = false)
        val back = buildString {
            person?.important?.takeIf { it.isNotBlank() }?.let { append("Wichtig: ").append(it.trim().replace('\n', ' ')).append('\n') }
            if (own.isNotEmpty()) append("Medikamente: ").append(own.joinToString(", ") { it.name }).append('\n')
            append("Stand ").append(LocalDate.now().format(DATE))
        }
        pdf.draw(pdf.layout(back, pdf.paint(7.2f), inner, maxLines = 11), x2 + 8f, top + 8f, advance = false)
        pdf.y = top + ch + 6f
        return pdf.save(file)
    }

    // ------------------------------------------------------------------ Medikamentenplan

    private fun slotIndex(h: Int): Int = when {
        h < 11 -> 0
        h < 15 -> 1
        h < 21 -> 2
        else -> 3
    }

    /**
     * Mengen fuer morgens, mittags, abends, zur Nacht: aus den Mengen je Einnahme (z. B. 8 IE, 5 ml),
     * sonst aus dem Schema der Dosierung, sonst aus den Einnahmezeiten.
     */
    private fun slots(m: Medication): Pair<List<String>, String> {
        val unitNote = if (m.unit != "Stück") listOf(m.dosage, "Mengen in ${m.unit}").filter { it.isNotBlank() }.joinToString(" · ") else m.dosage
        val doses = m.doseMap()
        if (doses.isNotEmpty()) {
            val sums = DoubleArray(4)
            val has = BooleanArray(4)
            m.timeList().forEach { t ->
                val h = runCatching { LocalTime.parse(t).hour }.getOrNull() ?: return@forEach
                val d = de.meinhelfer.tag.summary.Doses.at(doses, t) ?: return@forEach
                val i = slotIndex(h)
                sums[i] += d
                has[i] = true
            }
            if (has.any { it }) return List(4) { if (has[it]) de.meinhelfer.tag.summary.Amounts.fmt(sums[it]) else "0" } to unitNote
        }
        MedPlans.schemaOf(m.dosage)?.let { (amounts, rest) -> return amounts.map { if (it.isBlank()) "0" else it } to rest }
        val result = MutableList(4) { "" }
        m.timeList().forEach { t ->
            val h = runCatching { LocalTime.parse(t).hour }.getOrNull() ?: return@forEach
            result[slotIndex(h)] = if (m.unit == "Stück") "1" else "×"
        }
        return (if (result.all { it.isBlank() }) result else result.map { it.ifBlank { "0" } }) to unitNote
    }

    fun medPlan(
        context: Context,
        person: Person?,
        meds: List<Medication>,
        doctors: List<Doctor>,
        caregiver: CaregiverProfile,
        duties: List<de.meinhelfer.tag.data.Responsibility> = emptyList(),
    ): File {
        val pid = person?.id ?: 1L
        val rx = de.meinhelfer.tag.data.Duties.defaultFor(duties, pid, de.meinhelfer.tag.data.Duties.REZEPT)?.let { id -> doctors.firstOrNull { it.id == id } }
            ?: familyDoctor(doctors)
        val pharmacy = de.meinhelfer.tag.data.Duties.defaultFor(duties, pid, de.meinhelfer.tag.data.Duties.APOTHEKE)?.let { id -> doctors.firstOrNull { it.id == id } }
        val file = File(PdfPages.dir(context, "medikationsplan"), "Medikamentenplan_${safe(person?.name ?: "")}_${LocalDate.now()}.pdf")
        val own = meds.filter { person == null || it.personId == person.id }
            .sortedBy { it.timeList().firstOrNull() ?: "99" }
        // Spalten (Querformat): Medikament, Staerke/Form, Mo, Mi, Ab, zN, Hinweise
        val cols = floatArrayOf(200f, 140f, 34f, 34f, 34f, 34f, 0f)
        fun header(p: PdfPages) {
            val total = p.width.toFloat()
            cols[6] = total - cols.take(6).sum()
            p.text("Medikamentenplan", 18f, bold = true)
            p.gap(2f)
            val who = listOfNotNull(
                person?.name,
                person?.birthDate?.let { "geb. " + LocalDate.ofEpochDay(it).format(DATE) },
                person?.careLevel?.takeIf { it in 1..5 }?.let { "Pflegegrad $it" },
            ).joinToString(" · ")
            if (who.isNotBlank()) p.text("für: $who", 11f)
            val from = listOfNotNull(
                rx?.let { "Arzt: " + it.name + if (it.phone.isNotBlank()) ", ${it.phone}" else "" },
                pharmacy?.let { "Apotheke: " + it.name + if (it.phone.isNotBlank()) ", ${it.phone}" else "" },
                caregiver.takeIf { it.isSet && it.name.isNotBlank() }?.let { "erstellt von: " + it.name + (it.phone.takeIf { ph -> ph.isNotBlank() }?.let { ph -> ", $ph" } ?: "") },
                "Stand: " + LocalDate.now().format(DATE),
            ).joinToString("   ")
            p.text(from, 9.5f, gray = true)
            p.gap(8f)
            // Tabellenkopf
            val heads = listOf("Medikament", "Stärke / Form", "morgens", "mittags", "abends", "zur Nacht", "Hinweise")
            val top = p.y
            p.fill(RectF(p.left, top, p.right, top + 26f), Color.rgb(232, 236, 240), radius = 2f)
            var x = p.left
            heads.forEachIndexed { i, h ->
                val l = p.layout(h, p.paint(if (i in 2..5) 7.5f else 9f, bold = true), (cols[i] - 6).toInt())
                p.draw(l, x + 3f, top + (26f - l.height) / 2, advance = false)
                x += cols[i]
            }
            p.y = top + 26f
        }
        val pdf = PdfPages(842, 595, margin = 34f, footer = "Medikamentenplan · Mein Helfer für den Tag", onNewPage = { header(it) })
        pdf.newPage()

        val grid = android.graphics.Paint().apply { color = Color.rgb(170, 170, 170); strokeWidth = 0.6f }
        if (own.isEmpty()) pdf.text("Keine Medikamente eingetragen.", 11f)
        own.forEach { m ->
            val (amounts, strength) = slots(m)
            val cells = listOf(
                m.name,
                strength,
                amounts[0], amounts[1], amounts[2], amounts[3],
                listOfNotNull(
                    m.notes.trim().takeIf { it.isNotBlank() }?.replace('\n', ' '),
                    m.timeList().takeIf { it.isNotEmpty() && amounts.all { a -> a.isBlank() } }?.let { "Einnahme: ${it.joinToString(", ")} Uhr" },
                ).joinToString(" · ").take(300),
            )
            val layouts = cells.mapIndexed { i, c ->
                pdf.layout(c, pdf.paint(if (i == 0) 10f else if (i in 2..5) 12f else 9.5f, bold = i == 0 || i in 2..5), (cols[i] - 8).toInt())
            }
            val rowH = (layouts.maxOf { it.height } + 10).toFloat()
            pdf.ensure(rowH)
            val top = pdf.y
            var x = pdf.left
            layouts.forEachIndexed { i, l ->
                val dx = if (i in 2..5) (cols[i] - l.getLineWidth(0)) / 2 else 4f
                pdf.draw(l, x + dx, top + 5f, advance = false)
                x += cols[i]
            }
            pdf.y = top + rowH
            pdf.canvas.drawLine(pdf.left, pdf.y, pdf.right, pdf.y, grid)
        }
        pdf.gap(12f)
        pdf.text(
            "Selbst erstellt aus den Angaben in der App. Das ist kein offizieller bundeseinheitlicher Medikationsplan; maßgeblich sind die Angaben von Arztpraxis und Apotheke. " +
                "Bitte nach jeder Änderung neu ausdrucken und den alten Plan vernichten.",
            8.5f,
            gray = true,
        )
        return pdf.save(file)
    }

    // ------------------------------------------------------------------ Pflegetagebuch

    fun careDiary(
        context: Context,
        person: Person?,
        entries: List<de.meinhelfer.tag.data.CareLogEntry>,
        fromDay: Long,
        toDay: Long,
        caregiver: CaregiverProfile,
        /** Messwerte der Person: zeigen, wie oft gemessen wurde (Bereich 5, z. B. Blutzucker messen) */
        vitals: List<de.meinhelfer.tag.data.VitalEntry> = emptyList(),
    ): File {
        val areas = de.meinhelfer.tag.summary.CareAreas
        val file = File(PdfPages.dir(context, "pflegetagebuch"), "Pflegetagebuch_${safe(person?.name ?: "")}_${LocalDate.now()}.pdf")
        val pdf = PdfPages(595, 842, margin = 44f, footer = "Pflegetagebuch · Mein Helfer für den Tag")
        pdf.newPage()
        val days = entries.map { it.day }.distinct().size
        pdf.text("Pflegetagebuch", 20f, bold = true)
        pdf.text(
            listOfNotNull(
                person?.name,
                person?.birthDate?.let { "geb. " + LocalDate.ofEpochDay(it).format(DATE) },
                person?.careLevel?.takeIf { it in 1..5 }?.let { "derzeit Pflegegrad $it" },
            ).joinToString(" · "),
            11f,
        )
        pdf.text("Zeitraum " + LocalDate.ofEpochDay(fromDay).format(DATE) + " bis " + LocalDate.ofEpochDay(toDay).format(DATE) + " · an $days Tag(en) geführt", 10f)
        if (caregiver.isSet && caregiver.name.isNotBlank()) {
            pdf.text("Geführt von: " + listOfNotNull(caregiver.name, caregiver.relation.ifBlank { null }, caregiver.phone.ifBlank { null }).joinToString(", "), 10f, gray = true)
        }
        pdf.gap(10f)
        if (entries.isEmpty()) {
            pdf.text("Keine Einträge im Zeitraum.", 11f)
            return pdf.save(file)
        }

        // Zusammenfassung je Bereich
        pdf.text("Zusammenfassung nach Bereichen", 13f, bold = true)
        pdf.gap(4f)
        val stats = areas.stats(entries)
        val cols = floatArrayOf(0f, 46f, 52f, 52f, 40f, 130f)
        cols[0] = pdf.width - cols.drop(1).sum()
        areas.ALL.forEach { area ->
            val list = stats.filter { it.module == area.module }
            if (list.isEmpty()) return@forEach
            pdf.gap(6f)
            pdf.ensure(60f)
            pdf.text("${area.module}. ${area.title} (Gewichtung ${area.weight})", 11f, bold = true)
            val heads = listOf("Wobei", "Tage", "× pro Tag", "Min./Tag", "nachts", "Art der Hilfe")
            var x = pdf.left
            val top = pdf.y + 2f
            heads.forEachIndexed { i, h ->
                pdf.draw(pdf.layout(h, pdf.paint(8f, bold = true, gray = true), (cols[i] - 4).toInt()), x, top, advance = false)
                x += cols[i]
            }
            pdf.y = top + 12f
            list.forEach { st ->
                val cells = listOf(
                    st.activity + (if (st.notes.isNotEmpty()) "\n" + st.notes.joinToString("; ") else ""),
                    "${st.days} von $days",
                    String.format(Locale.GERMANY, "%.1f", st.timesPerDay),
                    if (st.minutesPerDay > 0) "${st.minutesPerDay}" else "–",
                    if (st.nights > 0) "${st.nights} T." else "–",
                    st.helps.joinToString(", "),
                )
                val ls = cells.mapIndexed { i, c -> pdf.layout(c, pdf.paint(if (i == 0) 9.5f else 9f), (cols[i] - 4).toInt()) }
                val h = (ls.maxOf { it.height } + 5).toFloat()
                pdf.ensure(h)
                val t = pdf.y
                var cx = pdf.left
                ls.forEachIndexed { i, l ->
                    pdf.draw(l, cx, t + 2f, advance = false)
                    cx += cols[i]
                }
                pdf.y = t + h
                pdf.rule(Color.rgb(215, 215, 215))
            }
        }

        // Wie oft gemessen wurde (aus „Trinken und Werte“), als Beleg fuer Bereich 5
        val measured = vitals.filter { it.day in fromDay..toDay && it.kind != VitalKind.Trinken.key }
        if (measured.isNotEmpty()) {
            pdf.gap(10f)
            pdf.ensure(50f)
            pdf.text("Messungen im Zeitraum (aus „Trinken und Werte“, zu Bereich 5)", 11f, bold = true)
            val span = (toDay - fromDay + 1).toInt()
            VitalKind.MEASURES.forEach { k ->
                val list = measured.filter { it.kind == k.key }
                if (list.isEmpty()) return@forEach
                val mDays = list.map { it.day }.distinct().size
                pdf.text(
                    "• ${k.label}: an $mDays von $span Tagen gemessen, im Schnitt " +
                        String.format(Locale.GERMANY, "%.1f", list.size.toDouble() / mDays) + "× an diesen Tagen",
                    9.5f,
                    x = pdf.left + 8f,
                    w = pdf.width - 8,
                )
            }
        }

        // Tag fuer Tag
        pdf.gap(14f)
        pdf.ensure(40f)
        pdf.text("Einträge Tag für Tag", 13f, bold = true)
        entries.groupBy { it.day }.toSortedMap().forEach { (d, list) ->
            pdf.gap(6f)
            pdf.ensure(30f)
            pdf.text(LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY)), 10.5f, bold = true)
            list.sortedWith(compareBy({ it.module }, { it.atMillis })).forEach { e ->
                pdf.text(
                    "• [${e.module}] " + de.meinhelfer.tag.ui.careLine(e) + (if (e.note.isNotBlank()) " – ${e.note}" else ""),
                    9.5f,
                    x = pdf.left + 8f,
                    w = pdf.width - 8,
                )
            }
        }
        pdf.gap(12f)
        pdf.text(
            "Selbst geführtes Pflegetagebuch zur Vorbereitung der Begutachtung. Bewertet wird bei der Begutachtung, wie selbständig die Person ist; Minuten sind nur ein Anhaltspunkt. " +
                "Den Pflegegrad legt die Pflegekasse fest.",
            8.5f,
            gray = true,
        )
        return pdf.save(file)
    }

    // ------------------------------------------------------------------ Uebergabe-Bericht

    /** Was in den Uebergabe-Bericht soll. */
    data class HandoverOptions(
        val fromDay: Long,
        val toDay: Long,
        val substitute: String,
        val notes: List<Pair<String, String>>,
        val meds: Boolean = true,
        val appointments: Boolean = true,
        val contacts: Boolean = true,
        val tasks: Boolean = true,
        val values: Boolean = true,
        val diary: Boolean = true,
        val insuranceNumber: Boolean = false,
    )

    fun handover(
        context: Context,
        person: Person?,
        meds: List<Medication>,
        doctors: List<Doctor>,
        duties: List<de.meinhelfer.tag.data.Responsibility>,
        appointments: List<de.meinhelfer.tag.data.Appointment>,
        todos: List<de.meinhelfer.tag.data.Todo>,
        vitals: List<de.meinhelfer.tag.data.VitalEntry>,
        careLog: List<de.meinhelfer.tag.data.CareLogEntry>,
        caregiver: CaregiverProfile,
        drinkGoal: Int,
        o: HandoverOptions,
    ): File {
        val zone = java.time.ZoneId.systemDefault()
        val file = File(PdfPages.dir(context, "uebergabe"), "Uebergabe_${safe(person?.name ?: "")}_${LocalDate.now()}.pdf")
        val pdf = PdfPages(595, 842, margin = 44f, footer = "Übergabe · Mein Helfer für den Tag")
        val pid = person?.id ?: 1L
        pdf.newPage()
        fun section(title: String) {
            pdf.gap(12f)
            pdf.ensure(50f)
            pdf.text(title, 13f, bold = true)
            pdf.gap(2f)
            pdf.rule()
            pdf.gap(4f)
        }
        pdf.text("Übergabe", 22f, bold = true)
        pdf.text(
            listOfNotNull(
                "für " + (person?.name ?: "–"),
                "vom " + LocalDate.ofEpochDay(o.fromDay).format(DATE) + " bis " + LocalDate.ofEpochDay(o.toDay).format(DATE),
                o.substitute.trim().ifBlank { null }?.let { "Vertretung: $it" },
            ).joinToString(" · "),
            11.5f,
        )
        if (caregiver.isSet && (caregiver.name.isNotBlank() || caregiver.phone.isNotBlank())) {
            pdf.text("Übergeben von: " + listOfNotNull(caregiver.name.ifBlank { null }, caregiver.relation.ifBlank { null }, caregiver.phone.ifBlank { null }?.let { "Tel. $it" }).joinToString(", "), 10.5f)
        }
        pdf.text("Im Notfall: 112 · Ärztlicher Bereitschaftsdienst 116 117", 10.5f, bold = true, color = RED)

        // Person
        person?.let { p ->
            section("Person")
            listOfNotNull(
                p.birthDate?.let { "Geboren am " + LocalDate.ofEpochDay(it).format(DATE) },
                p.careLevel.takeIf { it in 1..5 }?.let { "Pflegegrad $it" },
                address(p).takeIf { it.isNotBlank() }?.let { "Adresse: $it" },
                p.phone.takeIf { it.isNotBlank() }?.let { "Telefon: $it" },
                de.meinhelfer.tag.ui.accessSummary(p.floor, p.elevator, p.access).takeIf { it.isNotBlank() }?.let { "Zugang: $it" },
                p.insurer.takeIf { it.isNotBlank() }?.let { "Krankenkasse: $it" + if (o.insuranceNumber && p.insuranceNumber.isNotBlank()) ", Versichertennummer ${p.insuranceNumber}" else "" },
            ).forEach { pdf.text(it, 10.5f) }
            if (p.important.isNotBlank()) {
                pdf.gap(4f)
                pdf.text("Wichtig zu wissen: " + p.important.trim(), 10.5f, bold = true, color = RED)
            }
        }

        // Eigene Hinweise
        o.notes.filter { it.second.isNotBlank() }.forEach { (title, text) ->
            section(title)
            text.trim().lines().forEach { pdf.text(it, 10.5f) }
        }

        // Medikamente
        if (o.meds) {
            val own = meds.filter { it.personId == pid }.sortedBy { it.timeList().firstOrNull() ?: "99" }
            section("Medikamente")
            if (own.isEmpty()) pdf.text("Keine Medikamente eingetragen.", 10.5f)
            own.forEach { m ->
                val times = m.timeList().joinToString(", ")
                pdf.text(
                    "• " + m.name + (if (m.dosage.isNotBlank()) " – ${m.dosage}" else "") + (if (times.isNotBlank()) " · $times Uhr" else "") +
                        (m.doseSummary()?.let { " · $it" } ?: "") +
                        (if (m.notes.isNotBlank()) " · " + m.notes.trim().replace('\n', ' ') else ""),
                    10.5f,
                )
            }
            val low = own.filter { (it.daysLeft() ?: Int.MAX_VALUE) <= (o.toDay - LocalDate.now().toEpochDay()).toInt() + 3 }
            if (low.isNotEmpty()) {
                pdf.gap(4f)
                pdf.text("Vorrat reicht nicht bis zum Ende der Vertretung: " + low.joinToString(", ") { it.name + " (ca. ${it.daysLeft()} Tage)" } + ". Bitte rechtzeitig nachbestellen.", 10f, bold = true)
            }
        }

        // Kontakte
        if (o.contacts) {
            section("Ärzte und Kontakte")
            val rx = de.meinhelfer.tag.data.Duties.resolve(duties, null, pid, de.meinhelfer.tag.data.Duties.REZEPT, o.fromDay)
            val ph = de.meinhelfer.tag.data.Duties.resolve(duties, null, pid, de.meinhelfer.tag.data.Duties.APOTHEKE, o.fromDay)
            doctors.firstOrNull { it.id == rx.id }?.let { d -> pdf.text("Rezepte und Medikamente: ${d.name}" + (if (d.phone.isNotBlank()) ", ${d.phone}" else "") + " " + de.meinhelfer.tag.data.Duties.note(rx, doctors), 10.5f, bold = true) }
            doctors.firstOrNull { it.id == ph.id }?.let { d -> pdf.text("Apotheke: ${d.name}" + (if (d.phone.isNotBlank()) ", ${d.phone}" else "") + " " + de.meinhelfer.tag.data.Duties.note(ph, doctors), 10.5f, bold = true) }
            // Vertretungen, die in den Zeitraum fallen
            duties.filter { it.role == de.meinhelfer.tag.data.Duties.VERTRETUNG && (it.fromDay ?: Long.MIN_VALUE) <= o.toDay && (it.untilDay ?: Long.MAX_VALUE) >= o.fromDay }.forEach { s ->
                val a = doctors.firstOrNull { it.id == s.replacesId }?.name ?: "–"
                val b = doctors.firstOrNull { it.id == s.contactId }
                pdf.text("Abwesend: $a ${de.meinhelfer.tag.data.Duties.period(s)}, Vertretung: ${b?.name ?: "–"}" + (b?.phone?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""), 10f)
            }
            doctors.filter { it.phone.isNotBlank() || it.address.isNotBlank() }.forEach { d ->
                pdf.text("• " + d.name + (if (d.specialty.isNotBlank()) " (${d.specialty})" else "") + (if (d.phone.isNotBlank()) ": ${d.phone}" else "") + (if (d.address.isNotBlank()) " · ${d.address}" else ""), 10f)
            }
        }

        // Termine im Zeitraum
        if (o.appointments) {
            val from = LocalDate.ofEpochDay(o.fromDay).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = LocalDate.ofEpochDay(o.toDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val list = appointments.filter { it.personId == pid && it.whenMillis in from until to }.sortedBy { it.whenMillis }
            section("Termine im Zeitraum")
            if (list.isEmpty()) pdf.text("Keine Termine eingetragen.", 10.5f)
            val f = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm", Locale.GERMANY)
            list.forEach { a ->
                val d = doctors.firstOrNull { it.id == a.doctorId }
                pdf.text(
                    "• " + java.time.Instant.ofEpochMilli(a.whenMillis).atZone(zone).format(f) + " Uhr – " + a.title +
                        (d?.let { " (${it.name})" } ?: "") + (if (a.place.isNotBlank()) " · ${a.place}" else "") + (if (a.notes.isNotBlank()) " · ${a.notes}" else ""),
                    10.5f,
                )
            }
        }

        // Aufgaben
        if (o.tasks) {
            val open = todos.filter { it.personId == pid && !it.done && !it.archived && (it.dueDay == null || it.dueDay <= o.toDay + 7) }
                .sortedWith(compareBy({ it.dueDay ?: Long.MAX_VALUE }, { -it.priority }))
            if (open.isNotEmpty()) {
                section("Offene Aufgaben")
                open.take(25).forEach { t ->
                    pdf.text("☐ " + t.text + (t.dueDay?.let { " (bis " + LocalDate.ofEpochDay(it).format(DATE) + ")" } ?: "") + if (t.priority == 2) " – dringend" else "", 10.5f)
                }
            }
        }

        // Werte
        if (o.values) {
            val latest = VitalKind.MEASURES.mapNotNull { k -> vitals.filter { it.personId == pid && it.kind == k.key }.maxByOrNull { it.atMillis } }
            val plan = de.meinhelfer.tag.summary.MeasurePlans.parse(de.meinhelfer.tag.update.AppPrefs.measurePlan(context, pid))
            if (drinkGoal > 0 || latest.isNotEmpty() || plan.isNotEmpty()) {
                section("Trinken und Werte")
                if (drinkGoal > 0) pdf.text("Trinkziel: $drinkGoal ml am Tag", 10.5f)
                plan.forEach { (k, times) ->
                    pdf.text("${k.label} messen: täglich um " + de.meinhelfer.tag.summary.MeasurePlans.timesText(times) + " Uhr (in der App eintragen oder auf dem Tagesplan notieren)", 10.5f, bold = true)
                }
                latest.forEach { e ->
                    val k = VitalKind.of(e.kind)
                    val range = AppPrefsBridge.range(context, pid, e.kind)
                    pdf.text(
                        "${k?.label}: zuletzt ${Vitals.valueText(e)} am " + LocalDate.ofEpochDay(e.day).format(DATE) +
                            (range?.let { r -> " · Bereich laut Absprache: " + (r.first?.let { Vitals.number(it, k?.decimals ?: 0) } ?: "…") + " bis " + (r.second?.let { Vitals.number(it, k?.decimals ?: 0) } ?: "…") } ?: ""),
                        10.5f,
                    )
                }
            }
        }

        // Hilfebedarf aus dem Pflegetagebuch
        if (o.diary) {
            val recent = careLog.filter { it.personId == pid && it.day > LocalDate.now().toEpochDay() - 28 }
            if (recent.isNotEmpty()) {
                section("Wobei Hilfe nötig ist (aus dem Pflegetagebuch)")
                val stats = de.meinhelfer.tag.summary.CareAreas.stats(recent)
                de.meinhelfer.tag.summary.CareAreas.ALL.forEach { area ->
                    val list = stats.filter { it.module == area.module }
                    if (list.isEmpty()) return@forEach
                    pdf.text(area.title, 10.5f, bold = true)
                    list.take(8).forEach { st ->
                        pdf.text(
                            "• " + st.activity + ": " + st.helps.joinToString(", ") +
                                (if (st.nights > 0) ", auch nachts" else "") + (if (st.notes.isNotEmpty()) " – " + st.notes.joinToString("; ") else ""),
                            10f,
                            x = pdf.left + 8f,
                            w = pdf.width - 8,
                        )
                    }
                }
            }
        }

        pdf.gap(14f)
        pdf.text("Aus den Angaben in der App erstellt. Bei Unklarheiten bitte anrufen. Maßgeblich für Medikamente sind Arztpraxis und Apotheke.", 8.5f, gray = true)
        return pdf.save(file)
    }

    /** Kleiner Zugriff auf die Einstellungen (eigener Bereich eines Messwerts). */
    private object AppPrefsBridge {
        fun range(context: Context, personId: Long, kind: String) = de.meinhelfer.tag.update.AppPrefs.vitalRange(context, personId, kind)
    }
}
