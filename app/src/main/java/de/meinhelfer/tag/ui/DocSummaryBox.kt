package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.ai.AiClient
import de.meinhelfer.tag.ai.AiConfig
import de.meinhelfer.tag.ai.AiLimitException
import de.meinhelfer.tag.ai.AiProvider
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Person
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val SUMMARY_SYSTEM =
    "Du hilfst pflegenden Angehörigen, Schreiben und Berichte zu verstehen. Du bist kein Arzt, Jurist oder Berater und ersetzt keinen. " +
        "Fasse den Text in einfacher Sprache zusammen, höchstens 6 kurze Stichpunkte, jede Zeile beginnt mit „- “. " +
        "Nenne, worum es geht, wichtige Daten, Fristen und Beträge und was als Nächstes zu klären sein könnte (nur als Frage an Arzt, Pflegekasse oder die genannte Stelle). " +
        "Keine Diagnosen, keine Dosierungsempfehlungen. Erfinde nichts: Was nicht im Text steht, lässt du weg. " +
        "Ist der Text kaum lesbar, sage das in einem Stichpunkt. Keine Einleitung, kein Schlusssatz."

private const val TASKS_SYSTEM =
    "Du hilfst pflegenden Angehörigen, aus einem Schreiben oder Bericht konkrete Aufgaben abzuleiten. Du bist kein Arzt und ersetzt keinen. " +
        "Nenne nur Aufgaben, die im Text stehen oder sich direkt daraus ergeben (z. B. Untersuchung machen lassen, Termin vereinbaren, Antrag oder Unterlagen einreichen, Rezept holen, Frist beachten). " +
        "Keine Diagnosen, keine Dosierungen, nichts erfinden. Höchstens 5 Aufgaben. " +
        "Antworte NUR mit einem JSON-Array ohne weiteren Text, jedes Element so: " +
        "{\"aufgabe\": \"kurzer Satz im Imperativ\", \"stelle\": \"zuständige Stelle, z. B. Hausarzt, Nephrologe, Pflegekasse, Apotheke, oder leer\", " +
        "\"frist_tage\": Zahl der Tage ab heute bis spätestens erledigt oder null, \"prioritaet\": \"hoch\" oder \"normal\" oder \"niedrig\", " +
        "\"ausser_haus\": true wenn man dafür das Haus verlassen muss, sonst false}. " +
        "Rechne Zeitangaben aus dem Text mit dem genannten heutigen Datum in Tage um (z. B. „Anfang nächster Woche“ = Tage bis zum nächsten Montag). " +
        "Gibt es keine Aufgaben, antworte []."

private const val MAX_CHARS = 12_000

