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
import de.meinhelfer.tag.ai.AiClient
import de.meinhelfer.tag.ai.AiConfig
import kotlinx.coroutines.launch

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
    val added = remember { mutableStateListOf<String>() }

    val configured = AiConfig.isConfigured(context)
    val provider = AiConfig.provider(context)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!configured) {
            Text(
                "KI nicht eingerichtet. Du kannst sie unter Einstellungen → KI-Assistent einrichten (z. B. kostenlos mit Google Gemini).",
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("${provider.label} denkt nach …", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            OutlinedButton(onClick = { confirm = true }) { Text(buttonLabel) }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        result?.let { bullets ->
            bullets.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyMedium)
                val isAdded = line in added
                TextButton(onClick = {
                    onTodo(line)
                    added.add(line)
                }, enabled = !isAdded) {
                    Text(if (isAdded) "✓ Auf der To-do-Liste" else "+ Auf meine To-do-Liste")
                }
            }
            Text(AiClient.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirm) {
        AlertDialog(
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
                    Text(promptText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    loading = true
                    error = null
                    result = null
                    scope.launch {
                        AiClient.ask(context, AiClient.SYSTEM_PROMPT, promptText)
                            .onSuccess { result = AiClient.bullets(it) }
                            .onFailure { error = it.message ?: "Das hat nicht geklappt." }
                        loading = false
                    }
                }) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}
