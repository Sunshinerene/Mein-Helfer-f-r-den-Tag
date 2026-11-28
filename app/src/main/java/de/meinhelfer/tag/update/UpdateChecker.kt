package de.meinhelfer.tag.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
)

enum class RoadmapKind { HEADING, OPEN, DONE }

data class RoadmapLine(val text: String, val kind: RoadmapKind)

/** Prueft GitHub-Releases auf neue Versionen, laedt die APK und startet die Installation. */
object UpdateChecker {
    private const val REPO = "Sunshinerene/Mein-Helfer-f-r-den-Tag"
    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val ROADMAP_RAW = "https://raw.githubusercontent.com/$REPO/main/TODO.md"

    const val ISSUES_NEW_URL = "https://github.com/$REPO/issues/new"
    const val PROJECT_URL = "https://github.com/$REPO"

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    fun developmentChannel(context: Context): Boolean =
        context.getSharedPreferences("update_channel", Context.MODE_PRIVATE).getBoolean("dev", false)

    fun setDevelopmentChannel(context: Context, enabled: Boolean) {
        context.getSharedPreferences("update_channel", Context.MODE_PRIVATE).edit().putBoolean("dev", enabled).apply()
    }

    /** true, wenn [remote] (z. B. "0.3.0") neuer ist als [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        if (remote.substringBefore("-dev.").removePrefix("v") == local.substringBefore("-dev.").removePrefix("v") && remote.contains("-dev.") != local.contains("-dev.")) return !remote.contains("-dev.")
        val r = versionParts(remote)
        val l = versionParts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return !remote.contains("-dev.") && local.contains("-dev.") && remote.substringBefore("-dev.") == local.substringBefore("-dev.")
    }

    private fun versionParts(version: String): List<Int> =
        version.trim().removePrefix("v").replace("-dev.", ".").split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

    /** Neuestes Release oder null, wenn es (noch) keines mit APK gibt. Wirft IOException bei Netzwerkfehlern. */
    suspend fun fetchLatest(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val dev = developmentChannel(context)
        val body = httpGet(if (dev) "https://api.github.com/repos/$REPO/releases?per_page=100" else RELEASES_API) ?: return@withContext null
        val json = if (dev) {
            val releases = org.json.JSONArray(body)
            (0 until releases.length()).map { releases.getJSONObject(it) }
                .filter { !it.optBoolean("draft") && it.optBoolean("prerelease") && it.optString("tag_name").matches(Regex("""v[0-9]+\.[0-9]+\.[0-9]+-dev\.[0-9]+""")) && it.optJSONArray("assets")?.let { a -> (0 until a.length()).any { n -> a.getJSONObject(n).optString("name").endsWith(".apk") } } == true }
                .reduceOrNull { a, b -> if (isNewer(b.optString("tag_name"), a.optString("tag_name"))) b else a } ?: return@withContext null
        } else JSONObject(body)
        val tag = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: return@withContext null

        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (tag.isBlank() || apkUrl.isNullOrBlank()) return@withContext null

        UpdateInfo(
            version = tag.removePrefix("v"),
            notes = json.optString("body"),
            apkUrl = apkUrl,
            pageUrl = json.optString("html_url"),
        )
    }

    /** Laedt die APK in den Cache der App. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "MeinHelfer-${info.version}.apk")
            val partial = File(dir, "download.part")

            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "MeinHelfer-App")
            try {
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
                if (partial.length() == 0L || (total > 0 && partial.length() != total)) throw IOException("APK unvollständig")
                check(partial.renameTo(target)) { "APK konnte nicht gespeichert werden" }
            } finally {
                connection.disconnect()
                partial.delete()
            }
            target
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Oeffnet die Android-Einstellung "Installation aus dieser Quelle erlauben" fuer diese App. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Beträge der Pflegekasse aus dem Repo (daten/leistungen.txt), null bei Fehler. */
    suspend fun fetchRates(): String? = withContext(Dispatchers.IO) {
        runCatching { httpGet("https://raw.githubusercontent.com/$REPO/main/daten/leistungen.txt") }.getOrNull()
    }

    /** Lädt eine Datei (z. B. die Übersicht des Ministeriums als PDF) nach [target]; false bei Fehler oder 404. */
    suspend fun download(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MeinHelfer-App")
            try {
            if (c.responseCode !in 200..299) return@runCatching false
            val type = c.contentType.orEmpty()
            if (type.isNotEmpty() && !type.contains("pdf", ignoreCase = true)) return@runCatching false
            c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.length() > 1000
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** Liest die Roadmap live aus TODO.md im Repo. */
    suspend fun fetchRoadmap(): List<RoadmapLine> = withContext(Dispatchers.IO) {
        val body = httpGet(ROADMAP_RAW) ?: return@withContext emptyList()
        body.lines().mapNotNull { raw ->
            val line = raw.trim().replace("**", "").replace("`", "")
            when {
                line.startsWith("## ") -> RoadmapLine(line.removePrefix("## "), RoadmapKind.HEADING)
                line.startsWith("- [ ] ") -> RoadmapLine(line.removePrefix("- [ ] "), RoadmapKind.OPEN)
                line.startsWith("- [x] ") -> RoadmapLine(line.removePrefix("- [x] "), RoadmapKind.DONE)
                else -> null
            }
        }
    }

    /** GET-Anfrage. Gibt null bei 404 zurueck, wirft IOException bei anderen Fehlern. */
    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "MeinHelfer-App")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            return when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }
}
