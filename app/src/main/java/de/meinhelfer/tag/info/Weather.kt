package de.meinhelfer.tag.info

import android.content.Context
import de.meinhelfer.tag.summary.WeatherHint
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId

/**
 * Wetter für Termine über Open-Meteo (frei, ohne Konto). Gesendet werden nur der eingestellte Ort
 * bzw. seine Koordinaten, nie Termine oder Namen. Höchstens alle 3 Stunden, Ergebnis wird auf dem Handy gemerkt.
 */
object Weather {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "MeinHelfer-App")
        if (c.responseCode !in 200..299) throw IllegalStateException("Fehler ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Ort suchen: Name und Koordinaten. Erst Open-Meteo (Name oder PLZ), sonst die Ortssuche des Handys (Android-Geocoder).
     * Fehler mit Grund, damit man sieht, ob es an der Verbindung oder am Ort liegt.
     */
    suspend fun find(context: Context, place: String): Result<Triple<String, Double, Double>> = withContext(Dispatchers.IO) {
        val q = place.trim()
        val first = runCatching {
            // Leerzeichen als %20 (manche Server lesen „+“ nicht als Leerzeichen)
            val enc = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&language=de&format=json&name=$enc"))
            val arr = json.optJSONArray("results") ?: throw NoSuchElementException("nicht gefunden")
            val all = (0 until arr.length()).map { arr.getJSONObject(it) }
            val r = all.firstOrNull { it.optString("country_code").equals("DE", true) } ?: all.first()
            val label = listOf(r.optString("name"), r.optString("admin1")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }
        if (first.isSuccess) return@withContext first
        val second = runCatching {
            if (!android.location.Geocoder.isPresent()) throw IllegalStateException("keine Ortssuche auf dem Handy")
            @Suppress("DEPRECATION")
            val list = android.location.Geocoder(context, java.util.Locale.GERMANY).getFromLocationName(if (q.all { it.isDigit() }) "$q, Deutschland" else q, 5)
            val a = list?.firstOrNull { it.countryCode.equals("DE", true) } ?: list?.firstOrNull() ?: throw NoSuchElementException("nicht gefunden")
            val label = listOfNotNull(a.locality ?: a.subAdminArea ?: a.featureName, a.adminArea).filter { it.isNotBlank() }.distinct().joinToString(", ")
            Triple(label.ifBlank { q }, a.latitude, a.longitude)
        }
        if (second.isSuccess) second
        else Result.failure(IllegalStateException(
            "Open-Meteo: " + (first.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "?") +
                " · Handy: " + (second.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "?"),
        ))
    }

    /** Vorhersage holen, wenn eingeschaltet, ein Ort gesetzt ist und die letzte älter als 3 Stunden ist. */
    suspend fun refresh(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!AppPrefs.weatherOn(context)) return@withContext
        val (lat, lon) = AppPrefs.weatherCoords(context) ?: return@withContext
        if (!force && System.currentTimeMillis() - AppPrefs.weatherFetched(context) < 3L * 3600 * 1000) return@withContext
        runCatching {
            val text = get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&hourly=temperature_2m,precipitation_probability,weather_code&timezone=auto&forecast_days=3",
            )
            AppPrefs.setWeatherData(context, text)
        }
    }

    /** Hinweis für einen Zeitpunkt aus der gemerkten Vorhersage (leer = nichts Besonderes oder keine Daten). */
    fun hintAt(context: Context, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (!AppPrefs.weatherOn(context)) return ""
        val data = AppPrefs.weatherData(context).ifBlank { return "" }
        return runCatching {
            val h = JSONObject(data).getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val t = Instant.ofEpochMilli(millis).atZone(zone)
            val key = "%04d-%02d-%02dT%02d:00".format(t.year, t.monthValue, t.dayOfMonth, t.hour)
            val i = (0 until times.length()).firstOrNull { times.getString(it) == key } ?: return ""
            WeatherHint.text(
                h.getJSONArray("temperature_2m").optDouble(i, 15.0),
                h.getJSONArray("precipitation_probability").optInt(i, 0),
                h.getJSONArray("weather_code").optInt(i, 0),
                "%d:%02d".format(t.hour, t.minute),
            )
        }.getOrDefault("")
    }
}
