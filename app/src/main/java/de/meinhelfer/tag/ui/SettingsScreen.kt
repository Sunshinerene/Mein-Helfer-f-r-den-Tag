package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RadioButton
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
import androidx.compose.material3.FilterChip
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
import de.meinhelfer.tag.ai.AiClient
import de.meinhelfer.tag.ai.AiConfig
import de.meinhelfer.tag.ai.AiProvider
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import de.meinhelfer.tag.update.RoadmapKind
import de.meinhelfer.tag.update.RoadmapLine
import de.meinhelfer.tag.update.UpdateChecker
import de.meinhelfer.tag.update.UpdateState
import de.meinhelfer.tag.update.UpdateWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenGuide: () -> Unit, onStartTour: () -> Unit) {
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
            item { HelpCard(onOpenGuide, onStartTour) }
            item { ThemeCard(vm) }
            item { MedInfoCard() }
            item { AiCard() }
            item { FeedbackCard(context) }
            item { RoadmapCard(roadmap, roadmapError) }
            item { AboutCard(context) }
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
    HCard(Modifier.fillMaxWidth()) {
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
    HCard(Modifier.fillMaxWidth()) {
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
    HCard(Modifier.fillMaxWidth()) {
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

@Composable
private fun ThemeCard(vm: MainViewModel) {
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val style by vm.designStyle.collectAsStateWithLifecycle()
    val darkNow = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Darstellung", style = MaterialTheme.typography.titleMedium)
            Text("Hell oder dunkel", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Wie das Handy", "light" to "Hell", "dark" to "Dunkel").forEach { (key, label) ->
                    FilterChip(selected = mode == key, onClick = { vm.setThemeMode(key) }, label = { Text(label) })
                }
            }
            Text("Design", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            DesignStyle.entries.forEach { option ->
                val colors = designSpec(option, darkNow).preview
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setDesignStyle(option) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = style == option, onClick = null)
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        colors.forEach { color ->
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .background(color, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                        }
                    }
                    Column {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        Text(option.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MedInfoCard() {
    val context = LocalContext.current
    var auto by remember { mutableStateOf(AppPrefs.medInfoAuto(context)) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Medikamenten-Beschreibung", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = auto, onCheckedChange = {
                    auto = it
                    AppPrefs.setMedInfoAuto(context, it)
                })
                Text(
                    if (auto) "Automatisch laden" else "Nur auf Knopfdruck laden",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Beim Anlegen oder Bearbeiten eines Medikaments sucht die App im Internet (Wikipedia) eine kurze Beschreibung. Aus: nur nach Tipp auf den Knopf. An: automatisch, kurz nachdem du den Namen getippt hast. In beiden Fällen wird nur der Medikamentenname gesendet. Die Beschreibung ist nur ein Anhaltspunkt und ersetzt nie Arzt oder Apotheke.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AiCard() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }
    var key by remember { mutableStateOf(AiConfig.apiKey(context, provider)) }
    var model by remember { mutableStateOf(AiConfig.model(context, provider)) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val storageOk = remember { AiConfig.storageAvailable(context) }

    fun choose(p: AiProvider) {
        provider = p
        AiConfig.setProvider(context, p)
        key = AiConfig.apiKey(context, p)
        model = AiConfig.model(context, p)
        testResult = null
    }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KI-Assistent (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Die KI liefert Anhaltspunkte, z. B. „Medikamentenplan vom Hausarzt prüfen lassen“. Sie ist kein Arzt und kein Apotheker. " +
                    "Du wählst den Dienst und hinterlegst deinen eigenen Schlüssel (API-Key). Gesendet wird nur nach deiner Bestätigung und immer ohne Namen der Person. " +
                    "Ein Abo bei ChatGPT oder Claude reicht dafür nicht, es braucht einen API-Schlüssel des jeweiligen Anbieters.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!storageOk) {
                Text(
                    "Der verschlüsselte Speicher ist auf diesem Handy nicht verfügbar, deshalb können keine Schlüssel gespeichert werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text("Kostenlos nutzbar", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.filter { it.free }.forEach { p ->
                    FilterChip(selected = provider == p, onClick = { choose(p) }, label = { Text(p.label) })
                }
            }
            Text("Kostenpflichtig", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.filter { !it.free }.forEach { p ->
                    FilterChip(selected = provider == p, onClick = { choose(p) }, label = { Text(p.label) })
                }
            }
            Text(provider.note, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    AiConfig.setApiKey(context, provider, it)
                    testResult = null
                },
                label = { Text("API-Schlüssel für ${provider.label}") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                enabled = storageOk,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    AiConfig.setModel(context, provider, it)
                    testResult = null
                },
                label = { Text("Modell") },
                singleLine = true,
                enabled = storageOk,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Der Modellname ist vorausgefüllt. Falls der Anbieter ihn ändert, trage hier den neuen Namen ein. Der Schlüssel wird verschlüsselt nur auf diesem Handy gespeichert.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { openUrl(context, provider.keyUrl) }) { Text("Schlüssel besorgen") }
                Button(
                    enabled = key.isNotBlank() && !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            AiClient.ask(context, "Antworte nur mit dem Wort OK.", "Test")
                                .onSuccess { testResult = "Verbindung klappt." }
                                .onFailure { testResult = it.message ?: "Das hat nicht geklappt." }
                            testing = false
                        }
                    },
                ) { Text(if (testing) "Teste …" else "Testen") }
            }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun HelpCard(onOpenGuide: () -> Unit, onStartTour: () -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hilfe", style = MaterialTheme.typography.titleMedium)
            Text("Die Anleitung erklärt alles nach Themen, der Rundgang zeigt es Schritt für Schritt mit Bildern.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenGuide) { Text("Anleitung lesen") }
                Button(onClick = onStartTour) { Text("Rundgang") }
            }
        }
    }
}

@Composable
private fun AboutCard(context: Context) {
    HCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppLogo(64.dp)
            Column {
                Text("Mein Helfer für den Tag", style = MaterialTheme.typography.titleMedium)
                Text("Version ${UpdateChecker.installedVersion(context)}", style = MaterialTheme.typography.bodyMedium)
                Text("by $AUTHOR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
