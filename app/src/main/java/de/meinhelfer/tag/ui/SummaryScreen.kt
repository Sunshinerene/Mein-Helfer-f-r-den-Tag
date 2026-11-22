package de.meinhelfer.tag.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.summary.Period
import de.meinhelfer.tag.summary.Summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenDailyPlan: () -> Unit = {},
    onOpenHandover: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
) {
    val context = LocalContext.current
    var period by remember { mutableStateOf(Period.Day) }
    var offset by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    val addedTodos = remember { mutableStateListOf<String>() }
    var withNotes by remember { mutableStateOf(false) }

    // Bei jedem Wechsel von Zeitraum oder Seite neu berechnen (alles lokal auf dem Geraet).
    LaunchedEffect(period, offset) {
        summary = vm.buildSummary(period, offset)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Berichte und Auswertung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ReportsCard(vm, onOpenDailyPlan = onOpenDailyPlan, onOpenHandover = onOpenHandover, onOpenBudget = onOpenBudget) }
            item { Text("Auswertung", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p ->
                        FilterChip(
                            selected = p == period,
                            onClick = {
                                period = p
                                offset = 0
                            },
                            label = { Text(p.label) },
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(shape = buttonShape(), onClick = { offset-- }) { Text("‹ Zurück") }
                    OutlinedButton(shape = buttonShape(), onClick = { offset++ }, enabled = offset < 0) { Text("Weiter ›") }
                }
            }

            val s = summary
            if (s == null) {
                item { Text("Berechne …") }
            } else {
                item {
                    Text(s.title, style = MaterialTheme.typography.titleLarge)
                }
                block("Überblick", s.overview, null)?.let { item { it() } }
                block("Gut gelaufen", s.good, null)?.let { item { it() } }
                block("Auffällig", s.notable, warning = true)?.let { item { it() } }
                block(
                    "Empfehlungen (nur Anhaltspunkte)",
                    s.advice,
                    null,
                    onTodo = { line ->
                        vm.addTodo(line)
                        addedTodos.add(line)
                    },
                    added = addedTodos,
                )?.let { item { it() } }
                block("Erledigte Aufgaben", s.tasksDone, null)?.let { item { it() } }
                block("Bemerkungen", s.notes, null)?.let { item { it() } }
                block("Verlauf", s.perDay, null)?.let { item { it() } }
                item {
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("KI-Einschätzung (nur Anhaltspunkt)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Optional. Die KI bekommt nur diese Auswertung, ohne Namen der Person. Du siehst den Text vor dem Senden und entscheidest.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Switch(checked = withNotes, onCheckedChange = { withNotes = it })
                                Text("Meine Bemerkungen mitsenden", style = MaterialTheme.typography.bodyMedium)
                            }
                            val sendable = if (withNotes) s else s.copy(notes = emptyList())
                            AiHintBox(
                                buttonLabel = "KI-Hinweise holen",
                                promptText = "Hier ist eine automatisch erstellte Zusammenfassung zu Medikamenteneinnahme und Terminen einer betreuten Person. " +
                                    "Nenne Anhaltspunkte, was man mit Hausarzt oder Apotheke besprechen könnte.\n\n" + sendable.toText(),
                                onTodo = { vm.addTodo(it) },
                            )
                        }
                    }
                }
                item {
                    Text(Summary.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Button(shape = buttonShape(), 
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Auswertung: ${s.title}")
                                putExtra(Intent.EXTRA_TEXT, s.toText())
                            }
                            try {
                                context.startActivity(Intent.createChooser(send, "Auswertung teilen"))
                            } catch (_: Exception) {
                                // keine passende App
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Teilen / Kopieren") }
                }
            }
        }
    }
}

/** Karte mit Titel und Stichpunkten; null, wenn es nichts anzuzeigen gibt. */
private fun block(
    title: String,
    lines: List<String>,
    warning: Boolean?,
    onTodo: ((String) -> Unit)? = null,
    added: List<String> = emptyList(),
): (@Composable () -> Unit)? {
    if (lines.isEmpty()) return null
    return {
        HCard(
            modifier = Modifier.fillMaxWidth(),
            colors = if (warning == true) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            } else {
                null
            },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                lines.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodyMedium)
                    if (onTodo != null) {
                        val isAdded = line in added
                        TextButton(onClick = { onTodo(line) }, enabled = !isAdded) {
                            Text(if (isAdded) "✓ Als Aufgabe gemerkt" else "+ Als Aufgabe merken")
                        }
                    }
                }
            }
        }
    }
}
