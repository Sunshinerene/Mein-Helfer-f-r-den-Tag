package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.update.RoadmapKind
import de.meinhelfer.tag.update.RoadmapLine
import de.meinhelfer.tag.update.UpdateChecker
import de.meinhelfer.tag.update.UpdateState
import de.meinhelfer.tag.update.UpdateWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    var autoCheck by remember { mutableStateOf(AppPrefs.autoUpdateCheck(context)) }
    var roadmap by remember { mutableStateOf<List<RoadmapLine>?>(null) }
    var roadmapError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            roadmap = UpdateChecker.fetchRoadmap()
        } catch (_: Exception) {
            roadmapError = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
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
            item {
                UpdateCard(
                    state = updateState,
                    autoCheck = autoCheck,
                    onAutoCheckChange = { enabled ->
                        autoCheck = enabled
                        AppPrefs.setAutoUpdateCheck(context, enabled)
                        if (enabled) UpdateWorker.schedule(context) else UpdateWorker.cancel(context)
                    },
                    onCheck = { vm.checkForUpdate() },
                    onDownload = { vm.downloadUpdate() },
                    context = context,
                )
            }
            item { FeedbackCard(context) }
            item { RoadmapCard(roadmap, roadmapError) }
        }
    }
}

@Composable
private fun UpdateCard(
    state: UpdateState,
    autoCheck: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    context: Context,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App-Updates", style = MaterialTheme.typography.titleMedium)
            Text("Installierte Version: ${UpdateChecker.installedVersion(context)}")

            when (state) {
                UpdateState.Idle -> Text("Noch nicht geprüft.")
                UpdateState.Checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Suche nach Updates …")
                }
                UpdateState.UpToDate -> Text("Du hast die neueste Version.")
                is UpdateState.Available -> {
                    Text("Neue Version ${state.info.version} verfügbar", fontWeight = FontWeight.Bold)
                    if (state.info.notes.isNotBlank()) {
                        Text(state.info.notes.trim(), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onDownload) { Text("Herunterladen") }
                }
                is UpdateState.Downloading -> {
                    Text("Lade Version ${state.info.version} …")
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> {
                    Text("Version ${state.info.version} ist heruntergeladen.", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (UpdateChecker.canInstall(context)) {
                                UpdateChecker.install(context, state.file)
                            } else {
                                UpdateChecker.openInstallPermissionSettings(context)
                            }
                        },
                    ) { Text("Installieren") }
                    Text(
                        "Beim ersten Mal fragt Android, ob diese App Updates installieren darf. " +
                            "Erlauben, dann zurückgehen und erneut auf „Installieren“ tippen. " +
                            "Deine Daten bleiben beim Update erhalten.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is UpdateState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }

            OutlinedButton(
                onClick = onCheck,
                enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            ) { Text("Nach Updates suchen") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Regelmäßig im Hintergrund suchen und per Benachrichtigung informieren",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = autoCheck, onCheckedChange = onAutoCheckChange)
            }
        }
    }
}

@Composable
private fun FeedbackCard(context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Feedback", style = MaterialTheme.typography.titleMedium)
            Text("Fehler gefunden oder eine Idee? Dein Feedback hilft, die App gemeinsam weiterzuentwickeln.")
            Button(onClick = { openUrl(context, UpdateChecker.ISSUES_NEW_URL) }) {
                Text("Idee oder Fehler melden")
            }
            Text(
                "Hinweis: Meldungen sind öffentlich sichtbar. Bitte keine Namen, Diagnosen oder andere persönliche Daten eintragen.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RoadmapCard(roadmap: List<RoadmapLine>?, error: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Vorschau: das ist geplant", style = MaterialTheme.typography.titleMedium)
            when {
                error -> Text("Konnte nicht geladen werden (keine Internetverbindung?).")
                roadmap == null -> Text("Lade …")
                roadmap.isEmpty() -> Text("Noch keine Einträge.")
                else -> roadmap.forEach { line ->
                    when (line.kind) {
                        RoadmapKind.HEADING -> Text(
                            line.text,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        RoadmapKind.OPEN -> Text("•  ${line.text}", style = MaterialTheme.typography.bodyMedium)
                        RoadmapKind.DONE -> Text("✓  ${line.text}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // kein Browser installiert
    }
}
