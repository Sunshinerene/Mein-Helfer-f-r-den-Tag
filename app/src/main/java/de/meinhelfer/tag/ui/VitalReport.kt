package de.meinhelfer.tag.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Duties
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.export.VitalReportPdf
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.summary.MeasurePlans
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.VitalStats
import de.meinhelfer.tag.summary.Vitals
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REPORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/** Bericht über Messwerte für die gewählte Person und einen Zeitraum: PDF teilen, drucken, mailen oder unter Dokumente ablegen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VitalReportDialog(vm: MainViewModel, person: Person?, onClose: () -> Unit, initialDays: Int = 14) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val duties by vm.duties.collectAsStateWithLifecycle()
    val pid = person?.id ?: 1L
    val today = LocalDate.now()
    var days by remember { mutableIntStateOf(initialDays) }
    var from by remember { mutableStateOf(today.minusDays(initialDays - 1L)) }
    var to by remember { mutableStateOf(today) }
    var deselected by remember { mutableStateOf(setOf<String>()) }
    var charts by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var savedKey by remember { mutableStateOf<String?>(null) }

    val fromDay = minOf(from, to).toEpochDay()
    val toDay = maxOf(from, to).toEpochDay()
    val inRange = vitals.filter { it.personId == pid && it.day in fromDay..toDay }
    val available = VitalKind.entries.filter { k -> inRange.any { it.kind == k.key } }
    val chosen = available.filter { it.key !in deselected }
    val key = "$fromDay-$toDay-${chosen.joinToString { it.key }}-$charts"

    // Empfänger: Kontakte mit E-Mail, zuerst der zuständige Arzt
    val withMail = doctors.filter { it.email.isNotBlank() }
    val rx = Duties.defaultFor(duties, pid, Duties.REZEPT)
    var mailTo by remember { mutableStateOf(withMail.firstOrNull { it.id == rx }?.let { doctorLabel(it) } ?: "") }

    fun make(ctx: Context): File = VitalReportPdf.build(
        ctx, person, inRange.filter { e -> chosen.any { it.key == e.kind } }, chosen, fromDay, toDay,
        AppPrefs.drinkGoal(ctx, pid),
        VitalKind.MEASURES.mapNotNull { k -> AppPrefs.vitalRange(ctx, pid, k.key)?.let { k.key to it } }.toMap(),
        charts,
        MeasurePlans.parse(AppPrefs.measurePlan(ctx, pid)),
    )

    fun run(action: suspend (File) -> Unit) {
        if (busy || chosen.isEmpty()) return
        busy = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { make(context.applicationContext) }.getOrNull() }
            if (f == null) Toast.makeText(context, "PDF konnte nicht erstellt werden.", Toast.LENGTH_SHORT).show() else action(f)
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Bericht: Messwerte" + (person?.let { " · ${it.name}" } ?: "")) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zeitraum", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 14, 30, 90).forEach { d ->
                        FilterChip(selected = days == d, onClick = {
                            days = d
                            to = today
                            from = today.minusDays(d - 1L)
                        }, label = { Text("$d Tage") })
                    }
                    FilterChip(selected = days == 0, onClick = { days = 0 }, label = { Text("Eigener") })
                }
                if (days == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DatePickerField(label = "Von", date = from, onChange = { from = it }, modifier = Modifier.weight(1f))
                        DatePickerField(label = "Bis", date = to, onChange = { to = it }, modifier = Modifier.weight(1f))
                    }
                }
                Text(
                    LocalDate.ofEpochDay(fromDay).format(REPORT_DATE) + " bis " + LocalDate.ofEpochDay(toDay).format(REPORT_DATE) +
                        " · ${inRange.size} Einträge",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                Text("Welche Werte?", style = MaterialTheme.typography.labelLarge)
                if (available.isEmpty()) {
                    Text("In diesem Zeitraum ist nichts eingetragen.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        available.forEach { k ->
                            val on = k.key !in deselected
                            FilterChip(
                                selected = on,
                                onClick = { deselected = if (on) deselected + k.key else deselected - k.key },
                                label = { Text(k.emoji + " " + k.label + " (" + inRange.count { it.kind == k.key } + ")") },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mit Verlauf als Grafik", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = charts, onCheckedChange = { charts = it })
                }
                Text(
                    "Im Bericht: je Wert Anzahl, Spanne und Durchschnitt (auch je Angabe wie „nüchtern“), dein eigener Bereich, " +
                        "geplante Messzeiten und alle Messungen mit Uhrzeit und Notiz. Keine Bewertung.",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ok = !busy && chosen.isNotEmpty()
                    OutlinedButton(shape = buttonShape(), enabled = ok, onClick = { run { sharePdfFile(context, it, "Messwerte teilen") } }) { Text("Teilen") }
                    OutlinedButton(shape = buttonShape(), enabled = ok, onClick = { run { printPdfFile(context, it) } }) { Text("Drucken") }
                    OutlinedButton(shape = buttonShape(), enabled = ok && savedKey != key, onClick = {
                        run { f ->
                            val stored = runCatching { DocumentStore.store(context, f) }.getOrNull()
                            if (stored == null) {
                                Toast.makeText(context, "Ablegen hat nicht geklappt.", Toast.LENGTH_LONG).show()
                            } else {
                                val stats = chosen.mapNotNull { k ->
                                    if (k == VitalKind.Trinken) null else VitalStats.of(k, inRange, AppPrefs.vitalRange(context, pid, k.key))?.let { k.label + ": " + VitalStats.line(it) }
                                }
                                vm.addDocumentNow(
                                    Document(
                                        title = VitalReportPdf.title(fromDay, toDay),
                                        category = "Befund / Labor",
                                        docDate = toDay,
                                        fileName = stored,
                                        mimeType = "application/pdf",
                                        text = (listOf("Messwerte " + chosen.joinToString(", ") { it.label }) + stats).joinToString("\n"),
                                        notes = "In der App erstellt („Trinken und Werte“). Selbst erfasst, keine Bewertung.",
                                    ),
                                )
                                savedKey = key
                                Toast.makeText(context, "Unter Dokumente abgelegt (Befund / Labor).", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text(if (savedKey == key) "Abgelegt ✓" else "In Dokumente ablegen") }
                }
                if (withMail.isNotEmpty()) {
                    DropdownField(
                        label = "Per E-Mail an",
                        value = mailTo,
                        options = listOf("– in der E-Mail-App wählen –") + withMail.map { doctorLabel(it) },
                        editable = false,
                        onValueChange = { mailTo = if (it.startsWith("–")) "" else it },
                    )
                }
                OutlinedButton(shape = buttonShape(), enabled = !busy && chosen.isNotEmpty(), onClick = {
                    run { f ->
                        val recipient = withMail.firstOrNull { doctorLabel(it) == mailTo }?.email
                        val caregiver = AppPrefs.caregiver(context)
                        val period = LocalDate.ofEpochDay(fromDay).format(REPORT_DATE) + " bis " + LocalDate.ofEpochDay(toDay).format(REPORT_DATE)
                        val who = person?.name?.takeIf { it.isNotBlank() }?.let { " von $it" } ?: ""
                        val text = "Guten Tag,\n\nanbei die selbst erfassten Messwerte$who vom $period als PDF " +
                            "(${chosen.joinToString(", ") { it.label }}).\n\n" +
                            if (caregiver.isSet) caregiver.signature() else "Mit freundlichen Grüßen"
                        openMailWithFiles(context, recipient, "Messwerte$who, $period", text, listOf(f))
                    }
                }) { Text("✉ Per E-Mail senden") }
                Text(
                    "Nichts wird von selbst verschickt: Die E-Mail öffnet sich in deiner E-Mail-App, dort prüfst du sie und sendest selbst.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

/** Kurze Karte mit Knopf zum Bericht, z. B. in „Trinken und Werte“. */
@Composable
fun VitalReportButton(vm: MainViewModel, person: Person?, label: String = "Bericht erstellen …", initialDays: Int = 14) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(shape = buttonShape(), onClick = { show = true }) { Text(label) }
    if (show) VitalReportDialog(vm, person, onClose = { show = false }, initialDays = initialDays)
}

/** Messwerte der letzten [days] Tage als PDF mit allen vorhandenen Werten (z. B. für die Terminvorbereitung). */
fun recentVitalsPdf(context: Context, person: Person?, vitals: List<VitalEntry>, days: Int = 14): File {
    val pid = person?.id ?: 1L
    val today = LocalDate.now().toEpochDay()
    val from = today - days + 1
    val list = vitals.filter { it.personId == pid && it.day in from..today }
    val kinds = VitalKind.entries.filter { k -> list.any { it.kind == k.key } }
    return VitalReportPdf.build(
        context, person, list, kinds, from, today, AppPrefs.drinkGoal(context, pid),
        VitalKind.MEASURES.mapNotNull { k -> AppPrefs.vitalRange(context, pid, k.key)?.let { k.key to it } }.toMap(),
        charts = true,
        plan = MeasurePlans.parse(AppPrefs.measurePlan(context, pid)),
    )
}

/** „Zuletzt: 🩸 128 mg/dl (nüchtern)“ */
internal fun vitalShort(e: VitalEntry): String =
    (VitalKind.of(e.kind)?.emoji ?: "") + " " + Vitals.valueText(e) + if (e.context.isNotBlank()) " (${e.context})" else ""
