package de.meinhelfer.tag.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Medikament mit Einnahmezeiten und Vorrat (fuer die Nachbestell-Warnung). */
@Entity
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    /** Einnahmezeiten, kommagetrennt, z. B. "08:00,20:00" */
    val times: String = "",
    /** Aktueller Vorrat in Einheiten (Tabletten usw.) */
    val stock: Int = 0,
    /** Verbrauch pro Tag in Einheiten */
    val dailyUse: Int = 1,
    val notes: String = "",
) {
    fun timeList(): List<String> =
        times.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Verbleibende Tage oder null, wenn nicht berechenbar. */
    fun daysLeft(): Int? = if (dailyUse > 0) stock / dailyUse else null
}

@Entity
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val whenMillis: Long,
    val place: String = "",
    val notes: String = "",
    /** Verknuepfter Arzt bzw. Institution (optional). */
    val doctorId: Long? = null,
)

@Entity
data class Doctor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val specialty: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
)
