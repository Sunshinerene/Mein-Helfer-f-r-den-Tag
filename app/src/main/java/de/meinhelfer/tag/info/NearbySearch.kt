package de.meinhelfer.tag.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Art der Einrichtung fuer die Umkreissuche. [contactType] wird beim Speichern als Art des Kontakts gesetzt. */
enum class NearbyKind(val label: String, val contactType: String, val radiusMeters: Int, val phoneFirst: Boolean) {
    Apotheke("Apotheke", "Apotheke", 6000, false),
    Pflegedienst("Pflegedienst", "Pflegedienst", 15000, true),
    Sanitaetshaus("Sanitätshaus", "Sanitätshaus", 15000, true),
    Notaufnahme("Notaufnahme / Krankenhaus", "Krankenhaus", 25000, false),
    ;

    /** Overpass-Filter (ohne Umkreis) fuer diese Art. */
    internal fun filters(): List<String> = when (this) {
        Apotheke -> listOf("[\"amenity\"=\"pharmacy\"]")
        Pflegedienst -> listOf(
            "[\"social_facility\"=\"ambulatory_care\"]",
            "[\"name\"~\"Pflegedienst|Sozialstation|Ambulante Pflege|Häusliche Pflege\",i]",
        )
        Sanitaetshaus -> listOf(
            "[\"shop\"=\"medical_supply\"]",
            "[\"name\"~\"Sanitätshaus|Sanitaetshaus\",i]",
        )
        Notaufnahme -> listOf("[\"amenity\"=\"hospital\"]")
    }
}

/**
 * Umkreissuche ueber OpenStreetMap (Nominatim + Overpass). Gesendet wird nur "PLZ Ort",
 * nie Name oder Strasse der Person. Die Daten sind unvollstaendig, deshalb nur ein Anhaltspunkt.
 */
object NearbySearch {
    fun mapsUrl(kind: NearbyKind, zip: String, city: String): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            URLEncoder.encode("${kind.label} $zip $city".trim(), "UTF-8")

    suspend fun search(kind: NearbyKind, zip: String, city: String): Result<List<FahrdienstHit>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val place = "$zip $city".trim()
                require(place.isNotBlank()) { "Kein Ort" }
                val (lat, lon) = FahrdienstSearch.geocode(place)
                val around = "(around:${kind.radiusMeters},$lat,$lon)"
                val query = "[out:json][timeout:25];(" +
                    kind.filters().joinToString("") { "nwr$it$around;" } +
                    ");out center tags 80;"
                FahrdienstSearch.parse(FahrdienstSearch.overpass(query), lat, lon, phoneFirst = kind.phoneFirst)
            }
        }
}
