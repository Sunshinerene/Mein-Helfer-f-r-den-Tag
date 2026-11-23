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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
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
    initialCategory: String? = null,
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

    var categoryKey by rememberSaveable { mutableStateOf(initialCategory) }
    var query by rememberSaveable { mutableStateOf("") }
    val category = SettingsCategory.entries.firstOrNull { it.name == categoryKey }
    androidx.activity.compose.BackHandler(enabled = category != null) { categoryKey = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.title ?: "Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { if (category != null) categoryKey = null else onBack() }) {
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
            when (category) {
                null -> {
                    val available = updateState is UpdateState.Available || updateState is UpdateState.Ready
                    if (available) {
                        item {
                            HCard(
                                Modifier.fillMaxWidth(),
                                onClick = { categoryKey = SettingsCategory.App.name },
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Text("Neue Version verfügbar. Tippen zum Aktualisieren.", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(40) },
                            placeholder = { Text("Einstellung suchen, z. B. Schrift, KI, Sicherung") },
                            singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val q = query.trim().lowercase()
                    val shown = SettingsCategory.entries.filter { c ->
                        q.isEmpty() || c.title.lowercase().contains(q) || c.keywords.split(" ").any { it.startsWith(q) || q.startsWith(it) && it.length >= 4 }
                    }
                    if (shown.isEmpty()) {
                        item { Text("Nichts gefunden. Versuch ein anderes Wort.", style = MaterialTheme.typography.bodyMedium) }
                    }
                    shown.forEach { c ->
                        item(key = c.name) {
                            CategoryRow(c, categorySummary(c, vm, caregiver, updateState, context)) { categoryKey = c.name }
                        }
                    }
                }
                SettingsCategory.Profil -> {
                    item { CaregiverCard(caregiver, onEditCaregiver) }
                    item { ConsentCard(onRevoked = onConsentRevoked) }
                }
                SettingsCategory.Assistent -> {
                    item { AssistantSettingsCard(vm) }
                    item {
                        Text(
                            "Wann Erinnerungen und Hinweise kommen und die Ruhezeit: Einstellungen → Erinnerungen und Freigaben. Ist der Assistent aus, zeigt die Heute-Seite wieder den Tages-Motivator.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                SettingsCategory.Darstellung -> {
                    item { ThemeCard(vm) }
                    item { AccessibilityCard(vm) }
                    item { EffectsCard(vm) }
                    item { DesignPresetsCard(vm) }
                    item { IntroCard() }
                }
                SettingsCategory.Startseite -> {
                    item { HomeCardsCard() }
                    item { MotivatorSettingsCard(vm) }
                    item { WidgetCard() }
                }
                SettingsCategory.Erinnerungen -> {
                    item { ReminderOverviewCard(vm) }
                    item { PermissionsCard() }
                    item { IntakeCard() }
                    item { CareReminderCard() }
                }
                SettingsCategory.Dokumente -> {
                    item { ViewerCard() }
                }
                SettingsCategory.Sicherheit -> {
                    item { SecurityCard() }
                    item { BackupCard() }
                }
                SettingsCategory.Internet -> {
                    item {
                        Text(
                            "Alles hier ist freiwillig. Ohne Einrichtung funktioniert die App vollständig, nur ohne Hilfen aus dem Internet.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item { AiCard() }
                    item { MedInfoCard() }
                }
                SettingsCategory.Hilfe -> {
                    item { HelpCard(onOpenGuide, onStartTour) }
                    item { FeedbackCard(context) }
                    item { SelfTestCard(vm) }
                }
                SettingsCategory.Beta -> {
                    item {
                        Text(
                            "Neue Funktionen in Erprobung. Sie können sich noch ändern; Rückmeldungen dazu helfen sehr (Hilfe und Rückmeldung → Feedback).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item { HealthConnectSettingsCard() }
                    item {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Weitere Beta-Funktionen", style = MaterialTheme.typography.titleMedium)
                                Text("Design „Aurora“ und „Effekte“: Einstellungen → Darstellung.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                SettingsCategory.App -> {
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
                    item { RoadmapCard(roadmap, roadmapError) }
                    item { AboutCard(context) }
                }
            }
        }
    }
}

/** Kategorien der Einstellungen; [keywords] fuer die Suche. */
private enum class SettingsCategory(val emoji: String, val title: String, val keywords: String) {
    Profil("👤", "Meine Daten und Zustimmung", "name adresse telefon rolle ausweis angehörige pflegedienst zustimmung nutzungsbedingungen widerruf nachweis"),
    Assistent("🌞", "Persönlicher Assistent", "assistent sunny name gesicht persönlich morgen abend überblick morgenüberblick vorschläge checkliste termin eigene werte schlaf schritte ruhepuls motivator helfer"),
    Darstellung("🎨", "Darstellung", "hell dunkel design farben schrift schriftgröße größe ecken effekte glas aurora liquid animation glanz transparenz einspieler video intro start logo ton barrierefreiheit kontrast vorlesen sprachausgabe bedienflächen größer zoom sehen"),
    Startseite("🏠", "Heute-Seite, Motivator und Widget", "heute karten notfallkarte tagesstart motivator stimmung smiley ziele widget startbildschirm"),
    Erinnerungen("🔔", "Erinnerungen und Freigaben", "benachrichtigung freigabe freigaben wecker alarm akku einnahme zeitfenster ton erinnerung trinken tagebuch pflegetagebuch ruhezeit nacht übersicht assistent morgen abend"),
    Dokumente("📄", "Dokumente", "betrachter viewer pdf drucken öffnen dokument"),
    Sicherheit("🔒", "Sicherheit und Sicherung", "app-sperre sperre fingerabdruck gesicht pin sicherung backup wiederherstellen zurücksetzen reset löschen"),
    Internet("🤖", "KI und Internet", "ki assistent schlüssel api gemini openai chatgpt claude ollama groq mistral wikipedia beschreibung internet"),
    Hilfe("💬", "Hilfe und Rückmeldung", "anleitung rundgang hilfe feedback fehler wunsch rückmeldung selbsttest test prüfen beispiel pdf datenbank"),
    Beta("🧪", "Beta-Funktionen", "beta uhr smartwatch fitnessband armband band waage blutdruckgerät health connect gesundheit verbinden fitbit garmin samsung withings omron aurora effekte"),
    App("ℹ️", "App und Updates", "update aktualisieren version neuigkeiten neu geplant über app"),
}

private fun categorySummary(
    c: SettingsCategory,
    vm: MainViewModel,
    caregiver: de.meinhelfer.tag.data.CaregiverProfile,
    updateState: UpdateState,
    context: Context,
): String = when (c) {
    SettingsCategory.Profil -> listOfNotNull(
        caregiver.name.takeIf { it.isNotBlank() } ?: "Noch nicht eingetragen",
        caregiver.roleValue?.label,
    ).joinToString(" · ")
    SettingsCategory.Darstellung -> {
        val mode = when (vm.themeMode.value) {
            "dark" -> "Dunkel"
            "light" -> "Hell"
            else -> "Wie das Handy"
        }
        val font = listOf(0.85f to "Klein", 1f to "Normal", 1.15f to "Groß", 1.3f to "Sehr groß", 1.5f to "Riesig")
            .minByOrNull { kotlin.math.abs(it.first - vm.fontFactor.value) }?.second ?: "Normal"
        "${vm.designStyle.value.label} · $mode · Schrift $font · Ecken ${vm.cornerStyle.value.label} · Einspieler " +
            if (AppPrefs.introEnabled(context)) "an" else "aus"
    }
    SettingsCategory.Startseite ->
        "Motivator " + (if (vm.motivatorOnToday.value) "an" else "aus") + " · Stimmungs-Check: " + vm.moodMode.value.label
    SettingsCategory.Erinnerungen -> "Übersicht, Ruhezeit, Freigaben, Einnahme-Zeitfenster, Trinken und Pflegetagebuch"
    SettingsCategory.Dokumente -> if (AppPrefs.internalViewer(context)) "Öffnen im eingebauten Betrachter" else "Öffnen mit einer anderen App"
    SettingsCategory.Sicherheit -> "App-Sperre " + (if (AppPrefs.lockEnabled(context)) "an" else "aus") + " · Sicherung, Zurücksetzen"
    SettingsCategory.Internet ->
        de.meinhelfer.tag.ai.AiMode.of(context).title + " · " + (if (AiConfig.isConfigured(context)) AiConfig.provider(context).label else "kein Dienst eingerichtet")
    SettingsCategory.Hilfe -> "Anleitung, Rundgang, Fehler oder Wunsch melden, Selbsttest"
    SettingsCategory.Assistent ->
        vm.assistantName.value + " · " + (if (vm.assistantOn.value) "an" else "aus (Tages-Motivator)")
    SettingsCategory.Beta -> "Uhr, Fitnessband und Messgeräte (Health Connect), Aurora und Effekte"
    SettingsCategory.App -> "Version " + UpdateChecker.installedVersion(context) +
        if (updateState is UpdateState.Available || updateState is UpdateState.Ready) " · Update verfügbar" else ""
}

@Composable
private fun CategoryRow(c: SettingsCategory, summary: String, onClick: () -> Unit) {
    HCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Button(shape = buttonShape(), onClick = onDownload) { Text("Herunterladen") }
                }
                is UpdateState.Downloading -> {
                    Text("Lade Version ${state.info.version} …")
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> {
                    Text("Version ${state.info.version} ist heruntergeladen.", fontWeight = FontWeight.Bold)
                    Button(shape = buttonShape(), 
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

            OutlinedButton(shape = buttonShape(), 
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
            Button(shape = buttonShape(), onClick = { openUrl(context, UpdateChecker.ISSUES_NEW_URL) }) {
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
                else -> {
                    // Offene Punkte vollstaendig, vom Verlauf nur die letzten 3 Versionen
                    var versions = 0
                    val shown = roadmap.takeWhile { line ->
                        if (line.kind == RoadmapKind.HEADING && line.text.startsWith("Version")) versions++
                        versions <= 3
                    }
                    shown.forEach { line ->
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
                    if (shown.size < roadmap.size) {
                        Text("Ältere Versionen stehen in der Datei TODO.md im GitHub-Projekt.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hell oder dunkel", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Wie das Handy", "light" to "Hell", "dark" to "Dunkel").forEach { (key, label) ->
                    FilterChip(selected = mode == key, onClick = { vm.setThemeMode(key) }, label = { Text(label) })
                }
            }
            Text("Schriftgröße", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            val fontFactor by vm.fontFactor.collectAsStateWithLifecycle()
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "Klein", 1f to "Normal", 1.15f to "Groß", 1.3f to "Sehr groß", 1.5f to "Riesig").forEach { (f, label) ->
                    FilterChip(selected = kotlin.math.abs(fontFactor - f) < 0.01f, onClick = { vm.setFontFactor(f) }, label = { Text(label) })
                }
            }
            Text("So groß wird die Schrift in der ganzen App. Sie gilt zusätzlich zur Schriftgröße des Handys.", style = MaterialTheme.typography.bodySmall)
        }
    }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Design", style = MaterialTheme.typography.titleMedium)
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
            OutlinedButton(shape = buttonShape(), onClick = { tweaking = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Farben von „${style.label}“ anpassen" + if (tweaks[style]?.isEmpty == false) " (geändert)" else "")
            }
        }
    }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ecken", style = MaterialTheme.typography.titleMedium)
            val corners by vm.cornerStyle.collectAsStateWithLifecycle()
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CornerStyle.entries.forEach { option ->
                    FilterChip(selected = corners == option, onClick = { vm.setCornerStyle(option) }, label = { Text(option.label) })
                }
            }
            Text(
                "Gilt einheitlich für Karten, Knöpfe, Eingabefelder und Fenster. „Wie im Design“ nimmt die Ecken des gewählten Designs.",
                style = MaterialTheme.typography.bodySmall,
            )
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
    var customUrl by remember { mutableStateOf(AiConfig.customUrl(context)) }
    var keyless by remember { mutableStateOf(AiConfig.keylessEnabled(context, provider)) }
    var tick by remember { mutableStateOf(0) }
    val ready = remember(provider, key, customUrl, keyless, tick) { AiConfig.isReady(context, provider) }

    fun choose(p: AiProvider) {
        provider = p
        AiConfig.setProvider(context, p)
        key = AiConfig.apiKey(context, p)
        model = AiConfig.model(context, p)
        keyless = AiConfig.keylessEnabled(context, p)
        testResult = null
        models = emptyList()
        modelsMessage = null
    }

    fun mark(p: AiProvider): String = p.label + if (AiConfig.isReady(context, p)) " ✓" else ""

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KI-Assistent (optional)", style = MaterialTheme.typography.titleMedium)
            AiModeChooser(AppPrefs.assistantName(context))
            androidx.compose.material3.HorizontalDivider()
            Text(
                "Die KI liefert Anhaltspunkte, z. B. „Medikamentenplan vom Hausarzt prüfen lassen“. Sie ist kein Arzt und kein Apotheker. " +
                    "Du wählst den Dienst und hinterlegst deinen eigenen Schlüssel (API-Key). Gesendet wird je nach KI-Modus nach deiner Bestätigung oder automatisch – immer ohne Namen und andere persönliche Angaben. " +
                    "Ein Abo bei ChatGPT oder Claude reicht dafür nicht, es braucht einen API-Schlüssel des jeweiligen Anbieters.",
                style = MaterialTheme.typography.bodySmall,
            )
            AiBeginnerHelp()
            if (!storageOk) {
                Text(
                    "Der verschlüsselte Speicher ist auf diesem Handy nicht verfügbar, deshalb können keine Schlüssel gespeichert werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text("Kostenlos nutzbar", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.filter { it.free && it != AiProvider.LOCAL }.forEach { p ->
                    FilterChip(selected = provider == p, onClick = { choose(p) }, label = { Text(mark(p)) })
                }
            }
            Text("Im eigenen Heimnetz", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = provider == AiProvider.LOCAL, onClick = { choose(AiProvider.LOCAL) }, label = { Text(mark(AiProvider.LOCAL)) })
            }
            Text("Kostenpflichtig", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.filter { !it.free }.forEach { p ->
                    FilterChip(selected = provider == p, onClick = { choose(p) }, label = { Text(mark(p)) })
                }
            }
            Text("Ein Haken ✓ zeigt, dass für diesen Dienst ein Schlüssel hinterlegt ist. Bei mehreren eingerichteten Diensten kannst du direkt in der App (bei den KI-Hinweisen) schnell wechseln.", style = MaterialTheme.typography.bodySmall)
            Text(provider.note, style = MaterialTheme.typography.bodySmall)
            if (provider == AiProvider.LOCAL) {
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        customUrl = it.trim()
                        AiConfig.setCustomUrl(context, it)
                        testResult = null
                    },
                    label = { Text("Adresse des Servers") },
                    placeholder = { Text("http://192.168.178.20:11434/v1") },
                    singleLine = true,
                    isError = customUrl.isNotBlank() && !AiConfig.isAllowedCustomUrl(customUrl),
                    supportingText = {
                        Text(
                            if (customUrl.isNotBlank() && !AiConfig.isAllowedCustomUrl(customUrl)) "Unverschlüsselt (http) geht nur im eigenen Netz, z. B. 192.168.x.x. Im Internet bitte https."
                            else "Ollama: Port 11434, LM Studio: Port 1234, jeweils mit /v1 am Ende.",
                        )
                    },
                    enabled = storageOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Der PC muss eingeschaltet und im selben WLAN sein. Ollama nimmt Anfragen aus dem Heimnetz erst an, wenn es dafür freigegeben ist (Einstellung „Expose to network“ bzw. OLLAMA_HOST=0.0.0.0), bei LM Studio „Serve on Local Network“ einschalten. Ein Schlüssel ist meist nicht nötig.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (provider == AiProvider.OVHCLOUD) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = keyless, onCheckedChange = {
                        keyless = it
                        AiConfig.setKeylessEnabled(context, provider, it)
                        tick++
                    })
                    Text("Ohne Schlüssel nutzen (2 Anfragen pro Minute)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    AiConfig.setApiKey(context, provider, it)
                    testResult = null
                },
                label = { Text("API-Schlüssel für ${provider.label}" + if (!provider.keyRequired) " (optional)" else "") },
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
            OutlinedButton(shape = buttonShape(), 
                enabled = ready && !loadingModels,
                onClick = {
                    loadingModels = true
                    modelsMessage = null
                    scope.launch {
                        AiClient.listModels(context, provider, key)
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
                OutlinedButton(shape = buttonShape(), onClick = { openUrl(context, provider.keyUrl) }) {
                    Text(if (provider == AiProvider.LOCAL) "Ollama laden" else "Schlüssel besorgen")
                }
                Button(shape = buttonShape(), 
                    enabled = ready && !testing,
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
                OutlinedButton(shape = buttonShape(), onClick = onOpenGuide) { Text("Anleitung lesen") }
                Button(shape = buttonShape(), onClick = onStartTour) { Text("Rundgang") }
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

/** Barrierefreiheit: hoher Kontrast, Größe der Oberfläche, Vorlesen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccessibilityCard(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val style by vm.designStyle.collectAsStateWithLifecycle()
    val scale by vm.uiScale.collectAsStateWithLifecycle()
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Barrierefreiheit", style = MaterialTheme.typography.titleMedium)

            Text("Hoher Kontrast", style = MaterialTheme.typography.labelLarge)
            Text(
                "Schwarz und Weiß mit kräftigen Farben und deutlichen Rändern, ohne graue Schrift. Im dunklen Modus Gelb auf Schwarz.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (style == DesignStyle.Kontrast) {
                OutlinedButton(shape = buttonShape(), onClick = { vm.setDesignStyle(DesignStyle.Schlicht) }) { Text("Ist an · zurück zu „Schlicht“") }
            } else {
                Button(shape = buttonShape(), onClick = { vm.setDesignStyle(DesignStyle.Kontrast) }) { Text("Hohen Kontrast einschalten") }
            }

            Text("Größe von Knöpfen und Feldern", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f to "Normal", 1.12f to "Größer", 1.25f to "Sehr groß").forEach { (f, label) ->
                    FilterChip(selected = kotlin.math.abs(scale - f) < 0.01f, onClick = { vm.setUiScale(f) }, label = { Text(label) })
                }
            }
            Text(
                "Vergrößert die ganze App: Knöpfe, Kästchen, Abstände und Schrift. Leichter zu treffen, dafür passt weniger auf den Bildschirm. " +
                    "Die Schriftgröße oben gilt zusätzlich.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Vorlesen", style = MaterialTheme.typography.labelLarge)
            Text(
                "„🔊 Vorlesen“ gibt es im Tagesplan, bei Dokumenten und in der Anleitung. Stimme und Geschwindigkeit stellst du unter Einstellungen → „Persönlicher Assistent“ → „Stimme“ ein.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Die App lässt sich auch mit TalkBack bedienen, dem Bildschirmleser von Android (Android-Einstellungen → Bedienungshilfen).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Startbildschirm-Widget: hinzufügen und festlegen, ob Namen zu sehen sind. */
@Composable
private fun WidgetCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var details by remember { mutableStateOf(AppPrefs.widgetDetails(context)) }
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    val canPin = manager.isRequestPinAppWidgetSupported
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Widget auf dem Startbildschirm", style = MaterialTheme.typography.titleMedium)
            Text(
                "Zeigt die nächste Einnahme und den nächsten Termin der gewählten Person, ohne die App zu öffnen. Antippen öffnet die App. " +
                    "Aktualisiert sich beim Abhaken, bei Erinnerungen und etwa jede halbe Stunde.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (canPin) {
                OutlinedButton(shape = buttonShape(), onClick = {
                    manager.requestPinAppWidget(
                        android.content.ComponentName(context, de.meinhelfer.tag.widget.HelperWidgetProvider::class.java),
                        null,
                        null,
                    )
                }) { Text("Widget hinzufügen") }
            } else {
                Text(
                    "Hinzufügen: Startbildschirm lange gedrückt halten → „Widgets“ → „Mein Helfer“.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Namen von Medikamenten und Terminen zeigen", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = details, onCheckedChange = {
                    details = it
                    AppPrefs.setWidgetDetails(context, it)
                    de.meinhelfer.tag.widget.HelperWidget.refresh(context)
                })
            }
            Text(
                if (details) "Jeder, der dein Handy entsperrt sieht, kann die Namen auf dem Startbildschirm lesen." +
                    (if (AppPrefs.lockEnabled(context)) " Die App-Sperre schützt das Widget nicht." else "")
                else "Das Widget zeigt nur Uhrzeit, Anzahl der Medikamente und Termin-Zeit.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Einspieler beim Start: an/aus, Ton, jetzt ansehen. */
@Composable
private fun IntroCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(AppPrefs.introEnabled(context)) }
    var sound by remember { mutableStateOf(AppPrefs.introSound(context)) }
    var preview by remember { mutableStateOf(false) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppLogo(44.dp)
                Text("Einspieler beim Start", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Kurzes Video (10 Sekunden) mit dem Logo, wenn die App geöffnet wird. Tippen oder „Überspringen“ beendet es sofort. " +
                    "Beim Öffnen aus einer Erinnerung oder vom Widget wird es nicht gezeigt.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Einspieler zeigen", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = on, onCheckedChange = {
                    on = it
                    AppPrefs.setIntroEnabled(context, it)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mit Ton (Lautstärke wie bei Medien)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = sound, onCheckedChange = {
                    sound = it
                    AppPrefs.setIntroSound(context, it)
                })
            }
            OutlinedButton(shape = buttonShape(), onClick = { preview = true }) { Text("▶ Jetzt ansehen") }
        }
    }
    if (preview) {
        IntroPreviewDialog(onClose = {
            preview = false
            sound = AppPrefs.introSound(context)
        })
    }
}

/** Effekte (Beta): Transparenz, Leuchtrand, Hintergrund, Bewegung, Animationen, Glanz. */
@Composable
private fun EffectsCard(vm: MainViewModel) {
    val effects by vm.effects.collectAsStateWithLifecycle()
    val style by vm.designStyle.collectAsStateWithLifecycle()
    val design = LocalDesign.current
    var open by remember { mutableStateOf(effects != Effects()) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Effekte (Beta)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Glas, Leuchtrand, bewegter Hintergrund und Animationen. Am schönsten mit dem Design „Aurora“, „Glas“ oder „Liquid“.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { open = !open }) { Text(if (open) "Zuklappen" else "Einstellen") }
            }
            if (open) {
                if (design.backdrop == null) {
                    Text(
                        "Das Design „${style.label}“ hat keinen farbigen Hintergrund. Durchsichtige Karten und der bewegte Hintergrund wirken erst mit „Aurora“, „Glas“ oder „Liquid“.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(shape = buttonShape(), onClick = { vm.setDesignStyle(DesignStyle.Aurora) }) { Text("Aurora ausprobieren") }
                }
                EffectSlider(
                    label = "Deckkraft der Karten",
                    hint = "Weniger = mehr Glas, der Hintergrund scheint durch.",
                    value = effects.cardAlpha,
                    designValue = design.cardColor?.alpha ?: 1f,
                    range = 0.05f..1f,
                    percent = true,
                    onChange = { vm.setEffects(effects.copy(cardAlpha = it)) },
                )
                EffectSlider(
                    label = "Leuchtrand",
                    hint = "Farbiger Rand um die Karten.",
                    value = effects.glow,
                    designValue = design.glow,
                    range = 0f..1f,
                    percent = true,
                    onChange = { vm.setEffects(effects.copy(glow = it)) },
                )
                EffectSlider(
                    label = "Farbflecken im Hintergrund",
                    hint = "Wie kräftig die leuchtenden Flecken hinter den Karten sind.",
                    value = effects.backdrop,
                    designValue = 1f,
                    range = 0f..2f,
                    percent = true,
                    onChange = { vm.setEffects(effects.copy(backdrop = it)) },
                )
                EffectSwitch("Bewegter Hintergrund", "Die Farbflecken wandern ganz langsam. Braucht etwas mehr Akku.", effects.motion) {
                    vm.setEffects(effects.copy(motion = it))
                }
                EffectSwitch("Lebendige Animationen", "Karten geben beim Antippen nach, Klappen und Reiterwechsel gleiten weich.", effects.animations) {
                    vm.setEffects(effects.copy(animations = it))
                }
                EffectSwitch("Glanz", "Skalen und Balken bekommen einen glänzenden Verlauf.", effects.gloss) {
                    vm.setEffects(effects.copy(gloss = it))
                }
                OutlinedButton(shape = buttonShape(), onClick = { vm.setEffects(Effects()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Alles wie im Design")
                }
            }
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    hint: String,
    value: Float,
    designValue: Float,
    range: ClosedFloatingPointRange<Float>,
    percent: Boolean,
    onChange: (Float) -> Unit,
) {
    val shown = if (value >= 0f) value else designValue.coerceIn(range.start, range.endInclusive)
    var current by remember(shown) { mutableStateOf(shown) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                (if (percent) "${(current * 100).toInt()} %" else String.format("%.1f", current)) + if (value < 0f) " (Design)" else "",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        androidx.compose.material3.Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
        )
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EffectSwitch(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Tages-Motivator: Karte, Tagesstart, Stimmungs-Check und Erinnerung. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MotivatorSettingsCard(vm: MainViewModel) {
    val context = LocalContext.current
    val onToday by vm.motivatorOnToday.collectAsStateWithLifecycle()
    val mode by vm.moodMode.collectAsStateWithLifecycle()
    var inDailyStart by remember { mutableStateOf(AppPrefs.motivatorInDailyStart(context)) }
    var notify by remember { mutableStateOf(AppPrefs.moodNotify(context)) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tages-Motivator", style = MaterialTheme.typography.titleMedium)
            Text(
                "Satz für den Tag, Vorhaben aus deinen letzten Tagen, Ziele Schritt für Schritt und ein kurzer Stimmungs-Check.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = onToday, onCheckedChange = { vm.setMotivatorOnToday(it) })
                Text("Auf der Heute-Seite zeigen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = inDailyStart, onCheckedChange = {
                    inDailyStart = it
                    AppPrefs.setMotivatorInDailyStart(context, it)
                })
                Text("Im Tagesstart zeigen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            Text("Stimmungs-Check", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                de.meinhelfer.tag.summary.MoodMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { vm.setMoodMode(m) }, label = { Text(m.label) })
                }
            }
            Text(
                "Gefragt wird " + de.meinhelfer.tag.summary.MoodSchedule.slotText(mode) + ". „Später“ zählt für das jeweilige Zeitfenster, nachts wird nicht gefragt.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (mode.isSlotted) {
                val hours = de.meinhelfer.tag.summary.MoodSchedule.reminderHours(mode)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = notify, onCheckedChange = {
                        notify = it
                        vm.setMoodNotify(it)
                    })
                    Column(Modifier.weight(1f)) {
                        Text("Erinnerung per Benachrichtigung", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Um " + hours.joinToString(", ") { "$it" }.replace(Regex(", (\\d+)$"), " und $1") + " Uhr, nur wenn du im Zeitfenster noch nichts eingetragen hast.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                "Stimmung, Ziele und Schritte bleiben verschlüsselt auf dem Handy und sind in der Sicherung enthalten. Zur KI geht nur etwas, wenn du es vorher bestätigst.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
