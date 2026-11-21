package de.meinhelfer.tag.export

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An wen geht die E-Mail? Bestimmt, welche Themen zur Auswahl stehen. */
enum class RecipientKind { Arzt, Pflegekasse, Pflegedienst, Apotheke, Sonstige }

/** Thema einer vorbereiteten E-Mail. */
enum class MailTopic(val label: String, val subject: String, val usesMeds: Boolean = false) {
    TerminAnfragen("Termin anfragen", "Terminanfrage"),
    TerminAbsagen("Termin absagen / verschieben", "Terminabsage bzw. Bitte um neuen Termin"),
    Rezept("Rezept anfordern", "Bitte um Folgerezept", usesMeds = true),
    Ueberweisung("Überweisung", "Bitte um Überweisung"),
    Befund("Befund / Bericht", "Bitte um Befund bzw. Arztbrief"),
    Krankenfahrt("Krankenfahrt (Verordnung)", "Bitte um Verordnung einer Krankenbeförderung"),
    Hausbesuch("Hausbesuch", "Bitte um Hausbesuch"),
    Pflegeberatung("Pflegeberatung", "Bitte um Pflegeberatung"),
    Pflegekurs("Pflegekurs", "Anfrage Pflegekurs"),
    Entlastung("Verhinderungs- und Kurzzeitpflege", "Anfrage Verhinderungspflege / Entlastung"),
    Pflegegrad("Pflegegrad beantragen / erhöhen", "Antrag Pflegegrad"),
    EinsatzAendern("Einsatz ändern", "Änderung des Einsatzes"),
    Auffaelligkeit("Auffälligkeit melden", "Rückmeldung zur Versorgung"),
    Bestellen("Medikamente bestellen", "Bestellung Medikamente", usesMeds = true),
    Frage("Allgemeine Frage", "Anfrage"),
    Rueckruf("Rückruf erbitten", "Bitte um Rückruf"),
}

data class MailContext(
    val person: Person?,
    val caregiver: CaregiverProfile,
    val meds: List<Medication> = emptyList(),
    val appointment: Appointment? = null,
    /** Freitext des Nutzers (Grund, Anliegen). */
    val extra: String = "",
    val includeBirth: Boolean = true,
    val includeInsuranceNumber: Boolean = false,
    val includeCareLevel: Boolean = false,
    val includeAddress: Boolean = false,
    val today: Long = LocalDate.now().toEpochDay(),
)

object MailTemplates {
    const val PLACEHOLDER = "(bitte ergänzen)"
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    /** Art des Empfaengers anhand von Fachrichtung und Name. */
    fun kindOf(d: Doctor): RecipientKind {
        val t = (d.specialty + " " + d.name).lowercase()
        return when {
            listOf("pflegekasse", "krankenkasse", "aok", "barmer", "techniker", "dak", "ikk", "bkk", "knappschaft", "kasse")
                .any { t.contains(it) } -> RecipientKind.Pflegekasse
            listOf("pflegedienst", "sozialstation", "ambulant", "diakonie", "caritas").any { t.contains(it) } -> RecipientKind.Pflegedienst
            t.contains("apotheke") -> RecipientKind.Apotheke
            listOf("sanitäts", "sanitaets", "fahrdienst", "taxi", "behörde", "behoerde", "amt").any { t.contains(it) } -> RecipientKind.Sonstige
            else -> RecipientKind.Arzt
        }
    }

    fun topicsFor(kind: RecipientKind): List<MailTopic> = when (kind) {
        RecipientKind.Arzt -> listOf(
            MailTopic.TerminAnfragen, MailTopic.TerminAbsagen, MailTopic.Rezept, MailTopic.Ueberweisung,
            MailTopic.Befund, MailTopic.Krankenfahrt, MailTopic.Hausbesuch, MailTopic.Rueckruf,
        )
        RecipientKind.Pflegekasse -> listOf(
            MailTopic.Pflegeberatung, MailTopic.Pflegegrad, MailTopic.Entlastung, MailTopic.Pflegekurs,
            MailTopic.Frage, MailTopic.Rueckruf,
        )
        RecipientKind.Pflegedienst -> listOf(
            MailTopic.EinsatzAendern, MailTopic.Auffaelligkeit, MailTopic.Frage, MailTopic.Rueckruf,
        )
        RecipientKind.Apotheke -> listOf(MailTopic.Bestellen, MailTopic.Frage, MailTopic.Rueckruf)
        RecipientKind.Sonstige -> listOf(MailTopic.Frage, MailTopic.Rueckruf)
    }

    /** Sinnvolle Voreinstellung fuer Thema je nach Anlass. */
    fun defaultTopic(kind: RecipientKind, appointment: Appointment?): MailTopic =
        if (appointment != null && kind == RecipientKind.Arzt) MailTopic.TerminAbsagen else topicsFor(kind).first()

