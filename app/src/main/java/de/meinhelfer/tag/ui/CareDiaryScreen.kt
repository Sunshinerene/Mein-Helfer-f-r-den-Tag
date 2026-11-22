package de.meinhelfer.tag.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.CareLogEntry
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.export.CarePdfs
import de.meinhelfer.tag.summary.CareAreas
import de.meinhelfer.tag.summary.HelpKind
import de.meinhelfer.tag.update.AppPrefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DIARY_DAY = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY)

/** Eine Zeile fuer einen Eintrag: "Duschen oder Baden – teilweise übernommen, 1×, 20 Min., nachts" */
internal fun careLine(e: CareLogEntry): String = e.activity + " – " + listOfNotNull(
    HelpKind.of(e.help)?.short,
    "${e.times}×",
    e.minutes.takeIf { it > 0 }?.let { "$it Min." },
    if (e.night) "auch nachts" else null,
).joinToString(", ")

/** Karte auf der Heute-Seite: Eintraege heute, schnell etwas eintragen. */
@Composable
fun CareDiaryTodayCard(vm: MainViewModel, todayDay: Long, onOpen: () -> Unit) {
    val log by vm.careLog.collectAsStateWithLifecycle()
    val today = log.filter { it.day == todayDay }
    val days14 = log.filter { it.day > todayDay - 14 }.map { it.day }.distinct().size
    var adding by remember { mutableStateOf(false) }
    Foldable(
        key = "pflegetagebuch",
        title = "Pflegetagebuch",
        summary = if (today.isEmpty()) "Heute noch nichts eingetragen" else "Heute ${today.size} Eintrag/Einträge",
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pflegetagebuch", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (days14 == 0) {
                        "Für die Begutachtung (Pflegegrad, Höherstufung): festhalten, wobei und wie oft geholfen wird. Am besten 1 bis 2 Wochen lang."
                    } else {
                        "In den letzten 14 Tagen an $days14 Tag(en) geführt."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                today.take(4).forEach { Text("• " + careLine(it), style = MaterialTheme.typography.bodyMedium) }
                if (today.size > 4) Text("… und ${today.size - 4} weitere", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = buttonShape(), onClick = { adding = true }) { Text("+ Eintrag") }
                    OutlinedButton(shape = buttonShape(), onClick = onOpen) { Text("Öffnen und PDF") }
                }
            }
        }
    }
    if (adding) {
        CareLogDialog(initial = null, day = todayDay, onDismiss = { adding = false }, onSave = {
            vm.saveCareLog(it)
            adding = false
        })
    }
}

