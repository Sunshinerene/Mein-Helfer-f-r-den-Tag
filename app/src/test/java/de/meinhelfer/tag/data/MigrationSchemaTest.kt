package de.meinhelfer.tag.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Prueft die Datenbank-Umstellungen gegen das Schema, das Room beim Bauen exportiert (app/schemas/...).
 *
 * Vorgehen mit echtem SQLite: Tabellen in der neuesten Fassung anlegen, die letzten einfachen Umstellungen
 * (neue Tabellen, neue Spalten) rueckwaerts entfernen und dann vorwaerts mit dem Code der App ausfuehren.
 * Am Ende muss jede Tabelle genau so aussehen wie das Schema (Spaltennamen, Typen, NOT NULL, Primaerschluessel).
 * Weicht etwas ab, wuerde die App beim Update abstuerzen; dann schlaegt der Build fehl.
 */
class MigrationSchemaTest {
    private data class Col(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    private fun schemaFile(): File {
        val dirs = listOf(File("schemas/de.meinhelfer.tag.data.AppDatabase"), File("app/schemas/de.meinhelfer.tag.data.AppDatabase"))
        val dir = dirs.firstOrNull { it.isDirectory } ?: error("Schema-Ordner fehlt: ${dirs.map { it.absolutePath }}")
        return dir.listFiles { f -> f.name.endsWith(".json") }!!.maxBy { it.nameWithoutExtension.toInt() }
    }

    /** Tabellenname -> CREATE-Anweisung aus dem exportierten Schema (ohne JSON-Bibliothek gelesen). */
    private fun schemaTables(json: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val re = Regex("\"tableName\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"createSql\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        re.findAll(json).forEach { m ->
            val table = m.groupValues[1]
            if (table == "room_master_table") return@forEach
            val sql = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\").replace("\${TABLE_NAME}", table)
            result[table] = sql
        }
        return result
    }

    /** Fuehrt eine Umstellung aus und merkt sich nur die SQL-Anweisungen. */
    private fun statements(m: Migration): List<String> {
        val list = mutableListOf<String>()
        val db = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") list += args!![0] as String
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase
        m.migrate(db)
        return list
    }

    private val createRe = Regex("""^\s*CREATE TABLE IF NOT EXISTS `?(\w+)`?""", RegexOption.IGNORE_CASE)
    private val addRe = Regex("""^\s*ALTER TABLE `?(\w+)`? ADD COLUMN `?(\w+)`?""", RegexOption.IGNORE_CASE)
    private val updateRe = Regex("""^\s*UPDATE\s""", RegexOption.IGNORE_CASE)

    private val insertRe = Regex("""^\s*INSERT INTO\s""", RegexOption.IGNORE_CASE)
    private val dropRe = Regex("""^\s*DROP TABLE `?(\w+)`?""", RegexOption.IGNORE_CASE)
    private val renameRe = Regex("""^\s*ALTER TABLE `?(\w+)`? RENAME TO `?(\w+)`?""", RegexOption.IGNORE_CASE)

    private fun simple(sql: String) = createRe.containsMatchIn(sql) || addRe.containsMatchIn(sql) || updateRe.containsMatchIn(sql)

    /** Tabelle neu anlegen (neue Tabelle, Daten kopieren, alte loeschen, umbenennen): nur mit bekannter Fassung davor pruefbar. */
    private fun rebuild(m: Migration, sql: String): Boolean {
        val before = AppDatabase.REBUILT_BEFORE[m.endVersion] ?: return false
        return simple(sql) || insertRe.containsMatchIn(sql) ||
            dropRe.find(sql)?.let { it.groupValues[1] in before } == true ||
            renameRe.find(sql)?.let { it.groupValues[2] in before } == true
    }

    private fun columns(c: Connection, table: String): List<Col> =
        c.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                val out = mutableListOf<Col>()
                while (rs.next()) out += Col(rs.getString("name"), rs.getString("type").uppercase(), rs.getInt("notnull") == 1, rs.getInt("pk"))
                out.sortedBy { it.name }
            }
        }

    private fun exec(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }

