package de.meinhelfer.tag.lab

import android.content.Context
import androidx.room.Room
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.DbCrypto
import de.meinhelfer.tag.backup.Backup
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.ai.SunnyMemory
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import kotlinx.coroutines.runBlocking

internal class LabRoundTrip(private val context: Context) {
    init { check(context.packageName == "de.meinhelfer.tag.lab") }
    private val dbFile get() = context.getDatabasePath(DbCrypto.DB_NAME)
    private val prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE)
    private val password get() = "lab-roundtrip-only".toCharArray()
    private val staging get() = File(context.filesDir, "restore_staging")
    private val docs get() = File(context.filesDir, "docs_enc")
    private val photos get() = File(context.filesDir, "photos")
    private fun room(): AppDatabase {
        check(DbCrypto.ensureLoaded())
        return Room.databaseBuilder(context, AppDatabase::class.java, DbCrypto.DB_NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .openHelperFactory(SupportOpenHelperFactory(DbCrypto.passphrase(context).toByteArray()))
            .build()
    }
    private fun clearFixture() {
        check(!staging.exists() && !File(context.filesDir, "restore_staging_cleanup").exists())
        check(DbCrypto.restoreMetadata(context) == null)
        check((listOf("", "-wal", "-shm", "-journal").map { File(dbFile.path + it) } + listOf(docs, photos)).none { File(it.path + ".vorher").exists() })
        listOf("", "-wal", "-shm", "-journal").forEach { check(File(dbFile.path + it).deleteRecursively()) }
        check(docs.deleteRecursively() && photos.deleteRecursively())
        check(prefs.edit().clear().commit())
        DbCrypto.setPassphrase(context, null)
    }
    fun run(): String = runBlocking {
        clearFixture()
        val archive = File(context.cacheDir, "room-roundtrip.mhbackup")
        val originalKey = DbCrypto.passphrase(context)
        val vocabulary = SunnyMemory.exportText(context)
        room().use { db ->
            val sql = db.openHelper.writableDatabase
            sql.execSQL("CREATE TABLE IF NOT EXISTS lab_roundtrip (value TEXT NOT NULL)")
            sql.execSQL("INSERT INTO lab_roundtrip VALUES ('SYNTHETIC_ROOM_A')")
            check(prefs.edit().putString("lab_roundtrip", "A").commit())
            DocumentStore.storeTo(context, File(docs, "roundtrip.enc"), "SYNTHETIC_DOC_A".byteInputStream())
            check(photos.mkdirs())
            File(photos, "roundtrip.txt").writeText("SYNTHETIC_PHOTO_A")
            val info = archive.outputStream().use { Backup.createForLab(context, it, password, db) }
            check(info.skipped == 0 && info.documents == 1 && info.photos == 1)
        }
        val results = mutableListOf("Room-Schema und reguläre Backup-Erstellung: BESTANDEN")
        val bytes = archive.readBytes()
        suspend fun reject(label: String, data: ByteArray, pass: CharArray) {
            val failure = runCatching { Backup.stage(context, data.inputStream(), pass) {} }.exceptionOrNull()
            check(failure is java.io.IOException) { "$label: Sicherung nicht korrekt abgelehnt: $failure" }
            check(!staging.exists())
            check(DbCrypto.storedPassphrase(context) == originalKey && prefs.getString("lab_roundtrip", null) == "A")
            results.add("$label: BESTANDEN")
        }
        reject("Falsches Passwort", bytes, "wrong-lab-password".toCharArray())
        reject("Abgeschnittene Sicherung", bytes.copyOf(bytes.size - 12), password)
        reject("Beschädigte Sicherung", bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }, password)
        clearFixture()
        SunnyMemory.importText(context, "", replace = true)
        archive.inputStream().use { Backup.stage(context, it, password) {} }
        Backup.confirmStaged(context)
        Backup.applyPending(context)
        check(DbCrypto.storedPassphrase(context) == originalKey)
        check(DbCrypto.isEncrypted(context))
        room().use { db ->
            db.openHelper.writableDatabase.query("SELECT value FROM lab_roundtrip").use {
                check(it.moveToFirst() && it.getString(0) == "SYNTHETIC_ROOM_A" && !it.moveToNext())
            }
        }
        check(DocumentStore.openPlain(context, "roundtrip.enc").use { it.readBytes().toString(Charsets.UTF_8) } == "SYNTHETIC_DOC_A")
        check(File(photos, "roundtrip.txt").readText() == "SYNTHETIC_PHOTO_A")
        check(prefs.getString("lab_roundtrip", null) == "A")
        check(SunnyMemory.exportText(context) == vocabulary)
        check(!staging.exists() && DbCrypto.restoreMetadata(context) == null)
        results.add("Wiederherstellung ohne vorhandene Datenbank: BESTANDEN")
        results.add("Dokumente, Fotos, Einstellungen, Schlüssel, Sprachschatz: BESTANDEN")
        results.joinToString("\n")
    }
}
