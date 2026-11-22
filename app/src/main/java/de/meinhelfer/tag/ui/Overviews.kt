package de.meinhelfer.tag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import de.meinhelfer.tag.summary.SummaryBuilder
import de.meinhelfer.tag.update.AppPrefs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Todo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Zeitraum der Heute-Seite: Tag (Standard), Woche oder Monat. */
internal enum class ViewPeriod(val label: String) { Tag("Tag"), Woche("Woche"), Monat("Monat") }

@Composable
internal fun PeriodSwitch(selected: ViewPeriod, onSelect: (ViewPeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewPeriod.entries.forEach { p ->
            FilterChip(selected = selected == p, onClick = { onSelect(p) }, label = { Text(p.label) })
        }
    }
}

private val DE = Locale.GERMANY
private val SHORT = DateTimeFormatter.ofPattern("dd.MM.", DE)
private val LONG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", DE)

private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun weekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, DE)

private fun dayLabel(d: LocalDate, today: Long): String = when (d.toEpochDay() - today) {
    0L -> "Heute"
    1L -> "Morgen"
    -1L -> "Gestern"
    else -> weekday(d).take(2) + ". " + d.format(SHORT)
}

// ---------------------------------------------------------------- Bearbeiten (gemeinsam)

/** Welcher Termin bzw. welche Aufgabe gerade geaendert wird. */
internal class OverviewEdit {
    var choiceAppt by mutableStateOf<Appointment?>(null)
    var choiceTask by mutableStateOf<Todo?>(null)
    var appt by mutableStateOf<Appointment?>(null)
    var task by mutableStateOf<Todo?>(null)
    var deleteAppt by mutableStateOf<Appointment?>(null)
    /** Gerade abgehakte Aufgabe: Ergebnis abfragen */
    var resultFor by mutableStateOf<Todo?>(null)
    /** Einnahme oder Termin: wie ist es gelaufen? */
    var status by mutableStateOf<StatusTarget?>(null)
}

@Composable
private fun TaskChoiceDialog(t: Todo, edit: OverviewEdit, vm: MainViewModel) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { edit.choiceTask = null },
        title = { Text(t.text.take(60)) },
        text = {
            Column {
                if (!confirm) {
                    TextButton(onClick = {
                        edit.task = t
                        edit.choiceTask = null
                    }) { Text("Bearbeiten") }
                    if (t.done) {
                        TextButton(onClick = {
                            edit.resultFor = t
                            edit.choiceTask = null
                        }) { Text(if (t.result.isBlank()) "Ergebnis eintragen" else "Ergebnis ändern") }
                    }
                    TextButton(onClick = { confirm = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                } else {
                    Text("Wirklich löschen? Die Aufgabe verschwindet auch aus dem Verlauf.")
                    TextButton(onClick = {
                        vm.deleteTodo(t)
                        edit.choiceTask = null
                    }) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { edit.choiceTask = null }) { Text("Abbrechen") } },
    )
}

@Composable
internal fun OverviewEditors(edit: OverviewEdit, doctors: List<Doctor>, vm: MainViewModel) {
    val history by vm.taskHistory.collectAsStateWithLifecycle()
    edit.choiceAppt?.let { a ->
        AlertDialog(
            onDismissRequest = { edit.choiceAppt = null },
            title = { Text(a.title.take(60)) },
            text = {
                Column {
                    de.meinhelfer.tag.data.Recurrence.describe(a)?.let { Text("Wiederkehrend: $it", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = {
                        edit.appt = a
                        edit.choiceAppt = null
                    }) { Text("Bearbeiten") }
                    TextButton(onClick = {
                        edit.deleteAppt = a
                        edit.choiceAppt = null
                    }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { edit.choiceAppt = null }) { Text("Abbrechen") } },
        )
    }
    edit.deleteAppt?.let { a -> AppointmentDeleteDialog(a, vm) { edit.deleteAppt = null } }
    edit.choiceTask?.let { t -> TaskChoiceDialog(t, edit, vm) }
    edit.appt?.let { a ->
        AppointmentEditor(initial = a, doctors = doctors, vm = vm) { edit.appt = null }
    }
    edit.task?.let { t ->
        TaskDialog(
            initial = t,
            doctors = doctors,
            history = history,
            onDismiss = { edit.task = null },
            onSave = { d ->
                if (t.id == 0L) vm.addTasks(listOf(d))
                else vm.updateTodo(t.copy(text = d.text, doctorId = d.doctorId, priority = d.priority, dueDay = d.dueDay, outside = d.outside, remindAt = d.remindAt))
                edit.task = null
            },
        )
    }
    edit.resultFor?.let { t ->
        TaskResultDialog(
            task = t,
            onSave = { r, st ->
                vm.saveTodoResult(t, r, st)
                edit.resultFor = null
            },
            onDismiss = { edit.resultFor = null },
        )
    }
    edit.status?.let { t ->
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
                    if (history.none { !it.done && it.text == text }) {
                        vm.addTasks(listOf(TaskDraft(text, appt.doctorId, 1, LocalDate.now().toEpochDay() + 1, false)))
                    }
                }
                edit.status = null
            },
            onReset = if (t.current != null) {
                {
                    vm.resetCheckin(t.kind, t.refId, t.day, t.slot)
                    edit.status = null
                }
            } else null,
            onDismiss = { edit.status = null },
        )
    }
}

