package de.meinhelfer.tag.backup

import android.content.Context
import android.content.Intent
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.DbCrypto
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.media.PhotoStore
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Was in einer Sicherung steckt (zur Anzeige vor dem Wiederherstellen). */
class BackupInfo(
    val createdMillis: Long,
    val appVersion: String,
    val dbVersion: Int,
    val documents: Int,
    val photos: Int,
    val skipped: Int,
)

/**
 * Sicherung und Wiederherstellung. Die Sicherung ist eine einzelne Datei, mit einem eigenen Passwort
 * verschluesselt (nicht an dieses Handy gebunden): Datenbank samt Schluessel, Dokumente im Klartext
 * (in der Datei aber verschluesselt), Fotos und Einstellungen. Wiederherstellen geschieht in zwei Schritten:
 * erst entpacken und pruefen ([stage]), dann beim naechsten App-Start austauschen ([applyPending]).
 */
object Backup {
    // Only the separately installed lab can arm this in-memory callback.
    internal var labCheckpoint: ((String) -> Unit)? = null
    private fun checkpoint(context: Context, name: String) {
        if (context.packageName == "de.meinhelfer.tag.lab") labCheckpoint?.invoke(name)
    }
    private const val STAGING = "restore_staging"
    private const val READY = "READY"
    private const val DB_ENTRY = "db/" + DbCrypto.DB_NAME
    private val SUFFIXES = listOf("", "-wal", "-shm", "-journal")

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    fun suggestedName(): String =
        "MeinHelfer-Sicherung-" + java.time.LocalDate.now().toString() + ".mhbackup"

    // ------------------------------------------------------------ Sicherung erstellen

    suspend fun create(
        context: Context,
        target: OutputStream,
        password: CharArray,
        onProgress: (String) -> Unit,
    ): BackupInfo = createFromDatabase(context, target, password, onProgress, null)

    internal suspend fun createForLab(context: Context, target: OutputStream, password: CharArray, db: AppDatabase): BackupInfo {
        check(context.packageName == "de.meinhelfer.tag.lab")
        return createFromDatabase(context, target, password, {}, db)
    }

    private suspend fun createFromDatabase(
        context: Context, target: OutputStream, password: CharArray,
        onProgress: (String) -> Unit, labDb: AppDatabase?,
    ): BackupInfo = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        onProgress("Datenbank wird vorbereitet …")
        val db = labDb ?: AppDatabase.get(app)
        val helperDb = db.openHelper.writableDatabase
        helperDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val dbVersion = helperDb.version
        val dbFile = app.getDatabasePath(DbCrypto.DB_NAME)
        val encrypted = DbCrypto.isEncrypted(app)
        val created = System.currentTimeMillis()

        var docs = 0
        var photos = 0
        var skipped = 0
        ZipOutputStream(BackupCrypto.encrypt(target, password)).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            fun entry(name: String, writer: (OutputStream) -> Unit) {
                zip.putNextEntry(ZipEntry(name))
                writer(zip)
                zip.closeEntry()
            }

            // Hold the SQLite writer lock while copying the database and its WAL.
            helperDb.beginTransactionNonExclusive()
            try {
                entry(DB_ENTRY) { out -> dbFile.inputStream().use { it.copyTo(out) } }
                val wal = File(dbFile.path + "-wal")
                if (wal.exists() && wal.length() > 0) entry("db/" + DbCrypto.DB_NAME + "-wal") { out -> wal.inputStream().use { it.copyTo(out) } }
                helperDb.setTransactionSuccessful()
            } finally {
                helperDb.endTransaction()
            }
            if (encrypted) {
                val pass = DbCrypto.storedPassphrase(app) ?: throw IllegalStateException("Datenbankschluessel fehlt")
                entry("db/passphrase.txt") { it.write(pass.toByteArray(Charsets.UTF_8)) }
            }