/** Ersetzt Name, Geburtsdatum, Versichertennummer, Strasse, IBAN und E-Mail der Person durch Platzhalter. Andere Namen im Text bleiben. */
internal fun maskPersonal(text: String, person: Person?): String {
    var t = text
    if (person != null) {
        person.name.split(Regex("\\s+")).filter { it.length >= 3 }.forEach { part ->
            t = t.replace(Regex(Regex.escape(part), RegexOption.IGNORE_CASE), "[Name]")
        }
        person.birthDate?.let { day ->
            val date = LocalDate.ofEpochDay(day)
            listOf("dd.MM.yyyy", "d.M.yyyy").forEach { pattern -> t = t.replace(date.format(DateTimeFormatter.ofPattern(pattern)), "[Geburtsdatum]") }
        }
        listOf(person.insuranceNumber, person.street).filter { it.isNotBlank() }.forEach { value ->
            t = t.replace(value, "[Angabe]", ignoreCase = true)
        }
    }
    t = t.replace(Regex("\\b[A-Z]{2}\\d{2}(?:\\s?\\d{4}){4}\\s?\\d{0,2}\\b"), "[IBAN]")
    t = t.replace(Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+"), "[E-Mail]")
    return t
}

/**
 * KI-Zusammenfassung eines Dokuments in einfacher Sprache. Gesendet wird erst nach Bestaetigung; der Nutzer sieht
 * vorher den genauen Text. Name und Nummern der Person werden durch Platzhalter ersetzt.
 */
@Composable
fun DocSummaryBox(
    text: String,
    person: Person?,
    summary: String,
    onSummary: (String) -> Unit,
    doctors: List<Doctor> = emptyList(),
    docDay: Long? = null,
    onAddTasks: ((List<TaskDraft>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var limitHit by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }
    var confirmTasks by remember { mutableStateOf(false) }
    var taskDrafts by remember { mutableStateOf<List<TaskDraft>?>(null) }
    var taskInfo by remember { mutableStateOf<String?>(null) }

    val configured = AiConfig.isConfigured(context)
    val otherReady = AiProvider.entries.any { it != provider && AiConfig.apiKey(context, it).isNotBlank() }
    val masked = remember(text, person) { maskPersonal(text.take(MAX_CHARS), person) }
    val promptText = "Text eines Dokuments (Namen und Nummern der betreuten Person sind durch Platzhalter ersetzt):\n\n$masked"
    val enoughText = text.trim().length >= 40
    val todayDate = LocalDate.now()
    val weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    val taskSource = if (summary.trim().length >= 20) maskPersonal(summary, person) else masked
    val taskPrompt = "Heute ist " + weekdays[todayDate.dayOfWeek.value - 1] + ", " + todayDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "." +
        (docDay?.let { " Das Dokument ist vom " + LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "." } ?: "") +
        "\n\n" + (if (summary.trim().length >= 20) "Zusammenfassung des Dokuments" else "Text des Dokuments") +
        " (Namen und Nummern der betreuten Person sind durch Platzhalter ersetzt):\n\n" + taskSource

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Zusammenfassung (Anhaltspunkt)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = summary,
            onValueChange = onSummary,
            label = { Text("Kurz erklärt, selbst schreiben oder per KI") },
            minLines = 2,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!configured && !otherReady) {
            Text(
                "KI nicht eingerichtet. Unter Einstellungen → KI-Assistent kannst du sie einrichten, dann erstellt sie hier eine Zusammenfassung.",
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
                Text("${provider.label} liest mit …", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (configured) {
            OutlinedButton(enabled = enoughText, onClick = { confirm = true }) {
                Text(if (summary.isBlank()) "Per KI zusammenfassen" else "Neu zusammenfassen")
            }
            if (!enoughText) {
                Text("Dafür ist zu wenig Text erkannt. Lies den Text neu aus oder trage ihn unten ein.", style = MaterialTheme.typography.bodySmall)
            }
            if (onAddTasks != null) {
                OutlinedButton(enabled = enoughText || summary.trim().length >= 20, onClick = { confirmTasks = true }) {
                    Text("Aufgaben vorschlagen")
                }
                taskInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
            "KI-Zusammenfassung, kann Fehler enthalten und ersetzt nicht das Dokument. Bei Fragen zählt, was im Original steht, und was Arzt oder Behörde sagen.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("An ${provider.label} senden?") },
            text = {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Genau dieser Text wird über das Internet an den Dienst gesendet. Name, Geburtsdatum, Versichertennummer und Straße der Person sind ersetzt. " +
                            "Andere Namen im Dokument (z. B. Ärzte oder Absender) und der übrige Inhalt können enthalten sein. Prüfe den Text und brich ab, wenn etwas nicht raus soll.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (text.length > MAX_CHARS) {
                        Text("Der Text ist lang, es werden nur die ersten ${MAX_CHARS / 1000} Tausend Zeichen gesendet.", style = MaterialTheme.typography.bodySmall)
                    }
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
                        AiClient.ask(context, SUMMARY_SYSTEM, promptText)
                            .onSuccess { answer -> onSummary(AiClient.bullets(answer).joinToString("\n") { "• $it" }) }
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

    if (confirmTasks) {
        AlertDialog(
            onDismissRequest = { confirmTasks = false },
            title = { Text("An ${provider.label} senden?") },
            text = {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Die KI soll daraus Aufgaben vorschlagen. Genau dieser Text wird über das Internet gesendet. Name, Geburtsdatum, Versichertennummer und Straße der Person sind ersetzt, " +
                            "Namen deiner Kontakte werden nicht gesendet. Die Zuordnung zum Kontakt passiert nur auf deinem Handy.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(taskPrompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTasks = false
                    loading = true
                    error = null
                    limitHit = false
                    taskInfo = null
                    scope.launch {
                        AiClient.ask(context, TASKS_SYSTEM, taskPrompt)
                            .onSuccess { answer ->
                                val drafts = parseTaskSuggestions(answer, doctors, LocalDate.now().toEpochDay())
                                if (drafts.isEmpty()) taskInfo = "Die KI hat keine Aufgaben im Text gefunden." else taskDrafts = drafts
                            }
                            .onFailure {
                                error = it.message ?: "Das hat nicht geklappt."
                                limitHit = it is AiLimitException
                            }
                        loading = false
                    }
                }) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { confirmTasks = false }) { Text("Abbrechen") } },
        )
    }

    taskDrafts?.let { drafts ->
        TaskSuggestDialog(
            drafts = drafts,
            doctors = doctors,
            onAdd = { chosen ->
                onAddTasks?.invoke(chosen)
                taskInfo = "${chosen.size} Aufgabe(n) auf die Heute-Seite übernommen."
                taskDrafts = null
            },
            onDismiss = { taskDrafts = null },
        )
    }
}
