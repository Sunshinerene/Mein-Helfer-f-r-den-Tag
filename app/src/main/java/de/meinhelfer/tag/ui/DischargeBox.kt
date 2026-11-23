package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import android.widget.Toast
import de.meinhelfer.tag.ai.DischargeAi
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.summary.Discharge
import de.meinhelfer.tag.summary.FollowUp
import de.meinhelfer.tag.summary.MedPlan
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private const val NO_CONTACT_FOLLOW = "– keine Auswahl –"

/**
 * Im Dokument (Entlassbericht): Medikation bei Entlassung und Kontrollen als Vorschläge.
 * Erkannt wird auf dem Handy; auf Wunsch genauer per KI. Übernommen wird nur, was du bestätigst.
 */
@Composable
fun DischargeBox(doc: Document, person: Person?, meds: List<Medication>, doctors: List<Doctor>, vm: MainViewModel) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val local = remember(doc.text, doc.docDate) { Discharge.read(doc.text, doc.docDate) }
    var plan by remember(doc.id) { mutableStateOf(local.plan.takeIf { it.meds.isNotEmpty() }) }
    var followUps by remember(doc.id) { mutableStateOf(local.followUps) }
    var stopped by remember(doc.id) { mutableStateOf(local.stopped) }
    var byAi by remember(doc.id) { mutableStateOf(false) }
    var reviewMeds by remember { mutableStateOf(false) }
    var reviewFollow by remember { mutableStateOf(false) }
    val personId = person?.id ?: doc.personId
    val baseDay = local.dischargeDay ?: doc.docDate ?: today.toEpochDay()

    HCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Aus dem Entlassbericht", style = MaterialTheme.typography.titleSmall)
            local.dischargeDay?.let {
                Text("Entlassen am " + LocalDate.ofEpochDay(it).format(DAY_FORMAT), style = MaterialTheme.typography.bodySmall)
            }
            if (byAi) Text("Von der KI ausgelesen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            // Medikamente
            val p = plan
            Text(
                when {
                    p == null -> "Medikamente: keine sicher erkannt."
                    local.fromSection && !byAi -> "Medikamente bei Entlassung: ${p.meds.size} erkannt (Abschnitt „Medikation bei Entlassung“)."
                    else -> "Medikamente: ${p.meds.size} erkannt."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (stopped.isNotEmpty()) {
                Text(
                    "Laut Bericht abgesetzt oder pausiert: " + stopped.joinToString("; "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (p != null) {
                Button(shape = buttonShape(), onClick = { reviewMeds = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Medikamente prüfen und übernehmen")
                }
            }

            // Kontrollen und Termine
            HorizontalDivider()
            if (followUps.isEmpty()) {
                Text("Kontrollen und Termine: keine erkannt.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Kontrollen und Termine: ${followUps.size} erkannt", style = MaterialTheme.typography.bodyMedium)
                followUps.take(4).forEach { f ->
                    Text("• " + f.title + (followUpWhen(f)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
                Button(shape = buttonShape(), onClick = { reviewFollow = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Als Aufgabe oder Termin übernehmen")
                }
            }

            AiActionButton(
                label = "Mit KI genauer auslesen",
                note = "Gesendet wird der erkannte Text des Berichts; Name, Geburtsdatum, Versichertennummer und Straße der Person werden vorher ersetzt. Namen deiner Kontakte werden nicht gesendet.",
                system = DischargeAi.SYSTEM_PROMPT,
                maxTokens = 4000,
                prompt = { DischargeAi.prompt(maskPersonal(doc.text, person)) },
                task = de.meinhelfer.tag.ai.AiTask.Plan,
                onAnswer = { answer, _ ->
                    val parsed = DischargeAi.parse(answer, LocalDate.ofEpochDay(baseDay))
                    if (parsed != null) {
                        parsed.first?.let { plan = it }
                        if (parsed.second.isNotEmpty()) followUps = parsed.second
                        if (parsed.third.isNotEmpty()) stopped = parsed.third
                        byAi = true
                    }
                    parsed != null
                },
            )
            Text(
                "Nur Vorschläge aus dem Text, bitte mit dem Bericht vergleichen. Übernommen wird nur, was du bestätigst. " +
                    "Bei Fragen zu Medikamenten zählt, was Ärztin, Arzt oder Apotheke sagen.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    val current = plan
    if (reviewMeds && current != null) {
        MedPlanReviewDialog(
            plan = current,
            planDay = current.day ?: baseDay,
            meds = meds,
            personId = personId,
            docId = doc.id,
            vm = vm,
            onDone = { reviewMeds = false },
        )
    }
    if (reviewFollow) {
        FollowUpDialog(
            items = followUps,
            doctors = doctors,
            onConfirm = { tasks, appointments ->
                if (tasks.isNotEmpty()) vm.addTasks(tasks.map { it.copy(docIds = doc.id.toString()) })
                appointments.forEach { vm.add(it.copy(docIds = doc.id.toString())) }
                reviewFollow = false
                Toast.makeText(
                    context,
                    listOfNotNull(
                        tasks.size.takeIf { it > 0 }?.let { "$it Aufgabe(n)" },
                        appointments.size.takeIf { it > 0 }?.let { "$it Termin(e)" },
                    ).joinToString(" und ") + " übernommen.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { reviewFollow = false },
        )
    }
}

/** „am 03.12.2026, 10:30 Uhr“ bzw. „um den 19.11.2026“ */
private fun followUpWhen(f: FollowUp): String? {
    val d = f.day?.let { LocalDate.ofEpochDay(it).format(DAY_FORMAT) } ?: return null
    return if (f.exact) "am $d" + (f.time?.let { ", $it Uhr" } ?: "") else "etwa $d"
}

private class FollowRow(f: FollowUp, doctors: List<Doctor>) {
    val source = f
    var use by mutableStateOf(true)
    var title by mutableStateOf(f.title)
    var day by mutableStateOf(f.day?.let { LocalDate.ofEpochDay(it) })
    var time by mutableStateOf(f.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() })

    /** Genaues Datum mit Uhrzeit: gleich als Termin eintragen, sonst Aufgabe „Termin vereinbaren“. */
    var asAppointment by mutableStateOf(f.exact && f.time != null)
    var doctorId by mutableStateOf(matchDoctor(f.place, doctors))
}

/** Kontrollen prüfen: als Aufgabe „Termin vereinbaren“ (Frist eine Woche vorher) oder gleich als Termin. */
@Composable
private fun FollowUpDialog(
    items: List<FollowUp>,
    doctors: List<Doctor>,
    onConfirm: (List<TaskDraft>, List<Appointment>) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember(items) { items.map { FollowRow(it, doctors) } }
    val today = LocalDate.now()
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text("Kontrollen übernehmen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Anhaltspunkte aus dem Bericht. Ohne festen Termin wird eine Aufgabe „Termin vereinbaren“ angelegt, fällig eine Woche vor dem genannten Zeitpunkt. " +
                        "Der Bericht wird jeweils als Unterlage verknüpft.",
                    style = MaterialTheme.typography.bodySmall,
                )
                rows.forEach { r ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.use, onCheckedChange = { r.use = it })
                        OutlinedTextField(
                            value = r.title,
                            onValueChange = { r.title = it },
                            label = { Text("Was") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (r.source.sentence.isNotBlank()) {
                        Text("„" + r.source.sentence + "“", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                    if (r.use) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !r.asAppointment, onClick = { r.asAppointment = false })
                            Text("Aufgabe: Termin vereinbaren", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = r.asAppointment, onClick = { r.asAppointment = true })
                            Text("Termin steht schon fest: eintragen", style = MaterialTheme.typography.bodyMedium)
                        }
                        DatePickerField(
                            label = if (r.asAppointment) "Datum des Termins" else "Ungefähr bis wann (Kontrolle)",
                            date = r.day,
                            onChange = { r.day = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (r.asAppointment) {
                            TimePickerField(label = "Uhrzeit", time = r.time, onChange = { r.time = it }, modifier = Modifier.fillMaxWidth())
                            if (r.day == null || r.time == null) {
                                Text("Für einen Termin bitte Datum und Uhrzeit wählen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Text(
                                r.day?.let { d ->
                                    val due = maxOf(d.minusDays(7), today)
                                    "Frist der Aufgabe: " + due.format(DAY_FORMAT)
                                } ?: "Ohne Datum: Aufgabe ohne Frist.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (doctors.isNotEmpty()) {
                            DropdownField(
                                label = "Arzt / Stelle",
                                value = doctors.firstOrNull { it.id == r.doctorId }?.let { doctorLabel(it) } ?: "",
                                options = listOf(NO_CONTACT_FOLLOW) + doctors.map { doctorLabel(it) },
                                editable = false,
                                onValueChange = { chosen -> r.doctorId = doctors.firstOrNull { doctorLabel(it) == chosen }?.id },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = rows.filter { it.use && it.title.isNotBlank() }
            val valid = chosen.isNotEmpty() && chosen.none { it.asAppointment && (it.day == null || it.time == null) }
            TextButton(enabled = valid, onClick = {
                val tasks = mutableListOf<TaskDraft>()
                val appointments = mutableListOf<Appointment>()
                chosen.forEach { r ->
                    val d = r.day
                    val t = r.time
                    if (r.asAppointment && d != null && t != null) {
                        appointments += Appointment(
                            title = r.title.trim(),
                            whenMillis = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            notes = "Aus dem Entlassbericht" + (r.source.sentence.takeIf { it.isNotBlank() }?.let { ": „$it“" } ?: ""),
                            doctorId = r.doctorId,
                        )
                    } else {
                        tasks += TaskDraft(
                            text = "Termin vereinbaren: " + r.title.trim() + (d?.let { " (Kontrolle etwa am " + it.format(DAY_FORMAT) + ")" } ?: ""),
                            doctorId = r.doctorId,
                            priority = 1,
                            dueDay = d?.let { maxOf(it.minusDays(7), today).toEpochDay() },
                        )
                    }
                }
                onConfirm(tasks, appointments)
            }) { Text("Übernehmen (${chosen.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
