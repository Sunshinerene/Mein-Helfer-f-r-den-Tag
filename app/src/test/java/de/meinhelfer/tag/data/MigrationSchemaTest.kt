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

    private fun simple(sql: String) = createRe.containsMatchIn(sql) || addRe.containsMatchIn(sql) || updateRe.containsMatchIn(sql)

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
            if (!list.all { simple(it) }) break
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
                    sqlOf.getValue(m).reversed().forEach { sql ->
                        createRe.find(sql)?.let { exec(actual, "DROP TABLE `${it.groupValues[1]}`") }
                        addRe.find(sql)?.let { exec(actual, "ALTER TABLE `${it.groupValues[1]}` DROP COLUMN `${it.groupValues[2]}`") }
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
}
