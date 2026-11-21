package de.meinhelfer.tag.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM Medication ORDER BY name")
    fun all(): Flow<List<Medication>>

    @Insert
    suspend fun insert(item: Medication)

    @Delete
    suspend fun delete(item: Medication)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM Appointment ORDER BY whenMillis")
    fun all(): Flow<List<Appointment>>

    @Insert
    suspend fun insert(item: Appointment)

    @Delete
    suspend fun delete(item: Appointment)
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM Doctor ORDER BY name")
    fun all(): Flow<List<Doctor>>

    @Insert
    suspend fun insert(item: Doctor)

    @Delete
    suspend fun delete(item: Doctor)
}
