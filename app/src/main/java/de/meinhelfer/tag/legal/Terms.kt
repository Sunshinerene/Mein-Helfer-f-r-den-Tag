package de.meinhelfer.tag.legal

import java.security.MessageDigest

/**
 * Nutzungsbedingungen und wichtige Hinweise. Bei inhaltlichen Aenderungen [VERSION] erhoehen:
 * dann muss beim naechsten Start erneut zugestimmt werden.
 */
object Terms {
    const val VERSION = 1
    const val STAND = "21.11.2026"

    val SECTIONS: List<Pair<String, String>> = listOf(
        "1. Kein Ersatz für Fachleute" to
            "„Mein Helfer für den Tag“ ist ein Hilfsmittel zur Organisation im Pflegealltag. Die App ersetzt keine Ärztin und keinen Arzt, " +
            "keine Apotheke, keine Pflegefachkraft, kein Krankenhaus und keinen Notruf. Sie stellt keine Diagnosen und gibt keine Therapie- " +
            "oder Dosierungsanweisungen. Im Notfall immer 112 wählen. Für Pflegedienste: Die App ersetzt nicht die vorgeschriebene Pflegedokumentation.",
        "2. Einschätzungen und Empfehlungen sind nur Anhaltspunkte" to
            "Auswertungen, Hinweise, Vorschläge, Zusammenfassungen und Einschätzungen der App, der KI-Dienste und Informationen aus dem Internet " +
            "(z. B. Wikipedia, Meldungen, Umkreissuche) können unvollständig, veraltet oder falsch sein. Prüfe sie immer selbst und besprich " +
            "gesundheitliche Fragen mit Arzt oder Apotheke. Medikamente nie eigenmächtig absetzen, ändern oder neu einnehmen.",
        "3. Erinnerungen und Technik" to
            "Erinnerungen und Benachrichtigungen können ausbleiben, z. B. durch Energiesparfunktionen, ein ausgeschaltetes Handy oder Fehler. " +
            "Verlass dich bei wichtigen Einnahmen und Terminen nicht allein auf die App. Die App ist eine Testversion.",
        "4. Eigene Verantwortung" to
            "Für die Richtigkeit deiner Eingaben und für alle Entscheidungen bist du selbst verantwortlich. E-Mails, Schreiben, Anträge und " +
            "Nachrichten prüfst und versendest du selbst. Die App sendet nichts von allein.",
        "5. Haftung" to
            "Die App wird unentgeltlich bereitgestellt. Die Haftung des App-Erstellers (Sunshine@R) ist ausgeschlossen, soweit das gesetzlich " +
            "zulässig ist. Unberührt bleibt die Haftung für Vorsatz und grobe Fahrlässigkeit, für Schäden aus der Verletzung des Lebens, " +
            "des Körpers oder der Gesundheit sowie nach dem Produkthaftungsgesetz.",
        "6. Daten und Nachweis deiner Zustimmung" to
            "Deine Angaben bleiben verschlüsselt auf diesem Handy. Deine Zustimmung wird als Nachweis verschlüsselt in der App gespeichert: " +
            "Name, Rolle, Datum und Uhrzeit, App-Version, Gerät, Fassung und Prüfsumme dieses Textes. Der Nachweis wird nicht übertragen. " +
            "Du kannst ihn in den Einstellungen ansehen und teilen, er ist auch in deiner Sicherung enthalten. Er bleibt gespeichert, " +
            "solange du die App nutzt. Nach einem Widerruf oder nach Zustimmung zu einer neuen Fassung wird der alte Nachweis bis zum Ende " +
            "des dritten Kalenderjahres aufbewahrt (regelmäßige Verjährungsfrist) und dann automatisch gelöscht. Beim Deinstallieren " +
            "der App löscht Android alle Daten der App.",
        "7. Widerruf" to
            "Du kannst deine Zustimmung jederzeit in den Einstellungen widerrufen. Ohne Zustimmung lässt sich die App nicht nutzen. " +
            "Deine Daten bleiben dabei erhalten.",
    )

    /** Was einzeln bestaetigt werden muss. */
    val CONFIRMATIONS: List<String> = listOf(
        "Ich habe verstanden, dass die App keinen Arzt, keine Apotheke, keine Pflegefachkraft und kein Krankenhaus ersetzt und dass ich im Notfall die 112 wähle.",
        "Ich habe verstanden, dass Einschätzungen und Empfehlungen der App und der KI nur Anhaltspunkte sind, die ich selbst prüfe bzw. prüfen lasse.",
        "Ich akzeptiere die Nutzungsbedingungen einschließlich der Regelung zur Haftung.",
    )

    /** Der vollstaendige Text, dem zugestimmt wird (fuer Pruefsumme und Nachweis). */
    fun fullText(): String = buildString {
        appendLine("Nutzungsbedingungen und wichtige Hinweise – Mein Helfer für den Tag")
        appendLine("Fassung $VERSION, Stand $STAND")
        SECTIONS.forEach { (head, body) ->
            appendLine()
            appendLine(head)
            appendLine(body)
        }
        appendLine()
        appendLine("Bestätigt:")
        CONFIRMATIONS.forEach { appendLine("[x] $it") }
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun textHash(): String = sha256(fullText())
}
