package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.meinhelfer.tag.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy HH:mm", Locale.GERMANY)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT)

/** "08:00,20:00" -> Liste von Uhrzeiten (ungueltige Eintraege werden ignoriert). */
fun parseTimes(text: String): List<LocalTime> =
    text.split(",")
        .mapNotNull { ReminderScheduler.parseTime(it) }
        .map { (hour, minute) -> LocalTime.of(hour, minute) }
        .distinct()
        .sorted()

/** Dropdown mit Vorschlaegen. Bei [editable] kann zusaetzlich frei getippt werden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !editable,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PickerButton(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text(value, maxLines = 1) }
    }
}

/** Datumsauswahl mit Kalenderansicht. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    date: LocalDate?,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    PickerButton(label, date?.format(DATE_FORMAT) ?: "Datum wählen", modifier) { show = true }

    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        show = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Uhrzeitauswahl (24 Stunden). */
@Composable
fun TimePickerField(
    label: String,
    time: LocalTime?,
    onChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    PickerButton(label, time?.format(TIME_FORMAT)?.plus(" Uhr") ?: "Uhrzeit wählen", modifier) { show = true }

    if (show) {
        TimePickerDialog(
            initial = time ?: LocalTime.of(9, 0),
            onConfirm = {
                onChange(it)
                show = false
            },
            onDismiss = { show = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
                }
            }
        }
    }
}

/** Einnahmezeiten: feste Tageszeiten per Antippen, weitere Uhrzeiten per Zeitauswahl. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimesChooser(times: List<LocalTime>, onChange: (List<LocalTime>) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val presets = listOf(
        "Morgens" to LocalTime.of(8, 0),
        "Mittags" to LocalTime.of(12, 0),
        "Abends" to LocalTime.of(18, 0),
        "Nachts" to LocalTime.of(22, 0),
    )

    Column {
        Text("Einnahmezeiten", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            presets.forEach { (name, time) ->
                FilterChip(
                    selected = time in times,
                    onClick = { onChange(if (time in times) times - time else (times + time).sorted()) },
                    label = { Text("$name ${time.format(TIME_FORMAT)}") },
                )
            }
            times.filter { time -> presets.none { it.second == time } }.forEach { time ->
                FilterChip(
                    selected = true,
                    onClick = { onChange(times - time) },
                    label = { Text("${time.format(TIME_FORMAT)} ✕") },
                )
            }
            AssistChip(onClick = { showPicker = true }, label = { Text("+ Uhrzeit") })
        }
    }

    if (showPicker) {
        TimePickerDialog(
            initial = LocalTime.of(12, 0),
            onConfirm = { picked ->
                if (picked !in times) onChange((times + picked).sorted())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
