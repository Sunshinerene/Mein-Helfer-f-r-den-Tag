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
    /** Stockwerk der Wohnung, z. B. "EG" oder "3. Stock" (fuer Fahrdienst und Notfall). */
    val floor: String = "",
    /** Aufzug vorhanden? 0 = unbekannt, 1 = ja, 2 = nein. */
    val elevator: Int = 0,
    /** Hinweise zum Zugang (Klingel, Treppen, Hof ...). */
    val access: String = "",
    /** Telefonnummer der Person (Festnetz oder Handy), optional. */
    val phone: String = "",
)

/** Medikament mit Einnahmezeiten und Vorrat (fuer die Nachbestell-Warnung). */
@Entity
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    /** Einnahmezeiten, kommagetrennt, z. B. "08:00,20:00" */
    val times: String = "",
    /** Aktueller Vorrat in [unit] (Stück, ml, IE …), Kommazahlen erlaubt. */
    val stock: Double = 0.0,
    /** Verbrauch pro Tag in [unit] */
    val dailyUse: Double = 1.0,
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
    /** Herkunft des Fotos, wenn es aus dem Internet stammt (Urheber, Lizenz, Quelle), sonst leer. */
    val photoCredit: String = "",
    /** false = Vorrat noch nicht eingetragen (z. B. nach dem Einlesen eines Plans): keine Nachbestell-Warnung. */
    val stockKnown: Boolean = true,
    /** Inhalt pro Packung in [unit] (fuer "Neue Packung"), 0 = unbekannt. */
    val packSize: Double = 0.0,
    /** Einheit fuer Vorrat, Verbrauch und Menge je Einnahme: Stueck, ml, IE, Tropfen, Hub, g ([de.meinhelfer.tag.summary.MedUnit]). */
    val unit: String = "Stück",
    /** Menge je Einnahmezeit in [unit], z. B. "08:00=8,18:00=6"; leer = keine Angabe ([de.meinhelfer.tag.summary.Doses]). */
    val doses: String = "",
) {
    fun timeList(): List<String> =
        times.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Aktueller Vorrat: gezaehlter Vorrat minus Verbrauch seit dem Zaehltag (nie unter 0). */
    fun currentStock(today: Long = LocalDate.now().toEpochDay()): Double {
        val used = (today - stockDay).coerceAtLeast(0) * dailyUse.coerceAtLeast(0.0)
        return (stock - used).coerceAtLeast(0.0)
    }

    /** Verbleibende Tage oder null, wenn nicht berechenbar. */
    fun daysLeft(today: Long = LocalDate.now().toEpochDay()): Int? =
        if (stockKnown && dailyUse > 0) kotlin.math.floor(currentStock(today) / dailyUse + 1e-9).toInt() else null

    /** "Vorrat: 20 Stück", "Vorrat: 77,5 ml" bzw. "Vorrat: unbekannt" */
    fun stockText(today: Long = LocalDate.now().toEpochDay()): String =
        if (stockKnown) "Vorrat: " + stockAmountText(today) else "Vorrat: unbekannt"

    /** "20 Stück", "1200 IE" */
    fun stockAmountText(today: Long = LocalDate.now().toEpochDay()): String =
        de.meinhelfer.tag.summary.Amounts.withUnit(currentStock(today), unit)

    fun doseMap(): Map<String, Double> = de.meinhelfer.tag.summary.Doses.parse(doses)

    /** Menge fuer die Einnahme um [slot], z. B. "8 IE"; bei 1 Stueck nichts (steht schon in der Dosierung). */
    fun doseText(slot: String): String? {
        val v = de.meinhelfer.tag.summary.Doses.at(doseMap(), slot) ?: return null
        if (unit == "Stück" && v == 1.0) return null
        return de.meinhelfer.tag.summary.Amounts.withUnit(v, unit)
    }

    /** Mengen fuer alle Einnahmen, z. B. "je 5 ml" oder "08:00 8 IE, 18:00 6 IE"; null ohne Angaben. */
    fun doseSummary(): String? = de.meinhelfer.tag.summary.Doses.summary(timeList(), doseMap(), unit)

    /** "Insulin (8 IE · Lantus 100 E/ml)" fuer eine Einnahme um [slot], ohne [slot] mit allen Mengen. */
    fun intakeText(slot: String? = null): String {
        val amount = if (slot != null) doseText(slot) else doseSummary()?.takeUnless { unit == "Stück" && it == "je 1 Stück" }
        val extra = listOfNotNull(amount, dosage.takeIf { it.isNotBlank() }).joinToString(" · ")
        return name + if (extra.isNotEmpty()) " ($extra)" else ""
    }
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
    /** Gehoert zu einer Terminserie (0 = Einzeltermin). Alle Termine einer Serie haben dieselbe Nummer. */
    val seriesId: Long = 0,
    /** Wiederholung: 0 = keine, 7/14/21/28 = alle so viele Tage, -1 = jeden Monat am gleichen Tag. */
    val repeatDays: Int = 0,
    /** Wiederholen bis einschliesslich (Epoch-Tag), null = ohne Ende. */
    val repeatUntil: Long? = null,
)

