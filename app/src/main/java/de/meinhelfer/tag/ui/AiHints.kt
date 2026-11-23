package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import de.meinhelfer.tag.ai.AiLimitException
import de.meinhelfer.tag.ai.AiProvider
import de.meinhelfer.tag.ai.AiClient
import de.meinhelfer.tag.ai.AiConfig
import kotlinx.coroutines.launch

/** Schnellwahl: zwischen allen eingerichteten KI-Diensten (mit Schluessel) wechseln. Erst ab zwei Diensten sichtbar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiProviderSwitch(selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    val context = LocalContext.current
    val usable = AiProvider.entries.filter { AiConfig.isReady(context, it) }
    if (usable.isEmpty() || (usable.size < 2 && selected in usable)) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("KI-Dienst wechseln", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            usable.forEach { p ->
                FilterChip(
                    selected = p == selected,
                    onClick = { onSelect(p) },
                    label = { Text(p.label.substringBefore(" (")) },
                )
            }
        }
    }
}

/**
 * KI-Hinweis als Anhaltspunkt. Gesendet wird erst nach Bestaetigung, und der Nutzer sieht vorher
 * den genauen Text. Antworten lassen sich als To-do uebernehmen ("beim Hausarzt ansprechen").
 */
@Composable
fun AiHintBox(
    buttonLabel: String,
    promptText: String,
    onTodo: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var limitHit by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }
    val added = remember { mutableStateListOf<String>() }

    val configured = !de.meinhelfer.tag.ai.AiMode.isOff(context) && AiConfig.isConfigured(context)
    val otherReady = !de.meinhelfer.tag.ai.AiMode.isOff(context) && AiProvider.entries.any { it != provider && AiConfig.isReady(context, it) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!configured) {
            Text(
                if (de.meinhelfer.tag.ai.AiMode.isOff(context)) de.meinhelfer.tag.ai.AiMode.OFF_TEXT else "KI nicht eingerichtet. Du kannst sie unter Einstellungen → KI und Internet einrichten (z. B. kostenlos mit Google Gemini).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if ((configured || otherReady) && !loading) {
            AiProviderSwitch(selected = provider) {
                provider = it
                AiConfig.setProvider(context, it)
                error = null
                limitHit = false
            }
        }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("${provider.label} denkt nach …", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (configured) {
            OutlinedButton(shape = buttonShape(), onClick = { confirm = true }) { Text(buttonLabel) }
        }
        if (!loading) AiShareButton(AiClient.SYSTEM_PROMPT) { promptText }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (limitHit) {
            Text(
                if (otherReady) "Wähle oben einen anderen KI-Dienst und sende erneut."
                else "Du kannst unter Einstellungen → KI und Internet einen weiteren Dienst einrichten (z. B. Groq oder Mistral, kostenlos) und dann hier umschalten.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        result?.let { bullets ->
            bullets.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyMedium)
                val isAdded = line in added
                TextButton(onClick = {
                    onTodo(line)
                    added.add(line)
                }, enabled = !isAdded) {
                    Text(if (isAdded) "✓ Als Aufgabe gemerkt" else "+ Als Aufgabe merken")
                }
            }
            Text(AiClient.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
            HintReader("ki-${bullets.hashCode()}") { bullets.joinToString(" ") + " " + AiClient.DISCLAIMER }
        }
    }

    if (confirm) {
        val send0: () -> Unit = {
                    confirm = false
                    loading = true
                    error = null
                    limitHit = false
                    result = null
                    scope.launch {
                        AiClient.ask(context, AiClient.SYSTEM_PROMPT, promptText)
                            .onSuccess { result = AiClient.bullets(it) }
                            .onFailure {
                                error = it.message ?: "Das hat nicht geklappt."
                                limitHit = it is AiLimitException
                            }
                        loading = false
                    }
                }

        if (de.meinhelfer.tag.ai.AiMode.skipConfirm(context)) {
            androidx.compose.runtime.LaunchedEffect(Unit) { send0() }
        } else AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirm = false },
            title = { Text("An ${provider.label} senden?") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Genau dieser Text wird über das Internet an den Dienst gesendet. Der Name der Person ist nicht enthalten.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(de.meinhelfer.tag.summary.Anonymizer.scrub(promptText), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = send0) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}
