package de.meinhelfer.tag.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Betreute Person, fuer die Medikamente, Termine und Bemerkungen gelten. */
@Entity
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Geburtsdatum als Epoch-Tag, optional. */
    val birthDate: Long? = null,
    /** Pflegegrad 1 bis 5, 0 = keiner bzw. noch unbekannt. */
    val careLevel: Int = 0,
    /** Freitext "Wichtig zu wissen" (z. B. Allergien, Besonderheiten). */
    val important: String = "",
    val street: String = "",
    val zip: String = "",
    val city: String = "",
    /** Krankenkasse (die Pflegekasse gehoert immer dazu). */
    val insurer: String = "",
    /** Versichertennummer, nur zum Nachschauen beim Ausfuellen von Antraegen. */
    val insuranceNumber: String = "",
)

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
    /** Dateiname des Packungsfotos im App-Ordner (nur lokal gespeichert), optional. */
    val photoPath: String? = null,
    /** Tag (Epoch-Tag), an dem das Medikament angelegt wurde – davor gibt es keine "verpassten" Einnahmen. */
    val createdDay: Long = LocalDate.now().toEpochDay(),
    /** Tag (Epoch-Tag), auf den sich [stock] bezieht; der Vorrat sinkt danach taeglich um [dailyUse]. */
    val stockDay: Long = LocalDate.now().toEpochDay(),
    /** Zu welcher betreuten Person das Medikament gehoert. */
    val personId: Long = 1,
    /** Zustaendiger Arzt (Kontakt) fuer Folgerezepte, optional. */
    val doctorId: Long? = null,
    /** Zustaendige Apotheke (Kontakt) fuer die Nachbestellung, optional. */
    val pharmacyId: Long? = null,
    /** Pharmazentralnummer (8 Ziffern) von der Packung, optional. */
    val pzn: String = "",
    /** Beschreibung aus dem Internet (nur Anhaltspunkt), optional. */
    val description: String = "",
    /** Quelle der Beschreibung, z. B. Link zur Wikipedia-Seite. */
    val descriptionSource: String = "",
    /** KI-Einschaetzung (Staerke, Nebenwirkungen, Wechselwirkungen) als JSON, nur Anhaltspunkt, optional. */
    val rating: String = "",
) {
    fun timeList(): List<String> =
        times.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Aktueller Vorrat: gezaehlter Vorrat minus Verbrauch seit dem Zaehltag (nie unter 0). */
    fun currentStock(today: Long = LocalDate.now().toEpochDay()): Int {
        val used = (today - stockDay).coerceAtLeast(0) * dailyUse.coerceAtLeast(0)
        return (stock - used).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Verbleibende Tage oder null, wenn nicht berechenbar. */
    fun daysLeft(today: Long = LocalDate.now().toEpochDay()): Int? =
        if (dailyUse > 0) currentStock(today) / dailyUse else null
}

@Entity
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val whenMillis: Long,
    /** Ende des Termins (optional, "von – bis"). */
    val endMillis: Long? = null,
    val place: String = "",
    val notes: String = "",
    /** Verknuepfter Arzt bzw. Institution (optional). */
    val doctorId: Long? = null,
    /** Zu welcher betreuten Person der Termin gehoert. */
    val personId: Long = 1,
    /** Erinnerung so viele Minuten vor dem Termin; negativ = keine Erinnerung. */
    val reminderMinutes: Int = 60,
    /** Angehaengte Dokumente (IDs, kommagetrennt), optional. */
    val docIds: String = "",
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

/**
 * Abhaken: eine Medikamenten-Einnahme (kind = "MED", slot = Uhrzeit) oder ein Termin (kind = "APPT")
 * an einem Tag, optional mit Bemerkung.
 */
@Entity
data class Checkin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val refId: Long,
    /** Epoch-Tag */
    val day: Long,
    /** Einnahmezeit "08:00" bei Medikamenten, sonst leer */
    val slot: String = "",
    val done: Boolean = false,
    val note: String = "",
    val atMillis: Long = 0,
) {
    companion object {
        const val MED = "MED"
        const val APPT = "APPT"
    }
}

/** Freie Bemerkung zum ganzen Tag. */
@Entity
data class DayNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val personId: Long = 1,
    val text: String = "",
)

/** Eigene To-do-Liste je Person (z. B. "Medikamentenplan beim Hausarzt besprechen"). */
@Entity
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 1,
    val text: String,
    val done: Boolean = false,
    val createdMillis: Long = 0,
)

/** Dokument (Arztbericht, Befund, Bescheid ...). Die Datei liegt verschluesselt im App-Ordner, der erkannte Text in der Datenbank. */
@Entity
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 1,
    val title: String,
    val category: String = "Sonstiges",
    /** Datum des Dokuments als Epoch-Tag, optional. */
    val docDate: Long? = null,
    /** Dateiname der verschluesselten Datei im App-Ordner. */
    val fileName: String,
    val mimeType: String = "application/pdf",
    /** Aus dem Dokument ausgelesener Text (durchsuchbar, korrigierbar). */
    val text: String = "",
    val notes: String = "",
    val doctorId: Long? = null,
    val createdMillis: Long = System.currentTimeMillis(),
)
