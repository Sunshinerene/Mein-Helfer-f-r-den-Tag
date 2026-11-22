package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.export.CarePdfs
import de.meinhelfer.tag.update.AppPrefs
import java.time.LocalDate

/** Freitexte im Uebergabe-Bericht (werden je Person gemerkt). */
private val HANDOVER_NOTES = listOf(
    "ablauf" to ("Tagesablauf" to "z. B. 8 Uhr aufstehen, Frühstück, Tabletten; mittags ruhen; 20 Uhr ins Bett"),
    "essen" to ("Essen und Trinken" to "Vorlieben, Abneigungen, Hilfe beim Essen, Trinkmenge"),
    "hinweise" to ("Wichtige Hinweise" to "z. B. Sturzgefahr, Hörgerät, Brille, Schlüssel, Haustiere, wer kommt wann (Pflegedienst)"),
    "beruhigen" to ("Was hilft, was vermeiden" to "z. B. was beruhigt, Gewohnheiten, Reizthemen"),
)

@Composable
private fun CheckLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Uebergabe fuer Vertretung, Urlaub oder Pflegedienst: Zeitraum, eigene Hinweise, Auswahl der Abschnitte, PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverScreen(
    vm: MainViewModel,
    person: Person?,
    meds: List<Medication>,
    appointments: List<Appointment>,
    todos: List<Todo>,
    doctors: List<Doctor>,
    caregiver: CaregiverProfile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pid = person?.id ?: 1L
    val duties by vm.duties.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val careLog by vm.careLog.collectAsStateWithLifecycle()
    var from by remember { mutableStateOf(LocalDate.now()) }
    var until by remember { mutableStateOf(LocalDate.now().plusDays(14)) }
    var substitute by remember(pid) { mutableStateOf(AppPrefs.handoverText(context, pid, "vertretung")) }
    val notes = remember(pid) { HANDOVER_NOTES.associate { (k, _) -> k to mutableStateOf(AppPrefs.handoverText(context, pid, k)) } }
    var withMeds by rememberSaveable { mutableStateOf(true) }
    var withAppts by rememberSaveable { mutableStateOf(true) }
    var withContacts by rememberSaveable { mutableStateOf(true) }
    var withTasks by rememberSaveable { mutableStateOf(true) }
    var withValues by rememberSaveable { mutableStateOf(true) }
    var withDiary by rememberSaveable { mutableStateOf(true) }
    var withNumber by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Übergabe-Bericht") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.padding(inner), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Für Urlaub, Krankheit oder einen Wechsel: Alles Wichtige für die Vertretung auf ein paar Seiten, als PDF zum Teilen oder Ausdrucken." +
                        (person?.name?.let { " Für $it." } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Zeitraum und Vertretung", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DatePickerField(label = "Von", date = from, onChange = { from = it }, modifier = Modifier.weight(1f))
                            DatePickerField(label = "Bis", date = until, onChange = { until = it }, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(
                            value = substitute,
                            onValueChange = {
                                substitute = it
                                AppPrefs.setHandoverText(context, pid, "vertretung", it)
                            },
                            label = { Text("Wer vertritt? (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Eigene Hinweise", style = MaterialTheme.typography.titleMedium)
                        Text("Wird je Person gemerkt, beim nächsten Mal steht es schon da.", style = MaterialTheme.typography.bodySmall)
                        HANDOVER_NOTES.forEach { (key, meta) ->
                            val state = notes.getValue(key)
                            OutlinedTextField(
                                value = state.value,
                                onValueChange = {
                                    state.value = it
                                    AppPrefs.setHandoverText(context, pid, key, it)
                                },
                                label = { Text(meta.first) },
                                placeholder = { Text(meta.second) },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                        Text("Was soll hinein?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                        CheckLine("Medikamente (mit Hinweis, wenn der Vorrat nicht reicht)", withMeds) { withMeds = it }
                        CheckLine("Termine im Zeitraum", withAppts) { withAppts = it }
                        CheckLine("Ärzte und Kontakte (zuständig, Vertretungen)", withContacts) { withContacts = it }
                        CheckLine("Offene Aufgaben", withTasks) { withTasks = it }
                        CheckLine("Trinkziel und letzte Werte", withValues) { withValues = it }
                        CheckLine("Wobei Hilfe nötig ist (Pflegetagebuch)", withDiary) { withDiary = it }
                        CheckLine("Versichertennummer", withNumber) { withNumber = it }
                    }
                }
            }
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PdfButtons(shareLabel = "Als PDF teilen", chooserTitle = "Übergabe teilen") { ctx ->
                            CarePdfs.handover(
                                ctx, person, meds, doctors, duties, appointments, todos, vitals, careLog, caregiver,
                                AppPrefs.drinkGoal(ctx, pid),
                                CarePdfs.HandoverOptions(
                                    fromDay = from.toEpochDay(),
                                    toDay = maxOf(from, until).toEpochDay(),
                                    substitute = substitute,
                                    notes = HANDOVER_NOTES.map { (k, meta) -> meta.first to notes.getValue(k).value },
                                    meds = withMeds,
                                    appointments = withAppts,
                                    contacts = withContacts,
                                    tasks = withTasks,
                                    values = withValues,
                                    diary = withDiary,
                                    insuranceNumber = withNumber,
                                ),
                            )
                        }
                        Text(
                            "Die PDF enthält Gesundheitsdaten. Nur an Menschen geben, die die Pflege übernehmen. Erstellt wird sie auf dem Handy; geteilt wird nur, was du auswählst.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
