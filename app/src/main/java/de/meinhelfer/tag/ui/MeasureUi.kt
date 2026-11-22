package de.meinhelfer.tag.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.summary.MeasureDue
import de.meinhelfer.tag.summary.MeasurePlans
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.Vitals
import de.meinhelfer.tag.update.AppPrefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Messwerte als Schnellknöpfe: eigene Auswahl der Person, sonst die in den letzten 30 Tagen am häufigsten gemessenen,
 * sonst Blutdruck, Blutzucker und Temperatur.
 */
internal fun quickKinds(context: Context, personId: Long, vitals: List<VitalEntry>): List<VitalKind> {
    AppPrefs.vitalQuick(context, personId)?.let { keys -> return keys.mapNotNull { VitalKind.of(it) }.filter { it != VitalKind.Trinken } }
    val since = LocalDate.now().toEpochDay() - 30
    val used = vitals.filter { it.personId == personId && it.day >= since && it.kind != VitalKind.Trinken.key }
        .groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
        .mapNotNull { VitalKind.of(it.key) }.take(3)
    return used.ifEmpty { VitalKind.DEFAULT_QUICK }
}

/** Geplante Messungen von heute für eine Person. */
internal fun todayMeasures(context: Context, personId: Long, vitals: List<VitalEntry>): List<MeasureDue> {
    val plan = MeasurePlans.parse(AppPrefs.measurePlan(context, personId))
    if (plan.isEmpty()) return emptyList()
    return MeasurePlans.forDay(personId, plan, vitals, LocalDate.now().toEpochDay())
}

/** Heute zu messen: erledigt mit Wert, offen zum Antippen (öffnet das Eintragen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayMeasures(dues: List<MeasureDue>, onEnter: (VitalKind) -> Unit) {
    if (dues.isEmpty()) return
    val now = LocalDateTime.now()
    Text("Heute zu messen", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dues.forEach { m ->
            val late = !m.done && LocalDate.ofEpochDay(m.day).atTime(m.time).plusMinutes(MeasurePlans.AFTER_MINUTES).isBefore(now)
            val label = if (m.done) {
                "✓ ${m.timeText} ${m.kind.label} " + Vitals.valueText(m.entry!!)
            } else {
                (if (late) "⚠ " else "") + "${m.timeText} ${m.kind.emoji} ${m.kind.label}"
            }
            FilterChip(
                selected = m.done,
                onClick = { if (!m.done) onEnter(m.kind) },
                label = { Text(label, color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
            )
        }
    }
}

/**
 * Für eine Person festlegen, welche Werte als Schnellknöpfe erscheinen und wann gemessen werden soll
 * (mit Erinnerung, z. B. Blutzucker 3× täglich wie vom Arzt besprochen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasureSettingsDialog(vm: MainViewModel, personId: Long, personName: String?, vitals: List<VitalEntry>, onDone: () -> Unit) {
    val context = LocalContext.current
    var quick by remember { mutableStateOf(quickKinds(context, personId, vitals).map { it.key }) }
    var plan by remember { mutableStateOf(MeasurePlans.parse(AppPrefs.measurePlan(context, personId))) }
    var pickFor by remember { mutableStateOf<VitalKind?>(null) }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDone,
        title = { Text("Messwerte" + (personName?.let { " für $it" } ?: "")) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "„Schnellknopf“: erscheint auf der Heute-Seite zum schnellen Eintragen. „+ Messzeit“: feste Uhrzeit mit Erinnerung, " +
                        "z. B. Blutzucker 07:30, 12:00 und 18:00. Wie oft gemessen wird, legen Ärztin oder Arzt fest.",
                    style = MaterialTheme.typography.bodySmall,
                )
                VitalKind.MEASURES.forEach { k ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.emoji + " " + k.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = k.key in quick,
                            onClick = { quick = if (k.key in quick) quick - k.key else quick + k.key },
                            label = { Text("Schnellknopf") },
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        plan[k].orEmpty().forEach { t ->
                            FilterChip(
                                selected = true,
                                onClick = {
                                    val rest = plan[k].orEmpty() - t
                                    plan = if (rest.isEmpty()) plan - k else plan + (k to rest)
                                },
                                label = { Text(MeasurePlans.timesText(listOf(t)) + " ✕") },
                            )
                        }
                        AssistChip(onClick = { pickFor = k }, label = { Text("+ Messzeit") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setMeasureSettings(personId, VitalKind.MEASURES.map { it.key }.filter { it in quick }, MeasurePlans.format(plan))
                onDone()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )

    pickFor?.let { k ->
        TimePickerDialog(
            initial = plan[k]?.lastOrNull()?.plusHours(4)?.takeIf { it.hour >= 6 } ?: LocalTime.of(8, 0),
            onConfirm = { t ->
                plan = plan + (k to (plan[k].orEmpty() + t).distinct().sorted())
                pickFor = null
            },
            onDismiss = { pickFor = null },
        )
    }
}
