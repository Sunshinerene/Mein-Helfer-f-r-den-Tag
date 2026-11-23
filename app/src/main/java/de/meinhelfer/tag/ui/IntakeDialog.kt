package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.summary.Amounts
import de.meinhelfer.tag.summary.MeasurePlans
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.Vitals
import de.meinhelfer.tag.update.AppPrefs
import java.time.LocalDate
import java.time.LocalTime

/**
 * Einnahme-Fenster für eine Uhrzeit: alle Medikamente dieser Zeit abhaken, die tatsächlich gegebene Menge eintragen
 * (z. B. Insulin nach Schema) und Messwerte wie Blutzucker gleich mit festhalten. Weitere Werte lassen sich hinzufügen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntakeDialog(vm: MainViewModel, meds: List<Medication>, day: Long, slot: String, checks: List<Checkin>, onDone: () -> Unit) {
    val context = LocalContext.current
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val pid by vm.personId.collectAsStateWithLifecycle()
    val slotTime = runCatching { LocalTime.parse(slot) }.getOrNull() ?: LocalTime.now()
    fun checkOf(m: Medication) = checks.firstOrNull { it.kind == Checkin.MED && it.refId == m.id && it.day == day && it.slot == slot }

    val given = remember { mutableStateMapOf<Long, Boolean>().apply { meds.forEach { m -> put(m.id, checkOf(m)?.status.isNullOrEmpty()) } } }
    val amounts = remember {
        mutableStateMapOf<Long, String>().apply {
            meds.forEach { m -> put(m.id, (checkOf(m)?.amount ?: m.doseMap()[slot])?.let { Amounts.fmt(it) }.orEmpty()) }
        }
    }
    // Messwerte: aus den Medikamenten dieser Uhrzeit und geplante Messzeiten in der Nähe
    val plan = remember(pid) { MeasurePlans.parse(AppPrefs.measurePlan(context, pid)) }
    val planned = plan.filter { (_, times) -> times.any { kotlin.math.abs(it.toSecondOfDay() - slotTime.toSecondOfDay()) <= 90 * 60 } }.keys
    var kinds by remember {
        mutableStateOf((meds.flatMap { it.intakeVitalKeys() }.mapNotNull { VitalKind.of(it) } + planned).distinct().filter { it != VitalKind.Trinken })
    }
    val v1 = remember { mutableStateMapOf<String, String>() }
    val v2 = remember { mutableStateMapOf<String, String>() }
    val ctx = remember { mutableStateMapOf<String, String>() }
    var sugarUnit by remember { mutableStateOf(AppPrefs.sugarUnit(context)) }
    var note by remember { mutableStateOf("") }
    var addMore by remember { mutableStateOf(false) }

    fun unitOf(k: VitalKind) = if (k == VitalKind.Blutzucker) sugarUnit else k.unit
    fun rangeOf(k: VitalKind) = if (k == VitalKind.Blutzucker && sugarUnit == "mmol/l") VitalKind.MMOL_RANGE else k.plausible
    fun ok(k: VitalKind): Boolean {
        val a = v1[k.key].orEmpty()
        if (a.isBlank()) return v2[k.key].isNullOrBlank()
        val x = Vitals.parse(a) ?: return false
        if (x !in rangeOf(k)) return false
        val sec = k.second ?: return true
        val y = Vitals.parse(v2[k.key].orEmpty()) ?: return false
        return y in sec
    }
    val allOk = kinds.all { ok(it) } && meds.all { m -> amounts[m.id].isNullOrBlank() || Amounts.parse(amounts[m.id]!!) != null }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDone,
        title = { Text("Einnahme $slot Uhr") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                meds.forEach { m ->
                    val planned1 = m.doseMap()[slot]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = given[m.id] == true, onCheckedChange = { given[m.id] = it })
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyLarge)
                            val sub = listOfNotNull(
                                planned1?.let { "geplant " + Amounts.withUnit(it, m.unit) },
                                m.dosage.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
                        }
                        if (m.askAtIntake) {
                            OutlinedTextField(
                                value = amounts[m.id].orEmpty(),
                                onValueChange = { amounts[m.id] = it.filter { c -> c.isDigit() || c in ",.½¼¾" }.take(6) },
                                label = { Text("gegeben (${m.unit})", maxLines = 1) },
                                singleLine = true,
                                isError = !amounts[m.id].isNullOrBlank() && Amounts.parse(amounts[m.id]!!) == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
                if (kinds.isNotEmpty() || addMore) HorizontalDivider()
                kinds.forEach { k ->
                    val recent = vitals.filter { it.kind == k.key }.firstOrNull {
                        kotlin.math.abs(it.atMillis - System.currentTimeMillis()) <= 90 * 60_000L
                    }
                    Text(k.emoji + " " + k.label, style = MaterialTheme.typography.titleSmall)
                    recent?.let { Text("Schon gemessen: " + vitalShort(it), style = MaterialTheme.typography.bodySmall) }
                    if (k == VitalKind.Blutzucker) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("mg/dl", "mmol/l").forEach { u ->
                                FilterChip(selected = sugarUnit == u, onClick = {
                                    sugarUnit = u
                                    AppPrefs.setSugarUnit(context, u)
                                }, label = { Text(u) })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = v1[k.key].orEmpty(),
                            onValueChange = { v1[k.key] = it.take(7) },
                            label = { Text(if (k.second != null) "Oberer Wert" else unitOf(k).ifBlank { "Wert" }, maxLines = 1) },
                            singleLine = true,
                            isError = !ok(k),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        if (k.second != null) {
                            OutlinedTextField(
                                value = v2[k.key].orEmpty(),
                                onValueChange = { v2[k.key] = it.take(7) },
                                label = { Text("Unterer Wert", maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (k.contexts.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            k.contexts.forEach { c ->
                                FilterChip(selected = ctx[k.key] == c, onClick = { ctx[k.key] = if (ctx[k.key] == c) "" else c }, label = { Text(c) })
                            }
                        }
                    }
                }
                if (addMore) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VitalKind.MEASURES.filter { it !in kinds }.forEach { k ->
                            AssistChip(onClick = {
                                kinds = kinds + k
                                addMore = false
                            }, label = { Text(k.emoji + " " + k.label) })
                        }
                    }
                } else {
                    TextButton(onClick = { addMore = true }) { Text("+ Messwert hinzufügen") }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Bemerkung (optional)") },
                    placeholder = { Text("z. B. vor dem Frühstück, Einstichstelle Bauch links") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Nur zum Festhalten, die App bewertet keine Werte. Leere Messfelder werden nicht gespeichert. " +
                        "Ohne Haken bleibt eine Einnahme offen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = allOk, onClick = {
                meds.forEach { m ->
                    if (given[m.id] == true) {
                        vm.saveCheckin(
                            Checkin.MED, m.id, day, slot, done = true, status = "", actual = "",
                            note = note.takeIf { it.isNotBlank() },
                            amount = if (m.askAtIntake) amounts[m.id]?.let { Amounts.parse(it) } else null,
                        )
                    }
                }
                val at = if (day == LocalDate.now().toEpochDay()) System.currentTimeMillis()
                else LocalDate.ofEpochDay(day).atTime(slotTime).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                kinds.forEach { k ->
                    val a = Vitals.parse(v1[k.key].orEmpty()) ?: return@forEach
                    vm.addVital(k.key, a, if (k.second != null) Vitals.parse(v2[k.key].orEmpty()) else null, unitOf(k), "", at, ctx[k.key].orEmpty())
                }
                onDone()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}
