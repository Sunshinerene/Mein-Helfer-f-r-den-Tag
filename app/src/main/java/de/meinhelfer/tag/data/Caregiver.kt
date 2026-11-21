package de.meinhelfer.tag.data

/** Wer sorgt fuer die Person? Bestimmt, welche Infos und Tipps zuerst kommen und wie E-Mails unterschrieben werden. */
enum class CaregiverRole(val key: String, val label: String) {
    Angehoeriger("angehoerig", "Pflegende/r Angehörige/r"),
    Pflegedienst("pflegedienst", "Pflegekraft vom Pflegedienst"),
    Betreuung("betreuung", "Betreuungs- oder Haushaltskraft (z. B. 24-Stunden-Betreuung)"),
    Betreuer("betreuer", "Rechtliche/r Betreuer/in oder Bevollmächtigte/r"),
    Ehrenamt("ehrenamt", "Nachbar/in, Freund/in oder Ehrenamt"),
    Selbst("selbst", "Ich selbst (pflegebedürftig)"),
    Sonstige("sonstige", "Etwas anderes"),
    ;

    companion object {
        fun fromKey(key: String?): CaregiverRole? = entries.firstOrNull { it.key == key }
    }
}

/** Die Daten der pflegenden Person selbst. Bleiben nur auf dem Handy; stehen in E-Mails, die du selbst abschickst. */
data class CaregiverProfile(
    val role: String = "",
    val name: String = "",
    /** z. B. Sohn, Tochter, Ehefrau, Nachbarin */
    val relation: String = "",
    /** Pflegedienst bzw. Organisation */
    val organisation: String = "",
    val phone: String = "",
    val email: String = "",
) {
    val isSet: Boolean get() = role.isNotBlank()
    val roleValue: CaregiverRole? get() = CaregiverRole.fromKey(role)

    /** Ein Satz zur Einleitung einer E-Mail, z. B. "Ich melde mich als Sohn von Anita Kunig." */
    fun intro(personName: String?): String {
        val who = personName?.takeIf { it.isNotBlank() } ?: "der betreuten Person"
        val org = organisation.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return when (roleValue) {
            CaregiverRole.Angehoeriger -> "Ich melde mich als ${relation.ifBlank { "Angehörige/r" }} von $who."
            CaregiverRole.Pflegedienst -> "Ich melde mich als Pflegekraft$org wegen der Versorgung von $who."
            CaregiverRole.Betreuung -> "Ich melde mich als Betreuungskraft$org von $who."
            CaregiverRole.Betreuer -> "Ich melde mich als Betreuer/in bzw. Bevollmächtigte/r von $who."
            CaregiverRole.Ehrenamt -> "Ich melde mich als ${relation.ifBlank { "Helfer/in" }} und unterstütze $who."
            CaregiverRole.Selbst -> "Ich melde mich in eigener Sache."
            else -> "Ich melde mich wegen $who."
        }
    }

    /** Gruss und Kontaktdaten am Ende einer E-Mail. */
    fun signature(): String = buildString {
        append("Mit freundlichen Grüßen\n")
        append(name.ifBlank { "(Name bitte ergänzen)" })
        organisation.takeIf { it.isNotBlank() && (roleValue == CaregiverRole.Pflegedienst || roleValue == CaregiverRole.Betreuung) }
            ?.let { append('\n').append(it) }
        if (phone.isNotBlank()) append("\nTelefon: ").append(phone)
        if (email.isNotBlank()) append("\nE-Mail: ").append(email)
    }
}
