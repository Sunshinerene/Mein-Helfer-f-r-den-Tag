package de.meinhelfer.tag.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Medication::class, Appointment::class, Doctor::class, Checkin::class, DayNote::class, Person::class, Todo::class, Document::class, ConsentRecord::class, MoodEntry::class, Goal::class, GoalStep::class, Responsibility::class, VitalEntry::class, CareLogEntry::class, BudgetEntry::class],
    version = 31,
    // Das Schema wird beim Bauen exportiert; ein Test vergleicht es mit den Umstellungen (MigrationSchemaTest).
    exportSchema = true,
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
    abstract fun consentDao(): ConsentDao
    abstract fun moodDao(): MoodDao
    abstract fun goalDao(): GoalDao
    abstract fun responsibilityDao(): ResponsibilityDao
    abstract fun vitalDao(): VitalDao
    abstract fun careLogDao(): CareLogDao
    abstract fun budgetDao(): BudgetDao

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

        /** Version 17: Stockwerk/Aufzug/Zugang bei Person und Kontakt, Dokumente an Aufgaben. */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("Person", "Doctor").forEach { t ->
                    db.execSQL("ALTER TABLE $t ADD COLUMN floor TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $t ADD COLUMN elevator INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $t ADD COLUMN access TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE Todo ADD COLUMN docIds TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Todo ADD COLUMN doneMillis INTEGER")
                db.execSQL("ALTER TABLE Todo ADD COLUMN result TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Todo ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Todo ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Appointment ADD COLUMN seriesId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Appointment ADD COLUMN repeatDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Appointment ADD COLUMN repeatUntil INTEGER")
            }
        }

        /** Version 19: Nachweis der Zustimmung zu den Nutzungsbedingungen. */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ConsentRecord` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `termsVersion` INTEGER NOT NULL, `action` TEXT NOT NULL, " +
                        "`atMillis` INTEGER NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `appVersion` TEXT NOT NULL, " +
                        "`device` TEXT NOT NULL, `textHash` TEXT NOT NULL, `deleteAfterMillis` INTEGER, " +
                        "`prevHash` TEXT NOT NULL, `recordHash` TEXT NOT NULL)",
                )
            }
        }

        /** Version 20: Stimmung, Ziele und Schritte (Tages-Motivator), Telefonnummer der Person. */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Person ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `MoodEntry` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMillis` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                        "`mood` INTEGER NOT NULL, `tags` TEXT NOT NULL, `note` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Goal` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `details` TEXT NOT NULL, `targetDay` INTEGER, `createdMillis` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `doneMillis` INTEGER, `aiCheck` TEXT NOT NULL, `aiCheckMillis` INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `GoalStep` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `detail` TEXT NOT NULL, `dueDay` INTEGER, `doneMillis` INTEGER, `note` TEXT NOT NULL)",
                )
            }
        }

        /** Version 21: Herkunft des Medikamentenfotos (freie Bilder aus dem Internet). */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN photoCredit TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Version 22: Vorrat "unbekannt". Ein gespeicherter Vorrat von 0 hiess bisher fast immer "nicht eingetragen"
         * (z. B. nach dem Einlesen eines Plans), deshalb gilt er jetzt als unbekannt statt als aufgebraucht.
         */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN stockKnown INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE Medication SET stockKnown = 0 WHERE stock = 0")
            }
        }

        /** Version 23: Packungsgroesse, Erinnerung bei Aufgaben, Code des Medikationsplans am Dokument. */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN packSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Todo ADD COLUMN remindAt INTEGER")
                db.execSQL("ALTER TABLE Document ADD COLUMN planCode TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Responsibility` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, `role` TEXT NOT NULL, " +
                        "`contactId` INTEGER NOT NULL, `replacesId` INTEGER, `fromDay` INTEGER, `untilDay` INTEGER)",
                )
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `VitalEntry` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, `atMillis` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, `kind` TEXT NOT NULL, `value` REAL NOT NULL, `value2` REAL, `unit` TEXT NOT NULL, `note` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `CareLogEntry` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                        "`atMillis` INTEGER NOT NULL, `module` INTEGER NOT NULL, `activity` TEXT NOT NULL, `help` TEXT NOT NULL, " +
                        "`times` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `night` INTEGER NOT NULL, `note` TEXT NOT NULL)",
                )
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `BudgetEntry` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`day` INTEGER NOT NULL, `amount` REAL NOT NULL, `days` INTEGER NOT NULL, `provider` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `note` TEXT NOT NULL)",
                )
            }
        }

        /** Medikamente in Version 26 (ganze Zahlen), fuer den Umbau in Version 27 und den Test der Umstellungen. */
        internal const val MEDICATION_V26_SQL =
            "CREATE TABLE IF NOT EXISTS `Medication` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`dosage` TEXT NOT NULL, `times` TEXT NOT NULL, `stock` INTEGER NOT NULL, `dailyUse` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                "`photoPath` TEXT, `createdDay` INTEGER NOT NULL, `stockDay` INTEGER NOT NULL, `personId` INTEGER NOT NULL, `doctorId` INTEGER, " +
                "`pharmacyId` INTEGER, `pzn` TEXT NOT NULL, `description` TEXT NOT NULL, `descriptionSource` TEXT NOT NULL, `rating` TEXT NOT NULL, " +
                "`photoCredit` TEXT NOT NULL, `stockKnown` INTEGER NOT NULL, `packSize` INTEGER NOT NULL)"

        private const val MEDICATION_COLUMNS =
            "`id`, `name`, `dosage`, `times`, `stock`, `dailyUse`, `notes`, `photoPath`, `createdDay`, `stockDay`, `personId`, `doctorId`, " +
                "`pharmacyId`, `pzn`, `description`, `descriptionSource`, `rating`, `photoCredit`, `stockKnown`, `packSize`"

        /**
         * Version 27: Medikamente mit Einheit (Stueck, ml, IE, Tropfen, Hub, g) und Menge je Einnahme.
         * Vorrat, Verbrauch und Packungsinhalt werden Kommazahlen (z. B. 7,5 ml am Tag oder eine halbe Tablette).
         * SQLite kann den Typ einer Spalte nicht aendern, deshalb wird die Tabelle neu angelegt und alles uebernommen.
         */
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Medication_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`dosage` TEXT NOT NULL, `times` TEXT NOT NULL, `stock` REAL NOT NULL, `dailyUse` REAL NOT NULL, `notes` TEXT NOT NULL, " +
                        "`photoPath` TEXT, `createdDay` INTEGER NOT NULL, `stockDay` INTEGER NOT NULL, `personId` INTEGER NOT NULL, `doctorId` INTEGER, " +
                        "`pharmacyId` INTEGER, `pzn` TEXT NOT NULL, `description` TEXT NOT NULL, `descriptionSource` TEXT NOT NULL, `rating` TEXT NOT NULL, " +
                        "`photoCredit` TEXT NOT NULL, `stockKnown` INTEGER NOT NULL, `packSize` REAL NOT NULL, `unit` TEXT NOT NULL, `doses` TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `Medication_new` ($MEDICATION_COLUMNS, `unit`, `doses`) " +
                        "SELECT $MEDICATION_COLUMNS, 'Stück', '' FROM `Medication`",
                )
                db.execSQL("DROP TABLE `Medication`")
                db.execSQL("ALTER TABLE `Medication_new` RENAME TO `Medication`")
            }
        }

        /** Version 28: Angabe zur Messung (z. B. „nüchtern“, „Ohr“). */
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE VitalEntry ADD COLUMN context TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 29: Einnahme-Fenster (gegebene Menge, Messwerte beim Abhaken) und Haltbarkeit nach dem Anbruch. */
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN askAtIntake INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Medication ADD COLUMN intakeVitals TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Medication ADD COLUMN openLifeDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Medication ADD COLUMN openedDay INTEGER")
                db.execSQL("ALTER TABLE Checkin ADD COLUMN amount REAL")
                // Insulin und andere Mittel in IE: beim Abhaken nach der gegebenen Menge fragen
                db.execSQL("UPDATE Medication SET askAtIntake = 1 WHERE unit = 'IE'")
            }
        }

        /** Version 30: Schwerbehindertenausweis und Wohnsituation je Person. */
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Person ADD COLUMN disability TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Person ADD COLUMN household TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 31: Essen und Trinken je Person (Diabetes, Kostform, Allergien). */
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Person ADD COLUMN nutrition TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Tabellen, die eine Umstellung neu anlegt: Version nach der Umstellung → Tabelle → Fassung davor (fuer den Test). */
        internal val REBUILT_BEFORE: Map<Int, Map<String, String>> = mapOf(27 to mapOf("Medication" to MEDICATION_V26_SQL))

        /** Alle Umstellungen in Reihenfolge (auch fuer den Test der Umstellungen). */
        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
            MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
        )

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context, encrypted: Boolean): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, DbCrypto.DB_NAME)
                .addMigrations(*ALL_MIGRATIONS)
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
