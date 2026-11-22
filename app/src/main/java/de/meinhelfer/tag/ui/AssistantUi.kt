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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.summary.Assistant
import de.meinhelfer.tag.summary.Expression
import de.meinhelfer.tag.summary.NextKind
import de.meinhelfer.tag.summary.NextUp
import de.meinhelfer.tag.summary.Proposal
import de.meinhelfer.tag.summary.SupportLevel
import de.meinhelfer.tag.summary.Tageslage
import de.meinhelfer.tag.summary.VitalKind
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
    val line = Tageslage.dayLine(persons, meds, checkins, appointments, todos, now, zone)
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
fun AssistantCard(vm: MainViewModel, onOpen: (tab: Int) -> Unit, onMood: () -> Unit) {
    val st = rememberAssistantState(vm)
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val speaking by Speech.speaking.collectAsState()
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
                        Text("Ich bin ${st.name}, dein Assistent (Beta).", style = MaterialTheme.typography.bodySmall)
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
    "werte" to (emptyList<String>() to listOf("Trinken und Werte", "Werte von Uhr oder Messgerät (Beta)", "Trinken und Pflegetagebuch")),
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

// ---------------------------------------------------------------- Einstellungen → Beta-Funktionen

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AssistantSettingsCard(vm: MainViewModel) {
    val on by vm.assistantOn.collectAsStateWithLifecycle()
    val name by vm.assistantName.collectAsStateWithLifecycle()
    var own by remember { mutableStateOf("") }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistantFace(Expression.Friendly, size = 44.dp)
                Text("Persönlicher Assistent (Beta)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
        }
    }
}
