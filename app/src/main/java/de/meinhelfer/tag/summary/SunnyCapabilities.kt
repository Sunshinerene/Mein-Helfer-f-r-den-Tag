package de.meinhelfer.tag.summary

/** Capability metadata only: registration never executes an action or grants permission. */
object SunnyCapabilities {
    enum class Connection { Device, Internet }
    enum class Approval { ReadOnly, Confirm, Biometric }
    data class Skill(val id: String, val title: String, val connection: Connection,
                     val approval: Approval, val planned: Boolean = false)
    val skills = listOf(
        Skill("daily_plan", "Tagesübersicht", Connection.Device, Approval.ReadOnly),
        Skill("mood", "Stimmungsreaktion", Connection.Device, Approval.ReadOnly),
        Skill("reorder", "Nachbestellung vorbereiten", Connection.Device, Approval.Confirm),
        Skill("data_check", "Daten-Check", Connection.Device, Approval.ReadOnly),
        Skill("mail_draft", "E-Mail vorbereiten", Connection.Device, Approval.Confirm, true),
        Skill("mail_send", "E-Mail versenden", Connection.Internet, Approval.Biometric, true),
        Skill("news", "Pflegenachrichten", Connection.Internet, Approval.ReadOnly),
        Skill("weather", "Wetterinformationen", Connection.Internet, Approval.ReadOnly),
        Skill("local_model", "KI-Modell auf dem Smartphone", Connection.Device, Approval.ReadOnly, true)
    )
    enum class Availability { Ready, Offline, Disabled, Planned, Unknown }
    fun availability(id: String, internet: Boolean, enabled: Boolean): Availability {
        val skill = skills.firstOrNull { it.id == id } ?: return Availability.Unknown
        return when {
            skill.planned -> Availability.Planned
            !enabled -> Availability.Disabled
            skill.connection == Connection.Internet && !internet -> Availability.Offline
            else -> Availability.Ready
        }
    }
    /** Recommendations never authorize execution. */
    fun maySuggest(id: String, internet: Boolean, enabled: Boolean, quiet: Boolean): Boolean =
        !quiet && availability(id, internet, enabled) == Availability.Ready
}
