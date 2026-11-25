package de.meinhelfer.tag.lab

import android.content.Context
import android.os.Process
import de.meinhelfer.tag.backup.Backup
import de.meinhelfer.tag.backup.BackupCrypto
import de.meinhelfer.tag.data.DbCrypto
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.update.AppPrefs
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

internal class LabRestore(private val context: Context) {
    init { check(context.packageName == "de.meinhelfer.tag.lab") }
    private val log = context.getSharedPreferences("lab_real_restore", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE)
    private val db get() = context.getDatabasePath(DbCrypto.DB_NAME)
    private val docs get() = File(context.filesDir, "docs_enc")
    private val photos get() = File(context.filesDir, "photos")
    private fun key(s: String) = "synthetic-lab-key-$s"
    private val password get() = "synthetic-lab-backup".toCharArray()
    val points = listOf("READY", "APPLYING", "ASIDE:" + DbCrypto.DB_NAME, "PLACE:" + DbCrypto.DB_NAME,
        "PLACE:docs_enc", "PLACE:photos", "KEY", "PREFS", "COMMITTED", "CLEAN:" + DbCrypto.DB_NAME + ".vorher")
    fun pending() = log.getBoolean("pending", false)
    fun report() = points.joinToString("\n") { "$it: ${log.getString("result_$it", "Nicht getestet")}" }

    private fun makeDb(file: File, state: String) {
        check(DbCrypto.ensureLoaded())
        file.parentFile?.mkdirs()
        check(!file.exists())
        val sql = SQLiteDatabase.openOrCreateDatabase(file, key(state), null, null, null)
        try {
            sql.execSQL("CREATE TABLE lab_fixture (value TEXT NOT NULL)")
            sql.execSQL("INSERT INTO lab_fixture VALUES ('TEST_$state')")
        } finally { sql.close() }
    }

    private fun verify(state: String) {
        check(db.isFile && DbCrypto.isEncrypted(context))
        check(DbCrypto.storedPassphrase(context) == key(state))
        val sql = SQLiteDatabase.openOrCreateDatabase(db, key(state), null, null, null)
        try {
            sql.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
            }
            sql.rawQuery("SELECT value FROM lab_fixture", emptyArray<String>()).use {
                check(it.moveToFirst() && it.getString(0) == "TEST_$state" && !it.moveToNext())
            }
        } finally { sql.close() }
        check(DocumentStore.openPlain(context, "test.txt").use { it.readBytes().toString(Charsets.UTF_8) } == "DOC_$state")
        check(File(photos, "test.txt").readText() == "PHOTO_$state")
        check(prefs.getString("lab_fixture", null) == state)
        check(!File(context.filesDir, "restore_staging").exists())
        check(!File(context.filesDir, "restore_staging_cleanup").exists())
        check(DbCrypto.restoreMetadata(context) == null)
        check(listOf(db, docs, photos).none { File(it.path + ".vorher").exists() })
    }

    fun start(point: String): String {
        check(point in points && !pending())
        check(!File(context.filesDir, "restore_staging").exists())
        check(!File(context.filesDir, "restore_staging_cleanup").exists())
        check(listOf(db, docs, photos).none { File(it.path + ".vorher").exists() })
        listOf("", "-wal", "-shm", "-journal").forEach { check(File(db.path + it).deleteRecursively()) }
        check(docs.deleteRecursively() && photos.deleteRecursively())
        makeDb(db, "A")
        DbCrypto.setPassphrase(context, key("A"))
        check(docs.mkdirs() && photos.mkdirs())
        DocumentStore.storeTo(context, File(docs, "test.txt"), "DOC_A".byteInputStream())
        File(photos, "test.txt").writeText("PHOTO_A")
        check(prefs.edit().clear().putString("lab_fixture", "A").commit())
        verify("A")
        val candidate = File(context.cacheDir, "lab_candidate.db")
        check(candidate.deleteRecursively())
        makeDb(candidate, "B")
        val archive = File(context.cacheDir, "lab_fixture.mhbackup")
        ZipOutputStream(BackupCrypto.encrypt(archive.outputStream(), password)).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry("db/" + DbCrypto.DB_NAME, candidate.readBytes())
            entry("db/passphrase.txt", key("B").toByteArray())
            entry("docs/test.txt", "DOC_B".toByteArray())
            entry("photos/test.txt", "PHOTO_B".toByteArray())
            entry("prefs.json", JSONObject().put("lab_fixture", JSONObject().put("t", "s").put("v", "B")).toString().toByteArray())
            entry("meta.json", JSONObject().put("format", 1).put("encrypted", true).put("dbVersion", 0).put("documents", 1).put("photos", 1).toString().toByteArray())
        }
        runBlocking { archive.inputStream().use { Backup.stage(context, it, password) {} } }
        check(log.edit().putBoolean("pending", true).putBoolean("reached", false).putString("point", point).commit())
        Backup.confirmStaged(context)
        if (point == "READY") {
            check(log.edit().putBoolean("reached", true).commit())
            Process.killProcess(Process.myPid())
            error("Prozessabbruch nicht erfolgt")
        }
        Backup.labCheckpoint = { reached ->
            if (reached == point) {
                check(log.edit().putBoolean("reached", true).commit())
                Process.killProcess(Process.myPid())
                error("Prozessabbruch nicht erfolgt")
            }
        }
        try { Backup.applyPending(context) } finally { Backup.labCheckpoint = null }
        error("Abbruchpunkt nicht erreicht")
    }

    fun evaluate(): String {
        if (!pending()) return report()
        val point = checkNotNull(log.getString("point", null))
        val result = runCatching {
            check(log.getBoolean("reached", false)) { "Abbruchpunkt nicht erreicht" }
            Backup.applyPending(context)
            val expected = if (point == "READY" || point == "COMMITTED" || point.startsWith("CLEAN:")) "B" else "A"
            verify(expected)
            "BESTANDEN – Zustand $expected"
        }.getOrElse { "FEHLER – ${it.message}" }
        check(log.edit().putString("result_$point", result).putBoolean("pending", !result.startsWith("BESTANDEN")).putBoolean("reached", !result.startsWith("BESTANDEN") && log.getBoolean("reached", false)).commit())
        return "$point: $result"
    }
}
