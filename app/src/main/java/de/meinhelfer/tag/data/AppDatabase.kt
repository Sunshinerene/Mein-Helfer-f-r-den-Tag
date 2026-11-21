package de.meinhelfer.tag.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// TODO (vor echten Nutzern): Datenbank verschluesseln (z. B. SQLCipher) und App-Sperre ergaenzen.
@Database(
    entities = [Medication::class, Appointment::class, Doctor::class, Checkin::class, DayNote::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun doctorDao(): DoctorDao
    abstract fun checkinDao(): CheckinDao
    abstract fun dayNoteDao(): DayNoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Version 2: Termine koennen mit einem Arzt bzw. einer Institution verknuepft werden. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Appointment ADD COLUMN doctorId INTEGER")
            }
        }

        /** Version 3: Medikamente koennen ein Foto der Packung haben. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN photoPath TEXT")
            }
        }

        /**
         * Version 4: Abhaken + Bemerkungen (Checkin, DayNote), Terminende, Vorrat-Stichtag.
         * Die SQL-Befehle muessen exakt dem Schema entsprechen, das Room fuer die Entities erwartet.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val today = java.time.LocalDate.now().toEpochDay()
                db.execSQL("ALTER TABLE Medication ADD COLUMN createdDay INTEGER NOT NULL DEFAULT $today")
                db.execSQL("ALTER TABLE Medication ADD COLUMN stockDay INTEGER NOT NULL DEFAULT $today")
                db.execSQL("ALTER TABLE Appointment ADD COLUMN endMillis INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Checkin` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `refId` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                        "`slot` TEXT NOT NULL, `done` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`atMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `DayNote` (" +
                        "`day` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`day`))",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mein_helfer.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
