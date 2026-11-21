package de.meinhelfer.tag.backup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import de.meinhelfer.tag.data.ConsentRecord
import de.meinhelfer.tag.data.DbCrypto
import de.meinhelfer.tag.legal.ConsentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * App zuruecksetzen: loescht alle Daten auf dem Handy (Datenbank, Dokumente, Fotos, PDFs, Einstellungen, KI-Schluessel).
 * Laeuft beim naechsten Start, bevor die Datenbank geoeffnet wird. Nur die Nachweise der Zustimmung bleiben
 * mit ihrer Aufbewahrungsfrist erhalten (siehe Nutzungsbedingungen).
 */
object AppReset {
    private const val MARKER = "zuruecksetzen_ausstehend"
    const val KEEP = "nachweise_behalten.json"

    private fun toJson(r: ConsentRecord) = JSONObject()
        .put("v", r.termsVersion).put("a", r.action).put("t", r.atMillis).put("n", r.name).put("r", r.role)
        .put("app", r.appVersion).put("dev", r.device).put("th", r.textHash)
        .put("del", r.deleteAfterMillis ?: JSONObject.NULL).put("ph", r.prevHash).put("rh", r.recordHash)

    fun fromJson(o: JSONObject) = ConsentRecord(
        termsVersion = o.getInt("v"), action = o.getString("a"), atMillis = o.getLong("t"), name = o.getString("n"),
        role = o.getString("r"), appVersion = o.getString("app"), device = o.getString("dev"), textHash = o.getString("th"),
        deleteAfterMillis = if (o.isNull("del")) null else o.getLong("del"), prevHash = o.getString("ph"), recordHash = o.getString("rh"),
    )

    /** Vormerken: Nachweise sichern, Benachrichtigungen entfernen. Danach die App neu starten (Backup.restartApp). */
    suspend fun schedule(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val records = ConsentStore.history(app)
        File(app.filesDir, KEEP).writeText(JSONArray(records.map { toJson(it) }).toString())
        File(app.filesDir, MARKER).writeText(System.currentTimeMillis().toString())
        runCatching { NotificationManagerCompat.from(app).cancelAll() }
    }

    /** Beim Start: alles loeschen, was vorgemerkt ist. Der Datenbank-Schluessel bleibt (neue leere Datenbank). */
    fun applyPending(context: Context) {
        val app = context.applicationContext
        val marker = File(app.filesDir, MARKER)
        if (!marker.exists()) return
        val db = app.getDatabasePath(DbCrypto.DB_NAME)
        listOf("", "-wal", "-shm", "-journal", ".klartext").forEach { File(db.path + it).delete() }
        app.filesDir.listFiles()?.forEach { f -> if (f.name != KEEP && f.name != MARKER) f.deleteRecursively() }
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        File(app.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (!f.name.startsWith("db_schluessel")) f.delete()
        }
        marker.delete()
    }
}
