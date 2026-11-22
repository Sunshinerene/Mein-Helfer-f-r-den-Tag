package de.meinhelfer.tag.info

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ein frei nutzbares Bild von Wikimedia Commons mit Urheber und Lizenz. */
data class FreeImage(
    val title: String,
    val thumbUrl: String,
    val bigUrl: String,
    val pageUrl: String,
    val license: String,
    val artist: String,
) {
    /** Kurzer Herkunftsvermerk fuer die App, z. B. "Max Muster, CC BY-SA 4.0, Wikimedia Commons" */
    val credit: String get() = listOf(artist, license, "Wikimedia Commons").filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Sucht freie Bilder auf Wikimedia Commons. Gesendet wird nur der Suchbegriff (Medikamentenname).
 * Zeichnungen (SVG, meist Strukturformeln) werden ausgelassen, damit vor allem Fotos erscheinen.
 */
object FreeImages {
    private const val UA = "MeinHelfer-App (Android; Sunshine@R)"

    /** Name ohne Hersteller-Zusaetze: "ASS 100 - 1 A Pharma®" -> "ASS 100" */
    fun searchTerm(name: String): String =
        name.replace("®", "").replace("™", "").split(" - ", " – ", ",").first()
            .replace(Regex("\\b(1 ?A ?Pharma|ratiopharm|Hexal|STADA|AL|Heumann|Aristo|Basics|Zentiva|TAD|AbZ|Mylan|Teva|Sandoz|beta)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ").trim()

    suspend fun search(term: String, limit: Int = 16): List<FreeImage> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("$term filetype:bitmap", "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrsearch=$q&gsrnamespace=6&gsrlimit=$limit&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=360"
        val text = get(url) ?: return@withContext emptyList()
        val pages = JSONObject(text).optJSONObject("query")?.optJSONObject("pages") ?: return@withContext emptyList()
        val out = mutableListOf<Pair<Int, FreeImage>>()
        for (key in pages.keys()) {
            val page = pages.optJSONObject(key) ?: continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = info.optString("mime")
            if (mime != "image/jpeg" && mime != "image/png" && mime != "image/webp") continue
            val thumb = info.optString("thumburl")
            if (thumb.isBlank()) continue
            val meta = info.optJSONObject("extmetadata")
            fun meta(k: String) = meta?.optJSONObject(k)?.optString("value").orEmpty()
            val artist = meta("Artist").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim().take(80)
            out += page.optInt("index", 999) to FreeImage(
                title = page.optString("title").removePrefix("File:").removePrefix("Datei:"),
                thumbUrl = thumb,
                bigUrl = thumb.replace("/360px-", "/900px-"),
                pageUrl = info.optString("descriptionurl"),
                license = meta("LicenseShortName"),
                artist = artist,
            )
        }
        out.sortedBy { it.first }.map { it.second }
    }

    /** Laedt ein Vorschaubild. */
    suspend fun thumbnail(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url)
            c.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    /** Laedt das Bild in [target]; bei Fehler mit der grossen Variante auf die Vorschau zurueckfallen. */
    suspend fun download(image: FreeImage, target: File): Boolean = withContext(Dispatchers.IO) {
        for (u in listOf(image.bigUrl, image.thumbUrl)) {
            val ok = runCatching {
                val c = open(u)
                if (c.responseCode !in 200..299) return@runCatching false
                c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                target.length() > 0
            }.getOrDefault(false)
            if (ok) return@withContext true
        }
        target.delete()
        false
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        setRequestProperty("User-Agent", UA)
    }

    private fun get(url: String): String? = runCatching {
        val c = open(url)
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
