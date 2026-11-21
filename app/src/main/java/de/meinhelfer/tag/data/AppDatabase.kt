package de.meinhelfer.tag.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// TODO (vor echten Nutzern): Datenbank verschluesseln (z. B. SQLCipher) und App-Sperre ergaenzen.
@Database(
    entities = [Medication::class, Appointment::class, Doctor::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun doctorDao(): DoctorDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Version 2: Termine koennen mit einem Arzt bzw. einer Institution verknuepft werden. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Appointment ADD COLUMN doctorId INTEGER")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mein_helfer.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
