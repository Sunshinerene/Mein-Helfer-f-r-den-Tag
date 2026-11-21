package de.meinhelfer.tag.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Beschreibung zu einem Medikament bzw. Wirkstoff. Nur ein Anhaltspunkt. */
data class MedInfo(val text: String, val sourceUrl: String, val title: String)

/**
 * Holt eine kurze Beschreibung aus der deutschen Wikipedia.
 * Gesendet wird ausschliesslich der Medikamentenname (ohne Staerke), nie Personen-, Termin- oder Vorratsdaten.
 */
object MedicationInfo {
    const val DISCLAIMER =
        "Nur ein Anhaltspunkt aus Wikipedia, keine ärztliche Empfehlung. Sie ersetzt weder Arzt, Apotheke noch die Packungsbeilage."

    private val MEDICAL_WORDS = Regex(
        "Arzneimittel|Arzneistoff|Wirkstoff|Medikament|Pharmak|Präparat|Heilmittel|Antibiotik|Schmerzmittel|Blutdruck|Therapie|Hormon|Vitamin",
        RegexOption.IGNORE_CASE,
    )
    private val STRIP = Regex("""\d+(?:[.,]\d+)?\s?(?:mg|µg|mcg|g|ml|IE|%)?|\b(?:Tabletten|Filmtabletten|Tabl|Kapseln|Hartkapseln|Retard|Tropfen|Saft|Salbe|Creme|Dragees|akut|forte|protect|mite|comp|AL|ratiopharm|HEXAL|STADA|TAD|Heumann|CT)\b\.?""", RegexOption.IGNORE_CASE)

    /** Entfernt Staerke und Darreichungsform, damit "Ibuprofen 400 mg Tabletten" zu "Ibuprofen" wird. */
    fun cleanName(name: String): String =
        name.replace(STRIP, " ").replace(Regex("""[^\p{L}\s\-]"""), " ").replace(Regex("""\s+"""), " ").trim()

    suspend fun load(name: String): MedInfo? = withContext(Dispatchers.IO) {
        val cleaned = cleanName(name)
        if (cleaned.length < 3) return@withContext null
        val tries = listOf(cleaned, cleaned.substringBefore(' ')).distinct().filter { it.length >= 3 }
        for (query in tries) {
            val exact = fetch("titles=${enc(query)}&redirects=1")
            val found = exact ?: fetch("generator=search&gsrsearch=${enc(query)}&gsrlimit=1")
            if (found != null) return@withContext found
        }
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(selector: String): MedInfo? {
        val url = "https://de.wikipedia.org/w/api.php?action=query&format=json&prop=extracts%7Cinfo&exintro=1&explaintext=1" +
            "&exsentences=4&inprop=url&$selector"
        val body = httpGet(url) ?: return null
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val keys = pages.keys().asSequence().toList()
        for (key in keys) {
            val page = pages.optJSONObject(key) ?: continue
            if (page.has("missing") || page.has("invalid")) continue
            val extract = page.optString("extract").trim()
            if (extract.length < 40) continue
            // Nur Seiten, die wirklich nach Arzneimittel klingen (sonst Verwechslungen, z. B. Begriffsklaerungen)
            if (!MEDICAL_WORDS.containsMatchIn(extract)) continue
            return MedInfo(
                text = shorten(extract),
                sourceUrl = page.optString("fullurl"),
                title = page.optString("title"),
            )
        }
        return null
    }

    private fun shorten(text: String): String {
        val clean = text.replace(Regex("""\s*\n+\s*"""), " ").trim()
        if (clean.length <= 700) return clean
        val cut = clean.substring(0, 700)
        val end = cut.lastIndexOf(". ")
        return if (end > 200) cut.substring(0, end + 1) else cut.trimEnd() + " …"
    }

    private fun httpGet(url: String): String? =
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "MeinHelfer-App/0.11 (Android; Sunshine@R)")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) {
            null
        }
}
