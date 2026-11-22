package de.meinhelfer.tag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.ai.GoalAi
import de.meinhelfer.tag.ai.MotivatorImpulse
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Goal
import de.meinhelfer.tag.data.GoalKind
import de.meinhelfer.tag.data.MoodEntry
import de.meinhelfer.tag.summary.GoalProgress
import de.meinhelfer.tag.summary.Intention
import de.meinhelfer.tag.summary.Motivator
import de.meinhelfer.tag.summary.MotivatorPlan
import de.meinhelfer.tag.summary.Pace
import de.meinhelfer.tag.summary.SupportLevel
import de.meinhelfer.tag.update.AppPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

/** Ein Vorhaben mit Haken. Schritte und Aufgaben werden dabei wirklich erledigt. */
@Composable
internal fun IntentionRow(item: Intention, vm: MainViewModel, onToggle: (() -> Unit)? = null) {
    val toggle = onToggle ?: { toggleIntention(item, vm) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { toggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.done, onCheckedChange = { toggle() })
        Text(
            item.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun toggleIntention(item: Intention, vm: MainViewModel) {
    when {
        item.stepId != null -> vm.toggleStepById(item.stepId)
        item.todoId != null -> vm.toggleTodoById(item.todoId)
        else -> vm.toggleOwnIntention(item.key)
    }
}

/** Stimmung der letzten drei Tage als Smileys. */
@Composable
internal fun MoodStrip(plan: MotivatorPlan) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        plan.moodDays.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Motivator.emojiFor(d.avg), fontSize = 26.sp)
                Text(Motivator.dayLabel(d.day, plan.day), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun GoalMini(p: GoalProgress, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(GoalKind.fromKey(p.goal.kind).emoji + " " + p.goal.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${p.done}/${p.total}", style = MaterialTheme.typography.labelMedium)
        }
        ProgressBar(p.fraction)
        Text(
            p.pace.label + (p.next?.let { " · Als Nächstes: ${it.title}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (p.pace == Pace.Behind || p.pace == Pace.FarBehind) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fortschrittsbalken, bei eingeschaltetem Glanz mit Farbverlauf. */
@Composable
internal fun ProgressBar(fraction: Float) {
    val gloss = LocalEffects.current.gloss
    val sc = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(sc.outline.copy(alpha = 0.25f), shape),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .then(
                        if (gloss) {
                            Modifier.background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(sc.tertiary, sc.primary)), shape)
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)), shape)
                        } else {
                            Modifier.background(sc.primary, shape)
                        },
                    ),
            )
        }
    }
}

