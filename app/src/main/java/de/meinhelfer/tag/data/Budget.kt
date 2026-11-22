package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Ausgabe aus einem Budget der Pflegekasse (Entlastungsbetrag, Verhinderungs- oder Kurzzeitpflege). Nur lokal. */
@Entity
data class BudgetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    /** Schluessel aus [de.meinhelfer.tag.summary.BudgetKind] */
    val kind: String,
    /** Tag der Leistung (Epoch-Tag); bestimmt Jahr und Uebertrag */
    val day: Long,
    /** Betrag in Euro */
    val amount: Double,
    /** Anzahl Tage (nur Verhinderungs- und Kurzzeitpflege) */
    val days: Int = 0,
    /** Anbieter, z. B. Pflegedienst oder Kurzzeitpflege-Einrichtung */
    val provider: String = "",
    /** offen, eingereicht, erstattet, abgelehnt */
    val status: String = "offen",
    val note: String = "",
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM BudgetEntry ORDER BY day DESC")
    fun all(): Flow<List<BudgetEntry>>

    @Insert
    suspend fun insert(item: BudgetEntry): Long

    @Update
    suspend fun update(item: BudgetEntry)

    @Delete
    suspend fun delete(item: BudgetEntry)

    @Query("DELETE FROM BudgetEntry WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}
