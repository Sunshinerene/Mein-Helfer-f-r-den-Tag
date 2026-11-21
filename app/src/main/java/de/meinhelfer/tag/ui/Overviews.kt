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
}

@Composable
private fun ChoiceDialog(title: String, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.take(60)) },
        text = {
            Column {
                if (!confirm) {
                    TextButton(onClick = onEdit) { Text("Bearbeiten") }
                    TextButton(onClick = { confirm = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                } else {
                    Text("Wirklich löschen?")
                    TextButton(onClick = onDelete) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
internal fun OverviewEditors(edit: OverviewEdit, doctors: List<Doctor>, vm: MainViewModel) {
    edit.choiceAppt?.let { a ->
        ChoiceDialog(
            title = a.title,
            onEdit = {
                edit.appt = a
                edit.choiceAppt = null
            },
            onDelete = {
                vm.delete(a)
                edit.choiceAppt = null
            },
            onDismiss = { edit.choiceAppt = null },
        )
    }
    edit.choiceTask?.let { t ->
        ChoiceDialog(
            title = t.text,
            onEdit = {
                edit.task = t
                edit.choiceTask = null
            },
            onDelete = {
                vm.deleteTodo(t)
                edit.choiceTask = null
            },
            onDismiss = { edit.choiceTask = null },
        )
    }
    edit.appt?.let { a ->
        AppointmentDialog(
            initial = a,
            doctors = doctors,
            onDismiss = { edit.appt = null },
            onSave = { saved ->
                if (saved.id == 0L) vm.add(saved) else vm.update(saved)
                edit.appt = null
            },
        )
    }
    edit.task?.let { t ->
        TaskDialog(
            initial = t,
            doctors = doctors,
            onDismiss = { edit.task = null },
            onSave = { d ->
                if (t.id == 0L) vm.addTasks(listOf(d))
                else vm.updateTodo(t.copy(text = d.text, doctorId = d.doctorId, priority = d.priority, dueDay = d.dueDay, outside = d.outside))
                edit.task = null
            },
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

/** Oben auf der Tagesansicht: Dringendes und Faelliges zuerst, dann die naechsten 3 Tage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayAheadCard(
    todos: List<Todo>,
    appointments: List<Appointment>,
    doctors: List<Doctor>,
    todayDay: Long,
    vm: MainViewModel,
) {
    val edit = remember { OverviewEdit() }
    val urgent = todos.filter { !it.done && (it.priority == 2 || (it.dueDay != null && it.dueDay <= todayDay)) }
        .sortedWith(compareByDescending<Todo> { it.priority }.thenBy { it.dueDay ?: Long.MAX_VALUE })
    val next = (1L..3L).map { LocalDate.ofEpochDay(todayDay + it) }

    val ahead = next.sumOf { d ->
        appointments.count { dayOf(it.whenMillis) == d } + todos.count { !it.done && it.dueDay == d.toEpochDay() && it.priority != 2 }
    }
    Foldable(
        key = "wichtig",
        title = "Wichtig und demnächst",
        summary = (if (urgent.isEmpty()) "Nichts Dringendes" else "${urgent.size} dringend oder fällig") +
            " · " + (if (ahead == 0) "nächste 3 Tage frei" else "$ahead in den nächsten 3 Tagen"),
    ) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Wichtig und demnächst", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            Text(
                "Dringendes und Fälliges zuerst, danach die nächsten 3 Tage.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )

            Text(
                "Dringend und fällig",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            if (urgent.isEmpty()) {
                Text("Nichts Dringendes.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
            urgent.take(8).forEach { t -> TaskLine(t, todayDay, vm) { edit.choiceTask = t } }
            if (urgent.size > 8) {
                Text("… und ${urgent.size - 8} weitere unten bei den Aufgaben.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }

            Text(
                "Die nächsten 3 Tage",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp, top = 10.dp),
            )
            var any = false
            next.forEach { d ->
                val appts = appointments.filter { dayOf(it.whenMillis) == d }.sortedBy { it.whenMillis }
                val tasks = todos.filter { !it.done && it.dueDay == d.toEpochDay() && it.priority != 2 }
                if (appts.isEmpty() && tasks.isEmpty()) return@forEach
                any = true
                Text(dayLabel(d, todayDay), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                appts.forEach { a ->
                    Text(
                        formatAppointmentTime(a.whenMillis, a.endMillis) + " – " + a.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { edit.choiceAppt = a }.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
                tasks.forEach { t ->
                    Text(
                        "Aufgabe: " + t.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { edit.choiceTask = t }.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
            if (!any) {
                Text("In den nächsten 3 Tagen ist nichts geplant.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    }
    OverviewEditors(edit, doctors, vm)
}

@Composable
private fun TaskLine(t: Todo, today: Long, vm: MainViewModel, onTap: () -> Unit) {
    val overdue = !t.done && t.dueDay != null && t.dueDay < today
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = t.done, onCheckedChange = { vm.setTodoDone(t, it) })
        Column(Modifier.weight(1f).clickable(onClick = onTap).padding(vertical = 4.dp)) {
            Text(
                t.text,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            val meta = listOfNotNull(
                if (t.priority == 2) "Dringend" else null,
                t.dueDay?.let { dueText(it, today) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue || t.priority == 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Ein Tag (fuer Woche und Monat)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayBlock(
    day: LocalDate,
    todayDay: Long,
    appointments: List<Appointment>,
    todos: List<Todo>,
    meds: List<Medication>,
    checkins: List<Checkin>,
    doctors: List<Doctor>,
    vm: MainViewModel,
    edit: OverviewEdit,
) {
    val epoch = day.toEpochDay()
    val appts = appointments.filter { dayOf(it.whenMillis) == day }.sortedBy { it.whenMillis }
    val tasks = todos.filter { it.dueDay == epoch }
        .sortedWith(compareBy<Todo> { it.done }.thenByDescending { it.priority }.thenBy { it.createdMillis })
    val planned = meds.filter { epoch >= it.createdDay }.sumOf { it.timeList().distinct().size }
    val taken = checkins.count { it.kind == Checkin.MED && it.day == epoch && it.done }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        appts.forEach { a ->
            val check = checkins.firstOrNull { it.kind == Checkin.APPT && it.refId == a.id && it.day == epoch }
            val doctor = doctors.firstOrNull { it.id == a.doctorId }
            Column(
                Modifier.fillMaxWidth().clickable { edit.choiceAppt = a }.padding(vertical = 4.dp),
            ) {
                Text(
                    formatAppointmentTime(a.whenMillis, a.endMillis) + " – " + a.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                val sub = listOfNotNull(
                    doctor?.let { doctorLabel(it) },
                    if (check?.done == true && check.status.isEmpty()) "hat stattgefunden" else statusText(check),
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
            }
        }
        tasks.forEach { t -> TaskLine(t, todayDay, vm) { edit.choiceTask = t } }
        if (planned > 0) {
            Text(
                if (epoch <= todayDay) "Einnahmen: $taken von $planned abgehakt" else "Einnahmen: $planned geplant",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (appts.isEmpty() && tasks.isEmpty()) {
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
                        overdue.sortedByDescending { it.priority }.forEach { t -> TaskLine(t, todayDay, vm) { edit.choiceTask = t } }
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
                        DayBlock(d, todayDay, appointments, todos, meds, checkins, doctors, vm, edit)
                    }
                }
            }
        }
        if (noDue.isNotEmpty()) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                        Text("Aufgaben ohne Frist", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                        noDue.sortedByDescending { it.priority }.forEach { t -> TaskLine(t, todayDay, vm) { edit.choiceTask = t } }
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
                                                RoundedCornerShape(8.dp),
                                            )
                                            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
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
                    DayBlock(d, todayDay, appointments, todos, meds, checkins, doctors, vm, edit)
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
