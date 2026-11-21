package de.meinhelfer.tag.legal

import android.content.Context
import android.os.Build
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.ConsentRecord
import de.meinhelfer.tag.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Zustimmung zu den Nutzungsbedingungen: speichern, widerrufen, pruefen und nach Frist loeschen.
 * Alles bleibt in der verschluesselten Datenbank auf dem Handy.
 */
object ConsentStore {
    private val SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss 'Uhr'", Locale.GERMANY)

    /** Ende des dritten Kalenderjahres nach [millis] (regelmaessige Verjaehrung, §§ 195, 199 BGB). */
    fun retentionEnd(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val year = Instant.ofEpochMilli(millis).atZone(zone).year + 3
        return Year.of(year).atMonth(12).atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    }

    private fun hashOf(r: ConsentRecord): String = Terms.sha256(
        listOf(r.prevHash, r.termsVersion, r.action, r.atMillis, r.name, r.role, r.appVersion, r.device, r.textHash).joinToString("|"),
    )

    private fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})".trim()

    /** Warum (noch) zugestimmt werden muss. */
    enum class Status { OK, FIRST, NEW_VERSION, REVOKED, CAREGIVER_CHANGED, RESET }

    private fun norm(s: String) = s.trim().lowercase(Locale.GERMANY).replace(Regex("\\s+"), " ")

    /** Gilt die Zustimmung fuer die eingetragene pflegende Person (Name und Rolle aus "Meine Daten")? */
    fun matchesCaregiver(r: ConsentRecord, name: String, role: String): Boolean =
        (name.isBlank() || norm(name) == norm(r.name)) && (role.isBlank() || role == r.role)

    /** Loescht abgelaufene Nachweise und gibt den aktuell gueltigen zurueck (oder null). */
    suspend fun current(context: Context): ConsentRecord? = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).consentDao()
        dao.deleteExpired(System.currentTimeMillis())
        val last = dao.list().lastOrNull()
        last?.takeIf { it.action == ConsentRecord.ACCEPTED && it.termsVersion == Terms.VERSION }
    }

    const val RESET = "ZURÜCKGESETZT"

    /** Nach dem Zuruecksetzen: aufbewahrte Nachweise zurueckholen und das Zuruecksetzen selbst festhalten. */
    private suspend fun importKept(context: Context) {
        val file = java.io.File(context.applicationContext.filesDir, de.meinhelfer.tag.backup.AppReset.KEEP)
        if (!file.exists()) return
        val dao = AppDatabase.get(context).consentDao()
        val now = System.currentTimeMillis()
        runCatching {
            val arr = org.json.JSONArray(file.readText())
            val kept = (0 until arr.length()).map { de.meinhelfer.tag.backup.AppReset.fromJson(arr.getJSONObject(it)) }
            kept.forEach { dao.insert(it.copy(id = 0, deleteAfterMillis = it.deleteAfterMillis ?: retentionEnd(now))) }
            val last = kept.lastOrNull()
            val draft = ConsentRecord(
                termsVersion = Terms.VERSION,
                action = RESET,
                atMillis = now,
                name = last?.name.orEmpty(),
                role = last?.role.orEmpty(),
                appVersion = runCatching { UpdateChecker.installedVersion(context) }.getOrDefault("?"),
                device = device(),
                textHash = Terms.textHash(),
                deleteAfterMillis = retentionEnd(now),
                prevHash = last?.recordHash.orEmpty(),
            )
            dao.insert(draft.copy(recordHash = hashOf(draft)))
        }
        file.delete()
    }

    /** Prueft Fassung, Widerruf und ob die pflegende Person noch dieselbe ist. */
    suspend fun status(context: Context): Status = withContext(Dispatchers.IO) {
        importKept(context)
        val dao = AppDatabase.get(context).consentDao()
        dao.deleteExpired(System.currentTimeMillis())
        val last = dao.list().lastOrNull() ?: return@withContext Status.FIRST
        val cg = de.meinhelfer.tag.update.AppPrefs.caregiver(context)
        when {
            last.action == RESET -> Status.RESET
            last.action == ConsentRecord.REVOKED -> Status.REVOKED
            last.termsVersion != Terms.VERSION -> Status.NEW_VERSION
            !matchesCaregiver(last, cg.name, cg.role) -> Status.CAREGIVER_CHANGED
            else -> Status.OK
        }
    }

    suspend fun history(context: Context): List<ConsentRecord> = withContext(Dispatchers.IO) {
        AppDatabase.get(context).consentDao().list()
    }

    /** Neue Zustimmung zur aktuellen Fassung. Fruehere Zustimmungen bekommen eine Loeschfrist. */
    suspend fun accept(context: Context, name: String, role: String): ConsentRecord = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).consentDao()
        val now = System.currentTimeMillis()
        val all = dao.list()
        all.filter { it.deleteAfterMillis == null }.forEach { dao.update(it.copy(deleteAfterMillis = retentionEnd(now))) }
        val draft = ConsentRecord(
            termsVersion = Terms.VERSION,
            action = ConsentRecord.ACCEPTED,
            atMillis = now,
            name = name.trim(),
            role = role,
            appVersion = runCatching { UpdateChecker.installedVersion(context) }.getOrDefault("?"),
            device = device(),
            textHash = Terms.textHash(),
            prevHash = all.lastOrNull()?.recordHash.orEmpty(),
        )
        val record = draft.copy(recordHash = hashOf(draft))
        val id = dao.insert(record)
        record.copy(id = id)
    }

    /** Widerruf: wird ebenfalls nachgewiesen; alle Nachweise bekommen die Loeschfrist. */
    suspend fun revoke(context: Context, name: String, role: String) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).consentDao()
        val now = System.currentTimeMillis()
        val all = dao.list()
        all.filter { it.deleteAfterMillis == null }.forEach { dao.update(it.copy(deleteAfterMillis = retentionEnd(now))) }
        val draft = ConsentRecord(
            termsVersion = Terms.VERSION,
            action = ConsentRecord.REVOKED,
            atMillis = now,
            name = name.trim(),
            role = role,
            appVersion = runCatching { UpdateChecker.installedVersion(context) }.getOrDefault("?"),
            device = device(),
            textHash = Terms.textHash(),
            deleteAfterMillis = retentionEnd(now),
            prevHash = all.lastOrNull()?.recordHash.orEmpty(),
        )
        dao.insert(draft.copy(recordHash = hashOf(draft)))
    }

    /** true, wenn kein Eintrag nachtraeglich veraendert wurde (Pruefsummen-Kette stimmt). */
    fun chainIntact(list: List<ConsentRecord>): Boolean =
        list.zipWithNext().all { (a, b) -> b.prevHash == a.recordHash } && list.all { hashOf(it) == it.recordHash }

    private fun time(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(SHOWN)

    /** Lesbarer Nachweis zum Teilen oder Ausdrucken. */
    fun proofText(list: List<ConsentRecord>, roleLabel: (String) -> String): String = buildString {
        appendLine("Nachweis der Zustimmung – Mein Helfer für den Tag")
        appendLine("Erstellt am ${time(System.currentTimeMillis())}")
        appendLine("Prüfsummen-Kette: " + if (chainIntact(list)) "unverändert" else "AUFFÄLLIG (Einträge passen nicht zusammen)")
        list.forEach { r ->
            appendLine()
            appendLine("${r.action} – Fassung ${r.termsVersion}")
            appendLine("Zeitpunkt: ${time(r.atMillis)}")
            appendLine("Name: ${r.name.ifBlank { "–" }}")
            appendLine("Rolle: ${roleLabel(r.role)}")
            appendLine("App-Version: ${r.appVersion}")
            appendLine("Gerät: ${r.device}")
            appendLine("Prüfsumme des Textes (SHA-256): ${r.textHash}")
            appendLine("Prüfsumme des Eintrags: ${r.recordHash}")
            appendLine("Aufbewahrung: " + (r.deleteAfterMillis?.let { "bis ${time(it)}, danach automatische Löschung" } ?: "solange die App genutzt wird"))
        }
        appendLine()
        appendLine("Wortlaut der aktuellen Fassung:")
        appendLine()
        append(Terms.fullText())
    }
}
