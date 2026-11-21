package de.meinhelfer.tag.info

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** Eine Meldung: nur Ueberschrift, Quelle, Datum und Link. Gelesen wird auf der Seite der Quelle. */
data class NewsItem(val title: String, val source: String, val link: String, val published: Long?)

/**
 * Aktuelle Meldungen zu einem Suchwort ueber den oeffentlichen RSS-Feed von Google News.
 * Gesendet wird nur das Suchwort, nie Name oder Daten der Person. Nur nach Tipp des Nutzers.
 */
object NewsFeed {
    private val DATE = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)

    fun browserUrl(query: String): String =
        "https://news.google.com/search?q=" + URLEncoder.encode("$query when:180d", "UTF-8") + "&hl=de&gl=DE&ceid=DE:de"

    suspend fun search(query: String): Result<List<NewsItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://news.google.com/rss/search?q=" + URLEncoder.encode("$query when:180d", "UTF-8") +
                "&hl=de&gl=DE&ceid=DE:de"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MeinHelfer-App")
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Fehler $code")
            connection.inputStream.use { parse(it) }
        }
    }

    private fun parse(stream: java.io.InputStream): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""
        var link = ""
        var date: Long? = null
        var source = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""
                        link = ""
                        date = null
                        source = ""
                    }
                    "title" -> if (inItem) title = parser.nextText().trim()
                    "link" -> if (inItem) link = parser.nextText().trim()
                    "pubDate" -> if (inItem) date = runCatching { DATE.parse(parser.nextText().trim())?.time }.getOrNull()
                    "source" -> if (inItem) source = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    val clean = if (source.isNotBlank() && title.endsWith(" - $source")) title.removeSuffix(" - $source") else title
                    if (clean.isNotBlank() && link.startsWith("http")) items.add(NewsItem(clean, source, link, date))
                }
            }
            event = parser.next()
        }
        return items.distinctBy { it.title }.sortedByDescending { it.published ?: 0L }.take(30)
    }
}
