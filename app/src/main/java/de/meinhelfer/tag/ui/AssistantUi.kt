package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.summary.Assistant
import de.meinhelfer.tag.summary.Expression
import de.meinhelfer.tag.summary.NextKind
import de.meinhelfer.tag.summary.NextUp
import de.meinhelfer.tag.summary.Proposal
import de.meinhelfer.tag.summary.SupportLevel
import de.meinhelfer.tag.summary.Tageslage
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.AssistantBrief
import de.meinhelfer.tag.update.AppPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Alles, was der Assistent gerade weiß (über alle betreuten Personen). */
private class AssistantState(
    val name: String,
    val line: List<NextUp>,
    val proposals: List<Proposal>,
    val preps: List<de.meinhelfer.tag.summary.AppointmentPrep>,
    val expression: Expression,
    val headline: String,
    val multi: Boolean,
)

@Composable
private fun rememberAssistantState(vm: MainViewModel): AssistantState {
    val name by vm.assistantName.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val appointments by vm.allAppointments.collectAsStateWithLifecycle()
    val todos by vm.allTodos.collectAsStateWithLifecycle()
    val checkins by vm.checkins.collectAsStateWithLifecycle()
    val duties by vm.duties.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val dismissed by vm.assistantDismissed.collectAsStateWithLifecycle()
    val vitals by vm.allVitals.collectAsStateWithLifecycle()
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val now = LocalDateTime.now()
    val zone = ZoneId.systemDefault()
    val window = AppPrefs.intakeWindow(androidx.compose.ui.platform.LocalContext.current).toLong()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val planTick by vm.measurePlanTick.collectAsStateWithLifecycle()
    val plans = remember(persons, planTick) {
        persons.associate { p -> p.id to de.meinhelfer.tag.summary.MeasurePlans.parse(AppPrefs.measurePlan(appContext, p.id)) }
    }
    val measures = de.meinhelfer.tag.summary.MeasurePlans.openToday(plans, vitals, now, zone)
    val line = Tageslage.dayLine(persons, meds, checkins, appointments, todos, now, zone, window, measures)
    val proposals = Assistant.proposals(persons, todos, meds, duties, doctors, today, dismissed)
    val measured = persons.associate { p ->
        p.id to vitals.any { it.personId == p.id && it.kind != VitalKind.Trinken.key && it.day >= today - 14 }
    }
    val preps = Assistant.preps(persons, appointments, checkins, meds, doctors, measured, now, zone)
    val doneToday = checkins.count { it.day == today && it.done } +
        todos.count { it.done && it.doneMillis != null && Instant.ofEpochMilli(it.doneMillis).atZone(zone).toLocalDate().toEpochDay() == today }
    val expression = Assistant.expression(line, plan?.support ?: SupportLevel.None, doneToday, now)
    return AssistantState(name, line, proposals, preps, expression, Assistant.headline(line, persons.size > 1), persons.size > 1)
}

private fun icon(n: NextUp) = when (n.kind) {
    NextKind.Intake -> "💊"
    NextKind.Appointment -> "📅"
    NextKind.Task -> "✅"
    NextKind.Measure -> "🩺"
}

