package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM Medication ORDER BY name")
    fun all(): Flow<List<Medication>>

    @Query("SELECT * FROM Medication")
    suspend fun list(): List<Medication>

    @Query("SELECT * FROM Medication WHERE personId = :personId")
    suspend fun listFor(personId: Long): List<Medication>

    @Query("SELECT * FROM Medication WHERE id = :id")
    suspend fun byId(id: Long): Medication?

    @Insert
    suspend fun insert(item: Medication)

    @Update
    suspend fun update(item: Medication)

    @Delete
    suspend fun delete(item: Medication)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM Appointment ORDER BY whenMillis")
    fun all(): Flow<List<Appointment>>

    @Query("SELECT * FROM Appointment")
    suspend fun list(): List<Appointment>

    @Query("SELECT * FROM Appointment WHERE personId = :personId")
    suspend fun listFor(personId: Long): List<Appointment>

    @Query("SELECT * FROM Appointment WHERE id = :id")
    suspend fun byId(id: Long): Appointment?

    @Insert
    suspend fun insert(item: Appointment)

    @Update
    suspend fun update(item: Appointment)

    @Delete
    suspend fun delete(item: Appointment)
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM Doctor ORDER BY name")
    fun all(): Flow<List<Doctor>>

    @Insert
    suspend fun insert(item: Doctor)

    @Update
    suspend fun update(item: Doctor)

    @Delete
    suspend fun delete(item: Doctor)
}

@Dao
interface CheckinDao {
    @Query("SELECT * FROM Checkin WHERE day BETWEEN :from AND :to")
    fun range(from: Long, to: Long): Flow<List<Checkin>>

    @Query("SELECT * FROM Checkin WHERE day BETWEEN :from AND :to")
    suspend fun rangeList(from: Long, to: Long): List<Checkin>

    @Query("SELECT * FROM Checkin WHERE kind = :kind AND refId = :refId AND day = :day AND slot = :slot LIMIT 1")
    suspend fun find(kind: String, refId: Long, day: Long, slot: String): Checkin?

    @Insert
    suspend fun insert(item: Checkin)

    @Update
    suspend fun update(item: Checkin)

    @Query("DELETE FROM Checkin WHERE kind = :kind AND refId = :refId")
    suspend fun deleteFor(kind: String, refId: Long)
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM DayNote WHERE personId = :personId AND day BETWEEN :from AND :to")
    fun range(personId: Long, from: Long, to: Long): Flow<List<DayNote>>

    @Query("SELECT * FROM DayNote WHERE personId = :personId AND day BETWEEN :from AND :to")
    suspend fun rangeList(personId: Long, from: Long, to: Long): List<DayNote>

    @Query("SELECT * FROM DayNote WHERE personId = :personId AND day = :day LIMIT 1")
    suspend fun find(personId: Long, day: Long): DayNote?

    @Insert
    suspend fun insert(item: DayNote)

    @Update
    suspend fun update(item: DayNote)

    @Delete
    suspend fun delete(item: DayNote)

    @Query("DELETE FROM DayNote WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM Person ORDER BY id")
    fun all(): Flow<List<Person>>

    @Query("SELECT * FROM Person ORDER BY id")
    suspend fun list(): List<Person>

    @Insert
    suspend fun insert(item: Person): Long

    @Update
    suspend fun update(item: Person)

    @Delete
    suspend fun delete(item: Person)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM Todo WHERE personId = :personId ORDER BY done, createdMillis")
    fun all(personId: Long): Flow<List<Todo>>

    @Insert
    suspend fun insert(item: Todo)

    @Update
    suspend fun update(item: Todo)

    @Delete
    suspend fun delete(item: Todo)

    @Query("DELETE FROM Todo WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM Document ORDER BY COALESCE(docDate, createdMillis / 86400000) DESC, id DESC")
    fun all(): Flow<List<Document>>

    @Query("SELECT * FROM Document WHERE personId = :personId")
    suspend fun listFor(personId: Long): List<Document>

    @Insert
    suspend fun insert(item: Document)

    @Update
    suspend fun update(item: Document)

    @Delete
    suspend fun delete(item: Document)
}
