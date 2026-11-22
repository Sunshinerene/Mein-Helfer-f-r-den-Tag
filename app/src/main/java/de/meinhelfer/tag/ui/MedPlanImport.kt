package de.meinhelfer.tag.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.ai.MedPlanAi
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.media.BarcodeReader
import de.meinhelfer.tag.media.CardScanner
import de.meinhelfer.tag.media.PhotoStore
import de.meinhelfer.tag.summary.MedPlan
import de.meinhelfer.tag.summary.MedPlans
import de.meinhelfer.tag.summary.PlanMatch
import de.meinhelfer.tag.summary.PlanStatus
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PLAN_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Letzte Aenderung am Medikamentenplan (Epoch-Tag): Uebernahme, eigene Aenderung oder neuestes Medikament. */
internal fun lastPlanChangeDay(context: Context, personId: Long, meds: List<Medication>): Long? {
    val changed = AppPrefs.medListChanged(context, personId).takeIf { it > 0 }
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
    val stand = AppPrefs.medPlanStand(context, personId).takeIf { it >= 0 }
    val created = meds.filter { it.personId == personId }.maxOfOrNull { it.createdDay }
    return listOfNotNull(changed, stand, created).maxOrNull()
}

private class ReviewRow(val match: PlanMatch) {
    var use by mutableStateOf(match.status != PlanStatus.Same)
    var name by mutableStateOf(match.med.name.takeUnless { match.med.nameMissing }.orEmpty())
    var dosage by mutableStateOf(match.med.dosage())
}

private class RemoveRow(val med: Medication) {
    var remove by mutableStateOf(false)
}