            val prefs = JSONObject()
            app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                val o = JSONObject()
                when (v) {
                    is Boolean -> o.put("t", "b").put("v", v)
                    is Int -> o.put("t", "i").put("v", v)
                    is Long -> o.put("t", "l").put("v", v)
                    is Float -> o.put("t", "f").put("v", v.toDouble())
                    is String -> o.put("t", "s").put("v", v)
                    is Set<*> -> o.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> return@forEach
                }
                prefs.put(k, o)
            }
            entry("prefs.json") { it.write(prefs.toString().toByteArray(Charsets.UTF_8)) }
            // Sunnys Sprachschatz
            entry("sprachschatz.txt") { it.write(de.meinhelfer.tag.ai.SunnyMemory.exportText(app).toByteArray(Charsets.UTF_8)) }

            val docNames = DocumentStore.storedNames(app)
            docNames.forEachIndexed { i, name ->
                onProgress("Dokumente ${i + 1} von ${docNames.size} …")
                val stream = try {
                    DocumentStore.openPlain(app, name)
                } catch (_: Exception) {
                    skipped++
                    return@forEachIndexed
                }
                stream.use { input -> entry("docs/$name") { out -> input.copyTo(out) } }
                docs++
            }
            val photoNames = PhotoStore.names(app)
            photoNames.forEachIndexed { i, name ->
                onProgress("Fotos ${i + 1} von ${photoNames.size} …")
                PhotoStore.file(app, name).inputStream().use { input -> entry("photos/$name") { out -> input.copyTo(out) } }
                photos++
            }

            val meta = JSONObject()
                .put("format", 1)
                .put("app", appVersion(app))
                .put("dbVersion", dbVersion)
                .put("encrypted", encrypted)
                .put("created", created)
                .put("documents", docs)
                .put("photos", photos)
                .put("skipped", skipped)
            entry("meta.json") { it.write(meta.toString().toByteArray(Charsets.UTF_8)) }
        }
        if (skipped == 0) AppPrefs.setLastBackup(app, created)
        BackupInfo(created, appVersion(app), dbVersion, docs, photos, skipped)
    }

    // ------------------------------------------------------------ Entpacken und pruefen

    /** Entpackt die Sicherung in einen Zwischenordner und prueft sie. Es wird noch nichts ersetzt. */
    suspend fun stage(
        context: Context,
        source: InputStream,
        password: CharArray,
        onProgress: (String) -> Unit,
    ): BackupInfo = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val staging = File(app.filesDir, STAGING)
        check(!File(staging, "APPLYING").exists() && !File(staging, "COMMITTED").exists()) {
            "Eine begonnene Wiederherstellung muss zuerst abgeschlossen werden."
        }
        check(staging.deleteRecursively()) { "Alter Zwischenstand konnte nicht entfernt werden" }
        check(staging.mkdirs()) { "Zwischenordner konnte nicht erstellt werden" }
        try {
            val base = staging.canonicalPath + File.separator
            var docs = 0
            BackupCrypto.decrypt(source, password).use { decrypted ->
            ZipInputStream(object : InputStream() {
                override fun read() = decrypted.read()
                override fun read(b: ByteArray, off: Int, len: Int) = decrypted.read(b, off, len)
                override fun close() {}
            }).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    val okName = name == "meta.json" || name == "prefs.json" || name == "sprachschatz.txt" || name.startsWith("db/") ||
                        name.startsWith("docs/") || name.startsWith("photos/")
                    if (!okName) continue
                    val rel = if (name.startsWith("docs/")) "docs_enc/" + name.removePrefix("docs/") else name
                    val target = File(staging, rel)
                    if (!target.canonicalPath.startsWith(base)) throw java.io.IOException("Ungueltiger Eintrag")
                    target.parentFile?.mkdirs()
                    when {
                        name.startsWith("docs/") -> {
                            docs++
                            onProgress("Dokumente werden gelesen ($docs) …")
                            DocumentStore.storeTo(app, target, nonClosing(zip))
                        }
                        else -> target.outputStream().use { nonClosing(zip).copyTo(it) }
                    }
                }
            }
            // ZIP may stop at its central directory before the authenticated stream ends.
            val drain = ByteArray(8192)
            while (decrypted.read(drain) != -1) { /* authenticate remaining blocks */ }
            }
            val metaFile = File(staging, "meta.json")
            if (!metaFile.exists()) throw java.io.IOException("Sicherung unvollständig")
            val meta = JSONObject(metaFile.readText())
            val dbFile = File(staging, DB_ENTRY)
            if (!dbFile.exists()) throw java.io.IOException("Sicherung unvollständig")

            onProgress("Prüfe die Daten …")
            val encrypted = meta.optBoolean("encrypted", true)
            val pass = if (encrypted) File(staging, "db/passphrase.txt").readText() else ""
            val sqlWal = File(staging, "db/" + DbCrypto.DB_NAME + "-wal")
            check(DbCrypto.ensureLoaded()) { "Verschluesselung nicht verfuegbar" }
            val version = probeVersion(dbFile, pass, encrypted)
            val current = de.meinhelfer.tag.data.DATABASE_SCHEMA_VERSION
            if (version > current) throw NewerBackupException()
            if (sqlWal.exists() && sqlWal.length() == 0L) sqlWal.delete()

            File(staging, "VERIFIED").writeText("1")
            BackupInfo(
                createdMillis = meta.optLong("created"),
                appVersion = meta.optString("app", "?"),
                dbVersion = version,
                documents = docs,
                photos = File(staging, "photos").listFiles()?.size ?: 0,
                skipped = meta.optInt("skipped"),
            )
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    class NewerBackupException : java.io.IOException("Die Sicherung stammt aus einer neueren App-Version")

    private fun probeVersion(file: File, pass: String, encrypted: Boolean): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(file, if (encrypted) pass else "", null, null, null)
        try {
            db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true) && !cursor.moveToNext()) { "Datenbankintegrität fehlerhaft" }
            }
            return db.version
        } finally {
            db.close()
        }
    }

    /** ZipInputStream soll beim Kopieren eines Eintrags nicht geschlossen werden. */
    private fun nonClosing(zip: ZipInputStream): InputStream = object : InputStream() {
        override fun read(): Int = zip.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = zip.read(b, off, len)
        override fun close() {}
    }

    /** Verwirft einen entpackten, aber nicht uebernommenen Stand. */
    fun confirmStaged(context: Context) {
        val staging = File(context.applicationContext.filesDir, STAGING)
        check(File(staging, "VERIFIED").exists()) { "Keine geprüfte Sicherung vorhanden" }
        check(!File(staging, "APPLYING").exists() && !File(staging, "COMMITTED").exists())
        File(staging, READY).outputStream().use { it.write(1); it.fd.sync() }
    }

    fun discardStaged(context: Context) {
        val staging = File(context.applicationContext.filesDir, STAGING)
        check(!File(staging, "APPLYING").exists() && !File(staging, "COMMITTED").exists()) {
            "Begonnene Wiederherstellung darf nicht verworfen werden."
        }
        staging.deleteRecursively()
    }

    // ------------------------------------------------------------ Austauschen beim Start

    /** Neustart der App, damit [applyPending] vor dem Oeffnen der Datenbank laufen kann. */
    fun restartApp(context: Context) {
        val app = context.applicationContext
        runCatching { AppDatabase.get(app).close() }
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) app.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * Wird beim Start der App aufgerufen, bevor die Datenbank geoeffnet wird. Tauscht die Daten aus,
     * die vorher entpackt und geprueft wurden. Geht etwas schief, bleibt der alte Stand erhalten.
     */
    fun applyPending(context: Context) {
        val app = context.applicationContext
        val staging = File(app.filesDir, STAGING)
        val cleanup = File(app.filesDir, STAGING + "_cleanup")
        if (cleanup.exists()) {
            check(!staging.exists()) { "Mehrere Wiederherstellungsstände vorhanden" }
            DbCrypto.clearRestoreKey(app)
            check(cleanup.deleteRecursively())
        }
        if (!staging.exists()) return
        fun finishCleanup() {
            check(!cleanup.exists())
            check(staging.renameTo(cleanup)) { "Wiederherstellungsprotokoll konnte nicht archiviert werden" }
            DbCrypto.clearRestoreKey(app)
            check(cleanup.deleteRecursively())
        }
        if (!File(staging, READY).exists() && listOf("APPLYING", "COMMITTED", "ROLLED_BACK").none { File(staging, it).exists() }) {
            staging.deleteRecursively()
            return
        }

        val dbFile = app.getDatabasePath(DbCrypto.DB_NAME)
        val docsDir = File(app.filesDir, "docs_enc")
        val photosDir = File(app.filesDir, "photos")
        val prefsFile = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE)
        // Never retry a partially applied restore automatically.
        val interrupted = File(staging, "APPLYING")
        if (File(staging, "ROLLED_BACK").exists()) {
            finishCleanup()
            AppPrefs.setRestoreResult(app, "fehler:" + System.currentTimeMillis())
            return
        }
        if (File(staging, "COMMITTED").exists()) {
            val originals = SUFFIXES.map { File(dbFile.path + it) } +
                SUFFIXES.map { File(dbFile.path + it + ".klartext") } + listOf(docsDir, photosDir)
            originals.forEach { check(File(it.path + ".vorher").deleteRecursively()) }
            finishCleanup()
            AppPrefs.setRestoreResult(app, "ok:" + System.currentTimeMillis())
            return
        }
        if (interrupted.exists()) {
            val metadata = JSONObject(checkNotNull(DbCrypto.restoreMetadata(app)) {
                "Wiederherstellungsprotokoll fehlt"
            })
            val originals = SUFFIXES.map { File(dbFile.path + it) } +
                SUFFIXES.map { File(dbFile.path + it + ".klartext") } +
                listOf(docsDir, photosDir)
            val presence = metadata.getJSONArray("presence")
            check(presence.length() == originals.size)
            originals.forEachIndexed { index, original ->
                val previous = File(original.path + ".vorher")
                if (previous.exists()) {
                    check(presence.getBoolean(index))
                    check(original.deleteRecursively())
                    check(previous.renameTo(original))
                    checkpoint(app, "ROLLBACK:" + original.name)
                } else if (!presence.getBoolean(index)) {
                    check(original.deleteRecursively())
                } else {
                    check(original.exists()) { "Original fehlt: ${original.name}" }
                }
            }
            DbCrypto.restoreSavedKey(app)
            checkpoint(app, "ROLLBACK_KEY")
            val json = metadata.getJSONObject("prefs")
            val edit = prefsFile.edit().clear()
            json.keys().forEach { k ->
                val o = json.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> edit.putBoolean(k, o.getBoolean("v"))
                    "i" -> edit.putInt(k, o.getInt("v"))
                    "l" -> edit.putLong(k, o.getLong("v"))
                    "f" -> edit.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> edit.putString(k, o.getString("v"))
                    "set" -> {
                        val arr = o.getJSONArray("v")
                        edit.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                    else -> error("Unbekannter Einstellungstyp")
                }
            }
            check(edit.commit())
            checkpoint(app, "ROLLBACK_PREFS")
            if (metadata.has("vocabulary")) de.meinhelfer.tag.ai.SunnyMemory.importText(app, metadata.getString("vocabulary"), replace = true)
            java.io.FileOutputStream(File(staging, "ROLLED_BACK")).use { marker ->
                marker.write(1)
                marker.fd.sync()
            }
            finishCleanup()
            AppPrefs.setRestoreResult(app, "fehler:" + System.currentTimeMillis())
            return
        }
        val oldPass = DbCrypto.storedPassphrase(app)
        val oldPrefs = prefsFile.all.toMap()
        val moved = mutableListOf<Pair<File, File>>() // (alt vorher-Name, urspruenglicher Ort)
        var passChanged = false
        val placed = mutableSetOf<File>()

        fun aside(f: File) {
            if (!f.exists()) return
            val to = File(f.path + ".vorher")
            check(!to.exists()) { "Vorheriger Wiederherstellungsstand vorhanden" }
            check(f.renameTo(to)) { "Umbenennen fehlgeschlagen" }
            moved.add(to to f)
            checkpoint(app, "ASIDE:" + f.name)
        }

        fun place(from: File, to: File) {
            check(!to.exists()) { "Ziel existiert bereits" }
            to.parentFile?.mkdirs()
            placed.add(to)
            check(from.renameTo(to)) { "Wiederherstellung konnte Datei nicht verschieben" }
            checkpoint(app, "PLACE:" + to.name)
        }

        try {
            val meta = JSONObject(File(staging, "meta.json").readText())
            val encrypted = meta.optBoolean("encrypted", true)

            val originalFiles = SUFFIXES.map { File(dbFile.path + it) } +
                SUFFIXES.map { File(dbFile.path + it + ".klartext") } +
                listOf(docsDir, photosDir)
            check(originalFiles.none { File(it.path + ".vorher").exists() }) {
                "Vorheriger Wiederherstellungsstand vorhanden"
            }
            val originalPrefs = JSONObject()
            oldPrefs.forEach { (k, v) ->
                val item = JSONObject()
                when (v) {
                    is Boolean -> item.put("t", "b").put("v", v)
                    is Int -> item.put("t", "i").put("v", v)
                    is Long -> item.put("t", "l").put("v", v)
                    is Float -> item.put("t", "f").put("v", v.toDouble())
                    is String -> item.put("t", "s").put("v", v)
                    is Set<*> -> item.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> error("Unbekannter Einstellungstyp")
                }
                originalPrefs.put(k, item)
            }
            val originalPresence = JSONArray(originalFiles.map { it.exists() })
            // No live files have been moved yet; a previous pre-marker attempt is safe to clear.
            DbCrypto.clearRestoreKey(app)
            DbCrypto.saveRestoreKey(app)
            DbCrypto.saveRestoreMetadata(app, JSONObject()
                .put("prefs", originalPrefs)
                .put("vocabulary", de.meinhelfer.tag.ai.SunnyMemory.exportText(app))
                .put("presence", originalPresence)
                .toString())
            java.io.FileOutputStream(interrupted).use { marker -> marker.write(1); marker.fd.sync() }
            checkpoint(app, "APPLYING")
            SUFFIXES.forEach { aside(File(dbFile.path + it)) }
            SUFFIXES.forEach { aside(File(dbFile.path + it + ".klartext")) }
            aside(docsDir)
            aside(photosDir)

            SUFFIXES.forEach { s ->
                val from = File(staging, "db/" + DbCrypto.DB_NAME + s)
                if (from.exists()) place(from, File(dbFile.path + s))
            }
            val stagedDocs = File(staging, "docs_enc")
            if (stagedDocs.exists()) place(stagedDocs, docsDir) else { placed.add(docsDir); check(docsDir.mkdirs()) }
            val stagedPhotos = File(staging, "photos")
            if (stagedPhotos.exists()) place(stagedPhotos, photosDir) else { placed.add(photosDir); check(photosDir.mkdirs()) }

            if (encrypted) {
                passChanged = true
                DbCrypto.setPassphrase(app, File(staging, "db/passphrase.txt").readText())
            }

            checkpoint(app, "KEY")
            val prefsJson = File(staging, "prefs.json")
            if (prefsJson.exists()) {
                val json = JSONObject(prefsJson.readText())
                val edit = prefsFile.edit().clear()
                json.keys().forEach { k ->
                    val o = json.getJSONObject(k)
                    when (o.getString("t")) {
                        "b" -> edit.putBoolean(k, o.getBoolean("v"))
                        "i" -> edit.putInt(k, o.getInt("v"))
                        "l" -> edit.putLong(k, o.getLong("v"))
                        "f" -> edit.putFloat(k, o.getDouble("v").toFloat())
                        "s" -> edit.putString(k, o.getString("v"))
                        "set" -> {
                            val arr = o.getJSONArray("v")
                            edit.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                        }
                    }
                }
                check(edit.commit()) { "Einstellungen nicht gespeichert" }
            }

            checkpoint(app, "PREFS")
            File(staging, "sprachschatz.txt").takeIf { it.exists() }?.let { f ->
                de.meinhelfer.tag.ai.SunnyMemory.importText(app, f.readText(), replace = true)
            }

            check(dbFile.isFile) { "Wiederhergestellte Datenbank fehlt" }
            check(DbCrypto.ensureLoaded()) { "Verschluesselung nicht verfuegbar" }
            probeVersion(dbFile, if (encrypted) File(staging, "db/passphrase.txt").readText() else "", encrypted)
            // Commit marker must be durable before deleting any original files.
            java.io.FileOutputStream(File(staging, "COMMITTED")).use { marker ->
                marker.write(1)
                marker.fd.sync()
            }
            checkpoint(app, "COMMITTED")
            // Erfolg: alte Staende loeschen
            moved.forEach { (vorher, _) -> check(vorher.deleteRecursively()); checkpoint(app, "CLEAN:" + vorher.name) }
            finishCleanup()
            AppPrefs.setRestoreResult(app, "ok:" + System.currentTimeMillis())
        } catch (t: Throwable) {
            if (cleanup.exists() || !staging.exists() || File(staging, "COMMITTED").exists()) throw t
            // Before APPLYING, no live data was changed. Never roll back using stale metadata.
            if (!interrupted.exists()) throw t
            // Zurueck zum alten Stand
            val rollback = runCatching {
                placed.forEach { check(it.deleteRecursively()) }
                moved.asReversed().forEach { (vorher, original) ->
                    check(original.deleteRecursively())
                    check(vorher.renameTo(original)) { "Original konnte nicht zurückverschoben werden: ${original.name}" }
                }
                if (passChanged) DbCrypto.restoreSavedKey(app)
                val edit = prefsFile.edit().clear()
                oldPrefs.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> edit.putBoolean(k, v)
                        is Int -> edit.putInt(k, v)
                        is Long -> edit.putLong(k, v)
                        is Float -> edit.putFloat(k, v)
                        is String -> edit.putString(k, v)
                        is Set<*> -> edit.putStringSet(k, v.map { it.toString() }.toSet())
                    }
                }
                check(edit.commit()) { "Einstellungen konnten nicht zurückgesetzt werden" }
                DbCrypto.restoreMetadata(app)?.let { saved ->
                    val metadata = JSONObject(saved)
                    if (metadata.has("vocabulary")) de.meinhelfer.tag.ai.SunnyMemory.importText(app, metadata.getString("vocabulary"), replace = true)
                }
            }
            if (rollback.isSuccess) {
                java.io.FileOutputStream(File(staging, "ROLLED_BACK")).use { marker ->
                    marker.write(1)
                    marker.fd.sync()
                }
                finishCleanup()
            }
            else throw IllegalStateException("Wiederherstellung unterbrochen; Sicherungsstände bleiben erhalten", rollback.exceptionOrNull())
            AppPrefs.setRestoreResult(app, "fehler:" + System.currentTimeMillis())
        }
    }
}
