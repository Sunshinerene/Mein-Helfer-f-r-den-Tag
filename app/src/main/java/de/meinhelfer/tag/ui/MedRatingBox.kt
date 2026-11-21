package de.meinhelfer.tag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import de.meinhelfer.tag.ai.MedRating
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.info.MedicationInfo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val LEVEL_WORDS = listOf("nicht beurteilt", "gering", "eher gering", "mittel", "eher hoch", "hoch")

/** Skala mit fuenf Feldern, nur als grobe Orientierung. */
@Composable
private fun ScaleRow(label: String, level: Int, emptyText: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (level == 0) (emptyText ?: LEVEL_WORDS[0]) else "${LEVEL_WORDS[level]} ($level/5)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val fill = when {
            level >= 4 -> MaterialTheme.colorScheme.error
            level == 3 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
        val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 1..5) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(if (i <= level) fill else empty, RoundedCornerShape(5.dp)),
                )
            }
        }
    }
}

/**
 * Optionale KI-Einschaetzung zu einem Medikament (Staerke, Nebenwirkungen, Wechselwirkungen mit den anderen
 * Medikamenten der Person). Gesendet wird erst nach Bestaetigung, ohne Namen der Person.
 */
@Composable
fun MedRatingBox(med: Medication, otherNames: List<String>, onUpdate: (Medication) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var limitHit by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }

    val rating = remember(med.rating) { MedRating.fromStored(med.rating) }
    val configured = AiConfig.isConfigured(context)
    val otherReady = AiProvider.entries.any { it != provider && AiConfig.apiKey(context, it).isNotBlank() }
    val medName = MedicationInfo.cleanName(med.name).ifBlank { med.name }
    val others = otherNames.map { MedicationInfo.cleanName(it).ifBlank { it } }.distinct()
    val promptText = MedRating.prompt(medName, others)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rating?.let { r ->
            ScaleRow("Wirkstärke", r.strength)
            ScaleRow("Nebenwirkungen", r.sideEffects)
            ScaleRow("Wechselwirkungen mit den übrigen", r.interactions, emptyText = "keine weiteren Medikamente")
            r.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            val date = runCatching { LocalDate.ofEpochDay(r.day).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault("")
            Text(
                "Stand $date" + (if (r.provider.isNotBlank()) ", ${r.provider}" else "") +
                    ". Ändert sich der Medikamentenplan, bitte neu einschätzen lassen.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!configured && !otherReady) {
            Text(
                "KI nicht eingerichtet. Du kannst sie unter Einstellungen → KI-Assistent einrichten (z. B. kostenlos mit Google Gemini).",
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
            OutlinedButton(onClick = { confirm = true }) {
                Text(if (rating == null) "Einschätzung per KI holen" else "Neu einschätzen lassen")
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (limitHit) {
            Text(
                if (otherReady) "Wähle oben einen anderen KI-Dienst und sende erneut."
                else "Du kannst unter Einstellungen → KI-Assistent einen weiteren Dienst einrichten und dann hier umschalten.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Grobe Orientierung der KI, kein Urteil. Dosis, Alter, Vorerkrankungen und das genaue Präparat sind nicht berücksichtigt. " +
                "Ob ein Medikament passt, entscheiden Arzt oder Apotheke. Frag dort nach.",
            style = MaterialTheme.typography.bodySmall,
        )
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
                        "Genau dieser Text wird über das Internet an den Dienst gesendet. Der Name der Person ist nicht enthalten, " +
                            "aber die Namen der Medikamente (für die Wechselwirkungen).",
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
                    limitHit = false
                    scope.launch {
                        val used = provider
                        AiClient.ask(context, MedRating.SYSTEM_PROMPT, promptText)
                            .onSuccess { answer ->
                                val parsed = MedRating.parse(answer, LocalDate.now().toEpochDay(), used.label.substringBefore(" ("))
                                if (parsed != null) onUpdate(med.copy(rating = parsed.toJson()))
                                else error = "Die Antwort der KI konnte nicht ausgewertet werden. Versuche es nochmal oder wähle ein anderes Modell."
                            }
                            .onFailure {
                                error = it.message ?: "Das hat nicht geklappt."
                                limitHit = it is AiLimitException
                            }
                        loading = false
                    }
                }) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}
