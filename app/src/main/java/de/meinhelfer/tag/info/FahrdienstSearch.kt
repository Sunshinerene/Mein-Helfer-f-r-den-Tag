package de.meinhelfer.tag.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Ein Fahrdienst aus der Umkreissuche (OpenStreetMap, unvollstaendig). */
data class FahrdienstHit(
    val name: String,
    val phone: String,
    val website: String,
    val email: String,
    val address: String,
    val openingHours: String,
    val wheelchair: String,
    val distanceKm: Double,
    /** Zusatzhinweis, z. B. "Notaufnahme". */
    val note: String = "",
)

/**
 * Sucht Krankenfahrten, Taxis und Fahrdienste in der Naehe ueber OpenStreetMap
 * (Nominatim fuer den Ort, Overpass fuer die Betriebe). Gesendet wird nur "PLZ Ort",
 * nie Name oder Strasse der Person. Nur nach Tipp und Bestaetigung des Nutzers.
 */
object FahrdienstSearch {
    private const val AGENT = "MeinHelfer-App (Android)"

    fun mapsUrl(zip: String, city: String): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            URLEncoder.encode("Krankenfahrten $zip $city".trim(), "UTF-8")

    fun routeUrl(destination: String): String =
        "https://www.google.com/maps/dir/?api=1&destination=" + URLEncoder.encode(destination, "UTF-8")

    suspend fun search(zip: String, city: String, radiusMeters: Int = 12000): Result<List<FahrdienstHit>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val place = "$zip $city".trim()
                require(place.isNotBlank()) { "Kein Ort" }
                val (lat, lon) = geocode(place)

                val query = "[out:json][timeout:25];" +
                    "nwr[\"name\"~\"Krankenfahrt|Krankentransport|Fahrdienst|Taxi|Behindertenfahr|Patientenfahr\",i]" +
                    "(around:$radiusMeters,$lat,$lon);out center tags 60;"
                parse(overpass(query), lat, lon)
            }
        }

    /** Ort (PLZ + Name) in Koordinaten umrechnen (Nominatim). */
    internal fun geocode(place: String): Pair<Double, Double> {
        val geo = get(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=de&q=" +
                URLEncoder.encode(place, "UTF-8"),
        )
        val arr = org.json.JSONArray(geo)
        if (arr.length() == 0) throw IllegalStateException("Ort nicht gefunden")
        val first = arr.getJSONObject(0)
        return first.getString("lat").toDouble() to first.getString("lon").toDouble()
    }

    /** Overpass-Abfrage ausfuehren. */
    internal fun overpass(query: String): String =
        post("https://overpass-api.de/api/interpreter", "data=" + URLEncoder.encode(query, "UTF-8"))

    internal fun parse(json: String, lat: Double, lon: Double, phoneFirst: Boolean = true): List<FahrdienstHit> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val hits = mutableListOf<FahrdienstHit>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isEmpty()) continue
            val center = e.optJSONObject("center")
            val eLat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
            val eLon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
            val distance = if (eLat.isNaN() || eLon.isNaN()) 999.0 else haversine(lat, lon, eLat, eLon)
            fun tag(vararg keys: String): String =
                keys.firstNotNullOfOrNull { k -> tags.optString(k).trim().takeIf { it.isNotEmpty() } } ?: ""
            val street = tag("addr:street")
            val number = tag("addr:housenumber")
            val address = listOf(
                listOf(street, number).filter { it.isNotEmpty() }.joinToString(" "),
                listOf(tag("addr:postcode"), tag("addr:city")).filter { it.isNotEmpty() }.joinToString(" "),
            ).filter { it.isNotEmpty() }.joinToString(", ")
            hits.add(
                FahrdienstHit(
                    name = name,
                    phone = tag("phone", "contact:phone", "contact:mobile", "mobile").substringBefore(";").trim(),
                    website = tag("website", "contact:website", "url"),
                    email = tag("email", "contact:email").substringBefore(";").trim(),
                    address = address,
                    openingHours = tag("opening_hours"),
                    wheelchair = tag("wheelchair"),
                    distanceKm = distance,
                    note = if (tags.optString("emergency") == "yes") "Notaufnahme" else "",
                ),
            )
        }
        // Doppelte (gleicher Name, gleiche Telefonnummer) entfernen; mit Telefon zuerst, dann nach Entfernung
        return hits
            .distinctBy { it.name.lowercase() + "|" + it.phone }
            .sortedWith(
                if (phoneFirst) compareBy<FahrdienstHit> { it.phone.isEmpty() }.thenBy { it.distanceKm }
                else compareBy { it.distanceKm },
            )
            .take(25)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.setRequestProperty("User-Agent", AGENT)
        c.setRequestProperty("Accept-Language", "de")
        val code = c.responseCode
        if (code !in 200..299) throw IllegalStateException("Fehler $code")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun post(url: String, form: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 40000
        c.setRequestProperty("User-Agent", AGENT)
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(form.toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) throw IllegalStateException("Fehler $code")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
