package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.summary.VitalImport
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.Vitals
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.verticalScroll

/** Was aus welchem Dokument übernommen werden soll. */
data class VitalImportJob(val personId: Long, val title: String, val text: String, val docDate: LocalDate?, val kind: VitalKind? = null)

/**
 * Messwerte aus einem Dokument prüfen und übernehmen: Art wählen, Werte ab- oder anhaken, Zahlen korrigieren.
 * Gespeichert wird erst mit „Übernehmen“.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VitalImportDialog(job: VitalImportJob, vm: MainViewModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var kind by remember { mutableStateOf(job.kind ?: VitalImport.guessKind(job.text) ?: VitalKind.Blutzucker) }
    var aiText by remember { mutableStateOf<String?>(null) }
    val result = remember(kind, aiText) { VitalImport.parse(aiText ?: job.text, kind, job.docDate) }
    val checked = remember(kind, aiText) { mutableStateListOf<Boolean>().apply { addAll(result.candidates.map { true }) } }
    val edited = remember(kind, aiText) { mutableStateListOf<String>().apply { addAll(result.candidates.map { valueText(it) }) } }
    var saving by remember { mutableStateOf(false) }
    // Optional: KI ordnet den erkannten Text (Handschrift) in eine saubere Liste, die dann wie gewohnt gelesen wird
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiConfirm by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun askAi() {
        aiBusy = true
        aiError = null
        scope.launch {
            de.meinhelfer.tag.ai.AiClient.ask(context, VITAL_AI_SYSTEM, vitalAiPrompt(kind, job), 2500, de.meinhelfer.tag.ai.AiTask.Dokument)
                .onSuccess { aiText = it }
                .onFailure { aiError = it.message ?: "Das hat nicht geklappt." }
            aiBusy = false
        }
    }
    val dayFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yy") }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onClose,
        title = { Text("Messwerte übernehmen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aus „${job.title}“. Bitte prüfen: Handschrift wird nicht immer richtig gelesen.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VitalImport.KINDS.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.emoji + " " + k.label) })
                    }
                }
                if (!de.meinhelfer.tag.ai.AiMode.isOff(context) && de.meinhelfer.tag.ai.AiConfig.isConfigured(context)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !aiBusy, onClick = { if (de.meinhelfer.tag.ai.AiMode.skipConfirm(context)) askAi() else aiConfirm = true }) {
                            Text(if (aiBusy) "KI ordnet …" else if (aiText == null) "🤖 Mit KI ordnen (Handschrift)" else "🤖 Nochmal mit KI")
                        }
                        if (aiText != null) TextButton(onClick = { aiText = null }) { Text("Ohne KI") }
                    }
                    aiError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                if (result.candidates.isEmpty()) {
                    Text(
                        "Keine ${kind.label}-Werte erkannt. Wähle eine andere Art oder korrigiere den erkannten Text im Dokument (Bearbeiten), z. B. Datum als 01.11. und Werte durch Leerzeichen getrennt.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(VitalImport.summary(result) + " · " + result.candidates[0].unit, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { checked.indices.forEach { checked[it] = true } }) { Text("Alle") }
                        TextButton(onClick = { checked.indices.forEach { checked[it] = false } }) { Text("Keine") }
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(result.candidates) { i, c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked.getOrElse(i) { false }, onCheckedChange = { checked[i] = it })
                                Column(Modifier.weight(1f)) {
                                    Text(LocalDate.ofEpochDay(c.day).format(dayFmt) + " " + "%02d:%02d".format(c.minute / 60, c.minute % 60), style = MaterialTheme.typography.bodyMedium)
                                    if (c.context.isNotBlank()) Text(c.context, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedTextField(
                                    value = edited.getOrElse(i) { "" },
                                    onValueChange = { edited[i] = it.take(9) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = if (kind == VitalKind.Blutdruck) KeyboardType.Text else KeyboardType.Decimal),
                                    modifier = Modifier.width(110.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = result.candidates.indices.filter { checked.getOrElse(it) { false } }
            TextButton(enabled = chosen.isNotEmpty() && !saving, onClick = {
                saving = true
                val list = chosen.mapNotNull { i -> applyEdit(result.candidates[i], edited.getOrElse(i) { "" }) }
                vm.importVitals(job.personId, list, "aus Dokument „${job.title}“") { added, skipped ->
                    android.widget.Toast.makeText(
                        context,
                        "$added Werte übernommen" + if (skipped > 0) ", $skipped schon vorhanden" else "",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    onClose()
                }
            }) { Text("${chosen.size} übernehmen") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
    if (aiConfirm) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { aiConfirm = false },
            title = { Text("An die KI senden?") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gesendet wird der erkannte Text der Liste (Messwerte), ohne Namen.", style = MaterialTheme.typography.bodySmall)
                    Text(de.meinhelfer.tag.summary.Anonymizer.scrub(vitalAiPrompt(kind, job)), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { aiConfirm = false; askAi() }) { Text("Senden") } },
            dismissButton = { TextButton(onClick = { aiConfirm = false }) { Text("Abbrechen") } },
        )
    }
}

private const val VITAL_AI_SYSTEM =
    "Du bekommst den automatisch erkannten Text einer handschriftlichen Messwert-Liste (oft unsauber). Ordne die Werte. " +
        "Gib NUR Zeilen in genau diesem Format aus, eine Messung pro Zeile, ohne weiteren Text: TT.MM.JJJJ HH:MM WERT " +
        "(bei Blutdruck WERT als OBEN/UNTEN, z. B. 135/85). Fehlt die Uhrzeit, nimm morgens 07:00, mittags 12:00, abends 18:00, spät 22:00. " +
        "Erfinde keine Werte; Unleserliches weglassen. Insulin-Einheiten sind keine Messwerte."

private fun vitalAiPrompt(kind: VitalKind, job: VitalImportJob): String =
    "Art: ${kind.label} (${kind.unit})" + (job.docDate?.let { "\nDatum des Dokuments: " + it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) } ?: "") +
        "\n\nErkannter Text:\n" + job.text.take(8000)

private fun valueText(c: VitalImport.Candidate): String =
    Vitals.number(c.value, if (c.unit == "mmol/l") 1 else c.kind.decimals) + (c.value2?.let { "/" + Vitals.number(it, 0) } ?: "")

/** Korrigierte Zahl übernehmen; Unlesbares fällt weg. */
private fun applyEdit(c: VitalImport.Candidate, text: String): VitalImport.Candidate? {
    val parts = text.split('/')
    val a = Vitals.parse(parts[0]) ?: return null
    val b = parts.getOrNull(1)?.let { Vitals.parse(it) }
    return c.copy(value = a, value2 = if (c.kind == VitalKind.Blutdruck) (b ?: c.value2) else null)
}
