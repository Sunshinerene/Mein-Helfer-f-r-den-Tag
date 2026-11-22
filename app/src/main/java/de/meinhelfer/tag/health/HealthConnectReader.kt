package de.meinhelfer.tag.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.meinhelfer.tag.summary.HealthValue
import de.meinhelfer.tag.summary.HealthValues
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Liest Messwerte aus Health Connect (Android-Gesundheitsdaten auf dem Handy), z. B. von Uhr, Fitnessband, Waage
 * oder Blutdruckgerät. Nur lesen, nur mit Erlaubnis, nichts verlässt das Handy. Übernommen wird erst nach Bestätigung.
 */
object HealthConnectReader {
    private val TYPES: List<KClass<out Record>> = listOf(
        BloodPressureRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        WeightRecord::class,
        BloodGlucoseRecord::class,
        BodyTemperatureRecord::class,
        OxygenSaturationRecord::class,
        HydrationRecord::class,
    )

    /** Leserechte für Blutdruck, Puls, Ruhepuls, Gewicht, Blutzucker, Temperatur, Sauerstoff und Trinkmenge. */
    val PERMISSIONS: Set<String> = TYPES.map { HealthPermission.getReadPermission(it) }.toSet()

    /** Health Connect ist da (ab Android 14 eingebaut, davor als App). */
    fun available(context: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    /** Health Connect muss erst installiert oder aktualisiert werden (Android 13 und älter). */
    fun needsUpdate(context: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED }.getOrDefault(false)

    suspend fun granted(context: Context): Set<String> = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }.getOrDefault(emptySet())

    /** Einstellungen von Health Connect öffnen (Rechte und Datenquellen verwalten). */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** Anzeigename der liefernden App (falls sichtbar), sonst bekannter Name oder Paketname. */
    fun label(context: Context, pkg: String): String {
        val app = runCatching {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrNull()
        return HealthValues.label(pkg, app)
    }

    /**
     * Werte im Zeitraum lesen. Herzfrequenz-Reihen von Uhren (viele Messungen am Stück) werden übergangen,
     * Einzelmessungen (z. B. vom Blutdruckgerät) und der Ruhepuls zählen.
     */
    suspend fun read(context: Context, from: Instant, to: Instant, mmol: Boolean): List<HealthValue> {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val range = TimeRangeFilter.between(from, to)
        val out = mutableListOf<HealthValue>()

        suspend fun <T : Record> all(type: KClass<T>): List<T> {
            if (HealthPermission.getReadPermission(type) !in granted) return emptyList()
            val list = mutableListOf<T>()
            var token: String? = null
            do {
                val response = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
                list += response.records
                token = response.pageToken
            } while (token != null && list.size < 5000)
            return list
        }

        all(BloodPressureRecord::class).forEach { r ->
            out += HealthValue(
                "blutdruck", r.systolic.inMillimetersOfMercury, r.diastolic.inMillimetersOfMercury, "mmHg",
                r.time.toEpochMilli(), r.metadata.dataOrigin.packageName,
            )
        }
        all(HeartRateRecord::class).filter { it.samples.size <= 3 }.forEach { r ->
            r.samples.forEach { s ->
                out += HealthValue("puls", s.beatsPerMinute.toDouble(), null, "/min", s.time.toEpochMilli(), r.metadata.dataOrigin.packageName)
            }
        }
        all(RestingHeartRateRecord::class).forEach { r ->
            out += HealthValue("puls", r.beatsPerMinute.toDouble(), null, "/min", r.time.toEpochMilli(), r.metadata.dataOrigin.packageName, note = "Ruhepuls")
        }
        all(WeightRecord::class).forEach { r ->
            out += HealthValue("gewicht", r.weight.inKilograms, null, "kg", r.time.toEpochMilli(), r.metadata.dataOrigin.packageName)
        }
        all(BloodGlucoseRecord::class).forEach { r ->
            val v = if (mmol) r.level.inMillimolesPerLiter else r.level.inMilligramsPerDeciliter
            out += HealthValue("blutzucker", v, null, if (mmol) "mmol/l" else "mg/dl", r.time.toEpochMilli(), r.metadata.dataOrigin.packageName)
        }
        all(BodyTemperatureRecord::class).forEach { r ->
            out += HealthValue("temperatur", r.temperature.inCelsius, null, "°C", r.time.toEpochMilli(), r.metadata.dataOrigin.packageName)
        }
        all(OxygenSaturationRecord::class).forEach { r ->
            out += HealthValue("sauerstoff", r.percentage.value, null, "%", r.time.toEpochMilli(), r.metadata.dataOrigin.packageName)
        }
        all(HydrationRecord::class).forEach { r ->
            out += HealthValue("trinken", r.volume.inMilliliters, null, "ml", r.startTime.toEpochMilli(), r.metadata.dataOrigin.packageName)
        }
        return out.filter { HealthValues.plausible(it) }.sortedByDescending { it.atMillis }
    }
}
