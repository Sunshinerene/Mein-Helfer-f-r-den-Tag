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
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.DayNote
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.summary.SummaryBuilder
import de.meinhelfer.tag.update.AppPrefs
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
    caregiver: de.meinhelfer.tag.data.CaregiverProfile = de.meinhelfer.tag.data.CaregiverProfile(),
    onEditCaregiver: () -> Unit = {},
    documents: List<de.meinhelfer.tag.data.Document> = emptyList(),
    onOpenDailyStart: (planOnly: Boolean) -> Unit = {},
    onOpenMotivator: (tab: Int) -> Unit = {},
    onOpenMood: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenVitals: () -> Unit = {},
    onOpenDiary: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
    onOpenHandover: () -> Unit = {},
) {
    val motivatorOn by vm.motivatorOnToday.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.ofEpochDay(todayDay)
    val nowTime = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()

    var showEmergency by remember { mutableStateOf(false) }
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


    val context = LocalContext.current
    val window = AppPrefs.intakeWindow(context)
    val nowMinutes = nowTime.hour * 60 + nowTime.minute
    var statusTarget by remember { mutableStateOf<StatusTarget?>(null) }

    // Rueckfragen: Einnahmen und Termine von gestern und heute, deren Zeit vorbei ist und zu denen noch nichts eingetragen wurde
    val allMedChecks = checkins.filter { it.kind == Checkin.MED }
        .associateBy { Triple(it.refId, it.day, SummaryBuilder.normSlot(it.slot)) }
    val followUps = mutableListOf<StatusTarget>()
    listOf(todayDay - 1, todayDay).forEach { d ->
        meds.forEach { m ->
            if (d < m.createdDay) return@forEach
            m.timeList().map { SummaryBuilder.normSlot(it) }.distinct().sorted().forEach { time ->
                val c = allMedChecks[Triple(m.id, d, time)]
                val open = c == null || (!c.done && c.status.isEmpty())
                if (open && intakePhase(d, time, todayDay, nowMinutes, window) == IntakePhase.Past) {
                    val label = (if (d < todayDay) "Gestern " else "") + "$time Uhr – ${m.name}" +
                        if (m.dosage.isNotBlank()) " (${m.dosage})" else ""
                    followUps += StatusTarget(
                        Checkin.MED, m.id, d, time, label,
                        "Wie war es mit dieser Einnahme? Deine Antwort hilft der Auswertung.", MED_PAST_OPTIONS, c,
                    )
                }
            }
        }
    }
    appointments.forEach { a ->
        val d = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
        if (d != todayDay && d != todayDay - 1) return@forEach
        val end = a.endMillis ?: (a.whenMillis + 60L * 60 * 1000)
        if (end >= nowMillis) return@forEach
        val c = apptChecks[a.id to d]
        if (c == null || (!c.done && c.status.isEmpty())) {
            followUps += StatusTarget(
                Checkin.APPT, a.id, d, "", (if (d < todayDay) "Gestern " else "") + a.title,
                "Hat der Termin stattgefunden?", APPT_OPTIONS, c, a,
            )
        }
    }

    val lowStock = meds.filter { (it.daysLeft(todayDay) ?: Int.MAX_VALUE) <= 7 }

    val newVersion = when (updateState) {
        is UpdateState.Available -> updateState.info.version
        is UpdateState.Downloading -> updateState.info.version
        is UpdateState.Ready -> updateState.info.version
        else -> null
    }

    // Freigaben fuer Erinnerungen nach jeder Rueckkehr in die App neu pruefen
    var permTick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        permTick++
        onPauseOrDispose { }
    }
    val remindersOk = remember(permTick) { AppPermissions.remindersReliable(context) }
    val todayList = androidx.compose.foundation.lazy.rememberLazyListState()
    val jumps = rememberJumpList(todayList)

    LazyColumn(
        state = todayList,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        jumps.reset()
        if (newVersion != null) {
            jumpItem(jumps, "Neue Version") {
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

        if (!remindersOk) {
            jumpItem(jumps, "Erinnerungen einrichten") { ReminderWarningCard() }
        }

        jumpItem(jumps, null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = buttonShape(), onClick = { onOpenDailyStart(false) }, modifier = Modifier.weight(1f)) {
                    FitText("Rückblick gestern", maxSize = 14.sp)
                }
                OutlinedButton(shape = buttonShape(), onClick = { onOpenDailyStart(true) }, modifier = Modifier.weight(1f)) {
                    FitText("Tagesplan (PDF)", maxSize = 14.sp)
                }
            }
        }

        jumpItem(jumps, "Profil") {
            if (person != null) {
                Foldable(
                    key = "profil",
                    title = person.name,
                    summary = person.careLevel.takeIf { it in 1..5 }?.let { "Pflegegrad $it" } ?: "Profil",
                ) { ProfileCard(person, todayDay, onEditProfile, onOpenWegweiser, onOpenBudget, onOpenHandover) }
            }
        }

        if (motivatorOn) {
            jumpItem(jumps, "Tages-Motivator") { MotivatorCard(vm, onOpen = onOpenMotivator, onMood = onOpenMood) }
        }
        jumpItem(jumps, null) { BackupReminderCard(onOpenBackup) }

        // ---- Rueckfragen
        if (followUps.isNotEmpty()) {
            jumpItem(jumps, "Kurz nachgefragt") {
            Foldable(key = "nachgefragt", title = "Kurz nachgefragt", summary = "${followUps.size} offen", colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                HCard(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                        Text("Kurz nachgefragt (${followUps.size})", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Hier fehlt noch eine Antwort. Genommen, später, vergessen? Das macht die Auswertung genauer.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        followUps.forEach { f ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(f.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                OutlinedButton(shape = buttonShape(), onClick = { statusTarget = f }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    Text("Antworten")
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        jumpItem(jumps, "Wichtig und demnächst") {
            DayAheadCard(todos = todos, appointments = appointments, doctors = doctors, todayDay = todayDay, vm = vm) { a ->
                val day = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
                val check = apptChecks[a.id to day]
                val done = check?.done == true
                val doctor = doctors.firstOrNull { it.id == a.doctorId }
                val sub = listOfNotNull(doctor?.let { doctorLabel(it) }, statusText(check)).joinToString(" · ").ifEmpty { null }
                CheckRow(
                    state = when {
                        done -> ToggleableState.On
                        check?.status == Checkin.CANCELLED || check?.status == Checkin.MOVED -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    title = formatAppointmentTime(a.whenMillis, a.endMillis) + " – " + a.title,
                    subtitle = sub,
                    warn = false,
                    note = check?.note.orEmpty(),
                    onClick = {
                        statusTarget = StatusTarget(
                            Checkin.APPT, a.id, day, "", a.title,
                            "Wie ist der Termin gelaufen?", APPT_OPTIONS, check, a,
                        )
                    },
                    onNote = {
                        noteTarget = NoteTarget(
                            Checkin.APPT, a.id, day, "", "Bemerkung: ${a.title}",
                            check?.note.orEmpty(), APPT_SUGGESTIONS,
                        )
                    },
                )
            }
        }

        if (!caregiver.isSet) {
            jumpItem(jumps, null) { CaregiverPromptCard(onEditCaregiver) }
        }

        if (AppPrefs.betaEmergency(context)) {
            jumpItem(jumps, "Notfallkarte") { EmergencyEntryCard { showEmergency = true } }
        }

        if (lowStock.isNotEmpty()) {
            jumpItem(jumps, "Bald nachbestellen") {
            Foldable(key = "nachbestellen", title = "Bald nachbestellen", summary = if (lowStock.size == 1) "1 Medikament" else "${lowStock.size} Medikamente", colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
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
                        androidx.compose.material3.Button(shape = buttonShape(), 
                            onClick = onOpenReorder,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Nachbestellung öffnen") }
                    }
                }
            }
        }
        }

        // ---- Einnahmen
        jumpItem(jumps, "Heute einnehmen") {
            Foldable(key = "einnahmen", title = "Heute einnehmen", summary = if (intakes.isEmpty()) "Keine Einnahmen eingetragen" else "$intakesDone von ${intakes.size} abgehakt, ${intakes.size - intakesDone} offen") {
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
                        val st = check?.status.orEmpty()
                        val phase = intakePhase(todayDay, time, todayDay, nowMinutes, window)
                        val label = "$time Uhr – ${m.name}" + if (m.dosage.isNotBlank()) " (${m.dosage})" else ""
                        val slotMinutes = (parseSlot(time)?.let { it.hour * 60 + it.minute }) ?: 0
                        val sub: String?
                        val warn: Boolean
                        when {
                            st.isNotEmpty() -> {
                                sub = statusText(check)
                                warn = st == Checkin.MISSED
                            }
                            done -> {
                                sub = null
                                warn = false
                            }
                            phase == IntakePhase.Before -> {
                                sub = "abhakbar ab ${clockText(slotMinutes - window)} Uhr"
                                warn = false
                            }
                            phase == IntakePhase.Past -> {
                                sub = "Zeit vorbei – bitte kurz nachtragen"
                                warn = true
                            }
                            else -> {
                                sub = null
                                warn = false
                            }
                        }
                        CheckRow(
                            state = when {
                                done -> ToggleableState.On
                                st == Checkin.MISSED || st == Checkin.SKIPPED -> ToggleableState.Indeterminate
                                else -> ToggleableState.Off
                            },
                            title = label,
                            subtitle = sub,
                            warn = warn,
                            note = check?.note.orEmpty(),
                            onClick = {
                                if (st.isEmpty() && phase == IntakePhase.Open) {
                                    vm.saveCheckin(Checkin.MED, m.id, todayDay, time, done = !done, status = "", actual = "")
                                } else {
                                    statusTarget = StatusTarget(
                                        Checkin.MED, m.id, todayDay, time, label,
                                        if (phase == IntakePhase.Before) "Die Zeit ist noch nicht da. Wurde die Einnahme schon vorgezogen?"
                                        else "Das Zeitfenster zum Abhaken ist vorbei. Wie war es?",
                                        if (phase == IntakePhase.Before) MED_BEFORE_OPTIONS else MED_PAST_OPTIONS,
                                        check,
                                    )
                                }
                            },
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
        }

        // ---- Trinken und Werte, Pflegetagebuch
        jumpItem(jumps, "Trinken und Werte") { VitalTodayCard(vm, person, todayDay, onOpen = onOpenVitals) }
        jumpItem(jumps, "Pflegetagebuch") { CareDiaryTodayCard(vm, todayDay, onOpen = onOpenDiary) }

        // ---- Aufgaben
        jumpItem(jumps, "Aufgaben") {
            val openTodos = todos.count { !it.done }
            val urgentTodos = todos.count { !it.done && it.priority == 2 }
            Foldable(
                key = "aufgaben",
                title = "Aufgaben",
                summary = if (openTodos == 0) "Nichts offen" else "$openTodos offen" + if (urgentTodos > 0) ", davon $urgentTodos dringend" else "",
            ) {
                TasksCard(todos = todos, doctors = doctors, person = person, vm = vm, meds = meds, caregiver = caregiver, onEditCaregiver = onEditCaregiver, documents = documents)
            }
        }

        // ---- Bemerkung zum Tag
        jumpItem(jumps, "Bemerkung zum Tag") {
            Foldable(key = "bemerkung", title = "Bemerkung zum Tag", summary = dayNote?.text?.takeIf { it.isNotBlank() }?.let { if (it.length > 60) it.take(60) + "…" else it } ?: "Noch nichts notiert") {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bemerkung zum Tag", style = MaterialTheme.typography.titleMedium)
                    Text(
                        dayNote?.text?.takeIf { it.isNotBlank() }
                            ?: "Noch nichts notiert, z. B. wie der Tag lief oder was aufgefallen ist.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(shape = buttonShape(), onClick = { editDayNote = true }) {
                        Text(if (dayNote?.text.isNullOrBlank()) "Bemerkung schreiben" else "Bemerkung ändern")
                    }
                }
            }
        }
        }

        // ---- Auswertung
        jumpItem(jumps, "Auswertung") {
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
        jumpItem(jumps, null) {
            Text(
                "Mein Helfer für den Tag · by $AUTHOR",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (showEmergency) {
        EmergencyDialog(
            person = person,
            meds = meds,
            doctors = doctors,
            caregiver = caregiver,
            onClose = { showEmergency = false },
        )
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
    statusTarget?.let { t ->
        StatusDialog(
            title = t.title,
            intro = t.intro,
            options = t.options,
            current = t.current,
            taskLabel = if (t.appt != null) "Aufgabe „Neuen Termin ausmachen“ für morgen anlegen" else null,
            taskStatuses = setOf(Checkin.CANCELLED, Checkin.MOVED),
            onSave = { opt, actual, note, createTask ->
                vm.saveCheckin(t.kind, t.refId, t.day, t.slot, done = opt.done, note = note, status = opt.status, actual = actual)
                val appt = t.appt
                if (createTask && appt != null && (opt.status == Checkin.CANCELLED || opt.status == Checkin.MOVED)) {
                    val text = "Neuen Termin ausmachen: ${appt.title}"
                    if (todos.none { !it.done && it.text == text }) {
                        vm.addTasks(listOf(TaskDraft(text, appt.doctorId, 1, todayDay + 1, false)))
                    }
                }
                statusTarget = null
            },
            onReset = if (t.current != null) {
                {
                    vm.resetCheckin(t.kind, t.refId, t.day, t.slot)
                    statusTarget = null
                }
            } else null,
            onDismiss = { statusTarget = null },
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(person: Person?, todayDay: Long, onEdit: () -> Unit, onWegweiser: () -> Unit, onBudget: () -> Unit = {}, onHandover: () -> Unit = {}) {
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
            if (person.phone.isNotBlank()) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Telefon: ${person.phone}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + person.phone.filter { it.isDigit() || it == '+' })))
                        } catch (_: Exception) {
                        }
                    }) { Text("Anrufen") }
                }
            }
            if (person.important.isNotBlank()) {
                Text("Wichtig: ${person.important}", style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            }
            if (head.isEmpty() && person.important.isBlank() && person.insurer.isBlank()) {
                Text(
                    "Du kannst im Profil Geburtsdatum, Pflegegrad und Wichtiges eintragen. Das bleibt nur in der App.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Profil bearbeiten") }
                TextButton(onClick = onWegweiser, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Wegweiser Leistungen") }
                TextButton(onClick = onBudget, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Budgets") }
                TextButton(onClick = onHandover, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Übergabe") }
            }
        }
    }
}

@Composable
private fun CheckRow(
    state: ToggleableState,
    title: String,
    subtitle: String?,
    warn: Boolean,
    note: String,
    onClick: () -> Unit,
    onNote: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TriStateCheckbox(state = state, onClick = onClick)
        Column(
            Modifier.weight(1f).clickable { onClick() }.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (state == ToggleableState.On) TextDecoration.LineThrough else TextDecoration.None,
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

/** Hinweis, wenn die letzte Sicherung zu lange her ist (Einstellung unter Sicherheit und Sicherung). */
@Composable
private fun BackupReminderCard(onOpenBackup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val due = remember(tick) { AppPrefs.backupDue(context) }
    if (!due) return
    val last = AppPrefs.lastBackup(context)
    HCard(
        Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Zeit für eine Sicherung", style = MaterialTheme.typography.titleSmall)
            Text(
                if (last > 0) {
                    val days = ((System.currentTimeMillis() - last) / (24L * 60 * 60 * 1000)).toInt()
                    "Die letzte Sicherung ist $days Tage her. Eine aktuelle Sicherung schützt deine Einträge, falls das Handy verloren geht oder kaputtgeht."
                } else {
                    "Es gibt noch keine Sicherung. Mit einer Sicherung kannst du alles auf einem neuen Handy wiederherstellen."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(shape = buttonShape(), onClick = onOpenBackup) { Text("Jetzt sichern") }
                TextButton(onClick = {
                    AppPrefs.setBackupSnoozeUntil(context, System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                    tick++
                }) { Text("In einer Woche") }
            }
        }
    }
}
