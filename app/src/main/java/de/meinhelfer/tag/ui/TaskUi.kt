package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Entwurf einer Aufgabe (z. B. als KI-Vorschlag, bevor sie uebernommen wird). */
data class TaskDraft(
    val text: String,
    val doctorId: Long? = null,
    /** 0 = niedrig, 1 = normal, 2 = hoch */
    val priority: Int = 1,
    val dueDay: Long? = null,
    val outside: Boolean = false,
    /** Erinnerung per Benachrichtigung (Millisekunden), null = keine. */
    val remindAt: Long? = null,
)

private val REMIND_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm", Locale.GERMANY)

/** "🔔 Mo 24.11., 09:00 Uhr" fuer eine geplante Erinnerung in der Zukunft, sonst null. */
internal fun remindText(at: Long?): String? {
    if (at == null || at <= System.currentTimeMillis()) return null
    return "🔔 " + java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).format(REMIND_FORMAT) + " Uhr"
}

/** Oeffnet die Kalender-App mit einem vorausgefuellten Eintrag (ganztägig am Fristtag oder zur Erinnerungszeit). */
internal fun addTaskToCalendar(context: Context, text: String, dueDay: Long?, remindAt: Long?, note: String = "") {
    val zone = java.time.ZoneId.systemDefault()
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = android.provider.CalendarContract.Events.CONTENT_URI
        putExtra(android.provider.CalendarContract.Events.TITLE, text.lines().first().take(120))
        val details = listOf(text.takeIf { it.lines().size > 1 || it.length > 120 }.orEmpty(), note).filter { it.isNotBlank() }.joinToString("\n\n")
        if (details.isNotBlank()) putExtra(android.provider.CalendarContract.Events.DESCRIPTION, details)
        when {
            remindAt != null -> {
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, remindAt)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, remindAt + 30 * 60 * 1000L)
            }
            dueDay != null -> {
                // Ganztaegige Eintraege erwarten Mitternacht UTC
                val start = LocalDate.ofEpochDay(dueDay).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 24 * 60 * 60 * 1000L)
            }
            else -> {
                val start = LocalDate.now().plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60 * 1000L)
            }
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Keine Kalender-App gefunden.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Verknuepfte Dokumente einer Aufgabe. */
internal fun Todo.attachedIds(): List<Long> = docIds.split(",").mapNotNull { it.trim().toLongOrNull() }

private const val NO_CONTACT_TASK = "– keine Auswahl –"
private val TASK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

internal fun priorityLabel(p: Int): String = when (p) {
    2 -> "Hoch"
    0 -> "Niedrig"
    else -> "Normal"
}

/** "fällig heute", "überfällig seit 3 Tagen", "fällig bis 25.11.2026 (in 4 Tagen)" */
internal fun dueText(dueDay: Long, today: Long = LocalDate.now().toEpochDay()): String {
    val diff = dueDay - today
    return when {
        diff == 0L -> "fällig heute"
        diff == 1L -> "fällig morgen"
        diff == -1L -> "überfällig seit gestern"
        diff < 0 -> "überfällig seit ${-diff} Tagen"
        else -> "fällig bis " + LocalDate.ofEpochDay(dueDay).format(TASK_DATE) + " (in $diff Tagen)"
    }
}

private fun sortTasks(list: List<Todo>): List<Todo> = list.sortedWith(
    compareBy<Todo> { it.done }
        .thenByDescending { it.priority }
        .thenBy { it.dueDay ?: Long.MAX_VALUE }
        .thenBy { it.createdMillis },
)

private fun startIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // keine passende App installiert
    }
}

