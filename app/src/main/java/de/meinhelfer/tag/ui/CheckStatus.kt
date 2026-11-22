package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.reminder.ReminderScheduler
import java.time.LocalTime

/** Wann darf eine Einnahme normal abgehakt werden? Davor und danach wird nachgefragt, wie es lief. */
internal enum class IntakePhase { Before, Open, Past }

internal fun intakePhase(day: Long, slot: String, today: Long, nowMinutes: Int, window: Int): IntakePhase {
    if (day < today) return IntakePhase.Past
    if (day > today) return IntakePhase.Before
    val t = ReminderScheduler.parseTime(slot)?.let { it.first * 60 + it.second } ?: return IntakePhase.Open
    return when {
        nowMinutes < t - window -> IntakePhase.Before
        nowMinutes <= t + window -> IntakePhase.Open
        else -> IntakePhase.Past
    }
}

/** "07:30" fuer eine Minutenzahl seit Mitternacht (nicht unter 0). */
internal fun clockText(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Eine Antwortmoeglichkeit im Status-Dialog. */
internal class StatusOption(
    val status: String,
    val label: String,
    /** Zaehlt als genommen bzw. stattgefunden. */
    val done: Boolean,
    val askTime: Boolean = false,
)

internal val MED_PAST_OPTIONS = listOf(
    StatusOption(Checkin.UNTICKED, "Zur Zeit genommen, nur nicht abgehakt", true),
    StatusOption(Checkin.LATE, "Später genommen", true, askTime = true),
    StatusOption(Checkin.MISSED, "Vergessen, nicht genommen", false),
    StatusOption(Checkin.SKIPPED, "Bewusst nicht gegeben (z. B. abgelehnt oder auf Anweisung)", false),
)

internal val MED_BEFORE_OPTIONS = listOf(
    StatusOption(Checkin.EARLY, "Schon früher genommen", true, askTime = true),
    StatusOption(Checkin.SKIPPED, "Bewusst nicht gegeben (z. B. abgelehnt oder auf Anweisung)", false),
)

internal val APPT_OPTIONS = listOf(
    StatusOption("", "Hat stattgefunden", true),
    StatusOption(Checkin.CANCELLED, "Abgesagt oder ausgefallen", false),
    StatusOption(Checkin.MOVED, "Verschoben", false),
)

/** Kurzer Text zum Status fuer die Zeile, z. B. "später genommen um 09:30 Uhr". */
internal fun statusText(c: Checkin?): String? {
    if (c == null) return null
    val at = c.actual.takeIf { it.isNotBlank() }?.let { " um $it Uhr" }.orEmpty()
    return when (c.status) {
        Checkin.LATE -> "später genommen$at"
        Checkin.EARLY -> "früher genommen$at"
        Checkin.UNTICKED -> "zur Zeit genommen (nicht abgehakt)"
        Checkin.MISSED -> "vergessen"
        Checkin.SKIPPED -> "bewusst nicht gegeben"
        Checkin.CANCELLED -> "abgesagt bzw. ausgefallen"
        Checkin.MOVED -> "verschoben"
        else -> null
    }
}

/** Das, was im Status-Dialog gerade beantwortet wird. */
internal class StatusTarget(
    val kind: String,
    val refId: Long,
    val day: Long,
    val slot: String,
    val title: String,
    val intro: String?,
    val options: List<StatusOption>,
    val current: Checkin?,
    /** Gesetzt bei Terminen: bei Absage oder Verschiebung kann eine Folge-Aufgabe angelegt werden. */
    val appt: Appointment? = null,
)

/**
 * Fragt nach, wie es gelaufen ist (genommen, spaeter, vergessen, bewusst nicht, Termin abgesagt ...).
 * Die Antworten fliessen in die Auswertung ein. Bei Terminen kann gleich eine Aufgabe "Neuen Termin ausmachen" angelegt werden.
 */
@Composable
internal fun StatusDialog(
    title: String,
    intro: String?,
    options: List<StatusOption>,
    current: Checkin?,
    taskLabel: String?,
    taskStatuses: Set<String>,
    onSave: (StatusOption, String, String, Boolean) -> Unit,
    onReset: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val preselected = current?.let { c ->
        options.firstOrNull { it.status == c.status && it.done == c.done }
            ?: if (c.done) options.firstOrNull { it.status == Checkin.UNTICKED } else null
    }
    var selected by remember { mutableStateOf(preselected) }
    var time by remember { mutableStateOf(current?.actual?.let { ReminderScheduler.parseTime(it) }?.let { LocalTime.of(it.first, it.second) }) }
    var note by remember { mutableStateOf(current?.note.orEmpty()) }
    var createTask by remember { mutableStateOf(true) }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                intro?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                options.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = opt },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected === opt, onClick = { selected = opt })
                        Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val sel = selected
                if (sel != null && sel.askTime) {
                    TimePickerField(
                        label = "Um wie viel Uhr? (freiwillig)",
                        time = time,
                        onChange = { time = it },
                    )
                }
                if (sel != null && taskLabel != null && sel.status in taskStatuses) {
                    Row(
                        Modifier.fillMaxWidth().clickable { createTask = !createTask },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = createTask, onCheckedChange = { createTask = it })
                        Text(taskLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Bemerkung (freiwillig)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Zurücksetzen (nichts eingetragen)") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    selected?.let { opt ->
                        val actual = if (opt.askTime) time?.format(TIME_FORMAT).orEmpty() else ""
                        onSave(opt, actual, note, createTask)
                    }
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
