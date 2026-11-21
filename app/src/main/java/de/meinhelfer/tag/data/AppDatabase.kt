package de.meinhelfer.tag.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Medication::class, Appointment::class, Doctor::class, Checkin::class, DayNote::class, Person::class, Todo::class, Document::class],
    version = 16,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun doctorDao(): DoctorDao
    abstract fun checkinDao(): CheckinDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun personDao(): PersonDao
    abstract fun todoDao(): TodoDao
    abstract fun documentDao(): DocumentDao

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

        /**
         * Version 5: Betreute Personen. Bestehende Eintraege gehoeren zur Person mit der ID 1
         * (wird hier angelegt, "Betreute Person", spaeter umbenennbar).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Person` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
                )
                db.execSQL("INSERT INTO `Person` (`id`, `name`) VALUES (1, 'Betreute Person')")
                db.execSQL("ALTER TABLE Medication ADD COLUMN personId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE Appointment ADD COLUMN personId INTEGER NOT NULL DEFAULT 1")
                // DayNote bekommt eine eigene ID + Person: Tabelle neu anlegen und Daten uebernehmen.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `DayNote_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, " +
                        "`personId` INTEGER NOT NULL, `text` TEXT NOT NULL)",
                )
                db.execSQL("INSERT INTO `DayNote_new` (`day`, `personId`, `text`) SELECT `day`, 1, `text` FROM `DayNote`")
                db.execSQL("DROP TABLE `DayNote`")
                db.execSQL("ALTER TABLE `DayNote_new` RENAME TO `DayNote`")
            }
        }

        /** Version 6: To-do-Liste und Erinnerungszeit pro Termin. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Todo` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, `done` INTEGER NOT NULL, `createdMillis` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE Appointment ADD COLUMN reminderMinutes INTEGER NOT NULL DEFAULT 60")
            }
        }

        /** Version 7: Arzt und Apotheke je Medikament (fuer die Nachbestellung). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN doctorId INTEGER")
                db.execSQL("ALTER TABLE Medication ADD COLUMN pharmacyId INTEGER")
            }
        }

        /** Version 8: PZN und Beschreibung je Medikament. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN pzn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Medication ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Medication ADD COLUMN descriptionSource TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 9: Geburtsdatum, Pflegegrad und "Wichtig" je Person. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Person ADD COLUMN birthDate INTEGER")
                db.execSQL("ALTER TABLE Person ADD COLUMN careLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Person ADD COLUMN important TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 10: Dokumente. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Document` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `category` TEXT NOT NULL, `docDate` INTEGER, " +
                        "`fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`notes` TEXT NOT NULL, `doctorId` INTEGER, `createdMillis` INTEGER NOT NULL)",
                )
            }
        }

        /** Version 11: Adresse, Krankenkasse und Versichertennummer je Person. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("street", "zip", "city", "insurer", "insuranceNumber").forEach { column ->
                    db.execSQL("ALTER TABLE Person ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /** Version 12: KI-Einschaetzung je Medikament. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN rating TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 13: Dokumente an Termine anhaengen. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Appointment ADD COLUMN docIds TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 14: Zusammenfassung je Dokument. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Document ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Todo ADD COLUMN doctorId INTEGER")
                db.execSQL("ALTER TABLE Todo ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE Todo ADD COLUMN dueDay INTEGER")
                db.execSQL("ALTER TABLE Todo ADD COLUMN outside INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Checkin ADD COLUMN status TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Checkin ADD COLUMN actual TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context, encrypted: Boolean): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, DbCrypto.DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
            if (encrypted) {
                builder.openHelperFactory(
                    net.zetetic.database.sqlcipher.SupportOpenHelperFactory(DbCrypto.passphrase(context).toByteArray(Charsets.UTF_8)),
                )
            }
            return builder.build()
        }

        /** Oeffnet die Datenbank verschluesselt; eine alte unverschluesselte wird dabei umgewandelt (mit Rueckfall). */
        private fun open(context: Context): AppDatabase {
            val file = context.getDatabasePath(DbCrypto.DB_NAME)
            val prepared = DbCrypto.prepare(context)
            val db = build(context, prepared.encrypted)
            if (prepared.pendingBackup) {
                try {
                    db.openHelper.writableDatabase
                } catch (_: Throwable) {
                    try {
                        db.close()
                    } catch (_: Throwable) {
                    }
                    DbCrypto.rollback(file)
                    DbCrypto.noteFailure(context)
                    return build(context, false)
                }
                DbCrypto.discardBackup(file)
            }
            return db
        }
    }
}
