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