private fun newAppointmentOn(day: LocalDate): Appointment =
    Appointment(
        title = "",
        whenMillis = LocalDateTime.of(day, java.time.LocalTime.of(10, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

private fun newTaskOn(day: LocalDate): Todo = Todo(text = "", dueDay = day.toEpochDay())

// ---------------------------------------------------------------- Tag: Wichtig und demnaechst

/**
 * Oben auf der Tagesansicht, eine Karte fuer alles Anstehende:
 * Dringendes und Faelliges zuerst, dann Termine von heute, die naechsten 3 Tage (Termine und Aufgaben)
 * und weitere Termine bis 7 Tage. Termine werden ueber [apptRow] gezeigt (zum Abhaken auf der Heute-Seite).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayAheadCard(
    todos: List<Todo>,
    appointments: List<Appointment>,
    doctors: List<Doctor>,
    todayDay: Long,
    vm: MainViewModel,
    apptRow: @Composable (Appointment) -> Unit,
) {
    val edit = remember { OverviewEdit() }
    val urgent = todos.filter { !it.done && (it.priority == 2 || (it.dueDay != null && it.dueDay <= todayDay)) }
        .sortedWith(compareByDescending<Todo> { it.priority }.thenBy { it.dueDay ?: Long.MAX_VALUE })
    fun apptsOn(d: Long) = appointments.filter { dayOf(it.whenMillis).toEpochDay() == d }.sortedBy { it.whenMillis }
    fun tasksOn(d: Long) = todos.filter { !it.done && it.dueDay == d && it.priority != 2 }
    val todayAppts = apptsOn(todayDay)
    val soon = (1L..3L).map { todayDay + it }
    val later = (4L..7L).map { todayDay + it }
    val soonCount = soon.sumOf { apptsOn(it).size + tasksOn(it).size }
    val laterCount = later.sumOf { apptsOn(it).size }

    Foldable(
        key = "wichtig",
        title = "Wichtig und demnächst",
        summary = listOf(
            if (urgent.isEmpty()) "Nichts Dringendes" else "${urgent.size} dringend oder fällig",
            if (todayAppts.isEmpty()) "heute kein Termin" else "heute ${todayAppts.size} Termin" + if (todayAppts.size > 1) "e" else "",
            if (soonCount + laterCount == 0) "Woche frei" else "${soonCount + laterCount} in den nächsten 7 Tagen",
        ).joinToString(" · "),
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Wichtig und demnächst", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))

                Text(
                    "Dringend und fällig",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
                if (urgent.isEmpty()) {
                    Text("Nichts Dringendes.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
                urgent.take(8).forEach { t -> TaskLine(t, todayDay, vm, edit) }
                if (urgent.size > 8) {
                    Text("… und ${urgent.size - 8} weitere unten bei den Aufgaben.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                }

                Text("Termine heute", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
                if (todayAppts.isEmpty()) {
                    Text("Heute keine Termine.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
                todayAppts.forEach { apptRow(it) }

                Text("Die nächsten 3 Tage", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
                if (soonCount == 0) {
                    Text("Nichts geplant.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
                soon.forEach { d ->
                    val appts = apptsOn(d)
                    val tasks = tasksOn(d)
                    if (appts.isEmpty() && tasks.isEmpty()) return@forEach
                    Text(dayLabel(LocalDate.ofEpochDay(d), todayDay), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                    appts.forEach { apptRow(it) }
                    tasks.forEach { t -> TaskLine(t, todayDay, vm, edit) }
                }

                if (laterCount > 0) {
                    Text("Weitere Termine bis 7 Tage", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
                    later.forEach { d ->
                        val appts = apptsOn(d)
                        if (appts.isEmpty()) return@forEach
                        Text(dayLabel(LocalDate.ofEpochDay(d), todayDay), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        appts.forEach { apptRow(it) }
                    }
                }
            }
        }
    }
    OverviewEditors(edit, doctors, vm)
}

/** Aufgabe mit Haken; beim Abhaken wird nach dem Ergebnis gefragt. */
@Composable
internal fun TaskLine(t: Todo, today: Long, vm: MainViewModel, edit: OverviewEdit) {
    val overdue = !t.done && t.dueDay != null && t.dueDay < today
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = t.done, onCheckedChange = { checked ->
            vm.setTodoDone(t, checked)
            if (checked) edit.resultFor = t
        })
        Column(Modifier.weight(1f).clickable { edit.choiceTask = t }.padding(vertical = 4.dp)) {
            Text(
                t.text,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            val meta = listOfNotNull(
                if (t.priority == 2 && !t.done) "Dringend" else null,
                if (!t.done) t.dueDay?.let { dueText(it, today) } else null,
                if (!t.done) remindText(t.remindAt) else null,
                if (t.done) stars(t.rating).takeIf { it.isNotEmpty() } else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue || (t.priority == 2 && !t.done)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (t.done && t.result.isNotBlank()) {
                Text("Ergebnis: ${t.result}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------- Ein Tag (fuer Woche und Monat)

/** Einnahmen eines Tages zum Abhaken; vergangene und vorgezogene werden wie auf der Heute-Seite nachgefragt. */
@Composable
internal fun IntakeRows(
    epoch: Long,
    todayDay: Long,
    meds: List<Medication>,
    checkins: List<Checkin>,
    vm: MainViewModel,
    edit: OverviewEdit,
) {
    val context = LocalContext.current
    val window = AppPrefs.intakeWindow(context)
    val now = java.time.LocalTime.now()
    val nowMinutes = now.hour * 60 + now.minute
    val slots = meds.filter { epoch >= it.createdDay }
        .flatMap { m -> m.timeList().map { SummaryBuilder.normSlot(it) }.distinct().map { it to m } }
        .sortedBy { it.first }
    slots.forEach { (time, m) ->
        val c = checkins.firstOrNull { it.kind == Checkin.MED && it.refId == m.id && it.day == epoch && SummaryBuilder.normSlot(it.slot) == time }
        val st = c?.status.orEmpty()
        val done = c?.done == true
        val phase = intakePhase(epoch, time, todayDay, nowMinutes, window)
        val label = "$time Uhr – ${m.name}" + if (m.dosage.isNotBlank()) " (${m.dosage})" else ""
        val future = epoch > todayDay
        val sub = when {
            st.isNotEmpty() -> statusText(c)
            done -> null
            future -> "geplant"
            phase == IntakePhase.Before -> "noch nicht dran"
            phase == IntakePhase.Past -> "offen – bitte nachtragen"
            else -> null
        }
        val tap = {
            if (!future) {
                if (st.isEmpty() && phase == IntakePhase.Open) {
                    vm.saveCheckin(Checkin.MED, m.id, epoch, time, done = !done, status = "", actual = "")
                } else {
                    edit.status = StatusTarget(
                        Checkin.MED, m.id, epoch, time, label,
                        if (phase == IntakePhase.Before) "Die Zeit ist noch nicht da. Wurde die Einnahme schon vorgezogen?"
                        else "Wie war es mit dieser Einnahme?",
                        if (phase == IntakePhase.Before) MED_BEFORE_OPTIONS else MED_PAST_OPTIONS,
                        c,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(
                state = when {
                    done -> ToggleableState.On
                    st == Checkin.MISSED || st == Checkin.SKIPPED -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                },
                onClick = tap,
                enabled = !future,
            )
            Column(Modifier.weight(1f).clickable(enabled = !future, onClick = tap).padding(vertical = 2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (phase == IntakePhase.Past && !done && st.isEmpty() || st == Checkin.MISSED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayBlock(
    day: LocalDate,
    todayDay: Long,
    appointments: List<Appointment>,
    todos: List<Todo>,
    history: List<Todo>,
    meds: List<Medication>,
    checkins: List<Checkin>,
    doctors: List<Doctor>,
    vm: MainViewModel,
    edit: OverviewEdit,
    intakesOpenInitially: Boolean,
) {
    val epoch = day.toEpochDay()
    val zone = ZoneId.systemDefault()
    val appts = appointments.filter { dayOf(it.whenMillis) == day }.sortedBy { it.whenMillis }
    val tasks = todos.filter { it.dueDay == epoch }
        .sortedWith(compareBy<Todo> { it.done }.thenByDescending { it.priority }.thenBy { it.createdMillis })
    // An diesem Tag erledigt (auch ausgeblendete), soweit nicht schon oben mit Frist an diesem Tag
    val doneHere = history.filter { t ->
        t.done && t.doneMillis != null && t.dueDay != epoch &&
            Instant.ofEpochMilli(t.doneMillis).atZone(zone).toLocalDate().toEpochDay() == epoch
    }
    val active = meds.filter { epoch >= it.createdDay }
    val planned = active.sumOf { it.timeList().distinct().size }
    // Nur Einnahmen der Medikamente dieser Person zaehlen
    val medIds = active.map { it.id }.toSet()
    val taken = checkins.count { it.kind == Checkin.MED && it.day == epoch && it.done && it.refId in medIds }.coerceAtMost(planned)
    var showIntakes by remember(epoch) { mutableStateOf(intakesOpenInitially) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        appts.forEach { a ->
            val check = checkins.firstOrNull { it.kind == Checkin.APPT && it.refId == a.id && it.day == epoch }
            val doctor = doctors.firstOrNull { it.id == a.doctorId }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { edit.choiceAppt = a }.padding(vertical = 4.dp)) {
                    Text(
                        formatAppointmentTime(a.whenMillis, a.endMillis) + " – " + a.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val sub = listOfNotNull(
                        doctor?.let { doctorLabel(it) },
                        if (a.seriesId != 0L) "wiederkehrend" else null,
                        if (check?.done == true && check.status.isEmpty()) "hat stattgefunden" else statusText(check),
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                }
                if (epoch <= todayDay) {
                    TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = {
                        edit.status = StatusTarget(Checkin.APPT, a.id, epoch, "", a.title, "Wie ist der Termin gelaufen?", APPT_OPTIONS, check, a)
                    }) { Text(if (check == null) "Wie lief es?" else "Ändern") }
                }
            }
        }
        tasks.forEach { t -> TaskLine(t, todayDay, vm, edit) }
        doneHere.forEach { t ->
            Column(Modifier.fillMaxWidth().clickable { edit.choiceTask = t }.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)) {
                Text("Erledigt: ${t.text}", style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough)
                val meta = listOfNotNull(
                    stars(t.rating).takeIf { it.isNotEmpty() },
                    t.result.takeIf { it.isNotBlank() }?.let { "Ergebnis: $it" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (planned > 0) {
            TextButton(contentPadding = PaddingValues(horizontal = 0.dp), onClick = { showIntakes = !showIntakes }) {
                Text(
                    (if (epoch <= todayDay) "Einnahmen: $taken von $planned abgehakt" else "Einnahmen: $planned geplant") +
                        if (showIntakes) " ▴" else " ▾",
                )
            }
            if (showIntakes) IntakeRows(epoch, todayDay, meds, checkins, vm, edit)
        }
        if (appts.isEmpty() && tasks.isEmpty() && doneHere.isEmpty()) {
            Text("Keine Termine oder Aufgaben.", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow {
            TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = { edit.appt = newAppointmentOn(day) }) { Text("+ Termin") }
            TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = { edit.task = newTaskOn(day) }) { Text("+ Aufgabe") }
        }
    }
}

// ---------------------------------------------------------------- Woche

@Composable
private fun PeriodHeader(title: String, subtitle: String?, onPrev: () -> Unit, onNext: () -> Unit, onNow: () -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("‹") }
            Column(Modifier.weight(1f).clickable(onClick = onNow), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            TextButton(onClick = onNext) { Text("›") }
        }
    }
}

/** Wochenansicht: jeder Tag mit Terminen, Aufgaben und Einnahmen, alles antippbar und aenderbar. */
@Composable
internal fun WeekOverview(
    vm: MainViewModel,
    appointments: List<Appointment>,
    todos: List<Todo>,
    meds: List<Medication>,
    doctors: List<Doctor>,
    todayDay: Long,
) {
    val thisMonday = LocalDate.ofEpochDay(todayDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    var weekStart by rememberSaveable { mutableStateOf(thisMonday) }
    val start = LocalDate.ofEpochDay(weekStart)
    val checkins by vm.overviewCheckins.collectAsStateWithLifecycle()
    val history by vm.taskHistory.collectAsStateWithLifecycle()
    val edit = remember { OverviewEdit() }
    LaunchedEffect(weekStart) { vm.setOverviewRange(weekStart, weekStart + 6) }

    val week = start.get(WeekFields.ISO.weekOfWeekBasedYear())
    val containsToday = todayDay in weekStart..(weekStart + 6)
    val overdue = if (containsToday) todos.filter { !it.done && it.dueDay != null && it.dueDay < todayDay } else emptyList()
    val noDue = todos.filter { !it.done && it.dueDay == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PeriodHeader(
                title = "Woche $week",
                subtitle = start.format(SHORT) + " – " + start.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)),
                onPrev = { weekStart -= 7 },
                onNext = { weekStart += 7 },
                onNow = { weekStart = thisMonday },
            )
        }
        if (overdue.isNotEmpty()) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                        Text("Überfällig", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                        overdue.sortedByDescending { it.priority }.forEach { t -> TaskLine(t, todayDay, vm, edit) }
                    }
                }
            }
        }
        items7(start).forEach { d ->
            item(key = d.toEpochDay()) {
                val isToday = d.toEpochDay() == todayDay
                HCard(
                    Modifier.fillMaxWidth(),
                    colors = if (isToday) androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else null,
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(
                            weekday(d) + ", " + d.format(SHORT) + if (isToday) " (heute)" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        DayBlock(d, todayDay, appointments, todos, history, meds, checkins, doctors, vm, edit, isToday)
                    }
                }
            }
        }
        if (noDue.isNotEmpty()) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                        Text("Aufgaben ohne Frist", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                        noDue.sortedByDescending { it.priority }.forEach { t -> TaskLine(t, todayDay, vm, edit) }
                    }
                }
            }
        }
    }
    OverviewEditors(edit, doctors, vm)
}

private fun items7(start: LocalDate): List<LocalDate> = (0L..6L).map { start.plusDays(it) }

// ---------------------------------------------------------------- Monat

/** Monatsansicht: Kalender mit Markierungen je Tag; ein Tipp zeigt den Tag darunter, bearbeitbar wie in der Woche. */
@Composable
internal fun MonthOverview(
    vm: MainViewModel,
    appointments: List<Appointment>,
    todos: List<Todo>,
    meds: List<Medication>,
    doctors: List<Doctor>,
    todayDay: Long,
) {
    val today = LocalDate.ofEpochDay(todayDay)
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf(todayDay) }
    val ym = remember(monthKey) { YearMonth.parse(monthKey) }
    val first = ym.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val length = ym.lengthOfMonth()
    val rows = (offset + length + 6) / 7
    val checkins by vm.overviewCheckins.collectAsStateWithLifecycle()
    val history by vm.taskHistory.collectAsStateWithLifecycle()
    val edit = remember { OverviewEdit() }
    LaunchedEffect(monthKey) { vm.setOverviewRange(first.toEpochDay(), ym.atEndOfMonth().toEpochDay()) }

    val apptCount = appointments.groupingBy { dayOf(it.whenMillis).toEpochDay() }.eachCount()
    val openTasks = todos.filter { !it.done && it.dueDay != null }
    val taskCount = openTasks.groupingBy { it.dueDay!! }.eachCount()
    val urgentDays = openTasks.filter { it.priority == 2 || it.dueDay!! < todayDay }.map { it.dueDay!! }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PeriodHeader(
                title = first.month.getDisplayName(TextStyle.FULL, DE) + " " + first.year,
                subtitle = null,
                onPrev = { monthKey = ym.minusMonths(1).toString() },
                onNext = { monthKey = ym.plusMonths(1).toString() },
                onNow = {
                    monthKey = YearMonth.from(today).toString()
                    selected = todayDay
                },
            )
        }
        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val n = r * 7 + c - offset + 1
                                if (n < 1 || n > length) {
                                    Box(Modifier.weight(1f).height(52.dp))
                                } else {
                                    val epoch = first.plusDays((n - 1).toLong()).toEpochDay()
                                    val isSel = epoch == selected
                                    val isToday = epoch == todayDay
                                    Column(
                                        Modifier.weight(1f).height(52.dp).padding(1.dp)
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                                MaterialTheme.shapes.extraSmall,
                                            )
                                            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall) else Modifier)
                                            .clickable { selected = epoch },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            n.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 3.dp)) {
                                            if ((apptCount[epoch] ?: 0) > 0) Dot(MaterialTheme.colorScheme.primary)
                                            if ((taskCount[epoch] ?: 0) > 0) {
                                                Dot(if (epoch in urgentDays) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                        Legend(MaterialTheme.colorScheme.primary, "Termin")
                        Legend(MaterialTheme.colorScheme.tertiary, "Aufgabe")
                        Legend(MaterialTheme.colorScheme.error, "dringend")
                    }
                }
            }
        }
        item {
            val d = LocalDate.ofEpochDay(selected)
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                    Text(d.format(LONG), style = MaterialTheme.typography.titleSmall)
                    DayBlock(d, todayDay, appointments, todos, history, meds, checkins, doctors, vm, edit, true)
                }
            }
        }
    }
    OverviewEditors(edit, doctors, vm)
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(7.dp).background(color, CircleShape))
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Dot(color)
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
