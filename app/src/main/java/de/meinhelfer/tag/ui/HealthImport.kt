package de.meinhelfer.tag.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.health.HEALTH_PRIVACY_TEXT
import de.meinhelfer.tag.health.HealthConnectReader
import de.meinhelfer.tag.summary.HealthValue
import de.meinhelfer.tag.summary.HealthValues
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.Vitals
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

/** Status von Health Connect: verfügbar, Rechte, Knopf zum Erlauben. Gemeinsam für „Trinken und Werte“ und Einstellungen → Beta. */
private class HealthState {
    var granted by mutableStateOf<Set<String>?>(null)
}

private fun kindsText(granted: Set<String>): String {
    val names = listOf(
        "BLOOD_PRESSURE" to "Blutdruck", "HEART_RATE" to "Puls", "WEIGHT" to "Gewicht", "BLOOD_GLUCOSE" to "Blutzucker",
        "BODY_TEMPERATURE" to "Temperatur", "OXYGEN_SATURATION" to "Sauerstoff", "HYDRATION" to "Trinkmenge",
        "SLEEP" to "Schlaf (für dich)", "STEPS" to "Schritte (für dich)",
    )
    return names.filter { (key, _) -> granted.any { it.endsWith("READ_$key") } }.joinToString(", ") { it.second }
}

/** In „Trinken und Werte“: Werte aus Health Connect holen, prüfen und übernehmen (Beta). */
@Composable
fun HealthImportCard(vm: MainViewModel, person: Person?, vitals: List<VitalEntry>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { HealthConnectReader.available(context) }
    val state = remember { HealthState() }
    var days by remember { mutableIntStateOf(7) }
    var loading by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<HealthValue>?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var why by remember { mutableStateOf(false) }

    LaunchedEffect(available) { if (available) state.granted = HealthConnectReader.granted(context) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { state.granted = HealthConnectReader.granted(context) }
    }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⌚ Von Uhr oder Messgerät (Beta)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Werte von Uhr, Fitnessband, Waage oder Blutdruckgerät übernehmen, statt sie abzutippen. Die App liest sie aus Health Connect auf diesem Handy, " +
                    "du siehst vorher jeden Wert mit Quelle und wählst aus.",
                style = MaterialTheme.typography.bodySmall,
            )
            val granted = state.granted
            when {
                !available -> {
                    Text(
                        if (HealthConnectReader.needsUpdate(context)) "Health Connect muss erst installiert oder aktualisiert werden."
                        else "Health Connect ist auf diesem Handy nicht verfügbar (eingebaut ab Android 14).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (HealthConnectReader.needsUpdate(context)) {
                        OutlinedButton(shape = buttonShape(), onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text("Health Connect im Play Store") }
                    }
                }
                granted == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                granted.isEmpty() -> {
                    Text("Noch kein Zugriff erlaubt.", style = MaterialTheme.typography.bodyMedium)
                    Button(shape = buttonShape(), onClick = { launcher.launch(HealthConnectReader.PERMISSIONS) }) { Text("Zugriff erlauben …") }
                    TextButton(onClick = { why = true }) { Text("Was wird gelesen?") }
                }
                else -> {
                    Text("Erlaubt: " + kindsText(granted), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 7, 30).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d Tage") }) }
                    }
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Werte werden gelesen …", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Button(shape = buttonShape(), onClick = {
                            loading = true
                            info = null
                            scope.launch {
                                val now = Instant.now()
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        HealthConnectReader.read(context, now.minus(days.toLong(), ChronoUnit.DAYS), now, AppPrefs.sugarUnit(context) == "mmol/l")
                                    }
                                }
                                loading = false
                                result.onSuccess { list ->
                                    val fresh = HealthValues.newOnes(list, vitals)
                                    if (fresh.isEmpty()) {
                                        info = if (list.isEmpty()) "In diesem Zeitraum liegen keine passenden Werte in Health Connect." else "Alle Werte aus diesem Zeitraum sind schon übernommen."
                                    } else {
                                        found = fresh
                                    }
                                }.onFailure { info = "Lesen hat nicht geklappt: " + (it.message ?: "unbekannter Fehler") }
                            }
                        }) { Text("Werte holen") }
                    }
                    info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { launcher.launch(HealthConnectReader.PERMISSIONS) }) { Text("Rechte ändern") }
                        TextButton(onClick = { why = true }) { Text("Was wird gelesen?") }
                    }
                }
            }
        }
    }

    found?.let { list ->
        HealthImportDialog(
            values = list,
            personId = person?.id ?: 1L,
            personName = person?.name,
            onImport = { chosen, labels ->
                vm.importVitals(chosen, labels)
                info = "${chosen.size} Wert(e) übernommen."
                found = null
            },
            onDismiss = { found = null },
        )
    }
    if (why) HealthWhyDialog { why = false }
}

