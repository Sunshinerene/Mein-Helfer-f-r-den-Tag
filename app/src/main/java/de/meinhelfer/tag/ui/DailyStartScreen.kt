package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.export.DayPlanPdf
import de.meinhelfer.tag.export.PdfPrintAdapter
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.summary.DayPlan
import de.meinhelfer.tag.summary.Period
import de.meinhelfer.tag.summary.Summary
import de.meinhelfer.tag.summary.SummaryBuilder
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tagesstart (beim ersten Oeffnen am Tag oder auf Wunsch):
 * 1. Rueckblick auf gestern zum Ergaenzen und Bestaetigen, 2. Tagesplan fuer heute, als PDF teilbar und druckbar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DailyStartScreen(
    vm: MainViewModel,
    person: Person?,
    meds: List<Medication>,
    appointments: List<Appointment>,
    todos: List<Todo>,
    doctors: List<Doctor>,
    checkins: List<Checkin>,
    todayDay: Long,
    startWithPlan: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val yesterday = todayDay - 1
    val history by vm.taskHistory.collectAsStateWithLifecycle()
    val edit = remember { OverviewEdit() }

    val ydMeds = meds.filter { yesterday >= it.createdDay && it.timeList().isNotEmpty() }
    val ydAppts = appointments.filter { Instant.ofEpochMilli(it.whenMillis).atZone(zone).toLocalDate().toEpochDay() == yesterday }
        .sortedBy { it.whenMillis }
    val ydDone = history.filter { t ->
        t.done && t.doneMillis != null && Instant.ofEpochMilli(t.doneMillis).atZone(zone).toLocalDate().toEpochDay() == yesterday
    }
    val nothingYesterday = ydMeds.isEmpty() && ydAppts.isEmpty() && ydDone.isEmpty()
    var step by remember { mutableIntStateOf(if (startWithPlan || nothingYesterday) 1 else 0) }

    // Offene Antworten von gestern
    val ydChecks = checkins.filter { it.day == yesterday }
    val openIntakes = ydMeds.sumOf { m ->
        m.timeList().map { SummaryBuilder.normSlot(it) }.distinct().count { time ->
            val c = ydChecks.firstOrNull { it.kind == Checkin.MED && it.refId == m.id && SummaryBuilder.normSlot(it.slot) == time }
            c == null || (!c.done && c.status.isEmpty())
        }
    }
    val openAppts = ydAppts.count { a ->
        val c = ydChecks.firstOrNull { it.kind == Checkin.APPT && it.refId == a.id }
        c == null || (!c.done && c.status.isEmpty())
    }

    var summary by remember { mutableStateOf<Summary?>(null) }
    LaunchedEffect(ydChecks) { summary = vm.buildSummary(Period.Day, -1) }
    var note by remember { mutableStateOf("") }
    var noteLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(yesterday) {
        note = vm.dayNoteText(yesterday)
        noteLoaded = true
    }
    val confirmedAt = AppPrefs.reviewConfirmed(context, person?.id ?: 0L, yesterday)

    val today = LocalDate.ofEpochDay(todayDay)
    val sections = DayPlan.build(person, meds, appointments, todos, doctors, today)
    val planTitle = "Tagesplan" + (person?.name?.let { " – $it" } ?: "")
    val planSubtitle = today.format(DayPlan.TITLE_DATE)

    suspend fun pdf(): File = withContext(Dispatchers.IO) { DayPlanPdf.create(context, planTitle, planSubtitle, sections, today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (step == 0) "Rückblick auf gestern" else "Heute zu tun") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            LocalDate.ofEpochDay(yesterday).format(DayPlan.TITLE_DATE) + (person?.name?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Bitte kurz prüfen und ergänzen. Was noch fehlt, ist rot markiert. Danach bestätigen, dann kommt der Tagesplan für heute.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (confirmedAt > 0) {
                            Text("Bereits bestätigt am ${consentTime(confirmedAt)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (openIntakes + openAppts > 0) {
                    item {
                        Text(
                            "Noch ohne Antwort: " + listOfNotNull(
                                if (openIntakes > 0) "$openIntakes Einnahme(n)" else null,
                                if (openAppts > 0) "$openAppts Termin(e)" else null,
                            ).joinToString(" und "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                summary?.let { s ->
                    item {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Zusammenfassung", style = MaterialTheme.typography.titleSmall)
                                (s.overview + s.notable).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                if (ydMeds.isNotEmpty()) {
                    item {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                                Text("Einnahmen gestern", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                                IntakeRows(yesterday, todayDay, meds, checkins, vm, edit)
                            }
                        }
                    }
                }
                if (ydAppts.isNotEmpty()) {
                    item {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                                Text("Termine gestern", style = MaterialTheme.typography.titleSmall)
                                ydAppts.forEach { a ->
                                    val c = ydChecks.firstOrNull { it.kind == Checkin.APPT && it.refId == a.id }
                                    val open = c == null || (!c.done && c.status.isEmpty())
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                            Text(formatAppointmentTime(a.whenMillis, a.endMillis) + " – " + a.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                if (open) "noch keine Antwort" else if (c?.done == true && c.status.isEmpty()) "hat stattgefunden" else statusText(c).orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (open) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = {
                                            edit.status = StatusTarget(Checkin.APPT, a.id, yesterday, "", a.title, "Wie ist der Termin gelaufen?", APPT_OPTIONS, c, a)
                                        }) { Text(if (open) "Antworten" else "Ändern") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (ydDone.isNotEmpty()) {
                    item {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Gestern erledigt", style = MaterialTheme.typography.titleSmall)
                                ydDone.forEach { t ->
                                    Text("✓ ${t.text}" + (if (t.result.isNotBlank()) " – ${t.result}" else ""), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                item {
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Bemerkung zu gestern", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                enabled = noteLoaded,
                                placeholder = { Text("Wie lief der Tag? Was ist aufgefallen?") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Column {
                        Button(shape = buttonShape(), modifier = Modifier.fillMaxWidth(), onClick = {
                            if (noteLoaded) vm.saveDayNote(yesterday, note)
                            AppPrefs.setReviewConfirmed(context, person?.id ?: 0L, yesterday, System.currentTimeMillis())
                            step = 1
                        }) { Text("Bestätigen und weiter zum Tagesplan") }
                        TextButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Überspringen") }
                    }
                }
            } else {
                item {
                    Column {
                        Text(planSubtitle + (person?.name?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                        if (!nothingYesterday) {
                            TextButton(onClick = { step = 0 }, contentPadding = PaddingValues(0.dp)) { Text("‹ Rückblick auf gestern") }
                        }
                    }
                }
                if (de.meinhelfer.tag.update.AppPrefs.motivatorInDailyStart(context)) {
                    item { DailyMotivatorBlock(vm) }
                }
                if (sections.isEmpty()) {
                    item { Text("Für heute ist nichts eingetragen.", style = MaterialTheme.typography.bodyLarge) }
                }
                sections.forEach { s ->
                    item {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleSmall, color = if (s.title.startsWith("Aufgaben: heute")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                s.lines.forEach { l ->
                                    Column {
                                        Text((if (l.check) "☐ " else "") + l.text, style = MaterialTheme.typography.bodyMedium)
                                        l.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = if (l.check) 22.dp else 0.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(shape = buttonShape(), onClick = {
                            scope.launch { runCatching { pdf() }.onSuccess { sharePdf(context, it) }.onFailure { toast(context, "PDF konnte nicht erstellt werden.") } }
                        }) { Text("Als PDF teilen") }
                        OutlinedButton(shape = buttonShape(), onClick = {
                            scope.launch {
                                runCatching { pdf() }.onSuccess { f ->
                                    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                    pm.print(f.nameWithoutExtension, PdfPrintAdapter(f, f.name), PrintAttributes.Builder().build())
                                }.onFailure { toast(context, "Drucken ist nicht möglich.") }
                            }
                        }) { Text("Drucken") }
                    }
                }
                item {
                    Column {
                    Button(shape = buttonShape(), onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
                    Text(
                        "Den Tagesstart findest du jederzeit oben auf der Heute-Seite. Automatisch erscheint er einmal am Tag (abschaltbar unter Einstellungen → Heute-Seite, Motivator und Widget).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    }
                }
            }
        }
    }
    OverviewEditors(edit, doctors, vm)
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun sharePdf(context: Context, file: File) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, DocumentStore.uriFor(context, file))
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension.replace('_', ' '))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Tagesplan teilen"))
    } catch (_: Exception) {
        toast(context, "Teilen ist nicht möglich.")
    }
}

/** Motivator im Tagesstart: Satz fuer den Tag, Geschafftes und Vorhaben. */
@Composable
private fun DailyMotivatorBlock(vm: MainViewModel) {
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val p = plan ?: return
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.greeting, style = MaterialTheme.typography.titleSmall)
                Text(p.sentence, style = MaterialTheme.typography.bodyLarge, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                if (p.achieved.isNotEmpty()) Text("Geschafft: " + p.achieved.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text("Vorhaben für heute", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
            p.intentions.take(4).forEach { IntentionRow(it, vm) }
        }
    }
}