@Composable
private fun NextUpRow(n: NextUp, multi: Boolean) {
    Text(
        icon(n) + " " + n.title + (if (multi && n.personName.isNotBlank()) " · ${n.personName}" else "") +
            (if (n.overdue) (if (n.kind == NextKind.Task) " · überfällig" else " · noch offen") else ""),
        style = MaterialTheme.typography.bodyMedium,
        color = if (n.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ProposalBox(p: Proposal, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("💡 " + p.text, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = buttonShape(), onClick = { vm.applyProposal(p) }) { Text(p.confirmLabel) }
            TextButton(onClick = { vm.dismissProposal(p.key) }) { Text("Nein, danke") }
        }
    }
}

/** Vorlese-Text des Assistenten. */
private fun speech(st: AssistantState, greeting: String?, sentence: String?): String = buildString {
    greeting?.let { append(it).append(". ") }
    append(st.headline).append(" ")
    st.preps.forEach { p ->
        append(p.whenText).append(" Uhr: ").append(p.appointment.title).append(". ")
        p.hints.forEach { append(it).append(". ") }
    }
    st.proposals.forEach { append(it.text).append(" ") }
    sentence?.let { append("Und für dich: ").append(it) }
}

/** Karte auf der Heute-Seite (ersetzt den Tages-Motivator, solange der Assistent an ist). */
@Composable
fun AssistantCard(vm: MainViewModel, onOpen: (tab: Int) -> Unit, onMood: () -> Unit, onOpenDailyStart: (planOnly: Boolean) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val personId by vm.personId.collectAsStateWithLifecycle()
    val todayDay by vm.today.collectAsStateWithLifecycle()
    var startTick by remember { mutableStateOf(0) }
    // Tagesstart über den Assistenten: einmal am Tag anbieten statt automatisch zu öffnen
    val offerStart = remember(personId, todayDay, startTick) {
        AppPrefs.dailyStartEnabled(context) && personId > 0 && AppPrefs.lastDailyStart(context, personId) < todayDay
    }
    val st = rememberAssistantState(vm)
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val speaking by Speech.speaking.collectAsState()
    val ownHealth by vm.ownHealth.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshOwnHealth() }
    val selfHints = AssistantBrief.selfHints(ownHealth, java.time.LocalTime.now().hour)
    val p = plan
    Foldable(
        key = "assistent",
        title = st.name + " · dein Assistent",
        summary = st.headline,
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistantFace(st.expression, size = 60.dp, speaking = speaking == "assistent", description = "Gesicht von ${st.name}")
                    Column(Modifier.weight(1f)) {
                        Text(p?.greeting ?: "Hallo", style = MaterialTheme.typography.titleMedium)
                        Text("Ich bin ${st.name}, dein persönlicher Assistent.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (offerStart) {
                    HCard(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Wollen wir kurz auf gestern schauen und den Tag planen?", style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(shape = buttonShape(), onClick = {
                                    AppPrefs.setLastDailyStart(context, personId, todayDay)
                                    startTick++
                                    onOpenDailyStart(false)
                                }) { Text("Tagesstart") }
                                TextButton(onClick = {
                                    AppPrefs.setLastDailyStart(context, personId, todayDay)
                                    startTick++
                                }) { Text("Heute nicht") }
                            }
                        }
                    }
                }
                Text(st.headline, style = MaterialTheme.typography.bodyLarge)
                if (st.line.size > 1) {
                    st.line.take(5).forEach { NextUpRow(it, st.multi) }
                    if (st.line.size > 5) Text("… und ${st.line.size - 5} weitere", style = MaterialTheme.typography.bodySmall)
                }
                st.preps.forEach { prep ->
                    HorizontalDivider()
                    Text(
                        "📅 ${prep.whenText} · ${prep.appointment.title}" + (if (st.multi && prep.personName.isNotBlank()) " (${prep.personName})" else ""),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (prep.hints.isEmpty()) {
                        Text("Ich sehe dafür nichts vorzubereiten.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        prep.hints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        PrepPdfButtons(vm, prep.appointment.personId)
                    }
                }
                if (st.proposals.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Mein Vorschlag", style = MaterialTheme.typography.labelLarge)
                    st.proposals.take(3).forEach { ProposalBox(it, vm) }
                }
                if (p != null) {
                    HorizontalDivider()
                    Text("Für dich", style = MaterialTheme.typography.labelLarge)
                    Text(p.sentence, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                    selfHints.forEach { Text("⌚ $it", style = MaterialTheme.typography.bodyMedium) }
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { onOpenDailyStart(false) }, modifier = Modifier.weight(1f)) { FitText("Rückblick gestern", maxSize = 14.sp) }
                    OutlinedButton(shape = buttonShape(), onClick = { onOpenDailyStart(true) }, modifier = Modifier.weight(1f)) { FitText("Tagesplan", maxSize = 14.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { onOpen(0) }, modifier = Modifier.weight(1f)) { FitText("Mehr", maxSize = 14.sp) }
                    OutlinedButton(shape = buttonShape(), onClick = { onOpen(2) }, modifier = Modifier.weight(1f)) { FitText("Ziele", maxSize = 14.sp) }
                }
                SpeakButton(owner = "assistent", label = "🔊 Vorlesen", modifier = Modifier.fillMaxWidth()) {
                    speech(st, p?.greeting, p?.sentence)
                }
            }
        }
    }
}

