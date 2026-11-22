package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Zustaendigkeit eines Kontakts: Arzt fuer Rezepte, Apotheke fuer Bestellung und Abholung (je betreuter Person)
 * oder eine Vertretung (z. B. Urlaub) fuer einen bestimmten Zeitraum, die fuer alle Personen gilt.
 */
@Entity
data class Responsibility(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Betreute Person; 0 = fuer alle (Vertretungen). */
    val personId: Long = 0,
    /** [Duties.REZEPT], [Duties.APOTHEKE] oder [Duties.VERTRETUNG] */
    val role: String,
    /** Zustaendiger Kontakt bzw. bei einer Vertretung der vertretende Kontakt. */
    val contactId: Long,
    /** Nur bei einer Vertretung: der abwesende Kontakt. */
    val replacesId: Long? = null,
    /** Zeitraum der Vertretung (Epoch-Tage, jeweils einschliesslich). */
    val fromDay: Long? = null,
    val untilDay: Long? = null,
)

@Dao
interface ResponsibilityDao {
    @Query("SELECT * FROM Responsibility")
    fun all(): Flow<List<Responsibility>>

    @Query("SELECT * FROM Responsibility")
    suspend fun list(): List<Responsibility>

    @Insert
    suspend fun insert(item: Responsibility): Long

    @Delete
    suspend fun delete(item: Responsibility)

    @Query("DELETE FROM Responsibility WHERE personId = :personId AND role = :role")
    suspend fun clearRole(personId: Long, role: String)

    @Query("DELETE FROM Responsibility WHERE contactId = :contactId OR replacesId = :contactId")
    suspend fun deleteForContact(contactId: Long)

    @Query("DELETE FROM Responsibility WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}

/** Wer ist gerade zustaendig? Eigene Angabe beim Medikament geht vor, danach der Standard der Person, dann die Vertretung. */
object Duties {
    const val REZEPT = "rezept"
    const val APOTHEKE = "apotheke"
    const val VERTRETUNG = "vertretung"

    private val SHORT = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)

    /** Ergebnis: wirksamer Kontakt, ob er aus dem Standard kommt und ggf. die Vertretung. */
    data class Resolved(val id: Long?, val fromDefault: Boolean, val substitute: Responsibility?, val baseId: Long?)

    fun defaultFor(list: List<Responsibility>, personId: Long, role: String): Long? =
        list.firstOrNull { it.personId == personId && it.role == role }?.contactId

    /** Vertretung fuer einen Kontakt an diesem Tag oder null. */
    fun substituteFor(list: List<Responsibility>, contactId: Long, day: Long = LocalDate.now().toEpochDay()): Responsibility? =
        list.filter {
            it.role == VERTRETUNG && it.replacesId == contactId &&
                (it.fromDay ?: Long.MIN_VALUE) <= day && day <= (it.untilDay ?: Long.MAX_VALUE)
        }.maxByOrNull { it.fromDay ?: Long.MIN_VALUE }

    fun resolve(list: List<Responsibility>, explicit: Long?, personId: Long, role: String, day: Long = LocalDate.now().toEpochDay()): Resolved {
        val base = explicit ?: defaultFor(list, personId, role)
        val sub = base?.let { substituteFor(list, it, day) }
        return Resolved(sub?.contactId ?: base, explicit == null && base != null, sub, base)
    }

    /** Medikament mit den gerade wirksamen Zustaendigen. Nur zum Anzeigen und Nachbestellen, nicht speichern. */
    fun effective(m: Medication, list: List<Responsibility>, day: Long = LocalDate.now().toEpochDay()): Medication =
        m.copy(
            doctorId = resolve(list, m.doctorId, m.personId, REZEPT, day).id,
            pharmacyId = resolve(list, m.pharmacyId, m.personId, APOTHEKE, day).id,
        )

    /** "bis 30.11." bzw. "vom 01.12. bis 14.12." */
    fun period(r: Responsibility): String = listOfNotNull(
        r.fromDay?.let { "vom " + LocalDate.ofEpochDay(it).format(SHORT) },
        r.untilDay?.let { "bis " + LocalDate.ofEpochDay(it).format(SHORT) },
    ).joinToString(" ")

    /** Text zur Herkunft, z. B. "(Standard)" oder "(Vertretung für Dr. A bis 30.11.)". */
    fun note(r: Resolved, doctors: List<Doctor>): String = when {
        r.substitute != null -> "(Vertretung für " + (doctors.firstOrNull { it.id == r.baseId }?.name ?: "–") + " " + period(r.substitute) + ")"
        r.fromDefault -> "(zuständig)"
        else -> ""
    }.trim()
}