@Entity
data class Doctor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val specialty: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    /** Stockwerk am Ort, z. B. "EG" oder "2. Stock". */
    val floor: String = "",
    /** Aufzug vorhanden? 0 = unbekannt, 1 = ja, 2 = nein. */
    val elevator: Int = 0,
    /** Hinweise zum Zugang (Eingang, Rampe, Treppen ...). */
    val access: String = "",
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
    /** Wie es lief: leer = normal (puenktlich genommen bzw. Termin wahrgenommen), sonst eine der Konstanten unten. */
    val status: String = "",
    /** Uhrzeit "HH:mm", wann spaeter oder frueher genommen wurde (optional). */
    val actual: String = "",
) {
    companion object {
        const val MED = "MED"
        const val APPT = "APPT"

        /** Zur Zeit genommen, nur nicht abgehakt (done = true). */
        const val UNTICKED = "UNTICKED"
        /** Spaeter genommen (done = true). */
        const val LATE = "LATE"
        /** Frueher genommen (done = true). */
        const val EARLY = "EARLY"
        /** Vergessen (done = false). */
        const val MISSED = "MISSED"
        /** Bewusst nicht gegeben, z. B. abgelehnt oder auf Anweisung (done = false). */
        const val SKIPPED = "SKIPPED"
        /** Termin abgesagt oder ausgefallen (done = false). */
        const val CANCELLED = "CANCELLED"
        /** Termin verschoben (done = false). */
        const val MOVED = "MOVED"
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

/** Aufgabe je Person (z. B. "Blutuntersuchung beim Hausarzt"), optional mit Kontakt, Prioritaet, Frist und Fahrdienst. */
@Entity
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 1,
    val text: String,
    val done: Boolean = false,
    val createdMillis: Long = 0,
    /** Zustaendiger Arzt bzw. Kontakt, null = keiner. */
    val doctorId: Long? = null,
    /** 0 = niedrig, 1 = normal, 2 = hoch. */
    val priority: Int = 1,
    /** Erledigen bis (Epoch-Tag), null = ohne Frist. */
    val dueDay: Long? = null,
    /** Findet ausserhalb der Wohnung statt (Fahrdienst anbieten). */
    val outside: Boolean = false,
    /** Verknuepfte Dokumente (IDs, kommagetrennt). */
    val docIds: String = "",
    /** Wann erledigt (Millisekunden), null = offen bzw. unbekannt (vor Version 0.36). */
    val doneMillis: Long? = null,
    /** Was dabei herausgekommen ist (z. B. Firma, Telefonnummer, Erfahrung), optional. */
    val result: String = "",
    /** Bewertung 1 bis 5 Sterne, 0 = keine. */
    val rating: Int = 0,
    /** Aus der Liste ausgeblendet, bleibt aber im Verlauf, in der Auswertung und in der Suche. */
    val archived: Boolean = false,
    /** Erinnerung per Benachrichtigung (Zeitpunkt in Millisekunden), null = keine. */
    val remindAt: Long? = null,
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
    /** Zusammenfassung in einfacher Sprache (per KI erstellt oder selbst geschrieben), optional. */
    val summary: String = "",
    /** Inhalt des Codes eines Medikationsplans (Data Matrix), beim Hochladen erkannt, sonst leer. */
    val planCode: String = "",
)