    /** Rate das Thema aus einem Aufgabentext. */
    fun guessTopic(kind: RecipientKind, text: String): MailTopic? {
        val t = text.lowercase()
        val topics = topicsFor(kind)
        val guess = when {
            t.contains("rezept") -> MailTopic.Rezept
            t.contains("überweis") || t.contains("ueberweis") -> MailTopic.Ueberweisung
            t.contains("befund") || t.contains("arztbrief") -> MailTopic.Befund
            t.contains("fahrt") || t.contains("transport") || t.contains("taxi") -> MailTopic.Krankenfahrt
            t.contains("hausbesuch") -> MailTopic.Hausbesuch
            t.contains("absag") || t.contains("verschieb") -> MailTopic.TerminAbsagen
            t.contains("termin") -> MailTopic.TerminAnfragen
            t.contains("bestell") -> MailTopic.Bestellen
            t.contains("rückruf") || t.contains("rueckruf") || t.contains("anrufen") -> MailTopic.Rueckruf
            else -> null
        }
        return guess?.takeIf { it in topics }
    }

    /** Welche Haken fuer die Angaben zur Person sind standardmaessig gesetzt? */
    fun defaultIncludes(kind: RecipientKind, topic: MailTopic): Triple<Boolean, Boolean, Boolean> =
        Triple(true, false, kind == RecipientKind.Pflegekasse || kind == RecipientKind.Pflegedienst || topic == MailTopic.Krankenfahrt)

    /** Voreinstellung fuer die Medikamentenauswahl. */
    fun defaultMeds(meds: List<Medication>, recipient: Doctor, kind: RecipientKind, today: Long): Set<Long> {
        val mine = meds.filter { if (kind == RecipientKind.Apotheke) it.pharmacyId == recipient.id else it.doctorId == recipient.id }
        fun low(m: Medication) = (m.daysLeft(today) ?: Int.MAX_VALUE) <= 7
        val pick = when {
            mine.any(::low) -> mine.filter(::low)
            mine.isNotEmpty() -> mine
            else -> meds.filter(::low)
        }
        return pick.map { it.id }.toSet()
    }

    fun subject(topic: MailTopic, ctx: MailContext): String =
        topic.subject + (ctx.person?.name?.takeIf { it.isNotBlank() }?.let { " – $it" } ?: "")

    fun body(topic: MailTopic, recipient: Doctor, ctx: MailContext): String {
        val p = ctx.person
        val who = p?.name?.takeIf { it.isNotBlank() } ?: "die betreute Person"
        val sb = StringBuilder()
        sb.append("Guten Tag,\n\n")
        sb.append(ctx.caregiver.intro(p?.name)).append("\n")
        personLine(ctx)?.let { sb.append("\n").append(it).append("\n") }
        sb.append("\n")
        sb.append(topicText(topic, recipient, ctx, who))
        sb.append("\n\n")
        sb.append("Bei Rückfragen erreichen Sie mich unter der unten genannten Nummer bzw. per E-Mail. Vielen Dank!\n\n")
        sb.append(ctx.caregiver.signature())
        return sb.toString()
    }

    private fun personLine(ctx: MailContext): String? {
        val p = ctx.person ?: return null
        val parts = mutableListOf<String>()
        if (p.name.isNotBlank()) parts += p.name
        if (ctx.includeBirth) p.birthDate?.let { parts += "geboren am " + LocalDate.ofEpochDay(it).format(DATE) }
        if (ctx.includeCareLevel && p.careLevel in 1..5) parts += "Pflegegrad ${p.careLevel}"
        if (ctx.includeInsuranceNumber && p.insuranceNumber.isNotBlank()) {
            parts += "Versichertennummer ${p.insuranceNumber}"
        }
        if (ctx.includeAddress) {
            val addr = listOf(p.street, listOf(p.zip, p.city).filter { it.isNotBlank() }.joinToString(" "))
                .filter { it.isNotBlank() }.joinToString(", ")
            if (addr.isNotBlank()) parts += addr
        }
        if (parts.isEmpty()) return null
        return "Betrifft: " + parts.joinToString(", ")
    }

    private fun reason(ctx: MailContext, label: String): String =
        "$label: " + ctx.extra.trim().ifBlank { PLACEHOLDER }

    private fun medLines(ctx: MailContext): String =
        if (ctx.meds.isEmpty()) "- $PLACEHOLDER" else ctx.meds.joinToString("\n") { m ->
            val days = m.daysLeft(ctx.today)
            "- ${m.name}" + (if (m.dosage.isNotBlank()) " (${m.dosage})" else "") +
                (if (m.pzn.isNotBlank()) ", PZN ${m.pzn}" else "") +
                (days?.let { " – Vorrat reicht noch ca. $it Tage" } ?: "")
        }