/** Karte auf der Heute-Seite. */
@Composable
fun MotivatorCard(vm: MainViewModel, onOpen: (tab: Int) -> Unit, onMood: () -> Unit) {
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val p = plan ?: return
    val moodText = p.moodEmoji?.let { " · Stimmung $it" } ?: ""
    Foldable(
        key = "motivator",
        title = "Tages-Motivator",
        summary = (if (p.openIntentions == 0) "Alle Vorhaben erledigt" else if (p.openIntentions == 1) "1 Vorhaben offen" else "${p.openIntentions} Vorhaben offen") + moodText,
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.greeting, style = MaterialTheme.typography.titleMedium)
                Text(p.sentence, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                p.impulse?.let { Text("Dein Impuls (KI): $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                MoodStrip(p)
                TextButton(onClick = onMood) { Text(if (p.moodToday) "Stimmung nochmal eintragen" else "Wie geht es dir? Stimmung eintragen") }
                if (p.achieved.isNotEmpty()) {
                    Text("Geschafft: " + p.achieved.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text("Vorhaben für heute", style = MaterialTheme.typography.labelLarge)
                p.intentions.forEach { IntentionRow(it, vm) }
                p.goals.take(2).forEach { g -> GoalMini(g) { onOpen(2) } }
                if (p.support != SupportLevel.None) {
                    TextButton(onClick = { onOpen(0) }) { Text("Unterstützung, wenn du sie brauchst") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { onOpen(0) }, modifier = Modifier.weight(1f)) { FitText("Mehr", maxSize = 14.sp) }
                    OutlinedButton(shape = buttonShape(), onClick = { onOpen(2) }, modifier = Modifier.weight(1f)) { FitText("Ziele", maxSize = 14.sp) }
                }
            }
        }
    }
}

/** Tages-Motivator: Heute, Stimmung und Ziele. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotivatorScreen(
    vm: MainViewModel,
    caregiver: CaregiverProfile,
    startTab: Int,
    onMood: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var goalId by rememberSaveable { mutableLongStateOf(-1L) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val goals by vm.goals.collectAsStateWithLifecycle()

    BackHandler(enabled = editing || goalId >= 0) {
        if (editing) editing = false else goalId = -1
    }

    if (editing) {
        GoalEditorScreen(
            vm = vm,
            initial = goals.firstOrNull { it.id == goalId },
            caregiver = caregiver,
            onDone = { savedId ->
                editing = false
                if (savedId != null) goalId = savedId
            },
        )
        return
    }
    val selected = goals.firstOrNull { it.id == goalId }
    if (selected != null) {
        GoalDetailScreen(
            vm = vm,
            goal = selected,
            caregiver = caregiver,
            onEdit = { editing = true },
            onBack = { goalId = -1 },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tages-Motivator") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Heute", "Stimmung", "Ziele").forEachIndexed { i, label ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
                }
            }
            when (tab) {
                0 -> TodayTab(vm, caregiver, onMood)
                1 -> MoodTab(vm, onMood)
                else -> GoalsTab(vm, onOpen = { goalId = it }, onNew = {
                    goalId = -1
                    editing = true
                })
            }
        }
    }
}

@Composable
private fun TodayTab(vm: MainViewModel, caregiver: CaregiverProfile, onMood: () -> Unit) {
    val context = LocalContext.current
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val p = plan ?: return
    val impulse = p.impulse

    val motList = androidx.compose.foundation.lazy.rememberLazyListState()
    val motJumps = rememberJumpList(motList)
    LazyColumn(state = motList, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        motJumps.reset()
        jumpItem(motJumps, "Satz und Stimmung") {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.greeting, style = MaterialTheme.typography.titleLarge)
                    Text(p.sentence, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                    impulse?.let {
                        Text("Dein Impuls (KI): $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    MoodStrip(p)
                    val trend = when (p.moodTrend) {
                        1 -> "Deine Stimmung ist gestiegen."
                        -1 -> "Deine Stimmung ist gesunken."
                        else -> if (p.moodAvg != null) "Deine Stimmung ist etwa gleich geblieben." else "Noch keine Stimmung eingetragen."
                    }
                    Text(trend, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onMood) { Text("Stimmung eintragen") }
                }
            }
        }
        jumpItem(motJumps, "Das hast du geschafft") {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Das hast du geschafft", style = MaterialTheme.typography.titleMedium)
                    Text("Letzte drei Tage und heute", style = MaterialTheme.typography.bodySmall)
                    if (p.achieved.isEmpty()) {
                        Text("Noch nichts eingetragen. Jeder Anfang zählt.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        p.achieved.forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        jumpItem(motJumps, "Vorhaben für heute") {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                    Text("Vorhaben für heute", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                    Text(
                        "Aus deinen letzten Tagen, deinen Zielen und Aufgaben. Abhaken erledigt Schritte und Aufgaben direkt.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    p.intentions.forEach { IntentionRow(it, vm) }
                }
            }
        }
        val tips = p.tips
        if (tips.isNotEmpty()) {
            jumpItem(motJumps, "Tipps") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tipps", style = MaterialTheme.typography.titleMedium)
                        tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        if (p.support != SupportLevel.None) {
            jumpItem(motJumps, "Unterstützung") { SupportBox(p.support, p.professional) }
        }
        jumpItem(motJumps, "Persönlicher Impuls (KI)") {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Persönlicher Impuls (KI, optional)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Die KI formuliert aus deinen letzten Tagen einen Satz, kleine Vorhaben und Tipps für heute.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AiActionButton(
                        label = if (impulse == null) "Impuls für heute holen" else "Neuen Impuls holen",
                        note = "Enthalten sind deine Stimmung der letzten Tage, Stichworte, Zahlen und die Titel deiner Ziele, aber keine Namen.",
                        system = GoalAi.SYSTEM_PROMPT,
                        prompt = { GoalAi.impulsePrompt(p, caregiver.roleValue?.label) },
                        onAnswer = { answer, provider ->
                            val parsed = GoalAi.parseImpulse(answer, p.day, provider)
                            if (parsed != null) {
                                val json = parsed.toJson()
                                AppPrefs.setMotivatorImpulse(context, json)
                                vm.refreshMotivator()
                            }
                            parsed != null
                        },
                    )
                }
            }
        }
        jumpItem(motJumps, null) {
            Text(
                "Der Motivator ist eine Alltagshilfe mit Anhaltspunkten. Er ersetzt keine ärztliche, psychologische oder Pflegeberatung. Im Notfall 112.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MoodTab(vm: MainViewModel, onMood: () -> Unit) {
    val moods by vm.moods.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    var toDelete by remember { mutableStateOf<MoodEntry?>(null) }
    val zone = ZoneId.systemDefault()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(shape = buttonShape(), onClick = onMood, modifier = Modifier.fillMaxWidth()) { Text("Stimmung eintragen") }
        }
        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Letzte 14 Tage", style = MaterialTheme.typography.titleMedium)
                    val days = (today - 13..today).map { d -> d to moods.filter { it.day == d }.map { it.mood } }
                    Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        days.forEach { (_, list) ->
                            val avg = list.takeIf { it.isNotEmpty() }?.average()
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                if (avg != null) {
                                    Text(Motivator.emojiFor(avg), fontSize = 12.sp)
                                    val color = when {
                                        avg >= 3.5 -> MaterialTheme.colorScheme.primary
                                        avg >= 2.5 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height((avg * 14).dp)
                                            .background(color, MaterialTheme.shapes.extraSmall),
                                    )
                                } else {
                                    Box(Modifier.width(4.dp).height(4.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LocalDate.ofEpochDay(today - 13).format(DateTimeFormatter.ofPattern("dd.MM.")), style = MaterialTheme.typography.labelSmall)
                        Text("Heute", style = MaterialTheme.typography.labelSmall)
                    }
                    val week = moods.filter { it.day > today - 7 }
                    if (week.isNotEmpty()) {
                        val avg = week.map { it.mood }.average()
                        Text(
                            "Letzte 7 Tage: ${Motivator.emojiFor(avg)} im Schnitt ${"%.1f".format(java.util.Locale.GERMAN, avg)} von 5, ${week.size}× eingetragen",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val tagCount = week.flatMap { it.tagList() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4)
                        if (tagCount.isNotEmpty()) {
                            Text("Häufig: " + tagCount.joinToString(", ") { "${it.key} (${it.value})" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (moods.isEmpty()) {
            item { Text("Noch keine Einträge.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(moods.take(60), key = { it.id }) { e ->
            HCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Motivator.emoji(e.mood), fontSize = 24.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            Motivator.label(e.mood) + " · " + Instant.ofEpochMilli(e.atMillis).atZone(zone).format(TIME),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (e.tags.isNotBlank()) Text(e.tagList().joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Default.Delete, contentDescription = "Löschen") }
                }
            }
        }
    }
    toDelete?.let { e ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eintrag löschen?") },
            text = { Text("${Motivator.emoji(e.mood)} ${Motivator.label(e.mood)} vom ${Instant.ofEpochMilli(e.atMillis).atZone(zone).format(TIME)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMood(e)
                    toDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun GoalsTab(vm: MainViewModel, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val steps by vm.goalSteps.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val today = LocalDate.now().toEpochDay()
    var showOld by rememberSaveable { mutableStateOf(false) }
    val active = goals.filter { it.status == Goal.ACTIVE }
    val paused = goals.filter { it.status == Goal.PAUSED }
    val reached = goals.filter { it.status == Goal.REACHED }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            androidx.compose.material3.Button(shape = buttonShape(), onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Neues Ziel") }
        }
        item {
            Text(
                "Ein Ziel kann ein Antrag sein (z. B. Pflegegrad), Entlastung, etwas im Alltag oder für deine Gesundheit. " +
                    "Du gehst es Schritt für Schritt an; der Motivator schätzt deinen Weg ein und gibt Tipps.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (active.isEmpty()) {
            item { Text("Noch kein aktives Ziel.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(active, key = { it.id }) { g ->
            GoalCard(g, Motivator.progress(g, steps, today), persons.firstOrNull { it.id == g.personId }?.name) { onOpen(g.id) }
        }
        if (paused.isNotEmpty() || reached.isNotEmpty()) {
            item {
                TextButton(onClick = { showOld = !showOld }) {
                    Text((if (showOld) "▴ " else "▾ ") + "Pausiert (${paused.size}) und erreicht (${reached.size})")
                }
            }
            if (showOld) {
                items(paused + reached, key = { it.id }) { g ->
                    GoalCard(g, Motivator.progress(g, steps, today), persons.firstOrNull { it.id == g.personId }?.name) { onOpen(g.id) }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(goal: Goal, p: GoalProgress, personName: String?, onClick: () -> Unit) {
    HCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val state = when (goal.status) {
                Goal.REACHED -> " · erreicht 🎉"
                Goal.PAUSED -> " · pausiert"
                else -> ""
            }
            Text(
                GoalKind.fromKey(goal.kind).label + " · " + (personName ?: "für mich") + state,
                style = MaterialTheme.typography.labelMedium,
            )
            GoalMini(p)
        }
    }
}
