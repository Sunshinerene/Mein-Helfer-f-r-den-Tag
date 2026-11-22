package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Todo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HIST_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

internal fun stars(rating: Int): String = if (rating in 1..5) "★".repeat(rating) + "☆".repeat(5 - rating) else ""

internal fun doneDateText(t: Todo): String? =
    t.doneMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(HIST_DATE) }

/**
 * Findet fruehere Aufgaben zum gleichen Thema (rein lokal).
 * Vergleicht Woerter grob (ohne Umlaute, ohne Fuellwoerter); "Rollstuhl" passt z. B. zu "Rollstuehle" und "Rollstuhlreparatur".
 */
internal object TopicMatcher {
    private val STOP = setOf(
        "und", "oder", "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einer", "eines",
        "fur", "fuer", "mit", "von", "vom", "beim", "bei", "nach", "auch", "noch", "mal", "zum", "zur", "ins", "im", "in", "an", "am", "auf", "aus",
        "suchen", "finden", "machen", "lassen", "klaren", "klaeren", "fragen", "nachfragen", "anrufen", "besorgen",
        "vereinbaren", "termin", "termine", "erledigen", "prufen", "pruefen", "holen", "abholen", "bitte", "wegen",
        "neue", "neuen", "neuer", "neues", "wieder", "genau", "sollte", "soll", "kann", "muss", "werden", "wird", "ist", "sind",
        "heute", "morgen", "woche", "alltag",
    )

    fun normalize(s: String): String =
        s.lowercase(Locale.GERMANY).replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")

    fun tokens(s: String): Set<String> =
        normalize(s).split(Regex("[^a-z0-9]+")).filter { it.length >= 4 && it !in STOP }.toSet()

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    /** Gleiches Wort oder gleicher Wortstamm (mind. 6 gleiche Buchstaben am Anfang, 80 % des kuerzeren Worts). */
    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        val p = commonPrefix(a, b)
        return p >= 6 && p >= shorter * 0.8
    }

    fun score(text: String, doctorId: Long?, past: Todo): Int {
        val a = tokens(text)
        if (a.isEmpty()) return 0
        val b = tokens(past.text + " " + past.result)
        var s = a.count { x -> b.any { y -> sameWord(x, y) } } * 2
        if (s > 0 && doctorId != null && doctorId == past.doctorId) s += 1
        return s
    }

    /** Fruehere, erledigte Aufgaben zum gleichen Thema, beste zuerst. */
    fun similar(text: String, doctorId: Long?, history: List<Todo>, excludeId: Long = 0, limit: Int = 3): List<Todo> =
        history.asSequence()
            .filter { it.done && it.id != excludeId }
            .map { it to score(text, doctorId, it) }
            .filter { it.second >= 2 }
            .sortedWith(compareByDescending<Pair<Todo, Int>> { it.second }.thenByDescending { it.first.doneMillis ?: it.first.createdMillis })
            .take(limit)
            .map { it.first }
            .toList()
}

