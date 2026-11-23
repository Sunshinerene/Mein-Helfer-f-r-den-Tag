package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import de.meinhelfer.tag.ai.AiMode

/**
 * KI-Modus wählen: Ohne KI, mit Bestätigung, vollumfänglich. Höhere Stufen erst nach Lesen der Hinweise und Bestätigung.
 */
@Composable
fun AiModeChooser(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mode by remember { mutableStateOf(AiMode.of(context)) }
    var asking by remember { mutableStateOf<AiMode?>(null) }
    Text("KI-Modus: Was darf $name mit KI?", style = MaterialTheme.typography.labelLarge)
    Text(
        "In jedem Modus entfernt $name vor dem Senden Namen, Adressen, Telefonnummern, E-Mails, Geburtsdaten, Versicherten- und Kontonummern.",
        style = MaterialTheme.typography.bodySmall,
    )
    AiMode.entries.forEach { m ->
        Row(
            Modifier.fillMaxWidth().selectable(selected = mode == m, onClick = {
                if (m == mode) return@selectable
                if (m.key > mode.key) asking = m else {
                    AiMode.set(context, m)
                    mode = m
                }
            }),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = mode == m, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(m.title + " · " + m.level, style = MaterialTheme.typography.bodyLarge)
                Text(m.short, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (mode == AiMode.Auto) {
        var on by remember { mutableStateOf(de.meinhelfer.tag.update.AppPrefs.screenTalkOn(context)) }
        var auto by remember { mutableStateOf(de.meinhelfer.tag.update.AppPrefs.screenTalkAuto(context)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$name sagt etwas zur Seite", style = MaterialTheme.typography.bodyLarge)
                Text("Im Blatt über das Gesicht unten: „💬 Was meinst du zu dieser Seite?“ – die KI bekommt, was auf der Seite steht (ohne Namen).", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(checked = on, onCheckedChange = {
                on = it
                de.meinhelfer.tag.update.AppPrefs.setScreenTalk(context, on, auto)
            })
        }
        if (on) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatisch beim Seitenwechsel", style = MaterialTheme.typography.bodyLarge)
                    Text("$name meldet sich von selbst mit einem kurzen Kommentar – je Seite höchstens alle 30 Minuten, nicht in der Ruhezeit. Verbraucht KI-Kontingent.", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = auto, onCheckedChange = {
                    auto = it
                    de.meinhelfer.tag.update.AppPrefs.setScreenTalk(context, on, auto)
                })
            }
        }
    }
    if (mode != AiMode.Off) AiTaskSettings(name, mode == AiMode.Auto)
    if (mode != AiMode.Off && !de.meinhelfer.tag.ai.AiConfig.isConfigured(context)) {
        Text("Noch kein KI-Dienst eingerichtet: Einstellungen → KI und Internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    asking?.let { m ->
        var read by remember(m) { mutableStateOf(false) }
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { asking = null },
            title = { Text("„${m.title}“ einschalten?") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.level, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(m.short, style = MaterialTheme.typography.bodyMedium)
                    Text("Hinweise und Risiken", style = MaterialTheme.typography.titleSmall)
                    m.risks.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text("Du kannst jederzeit zurück auf eine sicherere Stufe wechseln.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().selectable(selected = read, onClick = { read = !read }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = read, onCheckedChange = null)
                        Text("Ich habe die Hinweise gelesen und möchte das so.", style = MaterialTheme.typography.bodyMedium)
                    }
                    HintReader("ki-modus-${m.key}", auto = false) { "${m.title}. ${m.short} Hinweise und Risiken: " + m.risks.joinToString(" ") }
                }
            },
            confirmButton = {
                TextButton(enabled = read, onClick = {
                    AiMode.set(context, m)
                    mode = m
                    asking = null
                }) { Text("Einschalten") }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Abbrechen") } },
        )
    }
}

/**
 * KI-Aufgaben: welche Unterfunktionen KI nutzen, welcher Dienst je Aufgabe (mehrere freie Kontingente),
 * Verbrauch heute, Ausweichen bei Limit, Tageslimit für automatische Kommentare.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AiTaskSettings(name: String, auto: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val ready = remember(tick) { de.meinhelfer.tag.ai.AiProvider.entries.filter { de.meinhelfer.tag.ai.AiConfig.isReady(context, it) } }
    val standard = de.meinhelfer.tag.ai.AiConfig.provider(context)
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.HorizontalDivider()
    Row(Modifier.fillMaxWidth().selectable(selected = open, onClick = { open = !open }), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("KI-Aufgaben und Verbrauch", style = MaterialTheme.typography.labelLarge)
            val used = remember(tick) { de.meinhelfer.tag.ai.AiUsage.byProvider(context) }
            Text(
                if (used.isEmpty()) "Heute noch keine KI-Anfrage." else "Heute: " + used.entries.joinToString(", ") { (p, n) ->
                    p.label.substringBefore(" (") + " $n" + if (de.meinhelfer.tag.ai.AiUsage.limited(context, p)) " (Limit erreicht)" else ""
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
    }
    if (!open) return
    Text(
        "Jede Aufgabe kannst du einzeln ausschalten oder einem eigenen KI-Dienst geben – so verteilen sich die Anfragen auf mehrere kostenlose Kontingente (z. B. Kommentare an Groq, Dokumente an Gemini). Weitere Dienste richtest du unter „KI und Internet“ ein.",
        style = MaterialTheme.typography.bodySmall,
    )
    de.meinhelfer.tag.ai.AiTask.entries.filter { it != de.meinhelfer.tag.ai.AiTask.Allgemein }.forEach { t ->
        if (t == de.meinhelfer.tag.ai.AiTask.Seite && !auto) return@forEach
        var on by remember(t) { mutableStateOf(de.meinhelfer.tag.ai.AiTask.enabled(context, t)) }
        var chosen by remember(t) { mutableStateOf(de.meinhelfer.tag.ai.AiTask.provider(context, t)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title + " · heute " + de.meinhelfer.tag.ai.AiUsage.byTask(context, t), style = MaterialTheme.typography.bodyLarge)
                    Text(t.hint, style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = on, onCheckedChange = {
                    on = it
                    de.meinhelfer.tag.ai.AiTask.setEnabled(context, t, it)
                })
            }
            if (on && ready.size > 1) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = chosen == null,
                        onClick = { chosen = null; de.meinhelfer.tag.ai.AiTask.setProvider(context, t, null) },
                        label = { Text("Standard (" + standard.label.substringBefore(" (") + ")") },
                    )
                    ready.forEach { p ->
                        androidx.compose.material3.FilterChip(
                            selected = chosen == p,
                            onClick = { chosen = p; de.meinhelfer.tag.ai.AiTask.setProvider(context, t, p) },
                            label = { Text(p.label.substringBefore(" (")) },
                        )
                    }
                }
            }
        }
    }
    var fallback by remember { mutableStateOf(de.meinhelfer.tag.ai.AiTask.fallback(context)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Bei Limit ausweichen", style = MaterialTheme.typography.bodyLarge)
            Text("Ist das Tageskontingent eines Dienstes aufgebraucht, nimmt $name automatisch einen anderen eingerichteten Dienst.", style = MaterialTheme.typography.bodySmall)
        }
        androidx.compose.material3.Switch(checked = fallback, onCheckedChange = {
            fallback = it
            de.meinhelfer.tag.ai.AiTask.setFallback(context, it)
        })
    }
    if (auto) {
        var limit by remember { mutableStateOf(de.meinhelfer.tag.ai.AiTask.autoLimit(context)) }
        Text("Automatische Kommentare höchstens pro Tag", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 40, 999).forEach { n ->
                androidx.compose.material3.FilterChip(
                    selected = limit == n,
                    onClick = { limit = n; de.meinhelfer.tag.ai.AiTask.setAutoLimit(context, n) },
                    label = { Text(if (n == 999) "ohne Grenze" else "$n") },
                )
            }
        }
    }
    TextButton(onClick = { tick++ }) { Text("Verbrauch aktualisieren") }
    Text(
        "Gezählt wird nur auf dem Handy. Die echten Grenzen legt jeder Dienst selbst fest und ändert sie manchmal; der Zähler beginnt jeden Tag neu.",
        style = MaterialTheme.typography.bodySmall,
    )
}
