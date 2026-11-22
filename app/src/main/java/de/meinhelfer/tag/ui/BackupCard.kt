package de.meinhelfer.tag.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.backup.Backup
import de.meinhelfer.tag.backup.BackupInfo
import de.meinhelfer.tag.backup.WrongPasswordException
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BACKUP_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

private fun timeText(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(BACKUP_TIME)

private sealed interface BackupStep {
    object Idle : BackupStep
    object AskNewPassword : BackupStep
    object AskRestorePassword : BackupStep
    class Working(val text: String) : BackupStep
    class Done(val title: String, val text: String) : BackupStep
    class ConfirmRestore(val info: BackupInfo) : BackupStep
    object ResetWarn : BackupStep
    object ResetConfirm : BackupStep
}

private fun findActivity(context: android.content.Context): androidx.fragment.app.FragmentActivity? {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is androidx.fragment.app.FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Sicherung erstellen und wiederherstellen. Die Datei ist mit einem eigenen Passwort verschluesselt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<BackupStep>(BackupStep.Idle) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var lastBackup by remember { mutableStateOf(AppPrefs.lastBackup(context)) }
    // Kommt die Sicherung aus dem Zuruecksetzen, geht es danach dort weiter
    var resetAfterBackup by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }
    val restoreResult = remember { AppPrefs.restoreResult(context) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) {
            step = if (resetAfterBackup) BackupStep.ResetWarn else BackupStep.Idle
            resetAfterBackup = false
            return@rememberLauncherForActivityResult
        }
        val pw = password.toCharArray()
        step = BackupStep.Working("Sicherung wird erstellt …")
        scope.launch {
            try {
                val out = context.contentResolver.openOutputStream(uri, "w") ?: throw java.io.IOException("Zieldatei nicht beschreibbar")
                val info = Backup.create(context, out, pw) { text -> step = BackupStep.Working(text) }
                lastBackup = info.createdMillis
                step = BackupStep.Done(
                    "Sicherung gespeichert",
                    "${info.documents} Dokumente und ${info.photos} Fotos gesichert." +
                        (if (info.skipped > 0) "\n${info.skipped} Dokumente konnten nicht gelesen werden und fehlen in der Sicherung." else "") +
                        "\n\nBewahre die Datei und das Passwort getrennt und sicher auf, am besten außerhalb des Handys (Cloud, Computer, USB-Stick). Ohne das Passwort lässt sich die Sicherung nicht öffnen.",
                )
            } catch (e: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                // Ohne gelungene Sicherung nicht direkt weiter zum Zuruecksetzen
                resetAfterBackup = false
                step = BackupStep.Done("Sicherung nicht möglich", "Es hat nicht geklappt (${e.message ?: "Fehler"}). Bitte erneut versuchen.")
            }
            password = ""
            repeat = ""
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            step = BackupStep.Idle
        } else {
            pendingUri = uri
            password = ""
            step = BackupStep.AskRestorePassword
        }
    }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sicherung, Wiederherstellung und Zurücksetzen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sichert alles in einer Datei: Personen, Medikamente, Termine, Kontakte, Aufgaben, Dokumente, Fotos und Einstellungen. " +
                    "Die Datei ist mit einem Passwort verschlüsselt, das nur du kennst. Damit kannst du die Daten auch auf einem neuen Handy wiederherstellen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (lastBackup > 0) "Letzte Sicherung: ${timeText(lastBackup)}" else "Es wurde noch keine Sicherung erstellt.",
                style = MaterialTheme.typography.bodyMedium,
            )
            var remindDays by remember { mutableStateOf(AppPrefs.backupReminderDays(context)) }
            Text("An Sicherung erinnern", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Aus", 14 to "Alle 2 Wochen", 30 to "Monatlich").forEach { (d, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = remindDays == d,
                        onClick = {
                            remindDays = d
                            AppPrefs.setBackupReminderDays(context, d)
                        },
                        label = { Text(label) },
                    )
                }
            }
            when {
                restoreResult.startsWith("ok:") ->
                    Text("Zuletzt wiederhergestellt: ${timeText(restoreResult.removePrefix("ok:").toLongOrNull() ?: 0L)}", style = MaterialTheme.typography.bodySmall)
                restoreResult.startsWith("fehler:") ->
                    Text(
                        "Die letzte Wiederherstellung ist fehlgeschlagen (${timeText(restoreResult.removePrefix("fehler:").toLongOrNull() ?: 0L)}). Deine bisherigen Daten sind unverändert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = buttonShape(), onClick = {
                    password = ""
                    repeat = ""
                    step = BackupStep.AskNewPassword
                }) { Text("Sicherung erstellen") }
                OutlinedButton(shape = buttonShape(), onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Wiederherstellen") }
            }
            Text(
                "Nicht enthalten: der Zugangsschlüssel für die KI (bitte neu eintragen). Es wird nichts automatisch gesendet, du wählst selbst, wo die Datei gespeichert wird.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("App zurücksetzen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Löscht alle Daten auf diesem Handy und startet die App wie neu. Vorher kannst du eine Sicherung erstellen. Bestätigt wird mit PIN, Muster, Fingerabdruck oder Gesicht.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
                resetError = null
                step = BackupStep.ResetWarn
            }) { Text("App zurücksetzen …", color = MaterialTheme.colorScheme.error) }
        }
    }

    when (val s = step) {
        BackupStep.Idle -> {}
        BackupStep.AskNewPassword -> {
            val tooShort = password.length < 8
            val mismatch = password != repeat
            AlertDialog(
                properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
                onDismissRequest = { step = BackupStep.Idle },
                title = { Text("Passwort für die Sicherung") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Wähle ein Passwort mit mindestens 8 Zeichen. Wenn du es vergisst, kann niemand die Sicherung öffnen, auch ich nicht. Bitte notiere es dir sicher.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Passwort") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = repeat,
                            onValueChange = { repeat = it },
                            label = { Text("Passwort wiederholen") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = repeat.isNotEmpty() && mismatch,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = !tooShort && !mismatch, onClick = {
                        createLauncher.launch(Backup.suggestedName())
                    }) { Text("Weiter") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        step = if (resetAfterBackup) BackupStep.ResetWarn else BackupStep.Idle
                        resetAfterBackup = false
                    }) { Text("Abbrechen") }
                },
            )
        }
        BackupStep.AskRestorePassword -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { step = BackupStep.Idle },
            title = { Text("Passwort der Sicherung") },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty(), onClick = {
                    val uri = pendingUri ?: return@TextButton
                    val pw = password.toCharArray()
                    step = BackupStep.Working("Sicherung wird gelesen …")
                    scope.launch {
                        try {
                            val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Datei nicht lesbar")
                            val info = input.use { Backup.stage(context, it, pw) { text -> step = BackupStep.Working(text) } }
                            step = BackupStep.ConfirmRestore(info)
                        } catch (_: WrongPasswordException) {
                            step = BackupStep.Done("Passwort falsch", "Das Passwort stimmt nicht, oder die Datei ist beschädigt.")
                        } catch (e: Backup.NewerBackupException) {
                            step = BackupStep.Done("Sicherung zu neu", "Die Sicherung stammt aus einer neueren Version der App. Bitte zuerst die App aktualisieren.")
                        } catch (e: Exception) {
                            step = BackupStep.Done("Nicht möglich", "Die Sicherung konnte nicht gelesen werden (${e.message ?: "Fehler"}).")
                        }
                        password = ""
                    }
                }) { Text("Prüfen") }
            },
            dismissButton = { TextButton(onClick = { step = BackupStep.Idle }) { Text("Abbrechen") } },
        )
        is BackupStep.Working -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = {},
            title = { Text("Bitte warten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.text, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Die App bitte nicht schließen.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )
        is BackupStep.Done -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = {
                resetAfterBackup = false
                step = BackupStep.Idle
            },
            title = { Text(s.title) },
            text = { Text(s.text) },
            confirmButton = {
                if (resetAfterBackup) {
                    TextButton(onClick = {
                        resetAfterBackup = false
                        step = BackupStep.ResetConfirm
                    }) { Text("Weiter zum Zurücksetzen", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { step = BackupStep.Idle }) { Text("OK") }
                }
            },
            dismissButton = {
                if (resetAfterBackup) {
                    TextButton(onClick = {
                        resetAfterBackup = false
                        step = BackupStep.Idle
                    }) { Text("Nicht zurücksetzen") }
                }
            },
        )
        BackupStep.ResetWarn -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { step = BackupStep.Idle },
            title = { Text("App zurücksetzen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Achtung: Dabei werden alle Daten auf diesem Handy gelöscht: Personen, Medikamente, Termine, Aufgaben samt Verlauf, " +
                            "Dokumente, Fotos, Kontakte, Bemerkungen, PDFs, Einstellungen und KI-Schlüssel. Das lässt sich nicht rückgängig machen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (lastBackup > 0) "Letzte Sicherung: ${timeText(lastBackup)}." else "Es gibt noch keine Sicherung.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lastBackup > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Empfehlung: vorher eine Sicherung erstellen. Erhalten bleiben nur die Nachweise deiner Zustimmung zu den Nutzungsbedingungen (mit ihrer Aufbewahrungsfrist).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(shape = buttonShape(), onClick = {
                        password = ""
                        repeat = ""
                        resetAfterBackup = true
                        step = BackupStep.AskNewPassword
                    }, modifier = Modifier.fillMaxWidth()) { Text("Erst Sicherung erstellen") }
                }
            },
            confirmButton = {
                TextButton(onClick = { step = BackupStep.ResetConfirm }) {
                    Text("Ohne Sicherung weiter", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { step = BackupStep.Idle }) { Text("Abbrechen") } },
        )
        BackupStep.ResetConfirm -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { step = BackupStep.Idle },
            title = { Text("Zurücksetzen bestätigen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Zum Schutz musst du dich mit der Bildschirmsperre deines Handys ausweisen: PIN, Muster, Fingerabdruck oder Gesicht. " +
                            "Danach werden alle Daten gelöscht und die App startet neu.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    resetError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (!de.meinhelfer.tag.security.AppLock.available(context)) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text("Bildschirmsperre einrichten") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val activity = findActivity(context)
                    if (!de.meinhelfer.tag.security.AppLock.available(context) || activity == null) {
                        resetError = "Auf diesem Handy ist keine Bildschirmsperre eingerichtet. Ohne sie lässt sich die App nicht zurücksetzen. Richte zuerst eine ein."
                        return@TextButton
                    }
                    de.meinhelfer.tag.security.AppLock.authenticate(activity, "App zurücksetzen") { ok ->
                        if (!ok) {
                            resetError = "Nicht bestätigt. Es wurde nichts gelöscht."
                        } else {
                            step = BackupStep.Working("Daten werden gelöscht, die App startet neu …")
                            scope.launch {
                                de.meinhelfer.tag.backup.AppReset.schedule(context)
                                Backup.restartApp(context)
                            }
                        }
                    }
                }) { Text("Bestätigen und zurücksetzen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { step = BackupStep.Idle }) { Text("Abbrechen") } },
        )
        is BackupStep.ConfirmRestore -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = {
                Backup.discardStaged(context)
                step = BackupStep.Idle
            },
            title = { Text("Wiederherstellen?") },
            text = {
                Text(
                    "Sicherung vom ${timeText(s.info.createdMillis)} (App ${s.info.appVersion}) mit ${s.info.documents} Dokumenten und ${s.info.photos} Fotos.\n\n" +
                        "Alle jetzigen Daten auf diesem Handy werden durch die Sicherung ersetzt. Danach startet die App neu. " +
                        "Wenn etwas schiefgeht, bleiben die bisherigen Daten erhalten.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    step = BackupStep.Working("App wird neu gestartet …")
                    Backup.restartApp(context)
                }) { Text("Ersetzen und neu starten") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Backup.discardStaged(context)
                    step = BackupStep.Idle
                }) { Text("Abbrechen") }
            },
        )
    }
}
