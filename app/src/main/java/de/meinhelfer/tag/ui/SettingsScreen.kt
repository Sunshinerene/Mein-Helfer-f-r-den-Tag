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
import de.meinhelfer.tag.security.AppLock
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
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onStartTour: () -> Unit,
    caregiver: de.meinhelfer.tag.data.CaregiverProfile = de.meinhelfer.tag.data.CaregiverProfile(),
    onEditCaregiver: () -> Unit = {},
    onConsentRevoked: () -> Unit = {},
) {
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
            item { SectionTitle("Allgemein") }
            item { CaregiverCard(caregiver, onEditCaregiver) }
            item { PermissionsCard() }
            item { ThemeCard(vm) }
            item { HomeCardsCard() }
            item { IntakeCard() }
            item { ViewerCard() }
            item { SectionTitle("Daten und Sicherheit") }
            item { SecurityCard() }
            item { BackupCard() }
            item { ConsentCard(onRevoked = onConsentRevoked) }
            item { SectionTitle("Hilfen aus dem Internet (freiwillig)") }
            item { MedInfoCard() }
            item { AiCard() }
            item { SectionTitle("Hilfe und Rückmeldung") }
            item { HelpCard(onOpenGuide, onStartTour) }
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ThemeCard(vm: MainViewModel) {
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val style by vm.designStyle.collectAsStateWithLifecycle()
    val tweaks by vm.designTweaks.collectAsStateWithLifecycle()
    var tweaking by remember { mutableStateOf(false) }
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
            Text("Schriftgröße", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            val fontFactor by vm.fontFactor.collectAsStateWithLifecycle()
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "Klein", 1f to "Normal", 1.15f to "Groß", 1.3f to "Sehr groß", 1.5f to "Riesig").forEach { (f, label) ->
                    FilterChip(selected = kotlin.math.abs(fontFactor - f) < 0.01f, onClick = { vm.setFontFactor(f) }, label = { Text(label) })
                }
            }
            Text("So groß wird die Schrift in der ganzen App. Sie gilt zusätzlich zur Schriftgröße des Handys.", style = MaterialTheme.typography.bodySmall)
            Text("Design", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            DesignStyle.entries.forEach { option ->
                val colors = tweakedSpec(option, darkNow, tweaks[option]).preview
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
            OutlinedButton(onClick = { tweaking = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Farben von „${style.label}“ anpassen" + if (tweaks[style]?.isEmpty == false) " (geändert)" else "")
            }
        }
    }
    if (tweaking) {
        ColorTweakDialog(
            style = style,
            tweak = tweaks[style] ?: DesignTweak(),
            onChange = { vm.setDesignTweak(style, it) },
            onClose = { tweaking = false },
        )
    }
}

@Composable
private fun HomeCardsCard() {
    val context = LocalContext.current
    var emergency by remember { mutableStateOf(AppPrefs.betaEmergency(context)) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Heute-Seite", style = MaterialTheme.typography.titleMedium)
            Text(
                "Karten auf der Heute-Seite lassen sich dort direkt ein- und aufklappen. Zusätzlich:",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = emergency, onCheckedChange = {
                    emergency = it
                    AppPrefs.setBetaEmergency(context, it)
                })
                Column(Modifier.weight(1f)) {
                    Text("Notfallkarte auf der Heute-Seite", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Name, Geburtsdatum, Pflegegrad, Wichtiges, Medikamente, Kontakte und Ansprechperson auf einen Blick. Über das rote Telefon oben ist sie immer erreichbar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            var dailyStart by remember { mutableStateOf(AppPrefs.dailyStartEnabled(context)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = dailyStart, onCheckedChange = {
                    dailyStart = it
                    AppPrefs.setDailyStartEnabled(context, it)
                })
                Column(Modifier.weight(1f)) {
                    Text("Tagesstart automatisch", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Beim ersten Öffnen am Tag: Rückblick auf gestern zum Ergänzen und Bestätigen, danach der Tagesplan für heute (als PDF teilbar). Auf der Heute-Seite jederzeit über „Rückblick gestern“ und „Tagesplan“.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IntakeCard() {
    val context = LocalContext.current
    var window by remember { mutableStateOf(AppPrefs.intakeWindow(context)) }
    var followup by remember { mutableStateOf(AppPrefs.followupNotify(context)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Einnahmen abhaken", style = MaterialTheme.typography.titleMedium)
            Text(
                "Medikamente werden nur rund um die Einnahmezeit normal abgehakt. Davor und danach fragt die App nach, wie es lief (früher oder später genommen, vergessen, bewusst nicht gegeben). Das macht die Auswertung genauer.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Zeitfenster vor und nach der Einnahmezeit", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 Minuten", 60 to "1 Stunde", 120 to "2 Stunden").forEach { (minutes, label) ->
                    FilterChip(selected = window == minutes, onClick = {
                        window = minutes
                        AppPrefs.setIntakeWindow(context, minutes)
                    }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = followup, onCheckedChange = {
                    followup = it
                    AppPrefs.setFollowupNotify(context, it)
                    if (it) {
                        scope.launch { de.meinhelfer.tag.reminder.ReminderScheduler.rescheduleAll(context) }
                    }
                })
                Text(
                    "Benachrichtigen, wenn eine Rückfrage offen bleibt",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Wurde nach dem Zeitfenster nichts abgehakt, erinnert die App einmal und fragt nach, wie es lief.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SecurityCard() {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    var lockOn by remember { mutableStateOf(AppPrefs.lockEnabled(context)) }
    var grace by remember { mutableStateOf(AppPrefs.lockGraceSeconds(context)) }
    var protectScreen by remember { mutableStateOf(AppPrefs.screenProtected(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sicherheit", style = MaterialTheme.typography.titleMedium)
            val dbEncrypted = remember { de.meinhelfer.tag.data.DbCrypto.isEncrypted(context) }
            Text(
                if (dbEncrypted) "Datenbank: verschlüsselt (SQLCipher). Der Schlüssel liegt im Schlüsselspeicher des Handys."
                else "Datenbank: nicht verschlüsselt (die Umstellung war auf diesem Gerät nicht möglich).",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = lockOn, onCheckedChange = { wanted ->
                    message = null
                    if (activity == null) return@Switch
                    if (wanted && !AppLock.available(context)) {
                        message = "Lege zuerst in den Handy-Einstellungen eine Bildschirmsperre fest (PIN, Muster, Fingerabdruck oder Gesicht)."
                        return@Switch
                    }
                    // Zur Sicherheit erst bestätigen lassen, bevor die Sperre geändert wird
                    AppLock.authenticate(activity, if (wanted) "App-Sperre einschalten" else "App-Sperre ausschalten") { ok ->
                        if (ok) {
                            lockOn = wanted
                            AppPrefs.setLockEnabled(context, wanted)
                        }
                    }
                })
                Text(if (lockOn) "App-Sperre ist an" else "App-Sperre ist aus", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            Text(
                "Beim Öffnen der App fragt das Handy nach Fingerabdruck, Gesicht oder PIN. Die App speichert selbst keine PIN, es gilt die Sperre deines Handys.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (lockOn) {
                Text("Wieder sperren nach", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Sofort", 60 to "1 Minute", 300 to "5 Minuten").forEach { (seconds, label) ->
                        FilterChip(selected = grace == seconds, onClick = {
                            grace = seconds
                            AppPrefs.setLockGraceSeconds(context, seconds)
                        }, label = { Text(label) })
                    }
                }
                Text("Zeit, die die App im Hintergrund sein darf, bevor sie wieder gesperrt ist.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = protectScreen, onCheckedChange = {
                    protectScreen = it
                    AppPrefs.setScreenProtected(context, it)
                    activity?.window?.let { w ->
                        if (it) w.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                })
                Text("Bildschirmfotos und App-Vorschau sperren", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            Text(
                "Dann zeigt die Liste der zuletzt benutzten Apps keinen Inhalt, und Bildschirmfotos sind nicht möglich. Schalte es aus, wenn du Bildschirmfotos zum Feedback senden willst.",
                style = MaterialTheme.typography.bodySmall,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
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
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsMessage by remember { mutableStateOf<String?>(null) }
    val storageOk = remember { AiConfig.storageAvailable(context) }

    fun choose(p: AiProvider) {
        provider = p
        AiConfig.setProvider(context, p)
        key = AiConfig.apiKey(context, p)
        model = AiConfig.model(context, p)
        testResult = null
        models = emptyList()
        modelsMessage = null
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
                    FilterChip(selected = provider == p, onClick = { choose(p) }, label = { Text(p.label + if (AiConfig.apiKey(context, p).isNotBlank()) " ✓" else "") })
                }
            }
            Text("Kostenpflichtig", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.filter { !it.free }.forEach { p ->
                    FilterChip(selected = provider == p, onClick = { choose(p) }, label = { Text(p.label + if (AiConfig.apiKey(context, p).isNotBlank()) " ✓" else "") })
                }
            }
            Text("Ein Haken ✓ zeigt, dass für diesen Dienst ein Schlüssel hinterlegt ist. Bei mehreren eingerichteten Diensten kannst du direkt in der App (bei den KI-Hinweisen) schnell wechseln.", style = MaterialTheme.typography.bodySmall)
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
            if (models.isNotEmpty()) {
                DropdownField(
                    label = "Modell",
                    value = model,
                    options = models,
                    onValueChange = {
                        model = it
                        AiConfig.setModel(context, provider, it)
                        testResult = null
                    },
                )
            } else {
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
            }
            OutlinedButton(
                enabled = key.isNotBlank() && !loadingModels,
                onClick = {
                    loadingModels = true
                    modelsMessage = null
                    scope.launch {
                        AiClient.listModels(provider, key)
                            .onSuccess {
                                models = it
                                modelsMessage = if (it.isEmpty()) "Keine Modelle gefunden." else "${it.size} Modelle gefunden. Tippe auf das Feld „Modell“, um eines auszuwählen."
                            }
                            .onFailure { modelsMessage = it.message ?: "Die Liste konnte nicht geladen werden." }
                        loadingModels = false
                    }
                },
            ) { Text(if (loadingModels) "Lade …" else "Verfügbare Modelle laden") }
            modelsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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


@Composable
private fun ViewerCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var internal by remember { mutableStateOf(AppPrefs.internalViewer(context)) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Dokumente ansehen", style = MaterialTheme.typography.titleMedium)
            listOf(
                true to "In der App (Betrachter mit Zoom und Drucken)",
                false to "Mit einer anderen App auf dem Handy",
            ).forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        internal = value
                        AppPrefs.setInternalViewer(context, value)
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = internal == value, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "Der eingebaute Betrachter zeigt PDF, Fotos und Text. Andere Formate (z. B. Word) öffnen immer mit einer anderen App.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
