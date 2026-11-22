package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Trinkmenge oder Messwert (Blutdruck, Puls, Blutzucker, Temperatur, Sauerstoff, Gewicht, Atmung, Schmerz, INR, Urin). Nur lokal. */
@Entity
data class VitalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val atMillis: Long,
    /** Epoch-Tag */
    val day: Long,
    /** Schluessel aus [de.meinhelfer.tag.summary.VitalKind] */
    val kind: String,
    val value: Double,
    /** Zweiter Wert, z. B. unterer Blutdruck */
    val value2: Double? = null,
    /** Einheit beim Eintragen, z. B. "mg/dl" oder "mmol/l" */
    val unit: String = "",
    val note: String = "",
    /** Angabe zur Messung aus der Vorauswahl, z. B. "nüchtern" oder "Ohr" ([de.meinhelfer.tag.summary.VitalKind.contexts]). */
    val context: String = "",
)

/** Eintrag im Pflegetagebuch: wobei wie geholfen wurde (fuer die Begutachtung). Nur lokal. */
@Entity
data class CareLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    /** Epoch-Tag */
    val day: Long,
    val atMillis: Long,
    /** Lebensbereich 1 bis 6 wie bei der Begutachtung */
    val module: Int,
    val activity: String,
    /** Art der Hilfe, Schluessel aus [de.meinhelfer.tag.summary.HelpKind] */
    val help: String,
    /** Wie oft an diesem Tag */
    val times: Int = 1,
    /** Ungefaehre Minuten insgesamt (freiwillig) */
    val minutes: Int = 0,
    /** Auch nachts noetig */
    val night: Boolean = false,
    val note: String = "",
)

@Dao
interface VitalDao {
    @Query("SELECT * FROM VitalEntry ORDER BY atMillis DESC")
    fun all(): Flow<List<VitalEntry>>

    @Query("SELECT * FROM VitalEntry WHERE personId = :personId AND day BETWEEN :from AND :to ORDER BY atMillis")
    suspend fun range(personId: Long, from: Long, to: Long): List<VitalEntry>

    @Insert
    suspend fun insert(item: VitalEntry): Long

    @Delete
    suspend fun delete(item: VitalEntry)

    @Query("DELETE FROM VitalEntry WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}

@Dao
interface CareLogDao {
    @Query("SELECT * FROM CareLogEntry ORDER BY day DESC, atMillis")
    fun all(): Flow<List<CareLogEntry>>

    @Query("SELECT * FROM CareLogEntry WHERE personId = :personId AND day BETWEEN :from AND :to ORDER BY day, module, atMillis")
    suspend fun range(personId: Long, from: Long, to: Long): List<CareLogEntry>

    @Insert
    suspend fun insert(item: CareLogEntry): Long

    @Update
    suspend fun update(item: CareLogEntry)

    @Delete
    suspend fun delete(item: CareLogEntry)

    @Query("DELETE FROM CareLogEntry WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}
