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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.ai.GoalAi
import de.meinhelfer.tag.ai.GoalCheck
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Goal
import de.meinhelfer.tag.data.GoalKind
import de.meinhelfer.tag.data.GoalStep
import de.meinhelfer.tag.summary.GoalTemplates
import de.meinhelfer.tag.summary.Motivator
import de.meinhelfer.tag.summary.Pace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private class DraftStep(
    val id: Long = 0,
    title: String = "",
    val detail: String = "",
    val dueDay: Long? = null,
    val doneMillis: Long? = null,
    val note: String = "",
) {
    var title by mutableStateOf(title)
}

/** Neues Ziel anlegen oder ein Ziel bearbeiten (mit Vorlagen und KI-Vorschlag fuer den Weg). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalEditorScreen(vm: MainViewModel, initial: Goal?, caregiver: CaregiverProfile, onDone: (Long?) -> Unit) {
    val persons by vm.persons.collectAsStateWithLifecycle()
    val currentPerson by vm.personId.collectAsStateWithLifecycle()
    val allSteps by vm.goalSteps.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var kind by remember { mutableStateOf(GoalKind.fromKey(initial?.kind)) }
    var personId by remember { mutableStateOf(initial?.personId ?: currentPerson) }
    var details by remember { mutableStateOf(initial?.details ?: "") }
    var targetDay by remember { mutableStateOf(initial?.targetDay) }
    val steps = remember {
        mutableStateListOf<DraftStep>().apply {
            initial?.let { g ->
                allSteps.filter { it.goalId == g.id }.sortedBy { it.position }.forEach {
                    add(DraftStep(it.id, it.title, it.detail, it.dueDay, it.doneMillis, it.note))
                }
            }
        }
    }
    var aiInfo by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Neues Ziel" else "Ziel bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (initial == null) {
                Text("Vorlage (optional)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GoalTemplates.all.forEach { t ->
                        FilterChip(
                            selected = title == t.title,
                            onClick = {
                                title = t.title
                                kind = t.kind
                                personId = if (t.forPerson) currentPerson else 0L
                                targetDay = t.targetDays?.let { today + it }
                                steps.clear()
                                t.steps.forEach { s -> steps.add(DraftStep(title = s.title, detail = s.detail, dueDay = s.days?.let { today + it })) }
                                aiInfo = null
                            },
                            label = { Text(t.kind.emoji + " " + t.title) },
                        )
                    }
                }
                Text(GoalTemplates.NOTE, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                label = { Text("Ziel") },
                placeholder = { Text("z. B. Pflegegrad beantragen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Art", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.emoji + " " + k.label) })
                }
            }
            Text("Für wen?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = personId == 0L, onClick = { personId = 0L }, label = { Text("Für mich") })
                persons.forEach { p ->
                    FilterChip(selected = personId == p.id, onClick = { personId = p.id }, label = { Text(p.name) })
                }
            }
            OutlinedTextField(
                value = details,
                onValueChange = { details = it.take(600) },
                label = { Text("Details (optional)") },
                supportingText = { Text("Bitte keine Namen eintragen, falls du die KI nutzt.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DatePickerField("Wunschtermin (optional)", targetDay?.let { LocalDate.ofEpochDay(it) }, { targetDay = it.toEpochDay() }, Modifier.weight(1f))
                if (targetDay != null) TextButton(onClick = { targetDay = null }) { Text("Ohne") }
            }
            if (kind == GoalKind.Gesundheit) {
                Text(
                    "Bei Gesundheitszielen: Beschwerden, Vorerkrankungen und Medikamente bitte ärztlich abklären. Die App gibt nur Anhaltspunkte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text("Schritte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (steps.isEmpty()) {
                Text("Noch keine Schritte. Füge welche hinzu, nimm eine Vorlage oder lass dir einen Weg vorschlagen.", style = MaterialTheme.typography.bodySmall)
            }
            steps.forEachIndexed { i, s ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 6.dp))
                        OutlinedTextField(
                            value = s.title,
                            onValueChange = { s.title = it.take(120) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Column {
                            IconButton(onClick = { if (i > 0) steps.add(i - 1, steps.removeAt(i)) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nach oben")
                            }
                            IconButton(onClick = { if (i < steps.lastIndex) steps.add(i + 1, steps.removeAt(i)) }, enabled = i < steps.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten")
                            }
                        }
                        IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Default.Delete, contentDescription = "Schritt löschen") }
                    }
                    val extra = listOfNotNull(
                        s.detail.takeIf { it.isNotBlank() },
                        s.dueDay?.let { "bis " + LocalDate.ofEpochDay(it).format(DAY) },
                        s.doneMillis?.let { "erledigt" },
                    ).joinToString(" · ")
                    if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp))
                }
            }
            TextButton(onClick = { steps.add(DraftStep()) }) { Text("+ Schritt hinzufügen") }

            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Weg vorschlagen lassen (KI, optional)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Die KI schlägt Schritte mit Terminen vor. Vorhandene Schritte werden dabei ersetzt; danach kannst du alles anpassen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (title.isBlank()) {
                        Text("Gib zuerst oben ein, was dein Ziel ist.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val careLevel = persons.firstOrNull { it.id == personId }?.careLevel
                        AiActionButton(
                            label = "Weg vorschlagen",
                            note = "Enthalten sind Ziel, Art, Details, Wunschtermin, deine Rolle und ggf. der Pflegegrad, aber keine Namen.",
                            system = GoalAi.SYSTEM_PROMPT,
                            maxTokens = 2500,
                            prompt = { GoalAi.pathPrompt(title, kind, details, targetDay, caregiver.roleValue?.label, careLevel, personId != 0L, today) },
                            onAnswer = { answer, _ ->
                                val path = GoalAi.parsePath(answer)
                                if (path != null) {
                                    steps.clear()
                                    path.steps.forEach { s -> steps.add(DraftStep(title = s.title, detail = s.detail, dueDay = s.days?.let { today + it })) }
                                    aiInfo = listOfNotNull(path.assessment.takeIf { it.isNotBlank() }, path.tips.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• $it" })
                                        .joinToString("\n")
                                    if (targetDay == null) path.steps.mapNotNull { it.days }.maxOrNull()?.let { targetDay = today + it }
                                }
                                path != null
                            },
                        )
                    }
                    aiInfo?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                        Text("Nur ein Anhaltspunkt. Prüfe Abläufe und Fristen bei der zuständigen Stelle.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                shape = buttonShape(),
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val goal = Goal(
                        id = initial?.id ?: 0L,
                        personId = personId,
                        title = title.trim(),
                        kind = kind.key,
                        details = details.trim(),
                        targetDay = targetDay,
                        createdMillis = initial?.createdMillis ?: 0L,
                        status = initial?.status ?: Goal.ACTIVE,
                        doneMillis = initial?.doneMillis,
                        aiCheck = initial?.aiCheck ?: "",
                        aiCheckMillis = initial?.aiCheckMillis,
                    )
                    val list = steps.filter { it.title.isNotBlank() }.mapIndexed { i, d ->
                        GoalStep(id = d.id, goalId = goal.id, position = i, title = d.title.trim(), detail = d.detail, dueDay = d.dueDay, doneMillis = d.doneMillis, note = d.note)
                    }
                    vm.saveGoal(goal, list) { id -> onDone(id) }
                },
            ) { Text("Speichern") }
            OutlinedButton(shape = buttonShape(), onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
        }
    }
}

/** Ein Ziel mit seinen Schritten, Einschaetzung und KI-Check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(vm: MainViewModel, goal: Goal, caregiver: CaregiverProfile, onEdit: () -> Unit, onBack: () -> Unit) {
    val allSteps by vm.goalSteps.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    val zone = ZoneId.systemDefault()
    val steps = allSteps.filter { it.goalId == goal.id }.sortedBy { it.position }
    val p = Motivator.progress(goal, allSteps, today)
    val person = persons.firstOrNull { it.id == goal.personId }
    var stepEdit by remember { mutableStateOf<GoalStep?>(null) }
    var newStep by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }
    val check = GoalCheck.fromJson(goal.aiCheck)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(goal.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten") }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.padding(inner), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val kind = GoalKind.fromKey(goal.kind)
                        Text(kind.emoji + " " + kind.label + " · " + (person?.name ?: "für mich"), style = MaterialTheme.typography.labelLarge)
                        goal.targetDay?.let {
                            Text(
                                "Wunschtermin: " + LocalDate.ofEpochDay(it).format(DAY) + if (it >= today) " (in ${it - today} Tagen)" else " (vorbei)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (goal.details.isNotBlank()) Text(goal.details, style = MaterialTheme.typography.bodyMedium)
                        ProgressBar(p.fraction)
                        Text("${p.done} von ${p.total} Schritten · ${p.pace.label}", style = MaterialTheme.typography.bodyMedium)
                        if (goal.status == Goal.ACTIVE) Text(p.tip, style = MaterialTheme.typography.bodySmall)
                        when (goal.status) {
                            Goal.REACHED -> Text("Erreicht 🎉", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Goal.PAUSED -> Text("Pausiert", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (goal.status == Goal.ACTIVE && p.pace == Pace.Done) {
                item {
                    HCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Alle Schritte erledigt!", style = MaterialTheme.typography.titleMedium)
                            Button(shape = buttonShape(), onClick = {
                                vm.setGoalStatus(goal, Goal.REACHED)
                                celebrate = true
                            }) { Text("Ziel als erreicht markieren 🎉") }
                        }
                    }
                }
            }
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
                        Text("Schritte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                        if (steps.isEmpty()) {
                            Text("Noch keine Schritte.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                        }
                        steps.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = s.doneMillis != null, onCheckedChange = { vm.toggleStep(s) })
                                Column(Modifier.weight(1f).clickable { stepEdit = s }) {
                                    Text(s.title, style = MaterialTheme.typography.bodyLarge)
                                    if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodySmall)
                                    val done = s.doneMillis
                                    if (done != null) {
                                        Text("erledigt am " + Instant.ofEpochMilli(done).atZone(zone).toLocalDate().format(DAY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        s.dueDay?.let { d ->
                                            Text(
                                                "bis " + LocalDate.ofEpochDay(d).format(DAY) + if (d < today) " · überfällig" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (d < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (s.note.isNotBlank()) Text("Notiz: " + s.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Column {
                                    IconButton(onClick = { vm.moveStep(s, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nach oben") }
                                    IconButton(onClick = { vm.moveStep(s, 1) }, enabled = i < steps.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten") }
                                }
                            }
                        }
                        TextButton(onClick = { newStep = true }) { Text("+ Schritt hinzufügen") }
                    }
                }
            }
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Einschätzung des Wegs (KI, optional)", style = MaterialTheme.typography.titleMedium)
                        if (check != null) {
                            Text(
                                "Stand ${check.level}/5 · " + LocalDate.ofEpochDay(check.day).format(DAY) + " · " + check.provider,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            ProgressBar(check.level / 5f)
                            Text(check.assessment, style = MaterialTheme.typography.bodyMedium)
                            if (check.next.isNotBlank()) Text("Nächster Schritt: " + check.next, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            check.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                            check.newSteps.forEach { ns ->
                                val exists = steps.any { it.title.equals(ns.title, ignoreCase = true) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Vorschlag: " + ns.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { vm.addStep(goal.id, ns.title, ns.detail) }, enabled = !exists) {
                                        Text(if (exists) "✓ übernommen" else "+ übernehmen")
                                    }
                                }
                            }
                            Text("Nur ein Anhaltspunkt.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Die KI schätzt ein, wie gut dein Weg läuft, und schlägt den nächsten Schritt vor.", style = MaterialTheme.typography.bodySmall)
                        }
                        AiActionButton(
                            label = if (check == null) "Weg einschätzen" else "Neu einschätzen",
                            note = "Enthalten sind Ziel, Art, Details, Termine und Stand der Schritte, deine Rolle und ggf. der Pflegegrad, aber keine Namen.",
                            system = GoalAi.SYSTEM_PROMPT,
                            maxTokens = 2000,
                            prompt = { GoalAi.checkPrompt(goal, steps, caregiver.roleValue?.label, person?.careLevel, today) },
                            onAnswer = { answer, provider ->
                                val parsed = GoalAi.parseCheck(answer, today, provider)
                                if (parsed != null) vm.setGoalCheck(goal, parsed.toJson())
                                parsed != null
                            },
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (goal.status) {
                        Goal.ACTIVE -> {
                            if (p.pace != Pace.Done) {
                                OutlinedButton(shape = buttonShape(), onClick = {
                                    vm.setGoalStatus(goal, Goal.REACHED)
                                    celebrate = true
                                }, modifier = Modifier.fillMaxWidth()) { Text("Ziel erreicht 🎉") }
                            }
                            OutlinedButton(shape = buttonShape(), onClick = { vm.setGoalStatus(goal, Goal.PAUSED) }, modifier = Modifier.fillMaxWidth()) { Text("Pausieren") }
                        }
                        else -> OutlinedButton(shape = buttonShape(), onClick = { vm.setGoalStatus(goal, Goal.ACTIVE) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (goal.status == Goal.PAUSED) "Fortsetzen" else "Wieder aufnehmen")
                        }
                    }
                    OutlinedButton(shape = buttonShape(), onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Bearbeiten") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Ziel löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (newStep || stepEdit != null) {
        StepDialog(
            initial = stepEdit,
            onSave = { title, detail, due, note ->
                val s = stepEdit
                if (s == null) vm.addStep(goal.id, title, detail, due) else vm.updateStep(s.copy(title = title.trim(), detail = detail.trim(), dueDay = due, note = note.trim()))
                stepEdit = null
                newStep = false
            },
            onDelete = stepEdit?.let { s ->
                {
                    vm.deleteStep(s)
                    stepEdit = null
                }
            },
            onDismiss = {
                stepEdit = null
                newStep = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirmDelete = false },
            title = { Text("Ziel löschen?") },
            text = { Text("„${goal.title}“ mit allen Schritten wird gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteGoal(goal)
                    onBack()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    if (celebrate) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { celebrate = false },
            title = { Text("🎉 Geschafft!") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🏆", fontSize = 56.sp)
                    Text("Ziel erreicht: ${goal.title}. Das darfst du feiern!", style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = { TextButton(onClick = { celebrate = false }) { Text("Danke") } },
        )
    }
}

@Composable
private fun StepDialog(
    initial: GoalStep?,
    onSave: (title: String, detail: String, due: Long?, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var detail by remember { mutableStateOf(initial?.detail ?: "") }
    var due by remember { mutableStateOf(initial?.dueDay) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Schritt" else "Schritt bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("Schritt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = detail, onValueChange = { detail = it.take(400) }, label = { Text("Was genau? (optional)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DatePickerField("Bis wann? (optional)", due?.let { LocalDate.ofEpochDay(it) }, { due = it.toEpochDay() }, Modifier.weight(1f))
                    if (due != null) TextButton(onClick = { due = null }) { Text("Ohne") }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(400) },
                    label = { Text("Ergebnis oder Notiz (optional)") },
                    placeholder = { Text("z. B. Aktenzeichen, Ansprechperson") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Schritt löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, detail, due, note) }, enabled = title.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
