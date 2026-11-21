package de.meinhelfer.tag.letters

import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.export.MailTemplates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Eingabefeld einer Vorlage (z. B. Datum des Bescheids). */
class LetterField(
    val key: String,
    val label: String,
    val hint: String = "",
    val multiline: Boolean = false,
    val default: String = "",
)

/** Alles, was eine Vorlage zum Schreiben des Textes braucht. */
class LetterInput(
    val person: Person?,
    val caregiver: CaregiverProfile,
    /** true: schreibt eine bevollmaechtigte Person im Namen der betreuten Person. */
    val proxy: Boolean,
    val withInsuranceNumber: Boolean,
    val values: Map<String, String>,
    val meds: List<Medication>,
)

/** Fertiger Text eines Schreibens. [form] = Formular ohne Anschrift (z. B. Vollmacht) mit Titel. */
class LetterBody(
    val subject: String,
    val reference: String,
    val body: String,
    val signatures: List<String>,
    val enclosures: List<String>,
    val form: Boolean = false,
)

/** Wohin das Schreiben typischerweise geht: bestimmt, welche Kontakte zur Auswahl stehen. */
enum class LetterTo { Kasse, Arzt, Frei, Keiner }

class LetterTemplate(
    val id: String,
    val title: String,
    val description: String,
    val group: String,
    val docCategory: String,
    val to: LetterTo,
    val fields: List<LetterField>,
    val notice: String?,
    val build: (LetterInput) -> LetterBody,
)

/**
 * Vorlagen fuer Schreiben auf dem Postweg. Alles sind Entwuerfe mit Platzhaltern "(bitte ergaenzen)",
 * die man vor dem Ausdrucken prueft und aendert. Sie ersetzen keine Rechtsberatung.
 */
object LetterTemplates {
    private val P = MailTemplates.PLACEHOLDER
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

