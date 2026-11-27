package de.meinhelfer.tag.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Verschluesselt die Datenbank mit SQLCipher. Den Schluessel erzeugt die App selbst und legt ihn
 * verschluesselt im Android-Schluesselspeicher ab, er verlaesst das Handy nie.
 *
 * Eine vorhandene unverschluesselte Datenbank wird beim Start umgewandelt. Die alte Datei bleibt
 * so lange als ".klartext" liegen, bis die neue erfolgreich geoeffnet wurde. Geht etwas schief,
 * wird die alte Datenbank wiederhergestellt und die App laeuft wie bisher.
 */
object DbCrypto {
    const val DB_NAME = "mein_helfer.db"
    private const val PREFS = "db_schluessel"
    private const val KEY = "passphrase"
    private const val STATUS = "db_status"
    private const val MAX_FAILS = 2
    private val SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    private val HEADER = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    class Prepared(val encrypted: Boolean, val pendingBackup: Boolean)

    @Volatile private var loaded = false

    private fun load(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("sqlcipher")
            loaded = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun keyPrefs(context: Context) = EncryptedSharedPreferences.create(
        context, PREFS, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Gespeicherter Schluessel oder null (erzeugt keinen neuen). */
    fun storedPassphrase(context: Context): String? = try {
        keyPrefs(context).getString(KEY, null)
    } catch (_: Throwable) {
        null
    }

    /**
     * Startup-only inspection: do not initialize encrypted preferences when
     * their backing file or Android master key is absent.
     * An inaccessible existing store must not be treated as a missing passphrase.
     */
    internal fun inspectStartupKey(context: Context): DbStartupPolicy.KeyState =
        DbKeyInspection.inspect {
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS.xml")
            val backupFile = File(prefsFile.path + ".bak")
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val masterExists = store.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            if (!prefsFile.exists() && !backupFile.exists()) {
                check(!masterExists) { "Existing master key without database key store" }
                null
            } else {
                check(prefsFile.isFile && !backupFile.exists() && masterExists) {
                    "Database key store unavailable"
                }
                keyPrefs(context).getString(KEY, null)
            }
        }

    /** Existing database access must never generate a replacement passphrase. */
    internal fun requireExistingStartupPassphrase(context: Context): String {
        check(inspectStartupKey(context) == DbStartupPolicy.KeyState.Present) {
            "Database key unavailable"
        }
        val value = keyPrefs(context).getString(KEY, null)
        check(DbKeyInspection.inspect { value } == DbStartupPolicy.KeyState.Present) {
            "Database key unavailable"
        }
        return checkNotNull(value)
    }

    /** Setzt den Schluessel (bei Wiederherstellung einer Sicherung); null entfernt ihn. */
    fun setPassphrase(context: Context, value: String?) {
        val edit = keyPrefs(context).edit()
        if (value == null) edit.remove(KEY) else edit.putString(KEY, value)
        check(edit.commit()) { "Schluessel nicht gespeichert" }
    }

    /** Saves the original key in encrypted preferences before a restore changes it. */
    fun saveRestoreKey(context: Context) {
        val prefs = keyPrefs(context)
        check(!prefs.getBoolean("restore_key_pending", false)) { "Alter Wiederherstellungsschluessel vorhanden" }
        check(prefs.edit().putString("restore_old_key", prefs.getString(KEY, null))
            .putBoolean("restore_key_pending", true).commit())
    }

    /** Idempotent: retain the recovery key until the entire rollback is complete. */
    fun restoreSavedKey(context: Context) {
        val prefs = keyPrefs(context)
        check(prefs.getBoolean("restore_key_pending", false)) { "Wiederherstellungsschluessel fehlt" }
        val old = prefs.getString("restore_old_key", null)
        val edit = prefs.edit()
        if (old == null) edit.remove(KEY) else edit.putString(KEY, old)
        check(edit.commit())
    }

    /** Keep rollback metadata encrypted with the same Android-backed key store. */
    fun saveRestoreMetadata(context: Context, metadata: String) {
        check(keyPrefs(context).edit().putString("restore_metadata", metadata).commit())
    }

    fun restoreMetadata(context: Context): String? =
        keyPrefs(context).getString("restore_metadata", null)

    fun clearRestoreKey(context: Context) {
        check(keyPrefs(context).edit().remove("restore_old_key").remove("restore_key_pending").remove("restore_metadata").commit())
    }

    /** Laedt die SQLCipher-Bibliothek, falls noetig. */
    fun ensureLoaded(): Boolean = load()

    /** Schluessel (64 Hex-Zeichen). Wird beim ersten Mal erzeugt. */
    fun passphrase(context: Context): String {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.getString(KEY, null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        check(prefs.edit().putString(KEY, hex).commit()) { "Schluessel nicht gespeichert" }
        return hex
    }

    private fun isPlaintext(file: File): Boolean = try {
        file.inputStream().use { input ->
            val buf = ByteArray(HEADER.size)
            input.read(buf) == HEADER.size && buf.contentEquals(HEADER)
        }
    } catch (_: Exception) {
        false
    }

    /** Fuer die Anzeige in den Einstellungen. */
    fun isEncrypted(context: Context): Boolean {
        val file = context.getDatabasePath(DB_NAME)
        return file.exists() && file.length() > 0 && !isPlaintext(file)
    }

    fun hasBackup(file: File): Boolean = File(file.path + ".klartext").exists()

    private fun fails(context: Context): Int =
        context.getSharedPreferences(STATUS, Context.MODE_PRIVATE).getInt("fehlversuche", 0)

    private fun addFail(context: Context) {
        val p = context.getSharedPreferences(STATUS, Context.MODE_PRIVATE)
        p.edit().putInt("fehlversuche", p.getInt("fehlversuche", 0) + 1).apply()
    }

    /** Bereitet die Datei vor dem Oeffnen vor: ggf. umwandeln. Liefert, ob mit Schluessel geoeffnet werden soll. */
    fun prepare(context: Context): Prepared {
        val file = context.getDatabasePath(DB_NAME)
        if (!load()) return Prepared(false, false)

        // Abgebrochene Umstellung: alte Datei liegt noch da, neue fehlt
        if (!file.exists() && File(file.path + ".klartext").exists()) rollback(file)

        if (!file.exists() || file.length() == 0L) {
            return try {
                passphrase(context)
                Prepared(true, false)
            } catch (_: Throwable) {
                Prepared(false, false)
            }
        }
        if (!isPlaintext(file)) return Prepared(true, hasBackup(file))
        if (fails(context) >= MAX_FAILS) return Prepared(false, false)

        return try {
            migrate(context, file)
            Prepared(true, true)
        } catch (_: Throwable) {
            rollback(file)
            addFail(context)
            Prepared(false, false)
        }
    }

    private fun migrate(context: Context, file: File) {
        val pass = passphrase(context)
        val enc = File(file.parentFile, "$DB_NAME.neu")
        SUFFIXES.forEach { File(enc.path + it).delete() }

        var version = 0
        val plain = SQLiteDatabase.openOrCreateDatabase(file, "", null, null, null)
        try {
            version = plain.version
            plain.rawExecSQL("ATTACH DATABASE '${enc.path.replace("'", "''")}' AS encrypted KEY '$pass'")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }

        // Pruefen: laesst sich die neue Datei mit dem Schluessel oeffnen, mit gleicher Version?
        val probe = SQLiteDatabase.openOrCreateDatabase(enc, pass, null, null, null)
        try {
            check(probe.version == version) { "Version stimmt nicht" }
        } finally {
            probe.close()
        }
        check(!isPlaintext(enc)) { "Datei ist nicht verschluesselt" }

        // Tauschen: alte Dateien beiseite, neue an die Stelle
        SUFFIXES.forEach { s ->
            val f = File(file.path + s)
            if (f.exists()) check(f.renameTo(File(file.path + s + ".klartext"))) { "Umbenennen fehlgeschlagen" }
        }
        check(enc.renameTo(file)) { "Umbenennen fehlgeschlagen" }
        SUFFIXES.drop(1).forEach { File(enc.path + it).delete() }
    }

    /** Stellt die alte, unverschluesselte Datenbank wieder her. */
    fun rollback(file: File) {
        SUFFIXES.forEach { s ->
            val old = File(file.path + s + ".klartext")
            if (old.exists()) {
                File(file.path + s).delete()
                old.renameTo(File(file.path + s))
            }
        }
        // Reste der neuen Datei
        SUFFIXES.forEach { File(file.parentFile, "$DB_NAME.neu$it").delete() }
    }

    /** Nach erfolgreichem Oeffnen: die alte Klartext-Kopie ueberschreiben und loeschen. */
    fun discardBackup(file: File) {
        SUFFIXES.forEach { s ->
            val f = File(file.path + s + ".klartext")
            if (f.exists()) {
                wipe(f)
                f.delete()
            }
        }
    }

    fun noteFailure(context: Context) = addFail(context)

    private fun wipe(f: File) {
        try {
            RandomAccessFile(f, "rw").use { raf ->
                val zeros = ByteArray(8192)
                var left = raf.length()
                raf.seek(0)
                while (left > 0) {
                    val n = minOf(left, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, n)
                    left -= n
                }
                raf.fd.sync()
            }
        } catch (_: Exception) {
            // Loeschen versuchen wir trotzdem
        }
    }
}