/** Die Aufgaben-Karte auf der Heute-Seite: abhakbar, nach Wichtigkeit und Frist sortiert, mit Direktkontakt und Fahrdienst. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasksCard(
    todos: List<Todo>,
    doctors: List<Doctor>,
    person: Person?,
    vm: MainViewModel,
    meds: List<de.meinhelfer.tag.data.Medication> = emptyList(),
    caregiver: de.meinhelfer.tag.data.CaregiverProfile = de.meinhelfer.tag.data.CaregiverProfile(),
    onEditCaregiver: () -> Unit = {},
    documents: List<Document> = emptyList(),
) {
    val context = LocalContext.current
    var docsFor by remember { mutableStateOf<Todo?>(null) }
    var mailFor by remember { mutableStateOf<Todo?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Todo?>(null) }
    var rideFor by remember { mutableStateOf<Todo?>(null) }
    var confirmDelete by remember { mutableStateOf<Todo?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var resultFor by remember { mutableStateOf<Todo?>(null) }
    var pastFor by remember { mutableStateOf<Todo?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val history by vm.taskHistory.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    val openCount = todos.count { !it.done }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                "Aufgaben" + if (openCount > 0) " ($openCount offen)" else "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            if (todos.isEmpty()) {
                Text(
                    "Noch nichts vorgemerkt, z. B. „Blutuntersuchung beim Hausarzt“. Aufgaben können einen Arzt, eine Frist und eine Wichtigkeit haben.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
            val doneTasks = todos.filter { it.done }
            sortTasks(todos).filter { !it.done || showDone }.forEach { t ->
                val doctor = doctors.firstOrNull { it.id == t.doctorId }
                val overdue = !t.done && t.dueDay != null && t.dueDay < today
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = t.done, onCheckedChange = { checked ->
                        vm.setTodoDone(t, checked)
                        if (checked) resultFor = t
                    })
                    Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            t.text,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.clickable {
                                vm.setTodoDone(t, !t.done)
                                if (!t.done) resultFor = t
                            }.padding(vertical = 4.dp),
                        )
                        if (!t.done) {
                            val meta = listOfNotNull(
                                when (t.priority) {
                                    2 -> "Dringend"
                                    0 -> "Niedrige Priorität"
                                    else -> null
                                },
                                t.dueDay?.let { dueText(it, today) },
                                remindText(t.remindAt),
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (overdue || t.priority == 2) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            doctor?.let { Text(doctorLabel(it), style = MaterialTheme.typography.bodySmall) }
                            val past = TopicMatcher.similar(t.text, t.doctorId, history, excludeId = t.id)
                            if (past.isNotEmpty()) {
                                Text(
                                    "Dazu gab es schon etwas (${past.size}) – antippen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { pastFor = t }.padding(vertical = 2.dp),
                                )
                            }
                            FlowRow {
                                val pad = PaddingValues(horizontal = 8.dp)
                                if (doctor != null && doctor.phone.isNotBlank()) {
                                    TextButton(contentPadding = pad, onClick = {
                                        startIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + doctor.phone.filter { it.isDigit() || it == '+' })))
                                    }) { Text("Anrufen") }
                                }
                                if (doctor != null && doctor.email.isNotBlank()) {
                                    TextButton(contentPadding = pad, onClick = { mailFor = t }) { Text("E-Mail") }
                                }
                                if (t.outside) {
                                    TextButton(contentPadding = pad, onClick = { rideFor = t }) { Text("Fahrdienst") }
                                }
                                val attached = t.attachedIds().count { id -> documents.any { it.id == id } }
                                if (documents.isNotEmpty() || attached > 0) {
                                    TextButton(contentPadding = pad, onClick = { docsFor = t }) {
                                        Text(if (attached > 0) "Unterlagen ($attached)" else "Unterlagen")
                                    }
                                }
                                TextButton(contentPadding = pad, onClick = { editItem = t }) { Text("Bearbeiten") }
                                TextButton(contentPadding = pad, onClick = { confirmDelete = t }) { Text("Löschen") }
                            }
                        } else {
                            val meta = listOfNotNull(
                                doneDateText(t)?.let { "erledigt am $it" },
                                stars(t.rating).takeIf { it.isNotEmpty() },
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium)
                            if (t.result.isNotBlank()) Text("Ergebnis: ${t.result}", style = MaterialTheme.typography.bodySmall)
                            FlowRow {
                                val pad = PaddingValues(horizontal = 8.dp)
                                TextButton(contentPadding = pad, onClick = { resultFor = t }) {
                                    Text(if (t.result.isBlank()) "Ergebnis eintragen" else "Ergebnis ändern")
                                }
                                TextButton(contentPadding = pad, onClick = { vm.archiveTodo(t) }) { Text("In den Verlauf") }
                            }
                        }
                    }
                }
            }
            if (doneTasks.isNotEmpty()) {
                FlowRow {
                    TextButton(onClick = { showDone = !showDone }) {
                        Text(if (showDone) "Erledigte ausblenden" else "Erledigte anzeigen (${doneTasks.size})")
                    }
                    if (showDone) {
                        TextButton(onClick = { confirmClear = true }) { Text("Alle in den Verlauf") }
                    }
                }
            }
            FlowRow {
                TextButton(onClick = { showAdd = true }) { Text("+ Aufgabe hinzufügen") }
                TextButton(onClick = { showHistory = true }) { Text("Verlauf und Suche") }
            }
        }
    }

    resultFor?.let { t ->
        TaskResultDialog(
            task = t,
            onSave = { r, st ->
                vm.saveTodoResult(t, r, st)
                resultFor = null
            },
            onDismiss = { resultFor = null },
        )
    }
    pastFor?.let { t ->
        AlertDialog(
            onDismissRequest = { pastFor = null },
            title = { Text("Früher schon einmal") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    PastTopicsBox(TopicMatcher.similar(t.text, t.doctorId, history, excludeId = t.id, limit = 5), doctors)
                }
            },
            confirmButton = { TextButton(onClick = { pastFor = null }) { Text("Schließen") } },
        )
    }
    if (showHistory) {
        TaskHistoryDialog(history = history, doctors = doctors, vm = vm, onClose = { showHistory = false })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Erledigte in den Verlauf?") },
            text = { Text("Die erledigten Aufgaben verschwinden aus dieser Liste. Sie bleiben im Verlauf, in der Auswertung und in der Suche und werden bei ähnlichen Aufgaben wieder angezeigt.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.archiveDoneTodos()
                    confirmClear = false
                    showDone = false
                }) { Text("Verschieben") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } },
        )
    }

    if (showAdd || editItem != null) {
        TaskDialog(
            history = history,
            initial = editItem,
            doctors = doctors,
            onDismiss = {
                showAdd = false
                editItem = null
            },
            onSave = { d ->
                val e = editItem
                if (e == null) vm.addTasks(listOf(d))
                else vm.updateTodo(e.copy(text = d.text, doctorId = d.doctorId, priority = d.priority, dueDay = d.dueDay, outside = d.outside, remindAt = d.remindAt))
                showAdd = false
                editItem = null
            },
        )
    }

    docsFor?.let { t ->
        TaskDocsDialog(
            task = t,
            doctor = doctors.firstOrNull { it.id == t.doctorId },
            docs = documents,
            doctors = doctors,
            onSave = { ids ->
                vm.updateTodo(t.copy(docIds = ids.joinToString(",")))
                docsFor = null
            },
            onClose = { docsFor = null },
        )
    }

    mailFor?.let { t ->
        val d = doctors.firstOrNull { it.id == t.doctorId }
        if (d != null) {
            MailDraftDialog(
                recipient = d,
                person = person,
                caregiver = caregiver,
                meds = meds,
                note = t.text,
                initialTopic = de.meinhelfer.tag.export.MailTemplates.guessTopic(de.meinhelfer.tag.export.MailTemplates.kindOf(d), t.text),
                onEditProfile = onEditCaregiver,
                onClose = { mailFor = null },
            )
        }
    }

    rideFor?.let { t ->
        FahrdienstDialog(
            title = t.text,
            whenText = "Termin noch offen" + (t.dueDay?.let { ", gewünscht bis " + LocalDate.ofEpochDay(it).format(TASK_DATE) } ?: ""),
            place = "",
            doctor = doctors.firstOrNull { it.id == t.doctorId },
            doctors = doctors,
            person = person,
            onClose = { rideFor = null },
        )
    }

    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Löschen?") },
            text = { Text("„${t.text}“ wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.deleteTodo(t)
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}

/** Aufgabe anlegen oder bearbeiten: Text, Wichtigkeit, Frist, Kontakt, ausser Haus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskDialog(
    initial: Todo?,
    doctors: List<Doctor>,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    history: List<Todo> = emptyList(),
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var priority by remember { mutableStateOf(initial?.priority ?: 1) }
    var due by remember { mutableStateOf(initial?.dueDay?.let { LocalDate.ofEpochDay(it) }) }
    var doctorId by remember { mutableStateOf(initial?.doctorId) }
    var outside by remember { mutableStateOf(initial?.outside ?: false) }
    var outsideTouched by remember { mutableStateOf(initial != null) }
    val today = LocalDate.now()
    val zone = java.time.ZoneId.systemDefault()
    val initialRemind = initial?.remindAt?.takeIf { it > System.currentTimeMillis() }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
    var remindOn by remember { mutableStateOf(initialRemind != null) }
    var remindDate by remember { mutableStateOf(initialRemind?.toLocalDate()) }
    var remindTime by remember { mutableStateOf(initialRemind?.toLocalTime() ?: java.time.LocalTime.of(9, 0)) }
    val context = LocalContext.current
    val remindMillis: Long? = if (remindOn) {
        (remindDate ?: due ?: today.plusDays(1)).atTime(remindTime).atZone(zone).toInstant().toEpochMilli()
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null || initial.id == 0L) "Neue Aufgabe" else "Aufgabe bearbeiten") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Was ist zu tun?") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Automatisch pruefen, ob es frueher schon etwas zum Thema gab
                val past = remember(text, doctorId, history) {
                    TopicMatcher.similar(text, doctorId, history, excludeId = initial?.id ?: 0L)
                }
                PastTopicsBox(past, doctors)
                Text("Wichtigkeit", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2).forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(priorityLabel(p)) })
                    }
                }
                Text("Erledigt bis", style = MaterialTheme.typography.labelLarge)
                DatePickerField(label = "Frist", date = due, onChange = { due = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { due = today }) { Text("Heute") }
                    TextButton(onClick = { due = today.plusDays(1) }) { Text("Morgen") }
                    TextButton(onClick = { due = today.plusDays(7) }) { Text("In 1 Woche") }
                    if (due != null) TextButton(onClick = { due = null }) { Text("Ohne Frist") }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { remindOn = !remindOn },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = remindOn, onCheckedChange = { remindOn = it })
                    Text("Erinnern per Benachrichtigung", style = MaterialTheme.typography.bodyMedium)
                }
                if (remindOn) {
                    DatePickerField(label = "Erinnern am", date = remindDate ?: due ?: today.plusDays(1), onChange = { remindDate = it })
                    TimePickerField(label = "um", time = remindTime, onChange = { remindTime = it })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        due?.let { d ->
                            TextButton(onClick = {
                                remindDate = d
                                remindTime = java.time.LocalTime.of(9, 0)
                            }) { Text("Am Fristtag 9 Uhr") }
                            TextButton(onClick = {
                                remindDate = d.minusDays(1)
                                remindTime = java.time.LocalTime.of(18, 0)
                            }) { Text("Am Vortag 18 Uhr") }
                        }
                        TextButton(onClick = {
                            remindDate = today.plusDays(1)
                            remindTime = java.time.LocalTime.of(9, 0)
                        }) { Text("Morgen 9 Uhr") }
                    }
                    if (remindMillis != null && remindMillis <= System.currentTimeMillis()) {
                        Text("Dieser Zeitpunkt liegt in der Vergangenheit, es kommt keine Erinnerung.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (text.isNotBlank()) {
                    TextButton(onClick = { addTaskToCalendar(context, text.trim(), due?.toEpochDay(), remindMillis) }) {
                        Text("📅 In Kalender eintragen")
                    }
                }
                if (doctors.isNotEmpty()) {
                    DropdownField(
                        label = "Arzt / Stelle (zum direkten Kontakt)",
                        value = doctors.firstOrNull { it.id == doctorId }?.let { doctorLabel(it) } ?: "",
                        options = listOf(NO_CONTACT_TASK) + doctors.map { doctorLabel(it) },
                        editable = false,
                        onValueChange = { chosen ->
                            doctorId = doctors.firstOrNull { doctorLabel(it) == chosen }?.id
                            if (!outsideTouched && doctorId != null) outside = true
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        outside = !outside
                        outsideTouched = true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = outside, onCheckedChange = {
                        outside = it
                        outsideTouched = true
                    })
                    Text("Außer Haus (Fahrdienst anbieten)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(TaskDraft(text.trim(), doctorId, priority, due?.toEpochDay(), outside, remindMillis)) },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- KI-Vorschlaege

private fun norm(s: String) = s.lowercase().replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss").trim()

/** Ordnet "Hausarzt", "Nephrologe" usw. einem eigenen Kontakt zu (nur lokal, nichts wird gesendet). */
internal fun matchDoctor(stelle: String, doctors: List<Doctor>): Long? {
    val s = norm(stelle)
    if (s.length < 3) return null
    return doctors.firstOrNull { d ->
        val spec = norm(d.specialty)
        val name = norm(d.name)
        (spec.length >= 3 && (spec in s || s in spec)) || (name.length >= 3 && (name in s || s in name))
    }?.id
}

