package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.DayNote
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.summary.SummaryBuilder
import de.meinhelfer.tag.update.UpdateState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val MED_SUGGESTIONS = listOf(
    "Später genommen", "Vergessen", "Verweigert", "Mit Essen genommen", "Nebenwirkung aufgefallen", "Vorrat fast leer",
)
private val APPT_SUGGESTIONS = listOf(
    "Verschoben", "Abgesagt", "Alles gut besprochen", "Neuer Termin nötig", "Rezept bekommen", "Überweisung bekommen",
)
private val DAY_SUGGESTIONS = listOf(
    "Guter Tag", "Wenig gegessen", "Wenig getrunken", "Unruhig", "Schlecht geschlafen", "Viel Schmerzen",
)

/** Was gerade als Bemerkung bearbeitet wird. */
private data class NoteTarget(
    val kind: String,
    val refId: Long,
    val day: Long,
    val slot: String,
    val title: String,
    val initial: String,
    val suggestions: List<String>,
)

@Composable
fun TodayScreen(
    vm: MainViewModel,
    meds: List<Medication>,
    appointments: List<Appointment>,
    doctors: List<Doctor>,
    checkins: List<Checkin>,
    dayNote: DayNote?,
    todos: List<Todo>,
    todayDay: Long,
    nowMillis: Long,
    updateState: UpdateState,
    onOpenSettings: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenReorder: () -> Unit = {},
    person: Person? = null,
    onEditProfile: () -> Unit = {},
    onOpenWegweiser: () -> Unit = {},
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.ofEpochDay(todayDay)
    val nowTime = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()

    var noteTarget by remember { mutableStateOf<NoteTarget?>(null) }
    var editDayNote by remember { mutableStateOf(false) }

    val medChecks = checkins.filter { it.kind == Checkin.MED && it.day == todayDay }
        .associateBy { SummaryBuilder.normSlot(it.slot) to it.refId }
    val apptChecks = checkins.filter { it.kind == Checkin.APPT }.associateBy { it.refId to it.day }

    // Einnahmen von heute (nur Medikamente, die schon angelegt waren)
    val intakes = meds
        .flatMap { m -> m.timeList().map { SummaryBuilder.normSlot(it) }.distinct().map { time -> time to m } }
        .sortedBy { it.first }
    val intakesDone = intakes.count { (time, m) -> medChecks[time to m.id]?.done == true }

    // Termine: heute und die naechsten 7 Tage
    val upcoming = appointments.filter {
        val d = Instant.ofEpochMilli(it.whenMillis).atZone(zone).toLocalDate()
        !d.isBefore(today) && !d.isAfter(today.plusDays(7))
    }.sortedBy { it.whenMillis }

    val lowStock = meds.filter { (it.daysLeft(todayDay) ?: Int.MAX_VALUE) <= 7 }

    val newVersion = when (updateState) {
        is UpdateState.Available -> updateState.info.version
        is UpdateState.Downloading -> updateState.info.version
        is UpdateState.Ready -> updateState.info.version
        else -> null
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (newVersion != null) {
            item {
                HCard(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Neue Version $newVersion verfügbar", style = MaterialTheme.typography.titleMedium)
                        Text("Tippen, um zu den Einstellungen zu gelangen und das Update zu laden.")
                    }
                }
            }
        }

        item {
            ProfileCard(person, todayDay, onEditProfile, onOpenWegweiser)
        }

        if (lowStock.isNotEmpty()) {
            item {
                HCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bald nachbestellen", style = MaterialTheme.typography.titleMedium)
                        lowStock.forEach {
                            val d = it.daysLeft(todayDay) ?: 0
                            Text(
                                if (d <= 0) "${it.name}: Vorrat aufgebraucht" else "${it.name}: reicht noch ca. $d Tage",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = onOpenReorder) { Text("Nachbestellung öffnen") }
                    }
                }
            }
        }

        // ---- Einnahmen
        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                    Text(
                        "Heute einnehmen" + if (intakes.isNotEmpty()) " ($intakesDone von ${intakes.size})" else "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    if (intakes.isEmpty()) {
                        Text(
                            "Noch keine Einnahmezeiten eingetragen.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                    }
                    intakes.forEach { (time, m) ->
                        val check = medChecks[time to m.id]
                        val done = check?.done == true
                        val passed = !(parseSlot(time)?.isAfter(nowTime) ?: false)
                        val label = "$time Uhr – ${m.name}" + if (m.dosage.isNotBlank()) " (${m.dosage})" else ""
                        CheckRow(
                            done = done,
                            title = label,
                            subtitle = if (!done && passed) "noch nicht abgehakt" else null,
                            warn = !done && passed,
                            note = check?.note.orEmpty(),
                            onToggle = { vm.saveCheckin(Checkin.MED, m.id, todayDay, time, done = it) },
                            onNote = {
                                noteTarget = NoteTarget(
                                    Checkin.MED, m.id, todayDay, time, "Bemerkung: ${m.name}, $time Uhr",
                                    check?.note.orEmpty(), MED_SUGGESTIONS,
                                )
                            },
                        )
                    }
                }
            }
        }

        // ---- Termine
        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                    Text(
                        "Termine in den nächsten 7 Tagen",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    if (upcoming.isEmpty()) {
                        Text(
                            "Keine anstehenden Termine.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                    }
                    upcoming.forEach { a ->
                        val day = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
                        val check = apptChecks[a.id to day]
                        val done = check?.done == true
                        val doctor = doctors.firstOrNull { it.id == a.doctorId }
                        CheckRow(
                            done = done,
                            title = formatAppointmentTime(a.whenMillis, a.endMillis) + " – " + a.title,
                            subtitle = doctor?.let { doctorLabel(it) },
                            warn = false,
                            note = check?.note.orEmpty(),
                            onToggle = { vm.saveCheckin(Checkin.APPT, a.id, day, "", done = it) },
                            onNote = {
                                noteTarget = NoteTarget(
                                    Checkin.APPT, a.id, day, "", "Bemerkung: ${a.title}",
                                    check?.note.orEmpty(), APPT_SUGGESTIONS,
                                )
                            },
                        )
                    }
                }
            }
        }

        // ---- Aufgaben
        item { TasksCard(todos = todos, doctors = doctors, person = person, vm = vm) }

        // ---- Bemerkung zum Tag
        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bemerkung zum Tag", style = MaterialTheme.typography.titleMedium)
                    Text(
                        dayNote?.text?.takeIf { it.isNotBlank() }
                            ?: "Noch nichts notiert, z. B. wie der Tag lief oder was aufgefallen ist.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { editDayNote = true }) {
                        Text(if (dayNote?.text.isNullOrBlank()) "Bemerkung schreiben" else "Bemerkung ändern")
                    }
                }
            }
        }

        // ---- Auswertung
        item {
            HCard(
                onClick = onOpenSummary,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Auswertung", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Zusammenfassung für Tag, Woche oder Monat mit Auffälligem und Empfehlungen. Tippen zum Öffnen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Text(
                "Mein Helfer für den Tag · by $AUTHOR",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    noteTarget?.let { t ->
        NoteDialog(
            title = t.title,
            initial = t.initial,
            suggestions = t.suggestions,
            onDismiss = { noteTarget = null },
            onSave = { text ->
                vm.saveCheckin(t.kind, t.refId, t.day, t.slot, note = text)
                noteTarget = null
            },
        )
    }
    if (editDayNote) {
        NoteDialog(
            title = "Bemerkung zum Tag",
            initial = dayNote?.text.orEmpty(),
            suggestions = DAY_SUGGESTIONS,
            onDismiss = { editDayNote = false },
            onSave = { text ->
                vm.saveDayNote(todayDay, text)
                editDayNote = false
            },
        )
    }
}

private fun parseSlot(slot: String): LocalTime? =
    de.meinhelfer.tag.reminder.ReminderScheduler.parseTime(slot)?.let { (h, m) -> LocalTime.of(h, m) }

/** Zeile mit Haken, Text und Bemerkungs-Knopf. */
@Composable
private fun ProfileCard(person: Person?, todayDay: Long, onEdit: () -> Unit, onWegweiser: () -> Unit) {
    if (person == null) return
    val age = person.birthDate?.let { java.time.Period.between(LocalDate.ofEpochDay(it), LocalDate.ofEpochDay(todayDay)).years }
        ?.takeIf { it >= 0 }
    val head = listOfNotNull(
        age?.let { "$it Jahre" },
        person.careLevel.takeIf { it in 1..5 }?.let { "Pflegegrad $it" },
    ).joinToString(" · ")
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            if (head.isNotEmpty()) Text(head, style = MaterialTheme.typography.bodyMedium)
            if (person.insurer.isNotBlank()) Text("Krankenkasse: ${person.insurer}", style = MaterialTheme.typography.bodyMedium)
            if (person.important.isNotBlank()) {
                Text("Wichtig: ${person.important}", style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            }
            if (head.isEmpty() && person.important.isBlank() && person.insurer.isBlank()) {
                Text(
                    "Du kannst im Profil Geburtsdatum, Pflegegrad und Wichtiges eintragen. Das bleibt nur in der App.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Profil bearbeiten") }
                TextButton(onClick = onWegweiser, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Wegweiser Leistungen") }
            }
        }
    }
}

@Composable
private fun CheckRow(
    done: Boolean,
    title: String,
    subtitle: String?,
    warn: Boolean,
    note: String,
    onToggle: (Boolean) -> Unit,
    onNote: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = onToggle)
        Column(
            Modifier.weight(1f).clickable { onToggle(!done) }.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warn) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
            if (note.isNotBlank()) {
                Text(
                    "Bemerkung: $note",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
        TextButton(onClick = onNote, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(if (note.isBlank()) "Notiz" else "Ändern", maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteDialog(
    title: String,
    initial: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Bemerkung") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Schnellauswahl (antippen fügt den Text an):", style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    suggestions.forEach { s ->
                        AssistChip(
                            onClick = { text = if (text.isBlank()) s else "${text.trim()}; $s" },
                            label = { Text(s) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