    private fun apptText(a: Appointment?): String {
        if (a == null) return PLACEHOLDER
        val dt = Instant.ofEpochMilli(a.whenMillis).atZone(ZoneId.systemDefault())
        return "am ${dt.format(DATE)} um ${dt.format(TIME)} Uhr" + (a.title.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }

    private fun note(ctx: MailContext): String =
        ctx.extra.trim().takeIf { it.isNotBlank() }?.let { "\n\nAnmerkung: $it" }.orEmpty()

    private fun topicText(topic: MailTopic, recipient: Doctor, ctx: MailContext, who: String): String = when (topic) {
        MailTopic.TerminAnfragen ->
            "ich bitte um einen Termin für $who.\n${reason(ctx, "Grund")}\n\nBitte nennen Sie mir passende Termine. Ich bin zeitlich flexibel und freue mich über eine Rückmeldung per E-Mail oder Telefon."
        MailTopic.TerminAbsagen ->
            "den Termin ${apptText(ctx.appointment)} kann $who leider nicht wahrnehmen.\n${reason(ctx, "Grund")}\n\nBitte bieten Sie mir einen Ersatztermin an und teilen Sie mir diesen mit."
        MailTopic.Rezept ->
            "ich bitte um Folgerezepte für $who für folgende Medikamente:\n\n${medLines(ctx)}\n\nBitte geben Sie mir Bescheid, wann die Rezepte abgeholt werden können oder ob sie direkt an die Apotheke gehen.${note(ctx)}"
        MailTopic.Ueberweisung ->
            "ich bitte um eine Überweisung für $who.\n${reason(ctx, "Zu welchem Fachbereich bzw. weshalb")}\n\nBitte teilen Sie mir mit, wann ich sie abholen kann oder ob Sie sie zusenden."
        MailTopic.Befund ->
            "ich bitte um die Übermittlung des Befunds bzw. Arztbriefs für $who.\n${reason(ctx, "Um welche Untersuchung geht es")}\n\nSofern erforderlich, reiche ich eine Vollmacht bzw. Einverständniserklärung nach."
        MailTopic.Krankenfahrt ->
            "ich bitte um die Verordnung einer Krankenbeförderung (Muster 4) für $who.\nFahrt zu: ${ctx.appointment?.let { apptText(it) + (it.place.takeIf { pl -> pl.isNotBlank() }?.let { pl -> ", $pl" } ?: "") } ?: PLACEHOLDER}\n${reason(ctx, "Hinweis")}\n\nBitte prüfen Sie, ob die medizinische Notwendigkeit bescheinigt werden kann. Die Krankenkasse benötigt ggf. vorab eine Genehmigung."
        MailTopic.Hausbesuch ->
            "ich bitte um einen Hausbesuch bei $who.\n${reason(ctx, "Grund")}\n\nBitte melden Sie sich bei mir, wann das möglich ist."
        MailTopic.Pflegeberatung ->
            "ich bitte um einen Termin für eine Pflegeberatung (§ 7a SGB XI) für $who, gern auch als Hausbesuch.\n${reason(ctx, "Thema")}"
        MailTopic.Pflegekurs ->
            "ich interessiere mich für einen Pflegekurs nach § 45 SGB XI. Bitte senden Sie mir Informationen zu kostenlosen Kursen in der Nähe bzw. online.${note(ctx)}"
        MailTopic.Entlastung ->
            "ich bitte um Informationen und die nötigen Formulare zur Verhinderungspflege bzw. Kurzzeitpflege und zum Entlastungsbetrag für $who.\n${reason(ctx, "Zeitraum bzw. Anlass")}\n\nBitte nennen Sie mir auch, welcher Betrag noch zur Verfügung steht."
        MailTopic.Pflegegrad -> {
            val level = ctx.person?.careLevel?.takeIf { it in 1..5 }
            val cur = if (level != null) "Aktuell besteht Pflegegrad $level. " else ""
            "ich beantrage für $who " + (if (level != null) "die Höherstufung des Pflegegrads" else "die Feststellung eines Pflegegrads") +
                ". ${cur}Bitte senden Sie mir die Antragsunterlagen und teilen Sie mir mit, wie die Begutachtung abläuft.${note(ctx)}"
        }
        MailTopic.EinsatzAendern ->
            "ich bitte um eine Änderung des Einsatzes bei $who.\n${reason(ctx, "Gewünschte Änderung (Tag, Uhrzeit, Leistung)")}\n\nBitte bestätigen Sie mir die Änderung."
        MailTopic.Auffaelligkeit ->
            "mir ist bei $who Folgendes aufgefallen:\n${reason(ctx, "Beobachtung")}\n\nBitte geben Sie mir eine Rückmeldung, wie wir weiter vorgehen, und informieren Sie bei Bedarf die behandelnde Ärztin bzw. den behandelnden Arzt."
        MailTopic.Bestellen ->
            "ich bitte um die Bereitstellung bzw. Lieferung folgender Medikamente für $who:\n\n${medLines(ctx)}\n\nBitte teilen Sie mir mit, wann sie abgeholt werden können oder ob eine Lieferung möglich ist.${note(ctx)}"
        MailTopic.Frage ->
            "ich habe eine Frage zu $who.\n${reason(ctx, "Frage")}"
        MailTopic.Rueckruf ->
            "ich bitte um einen Rückruf zu $who.\n${reason(ctx, "Worum geht es")}"
    }
}