    fun longDate(day: LocalDate = LocalDate.now()): String =
        day.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY))

    private fun v(i: LetterInput, key: String): String = i.values[key]?.trim().orEmpty()

    private fun name(i: LetterInput): String = i.person?.name?.takeIf { it.isNotBlank() } ?: P

    private fun born(i: LetterInput): String? = i.person?.birthDate?.let { LocalDate.ofEpochDay(it).format(DATE) }

    fun address(p: Person?): String {
        if (p == null) return P
        val line = listOf(p.street, listOf(p.zip, p.city).filter { it.isNotBlank() }.joinToString(" "))
            .filter { it.isNotBlank() }.joinToString(", ")
        return line.ifBlank { P }
    }

    /** "Anita Kunig, geboren am 01.01.1940, Versichertennummer X" */
    private fun idLine(i: LetterInput, withNumber: Boolean = true): String = listOfNotNull(
        name(i),
        born(i)?.let { "geboren am $it" },
        if (withNumber && i.withInsuranceNumber) i.person?.insuranceNumber?.takeIf { it.isNotBlank() }?.let { "Versichertennummer $it" } else null,
    ).joinToString(", ")

    private fun reference(i: LetterInput): String = "Versicherte Person: " + idLine(i)

    /** "hiermit beantrage ich" bzw. "hiermit beantrage ich im Namen und mit Vollmacht von X" */
    private fun hiermit(i: LetterInput, verb: String): String =
        if (i.proxy) "hiermit $verb ich im Namen und mit Vollmacht von ${idLine(i, withNumber = false)}"
        else "hiermit $verb ich"

    private fun contact(i: LetterInput): String {
        val phone = i.caregiver.phone.takeIf { it.isNotBlank() } ?: P
        return "Für Rückfragen erreichen Sie mich unter $phone."
    }

    private fun confirmReceipt(): String =
        "Bitte bestätigen Sie mir den Eingang dieses Schreibens schriftlich mit Datum."

    private fun signatures(i: LetterInput): List<String> =
        if (i.proxy) {
            listOf(
                listOf(i.caregiver.name.ifBlank { P }, i.caregiver.relation.takeIf { it.isNotBlank() })
                    .filterNotNull().joinToString(", ") + " (bevollmächtigt)",
            )
        } else {
            listOf(name(i))
        }

    private fun enclosures(i: LetterInput, vararg extra: String): List<String> =
        (if (i.proxy) listOf("Vollmacht") else emptyList()) + extra.toList()

    private fun medList(i: LetterInput): String =
        if (i.meds.isEmpty()) "- $P" else i.meds.joinToString("\n") { m ->
            "- " + m.name + (m.dosage.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
        }

    private fun field(key: String, label: String, hint: String = "", multiline: Boolean = false, default: String = "") =
        LetterField(key, label, hint, multiline, default)

    val ALL: List<LetterTemplate> = listOf(
        // ---------------------------------------------------------- Vollmachten
        LetterTemplate(
            id = "vollmacht",
            title = "Vollmacht für Anträge und Auskünfte",
            description = "Damit du für die Person bei Pflegekasse, Krankenkasse, Ämtern und Pflegedienst Anträge stellen und Auskünfte einholen darfst.",
            group = "Vollmachten",
            docCategory = "Vollmacht / Verfügung",
            to = LetterTo.Keiner,
            fields = listOf(
                field("vertreter", "Name der bevollmächtigten Person", "Wer soll vertreten dürfen?"),
                field("vertreterAdresse", "Anschrift der bevollmächtigten Person", "Straße, PLZ Ort"),
            ),
            notice = "Unterschreiben kann nur, wer die Bedeutung der Vollmacht versteht. Ist das nicht mehr sicher der Fall, hilft eine rechtliche Betreuung (Amtsgericht, Betreuungsbehörde). Das hier ist keine Vorsorgevollmacht.",
            build = { i ->
                val vertreter = v(i, "vertreter").ifBlank { i.caregiver.name.ifBlank { P } }
                val vAdr = v(i, "vertreterAdresse").ifBlank { P }
                val rel = i.caregiver.relation.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                LetterBody(
                    subject = "Vollmacht",
                    reference = "",
                    body = "Ich, ${idLine(i, withNumber = false)},\nwohnhaft in ${address(i.person)},\n\n" +
                        "bevollmächtige hiermit\n\n$vertreter$rel,\nwohnhaft in $vAdr,\n\n" +
                        "mich in den folgenden Angelegenheiten gegenüber Pflegekassen, Krankenkassen, Sozialämtern, Behörden, Versicherungen und Pflegediensten zu vertreten (Nicht Gewünschtes bitte streichen):\n\n" +
                        "[  ] Anträge zu stellen, zu ändern und zurückzunehmen\n" +
                        "[  ] Auskünfte einzuholen und Einsicht in meine Unterlagen zu nehmen\n" +
                        "[  ] Bescheide und Post dieser Stellen entgegenzunehmen\n" +
                        "[  ] Widerspruch einzulegen\n" +
                        "[  ] Gutachten und Pflegeunterlagen anzufordern\n\n" +
                        "Die Vollmacht gilt ab dem Tag der Unterschrift bis auf Widerruf. Ich kann sie jederzeit schriftlich widerrufen. " +
                        "Sie umfasst keine Entscheidungen über Vermögen oder ärztliche Behandlungen.",
                    signatures = listOf(
                        "Ort, Datum, Unterschrift der Vollmachtgeberin / des Vollmachtgebers",
                        "Unterschrift der bevollmächtigten Person (Kenntnisnahme)",
                    ),
                    enclosures = emptyList(),
                    form = true,
                )
            },
        ),
        LetterTemplate(
            id = "schweigepflicht",
            title = "Entbindung von der Schweigepflicht (Ärzte)",
            description = "Damit Ärzte und Kliniken dir Auskunft über Befunde, Medikamente und Behandlung geben dürfen.",
            group = "Vollmachten",
            docCategory = "Vollmacht / Verfügung",
            to = LetterTo.Keiner,
            fields = listOf(
                field("vertreter", "Name der Person, die Auskunft erhalten darf"),
                field("vertreterAdresse", "Anschrift dieser Person", "Straße, PLZ Ort"),
                field("aerzte", "Nur für bestimmte Ärzte / Einrichtungen (optional)", "leer lassen = alle behandelnden Ärzte", multiline = true),
            ),
            notice = "Für eine einzelne Praxis fragt diese oft zusätzlich ein eigenes Formular an. Unterschreiben kann nur, wer die Bedeutung versteht.",
            build = { i ->
                val vertreter = v(i, "vertreter").ifBlank { i.caregiver.name.ifBlank { P } }
                val vAdr = v(i, "vertreterAdresse").ifBlank { P }
                val rel = i.caregiver.relation.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                val who = v(i, "aerzte").takeIf { it.isNotBlank() }?.let { "insbesondere:\n$it" } ?: "alle mich behandelnden Ärztinnen und Ärzte und medizinischen Einrichtungen"
                LetterBody(
                    subject = "Entbindung von der ärztlichen Schweigepflicht",
                    reference = "",
                    body = "Ich, ${idLine(i, withNumber = false)},\nwohnhaft in ${address(i.person)},\n\n" +
                        "entbinde hiermit $who von der Schweigepflicht gegenüber\n\n$vertreter$rel,\nwohnhaft in $vAdr.\n\n" +
                        "Diese Person darf in folgendem Umfang Auskunft erhalten (Nicht Gewünschtes bitte streichen):\n\n" +
                        "[  ] Diagnosen, Befunde und Behandlungsverlauf\n" +
                        "[  ] Medikation und geplante Behandlungen\n" +
                        "[  ] Einsicht in die Behandlungsunterlagen und Kopien daraus\n" +
                        "[  ] Absprachen zu Terminen und zur Organisation der Behandlung\n\n" +
                        "Diese Einwilligung gilt bis auf Widerruf. Ich kann sie jederzeit mit Wirkung für die Zukunft widerrufen.",
                    signatures = listOf("Ort, Datum, Unterschrift der Patientin / des Patienten"),
                    enclosures = emptyList(),
                    form = true,
                )
            },
        ),
        // ---------------------------------------------------------- Pflegekasse
        LetterTemplate(
            id = "pflegegrad",
            title = "Antrag auf Pflegegrad",
            description = "Formloser Erstantrag bei der Pflegekasse. Das Datum des Antrags ist für den Beginn der Leistungen wichtig.",
            group = "Pflegekasse",
            docCategory = "Antrag / Widerspruch",
            to = LetterTo.Kasse,
            fields = listOf(
                field("grund", "Warum wird Hilfe gebraucht?", "z. B. Mobilität, Körperpflege, Gedächtnis, Medikamente", multiline = true),
            ),
            notice = "Die Leistungen gelten in der Regel ab dem Tag der Antragstellung. Der Antrag kann auch formlos oder telefonisch gestellt werden. Die Pflegekasse schickt dann die Formulare und lässt begutachten. Beratung gibt es kostenlos beim Pflegestützpunkt.",
            build = { i ->
                val grund = v(i, "grund").ifBlank { P }
                LetterBody(
                    subject = "Antrag auf Leistungen der Pflegeversicherung (Feststellung eines Pflegegrades)",
                    reference = reference(i),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "beantrage")} Leistungen der Pflegeversicherung und die Feststellung eines Pflegegrades.\n\n" +
                        "Hilfe im Alltag wird benötigt wegen: $grund\n\n" +
                        "Bitte senden Sie mir die erforderlichen Antragsformulare zu und veranlassen Sie die Begutachtung. ${confirmReceipt()} " +
                        "Das Datum dieses Schreibens gilt als Datum der Antragstellung.\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i),
                )
            },
        ),
        LetterTemplate(
            id = "hoeherstufung",
            title = "Antrag auf Höherstufung (neue Begutachtung)",
            description = "Wenn sich der Hilfebedarf erhöht hat und ein höherer Pflegegrad geprüft werden soll.",
            group = "Pflegekasse",
            docCategory = "Antrag / Widerspruch",
            to = LetterTo.Kasse,
            fields = listOf(
                field("pg", "Aktueller Pflegegrad", "1 bis 5"),
                field("aenderung", "Was hat sich verändert?", "z. B. Sturz, Krankenhaus, mehr Hilfe bei Körperpflege nötig", multiline = true),
            ),
            notice = "Eine Höherstufung wird durch eine neue Begutachtung geprüft. Ein Pflegetagebuch über 1 bis 2 Wochen hilft bei der Begutachtung.",
            build = { i ->
                val pg = v(i, "pg").ifBlank { i.person?.careLevel?.takeIf { it in 1..5 }?.toString() ?: P }
                val change = v(i, "aenderung").ifBlank { P }
                LetterBody(
                    subject = "Antrag auf erneute Begutachtung (Höherstufung des Pflegegrades)",
                    reference = reference(i),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "beantrage")} eine erneute Begutachtung mit dem Ziel eines höheren Pflegegrades.\n\n" +
                        "Aktueller Pflegegrad: $pg\n\nSeit der letzten Begutachtung hat sich der Hilfebedarf erhöht: $change\n\n" +
                        "Bitte veranlassen Sie die Begutachtung. ${confirmReceipt()}\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i),
                )
            },
        ),
        LetterTemplate(
            id = "widerspruch",
            title = "Widerspruch gegen einen Bescheid",
            description = "Wenn der Pflegegrad oder eine Leistung abgelehnt oder zu niedrig festgelegt wurde.",
            group = "Pflegekasse",
            docCategory = "Antrag / Widerspruch",
            to = LetterTo.Kasse,
            fields = listOf(
                field("datum", "Datum des Bescheids", "TT.MM.JJJJ"),
                field("az", "Aktenzeichen (falls im Bescheid)"),
                field("grund", "Begründung (kurz)", "z. B. Hilfebedarf wurde zu niedrig eingeschätzt; sonst leer lassen und später nachreichen", multiline = true),
            ),
            notice = "Wichtig: Die Frist für den Widerspruch beträgt in der Regel einen Monat nach Erhalt des Bescheids (fehlt die Rechtsbehelfsbelehrung, ein Jahr). Am besten per Einschreiben senden oder persönlich abgeben und den Eingang bestätigen lassen. Beratung gibt es z. B. beim Pflegestützpunkt, VdK oder SoVD. Ohne Gewähr.",
            build = { i ->
                val datum = v(i, "datum").ifBlank { P }
                val az = v(i, "az").takeIf { it.isNotBlank() }?.let { ", Aktenzeichen $it" }.orEmpty()
                val grund = v(i, "grund")
                LetterBody(
                    subject = "Widerspruch gegen Ihren Bescheid vom $datum$az",
                    reference = reference(i),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "lege")} Widerspruch gegen Ihren Bescheid vom $datum$az ein. Mit der Entscheidung bin ich nicht einverstanden.\n\n" +
                        (if (grund.isNotBlank()) "Begründung: $grund\n\n" else "Eine ausführliche Begründung reiche ich nach. Bitte setzen Sie mir dafür eine angemessene Frist.\n\n") +
                        "Bitte übersenden Sie mir das vollständige Gutachten des Medizinischen Dienstes bzw. von Medicproof und die Unterlagen, auf die Sie Ihre Entscheidung stützen.\n\n" +
                        "${confirmReceipt()}\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i, "Kopie des Bescheids"),
                )
            },
        ),
        LetterTemplate(
            id = "verhinderungspflege",
            title = "Antrag auf Verhinderungspflege",
            description = "Wenn die Pflegeperson verhindert ist (Urlaub, Krankheit) und eine Ersatzpflege bezahlt werden soll.",
            group = "Pflegekasse",
            docCategory = "Antrag / Widerspruch",
            to = LetterTo.Kasse,
            fields = listOf(
                field("zeitraum", "Zeitraum", "z. B. 10.08. bis 24.08.2026"),
                field("grund", "Warum ist die Pflegeperson verhindert?", "z. B. Urlaub, Krankheit"),
                field("ersatz", "Wer übernimmt die Pflege?", "Name, Beziehung oder Pflegedienst"),
            ),
            notice = "Bezeichnung und Beträge dieser Leistungen können sich durch die Pflegereform ändern. Bitte bei der Pflegekasse oder im Pflegestützpunkt bestätigen lassen, wie der Antrag derzeit gestellt wird.",
            build = { i ->
                LetterBody(
                    subject = "Antrag auf Verhinderungspflege (§ 39 SGB XI)",
                    reference = reference(i),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "beantrage")} Leistungen der Verhinderungspflege nach § 39 SGB XI bzw. des entsprechenden Entlastungsbudgets.\n\n" +
                        "Zeitraum: ${v(i, "zeitraum").ifBlank { P }}\nGrund der Verhinderung: ${v(i, "grund").ifBlank { P }}\n" +
                        "Ersatzpflege durch: ${v(i, "ersatz").ifBlank { P }}\n\n" +
                        "Bitte teilen Sie mir mit, welche Nachweise Sie benötigen und wie die Kosten abgerechnet werden. ${confirmReceipt()}\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i),
                )
            },
        ),
        LetterTemplate(
            id = "pflegehilfsmittel",
            title = "Antrag auf Pflegehilfsmittel zum Verbrauch",
            description = "Z. B. Einmalhandschuhe, Desinfektionsmittel, Bettschutzeinlagen für die Pflege zu Hause.",
            group = "Pflegekasse",
            docCategory = "Antrag / Widerspruch",
            to = LetterTo.Kasse,
            fields = listOf(field("welche", "Welche Hilfsmittel?", "z. B. Handschuhe, Desinfektionsmittel, Bettschutzeinlagen", multiline = true)),
            notice = "Voraussetzung ist ein anerkannter Pflegegrad und Pflege zu Hause. Die Kasse nennt Lieferanten, über die abgerechnet wird.",
            build = { i ->
                LetterBody(
                    subject = "Antrag auf zum Verbrauch bestimmte Pflegehilfsmittel (§ 40 Abs. 2 SGB XI)",
                    reference = reference(i),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "beantrage")} die Kostenübernahme für zum Verbrauch bestimmte Pflegehilfsmittel. Die Pflege findet zu Hause statt.\n\n" +
                        "Benötigt werden: ${v(i, "welche").ifBlank { P }}\n\n" +
                        "Bitte teilen Sie mir mit, über welche Lieferanten die Abrechnung erfolgt. ${confirmReceipt()}\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i),
                )
            },
        ),
        // ---------------------------------------------------------- Arzt und Klinik
        LetterTemplate(
            id = "unterlagen",
            title = "Kopie der Behandlungsunterlagen anfordern",
            description = "Befunde, Arztbriefe und Akte bei Praxis oder Klinik anfordern.",
            group = "Arzt und Klinik",
            docCategory = "Antrag / Widerspruch",
            to = LetterTo.Arzt,
            fields = listOf(field("was", "Welche Unterlagen?", "z. B. alle Befunde der letzten 2 Jahre; leer = vollständige Akte", multiline = true)),
            notice = "Nach der Datenschutz-Grundverordnung (Art. 15) und dem Patientenrechtegesetz (§ 630g BGB) gibt es ein Recht auf Einsicht und eine Kopie. Ist es nicht die Person selbst, die anfragt, legt man Vollmacht bzw. Schweigepflichtentbindung bei.",
            build = { i ->
                val was = v(i, "was").ifBlank { "die vollständigen Behandlungsunterlagen" }
                LetterBody(
                    subject = "Bitte um Kopie der Behandlungsunterlagen",
                    reference = "Patient/in: " + idLine(i, withNumber = false),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "bitte")} um eine Kopie folgender Unterlagen: $was\n\n" +
                        "Rechtsgrundlage ist das Auskunftsrecht nach Art. 15 DSGVO sowie § 630g BGB. Eine Kopie nach Art. 15 DSGVO ist nach meinem Verständnis zunächst kostenfrei. " +
                        "Bitte senden Sie mir die Unterlagen per Post oder nennen Sie mir einen Abholtermin. ${confirmReceipt()}\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i, *(if (i.proxy) arrayOf("Entbindung von der Schweigepflicht") else emptyArray())),
                )
            },
        ),
        LetterTemplate(
            id = "rezept",
            title = "Folgerezept per Post anfordern",
            description = "Bitte an die Praxis um ein Rezept, mit der Liste der Medikamente.",
            group = "Arzt und Klinik",
            docCategory = "Rezept / Verordnung",
            to = LetterTo.Arzt,
            fields = listOf(field("rueck", "Wie soll das Rezept zurückkommen?", "z. B. per Post an obige Adresse, frankierter Umschlag liegt bei")),
            notice = "Die Medikamentenliste stammt aus der App. Bitte vor dem Ausdruck Name, Dosierung und Packungsgröße prüfen.",
            build = { i ->
                LetterBody(
                    subject = "Bitte um Folgerezept",
                    reference = "Patient/in: " + idLine(i, withNumber = false),
                    body = "Sehr geehrte Damen und Herren,\n\n" +
                        "${hiermit(i, "bitte")} um ein Folgerezept für diese Medikamente:\n\n${medList(i)}\n\n" +
                        "Rückgabe des Rezepts: ${v(i, "rueck").ifBlank { "per Post an die oben genannte Anschrift" }}\n\n" +
                        "Falls vorher ein Termin nötig ist, melden Sie sich bitte. ${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i),
                )
            },
        ),
        // ---------------------------------------------------------- Frei
        LetterTemplate(
            id = "frei",
            title = "Freies Schreiben",
            description = "Ein eigener Brief mit Anschrift, Betreff und Text, hübsch als PDF zum Ausdrucken.",
            group = "Sonstiges",
            docCategory = "Sonstiges",
            to = LetterTo.Frei,
            fields = listOf(
                field("betreff", "Betreff"),
                field("text", "Text", "Dein Anliegen in eigenen Worten", multiline = true),
            ),
            notice = null,
            build = { i ->
                LetterBody(
                    subject = v(i, "betreff").ifBlank { P },
                    reference = "",
                    body = "Sehr geehrte Damen und Herren,\n\n${v(i, "text").ifBlank { P }}\n\n${contact(i)}",
                    signatures = signatures(i),
                    enclosures = enclosures(i),
                )
            },
        ),
    )
}