/** Unterlagen für den Termin gleich als PDF teilen: Medikamentenplan und Werte der letzten 14 Tage. */
@Composable
private fun PrepPdfButtons(vm: MainViewModel, personId: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val duties by vm.duties.collectAsStateWithLifecycle()
    val vitals by vm.allVitals.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val person = persons.firstOrNull { it.id == personId }
    val mine = meds.filter { it.personId == personId }
    val values = vitals.filter { it.personId == personId && it.day > today - 14 }
    if (mine.isEmpty() && values.none { it.kind != VitalKind.Trinken.key }) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mine.isNotEmpty()) {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            de.meinhelfer.tag.export.CarePdfs.medPlan(context, person, mine, doctors, AppPrefs.caregiver(context), duties)
                        }
                        sharePdfFile(context, f, "Medikamentenplan teilen")
                    }
                }
            }) { Text("Medikamentenplan") }
        }
        if (values.any { it.kind != VitalKind.Trinken.key }) {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            recentVitalsPdf(context, person, values, 14)
                        }
                        sharePdfFile(context, f, "Werte teilen")
                    }
                }
            }) { Text("Werte (14 Tage)") }
        }
    }
}

// ---------------------------------------------------------------- Hilfe zur Seite und „Frag mich“

/** Welche Teile der Anleitung zu welcher Seite gehören (Abschnitt oder einzelne Punkte). */
private val PAGE_HELP: Map<String, Pair<List<String>, List<String>>> = mapOf(
    "heute" to (listOf("heute") to emptyList()),
    "medikamente" to (listOf("medikamente") to emptyList()),
    "medikamentenplan" to (listOf("medikamente") to emptyList()),
    "nachbestellung" to (listOf("nachbestellung") to emptyList()),
    "termine" to (listOf("termine") to emptyList()),
    "dokumente" to (listOf("dokumente") to emptyList()),
    "schreiben" to (emptyList<String>() to listOf("Schreiben und Vollmachten erstellen")),
    "kontakte" to (listOf("kontakte") to emptyList()),
    "infos" to (listOf("infos") to emptyList()),
    "wegweiser" to (listOf("wegweiser") to emptyList()),
    "werte" to (emptyList<String>() to listOf("Trinken und Werte", "Schnellknöpfe und Messzeiten", "Bericht über Messwerte: Zeitraum wählen, mailen, ablegen", "Werte von Uhr oder Messgerät (Beta)", "Trinken und Pflegetagebuch")),
    "tagebuch" to (emptyList<String>() to listOf("Pflegetagebuch", "Trinken und Pflegetagebuch")),
    "tagesstart" to (emptyList<String>() to listOf("Tagesstart: Rückblick und Tagesplan")),
    "budgets" to (emptyList<String>() to listOf("Budgets der Pflegekasse")),
    "uebergabe" to (emptyList<String>() to listOf("Übergabe-Bericht")),
    "assistent" to (listOf("motivator") to emptyList()),
    "auswertung" to (listOf("auswertung") to emptyList()),
    "einstellungen" to (listOf("einstellungen") to emptyList()),
    "anleitung" to (listOf("start") to emptyList()),
)

private val PAGE_NAMES = mapOf(
    "heute" to "Heute", "medikamente" to "Medikamente", "medikamentenplan" to "Medikamentenplan", "nachbestellung" to "Nachbestellung",
    "termine" to "Termine", "dokumente" to "Dokumente", "schreiben" to "Schreiben", "kontakte" to "Kontakte", "infos" to "Infos",
    "wegweiser" to "Wegweiser", "werte" to "Trinken und Werte", "tagebuch" to "Pflegetagebuch", "tagesstart" to "Tagesstart",
    "budgets" to "Budgets", "uebergabe" to "Übergabe", "assistent" to "Assistent", "auswertung" to "Auswertung",
    "einstellungen" to "Einstellungen", "anleitung" to "Anleitung",
)

internal fun guideFor(page: String): List<GuideItem> {
    val (sections, titles) = PAGE_HELP[page] ?: (listOf("start") to emptyList())
    val fromSections = GUIDE_SECTIONS.filter { it.id in sections }.flatMap { it.items }
    val fromTitles = GUIDE_SECTIONS.flatMap { it.items }.filter { it.title in titles }
    return (fromSections + fromTitles).distinctBy { it.title }
}

