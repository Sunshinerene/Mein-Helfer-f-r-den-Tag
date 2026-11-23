package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person

/** Daten-Check des Steuerwerks: fehlende Angaben, die im Alltag später fehlen würden. Nur lesen, nichts ändern. */
object DataCheck {
    fun findings(
        persons: List<Person>,
        meds: List<Medication>,
        doctors: List<Doctor>,
        appointments: List<Appointment>,
        nowMillis: Long,
        multi: Boolean = persons.size > 1,
    ): List<String> {
        val out = mutableListOf<String>()
        fun who(personId: Long) = if (multi) persons.firstOrNull { it.id == personId }?.name?.let { " ($it)" }.orEmpty() else ""
        meds.forEach { m ->
            if (m.timeList().isEmpty() && m.dailyUse > 0.0) out += "Bei „${m.name}“${who(m.personId)} fehlt die Einnahmezeit."
            if (!m.stockKnown) out += "Bei „${m.name}“${who(m.personId)} ist der Vorrat noch nicht eingetragen."
            if (m.doctorId == null || doctors.none { it.id == m.doctorId }) out += "Für „${m.name}“${who(m.personId)} ist kein Arzt für Folgerezepte eingetragen."
        }
        val usedDoctors = (meds.mapNotNull { it.doctorId } + appointments.mapNotNull { it.doctorId }).toSet()
        doctors.filter { it.id in usedDoctors && it.phone.isBlank() }.forEach { out += "Bei „${it.name}“ fehlt die Telefonnummer." }
        persons.forEach { p ->
            if (p.insurer.isBlank()) out += "Bei ${p.name} fehlt die Krankenkasse (wichtig für Notfallkarte und Anträge)."
        }
        appointments.filter { it.whenMillis > nowMillis && it.whenMillis - nowMillis < 30L * 24 * 3600 * 1000 }
            .filter { it.place.isBlank() && it.doctorId == null }
            .forEach { out += "Beim Termin „${it.title}“${who(it.personId)} fehlt der Ort." }
        return out
    }

    /** Ein Satz für Sunny: höchstens drei Funde, Rest gezählt. */
    fun sentence(found: List<String>): String {
        if (found.isEmpty()) return ""
        val shown = found.take(3).joinToString(" ")
        return "Mir ist beim Durchsehen aufgefallen: $shown" + if (found.size > 3) " Und ${found.size - 3} weitere Kleinigkeiten." else ""
    }
}
