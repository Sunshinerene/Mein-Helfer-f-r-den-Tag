package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** IDs der am Termin angehaengten Dokumente. */
internal fun Appointment.attachedIds(): List<Long> = docIds.split(",").mapNotNull { it.trim().toLongOrNull() }

/** Vorschlaege fuer einen Termin: passende Dokumente und was man mitnehmen koennte. Reine Hilfe, keine Pflichtliste. */
internal class VisitPlan(
    val suggestedDocs: List<Document>,
    val reasons: Map<Long, String>,
    val missingCategories: List<String>,
    val checklist: List<String>,
)

internal object VisitPlanner {
    private fun has(text: String, vararg words: String) = words.any { text.contains(it) }

    fun build(a: Appointment, doctor: Doctor?, person: Person?, medCount: Int, docs: List<Document>): VisitPlan {
        val text = (a.title + " " + doctor?.specialty.orEmpty() + " " + doctor?.name.orEmpty()).lowercase()
        val categories = DocMatcher.categoriesFor(text, general = true)
        val extra = mutableListOf<String>()
        when {
            has(text, "krankenhaus", "klinik", "aufnahme", "entlass", "op ") -> {
                extra += "Einweisung bzw. Überweisung, falls vorhanden"
                extra += "Vollmacht und Patientenverfügung, falls vorhanden"
            }
            has(text, "pflegebegutachtung", "begutachtung", "pflegekasse", "medicproof", "md ", "pflegegrad") -> {
                extra += "Notizen zum Alltag und zu Hilfebedarf (z. B. ein Pflegetagebuch)"
                extra += "Frühere Bescheide der Pflegekasse"
            }
            has(text, "physio", "ergo", "logo", "therapie") -> extra += "Die Verordnung (Rezept) für die Behandlung"
            has(text, "blut", "labor") -> extra += "Vorher nachfragen, ob man nüchtern kommen soll"
            has(text, "rezept", "apotheke") -> {}
            else -> extra += "Überweisung, falls für einen Facharzt nötig"
        }

        val checklist = buildList {
            add("Versichertenkarte")
            if (medCount > 0) add("Aktueller Medikamentenplan oder die Packungen ($medCount Medikamente erfasst)")
            addAll(extra)
            if (person != null && person.careLevel > 0 && has(text, "pflege", "begutachtung", "krankenhaus")) {
                add("Pflegegrad-Bescheid (Pflegegrad ${person.careLevel})")
            }
            add("Fragen und Beobachtungen vorher aufschreiben")
        }.distinct()

        val refDay = Instant.ofEpochMilli(a.whenMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val keywords = DocMatcher.keywords(listOf(a.title, a.notes), doctor)
        val matches = DocMatcher.match(docs, categories, doctor, keywords, refDay)
        val reasons = matches.associate { it.doc.id to it.reasonText }
        val suggested = matches.map { it.doc }
        val missing = categories.filter { c -> docs.none { it.category == c } }
        return VisitPlan(suggested, reasons, missing, checklist)
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

@Composable
internal fun DocRow(d: Document, hint: String?, checked: Boolean, onToggle: () -> Unit, onView: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
            Text(d.title, style = MaterialTheme.typography.bodyMedium)
            val meta = listOfNotNull(
                d.category,
                d.docDate?.let { LocalDate.ofEpochDay(it).format(SHORT_DATE) },
            ).joinToString(" · ")
            Text(meta + (hint?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onView) { Text("Ansehen") }
    }
}

/** Unterlagen zu einem Termin: Dokumente anhaengen, Vorschlaege sehen, Mitnehm-Liste auf die To-do-Liste setzen. */
@Composable
internal fun AppointmentDocsDialog(
    appointment: Appointment,
    doctor: Doctor?,
    doctors: List<Doctor>,
    docs: List<Document>,
    medCount: Int,
    person: Person?,
    onSave: (List<Long>) -> Unit,
    onAddTodo: (String) -> Unit,
    onClose: () -> Unit,
) {
    val plan = remember(appointment.id, docs.size) { VisitPlanner.build(appointment, doctor, person, medCount, docs) }
    val selected = remember {
        mutableStateListOf<Long>().apply { addAll(appointment.attachedIds().filter { id -> docs.any { it.id == id } }) }
    }
    val todoAdded = remember { mutableStateListOf<String>() }
    var showAll by remember { mutableStateOf(false) }
    var viewDoc by remember { mutableStateOf<Document?>(null) }
    val whenText = Instant.ofEpochMilli(appointment.whenMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY))

    fun toggle(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    val others = docs.filter { d -> plan.suggestedDocs.none { it.id == d.id } }
        .sortedByDescending { docEpochDay(it) }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onClose,
        title = { Text("Unterlagen: ${appointment.title}") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (docs.isEmpty()) {
                    Text(
                        "Noch keine Dokumente gespeichert. Im Tab „Dokumente“ kannst du Berichte, Befunde und Bescheide fotografieren oder als Datei laden und sie dann hier anhängen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    if (plan.suggestedDocs.isNotEmpty()) {
                        Text("Vorgeschlagen für diesen Termin", style = MaterialTheme.typography.labelLarge)
                        plan.suggestedDocs.forEach { d ->
                            DocRow(d, plan.reasons[d.id], d.id in selected, { toggle(d.id) }, { viewDoc = d })
                        }
                        OutlinedButton(shape = buttonShape(), onClick = {
                            plan.suggestedDocs.forEach { if (it.id !in selected) selected.add(it.id) }
                        }) { Text("Alle Vorschläge anhängen") }
                    }
                    if (others.isNotEmpty()) {
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(if (showAll) "Weitere Dokumente ausblenden" else "Weitere Dokumente anzeigen (${others.size})")
                        }
                        if (showAll) others.forEach { d -> DocRow(d, null, d.id in selected, { toggle(d.id) }, { viewDoc = d }) }
                    }
                }
                if (plan.missingCategories.isNotEmpty() && docs.isNotEmpty()) {
                    Text(
                        "Dazu ist noch nichts gespeichert: " + plan.missingCategories.joinToString(", ") + ".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("Zum Mitnehmen (Anregungen)", style = MaterialTheme.typography.labelLarge)
                plan.checklist.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• $item", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val added = item in todoAdded
                        TextButton(enabled = !added, onClick = {
                            onAddTodo("Termin ${appointment.title} ($whenText): $item")
                            todoAdded.add(item)
                        }) { Text(if (added) "✓" else "+ Aufgabe") }
                    }
                }
                Text(
                    "Das sind allgemeine Anregungen, keine Vorgabe. Was wirklich gebraucht wird, sagt die Praxis oder Stelle.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("Fertig") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )

    viewDoc?.let { d ->
        DocumentDetailDialog(doc = d, doctors = doctors, onEdit = null, onClose = { viewDoc = null })
    }
}