private val STOP = setOf(
    "wie", "was", "wo", "wer", "wann", "warum", "ich", "kann", "man", "der", "die", "das", "ein", "eine", "einen", "und", "oder", "mit",
    "für", "finde", "mache", "geht", "bei", "auf", "von", "den", "dem", "des", "ist", "sind", "mein", "meine", "meinen", "nicht", "auch",
    "hier", "dort", "gibt", "es", "zu", "im", "in", "an", "am", "wird", "werden", "soll", "will", "möchte", "bitte", "app",
)

/** Antworten aus der Anleitung zu einer Frage (ohne Internet): die passendsten Punkte. */
internal fun searchGuide(question: String, max: Int = 3): List<GuideItem> {
    val words = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }
    if (words.isEmpty()) return emptyList()
    val stems = words.map { it.take(if (it.length > 6) it.length - 2 else it.length) }
    return GUIDE_SECTIONS.flatMap { it.items }
        .map { item ->
            val title = item.title.lowercase()
            val text = item.text.lowercase()
            val score = stems.sumOf { st -> (if (st in title) 4 else 0) + (if (st in text) 1 else 0) } +
                (if (stems.all { it in title || it in text }) 3 else 0)
            item to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(max)
        .map { it.first }
}

/** Was der Assistent zur aktuellen Seite sagt (kurz, aus den Daten). */
@Composable
private fun pageNote(page: String, vm: MainViewModel, st: AssistantState): String {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val personId by vm.personId.collectAsStateWithLifecycle()
    val who = persons.firstOrNull { it.id == personId }?.name ?: "die Person"
    val zone = ZoneId.systemDefault()
    return when (page) {
        "medikamente", "medikamentenplan", "nachbestellung" -> {
            val low = lowStockMeds(meds, today)
            "Für $who sind ${meds.size} Medikamente eingetragen. " +
                if (low.isEmpty()) "Der Vorrat reicht überall länger als eine Woche (soweit bekannt)."
                else "Bald nachbestellen: " + low.joinToString(", ") { it.name } + "."
        }
        "termine" -> {
            val next = appointments.filter { it.whenMillis >= System.currentTimeMillis() }.minByOrNull { it.whenMillis }
            if (next == null) "Für $who ist kein Termin geplant."
            else {
                val d = Instant.ofEpochMilli(next.whenMillis).atZone(zone).toLocalDate().toEpochDay()
                "Nächster Termin für $who: ${Tageslage.dayWord(d, today)} um ${Tageslage.time(next.whenMillis, zone)} Uhr, ${next.title}." +
                    if (st.preps.isNotEmpty()) " Was vorzubereiten ist, steht auf der Heute-Seite bei mir." else ""
            }
        }
        "dokumente" -> "Für $who liegen ${documents.size} Dokumente sicher auf dem Handy. Entlassberichte und Medikamentenpläne erkenne ich beim Hinzufügen."
        "kontakte" -> "${doctors.size} Kontakte. Wer für Rezepte und Apotheke zuständig ist und wer vertritt, legst du beim Kontakt fest."
        "werte", "tagebuch" -> {
            val drunk = vitals.filter { it.kind == VitalKind.Trinken.key && it.day == today }.sumOf { it.value }.toInt()
            val measured = vitals.count { it.kind != VitalKind.Trinken.key && it.day == today }
            "Heute für $who: $drunk ml getrunken, $measured Messwerte eingetragen."
        }
        "infos", "wegweiser", "budgets" -> "Hier findest du Wissen und Leistungen. Was du dir merken willst, kannst du als Aufgabe übernehmen."
        else -> st.headline
    }
}