/** Eintrag anlegen oder aendern: Bereich, Taetigkeit, Art der Hilfe, wie oft, Minuten, nachts, Notiz. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareLogDialog(initial: CareLogEntry?, day: Long, onDismiss: () -> Unit, onSave: (CareLogEntry) -> Unit, onDelete: (() -> Unit)? = null) {
    var module by remember { mutableIntStateOf(initial?.module ?: 4) }
    var activity by remember { mutableStateOf(initial?.activity ?: "") }
    var help by remember { mutableStateOf(initial?.help ?: HelpKind.Teilweise.key) }
    var times by remember { mutableIntStateOf(initial?.times ?: 1) }
    var minutes by remember { mutableStateOf(initial?.minutes?.takeIf { it > 0 }?.toString() ?: "") }
    var night by remember { mutableStateOf(initial?.night ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val area = CareAreas.of(module)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Eintrag im Pflegetagebuch" else "Eintrag ändern") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalDate.ofEpochDay(day).format(DIARY_DAY), style = MaterialTheme.typography.labelLarge)
                Text("Bereich", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CareAreas.ALL.forEach { a ->
                        FilterChip(selected = module == a.module, onClick = {
                            if (module != a.module) activity = ""
                            module = a.module
                        }, label = { Text("${a.module} ${a.short}") })
                    }
                }
                Text(area.title, style = MaterialTheme.typography.bodySmall)
                DropdownField(
                    label = "Wobei? (auswählen oder eintippen)",
                    value = activity,
                    options = area.activities,
                    onValueChange = { activity = it },
                )
                Text("Wie wurde geholfen?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HelpKind.entries.forEach { h ->
                        FilterChip(selected = help == h.key, onClick = { help = h.key }, label = { Text(h.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wie oft heute:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(shape = buttonShape(), enabled = times > 1, onClick = { times-- }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("−") }
                    Text("$times×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedButton(shape = buttonShape(), enabled = times < 50, onClick = { times++ }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+") }
                }
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Minuten insgesamt (ungefähr, freiwillig)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().clickable { night = !night }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = night, onCheckedChange = { night = it })
                    Text("Auch nachts nötig", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Was genau? (optional)") },
                    placeholder = { Text("z. B. kann nicht allein aufstehen, wehrt sich, Sturzgefahr") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eintrag löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = activity.isNotBlank(), onClick = {
                onSave(
                    (initial ?: CareLogEntry(personId = 0, day = day, atMillis = System.currentTimeMillis(), module = module, activity = "", help = help)).copy(
                        module = module,
                        activity = activity.trim(),
                        help = help,
                        times = times,
                        minutes = minutes.toIntOrNull() ?: 0,
                        night = night,
                        note = note.trim(),
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Pflegetagebuch: Tag waehlen, Eintraege nach Bereich, "wie am Vortag", Uebersicht und PDF fuer die Begutachtung. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CareDiaryScreen(vm: MainViewModel, person: Person?, onBack: () -> Unit) {
    val log by vm.careLog.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    var day by rememberSaveable { mutableLongStateOf(today) }
    var editing by remember { mutableStateOf<CareLogEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pdfDays by remember { mutableIntStateOf(14) }
    val entries = log.filter { it.day == day }.sortedWith(compareBy({ it.module }, { it.atMillis }))
    val previous = log.filter { it.day < day }.maxOfOrNull { it.day }
    val recentDays = log.filter { it.day > today - 14 }.map { it.day }.distinct().size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pflegetagebuch" + (person?.name?.let { " – $it" } ?: "")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
            )
        },
    ) { inner ->
        val diaryList = androidx.compose.foundation.lazy.rememberLazyListState()
        val diaryJumps = rememberJumpList(diaryList)
        LazyColumn(
            state = diaryList,
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            diaryJumps.reset()
            jumpItem(diaryJumps, "Wozu?") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Wozu?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Bei der Begutachtung zählt, wie selbständig die Person in sechs Bereichen ist, nicht die Minuten. Das Tagebuch hilft, 1 bis 2 Wochen lang konkret festzuhalten, wobei, wie und wie oft geholfen wird, auch nachts. Den Bericht gibst du dem Gutachter bzw. der Gutachterin mit.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Gewichtung: Mobilität 10 %, Verstehen und Sprechen oder Verhalten 15 % (der höhere zählt), Selbstversorgung 40 %, Krankheit und Therapie 20 %, Alltag und Kontakte 15 %.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            jumpItem(diaryJumps, "Tag wählen") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(shape = buttonShape(), onClick = { day-- }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("◀") }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (day) {
                                today -> "Heute"
                                today - 1 -> "Gestern"
                                else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMANY))
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(shape = buttonShape(), enabled = day < today, onClick = { day++ }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("▶") }
                }
            }
            jumpItem(diaryJumps, null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = buttonShape(), onClick = { adding = true }) { Text("+ Eintrag") }
                    if (previous != null && entries.isEmpty()) {
                        OutlinedButton(shape = buttonShape(), onClick = { vm.copyCareDay(previous, day) }) {
                            Text(if (previous == day - 1) "Wie am Vortag übernehmen" else "Wie am " + LocalDate.ofEpochDay(previous).format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)) + " übernehmen")
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                jumpItem(diaryJumps, null) { Text("Für diesen Tag gibt es noch keine Einträge.", style = MaterialTheme.typography.bodyMedium) }
            }
            entries.groupBy { it.module }.forEach { (module, list) ->
                jumpItem(diaryJumps, CareAreas.of(module).title, key = "m$module") {
                    val area = CareAreas.of(module)
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${area.module} ${area.title}", style = MaterialTheme.typography.titleSmall)
                            list.forEach { e ->
                                Column(Modifier.fillMaxWidth().clickable { editing = e }.padding(vertical = 4.dp)) {
                                    Text(careLine(e), style = MaterialTheme.typography.bodyMedium)
                                    if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            jumpItem(diaryJumps, "Bericht für die Begutachtung") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bericht für die Begutachtung (PDF)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "In den letzten 14 Tagen an $recentDays Tag(en) geführt. Der Bericht fasst je Bereich zusammen, wobei an wie vielen Tagen und wie oft geholfen wurde, mit allen Einträgen Tag für Tag.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 28).forEach { d -> FilterChip(selected = pdfDays == d, onClick = { pdfDays = d }, label = { Text("$d Tage") }) }
                        }
                        PdfButtons(shareLabel = "Als PDF teilen", chooserTitle = "Pflegetagebuch teilen") { ctx ->
                            CarePdfs.careDiary(ctx, person, log.filter { it.day > today - pdfDays }, today - pdfDays + 1, today, AppPrefs.caregiver(ctx))
                        }
                    }
                }
            }
            jumpItem(diaryJumps, "Erinnerung am Abend") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) { DiaryReminderSetting() }
                }
            }
            jumpItem(diaryJumps, null) {
                Text(
                    "Selbst geführt, ersetzt keine Begutachtung. Den Pflegegrad legt die Pflegekasse nach der Begutachtung fest. Alles bleibt auf dem Handy, bis du die PDF teilst.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (adding) {
        CareLogDialog(initial = null, day = day, onDismiss = { adding = false }, onSave = {
            vm.saveCareLog(it)
            adding = false
        })
    }
    editing?.let { e ->
        CareLogDialog(
            initial = e,
            day = e.day,
            onDismiss = { editing = null },
            onSave = {
                vm.saveCareLog(it)
                editing = null
            },
            onDelete = {
                vm.deleteCareLog(e)
                editing = null
            },
        )
    }
}
