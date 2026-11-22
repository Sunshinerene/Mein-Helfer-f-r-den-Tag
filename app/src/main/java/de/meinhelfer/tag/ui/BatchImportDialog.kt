package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.summary.BatchFile
import de.meinhelfer.tag.summary.BatchPlan
import de.meinhelfer.tag.summary.DocGrouping
import de.meinhelfer.tag.summary.Expression

/**
 * Sammel-Import: Sind die Dateien einzelne Dokumente oder gehören welche zusammen (z. B. Seite 1 und 2)?
 * Zeigt den Vorschlag aus [DocGrouping], lässt ihn ändern und gibt die Dokumente als Gruppen von Datei-Indizes zurück.
 * Mit eingeschaltetem Assistenten fragt er selbst (Gesicht und Name).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatchImportDialog(
    files: List<BatchFile>,
    plan: BatchPlan,
    assistantName: String?,
    skipped: Int,
    personName: String? = null,
    onConfirm: (List<List<Int>>) -> Unit,
    onCancel: () -> Unit,
) {
    var order by remember { mutableStateOf(plan.order) }
    var join by remember { mutableStateOf(plan.join) }
    var reasons by remember { mutableStateOf(plan.reasons) }
    val groups = DocGrouping.groups(order, join)
    val n = files.size
    val allMergeable = files.all { it.mergeable }
    val docOf = IntArray(order.size)
    var d = 0
    for (pos in order.indices) {
        if (pos == 0 || !join[pos]) d++
        docOf[pos] = d
    }
    val docSize = docOf.toList().groupingBy { it }.eachCount()

    fun swapWithPrevious(pos: Int) {
        if (pos <= 0) return
        order = order.toMutableList().also { val t = it[pos]; it[pos] = it[pos - 1]; it[pos - 1] = t }
        reasons = reasons.toMutableList().also { it[pos] = null; it[pos - 1] = null }
        // Kann eine Datei nach dem Tauschen nicht mehr angehängt werden (z. B. Word), Haken entfernen
        join = join.mapIndexed { i, j -> j && i > 0 && files[order[i]].mergeable && files[order[i - 1]].mergeable }
    }

    val message = when {
        plan.hasSuggestion && assistantName != null ->
            "Ich habe mir die $n Dateien angesehen. Einige gehören wohl zusammen, z. B. Seite 1 und 2 eines Briefs. " +
                "Mein Vorschlag: ${plan.groups.size} Dokumente. Schau bitte kurz drüber."
        plan.hasSuggestion ->
            "Einige der $n Dateien gehören wohl zusammen, z. B. Seite 1 und 2 eines Briefs. Vorschlag: ${plan.groups.size} Dokumente. Bitte prüfen."
        assistantName != null ->
            "Sind das $n einzelne Dokumente? Ich habe keine Seiten gefunden, die zusammengehören. " +
                "Gehört eine Datei zur vorigen (z. B. Seite 2), hake bei ihr „gehört zur Datei darüber“ an."
        else ->
            "Sind das $n einzelne Dokumente? Gehört eine Datei zur vorigen (z. B. Seite 2), hake bei ihr „gehört zur Datei darüber“ an."
    }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = {},
        title = { Text("$n Dateien übernehmen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (assistantName != null) {
                        AssistantFace(
                            if (plan.hasSuggestion) Expression.Attentive else Expression.Thinking,
                            size = 44.dp,
                            description = "Gesicht von $assistantName",
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        if (assistantName != null) Text("$assistantName fragt", style = MaterialTheme.typography.labelLarge)
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (skipped > 0) {
                    Text(
                        "$skipped Datei(en) übersprungen (kein PDF, Foto, Text oder Word).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!personName.isNullOrBlank()) {
                    Text("Die Dokumente kommen zu: $personName", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = join.none { it },
                        onClick = { join = List(order.size) { false } },
                        label = { Text("Alle einzeln") },
                    )
                    if (plan.hasSuggestion) {
                        FilterChip(
                            selected = order == plan.order && join == plan.join,
                            onClick = {
                                order = plan.order
                                join = plan.join
                                reasons = plan.reasons
                            },
                            label = { Text("Vorschlag") },
                        )
                    }
                    if (allMergeable && n > 1) {
                        FilterChip(
                            selected = join.drop(1).all { it },
                            onClick = { join = List(order.size) { it > 0 } },
                            label = { Text("Alles ein Dokument") },
                        )
                    }
                }
                HorizontalDivider()
                order.forEachIndexed { pos, idx ->
                    val f = files[idx]
                    val joined = pos > 0 && join[pos]
                    if (!joined) {
                        val pages = docSize[docOf[pos]] ?: 1
                        Text(
                            "Dokument ${docOf[pos]}" + if (pages > 1) " · $pages Dateien" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Column(Modifier.fillMaxWidth().padding(start = if (joined) 16.dp else 0.dp)) {
                        val icon = when {
                            f.isPhoto -> "🖼"
                            f.mergeable -> "📄"
                            else -> "📝"
                        }
                        Text("$icon ${f.name}", style = MaterialTheme.typography.bodyMedium)
                        val preview = f.text.replace(Regex("\\s+"), " ").trim()
                        Text(
                            if (preview.isEmpty()) "Kein Text erkannt" else if (preview.length > 70) preview.take(70).trimEnd() + " …" else preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (pos > 0) {
                            val canJoin = f.mergeable && files[order[pos - 1]].mergeable
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (canJoin) {
                                    Checkbox(
                                        checked = join[pos],
                                        onCheckedChange = { v -> join = join.toMutableList().also { it[pos] = v } },
                                    )
                                    Text("gehört zur Datei darüber", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                } else {
                                    Text(
                                        "Wird ein eigenes Dokument (nur Fotos und PDFs lassen sich zusammenfügen).",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                TextButton(onClick = { swapWithPrevious(pos) }) { Text("↑ nach oben") }
                            }
                        }
                        val why = reasons.getOrNull(pos)
                        if (why != null && join[pos]) {
                            Text("Vorschlag, weil: $why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "Danach kommt jedes Dokument nacheinander wie gewohnt: Titel, Kategorie, Datum und Kontakt werden aus dem Text vorgeschlagen. " +
                        "Zusammengehörige Dateien werden ein Dokument mit mehreren Seiten, in der Reihenfolge von oben nach unten.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(shape = buttonShape(), onClick = { onConfirm(groups) }) {
                Text(if (groups.size == 1) "Weiter: 1 Dokument" else "Weiter: ${groups.size} Dokumente")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}
