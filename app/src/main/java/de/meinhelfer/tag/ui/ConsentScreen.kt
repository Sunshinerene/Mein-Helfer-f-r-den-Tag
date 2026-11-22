package de.meinhelfer.tag.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.meinhelfer.tag.data.CaregiverRole
import de.meinhelfer.tag.data.ConsentRecord
import de.meinhelfer.tag.legal.ConsentStore
import de.meinhelfer.tag.legal.Terms
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CONSENT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY)

internal fun consentTime(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(CONSENT_TIME)

internal fun roleLabel(key: String): String = CaregiverRole.fromKey(key)?.label ?: key.ifBlank { "–" }

@Composable
private fun TermsBody() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fassung ${Terms.VERSION}, Stand ${Terms.STAND}", style = MaterialTheme.typography.bodySmall)
        Terms.SECTIONS.forEach { (head, body) ->
            Text(head, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Pflicht-Zustimmung vor der Nutzung: Hinweise lesen, drei Punkte einzeln bestaetigen, Name und Rolle angeben.
 * Ohne Zustimmung laesst sich die App nicht nutzen ("Ablehnen" schliesst sie).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConsentScreen(
    onAccepted: () -> Unit,
    onDecline: () -> Unit,
    reason: ConsentStore.Status = ConsentStore.Status.FIRST,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cg = remember { AppPrefs.caregiver(context) }
    var name by remember { mutableStateOf(cg.name) }
    var role by remember { mutableStateOf(cg.role) }
    val checked = remember { mutableStateListOf(*Array(Terms.CONFIRMATIONS.size) { false }) }
    var saving by remember { mutableStateOf(false) }
    val ready = name.trim().length >= 2 && role.isNotBlank() && checked.all { it } && !saving

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Bevor es losgeht", style = MaterialTheme.typography.headlineSmall)
        Text(
            when (reason) {
                ConsentStore.Status.NEW_VERSION -> "Die Nutzungsbedingungen haben sich geändert. Bitte lies sie und stimme erneut zu."
                ConsentStore.Status.REVOKED -> "Deine Zustimmung wurde widerrufen. Ohne Zustimmung kann die App nicht genutzt werden."
                ConsentStore.Status.CAREGIVER_CHANGED -> "Die pflegende Person (Name oder Rolle) hat sich geändert. Die Zustimmung gilt immer für eine Person, deshalb bitte erneut bestätigen."
                ConsentStore.Status.RESET -> "Die App wurde zurückgesetzt. Bitte lies die Hinweise und stimme erneut zu."
                else -> "Bitte lies die wichtigen Hinweise und bestätige sie. Ohne diese Bestätigung kann die App nicht genutzt werden."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (reason == ConsentStore.Status.CAREGIVER_CHANGED || reason == ConsentStore.Status.NEW_VERSION) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { TermsBody() }
        }

        Text("Wer stimmt zu?", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Dein Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Deine Rolle", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaregiverRole.entries.forEach { r ->
                FilterChip(selected = role == r.key, onClick = { role = r.key }, label = { Text(r.label) })
            }
        }

        Text("Bitte einzeln bestätigen", style = MaterialTheme.typography.titleMedium)
        Terms.CONFIRMATIONS.forEachIndexed { i, text ->
            Row(
                Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(shape = buttonShape(), 
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                saving = true
                scope.launch {
                    ConsentStore.accept(context, name, role)
                    // Name und Rolle gelten auch fuer "Meine Daten" (die Zustimmung gehoert zu genau dieser Person)
                    val current = AppPrefs.caregiver(context)
                    AppPrefs.setCaregiver(context, current.copy(name = name.trim(), role = role))
                    saving = false
                    onAccepted()
                }
            },
        ) { Text("Zustimmen und App nutzen") }
        OutlinedButton(shape = buttonShape(), onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Ablehnen und schließen") }
        Text(
            "Deine Zustimmung wird mit Name, Rolle, Zeitpunkt, App-Version, Gerät und einer Prüfsumme dieses Textes verschlüsselt auf diesem Handy gespeichert. " +
                "Es wird nichts übertragen.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Einstellungen: Stand der Zustimmung, Bedingungen lesen, Nachweis teilen, widerrufen. */
@Composable
internal fun ConsentCard(onRevoked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<ConsentRecord>>(emptyList()) }
    var showTerms by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { history = ConsentStore.history(context) }
    val current = history.lastOrNull()?.takeIf { it.action == ConsentRecord.ACCEPTED }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nutzungsbedingungen und Zustimmung", style = MaterialTheme.typography.titleMedium)
            if (current != null) {
                Text(
                    "Zugestimmt am ${consentTime(current.atMillis)} von ${current.name} (${roleLabel(current.role)}), Fassung ${current.termsVersion}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Der Nachweis liegt verschlüsselt auf diesem Handy. Frühere Nachweise werden bis zum Ende des dritten Kalenderjahres aufbewahrt und dann automatisch gelöscht.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = buttonShape(), onClick = { showTerms = true }) { Text("Lesen") }
                OutlinedButton(shape = buttonShape(), onClick = {
                    shareProof(context, ConsentStore.proofText(history, ::roleLabel))
                }) { Text("Nachweis teilen") }
            }
            TextButton(onClick = { confirmRevoke = true }) {
                Text("Zustimmung widerrufen", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showTerms) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { showTerms = false },
            title = { Text("Nutzungsbedingungen") },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { TermsBody() } },
            confirmButton = { TextButton(onClick = { showTerms = false }) { Text("Schließen") } },
        )
    }
    if (confirmRevoke) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirmRevoke = false },
            title = { Text("Zustimmung widerrufen?") },
            text = {
                Text(
                    "Ohne Zustimmung lässt sich die App nicht nutzen. Deine Daten bleiben erhalten. Du kannst jederzeit erneut zustimmen. " +
                        "Der Widerruf wird ebenfalls als Nachweis gespeichert.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = false
                    scope.launch {
                        val cg = AppPrefs.caregiver(context)
                        ConsentStore.revoke(context, current?.name ?: cg.name, current?.role ?: cg.role)
                        onRevoked()
                    }
                }) { Text("Widerrufen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text("Abbrechen") } },
        )
    }
}

private fun shareProof(context: Context, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nachweis der Zustimmung – Mein Helfer für den Tag")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Nachweis teilen"))
    } catch (_: Exception) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Nachweis", text))
        Toast.makeText(context, "Nachweis kopiert", Toast.LENGTH_SHORT).show()
    }
}