/** Vorschlag pruefen und uebernehmen: neu, geaendert, unveraendert, nicht mehr im Plan. */
@Composable
fun MedPlanReviewDialog(
    plan: MedPlan,
    planDay: Long,
    meds: List<Medication>,
    personId: Long,
    docId: Long?,
    vm: MainViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val own = meds.filter { it.personId == personId }
    val (matches, missing) = remember(plan, own) { MedPlans.match(plan, own) }
    val rows = remember(matches) { matches.map { ReviewRow(it) } }
    val removeRows = remember(missing) { missing.map { RemoveRow(it) } }
    val last = lastPlanChangeDay(context, personId, meds)
    val older = last != null && planDay < last
    val today = LocalDate.now().toEpochDay()

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Medikamentenplan übernehmen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Stand des Plans: " + LocalDate.ofEpochDay(planDay).format(PLAN_DAY) + " · ausgelesen per " +
                        when (plan.source) {
                            "Code" -> "Code auf dem Plan"
                            "KI" -> "KI"
                            else -> "Texterkennung"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                last?.let {
                    Text(
                        "Deine Liste wurde zuletzt am " + LocalDate.ofEpochDay(it).format(PLAN_DAY) + " geändert." +
                            if (older) " Dieser Plan ist älter. Übernimm ihn nur, wenn du sicher bist, dass er noch gilt." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (older) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Bitte mit dem Original vergleichen und korrigieren. Nur Angehaktes wird übernommen.", style = MaterialTheme.typography.bodySmall)
                rows.forEach { r ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.use, onCheckedChange = { r.use = it })
                        Text(
                            r.match.status.label + (r.match.existing?.let { if (r.match.status == PlanStatus.Changed) ": ersetzt „${it.name}“" else "" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = when (r.match.status) {
                                PlanStatus.New -> MaterialTheme.colorScheme.primary
                                PlanStatus.Changed -> MaterialTheme.colorScheme.tertiary
                                PlanStatus.Same -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    OutlinedTextField(
                        value = r.name,
                        onValueChange = { r.name = it.take(80) },
                        label = { Text(if (r.match.med.nameMissing) "Name bitte ergänzen (${r.match.med.name})" else "Name") },
                        isError = r.use && r.name.isBlank(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = r.dosage,
                        onValueChange = { r.dosage = it.take(80) },
                        label = { Text("Dosierung") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val times = r.match.med.times()
                    Text(
                        if (times.isNotEmpty()) {
                            "Einnahme: " + times.joinToString(", ") + " Uhr (" +
                                r.match.med.amounts.mapIndexedNotNull { i, a -> if ((MedPlans.amount(a) ?: 0.0) > 0) MedPlans.SLOT_LABELS[i] else null }.joinToString(", ") + ")"
                        } else {
                            "Einnahmezeiten bitte danach beim Medikament festlegen" + r.match.med.freeSchema.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    r.match.med.noteText().takeIf { it.isNotBlank() }?.let { Text("Hinweis: $it", style = MaterialTheme.typography.bodySmall) }
                    r.match.existing?.takeIf { r.match.status == PlanStatus.Changed }?.let { e ->
                        Text(
                            "Bisher: " + listOf(e.dosage, e.times.takeIf { it.isNotBlank() }?.let { it.replace(",", ", ") + " Uhr" }.orEmpty())
                                .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "ohne Angaben" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (removeRows.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Nicht mehr im Plan", style = MaterialTheme.typography.titleSmall)
                    Text("Bitte nur entfernen, wenn das Medikament wirklich abgesetzt ist.", style = MaterialTheme.typography.bodySmall)
                    removeRows.forEach { rr ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rr.remove, onCheckedChange = { rr.remove = it })
                            Text("Entfernen: " + rr.med.name + if (rr.med.dosage.isNotBlank()) " (${rr.med.dosage})" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    "Den Vorrat trägst du danach beim Medikament ein, sonst erscheint „Bald nachbestellen“. Maßgeblich ist immer der Plan von Arztpraxis oder Apotheke.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            val chosen = rows.count { it.use } + removeRows.count { it.remove }
            TextButton(
                enabled = chosen > 0 && rows.none { it.use && it.name.isBlank() },
                onClick = {
                    val adds = mutableListOf<Medication>()
                    val updates = mutableListOf<Medication>()
                    rows.filter { it.use }.forEach { r ->
                        val pm = r.match.med
                        val times = pm.times().joinToString(",")
                        val e = r.match.existing
                        if (e == null) {
                            adds += Medication(
                                name = r.name.trim(),
                                dosage = r.dosage.trim(),
                                times = times,
                                stock = 0,
                                dailyUse = pm.dailyUse(),
                                notes = pm.noteText(),
                                pzn = pm.pzn,
                                personId = personId,
                            )
                        } else {
                            val notes = pm.noteText().takeIf { it.isNotBlank() && !e.notes.contains(it) }?.let { listOf(e.notes, it).filter { s -> s.isNotBlank() }.joinToString("\n") } ?: e.notes
                            updates += e.copy(
                                name = r.name.trim(),
                                dosage = r.dosage.trim(),
                                times = if (times.isNotBlank()) times else e.times,
                                dailyUse = if (pm.hasSchema) pm.dailyUse() else e.dailyUse,
                                // Vorrat auf heute umrechnen, damit der neue Verbrauch nicht rueckwirkend zaehlt
                                stock = e.currentStock(today),
                                stockDay = today,
                                notes = notes,
                                pzn = e.pzn.ifBlank { pm.pzn },
                            )
                        }
                    }
                    vm.applyMedPlan(personId, adds, updates, removeRows.filter { it.remove }.map { it.med }, planDay, docId)
                    onDone()
                },
            ) { Text("Übernehmen ($chosen)") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}

/** Im Dokument: Medikamentenplan erkannt, uebernehmen (Text, Code auf dem Plan oder KI). */
@Composable
fun MedPlanImportBox(doc: Document, person: Person?, meds: List<Medication>, vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val personId = person?.id ?: doc.personId
    val local = remember(doc.text) { MedPlans.parseText(doc.text) }
    var plan by remember(doc.id) { mutableStateOf(local.takeIf { it.meds.isNotEmpty() }) }
    var review by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val planDay = MedPlans.planDay(doc, plan)
    val last = lastPlanChangeDay(context, personId, meds)

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let { File(it) }
        pendingPath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (!ok) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        reading = true
        scope.launch {
            val codes = try {
                val bitmap = withContext(Dispatchers.IO) { PhotoStore.decodeSampled(file, 2000) }
                bitmap?.let { b -> runCatching { BarcodeReader.scanAll(b) }.getOrDefault(emptyList()).also { b.recycle() } } ?: emptyList()
            } finally {
                withContext(Dispatchers.IO) { file.delete() }
            }
            reading = false
            val parsed = codes.firstNotNullOfOrNull { MedPlans.parseBmp(it) }
            if (parsed == null || parsed.meds.isEmpty()) {
                message = "Kein Code eines Medikationsplans erkannt. Der quadratische Code steht oben rechts auf dem Plan. Näher ran, gut beleuchtet."
            } else {
                // Namen fehlen bei Eintraegen nur mit PZN: bei gleicher Anzahl aus der Texterkennung in gleicher Reihenfolge vorschlagen
                val names = local.meds
                val filled = if (names.size == parsed.meds.size) {
                    parsed.meds.mapIndexed { i, m -> if (m.nameMissing) m.copy(name = names[i].name, strength = m.strength.ifBlank { names[i].strength }, nameMissing = false) else m }
                } else parsed.meds
                plan = parsed.copy(meds = filled)
                message = "Aus dem Code gelesen: ${filled.size} Medikamente" + if (filled.any { it.nameMissing }) ". Bei manchen fehlt der Name, bitte ergänzen." else "."
                review = true
            }
        }
    }

    HCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Medikamentenplan", style = MaterialTheme.typography.titleSmall)
            val p = plan
            Text(
                if (p != null) "${p.meds.size} Medikamente erkannt, Stand " + LocalDate.ofEpochDay(planDay).format(PLAN_DAY) + "."
                else "Im Text wurden keine Medikamente sicher erkannt. Versuche den Code auf dem Plan oder die KI.",
                style = MaterialTheme.typography.bodyMedium,
            )
            last?.let {
                Text(
                    if (planDay > it) "Neuer als deine letzte Änderung am " + LocalDate.ofEpochDay(it).format(PLAN_DAY) + "."
                    else "Nicht neuer als deine letzte Änderung am " + LocalDate.ofEpochDay(it).format(PLAN_DAY) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (planDay > it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p != null) {
                Button(shape = buttonShape(), onClick = { review = true }, modifier = Modifier.fillMaxWidth()) { Text("Ansehen und übernehmen") }
            }
            OutlinedButton(shape = buttonShape(), enabled = !reading, onClick = {
                val file = CardScanner.newFile(context)
                pendingPath = file.path
                try {
                    camera.launch(CardScanner.uriFor(context, file))
                } catch (_: Exception) {
                    file.delete()
                    pendingPath = null
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Code auf dem Plan fotografieren") }
            if (reading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Code wird gelesen …", style = MaterialTheme.typography.bodySmall)
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Der bundeseinheitliche Medikationsplan hat oben rechts einen Code mit allen Einnahmen. Das Foto wird nur auf dem Handy gelesen und gleich gelöscht.",
                style = MaterialTheme.typography.bodySmall,
            )
            AiActionButton(
                label = "Mit KI genauer auslesen",
                note = "Gesendet wird der erkannte Text des Dokuments; Name, Geburtsdatum, Versichertennummer und Straße der Person werden vorher ersetzt.",
                system = MedPlanAi.SYSTEM_PROMPT,
                prompt = { MedPlanAi.prompt(maskPersonal(doc.text, person)) },
                onAnswer = { answer, _ ->
                    val parsed = MedPlanAi.parse(answer)
                    if (parsed != null) {
                        plan = parsed
                        review = true
                    }
                    parsed != null
                },
            )
        }
    }

    val current = plan
    if (review && current != null) {
        MedPlanReviewDialog(
            plan = current,
            planDay = MedPlans.planDay(doc, current),
            meds = meds,
            personId = personId,
            docId = doc.id,
            vm = vm,
            onDone = { review = false },
        )
    }
}

/** Auf der Medikamente-Seite: neuer Medikamentenplan in den Dokumenten gefunden. */
@Composable
fun MedPlanSuggestion(documents: List<Document>, meds: List<Medication>, person: Person?, vm: MainViewModel) {
    val context = LocalContext.current
    val personId = person?.id ?: return
    var hidden by remember { mutableStateOf(AppPrefs.medPlanHandled(context)) }
    var open by remember { mutableStateOf(false) }
    val last = lastPlanChangeDay(context, personId, meds)
    val candidate = remember(documents, hidden, last) {
        documents.filter { it.personId == personId && it.id.toString() !in hidden && MedPlans.isPlanDocument(it) }
            .map { it to MedPlans.planDay(it) }
            .filter { (_, day) -> last == null || day > last }
            .maxByOrNull { it.second }
    } ?: return
    val (doc, day) = candidate
    HCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Neuer Medikamentenplan gefunden", style = MaterialTheme.typography.titleSmall)
            Text(
                "„${doc.title}“ vom " + LocalDate.ofEpochDay(day).format(PLAN_DAY) + " ist neuer als deine Liste. Medikamente mit Einnahmezeiten übernehmen?",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = buttonShape(), onClick = { open = true }) { Text("Ansehen") }
                TextButton(onClick = {
                    AppPrefs.addMedPlanHandled(context, doc.id)
                    hidden = AppPrefs.medPlanHandled(context)
                }) { Text("Nicht jetzt") }
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(doc.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { MedPlanImportBox(doc, person, meds, vm) } },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    hidden = AppPrefs.medPlanHandled(context)
                }) { Text("Schließen") }
            },
        )
    }
}
