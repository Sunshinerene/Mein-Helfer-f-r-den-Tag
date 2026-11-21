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

    /** Loescht abgelaufene Nachweise und gibt den aktuell gueltigen zurueck (oder null). */
    suspend fun current(context: Context): ConsentRecord? = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).consentDao()
        dao.deleteExpired(System.currentTimeMillis())
        val last = dao.list().lastOrNull()
        last?.takeIf { it.action == ConsentRecord.ACCEPTED && it.termsVersion == Terms.VERSION }
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
