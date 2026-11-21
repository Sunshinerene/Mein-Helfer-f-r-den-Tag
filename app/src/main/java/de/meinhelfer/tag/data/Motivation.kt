package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Stimmung der pflegenden Person (Smiley-Bewertung). Bleibt verschluesselt auf dem Handy. */
@Entity
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    /** Epoch-Tag */
    val day: Long,
    /** 1 = schlecht, 2 = nicht gut, 3 = geht so, 4 = gut, 5 = sehr gut */
    val mood: Int,
    /** Stichworte, kommagetrennt (z. B. erschöpft, zufrieden) */
    val tags: String = "",
    val note: String = "",
) {
    fun tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/** Ein Ziel mit Schritten, z. B. ein Antrag, etwas fuer die Gesundheit oder den Alltag. */
@Entity
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Betreute Person, 0 = eigenes Ziel (z. B. die eigene Gesundheit) */
    val personId: Long = 0,
    val title: String,
    /** Siehe GoalKind */
    val kind: String = "eigenes",
    val details: String = "",
    /** Wunschtermin (Epoch-Tag), optional */
    val targetDay: Long? = null,
    val createdMillis: Long = 0,
    /** AKTIV, ERREICHT, PAUSIERT */
    val status: String = ACTIVE,
    val doneMillis: Long? = null,
    /** Letzte KI-Einschaetzung des Wegs (JSON), leer = keine */
    val aiCheck: String = "",
    val aiCheckMillis: Long? = null,
) {
    companion object {
        const val ACTIVE = "AKTIV"
        const val REACHED = "ERREICHT"
        const val PAUSED = "PAUSIERT"
    }
}

/** Art eines Ziels. */
enum class GoalKind(val key: String, val label: String, val emoji: String) {
    Antrag("antrag", "Antrag", "📝"),
    Gesundheit("gesundheit", "Gesundheit", "💚"),
    Alltag("alltag", "Alltag", "🏠"),
    Entlastung("entlastung", "Entlastung", "🌿"),
    Eigenes("eigenes", "Eigenes", "⭐"),
    ;

    companion object {
        fun fromKey(key: String?): GoalKind = entries.firstOrNull { it.key == key } ?: Eigenes
    }
}

/** Ein Schritt auf dem Weg zu einem Ziel. */
@Entity
data class GoalStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val position: Int,
    val title: String,
    val detail: String = "",
    /** Bis wann (Epoch-Tag), optional */
    val dueDay: Long? = null,
    /** Wann erledigt, null = offen */
    val doneMillis: Long? = null,
    val note: String = "",
)

@Dao
interface MoodDao {
    @Query("SELECT * FROM MoodEntry ORDER BY atMillis DESC")
    fun all(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM MoodEntry WHERE day >= :fromDay ORDER BY atMillis")
    suspend fun since(fromDay: Long): List<MoodEntry>

    @Insert
    suspend fun insert(item: MoodEntry): Long

    @Delete
    suspend fun delete(item: MoodEntry)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM Goal ORDER BY createdMillis DESC")
    fun all(): Flow<List<Goal>>

    @Query("SELECT * FROM GoalStep ORDER BY goalId, position")
    fun allSteps(): Flow<List<GoalStep>>

    @Query("SELECT * FROM GoalStep WHERE goalId = :goalId ORDER BY position")
    suspend fun steps(goalId: Long): List<GoalStep>

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Insert
    suspend fun insertStep(step: GoalStep): Long

    @Update
    suspend fun updateStep(step: GoalStep)

    @Delete
    suspend fun deleteStep(step: GoalStep)

    @Query("DELETE FROM GoalStep WHERE goalId = :goalId")
    suspend fun deleteSteps(goalId: Long)

    @Query("DELETE FROM GoalStep WHERE goalId IN (SELECT id FROM Goal WHERE personId = :personId)")
    suspend fun deleteStepsForPerson(personId: Long)

    @Query("DELETE FROM Goal WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}