    @Test
    fun latestMigrationsMatchExportedSchema() {
        val file = schemaFile()
        val version = file.nameWithoutExtension.toInt()
        val tables = schemaTables(file.readText())
        assertTrue("Keine Tabellen im Schema gefunden", tables.size >= 10)

        val migrations = AppDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertEquals("Letzte Umstellung muss zur Datenbank-Version passen", version, migrations.last().endVersion)
        migrations.zipWithNext().forEach { (a, b) -> assertEquals("Lücke in den Umstellungen bei ${a.endVersion}", a.endVersion, b.startVersion) }

        // Von hinten alle Umstellungen nehmen, die nur Tabellen anlegen, Spalten ergaenzen oder Werte setzen
        val sqlOf = mutableMapOf<Migration, List<String>>()
        val chain = mutableListOf<Migration>()
        for (m in migrations.reversed()) {
            val list = runCatching { statements(m) }.getOrNull() ?: break
            if (!list.all { simple(it) || rebuild(m, it) }) break
            sqlOf[m] = list
            chain.add(0, m)
        }
        assertTrue("Zu wenige Umstellungen geprüft: ${chain.size}", chain.size >= 4)

        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { expected ->
            DriverManager.getConnection("jdbc:sqlite::memory:").use { actual ->
                tables.values.forEach {
                    exec(expected, it)
                    exec(actual, it)
                }
                // Rueckwaerts: Zustand vor der ersten gepruefen Umstellung herstellen
                chain.reversed().forEach { m ->
                    val before = AppDatabase.REBUILT_BEFORE[m.endVersion].orEmpty()
                    val renamed = sqlOf.getValue(m).mapNotNull { renameRe.find(it)?.groupValues?.get(1) }.toSet()
                    sqlOf.getValue(m).reversed().forEach { sql ->
                        val rename = renameRe.find(sql)
                        when {
                            // Umgebaute Tabelle: wieder in die Fassung davor bringen
                            rename != null -> {
                                val table = rename.groupValues[2]
                                exec(actual, "DROP TABLE `$table`")
                                exec(actual, before.getValue(table))
                            }
                            createRe.find(sql)?.groupValues?.get(1)?.let { it in renamed } == true -> Unit
                            insertRe.containsMatchIn(sql) || dropRe.containsMatchIn(sql) -> Unit
                            else -> {
                                createRe.find(sql)?.let { exec(actual, "DROP TABLE `${it.groupValues[1]}`") }
                                addRe.find(sql)?.let { exec(actual, "ALTER TABLE `${it.groupValues[1]}` DROP COLUMN `${it.groupValues[2]}`") }
                            }
                        }
                    }
                }
                // Vorwaerts mit dem Code der App
                chain.forEach { m -> sqlOf.getValue(m).forEach { sql -> exec(actual, sql) } }

                tables.keys.forEach { t ->
                    assertEquals(
                        "Tabelle $t passt nach den Umstellungen ${chain.first().startVersion} bis $version nicht zum Schema",
                        columns(expected, t),
                        columns(actual, t),
                    )
                }
            }
        }
        println("Geprüft: Umstellungen ${chain.first().startVersion} → $version, ${tables.size} Tabellen")
    }

    /** Version 27: Medikamente werden umgebaut (Kommazahlen, Einheit); alle Angaben müssen erhalten bleiben. */
    @Test
    fun medicationRebuildKeepsData() {
        val m = AppDatabase.ALL_MIGRATIONS.first { it.endVersion == 27 }
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            exec(c, AppDatabase.MEDICATION_V26_SQL)
            exec(
                c,
                "INSERT INTO Medication (id, name, dosage, times, stock, dailyUse, notes, photoPath, createdDay, stockDay, personId, doctorId, " +
                    "pharmacyId, pzn, description, descriptionSource, rating, photoCredit, stockKnown, packSize) VALUES " +
                    "(7, 'Ramipril', '5 mg', '08:00', 40, 2, 'zum Essen', NULL, 100, 101, 3, 5, NULL, '12345678', '', '', '', '', 1, 50)",
            )
            statements(m).forEach { exec(c, it) }
            c.createStatement().use { st ->
                st.executeQuery("SELECT id, name, stock, dailyUse, packSize, unit, doses, personId, doctorId, stockDay FROM Medication").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(7L, rs.getLong("id"))
                    assertEquals("Ramipril", rs.getString("name"))
                    assertEquals(40.0, rs.getDouble("stock"), 1e-9)
                    assertEquals(2.0, rs.getDouble("dailyUse"), 1e-9)
                    assertEquals(50.0, rs.getDouble("packSize"), 1e-9)
                    assertEquals("Stück", rs.getString("unit"))
                    assertEquals("", rs.getString("doses"))
                    assertEquals(3L, rs.getLong("personId"))
                    assertEquals(5L, rs.getLong("doctorId"))
                    assertEquals(101L, rs.getLong("stockDay"))
                }
            }
            // Neue Einträge bekommen weiter fortlaufende Nummern
            exec(c, "INSERT INTO Medication (name, dosage, times, stock, dailyUse, notes, createdDay, stockDay, personId, pzn, description, descriptionSource, rating, photoCredit, stockKnown, packSize, unit, doses) VALUES ('Insulin', '', '22:00', 900.5, 14, '', 1, 1, 1, '', '', '', '', '', 1, 1500, 'IE', '22:00=14')")
            c.createStatement().use { st ->
                st.executeQuery("SELECT id, stock FROM Medication WHERE name = 'Insulin'").use { rs ->
                    assertTrue(rs.next())
                    assertTrue(rs.getLong("id") > 7)
                    assertEquals(900.5, rs.getDouble("stock"), 1e-9)
                }
            }
        }
    }
}
