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

    /** true, wenn [remote] (z. B. "0.3.0") neuer ist als [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = versionParts(remote)
        val l = versionParts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim().removePrefix("v").split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

    /** Neuestes Release oder null, wenn es (noch) keines mit APK gibt. Wirft IOException bei Netzwerkfehlern. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = httpGet(RELEASES_API) ?: return@withContext null
        val json = JSONObject(body)
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

            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "MeinHelfer-App")
            try {
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
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
            } finally {
                connection.disconnect()
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
