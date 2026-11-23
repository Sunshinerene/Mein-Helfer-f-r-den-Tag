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
import java.time.format.DateTimeFormatter

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
    val result = remember(kind) { VitalImport.parse(job.text, kind, job.docDate) }
    val checked = remember(kind) { mutableStateListOf<Boolean>().apply { addAll(result.candidates.map { true }) } }
    val edited = remember(kind) { mutableStateListOf<String>().apply { addAll(result.candidates.map { valueText(it) }) } }
    var saving by remember { mutableStateOf(false) }
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
}

private fun valueText(c: VitalImport.Candidate): String =
    Vitals.number(c.value, if (c.unit == "mmol/l") 1 else c.kind.decimals) + (c.value2?.let { "/" + Vitals.number(it, 0) } ?: "")

/** Korrigierte Zahl übernehmen; Unlesbares fällt weg. */
private fun applyEdit(c: VitalImport.Candidate, text: String): VitalImport.Candidate? {
    val parts = text.split('/')
    val a = Vitals.parse(parts[0]) ?: return null
    val b = parts.getOrNull(1)?.let { Vitals.parse(it) }
    return c.copy(value = a, value2 = if (c.kind == VitalKind.Blutdruck) (b ?: c.value2) else null)
}