/** Blatt, das sich über das Gesicht in der unteren Leiste öffnet: Lage, Vorschläge, Hilfe zur Seite, „Frag mich“. */
@Composable
fun AssistantSheetContent(vm: MainViewModel, page: String, onOpenGuide: () -> Unit, onOpenAssistant: () -> Unit) {
    val st = rememberAssistantState(vm)
    val speaking by Speech.speaking.collectAsState()
    var question by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val help = remember(page) { guideFor(page) }
    val note = pageNote(page, vm, st)
    val answers = remember(question) { searchGuide(question) }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistantFace(st.expression, size = 52.dp, speaking = speaking == "seite_$page", description = "Gesicht von ${st.name}")
            Column(Modifier.weight(1f)) {
                Text(st.name, style = MaterialTheme.typography.titleMedium)
                Text("Zur Seite „${PAGE_NAMES[page] ?: "Heute"}“", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(note, style = MaterialTheme.typography.bodyLarge)
        if (page != "heute" && st.line.isNotEmpty()) Text("Heute als Nächstes: " + st.line.first().title, style = MaterialTheme.typography.bodySmall)
        st.proposals.take(2).forEach { ProposalBox(it, vm) }
        SpeakButton(owner = "seite_$page", label = "🔊 Vorlesen") {
            note + " " + help.take(6).joinToString(" ") { it.title + ". " + it.text }
        }

        HorizontalDivider()
        Text("Frag mich", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("z. B. Wie drucke ich den Medikamentenplan?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (question.isNotBlank()) {
            if (answers.isEmpty()) {
                Text("Dazu finde ich in der Anleitung nichts. Versuch es mit anderen Worten oder schau in die Anleitung.", style = MaterialTheme.typography.bodySmall)
            }
            answers.forEach { a ->
                Column(Modifier.fillMaxWidth().clickable { open = if (open == "f_" + a.title) null else "f_" + a.title }) {
                    Text("❓ " + a.title, style = MaterialTheme.typography.bodyLarge)
                    if (open == "f_" + a.title) Text(a.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Freiwillig: Frage mit KI beantworten, nur mit Abschnitten der Anleitung, ohne Daten aus der App
            if (de.meinhelfer.tag.ai.AiConfig.isConfigured(context)) {
                AiActionButton(
                    label = "${st.name} mit KI fragen",
                    note = "Gesendet werden nur deine Frage, so wie du sie eingetippt hast, und passende Abschnitte der Anleitung. " +
                        "Keine Daten aus der App (keine Namen, Medikamente, Termine). Schreib in die Frage bitte keine Namen.",
                    system = assistantAiSystem(st.name),
                    prompt = { assistantAiPrompt(question, answers + searchGuide(question, 6) + help) },
                    onAnswer = { a, _ ->
                        aiAnswer = a.trim().takeIf { it.isNotBlank() }
                        aiAnswer != null
                    },
                )
            } else {
                Text("Mit eingerichteter KI (Einstellungen → KI und Internet) kann ich Fragen auch frei beantworten.", style = MaterialTheme.typography.bodySmall)
            }
            aiAnswer?.let {
                Text("🤖 $it", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "KI-Antwort, nur ein Anhaltspunkt. Bei Gesundheit, Recht und Geld zählt, was Ärztin, Arzt, Apotheke, Pflegekasse oder eine Beratungsstelle sagen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HorizontalDivider()
        Text("So geht's hier", style = MaterialTheme.typography.titleSmall)
        help.forEach { item ->
            Column(Modifier.fillMaxWidth().clickable { open = if (open == item.title) null else item.title }) {
                Text((if (open == item.title) "▴ " else "▾ ") + item.title, style = MaterialTheme.typography.bodyLarge)
                if (open == item.title) Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(shape = buttonShape(), onClick = onOpenAssistant, modifier = Modifier.weight(1f)) { FitText("Zu ${st.name}", maxSize = 14.sp) }
            OutlinedButton(shape = buttonShape(), onClick = onOpenGuide, modifier = Modifier.weight(1f)) { FitText("Anleitung", maxSize = 14.sp) }
        }
        Text(
            "Ich rechne nur auf dem Handy und ändere nichts ohne deinen Tipp. Keine medizinischen Ratschläge.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Kleines Gesicht für die untere Leiste. */
@Composable
fun AssistantDockFace(vm: MainViewModel) {
    val st = rememberAssistantState(vm)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistantFace(st.expression, size = 26.dp, description = "${st.name} öffnen")
        Text(st.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        if (st.line.any { it.overdue } || st.proposals.isNotEmpty()) {
            Text("•", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------- Einstellungen → Persönlicher Assistent

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AssistantSettingsCard(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val on by vm.assistantOn.collectAsStateWithLifecycle()
    val name by vm.assistantName.collectAsStateWithLifecycle()
    var own by remember { mutableStateOf("") }
    var morning by remember { mutableStateOf(AppPrefs.assistantMorning(context)) }
    var evening by remember { mutableStateOf(AppPrefs.assistantEvening(context)) }
    var ownValues by remember { mutableStateOf(AppPrefs.assistantOwnHealth(context)) }
    val ownLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshOwnHealth(force = true) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistantFace(Expression.Friendly, size = 44.dp)
                Text("Persönlicher Assistent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { vm.setAssistantOn(it) })
            }
            Text(
                "Dein Assistent ist für dich da: Er begleitet dich durch den Tag, sagt, was als Nächstes ansteht, bereitet Termine vor, " +
                    "schlägt Prioritäten und Nachbestellungen vor und hilft auf jeder Seite (Gesicht in der Leiste unten). " +
                    "Er übernimmt alles vom Tages-Motivator und ändert nichts ohne deinen Tipp. Aus: wieder der Tages-Motivator.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Name: $name", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Assistant.NAME_SUGGESTIONS.forEach { n ->
                    FilterChip(selected = n == name, onClick = { vm.setAssistantName(n) }, label = { Text(n) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = own,
                    onValueChange = { own = it.take(24) },
                    label = { Text("Eigener Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(shape = buttonShape(), enabled = own.isNotBlank(), onClick = {
                    vm.setAssistantName(own)
                    own = ""
                }) { Text("Übernehmen") }
            }
            HorizontalDivider()
            Text("Benachrichtigungen von $name", style = MaterialTheme.typography.labelLarge)
            OptionalTimeRow(
                label = "Morgenüberblick",
                hint = "Was heute ansteht und was für Termine bereitliegen sollte",
                minute = morning,
                default = 7 * 60 + 30,
            ) {
                morning = it
                AppPrefs.setAssistantMorning(context, it)
                de.meinhelfer.tag.reminder.ReminderScheduler.scheduleAssistant(context)
            }
            OptionalTimeRow(
                label = "Abend",
                hint = "Was geschafft ist, was offen bleibt, was du für morgen bereitlegen kannst",
                minute = evening,
                default = 20 * 60,
            ) {
                evening = it
                AppPrefs.setAssistantEvening(context, it)
                de.meinhelfer.tag.reminder.ReminderScheduler.scheduleAssistant(context)
            }
            Text(
                "Bei Terminerinnerungen hängt $name eine kurze Checkliste an (z. B. Versichertenkarte, Medikamentenplan). Die Ruhezeit stellst du unter Erinnerungen ein.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Meine eigenen Werte nutzen (Beta)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Schlaf, Schritte und Ruhepuls von deiner Uhr (Health Connect) für Hinweise nur für dich. Wird nur gelesen, nicht gespeichert.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = ownValues, onCheckedChange = {
                    ownValues = it
                    AppPrefs.setAssistantOwnHealth(context, it)
                    if (it && de.meinhelfer.tag.health.HealthConnectReader.available(context)) {
                        ownLauncher.launch(de.meinhelfer.tag.health.HealthConnectReader.OWN_PERMISSIONS)
                    }
                    vm.refreshOwnHealth(force = true)
                })
            }
            if (ownValues && !de.meinhelfer.tag.health.HealthConnectReader.available(context)) {
                Text("Health Connect ist auf diesem Handy nicht verfügbar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Uhrzeit mit Schalter (aus = -1). */
@Composable
internal fun OptionalTimeRow(label: String, hint: String, minute: Int, default: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label + if (minute >= 0) " um ${AssistantBrief.hm(minute)} Uhr" else " (aus)", style = MaterialTheme.typography.bodyLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = minute >= 0, onCheckedChange = { onChange(if (it) default else -1) })
        }
        if (minute >= 0) {
            TimePickerField(
                label = "Uhrzeit",
                time = java.time.LocalTime.of(minute / 60, minute % 60),
                onChange = { t -> onChange(t.hour * 60 + t.minute) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Einstellungen → Erinnerungen: alle Erinnerungen auf einen Blick, dazu die Ruhezeit. */
@Composable
fun ReminderOverviewCard(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val todos by vm.allTodos.collectAsStateWithLifecycle()
    val assistant by vm.assistantOn.collectAsStateWithLifecycle()
    val name by vm.assistantName.collectAsStateWithLifecycle()
    var quietOn by remember { mutableStateOf(AppPrefs.quietEnabled(context)) }
    var from by remember { mutableStateOf(AppPrefs.quietFrom(context)) }
    var to by remember { mutableStateOf(AppPrefs.quietTo(context)) }
    fun save() = AppPrefs.setQuiet(context, quietOn, from, to)
    val slots = meds.sumOf { it.timeList().size }
    val planned = todos.count { !it.done && !it.archived && (it.remindAt ?: 0L) > System.currentTimeMillis() }
    val drink = AppPrefs.drinkReminder(context)
    val diary = AppPrefs.diaryReminder(context)
    val morning = AppPrefs.assistantMorning(context)
    val evening = AppPrefs.assistantEvening(context)
    fun quietHit(m: Int) = quietOn && m >= 0 && AssistantBrief.inQuiet(m, from, to)
    val rows = listOf(
        "💊 Medikamente" to "$slots Einnahmezeiten, immer (auch in der Ruhezeit)" +
            if (AppPrefs.followupNotify(context)) "; Nachfrage nach ${AppPrefs.intakeWindow(context)} Min., wenn nicht abgehakt" else "",
        "📅 Termine" to "je Termin einstellbar, immer" + if (assistant) "; mit Checkliste von $name" else "",
        "✅ Aufgaben" to if (planned == 0) "keine Erinnerung geplant (beim Bearbeiten einer Aufgabe einstellbar)" else "$planned geplant",
        "🙂 Stimmungs-Check" to if (AppPrefs.moodNotify(context)) "an (Häufigkeit unter Heute-Seite, Motivator und Widget)" else "aus",
        "💧 Trinken" to when (drink) { 2 -> "alle 2 Stunden (10–18 Uhr)"; 3 -> "alle 3 Stunden (10–19 Uhr)"; else -> "aus" },
        "📓 Pflegetagebuch" to if (diary >= 0) "um ${AssistantBrief.hm(diary)} Uhr" else "aus",
        "🌅 $name morgens" to if (!assistant) "Assistent aus" else if (morning >= 0) "um ${AssistantBrief.hm(morning)} Uhr" + (if (quietHit(morning)) " – liegt in der Ruhezeit!" else "") else "aus",
        "🌙 $name abends" to if (!assistant) "Assistent aus" else if (evening >= 0) "um ${AssistantBrief.hm(evening)} Uhr" + (if (quietHit(evening)) " – liegt in der Ruhezeit!" else "") else "aus",
    )
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Alle Erinnerungen auf einen Blick", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = if (value.endsWith("!")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ruhezeit", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "In dieser Zeit melden sich Trinken, Stimmungs-Check und $name nicht. Medikamente, Termine, Aufgaben und das Pflegetagebuch kommen immer.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = quietOn, onCheckedChange = {
                    quietOn = it
                    save()
                })
            }
            if (quietOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePickerField(
                        label = "Von",
                        time = java.time.LocalTime.of(from / 60, from % 60),
                        onChange = { t ->
                            from = t.hour * 60 + t.minute
                            save()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TimePickerField(
                        label = "Bis",
                        time = java.time.LocalTime.of(to / 60, to % 60),
                        onChange = { t ->
                            to = t.hour * 60 + t.minute
                            save()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Anweisung für die KI, wenn „Frag mich“ mit KI beantwortet wird. */
internal fun assistantAiSystem(name: String): String =
    "Du bist $name, der persönliche Assistent in der Android-App „Mein Helfer für den Tag“ für pflegende Angehörige. " +
        "Antworte freundlich, in einfacher Sprache, in der Du-Form und kurz (höchstens sechs Sätze). " +
        "Fragen zur Bedienung der App beantwortest du nur anhand der mitgeschickten Abschnitte der Anleitung; erfinde keine Funktionen. " +
        "Steht die Antwort nicht darin, sag das ehrlich und nenne, wo man in der App nachsehen kann. " +
        "Allgemeine Fragen zur Pflege darfst du kurz allgemein beantworten, ausdrücklich als Anhaltspunkt, und auf Pflegestützpunkt, Pflegekasse oder Beratungsstellen verweisen. " +
        "Gib keine medizinischen Ratschläge, keine Diagnosen und keine Rechts- oder Finanzberatung."

/** Frage und passende Abschnitte der Anleitung (ohne Daten aus der App). */
internal fun assistantAiPrompt(question: String, items: List<GuideItem>): String = buildString {
    appendLine("Frage: " + question.trim().take(500))
    appendLine()
    appendLine("Passende Abschnitte der Anleitung:")
    var size = 0
    items.distinctBy { it.title }.forEach { item ->
        val block = "- " + item.title + ": " + item.text
        if (size + block.length > 7000) return@forEach
        appendLine(block)
        size += block.length
    }
}
