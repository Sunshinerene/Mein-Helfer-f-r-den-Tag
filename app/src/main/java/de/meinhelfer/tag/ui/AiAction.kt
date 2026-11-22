package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import de.meinhelfer.tag.ai.AiLimitException
import de.meinhelfer.tag.ai.AiProvider
import kotlinx.coroutines.launch

/**
 * Knopf fuer eine KI-Anfrage. Gesendet wird erst nach Bestaetigung; vorher sieht man den genauen Text.
 * [onAnswer] wertet die Antwort aus und gibt false zurueck, wenn das nicht geklappt hat.
 */
@Composable
fun AiActionButton(
    label: String,
    note: String,
    system: String,
    prompt: () -> String,
    onAnswer: (answer: String, provider: String) -> Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    maxTokens: Int = 1500,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var limitHit by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }
    val configured = AiConfig.isConfigured(context)
    val otherReady = AiProvider.entries.any { it != provider && AiConfig.isReady(context, it) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!configured && !otherReady) {
            Text(
                "KI nicht eingerichtet. Unter Einstellungen → KI und Internet kannst du sie einrichten (z. B. kostenlos mit Google Gemini). Oder teile den Text an eine KI-App auf deinem Handy.",
                style = MaterialTheme.typography.bodySmall,
            )
            AiShareButton(system, text = prompt)
            return@Column
        }
        if (!loading) {
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
                Text("${provider.label.substringBefore(" (")} denkt nach …", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (AiConfig.isConfigured(context)) {
            if (filled) {
                Button(shape = buttonShape(), onClick = { confirmText = prompt() }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            } else {
                OutlinedButton(shape = buttonShape(), onClick = { confirmText = prompt() }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        if (!loading) AiShareButton(system, text = prompt)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (limitHit) {
            Text(
                if (otherReady) "Wähle oben einen anderen KI-Dienst und sende erneut."
                else "Unter Einstellungen → KI und Internet kannst du einen weiteren Dienst einrichten und hier umschalten.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    confirmText?.let { text ->
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirmText = null },
            title = { Text("An ${provider.label.substringBefore(" (")} senden?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Genau dieser Text wird über das Internet an den Dienst gesendet. $note", style = MaterialTheme.typography.bodySmall)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmText = null
                    loading = true
                    error = null
                    limitHit = false
                    val used = provider
                    scope.launch {
                        AiClient.ask(context, system, text, maxTokens)
                            .onSuccess { answer ->
                                if (!onAnswer(answer, used.label.substringBefore(" ("))) {
                                    error = "Die Antwort der KI konnte nicht ausgewertet werden. Versuche es nochmal oder wähle ein anderes Modell."
                                }
                            }
                            .onFailure {
                                error = it.message ?: "Das hat nicht geklappt."
                                limitHit = it is AiLimitException
                            }
                        loading = false
                    }
                }) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { confirmText = null }) { Text("Abbrechen") } },
        )
    }
}

/**
 * Gibt den vorbereiteten Text (ohne Namen) ueber das Android-Teilen an eine KI-App weiter, z. B. Gemini oder ChatGPT.
 * Die Antwort bleibt in der anderen App; die Vorgaben fuer JSON werden dafuer durch eine Bitte um lesbaren Text ersetzt.
 */
internal fun shareToAiApp(context: Context, system: String, text: String) {
    val rules = system.split(Regex("(?<=[.!?])\\s+")).filterNot { it.contains("JSON", ignoreCase = true) }.joinToString(" ")
    val body = buildString {
        if (rules.isNotBlank()) {
            appendLine(rules)
            appendLine()
        }
        appendLine(text.trim())
        appendLine()
        append("Bitte antworte in einfachem, gut lesbarem Deutsch mit kurzen Abschnitten oder Stichpunkten, nicht als JSON. Ignoriere dafür die Formatvorgaben oben.")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(Intent.createChooser(send, "An KI-App teilen"))
    } catch (_: Exception) {
    }
}

/** Kleiner Knopf: Text erst zeigen, dann an eine KI-App auf dem Handy teilen. Geht auch ohne eingerichteten KI-Dienst. */
@Composable
fun AiShareButton(system: String, label: String = "An KI-App teilen (z. B. Gemini)", text: () -> String) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { preview = text() }) { Text(label) }
    preview?.let { t ->
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { preview = null },
            title = { Text("An KI-App teilen?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dieser Text wird an die App übergeben, die du gleich auswählst (z. B. Gemini oder ChatGPT). Die Antwort erscheint dort, nicht hier; was die App mit dem Text macht, bestimmt ihr Anbieter.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(t, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    shareToAiApp(context, system, t)
                }) { Text("Teilen") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Abbrechen") } },
        )
    }
}