/** Eine fruehere Aufgabe kurz: Datum, Text, Ergebnis, Sterne, Kontakt. */
@Composable
internal fun PastTaskLine(t: Todo, doctors: List<Doctor>, onClick: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(
                    if (t.done) "erledigt" + (doneDateText(t)?.let { " am $it" } ?: "") else "offen",
                    stars(t.rating).takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(t.text, style = MaterialTheme.typography.bodyMedium)
            if (t.result.isNotBlank()) {
                Text("Ergebnis: ${t.result}", style = MaterialTheme.typography.bodyMedium)
            }
            doctors.firstOrNull { it.id == t.doctorId }?.let {
                Text(doctorLabel(it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Hinweis "Dazu gab es schon etwas" mit den frueheren Aufgaben. */
@Composable
internal fun PastTopicsBox(matches: List<Todo>, doctors: List<Doctor>) {
    if (matches.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Dazu gab es schon etwas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        matches.forEach { PastTaskLine(it, doctors) }
    }
}

/** Nach dem Abhaken: festhalten, was herausgekommen ist (freiwillig). */
@Composable
internal fun TaskResultDialog(task: Todo, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var result by remember { mutableStateOf(task.result) }
    var rating by remember { mutableIntStateOf(task.rating) }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text(if (task.result.isBlank()) "Erledigt – was kam heraus?" else "Ergebnis") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(task.text, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = result,
                    onValueChange = { result = it },
                    label = { Text("Ergebnis (freiwillig)") },
                    placeholder = { Text("z. B. Firma, Telefonnummer, Preis, wie es lief") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Bewertung (freiwillig)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { n ->
                        Text(
                            if (n <= rating) "★" else "☆",
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { rating = if (rating == n) 0 else n }.padding(horizontal = 4.dp),
                        )
                    }
                }
                Text(
                    "Bleibt im Aufgaben-Verlauf und wird bei ähnlichen Aufgaben später wieder angezeigt.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(result, rating) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (task.result.isBlank()) "Überspringen" else "Abbrechen") } },
    )
}

/** Verlauf aller Aufgaben der Person, durchsuchbar (Text, Ergebnis, Kontakt, Datum). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskHistoryDialog(
    history: List<Todo>,
    doctors: List<Doctor>,
    vm: MainViewModel,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 alle, 1 offen, 2 erledigt
    var chosen by remember { mutableStateOf<Todo?>(null) }
    var resultFor by remember { mutableStateOf<Todo?>(null) }
    var confirmDelete by remember { mutableStateOf<Todo?>(null) }

    val words = TopicMatcher.normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    val shown = history
        .filter { filter == 0 || (filter == 1 && !it.done) || (filter == 2 && it.done) }
        .filter { t ->
            if (words.isEmpty()) return@filter true
            val hay = TopicMatcher.normalize(
                listOfNotNull(t.text, t.result, doctors.firstOrNull { it.id == t.doctorId }?.let { doctorLabel(it) }, doneDateText(t)).joinToString(" "),
            )
            words.all { it in hay }
        }
        .sortedByDescending { it.doneMillis ?: it.createdMillis }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aufgaben-Verlauf", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Suchen") },
                    placeholder = { Text("z. B. Rollstuhl, Fahrdienst, 2025") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("Alle (${history.size})", "Offen", "Erledigt").forEachIndexed { i, label ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                    }
                }
                Text(
                    "Hier bleiben alle Aufgaben, auch ausgeblendete erledigte. Antippen für Ergebnis, Wiederaufnehmen oder Löschen.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shown.isEmpty()) {
                        item { Text(if (history.isEmpty()) "Noch keine Aufgaben." else "Nichts gefunden.", style = MaterialTheme.typography.bodyLarge) }
                    }
                    items(shown, key = { it.id }) { t -> PastTaskLine(t, doctors) { chosen = t } }
                }
            }
        }
    }

    chosen?.let { t ->
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { chosen = null },
            title = { Text(t.text.take(80)) },
            text = {
                Column {
                    TextButton(onClick = {
                        resultFor = t
                        chosen = null
                    }) { Text(if (t.result.isBlank()) "Ergebnis eintragen" else "Ergebnis ändern") }
                    if (t.done) {
                        TextButton(onClick = {
                            vm.reopenTodo(t)
                            chosen = null
                        }) { Text("Wieder aufnehmen (als offen)") }
                    }
                    TextButton(onClick = {
                        confirmDelete = t
                        chosen = null
                    }) { Text("Endgültig löschen", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { chosen = null }) { Text("Abbrechen") } },
        )
    }
    resultFor?.let { t ->
        TaskResultDialog(
            task = t,
            onSave = { r, s ->
                vm.saveTodoResult(t, r, s)
                resultFor = null
            },
            onDismiss = { resultFor = null },
        )
    }
    confirmDelete?.let { t ->
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirmDelete = null },
            title = { Text("Endgültig löschen?") },
            text = { Text("„${t.text.take(80)}“ wird auch aus dem Verlauf und der Auswertung entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTodo(t)
                    confirmDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}
