package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.VitalEntry
import kotlin.math.abs

/** Ein Messwert aus Health Connect (von Uhr, Band, Waage, Blutdruckgerät …), noch nicht übernommen. */
data class HealthValue(
    /** Schlüssel aus [VitalKind] */
    val kind: String,
    val value: Double,
    val value2: Double? = null,
    val unit: String,
    val atMillis: Long,
    /** Paketname der App, die den Wert geliefert hat */
    val source: String,
    /** Zusatz, z. B. „Ruhepuls“ */
    val note: String = "",
)

/** Auswahl und Abgleich von Werten aus Health Connect (ohne Android). */
object HealthValues {
    /** Bekannte Apps, die Werte an Health Connect geben (Anzeige-Namen). */
    val KNOWN_SOURCES = mapOf(
        "com.google.android.apps.healthdata" to "Health Connect (von Hand)",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.fitbit.FitbitMobile" to "Fitbit / Pixel Watch",
        "com.google.android.apps.adm" to "Google",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.samsung.android.app.health" to "Samsung Health",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.withings.wiscale2" to "Withings",
        "jp.co.omron.healthcare.omron_connect" to "OMRON connect",
        "com.omronhealthcare.omronconnect" to "OMRON connect",
        "com.beurer.connect.healthmanagerpro" to "Beurer HealthManager Pro",
        "com.huawei.health" to "Huawei Gesundheit",
        "com.xiaomi.wearable" to "Mi Fitness",
        "com.mc.miband1" to "Notify for Mi Band",
        "com.ouraring.oura" to "Oura",
        "com.polar.polarflow" to "Polar Flow",
        "com.suunto.suuntoapp" to "Suunto",
        "com.mysugr.android.companion" to "mySugr",
        "com.freestylelibre3.app.de" to "FreeStyle Libre 3",
        "com.freestylelibre.app.de" to "FreeStyle Libre",
    )

    fun label(source: String, appLabel: String? = null): String =
        appLabel?.takeIf { it.isNotBlank() } ?: KNOWN_SOURCES[source] ?: source.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    /**
     * Nur Werte, die noch nicht in der Liste stehen: gleiche Art, gleicher Wert und höchstens 2 Minuten Abstand gilt als schon da
     * (z. B. schon einmal übernommen oder von Hand eingetragen).
     */
    fun newOnes(values: List<HealthValue>, existing: List<VitalEntry>): List<HealthValue> =
        values.filter { v ->
            existing.none { e ->
                e.kind == v.kind && abs(e.atMillis - v.atMillis) <= 120_000 && abs(e.value - v.value) < 0.05 &&
                    (v.value2 == null || e.value2 == null || abs(v.value2 - e.value2) < 0.05)
            }
        }.distinctBy { Triple(it.kind, it.atMillis / 60_000, it.value) }

    /** Werte außerhalb der Plausibilitätsgrenzen (Tippfehler, Messfehler) werden nicht vorgeschlagen. */
    fun plausible(v: HealthValue): Boolean {
        val k = VitalKind.of(v.kind) ?: return false
        if (k == VitalKind.Blutzucker && v.unit == "mmol/l") return v.value in VitalKind.MMOL_RANGE
        return v.value in k.plausible && (v.value2 == null || k.second == null || v.value2 in k.second)
    }
}
