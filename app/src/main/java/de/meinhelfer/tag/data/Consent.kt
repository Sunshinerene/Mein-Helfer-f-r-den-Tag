package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Nachweis der Zustimmung zu den Nutzungsbedingungen. Liegt in der verschluesselten Datenbank.
 * [recordHash] verkettet jeden Eintrag mit dem vorigen ([prevHash]), damit nachtraegliche Aenderungen auffallen.
 */
@Entity
data class ConsentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Fassung der Nutzungsbedingungen */
    val termsVersion: Int,
    /** ZUGESTIMMT oder WIDERRUFEN */
    val action: String,
    val atMillis: Long,
    val name: String,
    val role: String,
    val appVersion: String,
    val device: String,
    /** SHA-256 des genauen Textes, dem zugestimmt wurde */
    val textHash: String,
    /** Automatisch loeschen ab diesem Zeitpunkt; null = aufbewahren, solange die App genutzt wird */
    val deleteAfterMillis: Long? = null,
    val prevHash: String = "",
    val recordHash: String = "",
) {
    companion object {
        const val ACCEPTED = "ZUGESTIMMT"
        const val REVOKED = "WIDERRUFEN"
    }
}

@Dao
interface ConsentDao {
    @Query("SELECT * FROM ConsentRecord ORDER BY atMillis, id")
    suspend fun list(): List<ConsentRecord>

    @Insert
    suspend fun insert(item: ConsentRecord): Long

    @Update
    suspend fun update(item: ConsentRecord)

    @Query("DELETE FROM ConsentRecord WHERE deleteAfterMillis IS NOT NULL AND deleteAfterMillis < :now")
    suspend fun deleteExpired(now: Long): Int
}