/**
 * Auf der Heute-Karte „Trinken und Werte“: Beim Öffnen ein kurzer Blick in Health Connect (letzte 3 Tage, nur die für diese
 * Person gewählten Quellen). Gibt es neue Werte, erscheint ein Hinweis; übernommen wird erst nach der Vorschau.
 */
@Composable
fun HealthNewValuesHint(vm: MainViewModel, person: Person?, vitals: List<VitalEntry>) {
    val context = LocalContext.current
    val pid = person?.id ?: return
    var fresh by remember(pid) { mutableStateOf<List<HealthValue>>(emptyList()) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(pid, vitals.size) {
        val sources = AppPrefs.healthSources(context, pid)
        if (sources.isEmpty() || !HealthConnectReader.available(context)) return@LaunchedEffect
        if (HealthConnectReader.granted(context).isEmpty()) return@LaunchedEffect
        val now = Instant.now()
        val list = runCatching {
            withContext(Dispatchers.IO) {
                HealthConnectReader.read(context, now.minus(3, ChronoUnit.DAYS), now, AppPrefs.sugarUnit(context) == "mmol/l")
            }
        }.getOrDefault(emptyList())
        fresh = HealthValues.newOnes(list.filter { it.source in sources }, vitals.filter { it.personId == pid })
    }
    if (fresh.isEmpty()) return
    val labels = remember(fresh) { fresh.map { it.source }.distinct().joinToString(", ") { HealthConnectReader.label(context, it) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "⌚ ${fresh.size} neue " + (if (fresh.size == 1) "Wert" else "Werte") + " von $labels",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { show = true }) { Text("Ansehen") }
    }
    if (show) {
        HealthImportDialog(
            values = fresh,
            personId = pid,
            personName = person.name,
            onImport = { chosen, l ->
                vm.importVitals(chosen, l)
                show = false
                fresh = emptyList()
            },
            onDismiss = { show = false },
        )
    }
}

@Composable
private fun HealthWhyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Health Connect") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HEALTH_PRIVACY_TEXT.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

/** Vorschau: Quellen auswählen (z. B. nur das Blutdruckgerät, nicht die eigene Uhr), dann übernehmen. */
@Composable
internal fun HealthImportDialog(
    values: List<HealthValue>,
    personId: Long,
    personName: String?,
    onImport: (List<HealthValue>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var ownSources by remember { mutableStateOf(AppPrefs.healthOwnSources(context)) }
    val allBySource = remember(values) { values.groupBy { it.source } }
    val bySource = allBySource.filterKeys { it !in ownSources }
    val labels = remember(allBySource) { allBySource.keys.associateWith { HealthConnectReader.label(context, it) } }
    var selected by remember {
        val remembered = AppPrefs.healthSources(context, personId).filter { it in bySource.keys }.toSet()
        mutableStateOf(if (remembered.isNotEmpty()) remembered else if (bySource.size == 1) bySource.keys else emptySet())
    }
    val chosen = values.filter { it.source in selected }
    val zone = ZoneId.systemDefault()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Werte übernehmen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Für: " + (personName ?: "betreute Person") + ". Wähle nur Quellen, deren Werte von dieser Person stammen. " +
                        "Trägst du selbst eine Uhr, stehen deine eigenen Werte auch in Health Connect.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val hidden = allBySource.keys.filter { it in ownSources }
                if (hidden.isNotEmpty()) {
                    Text(
                        "Ausgeblendet, weil sie zu dir gehören: " + hidden.joinToString(", ") { labels[it] ?: it } +
                            ". Ändern unter Einstellungen → Beta-Funktionen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                bySource.forEach { (src, list) ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = src in selected, onCheckedChange = { on -> selected = if (on) selected + src else selected - src })
                        Column(Modifier.weight(1f)) {
                            Text(labels[src] ?: src, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                list.groupBy { it.kind }.entries.joinToString(", ") { (k, l) -> "${l.size}× " + (VitalKind.of(k)?.label ?: k) },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TextButton(onClick = {
                        ownSources = ownSources + src
                        AppPrefs.setHealthOwnSources(context, ownSources)
                        selected = selected - src
                    }) { Text("Das ist mein eigenes Gerät") }
                    if (src in selected) {
                        list.take(4).forEach { v ->
                            val k = VitalKind.of(v.kind)
                            val dec = if (k == VitalKind.Blutzucker && v.unit == "mmol/l") 1 else k?.decimals ?: 1
                            Text(
                                "   " + Instant.ofEpochMilli(v.atMillis).atZone(zone).format(WHEN) + "  " + (k?.label ?: v.kind) + " " +
                                    Vitals.number(v.value, dec) + (v.value2?.let { "/" + Vitals.number(it, dec) } ?: "") + " " + v.unit +
                                    (if (v.note.isNotBlank()) " (${v.note})" else ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (list.size > 4) Text("   … und ${list.size - 4} weitere", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "Werte von Uhren und Bändern können ungenau sein. Jeder Wert bekommt die Quelle als Notiz und lässt sich einzeln löschen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                AppPrefs.setHealthSources(context, personId, selected)
                onImport(chosen, labels)
            }) { Text("Übernehmen (${chosen.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Einstellungen → Beta-Funktionen: Verbindung zu Health Connect. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HealthConnectSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { HealthConnectReader.available(context) }
    val state = remember { HealthState() }
    var why by remember { mutableStateOf(false) }
    var ownSources by remember { mutableStateOf(AppPrefs.healthOwnSources(context)) }
    LaunchedEffect(available) { if (available) state.granted = HealthConnectReader.granted(context) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { state.granted = HealthConnectReader.granted(context) }
    }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⌚ Uhr, Fitnessband und Messgeräte (Beta)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Über Health Connect übernimmt die App Blutdruck, Puls, Gewicht, Blutzucker, Temperatur, Sauerstoff und Trinkmenge aus anderen Apps, " +
                    "z. B. Fitbit / Pixel Watch, Samsung Health, Garmin Connect, Withings oder die App deines Blutdruckgeräts, sofern diese ihre Werte an Health Connect geben. " +
                    "In der jeweiligen App muss das Teilen mit Health Connect eingeschaltet sein.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Wichtig: Health Connect hat nur Werte, die auf diesem Handy ankommen. Uhr oder Messgerät der betreuten Person müssen also mit diesem Handy verbunden sein.",
                style = MaterialTheme.typography.bodySmall,
            )
            val granted = state.granted
            Text(
                when {
                    !available -> "Health Connect ist auf diesem Handy nicht verfügbar."
                    granted == null -> "Wird geprüft …"
                    granted.isEmpty() -> "Status: noch kein Zugriff erlaubt."
                    else -> "Status: erlaubt für " + kindsText(granted) + "."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (available) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { launcher.launch(HealthConnectReader.PERMISSIONS) }) {
                        Text(if (granted.isNullOrEmpty()) "Zugriff erlauben …" else "Rechte ändern")
                    }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        runCatching { context.startActivity(HealthConnectReader.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Health Connect öffnen") }
                }
            }
            if (ownSources.isNotEmpty()) {
                Text("Deine eigenen Geräte (nur für deinen Assistenten, nie bei der gepflegten Person):", style = MaterialTheme.typography.labelLarge)
                ownSources.forEach { src ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(HealthConnectReader.label(context, src), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            ownSources = ownSources - src
                            AppPrefs.setHealthOwnSources(context, ownSources)
                        }) { Text("Entfernen") }
                    }
                }
            }
            TextButton(onClick = { why = true }) { Text("Was wird gelesen?") }
            Text(
                "Übernehmen: Heute → „Trinken und Werte“ → „⌚ Von Uhr oder Messgerät“. Gelesen wird nur auf Tippen, gespeichert nur nach Bestätigung. " +
                    "Deine eigenen Werte (Schlaf, Schritte, Ruhepuls) nutzt nur dein Assistent, einschalten unter „Persönlicher Assistent“.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (why) HealthWhyDialog { why = false }
}
