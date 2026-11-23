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
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
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
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    var day by rememberSaveable { mutableLongStateOf(today) }
    var editing by remember { mutableStateOf<CareLogEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pdfDays by remember { mutableIntStateOf(14) }
    val diaryContext = androidx.compose.ui.platform.LocalContext.current
    var selfAnswers by remember(person?.id) { mutableStateOf(person?.let { AppPrefs.selfCheck(diaryContext, it.id) } ?: emptyMap()) }
    var showSelfCheck by remember { mutableStateOf(false) }
    var pdfWithCheck by remember { mutableStateOf(true) }
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
            jumpItem(diaryJumps, "Selbsteinschätzung") {
                val r = de.meinhelfer.tag.summary.SelfCheck.evaluate(selfAnswers)
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Selbsteinschätzung wie bei der Begutachtung", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Beantworte die Fragen der Begutachtung selbst – mit dem Tagebuch im Blick. So siehst du ungefähr, wo die Person steht, und gehst vorbereitet ins Gespräch. Nur ein Anhaltspunkt.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (selfAnswers.isNotEmpty()) {
                            val g = de.meinhelfer.tag.summary.SelfCheck.grade(r.total)
                            Text(
                                "${de.meinhelfer.tag.summary.SelfCheck.points(r.total)} von 100 Punkten → " + (if (g == 0) "kein Pflegegrad" else "etwa Pflegegrad $g") +
                                    if (!r.complete) " (noch ${r.questions - r.answered} Fragen offen)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Button(shape = buttonShape(), enabled = person != null, onClick = { showSelfCheck = true }) {
                            Text(if (selfAnswers.isEmpty()) "Einschätzung starten" else "Einschätzung ansehen und ändern")
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
                        if (selfAnswers.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = pdfWithCheck, onCheckedChange = { pdfWithCheck = it })
                                Text("Selbsteinschätzung beifügen", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        PdfButtons(shareLabel = "Als PDF teilen", chooserTitle = "Pflegetagebuch teilen") { ctx ->
                            CarePdfs.careDiary(
                                ctx, person, log.filter { it.day > today - pdfDays }, today - pdfDays + 1, today, AppPrefs.caregiver(ctx), vitals,
                                selfCheck = if (pdfWithCheck) selfAnswers else emptyMap(),
                            )
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

    if (showSelfCheck && person != null) {
        SelfCheckDialog(
            personName = person.name,
            initial = selfAnswers,
            diaryCounts = log.filter { it.day > today - 14 }.groupingBy { it.module }.eachCount(),
            onDismiss = { showSelfCheck = false },
            onSave = {
                selfAnswers = it
                AppPrefs.setSelfCheck(diaryContext, person.id, it)
                showSelfCheck = false
            },
        )
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

/** Fragebogen der Selbsteinschätzung, Bereich für Bereich, mit Punkten und Ergebnis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelfCheckDialog(
    personName: String,
    initial: Map<String, Int>,
    diaryCounts: Map<Int, Int>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Int>) -> Unit,
) {
    val check = de.meinhelfer.tag.summary.SelfCheck
    var answers by remember { mutableStateOf(initial) }
    val r = check.evaluate(answers)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        DesignSurface(Modifier.fillMaxWidth().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Text("Selbsteinschätzung – $personName", style = MaterialTheme.typography.titleLarge)
                val g = check.grade(r.total)
                Text(
                    "${check.points(r.total)} von 100 Punkten → " + (if (g == 0) "kein Pflegegrad" else "etwa Pflegegrad $g") +
                        " · ${r.answered}/${r.questions} beantwortet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyColumn(Modifier.weight(1f, fill = false).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            "Wähle jeweils, was im Alltag meistens zutrifft – nicht der beste Tag. Was die Person nur mit Hilfe kann, gilt nicht als selbständig. " +
                                "Bereiche 2 und 3: der höhere zählt. Bereich 5 ist vereinfacht.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    check.AREAS.forEach { area ->
                        item(key = "a${area.module}") {
                            Column {
                                androidx.compose.material3.HorizontalDivider()
                                Text(
                                    "${area.module} ${area.title}: ${r.sums[area.module]} Punkte → ${check.points(r.weighted.getValue(area.module))} gewichtet",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                diaryCounts[area.module]?.let {
                                    Text("Im Tagebuch (14 Tage): $it Einträge", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        area.questions.forEach { q ->
                            item(key = q.key) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(q.text, style = MaterialTheme.typography.bodyMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        q.options.forEachIndexed { i, o ->
                                            FilterChip(
                                                selected = answers[q.key] == i,
                                                onClick = { answers = if (answers[q.key] == i) answers - q.key else answers + (q.key to i) },
                                                label = { Text(o.label) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        val p = check.gradePlanned(r.total)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.material3.HorizontalDivider()
                            Text("Pflegegrade nach Punkten: 1 ab 12,5 · 2 ab 27 · 3 ab 47,5 · 4 ab 70 · 5 ab 90.", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Geplant ab 2027 (Reformentwurf, noch offen): 1 ab 15 · 2 ab 30 · 3 ab 50; auch die Bewertung in den Bereichen soll sich ändern." +
                                    if (p != g) " Mit diesen Schwellen wäre es " + (if (p == 0) "kein Pflegegrad." else "Pflegegrad $p.") else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Nur ein Anhaltspunkt zur Vorbereitung. Den Pflegegrad legt die Pflegekasse nach der Begutachtung fest. Beratung gibt es z. B. beim Pflegestützpunkt.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = buttonShape(), onClick = { onSave(answers) }) { Text("Speichern") }
                    OutlinedButton(shape = buttonShape(), enabled = answers.isNotEmpty(), onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, check.report(personName, answers))
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Selbsteinschätzung teilen"))
                    }) { Text("Teilen") }
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                }
            }
        }
    }
}