/** Liest die JSON-Antwort der KI. Ungueltiges wird ignoriert; Frist und Kontakt werden lokal berechnet. */
internal fun parseTaskSuggestions(answer: String, doctors: List<Doctor>, today: Long): List<TaskDraft> {
    val start = answer.indexOf('[')
    val end = answer.lastIndexOf(']')
    if (start < 0 || end <= start) return emptyList()
    val arr = runCatching { JSONArray(answer.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
    val out = mutableListOf<TaskDraft>()
    for (i in 0 until minOf(arr.length(), 6)) {
        val o = arr.optJSONObject(i) ?: continue
        val text = o.optString("aufgabe").trim().take(220)
        if (text.isBlank()) continue
        val days = if (o.isNull("frist_tage")) null else o.optInt("frist_tage", -1).takeIf { it in 0..365 }
        val prio = when (o.optString("prioritaet").lowercase()) {
            "hoch" -> 2
            "niedrig" -> 0
            else -> 1
        }
        val stelle = o.optString("stelle")
        out.add(
            TaskDraft(
                text = text,
                doctorId = matchDoctor(stelle, doctors),
                priority = prio,
                dueDay = days?.let { today + it },
                outside = o.optBoolean("ausser_haus", false),
            ),
        )
    }
    return out
}

/** Vorschlaege der KI: du waehlst aus, was als Aufgabe uebernommen wird, und kannst den Kontakt aendern. */
@Composable
internal fun TaskSuggestDialog(
    drafts: List<TaskDraft>,
    doctors: List<Doctor>,
    onAdd: (List<TaskDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    var items by remember { mutableStateOf(drafts) }
    var checked by remember { mutableStateOf(drafts.indices.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufgaben vorgeschlagen") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Anhaltspunkte aus dem Text. Hake an, was übernommen werden soll. Du kannst alles später auf der Heute-Seite ändern.",
                    style = MaterialTheme.typography.bodySmall,
                )
                items.forEachIndexed { i, d ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = i in checked,
                                onCheckedChange = { on -> checked = if (on) checked + i else checked - i },
                            )
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(d.text, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(
                                        "Wichtigkeit: " + priorityLabel(d.priority),
                                        d.dueDay?.let { dueText(it) } ?: "ohne Frist",
                                        if (d.outside) "außer Haus" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        if (doctors.isNotEmpty()) {
                            DropdownField(
                                label = "Arzt / Stelle",
                                value = doctors.firstOrNull { it.id == d.doctorId }?.let { doctorLabel(it) } ?: "",
                                options = listOf(NO_CONTACT_TASK) + doctors.map { doctorLabel(it) },
                                editable = false,
                                onValueChange = { chosen ->
                                    val id = doctors.firstOrNull { doctorLabel(it) == chosen }?.id
                                    items = items.toMutableList().also { it[i] = it[i].copy(doctorId = id) }
                                },
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checked.isNotEmpty(),
                onClick = { onAdd(items.filterIndexed { i, _ -> i in checked }) },
            ) { Text("Übernehmen (${checked.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Dokumente mit einer Aufgabe verknuepfen: verknuepfte oben, dann passende Vorschlaege mit Grund, dann alle anderen. */
@Composable
internal fun TaskDocsDialog(
    task: Todo,
    doctor: Doctor?,
    docs: List<Document>,
    doctors: List<Doctor>,
    onSave: (List<Long>) -> Unit,
    onClose: () -> Unit,
) {
    val selected = remember {
        mutableStateListOf<Long>().apply { addAll(task.attachedIds().filter { id -> docs.any { it.id == id } }) }
    }
    var viewDoc by remember { mutableStateOf<Document?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val matches = remember(task.id, docs.size) {
        val text = (task.text + " " + doctor?.specialty.orEmpty() + " " + doctor?.name.orEmpty()).lowercase()
        val refDay = task.dueDay ?: LocalDate.now().toEpochDay()
        DocMatcher.match(
            docs = docs,
            categories = DocMatcher.categoriesFor(text, general = false),
            doctor = doctor,
            keywords = DocMatcher.keywords(listOf(task.text), doctor),
            refDay = refDay,
        )
    }
    val linked = docs.filter { it.id in task.attachedIds() }.sortedByDescending { docEpochDay(it) }
    val suggested = matches.filter { m -> linked.none { it.id == m.doc.id } }
    val others = docs.filter { d -> linked.none { it.id == d.id } && suggested.none { it.doc.id == d.id } }
        .sortedByDescending { docEpochDay(it) }

    fun toggle(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Unterlagen: ${task.text.take(40)}") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (docs.isEmpty()) {
                    Text(
                        "Noch keine Dokumente gespeichert. Im Tab „Dokumente“ kannst du Berichte, Befunde und Rezepte fotografieren oder als Datei laden und sie dann hier verknüpfen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    if (linked.isNotEmpty()) {
                        Text("Verknüpft", style = MaterialTheme.typography.labelLarge)
                        linked.forEach { d -> DocRow(d, null, d.id in selected, { toggle(d.id) }, { viewDoc = d }) }
                    }
                    if (suggested.isNotEmpty()) {
                        Text("Passt vermutlich zur Aufgabe", style = MaterialTheme.typography.labelLarge)
                        suggested.forEach { m ->
                            DocRow(m.doc, m.reasonText, m.doc.id in selected, { toggle(m.doc.id) }, { viewDoc = m.doc })
                        }
                    } else if (linked.isEmpty()) {
                        Text(
                            "Zu dieser Aufgabe passt auf den ersten Blick kein Dokument. Unten kannst du selbst auswählen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (others.isNotEmpty()) {
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(if (showAll) "Weitere Dokumente ausblenden" else "Weitere Dokumente anzeigen (${others.size})")
                        }
                        if (showAll) others.forEach { d -> DocRow(d, null, d.id in selected, { toggle(d.id) }, { viewDoc = d }) }
                    }
                    Text(
                        "Die Vorschläge kommen aus Titel, Kategorie, Arzt, Stichwörtern im Text und Datum, nur auf deinem Handy berechnet. Bitte selbst prüfen, ob es passt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("Fertig") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )

    viewDoc?.let { d ->
        DocumentDetailDialog(doc = d, doctors = doctors, onEdit = null, onClose = { viewDoc = null })
    }
}
